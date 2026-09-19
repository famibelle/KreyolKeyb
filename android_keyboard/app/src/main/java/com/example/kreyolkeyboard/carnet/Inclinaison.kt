package com.example.kreyolkeyboard.carnet

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.View
import android.view.WindowManager
import java.util.WeakHashMap
import kotlin.math.PI
import kotlin.math.atan2

/**
 * L'inclinaison du téléphone, rendue à la carte ouverte.
 *
 * ## Pourquoi la carte bouge
 *
 * Le reflet spéculaire d'une carte distinguée suit déjà le roulis de
 * l'appareil (voir `Ornement.dessinerReflet`). Tant que la carte reste
 * immobile, cette lumière glisse sur un objet qui ne bouge pas : l'œil le
 * lit comme un défaut, pas comme un reflet. Faire pivoter la carte de
 * quelques degrés referme la boucle — la lumière a enfin une surface qui
 * lui donne raison.
 *
 * ## Ce que ça coûte
 *
 * Rien, ou presque. `rotationX` et `rotationY` sont des propriétés du
 * `RenderNode` : le GPU les applique à la composition, sans jamais
 * repasser par `onDraw`. Le reflet, lui, appelle `invalidate()` et rejoue
 * quelques centaines d'ordres de tracé. L'inclinaison est donc l'effet le
 * moins cher des deux, alors que c'est le plus visible.
 *
 * ## Les quatre décisions
 *
 * - **Le neutre est relatif, pas absolu.** Personne ne tient son téléphone
 *   à plat : au repos, le tangage vaut déjà soixante degrés. Une carte
 *   calée sur l'orientation absolue serait donc penchée en permanence. On
 *   retient l'orientation du premier échantillon comme repos, et on ne
 *   joue que l'écart. Un [RAPPEL] très lent ramène ce repos vers la
 *   posture courante, sans quoi la carte resterait de travers dès que le
 *   joueur s'allonge ou change de main.
 * - **[AMPLITUDE] vaut vingt degrés.** Elle en valait dix, par crainte que
 *   les filets d'or d'un pixel scintillent et que le bord fuyant devienne
 *   illisible. Sur l'appareil, dix degrés ne se voyaient presque pas : le
 *   capteur n'atteint jamais le bout de sa course dans une main, et la carte
 *   restait sous les deux degrés. Doublée à la demande du propriétaire
 *   (2026-09-15).
 * - **La carte contre-pivote.** Quand le bord droit du téléphone descend,
 *   le bord droit de la carte vient vers le joueur. La carte résiste au
 *   geste au lieu de le suivre, comme un objet posé qui garderait son
 *   aplomb : c'est ce décalage qui se lit comme de la profondeur. Une
 *   carte qui suivrait exactement l'écran ne bougerait pas, puisqu'elle
 *   est déjà dessinée dessus.
 * - **[TYPE_GRAVITY][Sensor.TYPE_GRAVITY] plutôt que l'accéléromètre brut.**
 *   L'accéléromètre mélange la pesanteur et l'accélération linéaire :
 *   marcher suffit à faire trembler la carte. Le capteur fusionné isole la
 *   pesanteur et supprime ce tremblement.
 *
 * ## Ce que l'inclinaison n'a pas le droit de faire
 *
 * Elle ne récompense pas. L'ornement monte avec la rareté ; la physique,
 * non. Une commune qui ne répondrait pas à la main se lirait comme un
 * bogue, pas comme une carte moins précieuse — le privilège d'un haut
 * palier, c'est la lumière, pas le poids.
 *
 * Et elle ne descend jamais dans la grille : seule la carte ouverte suit
 * l'appareil. Une grille entière qui s'incline à l'unisson donne le mal de
 * mer et se bat contre le défilement.
 */
object Inclinaison {

