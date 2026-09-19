package com.example.kreyolkeyboard.carnet

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Region
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.sin
import kotlin.random.Random

/**
 * La boîte de Leitner, en bois, vue de dessus et légèrement inclinée.
 *
 * Elle remplace la pastille « Réviser N cartes », qui disait le compte et rien
 * d'autre. Sept casiers y sont creusés, un par boîte, et les cartes s'y
 * empilent : la collection a enfin une forme, et l'avancement se lit sans
 * qu'un chiffre le dise.
 *
 * ## Le bois ne coûte pas un octet
 *
 * Aucun asset. Le grain est une bitmap de 2 × 256 pixels fabriquée une fois au
 * premier affichage — une somme de quatre sinus à fréquences non harmoniques
 * plus une gigue, une valeur par ligne — puis tuilée en [BitmapShader] et
 * étirée par une [Matrix] dans le sens des fibres. Deux kilooctets de tas, zéro
 * dans l'APK. Le procédé est celui de `DosRevision.lion()`, qui synthétise déjà
 * sa marque depuis un tableau de pixels.
 *
 * Mais une plaque brune bien veinée reste du plastique : ce qui la fait
 * basculer en bois ciré, c'est [LUSTRE], large et de faible intensité, à
 * l'inverse du reflet serré de l'or d'une carte distinguée. Et deux détails de
 * menuiserie valent plus que du grain fin : le dessus des cloisons est veiné
 * **en travers** (du bois de bout, `travers = true`), et le fond des casiers
 * est plus sombre et moins veiné que l'extérieur, comme du bois brut à l'ombre.
 *
 * ## Pourquoi l'objet est fixe
 *
 * Il ne suit pas l'appareil, et c'est un choix, pas une économie. Une vue de
 * dessus demande cinquante degrés ; [Inclinaison] documente qu'au-delà d'une
 * dizaine les filets d'un pixel scintillent et la typographie du bord fuyant
 * devient illisible. Surtout, l'objet est fait d'épaisseur — parois internes,
 * cartes debout dans leur fente — et une image plate que le GPU bascule n'a
 * aucune épaisseur, donc rien de ce qui fait la boîte. L'axonométrie est donc
 * tracée à la main, à angle figé, ce qui permet de peindre un éclairage
 * délibéré qu'aucun lustre piloté au capteur ne justifierait.
 *
 * La conséquence est qu'il n'y a rien à mettre en cache : la vue est hors du
 * `ScrollView` et rien ne l'invalide par image, donc `onDraw` ne tourne qu'au
 * changement de taille ou de contenu. Une bitmap hors écran serait de la
 * mémoire immobilisée pour zéro tracé économisé.
 *
 * ## Ce que la boîte n'a pas le droit de faire
 *
 * **Elle ne laisse pas choisir ce qu'on révise.** Les casiers se consultent, ils
 * ne se lancent pas : la file est bâtie par échéance, la plus ancienne d'abord
 * puis la boîte la plus basse, et plafonnée à [Sonje.PLAFOND_SESSION].
 * Réviser le casier 5 parce qu'il est joli, ce sont des cartes pas encore dues,
 * un calendrier faussé et la méthode qui ne veut plus rien dire. La révision est
 * donc **une** cible, la plaque de laiton, et elle porte le compte que la
 * pastille portait.
 *
 * Et elle ne récompense pas : la rareté d'une carte ne change rien à la boîte.
 * L'ornement monte avec le palier, la menuiserie non.
 */
internal class BoiteLeitner(context: Context) : View(context) {

    /** Un casier a été touché : à l'appelant de montrer ce qu'il contient. */
    var surCasier: ((Int) -> Unit)? = null

    /** La plaque a été touchée. Ne se déclenche que s'il y a quelque chose à revoir. */
    var surRevision: (() -> Unit)? = null

    private val densite = resources.displayMetrics.density
    private fun px(v: Float) = v * densite

    private val combien = IntArray(CASIERS)
    private val dues = IntArray(CASIERS)
    private var aRevoir = 0
    private var total = 0

    /** Le jour de la prochaine échéance, en jours depuis aujourd'hui. -1 : aucune. */
    private var prochainDans = -1
    private var prochainCombien = 0

    /** Les cartes, triées par compartiment. */
    private val cartesParCasier = Array(CASIERS) { mutableListOf<CarteMot>() }

    /** Par casier, les cartes dues d'abord, puis les autres : l'ordre de la pile. */
    private val pileParCasier = Array(CASIERS) { mutableListOf<CarteMot>() }

    /**
     * Le recto de chaque carte visible, par forme : la vignette du carnet, rendue
     * une fois hors écran par l'appelant.
     *
     * Une carte sans recto est dessinée en carton uni, et c'est l'état voulu au
     * premier affichage : le recto demande la fiche du dictionnaire, dont le
     * chargement prend plusieurs secondes, et la boîte ne les attend pas.
     */
    private val rectos = HashMap<String, Bitmap>()
    private val pinceauImage = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val cadreCarte = RectF()

    /** Le casier ou la plaque sous le doigt, pour l'état pressé. [RIEN] sinon. */
    private var presse = RIEN

