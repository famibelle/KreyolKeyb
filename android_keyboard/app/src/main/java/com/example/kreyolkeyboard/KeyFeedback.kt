package com.example.kreyolkeyboard

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.View
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Retour de frappe du clavier : vibration et son, au même endroit.
 *
 * Il existe parce que quatre classes en ont besoin (les touches dans
 * [KeyboardLayoutManager], les puces de suggestion dans le service, les caractères
 * accentués dans [AccentHandler], les emojis dans [EmojiPickerView]) et qu'une
 * politique de retour dupliquée finit toujours par diverger. Tout ce qui écrit du
 * texte passe par ici.
 *
 * ### Le clavier décide, et l'utilisateur peut couper
 *
 * Les deux retours sont **volontairement indépendants des réglages génériques du
 * téléphone**, et pilotés par [KeyboardPreferences] à la place.
 *
 * La v10.11.5 avait fait l'inverse, par souci de ne pas dupliquer un interrupteur
 * du système. Constaté sur un Samsung réel en 10.11.6 : sur One UI, « Vibration au
 * toucher » ne gouverne que le clavier Samsung, et rien n'est proposé pour les
 * claviers tiers. Le clavier restait donc muet quoi que fasse l'utilisateur. Gboard
 * et SwiftKey ont pour cette raison leurs propres interrupteurs, activés par
 * défaut : c'est le comportement standard, pas une extravagance.
 *
 * D'où les deux mécanismes de contournement, chacun documenté à son point d'appel :
 * `FLAG_IGNORE_GLOBAL_SETTING` pour la vibration, et la variante à volume explicite
 * de `playSoundEffect` pour le son.
 *
 * ### Le son n'était pas voulu et n'était pas le bon
 *
 * Avant la v10.11.6, aucune ligne du projet ne demandait de son, mais
 * `View.performClick()` jouait de lui-même `SoundEffectConstants.CLICK`, soit
 * `Effect_Tick.ogg`, le clic d'interface d'un bouton quelconque. Les touches
 * lettres cliquaient donc comme un bouton de formulaire, tandis que la barre
 * d'espace restait muette faute de `OnClickListener` (elle n'en a pas, pour éviter
 * le double espace décrit dans `setupButtonInteractions`). Android embarque
 * pourtant quatre sons de clavier distincts, que [effetPour] choisit désormais.
 *
 * Les appelants doivent poser `isSoundEffectsEnabled = false` sur leurs vues, sinon
 * le son implicite de `performClick()` s'ajoute à celui joué ici.
 */
object KeyFeedback {

    private const val TAG = "KeyFeedback"

    /**
     * Volume des sons de frappe, sur l'échelle linéaire de `playSoundEffect`.
     * Valeur modérée : ces sons ponctuent chaque appui, ils doivent s'entendre sans
     * couvrir ce que l'utilisateur écoute par ailleurs.
     */
    private const val SOUND_VOLUME = 0.4f

    /**
     * L'échelle des crans composés : plancher, dénivelé qui donne la pleine
     * force, et force du simple contact. Le plancher existe parce que sous
     * ~0,3 la plupart des actionneurs ne rendent plus rien de distinct. Le
     * plein est un peu au-dessus du plus haut dénivelé isolé de la face
     * (l'agrafe qui sort du fond de l'ouverture, 2,0) : au-delà, deux marches
     * fondues par `Ornement.crans`. Points de départ, à régler au pouce.
     */
    private const val ECHELLE_MIN = 0.3f
    private const val DENIVELE_PLEIN = 2.5f
    private const val ECHELLE_CONTACT = 0.4f

    /** Les crans composés sont gardés en dix échelons par signe. */
    private const val ECHELONS = 10

    private const val CRAN_INCONNU = 40L

    // Conservé entre les frappes : le service de son se cherche une fois, pas à
    // chaque touche. Le contexte d'application est utilisé pour ne pas retenir une
    // vue ni la fenêtre de saisie.
    private var audioManager: AudioManager? = null

    // Réglages en cache : ce code est sur le chemin de chaque appui de touche, il ne
    // doit pas relire les préférences à chaque frappe. Renseigné à la première
    // utilisation, puis mis à jour par [refresh].
    private var hapticEnabled: Boolean? = null
    private var soundEnabled: Boolean? = null

    /**
     * Relit les réglages. Appelé par le service à chaque prise de focus et par
     * l'écran de réglages après un changement, pour qu'un choix s'applique dès le
     * retour dans un champ de saisie.
     */
    fun refresh(context: Context) {
        hapticEnabled = KeyboardPreferences.hapticEnabled(context)
        soundEnabled = KeyboardPreferences.soundEnabled(context)
        sonder(context)
    }

    // ------------------------------------------------ le relief des cartes

