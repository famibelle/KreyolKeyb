package com.example.kreyolkeyboard.carnet

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View

/**
 * Les émaux des écus de la carte : la jauge de rareté ([EtoilesRarete]), le
 * nombre de vues gravé ([ChiffreGrave]) et le niveau de révision
 * ([JaugeNiveau]). Repris de LuxKeyb 37.8.0 (v24.0.0), sans le type et le
 * coût de sa carte imprimée, qui n'ont pas d'usage ici.
 *
 * Une forme plate, noire ou rouge, posée sur l'or ciselé du cadre, était le
 * seul élément de la carte qui n'était pas « travaillé », et le rond du coût
 * et le triangle du type n'avaient pas la même taille (propriétaire,
 * 2026-10-27). Chaque symbole est donc un émail serti, comme les gemmes et le
 * médaillon : une ombre portée, un dégradé clair en haut à gauche, un reflet,
 * une sertissure sombre. Tous tiennent dans la même hauteur, 11 à 13,6 unités
 * (environ 2,5 mm).
 *
 * Tout se dessine en unités de carte, rapportées à la hauteur de la case
 * ([Ornement.ECU_D_IMPRIME_TEXTE], [CASE] unités).
 */
internal object Email {

    /** La hauteur de la case des émaux, en unités de carte. */
    const val CASE = 16f

    private val boite = RectF()

    fun melange(a: Int, b: Int, t: Float): Int = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt()
    )

    /** Peint [chemin], déjà en pixels, comme un émail serti de [couleur]. */
    fun peindre(c: Canvas, p: Paint, chemin: Path, couleur: Int, trait: Int, u: Float) {
        chemin.computeBounds(boite, true)
        p.shader = null
        p.style = Paint.Style.FILL
        // L'ombre portée, vers le bas à droite comme la lumière du carnet.
        p.color = 0x59000000
        c.save()
        c.translate(0.5f * u, 0.7f * u)
        c.drawPath(chemin, p)
        c.restore()
        // L'émail bombé.
        p.color = Color.BLACK
        p.shader = LinearGradient(
            boite.left, boite.top, boite.right, boite.bottom,
            intArrayOf(melange(couleur, Color.WHITE, 0.45f), couleur, melange(couleur, Color.BLACK, 0.45f)),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        c.drawPath(chemin, p)
        p.shader = null
        // Le reflet, dans le quart haut gauche, sans sortir de la forme.
        c.save()
        c.clipPath(chemin)
        p.color = 0x80FFFFFF.toInt()
        c.drawOval(
            boite.left + boite.width() * 0.14f, boite.top + boite.height() * 0.1f,
            boite.left + boite.width() * 0.56f, boite.top + boite.height() * 0.4f, p
        )
        c.restore()
        // La sertissure.
        p.style = Paint.Style.STROKE
        p.strokeJoin = Paint.Join.ROUND
        p.strokeWidth = 0.9f * u
        p.color = trait
        c.drawPath(chemin, p)
        p.style = Paint.Style.FILL
    }
}

/**
 * Une pièce d'or : le coût d'une carte se paie en pièces ([PictoCout]). Un
 * émail d'or plus chaud que le métal de l'écu, cerclé de sombre pour s'en
 * détacher, avec l'anneau gravé d'une pièce.
 */
internal fun piece(c: Canvas, p: Paint, x: Float, y: Float, r: Float, trait: Int, u: Float) {
    Email.peindre(c, p, Path().apply { addCircle(x, y, r, Path.Direction.CW) }, 0xFFF2B705.toInt(), trait, u)
    p.style = Paint.Style.STROKE
    p.strokeWidth = 0.6f * u
    p.color = 0xFF9A6400.toInt()
    c.drawCircle(x, y, r * 0.68f, p)
    p.style = Paint.Style.FILL
}

/**
 * La rareté d'une carte, sur la ligne du bas : une **jauge de quatre places
 * d'étoile**, dont autant sont remplies que le palier le dit (★ commun, ★★ peu
 * commun, ★★★ rare, ★★★★ très rare).
 *
 * Le maximum se voit, comme sur une jauge : une carte commune montre les trois
 * étoiles qui lui manquent. Étoile pleine : l'émail d'or des pièces ; étoile
 * vide : un léger creux dans le métal. Calée à droite de la ligne, à la même
 * place sur toutes les cartes ; le mot du palier vient juste avant.
 *
 * Quatre places et non cinq comme côté LuxKeyb, qui a une ultra rare : une
 * cinquième étoile qu'aucune carte ne peut remplir promettrait une rareté qui
 * n'existe pas.
 */
class EtoilesRarete(context: Context, private val nombre: Int, private val trait: Int) : View(context) {