    private val pinceau = Paint(Paint.ANTI_ALIAS_FLAG)
    private val texte = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
    }
    private val legende = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val chemin = Path()

    // L'axonométrie, recalculée à chaque changement de taille. Le dessus est un
    // trapèze : le bord arrière est rentré de [FUITE] de chaque côté, ce qui
    // donne la perspective sans qu'aucune matrice n'entre en jeu.
    private var xAvG = 0f
    private var xAvD = 0f
    private var xArG = 0f
    private var xArD = 0f
    private var yAv = 0f
    private var yAr = 0f
    private var yMur = 0f

    private val uCasier = Array(CASIERS) { FloatArray(2) }
    private val zones = arrayOfNulls<Region>(CASIERS)
    private val plaque = RectF()

    /**
     * Donne à la boîte l'état du carnet.
     *
     * Le compte de la plaque et les cartes soulevées sont dérivés de la **même**
     * liste et du même [Sonje.estDue] : sans cela la plaque pourrait
     * annoncer quatre cartes là où aucun casier n'en soulève, ce qui se lirait
     * comme un bogue et non comme une échéance.
     */
    fun poser(cartes: List<CarteMot>, aujourdHui: Int = Sonje.aujourdHui()) {
        combien.fill(0)
        dues.fill(0)
        aRevoir = 0
        total = cartes.size
        for (i in cartesParCasier.indices) cartesParCasier[i].clear()

        var prochain = Int.MAX_VALUE
        prochainCombien = 0
        for (c in cartes) {
            val boite = c.boite.coerceIn(0, Sonje.BOITE_ACQUISE)
            combien[boite]++
            cartesParCasier[boite].add(c)
            if (Sonje.estDue(c.boite, c.jourEcheance, aujourdHui)) {
                dues[boite]++
                aRevoir++
            } else if (!c.acquise) {
                when {
                    c.jourEcheance < prochain -> { prochain = c.jourEcheance; prochainCombien = 1 }
                    c.jourEcheance == prochain -> prochainCombien++
                }
            }
        }
        prochainDans = if (prochainCombien > 0) prochain - aujourdHui else -1
        for (i in 0 until CASIERS) {
            val (d, r) = cartesParCasier[i]
                .sortedBy { it.numero }
                .partition { Sonje.estDue(it.boite, it.jourEcheance, aujourdHui) }
            pileParCasier[i].clear()
            pileParCasier[i].addAll(d)
            pileParCasier[i].addAll(r)
        }
        contentDescription = buildString {
            append("Boîte de révision, sept casiers, $total carte")
            if (total > 1) append("s")
            append(". ")
            append(
                if (aRevoir > 0) "${libelleRevision()}."
                else listOfNotNull(libelleAJour(), libelleProchaine()).joinToString(", ") + "."
            )
        }
        invalidate()
    }

    /**
     * Les cartes que la boîte montre effectivement, levées comprises.
     *
     * L'appelant ne rend le recto que de celles-ci : une pile n'en montre que
     * [CARTES_VUES], et rendre toute la collection coûterait un bitmap par carte
     * pour des faces que personne ne verrait.
     */
    fun cartesVisibles(): List<CarteMot> = (0 until CASIERS).flatMap { i ->
        val pile = pileParCasier[i]
        val levees = minOf(dues[i], LEVEES)
        pile.take(levees) + pile.drop(levees).take(minOf(pile.size, CARTES_VUES) - levees)
    }

    fun combienDans(casier: Int): Int = combien[casier]
    fun duesDans(casier: Int): Int = dues[casier]

    /** Le casier dont l'éventail est ouvert, qui reste éclairé. -1 : aucun. */
    private var casierEclaire = RIEN

    fun eclairer(casier: Int) {
        casierEclaire = casier
        invalidate()
    }

    /**
     * La face avant de la pile d'un casier, dans les coordonnées de la vue : là
     * d'où l'éventail fait partir les cartes, et où il les range.
     *
     * Même calcul que [casier], qui dessine cette face ; un casier vide rend la
     * place qu'y occuperait sa première carte.
     */
    fun faceDuCasier(i: Int): RectF {
        val u0 = uCasier[i][0]
        val u1 = uCasier[i][1]
        val marge = (u1 - u0) * 0.09f
        val vBase = vAvant + 0.025f
        val g = sx(u0 + marge, vBase)
        val dr = sx(u1 - marge, vBase)
        val bas = sy(vBase)
        val hauteur = (dr - g) * Ornement.HAUTEUR_VIGNETTE / Ornement.LARGEUR
        return RectF(g, bas - hauteur, dr, bas)
    }

    /** Donne les rectos rendus. Ceux qui manquent restent en carton uni. */
    fun poserRectos(parForme: Map<String, Bitmap>) {
        rectos.clear()
        rectos.putAll(parForme)
        invalidate()
    }

    /**
     * Le compte de la plaque est celui de la **session**, pas de l'arriéré.
     *
     * Une session s'arrête à [Sonje.PLAFOND_SESSION] cartes. Annoncer
     * trente cartes pour en servir douze, c'est un contrat rompu à la première
     * révision ; et l'arriéré entier est exactement le mur que l'étalement
     * existe pour ne jamais montrer.
     */
    private fun libelleRevision(): String {
        val n = minOf(aRevoir, Sonje.PLAFOND_SESSION)
        return if (n == 1) "Réviser 1 carte" else "Réviser $n cartes"
    }

    private fun libelleAJour(): String =
        if (prochainDans < 0) "Tout est acquis" else "Tout est à jour"

    /**
     * Quand revenir. Une plaque « rien à revoir » sans date est un bouton mort :
     * elle dit non sans dire à quand, et c'est ce silence qui fait décrocher.
     */
    private fun libelleProchaine(): String? {
        if (prochainDans < 0) return null
        val quand = when (prochainDans) {
            1 -> "demain"
            2 -> "après-demain"
            else -> "dans $prochainDans jours"
        }
        val cartes = if (prochainCombien == 1) "1 carte" else "$prochainCombien cartes"
        return "$cartes $quand"
    }

    // La boîte prend toute la hauteur que son parent lui donne : ouverte depuis
    // Spiller, elle est seule à l'écran. [HAUTEUR] ne sert plus que de plancher,
    // et de taille quand rien ne la borne (un ScrollView mesure en UNSPECIFIED).
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val plancher = px(HAUTEUR).toInt()
        val taille = MeasureSpec.getSize(heightMeasureSpec)
        val hauteur = when (MeasureSpec.getMode(heightMeasureSpec)) {
            MeasureSpec.UNSPECIFIED -> plancher
            else -> maxOf(taille, plancher)
        }
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), hauteur)
    }

    override fun onSizeChanged(l: Int, h: Int, ancienL: Int, ancienH: Int) {
        val fuite = l * FUITE
        xAvG = 0f
        xAvD = l.toFloat()
        xArG = fuite
        xArD = l - fuite
        // Le mur et l'ombre gardent leur épaisseur ; c'est la profondeur du
        // dessus qui absorbe la hauteur disponible, donc les fentes s'allongent.
        yMur = h - px(HAUTEUR - MUR_BAS)
        yAv = yMur - px(MUR_BAS - DESSUS_AVANT)
        yAr = px(DESSUS_ARRIERE)
        echelle = (yAv - yAr) / px(DESSUS_AVANT - DESSUS_ARRIERE)
        vAvant = (px(BANDEAU) / (yAv - yAr)).coerceIn(0.1f, 0.35f)
        plaque.set(l * 0.12f, yAv + px(7f), l * 0.88f, yMur - px(7f))
        decouperCasiers()
    }

    /** La profondeur du dessus rapportée à celle de la boîte compacte d'origine. */
    private var echelle = 1f

    /**
     * Où commencent les fentes, en profondeur.
     *
     * Le bandeau avant porte les légendes des casiers : sa hauteur est fixée
     * en densités, pas en part de profondeur, parce que du texte ne s'étire
     * pas avec le meuble.
     */
    private var vAvant = 0.13f

    /**
     * Les sept casiers en coordonnées de surface.
     *
     * La cloison qui précède « Acquis » est deux fois plus épaisse : ce casier
     * n'est pas une étape de la rotation mais sa sortie, et une menuiserie le
     * dit mieux qu'une légende.
     */
    private fun decouperCasiers() {
        val cloisons = CLOISON * (CASIERS - 2) + CLOISON_ACQUIS
        val large = (1f - 2f * BORD - cloisons) / CASIERS
        var u = BORD
        for (i in 0 until CASIERS) {
            uCasier[i][0] = u
            uCasier[i][1] = u + large
            u += large + if (i == CASIERS - 2) CLOISON_ACQUIS else CLOISON
            // La zone descend jusqu'à l'arête avant : toucher la légende d'un
            // casier, c'est désigner ce casier.
            zones[i] = region(quad(uCasier[i][0], uCasier[i][1], 0f, V_ARRIERE))
        }
    }

    // ---- la surface du dessus -------------------------------------------

    /** L'abscisse du point (u, v) : u de gauche à droite, v de l'avant vers le fond. */
    private fun sx(u: Float, v: Float): Float {
        val g = xAvG + (xArG - xAvG) * v
        val dr = xAvD + (xArD - xAvD) * v
        return g + (dr - g) * u
    }

    /** L'ordonnée ne dépend que de la profondeur : les deux bords sont horizontaux. */
    private fun sy(v: Float) = yAv + (yAr - yAv) * v

    private fun quad(u0: Float, u1: Float, v0: Float, v1: Float): Path {
        val p = Path()
        p.moveTo(sx(u0, v0), sy(v0))
        p.lineTo(sx(u1, v0), sy(v0))
        p.lineTo(sx(u1, v1), sy(v1))
        p.lineTo(sx(u0, v1), sy(v1))
        p.close()
        return p
    }

    private fun region(p: Path): Region {
        val bornes = RectF()
        p.computeBounds(bornes, true)
        val entier = Rect()
        bornes.roundOut(entier)
        return Region().apply { setPath(p, Region(entier)) }
    }

    // ---- le bois ---------------------------------------------------------

    /**
     * Pose une face : son fond dégradé, puis ses fibres.
     *
     * [travers] veine en travers, pour le dessus des cloisons : c'est du bois de
     * bout, et c'est ce détail que l'œil lit comme de la vraie menuiserie.
     */
    private fun bois(
        canvas: Canvas,
        forme: Path,
        haut: Int,
        bas: Int,
        travers: Boolean = false,
        fibre: Int = 255
    ) {
        val bornes = RectF()
        forme.computeBounds(bornes, true)
        pinceau.shader = LinearGradient(
            bornes.left, bornes.top, bornes.right, bornes.bottom,
            haut, bas, Shader.TileMode.CLAMP
        )
        pinceau.alpha = 255
        canvas.drawPath(forme, pinceau)

        if (fibre <= 0) return
        // La période est **fixe**, en densités, et non proportionnelle à la
        // face : mise à l'échelle de chaque face, la plus grande recevait moins
        // d'un cycle et se retrouvait lisse, ce qui rendait le bois plastique.
        // Une fibre se compte en millimètres, pas en parts de meuble.
        val m = Matrix()
        m.setScale(600f, px(PERIODE) / 256f)
        if (travers) m.postRotate(90f, bornes.centerX(), bornes.centerY())
        m.postTranslate(0f, bornes.top)
        pinceau.shader = BitmapShader(fibres(), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            .apply { setLocalMatrix(m) }
        pinceau.alpha = fibre
        canvas.drawPath(forme, pinceau)
        pinceau.alpha = 255
        pinceau.shader = null
    }

    override fun onDraw(canvas: Canvas) {
        if (total == 0) return
        val l = width.toFloat()

        // L'ombre au sol, avant tout le reste : c'est elle qui pose la boîte sur
        // la page au lieu de la laisser flotter.
        // Deux passes de plus en plus pâles valent un flou et ne coûtent qu'un
        // tracé de plus : une seule passe donnait une dalle grise à arête vive,
        // qui se lisait comme un élément d'interface posé sous la boîte.
        //
        // Même deux passes gardaient des bords nets : sur la page claire, elles
        // se lisaient encore comme une barre grise glissée sous la boîte. C'est
        // désormais un dégradé radial écrasé à la verticale, qui s'éteint dans
        // toutes les directions et n'a donc aucune arête à montrer.
        pinceau.color = Color.BLACK
        val ombre = RadialGradient(
            l / 2f, yMur, l * 0.52f,
            intArrayOf(0x38000000, 0x14000000, 0x00000000),
            floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP
        )
        ombre.setLocalMatrix(Matrix().apply { setScale(1f, px(16f) / (l * 0.52f), l / 2f, yMur) })
        pinceau.shader = ombre
        canvas.drawRect(0f, yMur - px(2f), l, yMur + px(18f), pinceau)
        pinceau.shader = null

        // Le mur avant, puis le dessus : une face verticale reçoit moins de
        // lumière qu'une face horizontale, et tout l'effet de volume est là.
        chemin.reset()
        chemin.moveTo(xAvG, yAv)
        chemin.lineTo(xAvD, yAv)
        chemin.lineTo(xAvD, yMur)
        chemin.lineTo(xAvG, yMur)
        chemin.close()
        bois(canvas, chemin, MUR_HAUT, MUR_OMBRE, fibre = 150)

        bois(canvas, quad(0f, 1f, 0f, 1f), DESSUS_CLAIR, DESSUS_SOMBRE, fibre = 145)

        // Le dessus des cloisons, veiné en travers. Posé après le plateau : il
        // ne fait que le reteinter, mais c'est ce qui sépare les fentes.
        for (i in 0 until CASIERS - 1) {
            val u0 = uCasier[i][1]
            val u1 = uCasier[i + 1][0]
            bois(
                canvas, quad(u0, u1, vAvant, V_ARRIERE),
                CLOISON_CLAIR, CLOISON_SOMBRE, travers = true, fibre = 95
            )
        }

        for (i in 0 until CASIERS) casier(canvas, i)
        legendes(canvas)

        // Le lisséré clair sur l'arête avant du plateau : une arête vive de bois
        // ciré attrape la lumière, et sans elle le mur et le dessus se touchent
        // sans qu'on voie l'angle.
        pinceau.shader = null
        pinceau.color = LUSTRE
        pinceau.strokeWidth = px(1.2f)
        pinceau.style = Paint.Style.STROKE
        canvas.drawLine(xAvG, yAv, xAvD, yAv, pinceau)
        pinceau.style = Paint.Style.FILL

        plaqueLaiton(canvas)
    }

    /** Un casier : sa fente à l'ombre, puis les cartes qui s'y tiennent debout. */
    private fun casier(canvas: Canvas, i: Int) {
        val u0 = uCasier[i][0]
        val u1 = uCasier[i][1]
        val fente = quad(u0, u1, vAvant, V_ARRIERE)

        // Le fond est plus sombre à l'avant qu'au fond : c'est là que la paroi
        // proche porte son ombre, et c'est ce dégradé qui creuse la fente.
        bois(canvas, fente, FOND_ARRIERE, FOND_AVANT, fibre = 45)

        val n = combien[i]
        if (n == 0) {
            voileSiPresse(canvas, fente, i)
            return
        }

        val visibles = if (n < CARTES_VUES) n else CARTES_VUES
        val soulevees = if (dues[i] < LEVEES) dues[i] else LEVEES
        val normales = visibles - soulevees

        // La pile s'empile en **espace écran**, pas en profondeur. Calée sur la
        // profondeur, chaque carte suivait la fuite du trapèze et se décalait de
        // quelques pixels sur le côté : la pile se lisait comme un escalier. Une
        // pile de fiches est parallèle aux parois de sa fente ; c'est la fente
        // qui converge, pas elle.
        // La pile s'assoit sur la lèvre de la fente, pas huit densités au-dessus,
        // sinon elle flotte. Et elle occupe une bonne moitié de la profondeur :
        // trop basse, la fente se lit comme un trou et la boîte comme vide.
        val marge = (u1 - u0) * 0.09f
        val vBase = vAvant + 0.025f
        val g = sx(u0 + marge, vBase)
        val dr = sx(u1 - marge, vBase)
        val bas = sy(vBase)
        // Proportionnées à la fente elle-même, et non plus à une boîte de
        // référence : une pile de huit cartes en occupe alors les trois quarts,
        // et quatre cartes ne sont plus un liseré au bas d'un long trou sombre.
        // La tranche reste la même d'un casier à l'autre, pour que la hauteur de
        // la pile continue de dire lequel est le plus rempli.
        // La face garde les proportions du recto, carré en vignette : une carte
        // étirée à la hauteur de la fente déformerait son illustration. Même
        // proportion en carton uni, pour que rien ne saute quand les rectos
        // arrivent.
        val hauteur = (dr - g) * Ornement.HAUTEUR_VIGNETTE / Ornement.LARGEUR
        // L'écart entre deux cartes et la levée des dues suivent la carte et non
        // la fente : réglés sur la profondeur, ils dépassaient la hauteur d'une
        // face carrée, et la pile se défaisait en cartes flottant séparément.
        val tranche = hauteur * 0.3f
        val levee = hauteur * 0.45f
        val pile = pileParCasier[i]

        canvas.save()
        canvas.clipPath(fente)
        // Les dues **coiffent** la pile, elles ne la fondent pas : rangées
        // devant, elles se retrouvaient sous les autres, ce qui est le contraire
        // de « voilà ce que vous devez ». Dessinées d'abord parce qu'elles sont
        // les plus hautes, donc les plus au fond.
        //
        // Et elles **dépassent** : levées d'un bon cran et chacune un peu de
        // travers, comme une fiche qu'on a tirée à moitié pour ne pas l'oublier.
        // Une pile bien alignée se lit comme rangée ; c'est le désordre qui dit
        // « à faire ».
        val cx = (g + dr) / 2f
        for (j in soulevees - 1 downTo 0) {
            val pied = bas - (normales + j) * tranche - levee
            canvas.save()
            canvas.rotate(PENCHES[j % PENCHES.size], cx, pied)
            carte(canvas, g, dr, pied, hauteur, true, pile.getOrNull(j))
            canvas.restore()
        }
        for (j in normales - 1 downTo 0) {
            carte(canvas, g, dr, bas - j * tranche, hauteur, false, pile.getOrNull(soulevees + j))
        }
        canvas.restore()
        voileSiPresse(canvas, fente, i)
    }

    /** Le retour du doigt sur un casier : la fente s'éclaire tant qu'on appuie. */
    private fun voileSiPresse(canvas: Canvas, fente: Path, i: Int) {
        if (presse != i && casierEclaire != i) return
        pinceau.shader = null
        pinceau.color = 0x2EFFFFFF
        canvas.drawPath(fente, pinceau)
    }

    /**
     * Une carte debout, vue de face : son recto quand il est rendu, sinon un
     * carton uni aux mêmes proportions.
     *
     * La pile se lit parce que la carte de devant masque les faces des suivantes
     * en laissant leur haut dépasser : c'est le rendu d'un jeu de cartes rangé.
     * Avec les rectos, ce qui dépasse est le haut du cadre et de l'illustration,
     * si bien que chaque carte de la pile reste reconnaissable à sa matière.
     */
    private fun carte(
        canvas: Canvas,
        g: Float,
        dr: Float,
        bas: Float,
        hauteur: Float,
        due: Boolean,
        qui: CarteMot?
    ) {
        val haut = bas - hauteur
        val rayon = (dr - g) * Ornement.RAYON / Ornement.LARGEUR
        cadreCarte.set(g, haut, dr, bas)
        val recto = qui?.let { rectos[it.forme] }

        if (recto != null) {
            canvas.drawBitmap(recto, null, cadreCarte, pinceauImage)
        } else {
            pinceau.shader = LinearGradient(
                g, haut, g, bas,
                if (due) CARTE_DUE_CLAIR else CARTE_CLAIR,
                if (due) CARTE_DUE_SOMBRE else CARTE_SOMBRE,
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(cadreCarte, rayon, rayon, pinceau)
            pinceau.shader = null
        }

        pinceau.style = Paint.Style.STROKE
        if (due) {
            // Un filet violet tout autour, pas seulement sur la tranche : penchée,
            // la carte doit se détacher de celle de derrière sur ses quatre côtés.
            pinceau.color = Carnet.COULEUR
            pinceau.strokeWidth = px(1.8f)
            canvas.drawRoundRect(cadreCarte, rayon, rayon, pinceau)
        } else if (recto == null) {
            pinceau.color = CARTE_TRANCHE
            pinceau.strokeWidth = px(1.4f)
            canvas.drawLine(g + rayon, haut, dr - rayon, haut, pinceau)
        }
        pinceau.style = Paint.Style.FILL
    }

    /**
     * Les légendes, brûlées dans le bois : sous chaque casier son compte et le
     * délai au bout duquel ses cartes reviennent, et sur le bandeau du fond la
     * règle du jeu en une phrase.
     *
     * Sans elles, sept fentes identiques ne disent rien à qui n'a pas lu Leitner.
     * Le délai est dit en mots courts (« 1 sem. ») et jamais par un numéro de
     * boîte, pour la raison que donne [etiquetteCasier].
     *
     * La taille du texte s'ajuste à la largeur d'un casier plutôt que l'inverse :
     * sur un écran étroit, « 3 jours » déborderait sur la légende voisine.
     */
    private fun legendes(canvas: Canvas) {
        val haut = sy(vAvant)
        val bandeau = yAv - haut
        val pas = sx(uCasier[1][0], vAvant / 2f) - sx(uCasier[0][0], vAvant / 2f)

        val rythmes = Array(CASIERS) { i ->
            if (i >= Sonje.BOITE_ACQUISE) "★ acquis"
            else rythmeCourt(Sonje.INTERVALLES[i])
        }
        legende.typeface = Typeface.DEFAULT
        ajuster(legende, minOf(px(11f), bandeau * 0.26f), rythmes.toList(), pas * 0.94f)
        texte.textAlign = Paint.Align.CENTER
        texte.textSize = minOf(px(16f), bandeau * 0.38f)

        val ligne1 = haut + bandeau * 0.46f
        val ligne2 = haut + bandeau * 0.84f
        for (i in 0 until CASIERS) {
            val cx = sx((uCasier[i][0] + uCasier[i][1]) / 2f, vAvant / 2f)
            val vide = combien[i] == 0
            grave(canvas, texte, combien[i].toString(), cx, ligne1, if (vide) 0x80 else 0xFF)
            grave(canvas, legende, rythmes[i], cx, ligne2, if (vide) 0x80 else 0xE6)
        }

        // La règle, sur le bandeau du fond. Plus étroit que l'avant : la fuite
        // du trapèze le rentre des deux côtés.
        val fond = sy(V_ARRIERE)
        val hautFond = fond - yAr
        val largeurFond = sx(1f, 1f) - sx(0f, 1f)
        val regle = "Bonne réponse : la carte avance et revient plus tard  →"
        legende.typeface = Typeface.DEFAULT_BOLD
        ajuster(legende, minOf(px(12.5f), hautFond * 0.5f), listOf(regle), largeurFond * 0.9f)
        val cy = yAr + hautFond / 2f - (legende.descent() + legende.ascent()) / 2f
        grave(canvas, legende, regle, width / 2f, cy, 0xD0)
    }

    /** Ramène [p] à [taille], puis le réduit si le plus long de [mots] dépasse [largeur]. */
    private fun ajuster(p: Paint, taille: Float, mots: List<String>, largeur: Float) {
        p.textSize = taille
        val plusLong = mots.maxOf { p.measureText(it) }
        if (plusLong > largeur) p.textSize = taille * largeur / plusLong
    }

    /** Du texte brûlé dans le bois : encre sombre, rehaut clair d'un pixel dessous. */
    private fun grave(canvas: Canvas, p: Paint, mot: String, cx: Float, y: Float, alpha: Int) {
        p.textAlign = Paint.Align.CENTER
        p.color = LUSTRE
        p.alpha = (0x40 * alpha) / 0xFF
        canvas.drawText(mot, cx, y + px(1f), p)
        p.color = BRULURE
        p.alpha = alpha
        canvas.drawText(mot, cx, y, p)
        p.alpha = 0xFF
    }

    /**
     * La plaque de laiton gravée, sur le mur avant.
     *
     * C'est ainsi qu'une vraie boîte à fiches est étiquetée, et c'est le seul
     * endroit de l'écran qui porte l'impératif que la pastille portait. Gravée
     * et non imprimée : le texte sombre reçoit un rehaut clair d'un pixel en
     * dessous, ce qui creuse la lettre dans le métal.
     *
     * Elle est l'action principale de l'écran, donc son plus gros bouton : le
     * mur avant a été rehaussé pour lui donner une cible de doigt pleine. Quand
     * rien n'est dû, elle ne se contente pas de dire non, elle dit quand revenir.
     */
    private fun plaqueLaiton(canvas: Canvas) {
        val rien = aRevoir == 0
        val enfonce = presse == PLAQUE && !rien
        val r = if (enfonce) px(1f) else 0f

        // Opaque d'abord : le pinceau sort de l'arête avant, tracée en LUSTRE à
        // 25 % d'alpha, et un shader hérite de l'alpha de sa peinture. Sans cette
        // ligne, le laiton était peint en transparence sur le mur et se lisait
        // comme du bois terne, quelle que soit sa palette.
        pinceau.color = Color.BLACK
        pinceau.shader = LinearGradient(
            plaque.left, plaque.top, plaque.left, plaque.bottom,
            if (rien) LAITON_TERNE else LAITON_CLAIR,
            if (rien) LAITON_TERNE_BAS else LAITON_SOMBRE,
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(
            plaque.left, plaque.top + r, plaque.right, plaque.bottom + r,
            px(5f), px(5f), pinceau
        )
        pinceau.shader = null
        // Un liseré clair en haut : la tranche du métal qui attrape la lumière,
        // et ce qui détache la plaque du bois sombre du mur.
        if (!rien) {
            pinceau.color = 0x70FFF6D0
            pinceau.style = Paint.Style.STROKE
            pinceau.strokeWidth = px(1.2f)
            canvas.drawLine(
                plaque.left + px(5f), plaque.top + r + px(1f),
                plaque.right - px(5f), plaque.top + r + px(1f), pinceau
            )
            pinceau.style = Paint.Style.FILL
        }

        texte.textAlign = Paint.Align.CENTER
        val cx = plaque.centerX()
        val encre = if (rien) GRAVURE_TERNE else GRAVURE
        val sousTitre = if (rien) libelleProchaine() else null

        if (sousTitre == null) {
            val mot = if (rien) libelleAJour() else libelleRevision()
            ajuster(texte, px(17f), listOf(mot), plaque.width() * 0.9f)
            val cy = plaque.centerY() - (texte.descent() + texte.ascent()) / 2f + r
            graveMetal(canvas, texte, mot, cx, cy, encre)
        } else {
            ajuster(texte, px(15f), listOf(libelleAJour()), plaque.width() * 0.9f)
            legende.typeface = Typeface.DEFAULT
            ajuster(legende, px(12.5f), listOf(sousTitre), plaque.width() * 0.9f)
            graveMetal(canvas, texte, libelleAJour(), cx, plaque.centerY() - px(2f) + r, encre)
            graveMetal(canvas, legende, sousTitre, cx, plaque.centerY() + px(14f) + r, encre)
        }
    }

    private fun graveMetal(canvas: Canvas, p: Paint, mot: String, cx: Float, y: Float, encre: Int) {
        p.textAlign = Paint.Align.CENTER
        p.color = LUSTRE
        canvas.drawText(mot, cx, y + px(1f), p)
        p.color = encre
        canvas.drawText(mot, cx, y, p)
    }

    // ---- le doigt --------------------------------------------------------

    override fun onTouchEvent(evenement: MotionEvent): Boolean {
        val x = evenement.x.toInt()
        val y = evenement.y.toInt()
        when (evenement.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!isEnabled) return false
                presse = cible(x, y)
                if (presse == RIEN) return false
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (presse != RIEN && cible(x, y) != presse) {
                    presse = RIEN
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                val sur = presse
                presse = RIEN
                invalidate()
                if (sur == RIEN) return true
                playSoundEffect(android.view.SoundEffectConstants.CLICK)
                if (sur == PLAQUE) {
                    if (aRevoir > 0) surRevision?.invoke()
                } else if (sur >= 0 && sur < CASIERS) {
                    // Un casier se consulte, plein ou vide. Il lançait la révision
                    // de sa première carte quand il en avait : c'était choisir ce
                    // qu'on révise, ce que la note de classe interdit, et le doigt
                    // qui voulait regarder se retrouvait interrogé.
                    surCasier?.invoke(sur)
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                presse = RIEN
                invalidate()
                return true
            }
        }
        return false
    }

    /**
     * Ce qui se trouve sous le doigt.
     *
     * Les fentes sont des trapèzes penchés vers l'intérieur : leurs
     * rectangles englobants se chevauchent, donc un test par [RectF] désignerait
     * le mauvais casier près des cloisons. Une [Region] par fente coûte sept
     * objets construits une fois et répond juste.
     */
    private fun cible(x: Int, y: Int): Int {
        if (plaque.contains(x.toFloat(), y.toFloat())) return PLAQUE
        for (i in 0 until CASIERS) {
            if (zones[i]?.contains(x, y) == true) return i
        }
        return RIEN
    }

    companion object {
        /**
         * Sept : les six boîtes de la rotation, plus la sortie.
         *
         * Dérivé et non écrit en dur, comme les libellés de l'étagère : une
         * suite d'intervalles retouchée laisserait sinon une menuiserie qui
         * ment, avec un casier de trop ou de moins et aucune erreur pour le
         * dire. Ce n'est pas un `const` parce que [Sonje.BOITE_ACQUISE]
         * se lit sur la taille du tableau d'intervalles.
         */
        val CASIERS = Sonje.BOITE_ACQUISE + 1

        private const val RIEN = -1
        private const val PLAQUE = -2

        // Le mur avant fait 60 dp, et non plus 28 : il porte la plaque, qui est
        // le bouton principal de l'écran et doit offrir une cible de doigt pleine.
        private const val HAUTEUR = 208f
        private const val DESSUS_ARRIERE = 8f
        private const val DESSUS_AVANT = 124f
        private const val MUR_BAS = 184f

        /** La hauteur du bandeau avant, qui porte les légendes des casiers. */
        private const val BANDEAU = 46f

        /** La rentrée du bord arrière, en part de la largeur : la perspective. */
        private const val FUITE = 0.075f

        private const val BORD = 0.035f
        private const val CLOISON = 0.013f
        private const val CLOISON_ACQUIS = 0.026f
        private const val V_ARRIERE = 0.87f

        private const val CARTES_VUES = 8
        private const val LEVEES = 3

        /** L'inclinaison de chaque carte due, en degrés : irrégulière, comme à la main. */
        private val PENCHES = floatArrayOf(-4f, 3f, -2f)

        /** L'espacement des fibres, en densités. Voir [bois]. */
        private const val PERIODE = 46f

        private const val DESSUS_CLAIR = 0xFFC08A4E.toInt()
        private const val DESSUS_SOMBRE = 0xFF9A6A38.toInt()
        private const val CLOISON_CLAIR = 0xFFCE9A5E.toInt()
        private const val CLOISON_SOMBRE = 0xFFA5743E.toInt()
        private const val MUR_HAUT = 0xFF8A5E30.toInt()
        private const val MUR_OMBRE = 0xFF5F3F1E.toInt()
        private const val FOND_ARRIERE = 0xFF6B4926.toInt()
        private const val FOND_AVANT = 0xFF3A2410.toInt()

        private const val CARTE_CLAIR = 0xFFF6EEDC.toInt()
        private const val CARTE_SOMBRE = 0xFFD9C8A6.toInt()
        private const val CARTE_TRANCHE = 0xFFFFFBF0.toInt()
        private const val CARTE_DUE_CLAIR = 0xFFFFFFFF.toInt()
        private const val CARTE_DUE_SOMBRE = 0xFFE4D8F2.toInt()

        // Le laiton porte l'impératif que la pastille portait : il doit se lire,
        // donc il est franchement clair et la gravure franchement sombre. Un
        // laiton « juste » plus terne était plus crédible et illisible.
        private const val LAITON_CLAIR = 0xFFF2D778.toInt()
        private const val LAITON_SOMBRE = 0xFFB8912E.toInt()
        private const val LAITON_TERNE = 0xFFB5AC92.toInt()
        private const val LAITON_TERNE_BAS = 0xFF8C836B.toInt()
        private const val GRAVURE = 0xFF31220A.toInt()
        private const val GRAVURE_TERNE = 0xFF413C2E.toInt()

        /** L'encre des légendes : du bois brûlé, lisible sur le dessus clair. */
        private const val BRULURE = 0xFF2E1A08.toInt()

        /** Le lustre du bois ciré : large et faible, à l'inverse du reflet de l'or. */
        private const val LUSTRE = 0x40FFFFFF

        private var fibresCache: Bitmap? = null

        /**
         * Les fibres du bois, fabriquées une fois pour toute l'application.
         *
         * Une valeur par ligne, donc des bandes horizontales que la matrice
         * d'un [BitmapShader] oriente et espace ensuite par face. Deux pixels de
         * large suffisent : la bande étant constante sur la ligne, la répétition
         * en x est invisible, et une largeur plus grande ne ferait
         * qu'occuper de la mémoire.
         *
         * La graine est **fixée** : le bois d'un objet ne change pas d'un
         * lancement à l'autre, et un grain tiré au hasard à chaque ouverture se
         * remarquerait précisément parce qu'il est censé être de la matière.
         */
        private fun fibres(): Bitmap = fibresCache ?: creerFibres().also { fibresCache = it }

        private fun creerFibres(): Bitmap {
            val hauteur = 256
            val pixels = IntArray(2 * hauteur)
            val sort = Random(20260914L)
            for (y in 0 until hauteur) {
                val t = y / hauteur.toFloat() * 6.2831855f
                var v = sin(t * 3f) * 0.36f +
                    sin(t * 7.3f) * 0.24f +
                    sin(t * 17.1f) * 0.13f +
                    sin(t * 31.7f) * 0.07f
                v += (sort.nextFloat() - 0.5f) * 0.34f
                val a = (((v + 1f) * 0.5f).coerceIn(0f, 1f) * 92f).toInt()
                val couleur = (a shl 24) or 0x2E1B0C
                pixels[y * 2] = couleur
                pixels[y * 2 + 1] = couleur
            }
            return Bitmap.createBitmap(pixels, 2, hauteur, Bitmap.Config.ARGB_8888)
        }
    }
}

/**
 * L'étiquette d'un casier, et sa planche.
 *
 * Le libellé dit le **rythme**, jamais le numéro de boîte : « revu chaque
 * semaine » se comprend sans rien savoir de Leitner, « boîte 2 » demande qu'on
 * ait lu la documentation. La planche, elle, est la barre de six segments que
 * chaque vignette porte déjà sur son cadre — le casier et les cartes qu'on y
 * trouve disent donc la même chose de la même façon, ce qui est la seule raison
 * de ne pas avoir inventé un autre indicateur ici.
 *
 * Fonction de paquet et non méthode de fragment : l'étagère du carnet et
 * l'écran de la boîte l'affichent tous les deux, et une étiquette recopiée dans
 * les deux aurait fini par diverger sur un seul des deux écrans.
 */
internal fun etiquetteCasier(ctx: Context, boite: Int, combien: Int, dues: Int): View {
    val d = ctx.resources.displayMetrics.density
    fun dp(v: Float) = (v * d).toInt()
    val accent = Carnet.COULEUR
    val acquis = boite >= Sonje.BOITE_ACQUISE
    val vide = combien == 0

    val bloc = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(dp(12f), dp(16f), dp(12f), dp(10f)) }
    }

    bloc.addView(LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.BOTTOM
        addView(TextView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
            text = if (acquis) "Acquis"
            else "Revient dans ${rythmeLong(Sonje.INTERVALLES[boite])}"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(if (vide) Color.parseColor("#9E9E9E") else accent)
        })
        addView(TextView(ctx).apply {
            text = when {
                vide -> "—"
                dues > 0 -> "$combien · $dues à revoir"
                else -> "$combien"
            }
            textSize = 13f
            setTextColor(
                if (vide) Color.parseColor("#BDBDBD") else Color.parseColor("#616161")
            )
        })
    })

    bloc.addView(LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(5f)
        ).apply { topMargin = dp(7f) }
        for (i in 0 until Sonje.BOITE_ACQUISE) {
            addView(View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.MATCH_PARENT, 1f
                ).apply { if (i > 0) leftMargin = dp(2f) }
                background = GradientDrawable().apply {
                    cornerRadius = 2f * d
                    setColor(
                        when {
                            i >= boite -> 0x22000000
                            vide -> eclaircir(accent, 0.6f)
                            else -> accent
                        }
                    )
                }
            })
        }
    })
    return bloc
}