    /**
     * Le débattement en degrés, de part et d'autre du repos.
     *
     * Public parce que [Carton] en a besoin : un appui du doigt s'exprime en
     * degrés, et le tracé de la tranche en roulis. Sans le même dénominateur
     * des deux côtés, l'épaisseur du carton ne correspondrait pas à sa
     * propre inclinaison.
     */
    const val AMPLITUDE = 20f

    /**
     * Le lissage du suivi.
     *
     * Un filtre passe-bas donne du poids à la carte : elle arrive sur son
     * inclinaison au lieu d'y sauter. C'est la même intention que le
     * lissage du reflet, et il faut que les deux se ressemblent — sinon la
     * lumière devance la surface qui la porte.
     */
    private const val LISSAGE = 0.18f

    /**
     * La dérive du repos vers la posture courante.
     *
     * Très lent — de l'ordre d'une dizaine de secondes — pour que la carte
     * tenue penchée finisse par se remettre d'aplomb sans que le
     * mouvement se voie.
     */
    internal const val RAPPEL = 0.006f

    /** La distance de caméra par défaut d'Android, en densités. */
    private const val CAMERA_DEFAUT = 1280f

    /**
     * Une perspective assez lointaine pour que vingt degrés restent une
     * inclinaison et non un écrasement du bord fuyant.
     */
    private const val CAMERA = 9000f

    /**
     * Les vues déjà suivies, pour ne pas en armer une deux fois.
     *
     * On n'y range qu'un drapeau, jamais le suiveur : une `WeakHashMap` dont
     * la valeur référencerait sa clé retiendrait la vue pour toujours. Le
     * suiveur, lui, est tenu en vie par la liste d'écouteurs de la vue —
     * c'est-à-dire exactement aussi longtemps qu'il doit l'être.
     */
    private val suivies = WeakHashMap<View, Boolean>()

    /**
     * Ce qui incline chaque vue, en degrés : `[pesanteurX, pesanteurY,
     * appuiX, appuiY]`.
     *
     * ## Pourquoi une somme, et pas deux écrivains
     *
     * `rotationX` et `rotationY` n'ont qu'une valeur. Tant que la pesanteur
     * était seule à écrire dedans, la question ne se posait pas ; depuis que
     * le doigt enfonce le carton, deux sources visent la même propriété, et
     * la dernière servie gagne — soixante fois par seconde, ce qui se voit
     * comme un tremblement et non comme un appui.
     *
     * Elles sont donc rangées séparément et **additionnées** au moment
     * d'écrire. La pesanteur continue de dire où est le bas ; l'appui dit
     * seulement de combien le carton s'enfonce en plus. Les deux restent
     * vraies en même temps, ce qui est exactement ce qu'on observe en posant
     * le pouce sur une carte tenue en main.
     *
     * Un `FloatArray` en valeur, et non le suiveur : une [WeakHashMap] dont
     * la valeur référencerait sa clé retiendrait la vue pour toujours. Quatre
     * flottants ne référencent rien.
     */
    private val etats = WeakHashMap<View, FloatArray>()

    private fun etat(vue: View): FloatArray =
        etats.getOrPut(vue) { FloatArray(4) }

    private fun appliquer(vue: View, e: FloatArray) {
        vue.rotationX = e[0] + e[2]
        vue.rotationY = e[1] + e[3]
    }

    /**
     * L'enfoncement du carton sous le doigt, en degrés, composé avec la
     * pesanteur s'il y en a une.
     *
     * Passer par ici plutôt que d'écrire dans `rotationX` directement est ce
     * qui rend l'appui visible sur une carte du carnet, où l'inclinaison est
     * armée **sur la carte elle-même** ; sans cela, le prochain échantillon
     * du capteur effacerait l'appui avant qu'on l'ait vu.
     */
    fun appui(vue: View, degresX: Float, degresY: Float) {
        val e = etat(vue)
        e[2] = degresX
        e[3] = degresY
        appliquer(vue, e)
    }

