package com.example.kreyolkeyboard.carnet

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Ce qui bouge dans la fenêtre de la carte ouverte : le temps qu'il faisait
 * le jour où on l'a gagnée.
 *
 * Le paysage dit déjà la saison, immobile (voir [Habillage]) ; l'ambiance la
 * fait vivre. L'hiver d'abord, en prototype (2026-10-12) : une boule à neige.
 *
 * ## Où, et où jamais
 *
 * Sur une carte montrée seule, celle du lecteur ou de la pochette de fin de
 * jeu, dans la fenêtre, devant le paysage et derrière le métal. Jamais dans
 * la grille : trente-six cartes qui
 * neigent ensemble coûteraient le défilement, et c'est la même règle que
 * l'inclinaison, qui ne descend pas non plus dans la grille. Rien quand le
 * téléphone demande des animations réduites.
 *
 * ## Ce que ça coûte
 *
 * Quelques dizaines de disques par image. C'est devenu raisonnable avec les
 * couches fixes de la carte ([CarteOrnee]) : sans elles, chaque image de
 * neige aurait rejoué tout le cadre.
 *
 * Tout est en unités de carte, dans le repère de la fenêtre, et le hasard est
 * semé par le mot : une carte neige toujours de la même façon.
 */
abstract class Ambiance(protected val zone: RectF, graine: Int) {

    private var etat = graine xor 0x5DEECE6

    // Par les champs et non par width()/height() : les tests tournent sur la
    // JVM, où les méthodes de RectF ne rendent que zéro.
    protected val largeur: Float get() = zone.right - zone.left
    protected val hauteur: Float get() = zone.bottom - zone.top

    /** Un tirage dans [0, 1[, reproductible d'une ouverture à l'autre. */
    protected fun hasard(): Float {
        etat = etat * 1103515245 + 12345
        return ((etat ushr 8) and 0xFFFF) / 65536f
    }

    /**
     * Le vrai bas, dans le plan de l'écran, en unités de pesanteur : voir
     * [Posture.basX]. (0, 1) téléphone droit, (0, 0) posé à plat.
     */
    open fun orienter(basX: Float, basY: Float) = Unit

    /** Une secousse de force [force], dans ]0, 1]. */
    open fun secouer(force: Float) = Unit

    /** Fait avancer l'ambiance de [dt] secondes. */
    abstract fun avancer(dt: Float)

    /** La dessine, en unités de carte, le canevas déjà découpé à la fenêtre. */
    abstract fun dessiner(c: Canvas, p: Paint)

    companion object {
        /**
         * L'ambiance d'un souvenir, ou `null` sans souvenir. La fête l'emporte
         * sur la saison ; le carême dépend de l'heure de la carte : des
         * aigrettes le jour, des lucioles le soir et la nuit ([nuit], les deux
         * paliers hauts). L'hivernage pleut.
         *
         * [oiseaux] : les oiseaux de l'été volent ici plutôt que d'être peints
         * dans le paysage, ce que fait le rendu en plans de la carte ouverte
         * (voir [Habillage.appliquerPlans]). Le rendu à plat les garde peints.
         */
        fun pour(souvenir: Souvenir?, zone: RectF, graine: Int, nuit: Boolean = false,
                 oiseaux: Boolean = false, lieu: Lieu? = null): Ambiance? {
            val faune = lieu?.let { Faune.de(it, zone, graine, nuit) }
            val temps = when (souvenir?.fete) {
                // Jamais de feu d'artifice : une nuit calme et ses lucioles.
                Fete.NOUVEL_AN -> Lucioles(zone, graine)
                Fete.CARNAVAL -> Tournoyants(zone, graine, Tournoyants.CONFETTIS)
                // Les flambeaux de la veillée : des braises qui montent, rien qui éclate.
                Fete.VINGT_SEPT_MAI -> Braises(zone, graine)
                Fete.TOUSSAINT -> Lumieres(zone, graine)
                Fete.CHANTE_NWEL -> Lumieres(zone, graine)
                null -> when (souvenir?.saison) {
                    Saison.HIVERNAGE -> Pluie(zone, graine)
                    Saison.CAREME -> {
                        val air = if (nuit) Lucioles(zone, graine) else Tournoyants(zone, graine, Tournoyants.AIGRETTES)
                        // Un lieu qui a sa faune la fait voler à la place des
                        // oiseaux génériques : deux vols se gêneraient.
                        if (oiseaux && faune == null) Ensemble(Oiseaux(zone, graine, nuit), air) else air
                    }
                    null -> null
                }
            }
            return when {
                temps == null -> faune
                faune == null -> temps
                else -> Ensemble(faune, temps)
            }
        }
    }
}

/**
 * Ce qui tombe dans une boule à neige : vers le vrai bas, et soulevé par une
 * secousse. La neige et les feuilles en héritent ; chacune dit seulement sa
 * vitesse, son balancement et son dessin.
 *
 * ## Le vrai bas
 *
 * On tombe vers le sol, pas vers le bas de l'écran : téléphone penché, en
 * biais ; tête en bas, on remonte l'écran. C'est la pesanteur absolue et non
 * le repos relatif du reflet, si bien qu'une carte tenue penchée garde sa
 * chute penchée, comme une boule qu'on tiendrait de travers. Posé à plat, le
 * téléphone n'a plus de bas dans le plan de l'écran : tout descend alors
 * doucement vers le bas de la carte plutôt que de flotter figé.
 *
 * ## La secousse
 *
 * Un coup sec lance chaque élément à contre-pesanteur, un peu de travers, et
 * l'élan s'éteint en deux secondes environ : tout tourbillonne, remonte, puis
 * retombe. Les éléments proches partent plus fort que les lointains.
 *
 * ## La profondeur
 *
 * Chaque élément tire une profondeur, de 0 (loin) à 1 (près), qui fixe sa
 * taille, sa vitesse et son opacité : c'est ce décalage qui donne de la
 * profondeur à une fenêtre plate. Et chacun balance à sa propre cadence : à
 * une cadence commune, l'ensemble ondulait comme un rideau.
 */