/**
 * L'intervalle d'un casier, dit en français plutôt qu'en jours bruts.
 *
 * Dérivé de [Sonje.INTERVALLES] et non écrit à la main : une suite
 * d'intervalles retouchée laisserait sinon sept libellés qui mentent, sans rien
 * casser au passage.
 */
internal fun rythmeLong(jours: Int): String {
    val (n, unite) = arrondiRythme(jours)
    return when (unite) {
        UniteRythme.JOUR -> if (n == 1) "1 jour" else "$n jours"
        UniteRythme.SEMAINE -> if (n == 1) "1 semaine" else "$n semaines"
        UniteRythme.MOIS -> "$n mois"
    }
}

/**
 * Le même délai en mots courts, pour la légende gravée sous un casier, où la
 * place est celle d'une fente.
 *
 * Les deux formes passent par **un seul** arrondi, [arrondiRythme]. Elles
 * disaient « 2 sem. » sur la boîte et « tous les 16 jours » dans le casier
 * ouvert : deux chiffres pour le même casier, dont le joueur ne peut deviner
 * qu'ils désignent la même chose.
 */
internal fun rythmeCourt(jours: Int): String {
    val (n, unite) = arrondiRythme(jours)
    return when (unite) {
        UniteRythme.JOUR -> if (n == 1) "1 jour" else "$n jours"
        UniteRythme.SEMAINE -> "$n sem."
        UniteRythme.MOIS -> "$n mois"
    }
}

private enum class UniteRythme { JOUR, SEMAINE, MOIS }

/** Arrondi à la semaine ou au mois : « 2 semaines » pour seize jours dit le rythme, et le rythme est tout ce qu'on lit là. */
private fun arrondiRythme(jours: Int): Pair<Int, UniteRythme> = when {
    jours <= 1 -> 1 to UniteRythme.JOUR
    jours < 7 -> jours to UniteRythme.JOUR
    jours < 30 -> (jours + 3) / 7 to UniteRythme.SEMAINE
    else -> (jours + 15) / 30 to UniteRythme.MOIS
}