    /**
     * Recule la caméra de [vue] assez pour que quelques degrés restent une
     * inclinaison et non un écrasement du bord fuyant.
     *
     * Les appelants qui ont déjà réglé leur perspective — la pochette et la
     * révision le font pour leur retournement — gardent la leur : on ne
     * touche qu'au défaut d'Android.
     */
    fun perspective(vue: View) {
        val densite = vue.resources.displayMetrics.density
        if (vue.cameraDistance <= CAMERA_DEFAUT * densite) {
            vue.cameraDistance = CAMERA * densite
        }
    }

    /**
     * Fait suivre à [vue] l'inclinaison de l'appareil.
     *
     * À appeler **une fois le retournement terminé**, depuis son
     * `withEndAction` : l'ouverture d'une carte anime déjà `rotationY`, et
     * deux écrivains sur la même propriété font trembler la carte pendant
     * toute l'animation.
     *
     * Le suivi s'arme et se désarme tout seul avec la vue ; il n'y a rien à
     * libérer côté appelant.
     */
    fun suivre(vue: View) {
        if (suivies.containsKey(vue)) return
        if (Pochette.animationsReduites(vue.context)) return
        val manager =
            vue.context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        val capteur = manager.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            ?: return

        perspective(vue)

        val suiveur = Suiveur(vue, manager, capteur)
        suivies[vue] = true
        vue.addOnAttachStateChangeListener(suiveur)
        if (vue.isAttachedToWindow) suiveur.armer()
    }

    /**
     * Le suivi d'une vue : un écouteur de capteur qui ne vit que tant que la
     * vue est à l'écran.
     */
    private class Suiveur(
        private val vue: View,
        private val manager: SensorManager,
        private val capteur: Sensor
    ) : SensorEventListener, View.OnAttachStateChangeListener {

        private var arme = false
        private val posture = Posture(vue, LISSAGE)

        fun armer() {
            if (arme) return
            manager.registerListener(this, capteur, SensorManager.SENSOR_DELAY_UI)
            arme = true
        }

        private fun desarmer() {
            if (!arme) return
            manager.unregisterListener(this)
            arme = false
            // Le repos est perdu avec le capteur : une carte qui revient à
            // l'écran doit reprendre la posture du moment, pas celle d'avant.
            posture.oublier()
        }

        override fun onViewAttachedToWindow(v: View) = armer()

        /**
         * La vue quitte l'écran : on rend le capteur et on remet la carte
         * d'aplomb, pour qu'elle ne réapparaisse pas figée sur la dernière
         * inclinaison connue. L'écouteur, lui, reste en place — si la vue
         * revient, le suivi reprend de lui-même.
         */
        override fun onViewDetachedFromWindow(v: View) {
            desarmer()
            val e = etat(vue)
            e[0] = 0f
            e[1] = 0f
            appliquer(vue, e)
        }

        override fun onAccuracyChanged(lequel: Sensor?, precision: Int) = Unit

        override fun onSensorChanged(evenement: SensorEvent) {
            posture.echantillon(evenement.values)

            // Deux écritures de propriété, pas un seul ordre de tracé : la
            // carte n'est pas redessinée, elle est recomposée.
            val e = etat(vue)
            e[0] = posture.tangage * AMPLITUDE
            e[1] = posture.roulis * AMPLITUDE
            appliquer(vue, e)
        }
    }
}

