package com.example.kreyolkeyboard.carnet

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View

/**
 * La place d'une carte qui manque encore à une série.
 *
 * Elle a la taille et l'arrondi d'une vignette, pour que la page d'album se
 * lise comme une grille dont on n'a pas encore tout collé, et elle porte un
 * **indice** : le nombre en chiffres, la silhouette du dessin, l'emblème du
 * jeu. Jamais le mot : c'est lui qu'on gagne.
 *
 * Elle est dessinée et non composée de vues, parce qu'une page des
 * enluminures en pose une centaine.
 */
class EmplacementVide(
    context: Context,
    private val serie: Serie,
    private val cle: String
) : View(context) {

    private val pinceau = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cadre = RectF()
    private val pointilles = DashPathEffect(floatArrayOf(10f, 7f), 0f)

    /** Le dessin, mis à l'échelle à la première trame qui connaît la taille. */
    private var silhouette: Path? = null
    private var silhouettePour = -1

    private val jeu: JeuCarte? = if (serie == Serie.JEUX) JeuCarte.parId(cle) else null
    private val paysage: Pair<Lieu, Rarete>? = if (serie == Serie.PAYSAGES) Series.paysageDe(cle) else null

    /** Le lieu en grisé : on reconnaît l'endroit et l'heure, pas la carte. */
    private val gris = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        alpha = 120
    }
    private val decoupe = Path()
    private var image: android.graphics.Bitmap? = null

    init {
        // Le lecteur d'écran dit ce qui manque, comme l'œil le voit.
        contentDescription = when (serie) {
            Serie.NOMBRES -> context.getString(com.example.kreyolkeyboard.R.string.emplacement_nombre, cle)
            Serie.JEUX -> context.getString(
                com.example.kreyolkeyboard.R.string.emplacement_jeu, jeu?.libelle(context) ?: cle
            )
            Serie.ENLUMINURES -> context.getString(com.example.kreyolkeyboard.R.string.emplacement_dessin)
            Serie.PAYSAGES -> context.getString(
                com.example.kreyolkeyboard.R.string.emplacement_paysage,
                paysage?.first?.nom ?: cle,
                paysage?.let { heure(it.second) } ?: ""
            )
        }
    }

    override fun onMeasure(largeurSpec: Int, hauteurSpec: Int) {
        // Carré, comme la vignette (300 × 300 unités de carte).
        val l = MeasureSpec.getSize(largeurSpec)
        setMeasuredDimension(l, l)
    }

    override fun onDraw(canvas: Canvas) {
        val l = width.toFloat()
        if (l <= 0f) return
        val d = resources.displayMetrics.density
        val rayon = l * Ornement.RAYON / Ornement.LARGEUR

        cadre.set(d, d, l - d, l - d)
        pinceau.style = Paint.Style.FILL
        pinceau.pathEffect = null
        pinceau.color = FOND
        canvas.drawRoundRect(cadre, rayon, rayon, pinceau)
        if (serie == Serie.PAYSAGES) lieu(canvas, rayon)

        pinceau.style = Paint.Style.STROKE
        pinceau.strokeWidth = 1.5f * d
        pinceau.color = TRAIT
        pinceau.pathEffect = pointilles
        canvas.drawRoundRect(cadre, rayon, rayon, pinceau)
        pinceau.pathEffect = null
        pinceau.style = Paint.Style.FILL

        when (serie) {
            Serie.NOMBRES -> texte(canvas, cle, l * 0.34f, l / 2f, gras = true)
            Serie.ENLUMINURES -> dessin(canvas, l)
            Serie.PAYSAGES -> paysage?.let {
                // Un bandeau clair sous le nom : sur le lieu grisé, le gris du
                // texte se perdait dans celui des toits.
                pinceau.color = FOND
                pinceau.alpha = 225
                canvas.drawRect(cadre.left + 2 * d, l * 0.80f, cadre.right - 2 * d, l * 0.96f, pinceau)
                pinceau.alpha = 255
                texte(canvas, it.first.nom, l * 0.09f, l * 0.88f, gras = true)
            }
            Serie.JEUX -> {
                texte(canvas, jeu?.emoji ?: "?", l * 0.26f, l * 0.46f, gras = false, opaque = true)
                texte(canvas, jeu?.libelle(context) ?: cle, l * 0.09f, l * 0.74f, gras = true)
            }
        }
    }

    private fun texte(
        c: Canvas, contenu: String, corps: Float, y: Float, gras: Boolean, opaque: Boolean = false
    ) {
        pinceau.textAlign = Paint.Align.CENTER
        pinceau.textSize = corps
        pinceau.typeface = if (gras) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        pinceau.color = TRAIT
        // Un emoji garde ses couleurs, voilées : un emblème gris ne se reconnaît plus.
        pinceau.alpha = if (opaque) 110 else 255
        val m = pinceau.fontMetrics
        c.drawText(contenu, width / 2f, y - (m.ascent + m.descent) / 2f, pinceau)
        pinceau.alpha = 255
    }

    private fun heure(r: Rarete): String = context.getString(
        when (r) {
            Rarete.COMMUN -> com.example.kreyolkeyboard.R.string.heure_0
            Rarete.PEU_COMMUN -> com.example.kreyolkeyboard.R.string.heure_1
            Rarete.RARE -> com.example.kreyolkeyboard.R.string.heure_2
            Rarete.TRES_RARE -> com.example.kreyolkeyboard.R.string.heure_3
        }
    )

    /** Le lieu de la case, à son heure, en grisé dans le cadre arrondi. */
    private fun lieu(c: Canvas, rayon: Float) {
        val (lieu, rarete) = paysage ?: return
        // Rendu à demi-définition, et gardé par la case : c'est un indice
        // grisé, pas une illustration, et soixante cases d'un coup ne
        // doivent pas vider le cache des cartes.
        val l = (cadre.width() / 2f).toInt()
        val h = (cadre.height() / 2f).toInt()
        val image = this.image?.takeIf { it.width == l && it.height == h } ?: run {
            Paysages.rendu(lieu, Heure.de(rarete, null), l, h)
        }?.also { this.image = it } ?: return
        decoupe.reset()
        decoupe.addRoundRect(cadre, rayon, rayon, Path.Direction.CW)
        c.save()
        c.clipPath(decoupe)
        c.drawBitmap(image, null, cadre, gris)
        c.restore()
    }

    /** La silhouette du meuble, à plat et sans gravure : une forme en creux. */
    private fun dessin(c: Canvas, l: Float) {
        val taille = l.toInt()
        if (silhouettePour != taille) {
            silhouettePour = taille
            silhouette = Meubles.chemin(cle)?.let { brut ->
                val cote = l * 0.58f
                val m = Matrix().apply {
                    setScale(cote / Meubles.COTE, cote / Meubles.COTE)
                    postTranslate(l / 2f, l / 2f)
                }
                Path().also {
                    brut.transform(m, it)
                    it.fillType = Path.FillType.EVEN_ODD
                }
            }
        }
        val p = silhouette ?: return texte(c, "?", l * 0.3f, l / 2f, gras = true)
        pinceau.color = SILHOUETTE
        c.drawPath(p, pinceau)
    }

    private companion object {
        val FOND = Color.parseColor("#EFEBE4")
        val TRAIT = Color.parseColor("#A89F90")
        val SILHOUETTE = Color.parseColor("#D3CBBE")
    }
}