    companion object {
        const val PLACES = 4
        /** La largeur d'une place, en unités de carte. */
        const val PAS = 8.2f
        /** La hauteur de la ligne du bas, en unités de carte (Ornement.SERIE_D). */
        private const val HAUTEUR = 9.5f
    }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        val u = height / HAUTEUR
        val pas = width / PLACES.toFloat()
        val rayon = minOf(pas * 0.48f, height * 0.5f)
        val cy = height / 2f
        for (k in 0 until PLACES) {
            val etoile = etoile(pas * (k + 0.5f), cy + rayon * 0.06f, rayon, rayon * 0.42f)
            if (k < nombre) Email.peindre(canvas, p, etoile, 0xFFF2B705.toInt(), trait, u) else creux(canvas, etoile, u)
        }
    }

    /** Une place vide : le creux d'une étoile, sa lèvre claire en bas. */
    private fun creux(c: Canvas, chemin: Path, u: Float) {
        p.shader = null
        p.style = Paint.Style.FILL
        p.color = 0x33000000
        c.drawPath(chemin, p)
        p.style = Paint.Style.STROKE
        p.strokeJoin = Paint.Join.ROUND
        p.strokeWidth = 0.5f * u
        p.color = 0x99FFFFFF.toInt()
        c.save()
        c.translate(0f, 0.45f * u)
        c.drawPath(chemin, p)
        c.restore()
        p.color = 0x59000000
        p.strokeWidth = 0.45f * u
        c.drawPath(chemin, p)
        p.style = Paint.Style.FILL
    }

    private fun etoile(cx: Float, cy: Float, rayon: Float, creux: Float): Path = Path().apply {
        for (k in 0 until 10) {
            val a = k * Math.PI.toFloat() / 5f - Math.PI.toFloat() / 2f
            val r = if (k % 2 == 0) rayon else creux
            val x = cx + kotlin.math.cos(a) * r
            val y = cy + kotlin.math.sin(a) * r
            if (k == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
}

/**
 * Un nombre gravé dans un écu de la carte de l'appli (l'écu VUES) : une lèvre
 * claire sous un creux du ton sombre du métal, comme le nom dans la plaque.
 * En blanc, comme avant, il se lisait mal sur l'or clair : c'est la leçon du
 * premier BAT de la carte imprimée (2026-10-26), reprise ici le 2026-10-27.
 */
class ChiffreGrave(context: Context, private val texte: String, private val metal: Ornement.Metal) : View(context) {

    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }

    override fun onDraw(canvas: Canvas) {
        val u = height / Email.CASE
        val taille = 12.5f * u
        p.textSize = taille
        val dispo = width - 8f * u
        if (p.measureText(texte) > dispo) p.textSize = taille * dispo / p.measureText(texte)
        val base = 7.6f * u + p.textSize * 0.36f
        p.color = metal.hi
        canvas.drawText(texte, width / 2f, base + 0.7f * u, p)
        p.color = metal.trait
        canvas.drawText(texte, width / 2f, base, p)
    }
}

/**
 * Le niveau d'une carte de l'appli, dans l'écu NIVEAU : une jauge de six
 * cases, une par casier de la Boîte de Leitner. Une case se remplit à chaque
 * casier franchi : vide pour une carte neuve, les six pleines quand le mot
 * est acquis. Remplir jusqu'au casier courant aurait rempli la jauge dès le
 * dernier casier, et une carte acquise n'aurait pas eu l'air différente.
 *
 * L'idée vient de la jauge de pièces de la carte imprimée (propriétaire,
 * 2026-10-27) : le chiffre « 3 » ne disait pas sur combien, la jauge montre
 * le chemin parcouru et ce qui reste. Des cases plutôt que des ronds : ce
 * sont les casiers de la boîte. Remplies de l'or des étoiles de rareté, vides
 * en creux dans le métal.
 */
class JaugeNiveau(context: Context, private val remplies: Int, private val trait: Int) : View(context) {

    companion object {
        /** Autant de cases que de casiers : Sonje.BOITE_ACQUISE. */
        val CASES = Sonje.BOITE_ACQUISE
        private const val COTE = 4.6f
        private const val PAS = 6f
    }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val carre = RectF()

    override fun onDraw(canvas: Canvas) {
        val u = height / Email.CASE
        // Centrée une unité au-dessus du milieu de la case : plus bas, les
        // cases des bords mordaient sur l'arrondi de l'écu de l'appli.
        val cy = 7f * u
        val depart = width / 2f - (CASES - 1) * PAS * u / 2f
        val demi = COTE / 2f * u
        for (k in 0 until CASES) {
            val x = depart + k * PAS * u
            carre.set(x - demi, cy - demi, x + demi, cy + demi)
            val chemin = Path().apply { addRoundRect(carre, 1f * u, 1f * u, Path.Direction.CW) }
            if (k < remplies) Email.peindre(canvas, p, chemin, 0xFFF2B705.toInt(), trait, u)
            else {
                p.shader = null
                p.style = Paint.Style.FILL
                p.color = 0x33000000
                canvas.drawPath(chemin, p)
                p.style = Paint.Style.STROKE
                p.strokeWidth = 0.5f * u
                p.color = 0x99FFFFFF.toInt()
                canvas.save()
                canvas.translate(0f, 0.4f * u)
                canvas.drawPath(chemin, p)
                canvas.restore()
                p.color = 0x59000000
                canvas.drawPath(chemin, p)
                p.style = Paint.Style.FILL
            }
        }
    }
}
