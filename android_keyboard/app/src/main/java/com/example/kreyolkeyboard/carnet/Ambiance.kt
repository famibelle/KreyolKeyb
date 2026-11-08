package com.example.kreyolkeyboard.carnet

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
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

    /**
     * Où regarde l'œil, de -1 à 1 sur chaque axe : ce qui décale les plans du
     * lieu ([Paysages.GLISSEMENT]). Ce qui se tient sur un plan glisse avec lui.
     */
    open fun regarder(x: Float, y: Float) = Unit

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
         * Les oiseaux de l'été n'en sont plus (2026-11-02) : sur la carte en
         * plans, ils sont posés sur le carton des nuages et voyagent avec lui
         * (voir [Motif]) ; le rendu à plat les garde peints ([Habillage]).
         */
        fun pour(souvenir: Souvenir?, zone: RectF, graine: Int, nuit: Boolean = false,
                 oiseaux: Boolean = false, lieu: Lieu? = null, heure: Heure? = null): Ambiance? {
            // Les bêtes peintes du lieu bougent quand le lieu est rendu en
            // plans, c'est-à-dire sans elles : jamais un jour de fête, où il
            // reste à plat et les garde peintes.
            val betes = if (oiseaux && lieu != null && heure != null && souvenir?.fete == null &&
                Paysages.BETES[lieu] != null) BetesDuLieu(zone, lieu, heure) else null
            val volants = lieu?.let { Faune.de(it, zone, graine, nuit) }
            // Les lucioles de la forêt la nuit ne s'ajoutent pas à celles que
            // le Nouvel An ou le carême de nuit apportent déjà : un seul essaim.
            val lucioles = souvenir?.fete == Fete.NOUVEL_AN ||
                (souvenir?.fete == null && souvenir?.saison == Saison.CAREME && nuit)
            val essaim = if (lucioles && volants is Lucioles) null else volants
            val faune = when {
                betes == null -> essaim
                essaim == null -> betes
                else -> Ensemble(betes, essaim)
            }
            val fete = when (souvenir?.fete) {
                // Jamais de feu d'artifice : une nuit calme et ses lucioles.
                Fete.NOUVEL_AN -> Lucioles(zone, graine)
                Fete.CARNAVAL -> Tournoyants(zone, graine, Tournoyants.CONFETTIS)
                // Les flambeaux de la veillée : des braises qui montent, rien qui éclate.
                Fete.VINGT_SEPT_MAI -> Braises(zone, graine)
                Fete.TOUSSAINT -> Lumieres(zone, graine)
                Fete.CHANTE_NWEL -> Lumieres(zone, graine)
                null -> null
            }
            // Un jour de fête de l'hivernage, la carte ouverte reste à plat
            // mais il y pleut quand même : l'averse tombe ici, devant les
            // lumières, au lieu d'être peinte (voir Habillage.dessus).
            val temps = when {
                fete != null && oiseaux && souvenir?.saison == Saison.HIVERNAGE -> Ensemble(Pluie(zone, graine), fete)
                fete != null -> fete
                else -> when (souvenir?.saison) {
                    Saison.HIVERNAGE -> Pluie(zone, graine)
                    Saison.CAREME -> {
                        val air = if (nuit) Lucioles(zone, graine) else Tournoyants(zone, graine, Tournoyants.AIGRETTES)
                        // Un lieu qui a sa faune volante la fait voler à la
                        // place des oiseaux génériques : deux vols se
                        // gêneraient. Les bêtes posées ne comptent pas.
                        // En pleine nuit (très rare) les garde-bœufs dorment.
                        if (oiseaux && volants == null && heure?.nuit != true) Ensemble(Oiseaux(zone, graine), air) else air
                    }
                    null -> null
                }
            }
            // La nuit, les étoiles de la carte ouverte scintillent, fête ou
            // non : le ciel peint pour elle n'en porte pas (Paysages.rendu).
            val ciel = if (oiseaux && heure?.nuit == true) Etoiles(zone, graine) else null
            // L'eau des douches de Sofaïa et de la chute du Carbet tombe quand
            // le lieu est rendu en plans.
            val enPlans = oiseaux && souvenir?.fete == null && Paysages.PAPIER
            val eau = when {
                enPlans && lieu == Lieu.SOFAIA -> Douches(zone, graine)
                enPlans && lieu == Lieu.CHUTES_CARBET -> Cascade(zone, graine)
                enPlans && lieu == Lieu.SAUT_LEZARDE -> Cascade(zone, graine, Paysages.CHUTE_LEZARDE)
                enPlans && lieu == Lieu.CASCADE_ECREVISSES -> Cascade(zone, graine, Paysages.CHUTE_ECREVISSES)
                enPlans && lieu == Lieu.RAVINE_CHAUDE -> Vapeurs(zone, graine)
                enPlans && lieu == Lieu.BAINS_JAUNES -> Vapeurs(zone, graine, Paysages.VAPEURS_BAINS, plan = 3,
                    nappe = floatArrayOf(68f, 30f, 46f, 10f), force = 0.6f)
                enPlans && lieu == Lieu.POINTE_CHATEAUX -> Vagues(zone, graine)
                enPlans && lieu == Lieu.DESIRADE -> Ressac(zone, graine, phare = heure?.allume == true)
                enPlans && lieu == Lieu.BOUILLANTE -> Reflets(zone, graine, nuit = heure?.nuit == true)
                enPlans && lieu == Lieu.PORTE_ENFER -> Reflets(zone, graine, nuit = heure?.nuit == true, eau = Paysages.EAU_PORTE_ENFER, eclatsDuJour = 26)
                enPlans && lieu == Lieu.PETITE_TERRE -> Reflets(zone, graine, nuit = heure?.nuit == true, eau = Paysages.EAU_PETITE_TERRE, eclatsDuJour = 22)
                enPlans && lieu == Lieu.GUEULE_GRAND_GOUFFRE -> Reflets(zone, graine, nuit = heure?.nuit == true, eau = Paysages.EAU_GOUFFRE, eclatsDuJour = 30, plan = 2)
                enPlans && lieu == Lieu.GRANDE_VIGIE -> Reflets(zone, graine, nuit = heure?.nuit == true, eau = Paysages.EAU_VIGIE, eclatsDuJour = 26)
                enPlans && lieu == Lieu.ILET_GOSIER -> Reflets(zone, graine, nuit = heure?.nuit == true, eau = Paysages.EAU_GOSIER, eclatsDuJour = 26)
                enPlans && lieu == Lieu.GRAND_CUL_DE_SAC -> Reflets(zone, graine, nuit = heure?.nuit == true, eau = Paysages.EAU_CUL_DE_SAC, eclatsDuJour = 20)
                enPlans && lieu == Lieu.MEMORIAL_ACTE -> Reflets(zone, graine, nuit = heure?.nuit == true, eau = Paysages.EAU_ACTE, eclatsDuJour = 22, plan = 4)
                enPlans && lieu == Lieu.ILET_MACOU -> Reflets(zone, graine, nuit = heure?.nuit == true, eau = Paysages.EAU_MACOU, eclatsDuJour = 8)
                enPlans && lieu == Lieu.MORNE_A_L_EAU && heure?.allume == true -> Bougies(zone, graine)
                else -> null
            }
            return listOfNotNull(ciel, eau, faune, temps).let {
                when (it.size) {
                    0 -> null
                    1 -> it[0]
                    else -> Ensemble(*it.toTypedArray())
                }
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
 *
 * Depuis le chantier gouache (2026-11-02), des flocons de papier : une petite
 * ombre, le liseré du carton kraft, puis le disque blanc (skill
 * `direction-artistique` : ce qui est matière devient papier peint).
 */
private class Neige(zone: RectF, graine: Int) :
    Chute(zone, graine, FLOCONS, 5f, 16f, 1.5f, 4.5f, 0.55f, 1.35f) {

    private val r = FloatArray(n) { 0.55f + profondeur[it] * 1.05f }
    private val alpha = IntArray(n) { (150 + profondeur[it] * 105).toInt() }

    override fun dessinerUn(c: Canvas, p: Paint, i: Int, fx: Float, fy: Float) {
        // L'ombre, puis le liseré de kraft sous le flocon : sans un liseré, la
        // neige disparaissait sur les ciels d'hiver clairs du matin et de
        // l'après-midi, blanc sur blanc.
        p.color = OMBRE_PAPIER
        p.alpha = alpha[i] / 4
        c.drawCircle(fx + 0.25f, fy + 0.35f, r[i] + 0.3f, p)
        p.color = KRAFT
        p.alpha = alpha[i]
        c.drawCircle(fx, fy, r[i] + 0.3f, p)
        p.color = 0xFFFFFFFF.toInt()
        p.alpha = alpha[i]
        c.drawCircle(fx, fy, r[i], p)
    }

    private companion object {
        const val FLOCONS = 46
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
 *
 * Depuis le chantier gouache (2026-11-02, maquette `automne.html`), ce sont
 * des pièces de **papier peint** ([Style.papier]) : une petite ombre, le
 * papier kraft découpé, la gouache aux couleurs vives posée un peu en
 * retrait du bord, puis la nervure ; le revers est d'un ton plus sombre. Les
 * aigrettes, trop fines pour un bord de papier, gardent leur dessin, avec
 * leur ombre et un trait de kraft.
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
        val forme: () -> android.graphics.Path,
        /** Une pièce de papier découpé : un bord de kraft autour de la gouache. */
        val papier: Boolean = true
    ) {
        /** Deux images par teinte : la face, puis le revers, plus sombre. */
        internal val sprites = arrayOfNulls<android.graphics.Bitmap>(teintes.size * 2)
        internal val chemin by lazy { forme() }
        /** Le même, décalé là où tombe son ombre. */
        internal val ombre by lazy { forme().apply { offset(0.18f, 0.28f) } }
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
        val revers = retourne < 0f
        val image = if (c.isHardwareAccelerated) sprite(teinte[i], revers) else null
        p.alpha = alpha[i]
        if (image != null) {
            p.isFilterBitmap = true
            c.drawBitmap(image, null, style.cadre, p)
            p.isFilterBitmap = false
        } else {
            peindre(c, p, style.teintes[teinte[i]], alpha[i], revers)
        }
        c.restore()
    }

    private fun peindre(c: Canvas, p: Paint, couleur: Int, a: Int, revers: Boolean) {
        p.style = Paint.Style.FILL
        // L'ombre de la pièce sur ce qu'il y a derrière.
        p.color = OMBRE_PAPIER
        p.alpha = a / 4
        c.drawPath(style.ombre, p)
        val gouache = if (revers) Heure.melange(couleur, 0xFF000000.toInt(), 0.18f) else couleur
        if (style.papier) {
            // Le papier kraft, puis la gouache, un peu en retrait du bord.
            p.color = KRAFT
            p.alpha = a
            c.drawPath(style.chemin, p)
            c.save()
            c.scale(0.86f, 0.9f)
            p.color = gouache
            p.alpha = a
            c.drawPath(style.chemin, p)
            c.restore()
        } else {
            p.color = gouache
            p.alpha = a
            c.drawPath(style.chemin, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = 0.05f
            p.color = KRAFT
            p.alpha = a
            c.drawPath(style.chemin, p)
        }
        if (style.tige) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = 0.08f
            p.color = NERVURE
            p.alpha = a * 140 / 255
            c.drawLine(0f, -0.85f, 0f, 1.15f, p)
        }
        p.style = Paint.Style.FILL
    }

    /** La petite image de la teinte [k], face ou [revers], faite au premier besoin et partagée. */
    private fun sprite(k: Int, revers: Boolean): android.graphics.Bitmap? {
        val rang = k * 2 + (if (revers) 1 else 0)
        style.sprites[rang]?.let { return it }
        return try {
            val cadre = style.cadre
            val l = 48
            val h = (l * (cadre.bottom - cadre.top) / (cadre.right - cadre.left)).toInt().coerceIn(16, 96)
            val b = android.graphics.Bitmap.createBitmap(l, h, android.graphics.Bitmap.Config.ARGB_8888)
            val c = Canvas(b)
            c.scale(l / (cadre.right - cadre.left), h / (cadre.bottom - cadre.top))
            c.translate(-cadre.left, -cadre.top)
            peindre(c, Paint(Paint.ANTI_ALIAS_FLAG), style.teintes[k], 255, revers)
            style.sprites[rang] = b
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

        /** Les feuilles d'automne, aux couleurs vives de la maquette `automne.html`. */
        val FEUILLES = Style(
            "feuilles", 16, 4f, 10f, 5f, 11f, 3.4f, 6.6f,
            intArrayOf(
                0xFFE8632A.toInt(), // orange vif
                0xFFF2A33A.toInt(), // safran
                0xFFC8402F.toInt(), // rouge
                0xFFD98A2B.toInt(), // ocre
                0xFFF6C445.toInt()  // jaune
            ),
            0xFF4E2E14.toInt(), cadre(-0.85f, -1.1f, 1.05f, 1.4f), true, ::feuille
        )

        val PETALES = Style(
            "pétales", 22, 3f, 8f, 4f, 9f, 1.8f, 3.4f,
            intArrayOf(0xFFF8BBD0.toInt(), 0xFFFFFFFF.toInt(), 0xFFF48FB1.toInt(), 0xFFFCE4EC.toInt()),
            0xFFAD5C7A.toInt(), cadre(-0.95f, -1.05f, 1.15f, 1.35f), false, ::petale
        )

        /** Des aigrettes qui planent : presque sans poids, elles flânent. */
        val AIGRETTES = Style(
            "aigrettes", 12, 1.5f, 4f, 6f, 13f, 3f, 5f,
            intArrayOf(0xFFFFFFFF.toInt(), 0xFFFFFDE7.toInt()),
            0xFF6D6D6D.toInt(), cadre(-1f, -1.05f, 1.2f, 1.35f), false, ::aigrette, papier = false
        )

        val CONFETTIS = Style(
            "confettis", 30, 6f, 14f, 3f, 7f, 1.7f, 2.9f,
            intArrayOf(
                0xFFED2939.toInt(), 0xFF00A1DE.toInt(), 0xFFFFD54F.toInt(),
                0xFF66BB6A.toInt(), 0xFFAB47BC.toInt(), 0xFFFFFFFF.toInt()
            ),
            0xFF333333.toInt(), cadre(-0.5f, -1.05f, 0.7f, 1.35f), false, ::confetti
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
 * Les hérons garde-bœufs d'un ciel de carême, blancs, qui rentrent au dortoir
 * en file lâche : ils traversent la fenêtre tous dans le même sens, d'un
 * battement lent et continu, sans planer. C'est le vol de cet oiseau, et
 * c'est l'oiseau blanc qu'on voit traverser le ciel le soir en Guadeloupe.
 * Jusqu'au 2026-10-19 c'étaient des oiseaux sans espèce qui faisaient du
 * surplace, ce qu'aucun oiseau ne fait sauf le colibri (propriétaire).
 *
 * Quand la file est sortie, une autre entre de l'autre côté après une
 * pause. La pesanteur et les secousses ne les touchent pas.
 */
private class Oiseaux(zone: RectF, graine: Int) : Ambiance(zone, graine) {

    // Un pixel de la maquette, en unités de carte : la boîte de 100 de large
    // couvre la fenêtre, et la maquette en comptait 4,8 pixels par unité.
    private val px = largeur / 100f / 4.8f
    private val n = 5
    private val sens = if (hasard() < 0.5f) 1f else -1f
    // La file : chaque oiseau en retrait et un peu plus bas que le précédent.
    private val retrait = FloatArray(n) { it * (9f + hasard() * 5f) * px }
    private val ecart = FloatArray(n) { (it * 2.2f + (hasard() - 0.5f) * 3f) * px }
    private val t = FloatArray(n) { (3.4f + hasard() * 1.2f) * px }
    private val phase = FloatArray(n) { hasard() * 6.28f }
    private val vitesse = (5.5f + hasard() * 1.5f) * largeur / 100f
    private val haut = zone.top + hauteur * (0.1f + hasard() * 0.15f)
    private var tete = 0f
    private val trait = 1.3f * px
    private val chemin = android.graphics.Path()
    private var temps = 0f

    private val longueur: Float get() = retrait[n - 1] + 10f * px

    init {
        tete = hasard() * largeur
    }

    override fun avancer(dt: Float) {
        temps += dt
        tete += vitesse * dt
        // La file entière est sortie : une autre entre, après une pause d'une demi-largeur.
        if (tete - longueur > largeur * 1.5f) tete = -largeur * 0.1f
    }

    override fun dessiner(c: Canvas, p: Paint) {
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = trait
        p.strokeCap = Paint.Cap.ROUND
        p.color = COULEUR
        for (i in 0 until n) {
            val avance = tete - retrait[i]
            val x = if (sens > 0f) zone.left + avance else zone.right - avance
            val y = haut + ecart[i] + sin(temps * 0.6f + phase[i]) * 0.6f * px
            // Un battement lent et continu, deux et demi par seconde, ample.
            val aile = 0.7f + 0.5f * sin(temps * 2.5f * 2f * PI.toFloat() + phase[i])
            val ti = t[i]
            chemin.rewind()
            chemin.moveTo(x - ti, y - ti * (1.2f - aile))
            chemin.quadTo(x - ti * 0.4f, y - ti * aile, x, y)
            chemin.quadTo(x + ti * 0.4f, y - ti * aile, x + ti, y - ti * (1.2f - aile))
            c.drawPath(chemin, p)
        }
        p.style = Paint.Style.FILL
    }

    private companion object {
        const val COULEUR = 0xE6FFFFFF.toInt()
    }
}

/** Plusieurs ambiances l'une sur l'autre, la première dessous. */
/**
 * Les étoiles d'une nuit de carte, qui scintillent : chacune à sa place dans
 * le ciel peint ([Paysages.ETOILES]), chacune à son rythme, et la lumière
 * qui vacille plus qu'elle ne s'éteint, comme à travers l'air chaud des
 * tropiques. Elles glissent avec le plan du fond quand la carte penche.
 */
private class Etoiles(zone: RectF, graine: Int) : Ambiance(zone, graine) {
    private val cadre = Paysages.cadrage(largeur, hauteur)
    private val n = Paysages.ETOILES.size / 4
    private val cadence = FloatArray(n) { 0.6f + hasard() * 1.6f }
    private val phase = FloatArray(n) { hasard() * 2f * PI.toFloat() }
    // Quelques-unes seulement vacillent fort ; les autres respirent à peine.
    private val ampleur = FloatArray(n) { if (hasard() < 0.3f) 0.75f else 0.35f }
    private var temps = 0f
    private var regardX = 0f
    private var regardY = 0f

    override fun regarder(x: Float, y: Float) {
        regardX = x
        regardY = y
    }

    override fun avancer(dt: Float) {
        temps += dt
    }

    override fun dessiner(c: Canvas, p: Paint) {
        val e = cadre[0]
        val e0 = Paysages.ETOILES
        c.save()
        c.translate(zone.left + cadre[1] + Paysages.GLISSEMENT[0] * largeur * regardX,
            zone.top + cadre[2] + Paysages.GLISSEMENT[0] * hauteur * regardY)
        c.scale(e, e)
        p.style = Paint.Style.FILL
        p.shader = null
        for (i in 0 until n) {
            // Deux sinus de cadences différentes : un scintillement, pas un battement régulier.
            val s = 0.5f + 0.32f * sin(temps * cadence[i] * 2f * PI.toFloat() + phase[i]) +
                0.18f * sin(temps * cadence[i] * 5.3f + phase[i] * 1.7f)
            val eclat = e0[i * 4 + 3] * (1f - ampleur[i] * (1f - s))
            p.color = Paysages.COULEUR_ETOILE
            p.alpha = (eclat.coerceIn(0f, 1f) * 255f).toInt()
            c.drawCircle(e0[i * 4], e0[i * 4 + 1], e0[i * 4 + 2], p)
        }
        c.restore()
    }
}

private class Ensemble(private vararg val parts: Ambiance) : Ambiance(RectF(), 0) {
    override fun orienter(basX: Float, basY: Float) = parts.forEach { it.orienter(basX, basY) }
    override fun secouer(force: Float) = parts.forEach { it.secouer(force) }
    override fun regarder(x: Float, y: Float) = parts.forEach { it.regarder(x, y) }
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
 * - **les lucioles** les remplacent la nuit en forêt, lentes, leur lumière
 *   qui s'allume et s'éteint. Ce furent des molosses, des chauves-souris en
 *   zigzag, jusqu'à ce que le propriétaire y voie des cordes d'Halloween
 *   (2026-10-20) : ne pas les remettre.
 *
 * Les lieux de terre ouverte (Marie-Galante, Morne-à-l'Eau, la place de la
 * Victoire) n'en ont pas : leur saison suffit.
 */
internal object Faune {
    private val MER = setOf(Lieu.GRANDE_ANSE, Lieu.LES_SAINTES, Lieu.POINTE_CHATEAUX,
        Lieu.DESIRADE, Lieu.BOUILLANTE, Lieu.ILET_MACOU, Lieu.GRANDE_VIGIE, Lieu.PORTE_ENFER,
        Lieu.PETITE_TERRE, Lieu.ILET_GOSIER, Lieu.GRAND_CUL_DE_SAC, Lieu.GUEULE_GRAND_GOUFFRE,
        Lieu.FORT_DELGRES, Lieu.MEMORIAL_ACTE)
    private val FORET = setOf(Lieu.SOUFRIERE, Lieu.CHUTES_CARBET, Lieu.SOFAIA,
        Lieu.RAVINE_CHAUDE, Lieu.ROCHES_GRAVEES, Lieu.FIGUIER_MAUDIT, Lieu.GRAND_ETANG,
        Lieu.BAINS_JAUNES, Lieu.CASCADE_ECREVISSES, Lieu.SAUT_LEZARDE, Lieu.TRAVERSEE,
        Lieu.GRIVELIERE, Lieu.MATOUBA)

    fun de(lieu: Lieu, zone: RectF, graine: Int, nuit: Boolean): Ambiance? = when {
        lieu in MER && !nuit -> Fregates(zone, graine)
        lieu in FORET && !nuit -> Colibri(zone, graine)
        lieu in FORET -> Lucioles(zone, graine)
        else -> null
    }
}

/**
 * L'eau chaude qui tombe des douches de Sofaïa : sur chaque filet peint
 * ([Paysages.DOUCHES]), des gouttes claires qui accélèrent en tombant, et à
 * leur pied des ronds qui s'élargissent et s'effacent sur l'eau sombre. Le
 * bassin est sur le plan de devant : l'eau glisse avec lui quand on penche.
 */
private class Douches(zone: RectF, graine: Int) : Ambiance(zone, graine) {
    private val cadre = Paysages.cadrage(largeur, hauteur)
    private val n = Paysages.DOUCHES.size
    private val periode = FloatArray(n) { 0.55f + hasard() * 0.25f }
    private val phase = FloatArray(n) { hasard() }
    private var temps = 0f
    private var regardX = 0f
    private var regardY = 0f

    override fun regarder(x: Float, y: Float) {
        regardX = x
        regardY = y
    }

    override fun avancer(dt: Float) {
        temps += dt
    }

    override fun dessiner(c: Canvas, p: Paint) {
        val e = cadre[0]
        c.save()
        c.translate(zone.left + cadre[1] + Paysages.GLISSEMENT[4] * largeur * regardX,
            zone.top + cadre[2] + Paysages.GLISSEMENT[4] * hauteur * regardY)
        c.scale(e, e)
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        val haut = Paysages.DOUCHE_HAUT
        Paysages.DOUCHES.forEachIndexed { i, (x, bas) ->
            val chute = bas - haut
            // Trois gouttes par filet, également espacées dans le cycle.
            for (k in 0 until 3) {
                val t = ((temps / periode[i] + phase[i] + k / 3f) % 1f)
                val y = haut + chute * t * t
                val l = 0.6f + 1.6f * t
                p.strokeWidth = 0.42f
                p.color = 0xE6FFFFFF.toInt()
                c.drawLine(x, (y - l).coerceAtLeast(haut), x, y, p)
            }
            // Deux ronds par filet, décalés d'un demi-cycle.
            for (k in 0 until 2) {
                val t = ((temps / (periode[i] * 1.6f) + phase[i] + k / 2f) % 1f)
                val r = 0.5f + 2.2f * t
                p.strokeWidth = 0.22f
                p.color = (((1f - t) * 170f).toInt() shl 24) or 0xDDEEEA
                c.drawOval(x - r, bas + 0.5f - r * 0.32f, x + r, bas + 0.5f + r * 0.32f, p)
            }
        }
        p.style = Paint.Style.FILL
        c.restore()
    }
}

/**
 * L'eau de la chute du Carbet ([Paysages.CHUTE_CARBET]) ou du Saut de la
 * Lézarde ([Paysages.CHUTE_LEZARDE]) : des traînées
 * blanches qui dévalent le rideau peint en accélérant et s'écartent un peu
 * vers le pied, où l'écume bouillonne en bouffées qui gonflent et
 * s'effacent. La chute est sur le plan du milieu et glisse avec lui.
 */
private class Cascade(zone: RectF, graine: Int, private val eau: FloatArray = Paysages.CHUTE_CARBET) : Ambiance(zone, graine) {
    private val cadre = Paysages.cadrage(largeur, hauteur)
    private val n = 22
    private val voie = FloatArray(n) { hasard() - 0.5f }
    private val periode = FloatArray(n) { 0.7f + hasard() * 0.5f }
    private val phase = FloatArray(n) { hasard() }
    private val epaisseur = FloatArray(n) { 0.3f + hasard() * 0.35f }
    private val bouffees = 6
    private val decalage = FloatArray(bouffees) { hasard() - 0.5f }
    private val rythme = FloatArray(bouffees) { 0.9f + hasard() * 0.6f }
    private var temps = 0f
    private var regardX = 0f
    private var regardY = 0f

    override fun regarder(x: Float, y: Float) {
        regardX = x
        regardY = y
    }

    override fun avancer(dt: Float) {
        temps += dt
    }

    override fun dessiner(c: Canvas, p: Paint) {
        val (x, haut, bas, w) = eau
        val e = cadre[0]
        c.save()
        c.translate(zone.left + cadre[1] + Paysages.GLISSEMENT[3] * largeur * regardX,
            zone.top + cadre[2] + Paysages.GLISSEMENT[3] * hauteur * regardY)
        c.scale(e, e)
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        val chute = bas - haut
        for (i in 0 until n) {
            val t = (temps / periode[i] + phase[i]) % 1f
            val v = t * t * 0.6f + t * 0.4f
            val y = haut + chute * v
            val l = 1.5f + 3.5f * v
            // Dans le rideau peint, qui va de 0,7 à 1,1 fois la largeur.
            val ecart = w * (0.7f + 0.4f * v)
            val fx = x + voie[i] * ecart * 0.6f
            // Fondu à l'entrée (sous la brume) et au pied (dans l'écume).
            val alpha = (minOf(t / 0.15f, (1f - t) / 0.2f, 1f) * 220f).toInt().coerceIn(0, 255)
            // Sur un rideau blanc, une traînée blanche ne se voit pas : deux
            // sur trois sont d'un bleu d'eau, la troisième d'un blanc d'écume.
            p.strokeWidth = epaisseur[i]
            p.color = (alpha shl 24) or (if (i % 3 == 0) 0xFFFFFF else 0x7DB8B2)
            c.drawLine(fx, (y - l).coerceAtLeast(haut), fx, y, p)
        }
        p.style = Paint.Style.FILL
        for (j in 0 until bouffees) {
            val t = (temps / rythme[j] + j / bouffees.toFloat()) % 1f
            val r = w * (0.25f + 0.4f * t)
            p.color = (((1f - t) * 200f).toInt() shl 24) or 0xFFFFFF
            c.drawCircle(x + decalage[j] * w * 1.1f, bas - 0.4f - t * 1.6f, r, p)
        }
        c.restore()
    }
}

/**
 * La vapeur du bassin de Ravine Chaude ([Paysages.VAPEURS_RAVINE]), ou plus
 * légère celle des Bains Jaunes ([Paysages.VAPEURS_BAINS]) : par-dessus
 * chaque fumée peinte, des bouffées pâles montent lentement en grossissant,
 * ondulent un peu et s'effacent ; et au ras de l'eau, des nappes glissent
 * d'un bord à l'autre du bassin. Les nappes comptent le plus : le dessin du
 * mot, devant, cache presque toujours le haut des fumées, jamais l'eau sous
 * lui. Doux exprès : une eau tiède qui fume, pas une marmite.
 */
private class Vapeurs(
    zone: RectF, graine: Int,
    private val sources: Array<FloatArray> = Paysages.VAPEURS_RAVINE,
    private val plan: Int = 4,
    private val nappe: FloatArray = floatArrayOf(30f, 36f, 57.2f, 4f),
    private val force: Float = 1f
) : Ambiance(zone, graine) {
    private val cadre = Paysages.cadrage(largeur, hauteur)
    private val parSource = 5
    private val periode = FloatArray(sources.size * parSource) { 3.2f + hasard() * 1.6f }
    private val onde = FloatArray(periode.size) { hasard() * 6.28f }
    private val nappes = 6
    private val vitesse = FloatArray(nappes) { (0.9f + hasard() * 0.9f) * (if (hasard() < 0.5f) -1f else 1f) }
    private val depart = FloatArray(nappes) { hasard() }
    private val ligne = FloatArray(nappes) { hasard() }
    private var temps = 0f
    private var regardX = 0f
    private var regardY = 0f

    override fun regarder(x: Float, y: Float) {
        regardX = x
        regardY = y
    }

    override fun avancer(dt: Float) {
        temps += dt
    }

    override fun dessiner(c: Canvas, p: Paint) {
        val e = cadre[0]
        c.save()
        c.translate(zone.left + cadre[1] + Paysages.GLISSEMENT[plan] * largeur * regardX,
            zone.top + cadre[2] + Paysages.GLISSEMENT[plan] * hauteur * regardY)
        c.scale(e, e)
        p.style = Paint.Style.FILL
        sources.forEachIndexed { s, (x, pied, haut, w, derive) ->
            for (k in 0 until parSource) {
                val i = s * parSource + k
                val t = (temps / periode[i] + k / parSource.toFloat()) % 1f
                val y = pied - (pied - haut) * 1.3f * t
                val bx = x + derive * t * t + sin(temps * 1.1f + onde[i]) * 1.1f * t
                val r = w * (0.5f + 1.3f * t)
                p.color = ((sin(PI.toFloat() * t) * 150f * force).toInt() shl 24) or 0xFFFFFF
                c.drawCircle(bx, y, r, p)
            }
        }
        // Les nappes, au ras de l'eau du bassin ([nappe] : x, largeur, y, hauteur).
        for (k in 0 until nappes) {
            val u = ((depart[k] + temps * vitesse[k] / 40f) % 1f + 1f) % 1f
            val nx = nappe[0] + nappe[1] * u
            val ny = nappe[2] + ligne[k] * nappe[3] - sin(PI.toFloat() * u) * 1.2f
            val alpha = (sin(PI.toFloat() * u) * 130f * force).toInt()
            p.color = (alpha shl 24) or 0xFFFFFF
            val rx = 4f + 2.5f * ligne[k]
            c.drawOval(nx - rx, ny - 0.9f, nx + rx, ny + 0.9f, p)
        }
        c.restore()
    }
}

/**
 * La mer de la Pointe des Châteaux : l'écume qui jaillit par à-coups le long
 * du rivage ([Paysages.RIVAGE_CHATEAUX], plan du milieu), les embruns
 * arrachés à la crête de la grosse vague ([Paysages.VAGUE_CHATEAUX], plan
 * de devant), et des lignes d'écume qui roulent du bas vers le rivage en
 * rapetissant avec la distance.
 */
private class Vagues(zone: RectF, graine: Int) : Ambiance(zone, graine) {
    private val cadre = Paysages.cadrage(largeur, hauteur)
    private val jets = floatArrayOf(6f, 18f, 31f, 38f, 45f, 52f, 64f, 78f, 90f)
    private val rythmeJet = FloatArray(jets.size) { 2.2f + hasard() * 1.4f }
    private val phaseJet = FloatArray(jets.size) { hasard() }
    private val nEmbruns = 12
    private val placeEmbrun = FloatArray(nEmbruns) { hasard() }
    private val rythmeEmbrun = FloatArray(nEmbruns) { 1.1f + hasard() * 0.9f }
    private val nRouleaux = 8
    private val xRouleau = FloatArray(nRouleaux) { 4f + hasard() * 92f }
    private val rythmeRouleau = FloatArray(nRouleaux) { 6f + hasard() * 4f }
    private val phaseRouleau = FloatArray(nRouleaux) { hasard() }
    private val longueur = FloatArray(nRouleaux) { 2.5f + hasard() * 3f }
    private var temps = 0f
    private var regardX = 0f
    private var regardY = 0f

    override fun regarder(x: Float, y: Float) {
        regardX = x
        regardY = y
    }

    override fun avancer(dt: Float) {
        temps += dt
    }

    private fun blanc(t: Float, force: Float) = ((t.coerceIn(0f, 1f) * force).toInt() shl 24) or 0xFFFFFF

    override fun dessiner(c: Canvas, p: Paint) {
        val e = cadre[0]
        p.style = Paint.Style.FILL
        // Le plan du milieu : le rivage.
        c.save()
        c.translate(zone.left + cadre[1] + Paysages.GLISSEMENT[3] * largeur * regardX,
            zone.top + cadre[2] + Paysages.GLISSEMENT[3] * hauteur * regardY)
        c.scale(e, e)
        val rive = Paysages.RIVAGE_CHATEAUX
        jets.forEachIndexed { i, x ->
            val t = (temps / rythmeJet[i] + phaseJet[i]) % 1f
            // La mer frappe plus fort sous la pointe, comme sur la photo.
            val h = if (x in 30f..56f) 4.5f else 2.2f
            for (j in 0 until 4) {
                p.color = blanc(1f - t, 210f)
                c.drawCircle(x + (j - 1.5f) * 1.3f * t, rive - sin(PI.toFloat() * t) * h * (1f - abs(j - 1.5f) * 0.25f),
                    0.6f + 1.4f * t, p)
            }
        }
        c.restore()
        // Le plan de devant : la grosse vague et les rouleaux.
        c.save()
        c.translate(zone.left + cadre[1] + Paysages.GLISSEMENT[4] * largeur * regardX,
            zone.top + cadre[2] + Paysages.GLISSEMENT[4] * hauteur * regardY)
        c.scale(e, e)
        val (c0, cy0, c1, cy1) = Paysages.VAGUE_CHATEAUX
        for (k in 0 until nEmbruns) {
            val t = (temps / rythmeEmbrun[k] + k / nEmbruns.toFloat()) % 1f
            val u = placeEmbrun[k]
            val x0 = c0 + (c1 - c0) * u; val y0 = cy0 + (cy1 - cy0) * u
            // Le vent rabat les embruns en arrière de la vague qui déferle vers la gauche.
            p.color = blanc(1f - t, 230f)
            c.drawCircle(x0 + 3.5f * t, y0 - sin(PI.toFloat() * t * 0.85f) * 3f, 0.35f + 0.7f * t, p)
        }
        for (k in 0 until nRouleaux) {
            val t = (temps / rythmeRouleau[k] + phaseRouleau[k]) % 1f
            val y = 61f - 18f * t
            val loin = 0.45f + 0.55f * (y - 41f) / 20f
            val rx = longueur[k] * loin
            p.color = blanc(sin(PI.toFloat() * t), 160f)
            c.drawOval(xRouleau[k] - rx, y - 0.3f * loin, xRouleau[k] + rx, y + 0.3f * loin, p)
        }
        c.restore()
    }
}

/**
 * Le ressac de la Désirade : des lignes d'écume qui naissent au large
 * ([Paysages.RESSAC_DESIRADE], plan du loin), roulent vers le rivage en
 * s'allongeant et s'effacent ; et des gerbes qui jaillissent par à-coups au
 * pied du morne ([Paysages.GERBES_DESIRADE], plan du milieu). Doux exprès :
 * vu de loin, le ressac respire, il ne gronde pas.
 *
 * Le soir et la nuit ([phare]), le faisceau du phare tourne depuis la
 * lanterne ([Paysages.PHARE_DESIRADE]). Vu de côté, il s'allonge vers la
 * gauche puis rapetisse, éclate en lueur quand il passe face à nous, et
 * repart plus pâle, de dos. Il balaie le ciel au-dessus du dessin du mot.
 */
private class Ressac(zone: RectF, graine: Int, private val phare: Boolean) : Ambiance(zone, graine) {
    private val cadre = Paysages.cadrage(largeur, hauteur)
    private val nLignes = 7
    private val xLigne = FloatArray(nLignes) { hasard() }
    private val rythmeLigne = FloatArray(nLignes) { 5f + hasard() * 3f }
    private val phaseLigne = FloatArray(nLignes) { hasard() }
    private val longueur = FloatArray(nLignes) { 3f + hasard() * 4f }
    private val rythmeGerbe = FloatArray(Paysages.GERBES_DESIRADE.size) { 2.6f + hasard() * 1.6f }
    private val phaseGerbe = FloatArray(Paysages.GERBES_DESIRADE.size) { hasard() }
    private var temps = 0f
    private var regardX = 0f
    private var regardY = 0f

    override fun regarder(x: Float, y: Float) {
        regardX = x
        regardY = y
    }

    override fun avancer(dt: Float) {
        temps += dt
    }

    private fun blanc(t: Float, force: Float) = ((t.coerceIn(0f, 1f) * force).toInt() shl 24) or 0xFFFFFF

    private fun suivre(c: Canvas, plan: Int) {
        val e = cadre[0]
        c.save()
        c.translate(zone.left + cadre[1] + Paysages.GLISSEMENT[plan] * largeur * regardX,
            zone.top + cadre[2] + Paysages.GLISSEMENT[plan] * hauteur * regardY)
        c.scale(e, e)
    }

    override fun dessiner(c: Canvas, p: Paint) {
        p.style = Paint.Style.FILL
        val (x0, x1, large, rivage) = Paysages.RESSAC_DESIRADE
        suivre(c, 2)
        for (k in 0 until nLignes) {
            val t = (temps / rythmeLigne[k] + phaseLigne[k]) % 1f
            val y = large + 2f + (rivage - large - 2.6f) * t
            val pres = 0.5f + 0.5f * t
            val x = x0 + 4f + (x1 - x0 - 12f) * xLigne[k]
            val rx = longueur[k] * pres * (1f + 0.6f * t)
            p.color = blanc(sin(PI.toFloat() * t), 190f)
            c.drawOval(x - rx, y - 0.32f * pres, x + rx, y + 0.32f * pres, p)
        }
        c.restore()
        suivre(c, 3)
        Paysages.GERBES_DESIRADE.forEachIndexed { i, (x, y) ->
            val t = (temps / rythmeGerbe[i] + phaseGerbe[i]) % 1f
            for (j in 0 until 4) {
                p.color = blanc(1f - t, 200f)
                c.drawCircle(x + (j - 1.5f) * 1.1f * t, y - sin(PI.toFloat() * t) * 2.4f * (1f - abs(j - 1.5f) * 0.25f),
                    0.5f + 1.1f * t, p)
            }
        }
        if (phare) faisceau(c, p)
        c.restore()
    }

    private val cone = Path()

    private fun faisceau(c: Canvas, p: Paint) {
        val (lx, ly) = Paysages.PHARE_DESIRADE
        val a = 2f * PI.toFloat() * (temps / TOUR_PHARE % 1f)
        val face = sin(a).coerceAtLeast(0f)
        val longueur = 92f * abs(cos(a))
        val force = 0.3f + 0.7f * face
        if (longueur > 1.5f) {
            val sens = if (cos(a) < 0f) -1f else 1f
            val bout = lx + sens * longueur
            val demi = 0.7f + 0.055f * longueur
            cone.rewind()
            cone.moveTo(lx, ly - 0.7f)
            cone.lineTo(bout, ly - demi)
            cone.lineTo(bout, ly + demi)
            cone.lineTo(lx, ly + 0.7f)
            cone.close()
            p.color = 0xFFFFFFFF.toInt()
            p.shader = LinearGradient(lx, ly, bout, ly, ((force * 150f).toInt() shl 24) or 0xFFF2C4, 0x00FFF2C4,
                Shader.TileMode.CLAMP)
            c.drawPath(cone, p)
            p.shader = null
        }
        // L'éclat, quand le faisceau passe face à nous.
        val eclat = face * face * face * face
        p.color = ((40f + eclat * 170f).toInt() shl 24) or 0xFFF2C4
        c.drawCircle(lx, ly, 1.6f + eclat * 3.4f, p)
    }

    private companion object {
        /** Un tour du faisceau, en secondes. */
        const val TOUR_PHARE = 9f
    }
}

/**
 * L'eau calme de l'anse de Bouillante ([Paysages.REFLETS_BOUILLANTE], plan du
 * milieu), ou celle de la mangrove de l'Îlet Macou ([Paysages.EAU_MACOU],
 * avec moins d'éclats) ou celle de la baie du Mémorial ACTe ([Paysages.EAU_ACTE],
 * plan de devant) : des éclats de soleil qui s'allument et s'éteignent, plus serrés
 * du côté du soleil, à gauche, comme sur la photo ; et de longues rides qui
 * glissent lentement. La nuit, les éclats sont rares et pâles.
 */
private class Reflets(
    zone: RectF, graine: Int, nuit: Boolean,
    private val eau: FloatArray = Paysages.REFLETS_BOUILLANTE, eclatsDuJour: Int = 34, private val plan: Int = 3
) : Ambiance(zone, graine) {
    private val cadre = Paysages.cadrage(largeur, hauteur)
    private val nEclats = if (nuit) eclatsDuJour / 3 else eclatsDuJour
    private val force = if (nuit) 110f else 230f
    private val xEclat = FloatArray(nEclats) { val u = hasard(); u * u }
    private val yEclat = FloatArray(nEclats) { hasard() }
    private val rythme = FloatArray(nEclats) { 1.2f + hasard() * 1.8f }
    private val phase = FloatArray(nEclats) { hasard() }
    private val nRides = 6
    private val yRide = FloatArray(nRides) { hasard() }
    private val vitesse = FloatArray(nRides) { 1.2f + hasard() * 1.2f }
    private val depart = FloatArray(nRides) { hasard() }
    private var temps = 0f
    private var regardX = 0f
    private var regardY = 0f

    override fun regarder(x: Float, y: Float) {
        regardX = x
        regardY = y
    }

    override fun avancer(dt: Float) {
        temps += dt
    }

    override fun dessiner(c: Canvas, p: Paint) {
        val e = cadre[0]
        val (x0, x1, y0, y1) = eau
        c.save()
        c.translate(zone.left + cadre[1] + Paysages.GLISSEMENT[plan] * largeur * regardX,
            zone.top + cadre[2] + Paysages.GLISSEMENT[plan] * hauteur * regardY)
        c.scale(e, e)
        p.style = Paint.Style.FILL
        for (k in 0 until nRides) {
            val u = ((depart[k] + temps * vitesse[k] / 60f) % 1f + 1f) % 1f
            val y = y0 + 1f + (y1 - y0 - 2f) * yRide[k]
            val pres = (y - y0) / (y1 - y0)
            val x = x0 + (x1 - x0 + 10f) * u - 5f
            val rx = 4f + 5f * pres
            p.color = ((sin(PI.toFloat() * u) * 70f).toInt() shl 24) or 0xFFFFFF
            c.drawOval(x - rx, y - 0.18f - 0.12f * pres, x + rx, y + 0.18f + 0.12f * pres, p)
        }
        for (k in 0 until nEclats) {
            val t = (temps / rythme[k] + phase[k]) % 1f
            val vif = sin(PI.toFloat() * t).let { it * it }
            val y = y0 + (y1 - y0) * yEclat[k]
            val pres = (y - y0) / (y1 - y0)
            val x = x0 + (x1 - x0) * xEclat[k]
            val rx = (0.5f + 0.9f * pres) * (0.6f + 0.4f * vif)
            p.color = ((vif * force).toInt() shl 24) or 0xFFFFFF
            c.drawOval(x - rx, y - 0.2f - 0.1f * pres, x + rx, y + 0.2f + 0.1f * pres, p)
        }
        c.restore()
    }
}

/**
 * Les bougies du cimetière de Morne-à-l'Eau, le soir et la nuit
 * ([Paysages.BOUGIES_MORNE]) : chaque flamme vacille à son rythme, s'étire
 * et penche un peu, et sa lueur chaude respire autour d'elle. Chacune suit
 * le plan qui la porte quand la carte penche.
 */
private class Bougies(zone: RectF, graine: Int) : Ambiance(zone, graine) {
    private val cadre = Paysages.cadrage(largeur, hauteur)
    private val bougies = Paysages.BOUGIES_MORNE
    private val phase = FloatArray(bougies.size) { hasard() * 6.28f }
    private val vitesse = FloatArray(bougies.size) { 5f + hasard() * 4f }
    private val flamme = android.graphics.Path()
    private var temps = 0f
    private var regardX = 0f
    private var regardY = 0f

    override fun regarder(x: Float, y: Float) {
        regardX = x
        regardY = y
    }

    override fun avancer(dt: Float) {
        temps += dt
    }

    override fun dessiner(c: Canvas, p: Paint) {
        val e = cadre[0]
        p.style = Paint.Style.FILL
        for (plan in 0..2) {
            val g = Paysages.GLISSEMENT[plan + 2]
            c.save()
            c.translate(zone.left + cadre[1] + g * largeur * regardX, zone.top + cadre[2] + g * hauteur * regardY)
            c.scale(e, e)
            bougies.forEachIndexed { i, (x, y, pl) ->
                if (pl.toInt() != plan) return@forEachIndexed
                val t = temps * vitesse[i] + phase[i]
                val vacille = 0.75f + 0.15f * sin(t) + 0.1f * sin(t * 2.3f + 1f)
                val penche = 0.25f * sin(t * 0.7f)
                p.color = ((38f * vacille).toInt() shl 24) or 0xFF9A3C
                c.drawCircle(x, y - 1.6f, 3.4f + 0.8f * vacille, p)
                p.color = ((80f * vacille).toInt() shl 24) or 0xFFB050
                c.drawCircle(x, y - 1.5f, 1.5f + 0.6f * vacille, p)
                val haut = 0.9f + 1.3f * vacille
                flamme.rewind()
                flamme.moveTo(x + penche, y - 0.8f - haut)
                flamme.quadTo(x + 0.42f, y - 1.1f, x, y - 0.8f)
                flamme.quadTo(x - 0.42f, y - 1.1f, x + penche, y - 0.8f - haut)
                flamme.close()
                p.color = 0xFFFFB238.toInt()
                c.drawPath(flamme, p)
                p.color = 0xFFFFF0B0.toInt()
                c.drawCircle(x, y - 1.05f, 0.17f, p)
            }
            c.restore()
        }
    }
}

/**
 * Les bêtes peintes d'un lieu, rendues vivantes sur la carte ouverte : la
 * même place et les mêmes couleurs que sur la vignette, et le mouvement de
 * chaque espèce (voir `Peintre.bete` dans [Paysages]). Chacune glisse avec le
 * plan qui la porte quand la carte penche, pour ne pas patiner sur son sol.
 */
private class BetesDuLieu(zone: RectF, lieu: Lieu, heure: Heure) : Ambiance(zone, 0) {
    private val atelier = Paysages.Atelier(heure)
    private val betes = Paysages.BETES[lieu].orEmpty()
    private val cadre = Paysages.cadrage(largeur, hauteur)
    private var temps = 0f
    private var regardX = 0f
    private var regardY = 0f

    override fun regarder(x: Float, y: Float) {
        regardX = x
        regardY = y
    }

    override fun avancer(dt: Float) {
        temps += dt
    }

    override fun dessiner(c: Canvas, p: Paint) {
        val e = cadre[0]
        c.save()
        c.translate(zone.left + cadre[1], zone.top + cadre[2])
        c.scale(e, e)
        betes.forEachIndexed { i, b ->
            c.save()
            c.translate(Paysages.GLISSEMENT[b.plan + 2] * largeur * regardX / e,
                Paysages.GLISSEMENT[b.plan + 2] * hauteur * regardY / e)
            atelier.peindre(c, b, temps, i)
            c.restore()
        }
        c.restore()
    }
}

/**
 * Deux ou trois frégates en V arrondi qui tournent en larges cercles dans les
 * courants chauds, comme les vraies, en dérivant lentement avec le vent.
 * Elles planent presque tout le temps et donnent de loin en loin deux coups
 * d'ailes lents, chacune à son rythme (propriétaire, 2026-10-19). Le cercle
 * est vu de biais, donc aplati, et l'oiseau penche vers l'intérieur.
 */
private class Fregates(zone: RectF, graine: Int) : Ambiance(zone, graine) {
    private val u = largeur / 100f
    private val n = 2 + (hasard() * 2f).toInt()
    private val cx = FloatArray(n) { zone.left + hasard() * largeur }
    private val cy = FloatArray(n) { zone.top + hauteur * (0.12f + hasard() * 0.22f) }
    private val rayon = FloatArray(n) { (9f + hasard() * 7f) * u }
    private val angle = FloatArray(n) { hasard() * 6.28f }
    private val tour = FloatArray(n) { (0.22f + hasard() * 0.15f) * (if (hasard() < 0.5f) 1f else -1f) }
    private val t = FloatArray(n) { (4.5f + hasard() * 2.5f) * u }
    private val derive = (0.8f + hasard() * 0.8f) * u * (if (hasard() < 0.5f) 1f else -1f)
    private val phase = FloatArray(n) { hasard() * 6.28f }
    private val periode = FloatArray(n) { 5f + hasard() * 3f }
    private val chemin = android.graphics.Path()
    private var temps = 0f

    override fun avancer(dt: Float) {
        temps += dt
        for (i in 0 until n) {
            angle[i] += tour[i] * dt
            cx[i] += derive * dt
            val marge = rayon[i] + t[i] * 1.5f
            if (cx[i] > zone.right + marge) cx[i] = zone.left - marge
            if (cx[i] < zone.left - marge) cx[i] = zone.right + marge
        }
    }

    /**
     * L'ouverture des ailes : 1 en vol plané, jusqu'à -0,55 au bas d'un coup
     * d'aile. Deux battements de 0,45 s par période, le reste plané.
     */
    private fun aile(i: Int): Float {
        val cycle = (temps + phase[i] * periode[i]) % periode[i]
        if (cycle > BATTEMENT) return 1f
        return 0.225f + 0.775f * cos(cycle / BATTEMENT * 2f * PI.toFloat() * 2f)
    }

    override fun dessiner(c: Canvas, p: Paint) {
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        p.color = COULEUR
        for (i in 0 until n) {
            // Sur son cercle, vu de biais ; l'aile intérieure plus basse.
            val xx = cx[i] + cos(angle[i]) * rayon[i]
            val yy = cy[i] + sin(angle[i]) * rayon[i] * 0.3f
            val roulis = 0.12f * (if (tour[i] > 0f) 1f else -1f) * (0.6f + 0.4f * sin(angle[i]))
            val ti = t[i]
            val k = aile(i)
            p.strokeWidth = ti * 0.11f
            chemin.rewind()
            chemin.moveTo(xx - ti, yy - ti * (0.35f * k + roulis))
            chemin.quadTo(xx - ti * 0.35f, yy - ti * 0.3f * k, xx, yy + ti * 0.1f)
            chemin.quadTo(xx + ti * 0.35f, yy - ti * 0.3f * k, xx + ti, yy - ti * (0.35f * k - roulis))
            c.drawPath(chemin, p)
        }
        p.style = Paint.Style.FILL
    }

    private companion object {
        const val COULEUR = 0xD91E1A22.toInt()
        const val BATTEMENT = 0.9f
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

/** Le papier kraft des pièces découpées (voir [Peintre.peint] dans [Paysages]). */
private const val KRAFT = 0xFFB08A5A.toInt()

/** L'ombre d'une pièce de papier : le bleu nuit des ombres de carton. */
private const val OMBRE_PAPIER = 0xFF0B1020.toInt()

/** La nervure d'une feuille de papier. */
private const val NERVURE = 0xFF5A3214.toInt()