    /**
     * Ce que l'actionneur de ce téléphone-là sait rendre, du plus fin au plus
     * pauvre. Ne sert qu'aux cartes du carnet : les touches du clavier restent
     * sur `performHapticFeedback`, et rien ici n'est lu sur le chemin d'une frappe.
     *
     * Il n'y a pas de niveau intermédiaire à `createPredefined(EFFECT_TICK)`.
     * Il ne porterait pas plus d'information que [CANNED], un seul timbre sans
     * amplitude, mais il changerait l'effet joué : `CLOCK_TICK` n'est pas
     * garanti de se résoudre en `EFFECT_TICK`. Un appareil sans composition
     * doit sentir la carte exactement comme avant.
     */
    enum class NiveauTactile {
        /**
         * Primitives composées : un cran net et freiné, dont l'échelle porte
         * l'amplitude et la primitive porte le signe. Le seul niveau où le
         * relief signé existe vraiment.
         */
        COMPOSITION,
        /** `performHapticFeedback(CLOCK_TICK)` : un timbre, et le seul qui survive
         *  au retour tactile du système éteint. */
        CANNED,
        /** Rien d'utilisable : tout se tait, sans exception. */
        AUCUN
    }

    private var vibreur: Vibrator? = null
    private var niveau: NiveauTactile? = null
    private var dureeCran = 0L

    /**
     * Les crans composés, par signe et par échelle arrondie : un pouce qui
     * traverse une carte en franchit une dizaine, et aucun ne doit allouer.
     */
    private val crans = arrayOfNulls<VibrationEffect>(2 * (ECHELONS + 1))

    /** Le niveau atteint sur cet appareil, sondé une fois puis par [refresh]. */
    fun niveauTactile(context: Context): NiveauTactile =
        niveau ?: sonder(context)

    /**
     * Combien de temps un cran occupe l'actionneur, en millisecondes.
     *
     * Une carte qui demande un cran avant la fin du précédent obtient une
     * bouillie : sur la rangée des écus, deux arêtes sont à 1,2 mm l'une de
     * l'autre, soit ~50 ms pour un pouce qui explore. [Carton] s'en sert pour
     * éclaircir sa partition plutôt que de la brouiller.
     *
     * Zéro hors [NiveauTactile.COMPOSITION] : aux autres niveaux, la carte doit
     * se sentir exactement comme avant, et la cadence d'avant n'en avait pas.
     * Quarante millisecondes quand l'appareil compose sans dire en combien de
     * temps (Android 11, ou une durée que le pilote laisse à zéro) : la valeur
     * prudente, qui perd du détail sur un bon moteur plutôt que du rythme sur
     * un mauvais.
     */
    fun dureeDuCran(context: Context): Long {
        niveauTactile(context)
        return dureeCran
    }

    /**
     * Décide de la voie une fois pour toutes, jusqu'au prochain [refresh].
     *
     * ### La voie riche ne sert que là où elle ne sera pas jetée
     *
     * `Vibrator.vibrate()` n'a aucun équivalent de `FLAG_IGNORE_GLOBAL_SETTING`
     * : quand le retour tactile du système est éteint, la demande peut partir
     * au rebut sans un mot. Basculer naïvement sur lui recréerait le bogue de
     * la 10.11.5, en pire, puisque la carte se tairait là où elle vibrait. On
     * ne quitte donc [NiveauTactile.CANNED] que si le réglage système vaut
     * **explicitement** 1 ; illisible ou absent, c'est la route sûre.
     *
     * L'intensité tactile du système n'est pas lisible par une API publique.
     * On ne la lit pas : en Android 13+, l'usage `USAGE_TOUCH` la fait
     * appliquer par le système lui-même.
     *
     * ### Pas de liste de modèles
     *
     * `areAllPrimitivesSupported` est le test matériel honnête : il ne répond
     * oui que là où l'actionneur en est capable. Reconnaître des téléphones
     * par leur nom serait faux dès le prochain modèle.
     */
    private fun sonder(context: Context): NiveauTactile {
        val app = context.applicationContext
        val v = try {
            trouverVibreur(app)
        } catch (e: Exception) {
            null
        }
        vibreur = v
        val trouve = try {
            when {
                Build.VERSION.SDK_INT < Build.VERSION_CODES.O -> NiveauTactile.AUCUN
                v == null || !v.hasVibrator() -> NiveauTactile.AUCUN
                !voieRicheSure(app) -> NiveauTactile.CANNED
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                    v.areAllPrimitivesSupported(
                        VibrationEffect.Composition.PRIMITIVE_CLICK,
                        VibrationEffect.Composition.PRIMITIVE_TICK
                    ) -> NiveauTactile.COMPOSITION
                else -> NiveauTactile.CANNED
            }
        } catch (e: Exception) {
            Log.d(TAG, "Sonde tactile impossible: ${e.message}")
            NiveauTactile.CANNED
        }
        niveau = trouve
        dureeCran = if (trouve == NiveauTactile.COMPOSITION) mesurerCran(v) else 0L
        crans.fill(null)
        return trouve
    }