/**
 * La posture du téléphone telle que la carte doit la rendre : un roulis et un
 * tangage dans [-1, 1], comptés depuis le repos et dans le repère de l'écran.
 *
 * ## Pourquoi une seule posture
 *
 * [Inclinaison] fait pivoter la carte, [Carton] dessine sa tranche et son
 * reflet, et chacun lisait le capteur à sa façon. Le pivot partait du repos et
 * suivait la rotation de l'écran ; la tranche lisait l'inclinaison absolue,
 * dans le repère de l'appareil. Un téléphone tenu penché gardait donc sa
 * tranche alors que la carte s'était remise d'aplomb, et en paysage la tranche
 * se trompait de côté. Les deux lisent maintenant la même posture.
 *
 * ## Le signe
 *
 * C'est celui du contre-pivot. Bord droit du téléphone qui descend : x
 * diminue, le roulis est négatif, et un `rotationY` négatif amène le bord
 * droit de la carte vers le joueur. Haut qui monte : y augmente, le tangage
 * est positif, et le haut de la carte recule. L'écart `repos - x` d'origine
 * faisait suivre le téléphone à la carte.
 *
 * ## Pourquoi le tangage se lit en angle
 *
 * Le roulis se lit sur `x / g`, le tangage se lisait sur `y / g`. Or un
 * téléphone se tient presque debout, et `y = g·sin(tangage)` s'aplatit à
 * l'approche de la verticale : dix degrés vers l'avant ou l'arrière ne
 * bougeaient la carte que d'une fraction de degré, et l'inclinaison haut-bas
 * passait pour absente. Le tangage est donc l'angle `atan2(y, z)`, compté
 * depuis le repos et ramené à [-1, 1] sur [TANGAGE_PLEIN] : sa sensibilité ne
 * dépend plus de la façon dont on tient l'appareil. Le roulis garde sa
 * lecture, parce que le reflet et la tranche de [Carton] sont réglés dessus.
 */
internal class Posture(private val vue: View, private val lissage: Float) {

    private var cale = false
    private var reposX = 0f
    private var reposAngle = 0f

    var roulis = 0f
        private set
    var tangage = 0f
        private set

    fun echantillon(valeurs: FloatArray) {
        if (valeurs.size < 2) return

        // Le capteur parle dans le repère de l'appareil, pas dans celui de
        // l'écran : en paysage, ses deux axes sont échangés.
        val brutX = valeurs[0]
        val brutY = valeurs[1]
        var x = brutX
        var y = brutY
        when (rotationEcran()) {
            Surface.ROTATION_90 -> { x = -brutY; y = brutX }
            Surface.ROTATION_180 -> { x = -brutX; y = -brutY }
            Surface.ROTATION_270 -> { x = brutY; y = -brutX }
        }

        // Le z ne dépend pas de la rotation de l'écran : il reste la normale.
        val z = if (valeurs.size > 2) valeurs[2] else 0f
        val angle = atan2(y, z)

        if (!cale) {
            reposX = x
            reposAngle = angle
            cale = true
        }

        val g = SensorManager.GRAVITY_EARTH
        val ecart = demiTour(angle - reposAngle)
        val cibleRoulis = ((x - reposX) / g).coerceIn(-1f, 1f)
        val cibleTangage = (ecart / TANGAGE_PLEIN).coerceIn(-1f, 1f)
        roulis += (cibleRoulis - roulis) * lissage
        tangage += (cibleTangage - tangage) * lissage
        reposX += (x - reposX) * Inclinaison.RAPPEL
        reposAngle = demiTour(reposAngle + ecart * Inclinaison.RAPPEL)
    }

    /** Ramène un angle dans ]-π, π], pour qu'un téléphone retourné ne saute pas d'un tour. */
    private fun demiTour(a: Float): Float {
        val pi = PI.toFloat()
        var r = a
        while (r > pi) r -= 2f * pi
        while (r <= -pi) r += 2f * pi
        return r
    }

    private companion object {
        /**
         * L'écart de tangage, en radians, qui porte la carte au bout de son
         * débattement : quarante-cinq degrés. Dix degrés de poignet donnent
         * ainsi un peu plus de quatre degrés de carte.
         */
        val TANGAGE_PLEIN = (PI / 4).toFloat()
    }

    /** Le repos est perdu avec le capteur : la carte reprendra la posture du moment. */
    fun oublier() {
        cale = false
        roulis = 0f
        tangage = 0f
    }

    @Suppress("DEPRECATION")
    private fun rotationEcran(): Int {
        val fenetres = vue.context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        return fenetres?.defaultDisplay?.rotation ?: Surface.ROTATION_0
    }
}
