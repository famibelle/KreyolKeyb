package com.example.kreyolkeyboard.carnet

import com.example.kreyolkeyboard.R
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.LruCache
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import com.example.kreyolkeyboard.KeyFeedback
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Un casier ouvert : ses cartes sortent de la pile et s'étalent en éventail.
 *
 * Il remplace une grille de vignettes sur fond blanc, qui faisait quitter l'objet
 * au moment même où l'on s'y intéressait : on touchait un tiroir en bois et l'on
 * arrivait dans un écran de réglages. Ici les cartes **viennent** de la fente
 * touchée, et y retournent quand on referme. Le geste se lit comme celui d'une
 * main qui tire un paquet d'un casier et l'ouvre pour le regarder.
 *
 * ## Ce que fait le doigt
 *
 * - Glisser à l'horizontale fait tourner l'éventail, carte par carte, et il se
 *   cale sur la plus proche quand on lâche. C'est ce qui permet à un casier de
 *   deux cents cartes de tenir dans le même geste qu'un casier de quatre : on ne
 *   dessine jamais qu'une douzaine de cartes autour du centre.
 * - Toucher la carte du centre l'ouvre en grand. Toucher une carte de côté
 *   l'amène au centre d'abord : ouvrir directement une carte à demi cachée
 *   serait ouvrir une carte qu'on n'a pas lue.
 * - Toucher ailleurs range les cartes. Le bouton retour aussi, via l'appelant.
 *
 * ## Ce qu'il ne fait pas
 *
 * Il ne lance aucune révision, pour la raison que donne [BoiteLeitner] : un
 * casier se consulte, il ne se choisit pas.
 *
 * Il peut s'ouvrir **avant** que les cartes soient prêtes. Leur contenu demande
 * le dictionnaire, dont le premier chargement prend plusieurs secondes ; un
 * toucher qui n'ouvrirait rien pendant ce temps se lirait comme un toucher raté
 * et serait répété. Le voile et le titre arrivent donc tout de suite, avec
 * « Préparation des cartes… », et l'éventail se déploie dès [poserCartes].
 *
 * [depart] est la face avant de la pile, dans les coordonnées de cette vue.
 */