    /** Le contexte d'application seulement, comme l'`AudioManager` voisin. */
    private fun trouverVibreur(app: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (app.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                ?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    @Suppress("DEPRECATION")
    private fun voieRicheSure(app: Context): Boolean {
        if (app.checkSelfPermission(Manifest.permission.VIBRATE) !=
            PackageManager.PERMISSION_GRANTED
        ) return false
        return Settings.System.getInt(
            app.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 0
        ) == 1
    }

    private fun mesurerCran(v: Vibrator?): Long {
        if (v == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return CRAN_INCONNU
        val durees = v.getPrimitiveDurations(
            VibrationEffect.Composition.PRIMITIVE_CLICK,
            VibrationEffect.Composition.PRIMITIVE_TICK
        )
        val pire = durees.maxOrNull() ?: 0
        return if (pire > 0) pire.toLong() else CRAN_INCONNU
    }

    /**
     * Le cran composé pour un dénivelé : le signe choisit la primitive,
     * l'amplitude son échelle.
     *
     * Monter sur une pièce et en redescendre ne se ressemblent pas sur un
     * objet réel : le doigt bute contre une montée, il tombe d'une descente.
     * `PRIMITIVE_CLICK`, plus franc, pour la première ; `PRIMITIVE_TICK`, plus
     * léger, pour la seconde. C'est un point de départ à régler au pouce.
     */
    private fun cranCompose(denivele: Float): VibrationEffect? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val montee = denivele >= 0f
        val echelle = if (denivele == 0f) ECHELLE_CONTACT
        else ECHELLE_MIN + (1f - ECHELLE_MIN) * min(abs(denivele) / DENIVELE_PLEIN, 1f)
        val echelon = (echelle * ECHELONS).roundToInt().coerceIn(0, ECHELONS)
        val i = (if (montee) 0 else ECHELONS + 1) + echelon
        crans[i]?.let { return it }
        val primitive = if (montee) VibrationEffect.Composition.PRIMITIVE_CLICK
        else VibrationEffect.Composition.PRIMITIVE_TICK
        return VibrationEffect.startComposition()
            .addPrimitive(primitive, echelon.toFloat() / ECHELONS)
            .compose()
            .also { crans[i] = it }
    }

    private fun vibrerCompose(effet: VibrationEffect) {
        val v = vibreur ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            v.vibrate(effet, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
        } else {
            v.vibrate(effet)
        }
    }

    /**
     * Retour complet d'une frappe : vibration puis son.
     *
     * @param key la touche frappée, qui choisit le son. `null` ou toute autre valeur
     *   qu'espace, suppression et entrée donnent le son de frappe standard, ce qui
     *   couvre les lettres, la ponctuation, les touches de mode, les caractères
     *   accentués, les emojis et les puces de suggestion.
     */
    fun onKeyPress(view: View, key: String? = null) {
        val context = view.context
        if (hapticEnabled ?: KeyboardPreferences.hapticEnabled(context).also { hapticEnabled = it }) {
            vibrate(view)
        }
        if (soundEnabled ?: KeyboardPreferences.soundEnabled(context).also { soundEnabled = it }) {
            playSound(context, key)
        }
    }

    /**
     * Retour d'un cran de déplacement du curseur, quand le doigt glisse sur la
     * barre d'espace (v14.0.0) : vibration seule, et jamais de son.
     *
     * Un glissement d'un bord à l'autre de l'écran franchit une trentaine de
     * crans. Le son de frappe joué à chacun d'eux tournerait à la crécelle,
     * alors que la vibration donne exactement ce qu'on cherche ici : la
     * granularité du geste, un cran valant un caractère.
     *
     * [HapticFeedbackConstants.CLOCK_TICK] et non `KEYBOARD_TAP` : c'est
     * l'effet le plus court du catalogue, celui que le système réserve aux
     * défilements crantés. Répété trente fois, `KEYBOARD_TAP` se sent comme
     * une frappe continue.
     */
    fun onCursorStep(view: View) {
        val context = view.context
        if (hapticEnabled ?: KeyboardPreferences.hapticEnabled(context).also { hapticEnabled = it }) {
            vibrate(view, HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    /**
     * Retour d'une arête franchie par le doigt sur une carte du carnet
     * (v22.5.1) : vibration seule, et jamais de son.
     *
     * C'est la même intention que [onCursorStep] et le même effet, pour la
     * même raison : un pouce qui traverse une carte franchit une poignée de
     * reliefs — le bord du cadre, les flancs de l'ouverture, les écus — et ce
     * qu'on cherche à rendre est la granularité de la surface, pas une suite
     * de frappes. C'est aussi le seul retour du carnet qui fonctionne les
     * yeux fermés, donc le seul qui prouve vraiment que la carte est un objet
     * et pas une lumière.
     *
     * Le premier appel d'un geste ne correspond à aucune arête : c'est le
     * contact lui-même. Un carton posé ne claque pas quand on le touche, mais
     * un écran qui ne répond pas à un doigt posé n'a rien touché du tout.
     *
     * ### Le dénivelé (v26.0.0)
     *
     * [denivele] est ce que le doigt vient de franchir, en unités de carte,
     * positif en montant ; zéro est le contact. Au niveau
     * [NiveauTactile.COMPOSITION] le signe choisit le timbre et l'amplitude sa
     * force : c'est ce couple qui fait dire « je suis monté sur quelque chose
     * de large et j'en suis redescendu » plutôt que « j'ai senti deux tics ».
     *
     * Aux autres niveaux, il est ignoré et un seul timbre subsiste. Ce n'est
     * pas une paresse : **un faux second timbre est pire qu'un seul vrai**. Un
     * actionneur qui ne sait pas freiner rendrait la montée et la descente par
     * deux bourdonnements à peine différents, et le doigt en conclurait que la
     * carte est irrégulière, pas qu'elle a des volumes.
     */
    fun onCardRidge(view: View, denivele: Float = 0f) {
        val context = view.context
        val active = hapticEnabled
            ?: KeyboardPreferences.hapticEnabled(context).also { hapticEnabled = it }
        if (!active) return
        when (niveauTactile(context)) {
            NiveauTactile.COMPOSITION -> try {
                cranCompose(denivele)?.let { vibrerCompose(it) }
            } catch (e: Exception) {
                // Une composition refusée à l'exécution ne doit pas rendre la
                // carte muette : on retombe sur la route qui survit à tout.
                Log.d(TAG, "Cran composé refusé: ${e.message}")
                niveau = NiveauTactile.CANNED
                dureeCran = 0L
                vibrate(view, HapticFeedbackConstants.CLOCK_TICK)
            }
            NiveauTactile.CANNED -> vibrate(view, HapticFeedbackConstants.CLOCK_TICK)
            NiveauTactile.AUCUN -> Unit
        }
    }

    /**
     * Retour d'une carte qui passe au centre de l'éventail d'un casier
     * (v22.12.1) : vibration seule, le même cran que [onCursorStep], car
     * parcourir un casier de deux cents cartes est un défilement cranté.
     */
    fun onFanStep(view: View) {
        val context = view.context
        if (hapticEnabled ?: KeyboardPreferences.hapticEnabled(context).also { hapticEnabled = it }) {
            vibrate(view, HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    private fun vibrate(view: View, effect: Int = HapticFeedbackConstants.KEYBOARD_TAP) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // FLAG_IGNORE_GLOBAL_SETTING : sans lui, le système jette la demande
                // (« ignored_for_settings » dans dumpsys vibrator_manager) dès que le
                // retour tactile générique du téléphone est éteint, ce qui rendait le
                // clavier muet sur Samsung où ce réglage ne concerne pas les claviers
                // tiers. L'échappatoire est ici le réglage de l'application, pas celui
                // du système.
                view.performHapticFeedback(
                    effect,
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                )
            }
        } catch (e: Exception) {
            Log.d(TAG, "Feedback haptique non disponible: ${e.message}")
        }
    }

    private fun playSound(context: Context, key: String?) {
        try {
            val manager = audioManager ?: (context.applicationContext
                .getSystemService(Context.AUDIO_SERVICE) as? AudioManager)
                ?.also { audioManager = it }
            // La variante à volume explicite est celle des claviers : contrairement à
            // playSoundEffect(effectType), elle ne consulte pas le réglage « sons au
            // toucher » du téléphone. Même raison que le drapeau haptique ci-dessus.
            manager?.playSoundEffect(effetPour(key), SOUND_VOLUME)
        } catch (e: Exception) {
            Log.d(TAG, "Son de frappe non disponible: ${e.message}")
        }
    }

    /** Le son de frappe d'Android correspondant à la touche. */
    private fun effetPour(key: String?): Int = when (key) {
        " " -> AudioManager.FX_KEYPRESS_SPACEBAR
        "⌫" -> AudioManager.FX_KEYPRESS_DELETE
        "⏎" -> AudioManager.FX_KEYPRESS_RETURN
        else -> AudioManager.FX_KEYPRESS_STANDARD
    }
}