private abstract class Chute(
    zone: RectF,
    graine: Int,
    protected val n: Int,
    vitesseMin: Float,
    vitesseMax: Float,
    ampleurMin: Float,
    ampleurMax: Float,
    cadenceMin: Float,
    cadenceMax: Float
) : Ambiance(zone, graine) {

    protected val x = FloatArray(n)
    protected val y = FloatArray(n)
    protected val profondeur = FloatArray(n)
    private val vitesse = FloatArray(n)
    private val phase = FloatArray(n)
    private val ampleur = FloatArray(n)
    private val cadence = FloatArray(n)
    private val elanX = FloatArray(n)
    private val elanY = FloatArray(n)
    protected var temps = 0f
        private set

    /**
     * La chute, lissée : direction et force ensemble (longueur 0,35 à plat,
     * 1 debout). Lisser le vecteur et non sa seule direction est ce qui
     * permet le demi-tour : une direction lissée puis renormalisée ne
     * passait jamais de (0, 1) à (0, -1), le téléphone retourné gardait sa
     * neige tombant vers le bas de l'écran.
     */
    private var chuteX = 0f
    private var chuteY = 1f
    private var cibleX = 0f
    private var cibleY = 1f

    /** La direction de chute, unitaire, pour le balancement et la secousse. */
    protected var dirX = 0f
        private set
    protected var dirY = 1f
        private set

    init {
        for (i in 0 until n) {
            val p = hasard()
            profondeur[i] = p
            vitesse[i] = vitesseMin + p * (vitesseMax - vitesseMin) * 0.85f + hasard() * (vitesseMax - vitesseMin) * 0.15f
            ampleur[i] = ampleurMin + hasard() * (ampleurMax - ampleurMin)
            cadence[i] = cadenceMin + hasard() * (cadenceMax - cadenceMin)
            phase[i] = hasard() * 2f * PI.toFloat()
            x[i] = zone.left + hasard() * largeur
            y[i] = zone.top + hasard() * hauteur
        }
    }

    override fun orienter(basX: Float, basY: Float) {
        val m = sqrt(basX * basX + basY * basY).coerceAtMost(1f)
        // À plat, un reste de chute vers le bas de la carte : rien ne doit
        // jamais flotter immobile.
        val vx = basX
        val vy = basY + PLANCHER * (1f - m)
        val l = sqrt(vx * vx + vy * vy).coerceAtLeast(1e-4f)
        val force = PLANCHER + (1f - PLANCHER) * m
        cibleX = vx / l * force
        cibleY = vy / l * force
    }

    override fun secouer(force: Float) {
        val f = force.coerceIn(0f, 1f)
        for (i in 0 until n) {
            // À contre-pesanteur, et de travers : un tourbillon, pas un bloc.
            val angle = (hasard() - 0.5f) * 2.2f
            val c = cos(angle)
            val s = sin(angle)
            val ux = -dirX * c + dirY * s
            val uy = -dirY * c - dirX * s
            val v = ELAN * f * (0.55f + 0.45f * profondeur[i]) * (0.6f + 0.4f * hasard())
            elanX[i] += ux * v
            elanY[i] += uy * v
            // Une secousse dure plusieurs échantillons : sans plafond, leurs
            // élans s'additionneraient en tempête.
            val e = sqrt(elanX[i] * elanX[i] + elanY[i] * elanY[i])
            if (e > ELAN * 1.2f) {
                elanX[i] *= ELAN * 1.2f / e
                elanY[i] *= ELAN * 1.2f / e
            }
        }
    }

    override fun avancer(dt: Float) {
        temps += dt
        val k = (dt * LISSAGE).coerceAtMost(1f)
        chuteX += (cibleX - chuteX) * k
        chuteY += (cibleY - chuteY) * k
        val l = sqrt(chuteX * chuteX + chuteY * chuteY)
        if (l > 1e-3f) {
            dirX = chuteX / l
            dirY = chuteY / l
        }
        val frein = exp(-dt * FREIN)
        val marge = 6f
        for (i in 0 until n) {
            x[i] += (chuteX * vitesse[i] + elanX[i]) * dt
            y[i] += (chuteY * vitesse[i] + elanY[i]) * dt
            elanX[i] *= frein
            elanY[i] *= frein
            // Sortie d'un bord, retour par le bord opposé, à une place neuve :
            // la chute peut maintenant aller dans tous les sens.
            when {
                y[i] > zone.bottom + marge -> { y[i] = zone.top - marge; x[i] = zone.left + hasard() * largeur }
                y[i] < zone.top - marge -> { y[i] = zone.bottom + marge; x[i] = zone.left + hasard() * largeur }
            }
            when {
                x[i] > zone.right + marge -> { x[i] = zone.left - marge; y[i] = zone.top + hasard() * hauteur }
                x[i] < zone.left - marge -> { x[i] = zone.right + marge; y[i] = zone.top + hasard() * hauteur }
            }
        }
    }

    /** Le balancement de l'élément [i] à l'instant, dans [-1, 1]. */
    protected fun oscillation(i: Int): Float = sin(temps * cadence[i] + phase[i])

    override fun dessiner(c: Canvas, p: Paint) {
        p.shader = null
        p.style = Paint.Style.FILL
        // Le balancement se fait en travers de la chute.
        val px = -dirY
        val py = dirX
        for (i in 0 until n) {
            val b = oscillation(i) * ampleur[i]
            dessinerUn(c, p, i, x[i] + px * b, y[i] + py * b)
        }
        p.alpha = 255
        p.style = Paint.Style.FILL
    }

    /** Dessine l'élément [i], déjà balancé, en ([fx], [fy]). */
    protected abstract fun dessinerUn(c: Canvas, p: Paint, i: Int, fx: Float, fy: Float)

    private companion object {
        /** La part de chute qui reste téléphone à plat. */
        const val PLANCHER = 0.35f

        /** Vers la nouvelle chute en un tiers de seconde environ : ça a du poids. */
        const val LISSAGE = 3f

        /** L'élan d'une secousse franche, en unités de carte par seconde. */
        const val ELAN = 140f

        /** L'extinction de l'élan : divisé par e en 0,6 s, éteint en deux secondes. */
        const val FREIN = 1.6f
    }
}

