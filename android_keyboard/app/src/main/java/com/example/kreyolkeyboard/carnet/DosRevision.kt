package com.example.kreyolkeyboard.carnet

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.RadialGradient
import android.graphics.Shader
import com.example.kreyolkeyboard.R
import kotlin.math.abs

/**
 * Le verso d'une carte de révision : la question, écrite sur le dos du carton.
 *
 * ## Pourquoi un dos, et pourquoi celui-ci
 *
 * La révision retournait déjà ses cartes — sauf qu'il n'y avait rien à
 * retourner. `revelation` faisait partir la carte de `rotationY = -85°` pour
 * l'amener à zéro, mais rien n'occupait ces -85° : la carte surgissait de
 * profil, du néant, et la question d'avant flottait sur un aplat gris. Il ne
 * manquait pas un décor, il manquait **la première moitié du geste**.
 *
 * Ce dos-ci est donc la face question. « Verso » n'y veut pas dire « caché » :
 * le dos porte le mot kréyòl et le recto son sens.
 *
 * ## Ce qu'un dos de révision n'a pas le droit de dire
 *
 * [DosDeCarte], celui de la pochette, porte déjà la règle : un dos qui
 * trahirait la carte supprimerait le seul instant que la pochette fabrique.
 * En révision elle se durcit, parce que la question honnête est « le
 * savez-vous ? » et que toute fuite corrompt l'auto-notation. Deux tentations
 * ont donc été écartées, toutes deux à portée de copier-coller :
 *
 * - **La teinte générative.** [Ornement.teinteDe] la tire des premières
 *   lettres du mot. Un dos teinté serait reconnaissable *par mot* au bout de
 *   quelques semaines — pire encore depuis que la teinte suit le préfixe, qui
 *   réunit les formes d'une même famille : la fuite parfaite, invisible à la
 *   relecture du code.
 * - **Le liseré de rareté.** La pochette l'allume volontairement. Ici,
 *   « cette carte est très rare » se lit « ce mot est difficile », et le
 *   joueur ajuste sa réponse avant d'avoir cherché.
 *
 * Le dos est donc rigoureusement le même pour les douze cartes d'une session.
 * C'est aussi ce qui interdit un dos aux couleurs du jeu : le paquet mélange
 * les sept, et la couleur trahirait la provenance en même temps qu'elle
 * ferait clignoter l'écran. La révision est le rituel de l'application,
 * au-dessus des cinq jeux — d'où le logo, et non l'emoji d'un jeu.
 *
 * ## Le motif
 *
 * Deux angles qui se répondent en diagonale, et entre eux une bande oblique
 * claire où le texte de la question vient naturellement se poser. La
 * structure vient du carnet luxembourgeois ; **les couleurs, non**, et c'est
 * un point à ne pas défaire par inadvertance. Là-bas les deux angles sont le
 * rouge et le bleu clair de l'écu au lion. Les reprendre ici donnerait deux
 * choses fausses à la fois : la composition d'un autre produit, et un rouge
 * sur bleu que l'œil lit comme un drapeau — or la Guadeloupe n'a pas de
 * drapeau officiel, et celui qu'on lui prête le plus souvent porte la fleur
 * de lys, emblème de la période esclavagiste. Un dos de carte n'a aucune
 * raison d'entrer dans ce débat.
 *
 * Les deux angles prennent donc leurs couleurs **dans le logo de
 * l'application** : le vert du mot POTOMITAN et le bleu de l'anneau. Ce sont
 * aussi les deux teintes que le clavier emploie le plus — le vert des
 * suggestions kréyòl, le bleu de la barre d'espace — donc le dos appartient
 * visiblement à cette application-là et à aucune autre.
 *
 * Le logo n'y figure qu'en filigrane très effacé, tiré du `ic_launcher` livré
 * dans l'APK, et il n'est pas retouché : il est posé, à une échelle où sa
 * définition ne se voit pas. Le jour où il existe en `Path` comme le reste du
 * carnet, [lion] est le seul endroit à changer.
 */
class DosRevision(context: Context) : Carton(context) {

    override val hauteurUnites: Float = Ornement.HAUTEUR

