package com.example.kreyolkeyboard

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.View

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