/**
 * La neige : des disques, trois profondeurs, lente exprès. Une neige pressée
 * se lit comme de la pluie.
 */
private class Neige(zone: RectF, graine: Int) :
    Chute(zone, graine, FLOCONS, 5f, 16f, 1.5f, 4.5f, 0.55f, 1.35f) {

    private val r = FloatArray(n) { 0.55f + profondeur[it] * 1.05f }
    private val alpha = IntArray(n) { (150 + profondeur[it] * 105).toInt() }

    override fun dessinerUn(c: Canvas, p: Paint, i: Int, fx: Float, fy: Float) {
        // Un liseré gris-bleu sous le flocon : sans lui, la neige disparaissait
        // sur les ciels d'hiver clairs du matin et de l'après-midi, blanc sur
        // blanc.
        p.color = LISERE
        p.alpha = alpha[i] / 3
        c.drawCircle(fx, fy, r[i] + 0.45f, p)
        p.color = 0xFFFFFFFF.toInt()
        p.alpha = alpha[i]
        c.drawCircle(fx, fy, r[i], p)
    }

    private companion object {
        const val FLOCONS = 46
        const val LISERE = 0xFF4A5A78.toInt()
    }
}

/**
 * L'averse de l'hivernage : des traits fins et rapides, alignés sur la chute.
 * Rapide exprès, à l'inverse de la neige : c'est la vitesse qui fait lire de
 * la pluie. Le trait suit le vrai bas, si bien qu'une carte penchée pleut en
 * biais.
 */
private class Pluie(zone: RectF, graine: Int) :
    Chute(zone, graine, GOUTTES, 70f, 120f, 0.2f, 0.6f, 0.5f, 1.0f) {

    private val longueur = FloatArray(n) { 2.2f + profondeur[it] * 3.2f }
    private val alpha = IntArray(n) { (70 + profondeur[it] * 110).toInt() }

    override fun dessinerUn(c: Canvas, p: Paint, i: Int, fx: Float, fy: Float) {
        p.style = Paint.Style.STROKE
        p.strokeWidth = 0.28f + profondeur[i] * 0.22f
        p.strokeCap = Paint.Cap.ROUND
        p.color = TEINTE
        p.alpha = alpha[i]
        c.drawLine(fx, fy, fx - dirX * longueur[i], fy - dirY * longueur[i], p)
        p.style = Paint.Style.FILL
    }

    private companion object {
        const val GOUTTES = 60
        const val TEINTE = 0xFFDCE6F2.toInt()
    }
}

/**
 * Ce qui tournoie en tombant : feuilles d'automne, pétales de printemps,
 * aigrettes du carême, confettis du Carnaval. Une même boule à neige, un
 * [Style] par saison.
 *
 * L'élément penche dans le sens où il glisse, et se retourne sur lui-même en
 * tombant (sa largeur passe par zéro), ce qui suffit à le faire lire en trois
 * dimensions. Un fin contour sombre le détache d'un paysage de même teinte.
 *
 * Chaque teinte est dessinée une fois dans une petite image, que la carte
 * graphique fait ensuite tourner : tracées comme des chemins, les feuilles
 * étaient redécoupées à chaque angle.
 */