    /**
     * Le dos suit toujours la pesanteur : il n'a pas de rareté, donc pas de
     * raison de ne pas y avoir droit, et c'est la surface du carnet qui reste
     * le plus longtemps sous les yeux — tout le temps que le joueur cherche.
     */
    override val suitLaLumiere: Boolean get() = true

    private val pinceau = Paint(Paint.ANTI_ALIAS_FLAG)
    private val carton = RectF(0f, 0f, Ornement.LARGEUR, Ornement.HAUTEUR)
    private val angleVert: Path
    private val angleBleu: Path
    private val fond: LinearGradient

    init {
        setWillNotDraw(false)
        angleVert = angleHautGauche()
        angleBleu = angleBasDroit()
        fond = LinearGradient(
            0f, 0f, Ornement.LARGEUR * 0.6f, Ornement.HAUTEUR,
            BLANC, BLANC_OMBRE, Shader.TileMode.CLAMP
        )
    }

    /**
     * Les deux angles portent eux-mêmes le coin arrondi du carton.
     *
     * La solution évidente aurait été de tracer deux triangles francs et de
     * découper le tout à `clipPath`. Mais une découpe par chemin n'est pas
     * antialiasée : les coins du dos seraient crénelés là où ceux de la face
     * sont lisses, et c'est précisément le genre d'écart d'un pixel qui fait
     * lire le retournement comme deux objets qui se remplacent.
     */
    private fun angleHautGauche(): Path = Path().apply {
        val r = Ornement.RAYON
        moveTo(r, 0f)
        lineTo(ANGLE_HAUT, 0f)
        lineTo(0f, ANGLE_BAS)
        lineTo(0f, r)
        arcTo(RectF(0f, 0f, 2f * r, 2f * r), 180f, 90f)
        close()
    }

    private fun angleBasDroit(): Path = Path().apply {
        val r = Ornement.RAYON
        val l = Ornement.LARGEUR
        val h = Ornement.HAUTEUR
        moveTo(l - r, h)
        lineTo(l - ANGLE_HAUT, h)
        lineTo(l, h - ANGLE_BAS)
        lineTo(l, h - r)
        arcTo(RectF(l - 2f * r, h - 2f * r, l, h), 0f, 90f)
        close()
    }

    /**
     * Le relief du dos, et ce qu'il n'a pas le droit de dire.
     *
     * Le dos a sa propre gravure — le bord du carton, les deux hypoténuses et
     * les deux filets d'or en retrait — et elle est rigoureusement la même
     * pour les douze cartes d'une session. C'est la règle de classe appliquée
     * à la main : un dos dont le nombre de crans suivrait la rareté dirait
     * « ce mot est difficile » par le pouce au lieu de le dire par la
     * couleur, ce qui serait la même fuite déguisée en autre sens.
     *
     * Le dos est imprimé à plat : le bord du carton est sa seule marche, et
     * les filets ne sont que des **accrocs**, une montée et sa descente
     * fondues en un cran par [Ornement.crans], qu'on sent dans les deux sens
     * de la même façon.
     */
    override fun aretes(y: Float): Ornement.Relief {
        val l = Ornement.LARGEUR
        val brut = ArrayList<Ornement.Arete>(12)
        brut.add(Ornement.Arete(Ornement.BORD_CARTE, Ornement.Z_FACE))
        brut.add(Ornement.Arete(l - Ornement.BORD_CARTE, -Ornement.Z_FACE))
        obliqueHaute(brut, y, ANGLE_HAUT, ANGLE_BAS)
        obliqueHaute(brut, y, ANGLE_HAUT - RETRAIT_X, ANGLE_BAS - RETRAIT_Y)
        obliqueBasse(brut, y, ANGLE_HAUT, ANGLE_BAS)
        obliqueBasse(brut, y, ANGLE_HAUT - RETRAIT_X, ANGLE_BAS - RETRAIT_Y)
        return Ornement.crans(brut)
    }