internal class EventailCasier(
    context: Context,
    private val titre: String,
    private val sousTitre: String,
    private val messageVide: String,
    private val depart: RectF,
    private val aujourdHui: Int,
    private val rendre: (ContenuCarte, Int) -> Bitmap,
    private val surCarte: (ContenuCarte) -> Unit,
    private val surFermeture: () -> Unit
) : View(context) {

    private val densite = resources.displayMetrics.density
    private fun px(v: Float) = v * densite
    private val reduites = Pochette.animationsReduites(context)

    private var cartes: List<ContenuCarte>? = null

    /** Les cartes ne sont pas encore là : l'éventail attend [poserCartes]. */
    val enAttente: Boolean get() = cartes == null

    private var voile = 0f
    private var deploiement = 0f
    private var decalage = 0f
    private var fermeture = false

    private var animVoile: ValueAnimator? = null
    private var animDeploiement: ValueAnimator? = null
    private var animDecalage: ValueAnimator? = null

    /**
     * Les rectos à la taille de l'éventail, rendus à la demande. Vingt-quatre
     * suffisent : on n'en voit jamais plus d'une douzaine à la fois, et le reste
     * du casier se rend quand le doigt l'amène.
     */
    private val images = LruCache<String, Bitmap>(24)

    private val pinceau = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pinceauImage = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val texteTitre = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        textSize = px(21f)
    }
    private val texteDoux = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = px(14f)
    }
    private val cadre = RectF()
    private val ombre = RectF()
    private val rebond = OvershootInterpolator(1.15f)

    private val seuil = ViewConfiguration.get(context).scaledTouchSlop
    private var x0 = 0f
    private var decalage0 = 0f
    private var glisse = false
    private var suivi: VelocityTracker? = null

    /** La carte au centre lors du dernier cran senti. */
    private var rangSenti = -1

    init {
        isClickable = true
        contentDescription = "$titre, $sousTitre"
    }

    // ---- la géométrie ------------------------------------------------------

    /**
     * Sur tablette, l'éventail prend la mesure de l'écran.
     *
     * Plafonnées à 170 dp et posées sur un arc de 2,4 cartes de rayon, les
     * cartes d'un écran de dix pouces faisaient une petite main au milieu d'un
     * grand vide, et celles de droite plongeaient sous l'aide puis sous le bord.
     * Ici la carte se règle sur la hauteur, l'arc sur la largeur (une courbe
     * douce d'un bord à l'autre), et l'écart entre deux cartes sur l'arc : une
     * demi-carte, si bien qu'une quinzaine se lisent à la fois sans se couvrir
     * plus que dans une main.
     */
    private val tablette = resources.configuration.smallestScreenWidthDp >= 600

    /**
     * Un téléphone couché : sous les onglets il reste à peine 200 dp de haut.
     * Réglée sur la seule largeur, la carte y faisait 170 dp et l'éventail
     * débordait : le bas des cartes sous le bord, le titre et le compteur
     * derrière elles, l'aide en travers. Ici les textes tiennent chacun sur une
     * ligne, en haut et en bas, l'arc est presque plat pour que les cartes des
     * bords ne descendent pas sur l'aide, et la carte prend la hauteur qui reste.
     */
    private val basse get() = !tablette && height < px(HAUTEUR_BASSE)

    /** Ce que la main laisse aux textes en vue basse : titre au-dessus, aide en dessous. */
    private val margeHaute get() = px(36f)
    private val margeBasse get() = px(26f)

    /** De combien les cartes du bord descendent sous celle du centre. */
    private val descenteBord: Float get() =
        rayonArc * (1f - cos(kotlin.math.asin(min(1f, width / 2f / rayonArc).toDouble())).toFloat())

    private val largeurCarte: Float get() = when {
        tablette -> minOf(height * 0.34f, width * 0.22f, px(280f))
        // La carte du centre est grossie de 12 % : c'est elle qui doit tenir.
        basse -> minOf(
            (height - margeHaute - margeBasse - descenteBord) / 1.16f, width * 0.22f, px(170f)
        ).coerceAtLeast(px(48f))
        else -> min(width * 0.42f, px(170f))
    }
    private val hauteurCarte get() = largeurCarte * Ornement.HAUTEUR_VIGNETTE / Ornement.LARGEUR

    /** La main s'étale sur la largeur : sur tablette, ou sur un téléphone couché. */
    private val large get() = tablette || basse

    /** Le rayon de l'arc : assez grand pour que l'éventail reste une courbe douce. */
    private val rayonArc: Float get() = when {
        basse -> width * 4f
        tablette -> maxOf(largeurCarte * 2.4f, width.toFloat())
        else -> largeurCarte * 2.4f
    }
    private val centreY get() =
        if (basse) margeHaute + largeurCarte * 0.58f else height * 0.47f

    /** L'écart entre deux cartes, en degrés. */
    private val pas get() =
        if (large) Math.toDegrees((largeurCarte * 0.5f / rayonArc).toDouble()).toFloat() else PAS

    /** Au-delà, une carte est sous le bord de l'écran ou presque : on ne la dessine pas. */
    private val angleMax get() =
        if (large) Math.toDegrees(
            kotlin.math.asin(((width / 2f + largeurCarte * 0.7f) / rayonArc).coerceAtMost(1f).toDouble())
        ).toFloat()
        else ANGLE_MAX

    /** La longueur d'arc entre deux cartes : ce qu'un doigt doit parcourir pour en passer une. */
    private val pasPixels get() = rayonArc * Math.toRadians(pas.toDouble()).toFloat()

    private var ex = 0f
    private var ey = 0f
    private var erot = 0f
    private var eechelle = 1f
    private var eprogres = 0f

    /**
     * La pose de la carte [i] à cet instant : entre sa place dans la pile et sa
     * place dans l'éventail, selon [deploiement].
     *
     * Les cartes proches du centre partent un peu avant les autres : lâchées
     * ensemble, elles se lisaient comme une image qui grandit, et c'est ce léger
     * décalage qui les fait se lire comme des cartes qu'on étale.
     */
    private fun poser(i: Int) {
        val ecart = i - decalage
        val angle = ecart * pas
        val rad = Math.toRadians(angle.toDouble())
        val finX = width / 2f + rayonArc * sin(rad).toFloat()
        val finY = centreY + rayonArc * (1f - cos(rad).toFloat())
        val finEchelle = 1f + 0.12f * (1f - abs(ecart)).coerceAtLeast(0f)

        val retard = min(RETARD_MAX, abs(ecart) * 0.07f)
        val t = ((deploiement - retard) / (1f - RETARD_MAX)).coerceIn(0f, 1f)
        val e = if (fermeture) t else rebond.getInterpolation(t)

        val debutEchelle = depart.width() / largeurCarte
        ex = depart.centerX() + (finX - depart.centerX()) * e
        ey = depart.centerY() + (finY - depart.centerY()) * e
        erot = angle * e
        eechelle = debutEchelle + (finEchelle - debutEchelle) * e
        eprogres = t
    }

    private fun visibles(liste: List<ContenuCarte>): List<Int> =
        liste.indices.filter { abs(it - decalage) * pas <= angleMax }

    // ---- la vie ----------------------------------------------------------

    fun ouvrir() {
        animVoile = animer(voile, 1f, 180, DecelerateInterpolator()) { voile = it }
    }

    fun poserCartes(liste: List<ContenuCarte>) {
        if (fermeture) return
        if (width == 0) {
            post { poserCartes(liste) }
            return
        }
        cartes = liste
        // Toujours une carte au centre, donc un décalage entier : à 1,5 pour quatre
        // cartes, deux se partageaient le milieu et aucune n'était « la » carte
        // qu'un toucher ouvre.
        // Sur tablette la main est large : la carte du centre laisse des
        // cartes des deux côtés, sans en pousser hors de l'arc à gauche.
        decalage = when {
            liste.size <= 5 -> ((liste.size - 1).coerceAtLeast(0) / 2).toFloat()
            large -> min(((liste.size - 1) / 2).toFloat(), (angleMax / pas).toInt() - 1f).coerceAtLeast(2f)
            else -> min(2f, liste.size - 1f)
        }
        rangSenti = decalage.roundToInt()
        contentDescription = "$titre, $sousTitre. " +
            if (liste.isEmpty()) messageVide else context.getString(R.string.eventail_parcourir)
        // Les rectos visibles sont rendus avant le déploiement, sinon sa
        // première trame porterait le coût de tous, et c'est elle qu'on voit.
        // Ils l'étaient sur le fil principal : une cinquantaine de
        // millisecondes chacun, l'écran gelait 0,3 à 0,4 s au toucher du
        // casier (Galaxy A21s, 2026-10-12). Ils se rendent maintenant en fond
        // pendant que le voile s'ouvre, et l'éventail part quand ils sont là.
        val aRendre = visibles(liste).map { liste[it] }.filter { images.get(it.carte.forme) == null }
        var restants = aRendre.size
        val deployer = {
            if (!fermeture) {
                cartes = liste
                animDeploiement = animer(0f, 1f, 560, null) { deploiement = it }
            }
        }
        if (restants == 0) deployer()
        else aRendre.forEach { c -> demander(c) { if (--restants == 0) deployer() } }
    }

    /** Range les cartes dans leur casier, puis rend la main à l'appelant. */
    fun fermer() {
        if (fermeture) return
        fermeture = true
        animDecalage?.cancel()
        animDeploiement?.cancel()
        animVoile?.cancel()
        animDeploiement = animer(deploiement, 0f, 280, null) { deploiement = it }
        animVoile = animer(voile, 0f, 280, null, fin = surFermeture) { voile = it }
    }

    private fun animer(
        de: Float,
        a: Float,
        duree: Long,
        courbe: android.animation.TimeInterpolator?,
        fin: (() -> Unit)? = null,
        maj: (Float) -> Unit
    ): ValueAnimator = ValueAnimator.ofFloat(de, a).apply {
        duration = if (reduites) 0L else duree
        if (courbe != null) interpolator = courbe else interpolator = null
        addUpdateListener {
            maj(it.animatedValue as Float)
            invalidate()
        }
        if (fin != null) {
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) = fin()
            })
        }
        start()
    }

    override fun onDetachedFromWindow() {
        animVoile?.cancel()
        animDeploiement?.cancel()
        animDecalage?.cancel()
        super.onDetachedFromWindow()
    }

    /**
     * Refait le recto de [forme] : son paysage vient d'arriver. L'ancien reste
     * à l'écran jusqu'à ce que le nouveau soit prêt.
     */
    fun rafraichir(forme: String) {
        val c = cartes?.firstOrNull { it.carte.forme == forme } ?: return
        demander(c, refaire = true) {}
    }

    /** Les rectos demandés et pas encore rendus. */
    private val enCours = HashSet<String>()

    /**
     * Rend le recto de [c] sur le fil de fond, puis le range et redessine.
     * [fait] est appelé sur le fil principal, rendu réussi ou non.
     */
    private fun demander(c: ContenuCarte, refaire: Boolean = false, fait: () -> Unit) {
        val forme = c.carte.forme
        if (!refaire && !enCours.add(forme)) return
        val largeur = largeurCarte.toInt().coerceAtLeast(1)
        FilDeRendu.handler.post {
            val b = try {
                rendre(c, largeur)
            } catch (e: Throwable) {
                null
            }
            post {
                enCours.remove(forme)
                if (b != null && !fermeture) {
                    images.put(forme, b)
                    invalidate()
                }
                fait()
            }
        }
    }

    /** Le recto de [c] s'il est prêt ; sinon il est demandé, et l'emplacement attend. */
    private fun image(c: ContenuCarte): Bitmap? =
        images.get(c.carte.forme) ?: run {
            demander(c) {}
            null
        }

    // ---- le tracé ----------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        pinceau.style = Paint.Style.FILL
        pinceau.color = Color.argb((voile * 224).toInt(), 18, 11, 5)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), pinceau)

        val alpha = (voile * 255).toInt()
        texteTitre.color = Color.WHITE
        texteTitre.alpha = alpha
        texteDoux.color = PALE
        texteDoux.alpha = alpha
        if (basse) {
            // Titre et sous-titre sur une seule ligne, centrée comme un tout.
            val suite = " · $sousTitre"
            val lt = texteTitre.measureText(titre)
            val gauche = (width - lt - texteDoux.measureText(suite)) / 2f
            canvas.drawText(titre, gauche + lt / 2f, px(26f), texteTitre)
            texteDoux.textAlign = Paint.Align.LEFT
            canvas.drawText(suite, gauche + lt, px(26f), texteDoux)
            texteDoux.textAlign = Paint.Align.CENTER
        } else {
            canvas.drawText(titre, width / 2f, px(52f), texteTitre)
            canvas.drawText(sousTitre, width / 2f, px(76f), texteDoux)
        }

        val liste = cartes
        if (liste == null) {
            canvas.drawText(context.getString(R.string.eventail_preparation), width / 2f, centreY, texteDoux)
            return
        }
        if (liste.isEmpty()) {
            // StaticLayout centre lui-même ses lignes, et veut un pinceau aligné à
            // gauche : avec l'alignement centré de texteDoux, chaque ligne serait
            // décalée d'une demi-largeur.
            val paragraphe = TextPaint(texteDoux).apply { textAlign = Paint.Align.LEFT }
            val mise = StaticLayout(
                messageVide, paragraphe, (width * 0.8f).toInt(),
                Layout.Alignment.ALIGN_CENTER, 1.3f, 0f, false
            )
            canvas.save()
            canvas.translate(width * 0.1f, centreY - mise.height / 2f)
            mise.draw(canvas)
            canvas.restore()
            aide(canvas, context.getString(R.string.eventail_refermer), null, alpha)
            return
        }

        // Des bords vers le centre : la carte du milieu est posée en dernier,
        // donc par-dessus, comme dans une main.
        visibles(liste).sortedByDescending { abs(it - decalage) }.forEach { dessinerCarte(canvas, it, liste[it]) }

        if (liste.size > 1) {
            val rang = decalage.roundToInt().coerceIn(0, liste.size - 1) + 1
            // Couché, il n'y a pas de place au-dessus de la carte : le compteur
            // va dans le coin, sur la ligne du titre.
            if (basse) canvas.drawText("$rang / ${liste.size}", width - px(48f), px(26f), texteDoux)
            else canvas.drawText(
                "$rang / ${liste.size}", width / 2f,
                centreY - hauteurCarte * 0.62f - px(10f), texteDoux
            )
            aide(canvas, context.getString(R.string.eventail_aide_plusieurs), context.getString(R.string.eventail_ranger), alpha)
        } else {
            aide(canvas, context.getString(R.string.eventail_aide_une), context.getString(R.string.eventail_ranger), alpha)
        }
    }

    private fun aide(canvas: Canvas, ligne1: String, ligne2: String?, alpha: Int) {
        texteDoux.color = PALE
        texteDoux.alpha = (alpha * 0.8f).toInt()
        if (basse) {
            // La largeur ne manque pas : les deux lignes se suivent sur une seule.
            val ligne = if (ligne2 == null) ligne1 else "$ligne1 · $ligne2"
            canvas.drawText(ligne, width / 2f, height - px(10f), texteDoux)
        } else {
            val y = height - px(if (ligne2 == null) 32f else 50f)
            canvas.drawText(ligne1, width / 2f, y, texteDoux)
            if (ligne2 != null) canvas.drawText(ligne2, width / 2f, y + px(22f), texteDoux)
        }
        texteDoux.alpha = alpha
    }

    private fun dessinerCarte(canvas: Canvas, i: Int, c: ContenuCarte) {
        poser(i)
        val l = largeurCarte
        val h = hauteurCarte
        val rayon = l * Ornement.RAYON / Ornement.LARGEUR

        canvas.save()
        canvas.translate(ex, ey)
        canvas.rotate(erot)
        canvas.scale(eechelle, eechelle)
        cadre.set(-l / 2f, -h / 2f, l / 2f, h / 2f)

        // L'ombre grandit avec la sortie : dans la pile, la carte est posée ;
        // dans la main, elle est levée au-dessus du voile.
        ombre.set(cadre)
        ombre.offset(px(3f), px(7f) * eprogres)
        pinceau.color = Color.argb((110 * eprogres).toInt(), 0, 0, 0)
        canvas.drawRoundRect(ombre, rayon, rayon, pinceau)

        val recto = image(c)
        if (recto != null) canvas.drawBitmap(recto, null, cadre, pinceauImage)
        else {
            pinceau.color = ATTENTE
            canvas.drawRoundRect(cadre, rayon, rayon, pinceau)
        }

        if (Sonje.estDue(c.carte.boite, c.carte.jourEcheance, aujourdHui)) {
            pinceau.style = Paint.Style.STROKE
            pinceau.strokeWidth = px(3f)
            pinceau.color = Carnet.COULEUR
            canvas.drawRoundRect(cadre, rayon, rayon, pinceau)
            pinceau.style = Paint.Style.FILL
        }
        canvas.restore()
    }

    // ---- le doigt ----------------------------------------------------------

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (fermeture) return true
        val n = cartes?.size ?: 0
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Les onglets de l'application sont un ViewPager : sans ceci, il
                // prend le glissé pour lui et fait défiler la page entière au lieu
                // de l'éventail.
                parent?.requestDisallowInterceptTouchEvent(true)
                x0 = e.x
                decalage0 = decalage
                glisse = false
                animDecalage?.cancel()
                suivi?.recycle()
                suivi = VelocityTracker.obtain().also { it.addMovement(e) }
            }
            MotionEvent.ACTION_MOVE -> {
                suivi?.addMovement(e)
                val dx = e.x - x0
                if (!glisse && abs(dx) > seuil) glisse = true
                if (glisse && n > 1) {
                    decalage = (decalage0 - dx / pasPixels).coerceIn(-0.45f, n - 1 + 0.45f)
                    sentirCran(n)
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                suivi?.addMovement(e)
                suivi?.computeCurrentVelocity(1000)
                val vx = suivi?.xVelocity ?: 0f
                suivi?.recycle()
                suivi = null
                if (glisse) {
                    if (n > 1) allerA((decalage - vx / pasPixels * 0.2f).roundToInt().coerceIn(0, n - 1))
                } else {
                    performClick()
                    toucher(e.x, e.y)
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                suivi?.recycle()
                suivi = null
                if (n > 1) allerA(decalage.roundToInt().coerceIn(0, n - 1))
            }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    /** Les flèches d'un clavier : la carte voisine prend le centre. */
    fun tourner(sens: Int) {
        val n = cartes?.size ?: return
        if (fermeture || n < 2) return
        allerA((decalage.roundToInt() + sens).coerceIn(0, n - 1))
    }

    /** Entrée sur un clavier : ouvre la carte du centre, comme un toucher. */
    fun ouvrirCentre() {
        val liste = cartes ?: return
        if (fermeture) return
        if (liste.isEmpty()) fermer() else surCarte(liste[decalage.roundToInt().coerceIn(0, liste.size - 1)])
    }

    /** Amène au centre la carte [i] : la carte ouverte en grand a été feuilletée. */
    fun centrerSur(i: Int) {
        val n = cartes?.size ?: return
        if (fermeture || i !in 0 until n) return
        allerA(i)
    }

    private fun allerA(i: Int) {
        val n = cartes?.size ?: 0
        animDecalage = animer(decalage, i.toFloat(), 260, DecelerateInterpolator()) {
            decalage = it
            sentirCran(n)
        }
    }

    /**
     * Un cran chaque fois qu'une autre carte prend le centre, pendant le glissé
     * comme pendant le calage. Borné aux vraies cartes : le débord élastique
     * au-delà des extrémités ne donne pas de cran, puisqu'il n'y a rien derrière.
     */
    private fun sentirCran(n: Int) {
        if (n < 2) return
        val rang = decalage.roundToInt().coerceIn(0, n - 1)
        if (rang != rangSenti) {
            rangSenti = rang
            KeyFeedback.onFanStep(this)
        }
    }

    /** Du dessus vers le dessous : la carte touchée est la plus haute sous le doigt. */
    private fun toucher(x: Float, y: Float) {
        val liste = cartes
        if (liste == null || liste.isEmpty()) {
            fermer()
            return
        }
        for (i in visibles(liste).sortedBy { abs(it - decalage) }) {
            poser(i)
            val dx = x - ex
            val dy = y - ey
            val rad = Math.toRadians(erot.toDouble())
            val lx = (dx * cos(rad) + dy * sin(rad)).toFloat()
            val ly = (-dx * sin(rad) + dy * cos(rad)).toFloat()
            if (abs(lx) <= largeurCarte * eechelle / 2f && abs(ly) <= hauteurCarte * eechelle / 2f) {
                if (abs(i - decalage) < 0.5f) surCarte(liste[i]) else allerA(i)
                return
            }
        }
        fermer()
    }

    private companion object {
        /** L'écart entre deux cartes de l'éventail, en degrés, sur téléphone. */
        const val PAS = 13f

        /** Sur téléphone : au-delà, une carte est sous le bord de l'écran ou presque. */
        const val ANGLE_MAX = 80f

        const val RETARD_MAX = 0.3f

        /** Sous cette hauteur, en dp, la vue est « basse » : un téléphone couché. */
        const val HAUTEUR_BASSE = 420f

        const val PALE = 0xFFE6DCCB.toInt()

        /** La place d'un recto pas encore rendu : le papier de la carte. */
        const val ATTENTE = 0xFFEDE3CF.toInt()


    }
}

/**
 * Le fil où se rendent les rectos de la boîte et de l'éventail, un seul pour
 * tous : les rendus se suivent dans l'ordre demandé, le centre d'abord. Il a
 * sa boucle, parce qu'une vue construite hors du fil principal veut un
 * `Handler` : sur un simple `Thread`, la construction d'une vignette échoue.
 */
internal object FilDeRendu {
    val handler: android.os.Handler by lazy {
        val fil = android.os.HandlerThread("rectos", android.os.Process.THREAD_PRIORITY_DISPLAY)
        fil.start()
        android.os.Handler(fil.looper)
    }
}