private class Tournoyants(zone: RectF, graine: Int, private val style: Style) :
    Chute(
        zone, graine, style.nombre, style.vitesseMin, style.vitesseMax,
        style.ampleurMin, style.ampleurMax, 0.7f, 1.3f
    ) {

    /**
     * L'allure d'une saison. [forme] est dessinée dans [cadre], en unités
     * d'élément, pointe vers le haut ; [taille] est sa demi-longueur en
     * unités de carte, du plus lointain au plus proche.
     */
    class Style(
        val nom: String,
        val nombre: Int,
        val vitesseMin: Float,
        val vitesseMax: Float,
        val ampleurMin: Float,
        val ampleurMax: Float,
        val tailleMin: Float,
        val tailleMax: Float,
        val teintes: IntArray,
        val contour: Int,
        val cadre: RectF,
        val tige: Boolean,
        val forme: () -> android.graphics.Path
    ) {
        internal val sprites = arrayOfNulls<android.graphics.Bitmap>(teintes.size)
        internal val chemin by lazy { forme() }
    }

    private val taille = FloatArray(n) { style.tailleMin + profondeur[it] * (style.tailleMax - style.tailleMin) }
    private val teinte = IntArray(n)
    private val vrille = FloatArray(n)
    private val pente = FloatArray(n)
    private val alpha = IntArray(n) { (175 + profondeur[it] * 80).toInt() }

    init {
        for (i in 0 until n) {
            teinte[i] = (hasard() * style.teintes.size).toInt().coerceAtMost(style.teintes.size - 1)
            vrille[i] = 1.2f + hasard() * 2.2f
            pente[i] = hasard() * 360f
        }
    }

    override fun dessinerUn(c: Canvas, p: Paint, i: Int, fx: Float, fy: Float) {
        val o = oscillation(i)
        // Penché vers où il glisse, et autour de la direction de chute.
        val chute = Math.toDegrees(atan2(dirX.toDouble(), dirY.toDouble())).toFloat()
        val angle = pente[i] * 0.25f - chute + o * 38f
        val retourne = cos(temps * vrille[i] + pente[i])
        val t = taille[i]
        c.save()
        c.translate(fx, fy)
        c.rotate(angle)
        c.scale(t * (0.25f + 0.75f * abs(retourne)), t)
        val image = if (c.isHardwareAccelerated) sprite(teinte[i]) else null
        p.alpha = alpha[i]
        if (image != null) {
            p.isFilterBitmap = true
            c.drawBitmap(image, null, style.cadre, p)
            p.isFilterBitmap = false
        } else {
            peindre(c, p, style.teintes[teinte[i]], alpha[i])
        }
        c.restore()
    }

    private fun peindre(c: Canvas, p: Paint, couleur: Int, a: Int) {
        p.style = Paint.Style.FILL
        p.color = couleur
        p.alpha = a
        c.drawPath(style.chemin, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 0.08f
        p.color = style.contour
        p.alpha = a / 2
        c.drawPath(style.chemin, p)
        if (style.tige) c.drawLine(0f, -0.95f, 0f, 1.15f, p)
        p.style = Paint.Style.FILL
    }

    /** La petite image de la teinte [k], faite au premier besoin et partagée. */
    private fun sprite(k: Int): android.graphics.Bitmap? {
        style.sprites[k]?.let { return it }
        return try {
            val cadre = style.cadre
            val l = 48
            val h = (l * (cadre.bottom - cadre.top) / (cadre.right - cadre.left)).toInt().coerceIn(16, 96)
            val b = android.graphics.Bitmap.createBitmap(l, h, android.graphics.Bitmap.Config.ARGB_8888)
            val c = Canvas(b)
            c.scale(l / (cadre.right - cadre.left), h / (cadre.bottom - cadre.top))
            c.translate(-cadre.left, -cadre.top)
            peindre(c, Paint(Paint.ANTI_ALIAS_FLAG), style.teintes[k], 255)
            style.sprites[k] = b
            b
        } catch (e: Throwable) {
            null
        }
    }

    companion object {
        private fun cadre(l: Float, t: Float, r: Float, b: Float) =
            RectF().apply { left = l; top = t; right = r; bottom = b }

        /** Une feuille de hêtre, pointe en haut, d'une unité de demi-longueur. */
        private fun feuille() = android.graphics.Path().apply {
            moveTo(0f, -1f)
            cubicTo(0.75f, -0.55f, 0.7f, 0.55f, 0f, 1f)
            cubicTo(-0.7f, 0.55f, -0.75f, -0.55f, 0f, -1f)
            close()
        }

        /** Un pétale de cerisier : arrondi, et échancré à son bout. */
        private fun petale() = android.graphics.Path().apply {
            moveTo(0f, 1f)
            cubicTo(-0.9f, 0.4f, -0.8f, -0.8f, -0.25f, -1f)
            lineTo(0f, -0.75f)
            lineTo(0.25f, -1f)
            cubicTo(0.8f, -0.8f, 0.9f, 0.4f, 0f, 1f)
            close()
        }

        /** Une aigrette de pissenlit : une graine, un fil, une ombelle. */
        private fun aigrette() = android.graphics.Path().apply {
            // La graine, un losange arrondi ; le fil, un rectangle mince.
            moveTo(0f, 0.55f)
            cubicTo(0.16f, 0.65f, 0.16f, 0.9f, 0f, 1f)
            cubicTo(-0.16f, 0.9f, -0.16f, 0.65f, 0f, 0.55f)
            close()
            moveTo(-0.03f, -0.35f)
            lineTo(0.03f, -0.35f)
            lineTo(0.03f, 0.6f)
            lineTo(-0.03f, 0.6f)
            close()
            for (k in -3..3) {
                val a = k * 0.42f
                moveTo(0f, -0.35f)
                lineTo(sin(a) * 0.9f + 0.03f, -0.35f - cos(a) * 0.65f)
                lineTo(sin(a) * 0.9f - 0.03f, -0.35f - cos(a) * 0.65f)
                close()
            }
        }

        /**
         * Un confetti : un petit rectangle, tracé point par point. Ajouté
         * d'un bloc par `addRect`, il laissait l'image vide sur un Galaxy
         * A21s (2026-10-12) : les confettis (alors ceux de la Schueberfouer, côté LuxKeyb) étaient
         * invisibles. Toutes les formes d'ici sont donc tracées à la main.
         */
        private fun confetti() = android.graphics.Path().apply {
            moveTo(-0.45f, -1f)
            lineTo(0.45f, -1f)
            lineTo(0.45f, 1f)
            lineTo(-0.45f, 1f)
            close()
        }

        val FEUILLES = Style(
            "feuilles", 16, 4f, 10f, 5f, 11f, 3.4f, 6.6f,
            intArrayOf(
                0xFFB5481F.toInt(), // rouille
                0xFFE07B24.toInt(), // orange
                0xFFD9A03B.toInt(), // ocre
                0xFF8B5A2B.toInt(), // brun
                0xFF9E2B25.toInt()  // rouge sombre
            ),
            0xFF4E2E14.toInt(), cadre(-0.85f, -1.1f, 0.85f, 1.25f), true, ::feuille
        )

        val PETALES = Style(
            "pétales", 22, 3f, 8f, 4f, 9f, 1.8f, 3.4f,
            intArrayOf(0xFFF8BBD0.toInt(), 0xFFFFFFFF.toInt(), 0xFFF48FB1.toInt(), 0xFFFCE4EC.toInt()),
            0xFFAD5C7A.toInt(), cadre(-0.95f, -1.05f, 0.95f, 1.05f), false, ::petale
        )

        /** Des aigrettes qui planent : presque sans poids, elles flânent. */
        val AIGRETTES = Style(
            "aigrettes", 12, 1.5f, 4f, 6f, 13f, 3f, 5f,
            intArrayOf(0xFFFFFFFF.toInt(), 0xFFFFFDE7.toInt()),
            0xFF6D6D6D.toInt(), cadre(-1f, -1.05f, 1f, 1.05f), false, ::aigrette
        )

        val CONFETTIS = Style(
            "confettis", 30, 6f, 14f, 3f, 7f, 1.7f, 2.9f,
            intArrayOf(
                0xFFED2939.toInt(), 0xFF00A1DE.toInt(), 0xFFFFD54F.toInt(),
                0xFF66BB6A.toInt(), 0xFFAB47BC.toInt(), 0xFFFFFFFF.toInt()
            ),
            0xFF333333.toInt(), cadre(-0.5f, -1.05f, 0.5f, 1.05f), false, ::confetti
        )
    }
}

/**
 * Les lucioles d'un soir d'été : elles ne tombent pas, elles errent, chacune
 * sur sa courbe, et s'allument puis s'éteignent à leur rythme. La pesanteur
 * ne les tire que de loin ; une secousse les éparpille, puis elles reprennent
 * leur errance.
 */
private class Lucioles(zone: RectF, graine: Int) : Ambiance(zone, graine) {

    private val n = 14
    private val x = FloatArray(n) { zone.left + hasard() * largeur }
    private val y = FloatArray(n) { zone.top + hasard() * hauteur }
    private val cap = FloatArray(n) { hasard() * 2f * PI.toFloat() }
    private val virage = FloatArray(n) { (hasard() - 0.5f) * 1.6f }
    private val allure = FloatArray(n) { 3f + hasard() * 4f }
    private val pouls = FloatArray(n) { 0.6f + hasard() * 0.9f }
    private val phase = FloatArray(n) { hasard() * 6.28f }
    private val elanX = FloatArray(n)
    private val elanY = FloatArray(n)
    private var basX = 0f
    private var basY = 1f
    private var temps = 0f

    override fun orienter(basX: Float, basY: Float) {
        this.basX = basX
        this.basY = basY
    }

    override fun secouer(force: Float) {
        for (i in 0 until n) {
            val a = hasard() * 6.28f
            val v = 60f * force.coerceIn(0f, 1f) * (0.5f + 0.5f * hasard())
            elanX[i] = cos(a) * v
            elanY[i] = sin(a) * v
        }
    }

    override fun avancer(dt: Float) {
        temps += dt
        val frein = exp(-dt * 1.6f)
        for (i in 0 until n) {
            // Une courbe qui change de sens de temps en temps.
            cap[i] += (virage[i] + sin(temps * 0.4f + phase[i]) * 0.9f) * dt
            x[i] += (cos(cap[i]) * allure[i] + basX * 1.5f + elanX[i]) * dt
            y[i] += (sin(cap[i]) * allure[i] + basY * 1.5f + elanY[i]) * dt
            elanX[i] *= frein
            elanY[i] *= frein
            // Elles restent dans la fenêtre : au bord, demi-tour.
            if (x[i] < zone.left + 2f || x[i] > zone.right - 2f) {
                cap[i] = PI.toFloat() - cap[i]
                x[i] = x[i].coerceIn(zone.left + 2f, zone.right - 2f)
                elanX[i] = -elanX[i]
            }
            if (y[i] < zone.top + 2f || y[i] > zone.bottom - 2f) {
                cap[i] = -cap[i]
                y[i] = y[i].coerceIn(zone.top + 2f, zone.bottom - 2f)
                elanY[i] = -elanY[i]
            }
        }
    }

    override fun dessiner(c: Canvas, p: Paint) {
        p.shader = null
        p.style = Paint.Style.FILL
        for (i in 0 until n) {
            // Allumée une partie du temps seulement, et jamais tout à fait éteinte.
            val l = ((sin(temps * pouls[i] * 2f + phase[i]) + 0.35f) / 1.35f).coerceIn(0.08f, 1f)
            p.color = HALO
            p.alpha = (70 * l).toInt()
            c.drawCircle(x[i], y[i], 3.2f, p)
            p.color = COEUR
            p.alpha = (255 * l).toInt()
            c.drawCircle(x[i], y[i], 0.9f, p)
        }
        p.alpha = 255
    }

    private companion object {
        const val HALO = 0xFFD4E157.toInt()
        const val COEUR = 0xFFFFF9C4.toInt()
    }
}

/**
 * Les braises des flambeaux du 27 mai : elles montent du bas de la fenêtre, contre la
 * pesanteur, en vacillant, et pâlissent en montant. C'est la même boule à
 * neige, à l'envers : une vitesse négative fait remonter vers le haut vrai.
 */
private class Braises(zone: RectF, graine: Int) :
    Chute(zone, graine, 26, -16f, -7f, 1f, 3f, 1.5f, 3.2f) {

    private val r = FloatArray(n) { 0.45f + profondeur[it] * 0.75f }

    override fun dessinerUn(c: Canvas, p: Paint, i: Int, fx: Float, fy: Float) {
        // Plus pâle en haut de la fenêtre, et vacillante.
        val hauteurRelative = ((fy - zone.top) / hauteur).coerceIn(0f, 1f)
        val vacille = 0.7f + 0.3f * oscillation(i)
        val a = (255 * (0.25f + 0.75f * hauteurRelative) * vacille).toInt().coerceIn(0, 255)
        p.color = HALO
        p.alpha = a / 3
        c.drawCircle(fx, fy, r[i] * 2.4f, p)
        p.color = if (i % 3 == 0) JAUNE else ORANGE
        p.alpha = a
        c.drawCircle(fx, fy, r[i], p)
    }

    private companion object {
        const val HALO = 0xFFFF7043.toInt()
        const val ORANGE = 0xFFFFA726.toInt()
        const val JAUNE = 0xFFFFF176.toInt()
    }
}

/**
 * Des points dorés fixes qui scintillent : les bougies de la Toussaint, les
 * guirlandes des Chanté Nwèl.
 */
private class Lumieres(zone: RectF, graine: Int) : Ambiance(zone, graine) {

    private val n = 12
    private val x = FloatArray(n) { zone.left + 6f + hasard() * (largeur - 12f) }
    private val y = FloatArray(n) { zone.top + hauteur * (0.45f + hasard() * 0.5f) }
    private val pouls = FloatArray(n) { 1.2f + hasard() * 1.8f }
    private val phase = FloatArray(n) { hasard() * 6.28f }
    private var temps = 0f

    override fun avancer(dt: Float) {
        temps += dt
    }

    override fun dessiner(c: Canvas, p: Paint) {
        p.shader = null
        p.style = Paint.Style.FILL
        for (i in 0 until n) {
            val l = 0.45f + 0.55f * ((sin(temps * pouls[i] + phase[i]) + 1f) / 2f)
            p.color = HALO
            p.alpha = (90 * l).toInt()
            c.drawCircle(x[i], y[i], 2.6f, p)
            p.color = COEUR
            p.alpha = (255 * l).toInt()
            c.drawCircle(x[i], y[i], 0.8f, p)
        }
        p.alpha = 255
    }

    private companion object {
        const val HALO = 0xFFFFC107.toInt()
        const val COEUR = 0xFFFFF8E1.toInt()
    }
}

/**
 * Les oiseaux d'un ciel de carême, ceux que [Habillage] peint dans le paysage à
 * plat (37.4.4) : mêmes traits, même taille, même coin du ciel. Ils battent
 * lentement des ailes, chacun à son rythme, et planent sur place en dérivant
 * à peine. Ils ne traversent pas la fenêtre : on doit les voir vivre, pas
 * les voir partir. La pesanteur et les secousses ne les touchent pas.
 */
private class Oiseaux(zone: RectF, graine: Int, nuit: Boolean) : Ambiance(zone, graine) {

    // Un pixel de la maquette, en unités de carte : la boîte de 100 de large
    // couvre la fenêtre, et la maquette en comptait 4,8 pixels par unité.
    private val px = largeur / 100f / 4.8f
    private val n = 6
    private val x0 = FloatArray(n) { zone.left + largeur * (0.12f + hasard() * 0.5f) }
    private val y0 = FloatArray(n) { zone.top + hauteur * (0.08f + hasard() * 0.22f) }
    private val t = FloatArray(n) { (3f + hasard() * 3f) * px }
    private val battement = FloatArray(n) { 0.5f + hasard() * 0.4f }
    private val derive = FloatArray(n) { 0.15f + hasard() * 0.2f }
    private val phase = FloatArray(n) { hasard() * 6.28f }
    private val couleur = if (nuit) 0x99FFFFFF.toInt() else 0xB31E1A1E.toInt()
    private val trait = 1.3f * px
    private val chemin = android.graphics.Path()
    private var temps = 0f

    override fun avancer(dt: Float) {
        temps += dt
    }

    override fun dessiner(c: Canvas, p: Paint) {
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = trait
        p.strokeCap = Paint.Cap.ROUND
        p.color = couleur
        for (i in 0 until n) {
            // Une dérive de quelques pixels de maquette, en huit couché.
            val a = temps * derive[i] + phase[i]
            val x = x0[i] + sin(a) * 2.5f * px
            val y = y0[i] + sin(a * 2f) * 0.8f * px
            // L'aile monte et descend un peu autour de la pose peinte (0,7),
            // moins d'un battement par seconde : un oiseau qui plane.
            val aile = 0.7f + 0.15f * sin(temps * battement[i] * 2f * PI.toFloat() + phase[i])
            val ti = t[i]
            chemin.rewind()
            chemin.moveTo(x - ti, y - ti * (1.2f - aile))
            chemin.quadTo(x - ti * 0.4f, y - ti * aile, x, y)
            chemin.quadTo(x + ti * 0.4f, y - ti * aile, x + ti, y - ti * (1.2f - aile))
            c.drawPath(chemin, p)
        }
        p.style = Paint.Style.FILL
    }
}

/** Plusieurs ambiances l'une sur l'autre, la première dessous. */
private class Ensemble(private vararg val parts: Ambiance) : Ambiance(RectF(), 0) {
    override fun orienter(basX: Float, basY: Float) = parts.forEach { it.orienter(basX, basY) }
    override fun secouer(force: Float) = parts.forEach { it.secouer(force) }
    override fun avancer(dt: Float) = parts.forEach { it.avancer(dt) }
    override fun dessiner(c: Canvas, p: Paint) {
        for (a in parts) a.dessiner(c, p)
    }
}

/**
 * La faune d'un lieu, qui vit sur la carte ouverte quel que soit le jour où
 * elle a été gagnée. Les espèces viennent de la référence du propriétaire
 * (`horoscope-ia/lib/private/faune_guadeloupe_ref.md`, 2026-10-19) :
 *
 * - **la frégate superbe** plane au-dessus des lieux de mer, le jour ;
 * - **le colibri** butine les balisiers des lieux de forêt, le jour : c'est
 *   lui qui pollinise le balisier, et le dessin met les deux ensemble ;
 * - **la chauve-souris frugivore** les remplace la nuit, en forêt.
 *
 * Les lieux de terre ouverte (Marie-Galante, Morne-à-l'Eau, la place de la
 * Victoire) n'en ont pas : leur saison suffit.
 */
internal object Faune {
    private val MER = setOf(Lieu.GRANDE_ANSE, Lieu.LES_SAINTES, Lieu.POINTE_CHATEAUX,
        Lieu.DESIRADE, Lieu.BOUILLANTE, Lieu.ILET_MACOU)
    private val FORET = setOf(Lieu.SOUFRIERE, Lieu.CHUTES_CARBET, Lieu.SOFAIA,
        Lieu.RAVINE_CHAUDE, Lieu.ROCHES_GRAVEES, Lieu.FIGUIER_MAUDIT)

    fun de(lieu: Lieu, zone: RectF, graine: Int, nuit: Boolean): Ambiance? = when {
        lieu in MER && !nuit -> Fregates(zone, graine)
        lieu in FORET && !nuit -> Colibri(zone, graine)
        lieu in FORET -> ChauvesSouris(zone, graine)
        else -> null
    }
}

/**
 * Deux ou trois frégates qui planent sans un battement, ailes coudées et
 * queue fourchue, et traversent lentement le ciel en s'inclinant.
 */
private class Fregates(zone: RectF, graine: Int) : Ambiance(zone, graine) {
    private val u = largeur / 100f
    private val n = 2 + (hasard() * 2f).toInt()
    private val x = FloatArray(n) { zone.left + hasard() * largeur }
    private val y = FloatArray(n) { zone.top + hauteur * (0.08f + hasard() * 0.25f) }
    private val t = FloatArray(n) { (4.5f + hasard() * 2.5f) * u }
    private val vitesse = FloatArray(n) { (2.2f + hasard() * 1.6f) * u * (if (hasard() < 0.5f) 1f else -1f) }
    private val phase = FloatArray(n) { hasard() * 6.28f }
    private val chemin = android.graphics.Path()
    private var temps = 0f

    override fun avancer(dt: Float) {
        temps += dt
        for (i in 0 until n) {
            x[i] += vitesse[i] * dt
            val marge = t[i] * 1.5f
            if (x[i] > zone.right + marge) x[i] = zone.left - marge
            if (x[i] < zone.left - marge) x[i] = zone.right + marge
        }
    }

    override fun dessiner(c: Canvas, p: Paint) {
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        p.color = COULEUR
        for (i in 0 until n) {
            // Un lent roulis : une aile monte quand l'autre descend.
            val roulis = sin(temps * 0.5f + phase[i]) * 0.18f
            val yy = y[i] + sin(temps * 0.3f + phase[i]) * 1.2f * u
            val ti = t[i]
            p.strokeWidth = ti * 0.11f
            chemin.rewind()
            chemin.moveTo(x[i] - ti, yy - ti * (0.28f + roulis))
            chemin.lineTo(x[i] - ti * 0.42f, yy + ti * 0.06f)
            chemin.lineTo(x[i], yy - ti * 0.04f)
            chemin.lineTo(x[i] + ti * 0.42f, yy + ti * 0.06f)
            chemin.lineTo(x[i] + ti, yy - ti * (0.28f - roulis))
            chemin.moveTo(x[i], yy)
            chemin.lineTo(x[i] - ti * 0.1f, yy + ti * 0.42f)
            chemin.moveTo(x[i], yy)
            chemin.lineTo(x[i] + ti * 0.1f, yy + ti * 0.42f)
            c.drawPath(chemin, p)
        }
        p.style = Paint.Style.FILL
    }

    private companion object {
        const val COULEUR = 0xD91E1A22.toInt()
    }
}

/**
 * Un colibri qui butine : il file d'une fleur à l'autre en un éclair, puis
 * reste suspendu devant, ailes en battement flou. Les fleurs sont celles des
 * balisiers peints au premier plan, à gauche et à droite.
 */
private class Colibri(zone: RectF, graine: Int) : Ambiance(zone, graine) {
    private val u = largeur / 100f
    private val cibles = arrayOf(
        floatArrayOf(0.12f, 0.74f), floatArrayOf(0.86f, 0.74f), floatArrayOf(0.5f, 0.42f), floatArrayOf(0.3f, 0.6f)
    )
    private var de = 0
    private var vers = 1
    private var trajet = 1f
    private var attente = 1f + hasard()
    private var temps = 0f
    private var x = zone.left + cibles[0][0] * largeur
    private var y = zone.top + cibles[0][1] * hauteur
    private var regard = 1f

    override fun avancer(dt: Float) {
        temps += dt
        if (trajet < 1f) {
            trajet = (trajet + dt / 0.45f).coerceAtMost(1f)
            val e = 1f - (1f - trajet) * (1f - trajet)
            val ax = zone.left + cibles[de][0] * largeur; val ay = zone.top + cibles[de][1] * hauteur
            val bx = zone.left + cibles[vers][0] * largeur; val by = zone.top + cibles[vers][1] * hauteur
            x = ax + (bx - ax) * e
            y = ay + (by - ay) * e - sin(e * PI.toFloat()) * 6f * u
            regard = if (bx >= ax) 1f else -1f
        } else {
            attente -= dt
            if (attente <= 0f) {
                de = vers
                var suivant = (hasard() * cibles.size).toInt().coerceAtMost(cibles.size - 1)
                if (suivant == de) suivant = (suivant + 1) % cibles.size
                vers = suivant
                trajet = 0f
                attente = 1.2f + hasard() * 1.8f
            }
        }
    }

    override fun dessiner(c: Canvas, p: Paint) {
        p.shader = null
        p.style = Paint.Style.FILL
        // Le vol stationnaire : un tremblement de quelques dixièmes d'unité.
        val fx = x + sin(temps * 9f) * 0.25f * u
        val fy = y + sin(temps * 6.5f) * 0.35f * u
        val l = 1.6f * u
        // Les ailes : un voile qui bat trop vite pour être vu net.
        val battement = 0.55f + 0.45f * abs(sin(temps * 40f))
        p.color = AILE
        p.alpha = 120
        c.drawOval(fx - l * 0.5f, fy - l * 1.3f * battement, fx + l * 0.3f, fy, p)
        p.alpha = 255
        p.color = CORPS
        c.drawOval(fx - l * 0.75f, fy - l * 0.3f, fx + l * 0.75f, fy + l * 0.3f, p)
        c.drawCircle(fx + regard * l * 0.7f, fy - l * 0.25f, l * 0.3f, p)
        p.color = GORGE
        c.drawCircle(fx + regard * l * 0.55f, fy, l * 0.18f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = l * 0.12f
        p.color = CORPS
        c.drawLine(fx + regard * l * 0.95f, fy - l * 0.25f, fx + regard * l * 1.9f, fy - l * 0.05f, p)
        p.style = Paint.Style.FILL
    }

    private companion object {
        const val CORPS = 0xFF1F5A3C.toInt()
        const val GORGE = 0xFF8E2A6A.toInt()
        const val AILE = 0xFFE6F0EA.toInt()
    }
}

/**
 * Les chauves-souris frugivores de la nuit en forêt : un vol brisé, des
 * battements rapides, dans le haut de la fenêtre.
 */
private class ChauvesSouris(zone: RectF, graine: Int) : Ambiance(zone, graine) {
    private val u = largeur / 100f
    private val n = 3
    private val x = FloatArray(n) { zone.left + hasard() * largeur }
    private val y = FloatArray(n) { zone.top + hauteur * (0.1f + hasard() * 0.3f) }
    private val cap = FloatArray(n) { hasard() * 6.28f }
    private val phase = FloatArray(n) { hasard() * 6.28f }
    private val chemin = android.graphics.Path()
    private var temps = 0f

    override fun avancer(dt: Float) {
        temps += dt
        for (i in 0 until n) {
            cap[i] += sin(temps * 1.7f + phase[i]) * 2.4f * dt
            x[i] += cos(cap[i]) * 9f * u * dt
            y[i] += sin(cap[i]) * 5f * u * dt
            if (x[i] < zone.left + 2f * u || x[i] > zone.right - 2f * u) cap[i] = PI.toFloat() - cap[i]
            if (y[i] < zone.top + 2f * u || y[i] > zone.top + hauteur * 0.5f) cap[i] = -cap[i]
            x[i] = x[i].coerceIn(zone.left + 2f * u, zone.right - 2f * u)
            y[i] = y[i].coerceIn(zone.top + 2f * u, zone.top + hauteur * 0.5f)
        }
    }

    override fun dessiner(c: Canvas, p: Paint) {
        p.shader = null
        p.style = Paint.Style.FILL
        p.color = COULEUR
        for (i in 0 until n) {
            val t = 2.4f * u
            val aile = sin(temps * 14f + phase[i]) * 0.6f
            chemin.rewind()
            chemin.moveTo(x[i], y[i])
            chemin.lineTo(x[i] - t, y[i] - t * aile)
            chemin.lineTo(x[i] - t * 0.6f, y[i] + t * 0.15f)
            chemin.lineTo(x[i] - t * 0.3f, y[i] + t * 0.05f)
            chemin.lineTo(x[i], y[i] + t * 0.25f)
            chemin.lineTo(x[i] + t * 0.3f, y[i] + t * 0.05f)
            chemin.lineTo(x[i] + t * 0.6f, y[i] + t * 0.15f)
            chemin.lineTo(x[i] + t, y[i] - t * aile)
            chemin.close()
            c.drawPath(chemin, p)
        }
    }

    private companion object {
        const val COULEUR = 0xE6080A12.toInt()
    }
}