    /** Un filet imprimé : une montée et sa descente, fondues en accroc. */
    private fun filet(brut: MutableList<Ornement.Arete>, x: Float) {
        brut.add(Ornement.Arete(x - 1f, Z_FILET))
        brut.add(Ornement.Arete(x + 1f, -Z_FILET))
    }

    /** Où l'oblique de l'angle haut-gauche coupe la hauteur [y]. */
    private fun obliqueHaute(brut: MutableList<Ornement.Arete>, y: Float, x0: Float, y0: Float) {
        if (y < 0f || y >= y0) return
        filet(brut, x0 * (1f - y / y0))
    }

    /** La même, pour l'angle bas-droit, qui est son symétrique. */
    private fun obliqueBasse(brut: MutableList<Ornement.Arete>, y: Float, x0: Float, y0: Float) {
        val depuisLeBas = Ornement.HAUTEUR - y
        if (depuisLeBas < 0f || depuisLeBas >= y0) return
        filet(brut, Ornement.LARGEUR - x0 * (1f - depuisLeBas / y0))
    }

    /**
     * L'ordre est celui d'une impression : le fond, les deux aplats, les
     * liserés qui les séparent, le filigrane, puis le panneau et le bord.
     *
     * Tout est tracé en unités de carte, comme la face : c'est ce qui rend le
     * dos superposable au recto au pixel près, quelle que soit la taille à
     * laquelle la scène décide de le poser.
     */
    override fun onDraw(canvas: Canvas) {
        val u = width / Ornement.LARGEUR
        if (u <= 0f) return
        val l = Ornement.LARGEUR
        val h = Ornement.HAUTEUR

        canvas.save()
        canvas.scale(u, u)

        pinceau.style = Paint.Style.FILL
        pinceau.shader = fond
        canvas.drawRoundRect(carton, Ornement.RAYON, Ornement.RAYON, pinceau)
        pinceau.shader = null

        // Les deux angles du logo, portés à l'échelle du carton.
        // Dégradés radial pour donner du volume : clair au centre, sombre aux bords.
        pinceau.shader = RadialGradient(
            l * 0.35f, h * 0.35f, 120f,
            intArrayOf(0xFF5FD37A.toInt(), VERT, VERT),
            floatArrayOf(0f, 0.6f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(angleVert, pinceau)
        pinceau.shader = RadialGradient(
            l * 0.65f, h * 0.65f, 120f,
            intArrayOf(0xFF66D9FF.toInt(), BLEU, BLEU),
            floatArrayOf(0f, 0.6f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(angleBleu, pinceau)
        pinceau.shader = null

        // Le liseré blanc détache l'aplat de la bande ; le filet d'or, plus
        // fin et en retrait, est ce qui fait le carton plutôt que l'affiche.
        // Les quatre extrémités tombent sur des bords droits, loin des coins :
        // aucune n'a besoin d'être rattrapée par un arrondi.
        pinceau.style = Paint.Style.STROKE
        pinceau.color = BLANC
        pinceau.strokeWidth = 4f
        canvas.drawLine(ANGLE_HAUT, 0f, 0f, ANGLE_BAS, pinceau)
        canvas.drawLine(l - ANGLE_HAUT, h, l, h - ANGLE_BAS, pinceau)
        pinceau.color = OR
        pinceau.alpha = 140
        pinceau.strokeWidth = 1.2f
        canvas.drawLine(ANGLE_HAUT - RETRAIT_X, 0f, 0f, ANGLE_BAS - RETRAIT_Y, pinceau)
        canvas.drawLine(l - ANGLE_HAUT + RETRAIT_X, h, l, h - ANGLE_BAS + RETRAIT_Y, pinceau)
        pinceau.alpha = 255

        // Le filigrane, dans la bande claire. Il est posé en unités de carte
        // comme tout le reste, donc il suit la mise à l'échelle du canvas.
        lion(context)?.let { marque ->
            val cote = l * 0.62f
            val gauche = (l - cote) / 2f
            val haut = h * 0.30f - cote / 2f
            pinceau.style = Paint.Style.FILL
            pinceau.alpha = FILIGRANE
            canvas.drawBitmap(
                marque, null, RectF(gauche, haut, gauche + cote, haut + cote), pinceau
            )
            pinceau.alpha = 255
        }

        // Le bord, au même retrait que celui de la face.
        pinceau.style = Paint.Style.STROKE
        pinceau.strokeWidth = 1.6f
        pinceau.color = ENCRE
        pinceau.alpha = 46
        canvas.drawRoundRect(
            RectF(1.2f, 1.2f, l - 1.2f, h - 1.2f),
            Ornement.RAYON, Ornement.RAYON, pinceau
        )
        pinceau.alpha = 255

        // La tranche, puis le reflet : le dos étant le même pour toutes les
        // cartes, c'est la seule surface du carnet où le balayage se voit à
        // chaque question et pas seulement sur une rare.
        Ornement.dessinerTranche(canvas, pinceau, assiette, h)
        Ornement.refletBalaye(canvas, pinceau, roulis, h, 0x4A)
        canvas.restore()
    }

    companion object {
        /** Le vert du mot POTOMITAN et le bleu de l'anneau, dans `ic_launcher`. */
        private const val VERT = 0xFF1FA84C.toInt()
        private const val BLEU = 0xFF3197DB.toInt()
        private const val BLANC = 0xFFFFFFFF.toInt()
        private const val BLANC_OMBRE = 0xFFE4E8EC.toInt()
        private const val OR = 0xFFE0B74E.toInt()
        private const val ENCRE = 0xFF1B1610.toInt()

        /** Où les deux angles coupent les bords, en unités de carte. */
        private const val ANGLE_HAUT = 176f
        private const val ANGLE_BAS = 258f

        /**
         * De combien le filet d'or est en retrait du liseré blanc, sur chacun
         * des deux axes. Nommé parce que le tracé et le relief sous le doigt
         * doivent lire les mêmes nombres : un cran qui ne tomberait pas sur
         * son filet se sentirait comme un défaut de l'écran.
         */
        private const val RETRAIT_X = 26f
        private const val RETRAIT_Y = 38f

        /** L'épaisseur d'encre d'un filet, pour le doigt : un accroc léger. */
        private const val Z_FILET = 0.3f

        /** L'opacité du filigrane, sur 255. Voir la note de classe. */
        private const val FILIGRANE = 34

        private var marque: Bitmap? = null
        private var marqueEssayee = false

        /**
         * Le lion, détouré de son fond.
         *
         * `ic_launcher` est livré sur un fond opaque `#F5F7F7` : posé tel quel
         * sur le carton, il y ferait un carré clair, ce qui se lit comme un
         * autocollant et non comme un filigrane. L'alpha est donc recalculé
         * depuis la distance à cette teinte, ce qui a l'avantage de conserver
         * l'antialiasing des traits plutôt que de les découper au seuil.
         *
         * Trente-sept mille pixels parcourus une fois pour la vie du
         * processus, et le résultat est partagé par toutes les cartes.
         */
        private fun lion(context: Context): Bitmap? {
            if (marqueEssayee) return marque
            marqueEssayee = true
            marque = try {
                val source = BitmapFactory.decodeResource(
                    context.resources, R.mipmap.ic_launcher
                ) ?: return null
                val l = source.width
                val h = source.height
                val pixels = IntArray(l * h)
                source.getPixels(pixels, 0, l, 0, 0, l, h)
                for (i in pixels.indices) {
                    val px = pixels[i]
                    val ecart = maxOf(
                        abs(Color.red(px) - 245),
                        abs(Color.green(px) - 247),
                        abs(Color.blue(px) - 247)
                    )
                    val a = minOf(Color.alpha(px), minOf(255, ecart * 255 / SEUIL_FOND))
                    pixels[i] = (a shl 24) or (px and 0x00FFFFFF)
                }
                Bitmap.createBitmap(pixels, l, h, Bitmap.Config.ARGB_8888)
            } catch (e: Throwable) {
                // Un filigrane manquant n'est pas une raison de perdre la
                // question : le dos tient debout sur sa seule géométrie.
                null
            }
            return marque
        }

        /** La largeur de la rampe de détourage, en niveaux. */
        private const val SEUIL_FOND = 58
    }
}
