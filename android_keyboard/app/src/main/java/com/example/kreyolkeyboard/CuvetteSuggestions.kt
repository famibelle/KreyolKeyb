package com.example.kreyolkeyboard

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable

/**
 * Fond de la barre de suggestions, dessiné comme un plateau creusé dans le
 * clavier (v17.0.0).
 *
 * Remplace l'ombre portée de la 16.0.0, qui disait l'inverse. Une surface ne
 * peut pas être à la fois posée au-dessus du clavier et creusée dedans : la
 * 16.0.0 faisait tomber une ombre de la barre sur les touches, celle-ci creuse
 * la barre et rend aux touches le relief. Le travail n'est pas perdu, il change
 * de signe : ce qui était la retombée d'ombre sous la barre est devenu le
 * liséré éclairé de sa paroi basse.
 *
 * ### Les trois signaux, et pourquoi il les faut tous les trois
 *
 * Avec deux sur trois, l'œil voit un dégradé et pas un volume.
 *
 * 1. **Le fond du plateau est plus sombre que le fond du clavier.** C'est le
 *    signal principal, et c'est un renversement : jusqu'à la 16.0.0 la barre
 *    était plus claire que les touches dans les deux thèmes, ce qui la disait
 *    surélevée.
 * 2. **Une ombre interne le long du bord haut**, la paroi supérieure qui porte
 *    son ombre sur le fond.
 * 3. **Un liséré clair le long du bord bas**, la paroi inférieure qui prend la
 *    lumière. C'est celui qu'on oublie, et c'est celui qui fait basculer la
 *    lecture.
 *
 * ### La margelle
 *
 * Un creux a besoin d'un rebord visible, faute de quoi rien ne porte l'ombre.
 * Le service encastre donc la barre latéralement, et le fond du clavier
 * apparaît de chaque côté. Le bord bas est bordé par le clavier lui-même. Le
 * bord haut, lui, n'a pas de margelle possible : c'est le bord de la fenêtre de
 * saisie. D'où les **angles arrondis en bas seulement** : arrondir aussi le
 * haut laisserait voir l'application au travers, et un plateau dont la paroi
 * haute sort de l'écran est de toute façon ce que la géométrie décrit.
 *
 * ### Dessin sans découpe
 *
 * Le liséré suit la courbe des angles bas, ce qu'un `clipPath` obtiendrait au
 * prix d'un bord crénelé. Deux tracés pleins suffisent et restent lissés : le
 * plateau entier peint en couleur de liséré, puis le même tracé décalé vers le
 * haut de son épaisseur et peint en couleur de fond. Ne reste éclairée que la
 * bande courbe du bas.
 */
class CuvetteSuggestions(
    private val fond: Int,
    private val ombre: Int,
    private val lisere: Int,
    private val ombrePx: Int,
    private val liserePx: Int,
    private val rayonPx: Float
) : Drawable() {

    private val pinceau = Paint(Paint.ANTI_ALIAS_FLAG)
    private val chemin = Path()
    private var degrade: Shader? = null

    override fun onBoundsChange(bounds: Rect) {
        degrade = null
    }

    /** Le contour du plateau : angles vifs en haut, arrondis en bas. */
    private fun tracer(cadre: RectF): Path {
        chemin.reset()
        chemin.addRoundRect(
            cadre,
            floatArrayOf(
                0f, 0f,                 // haut gauche
                0f, 0f,                 // haut droit
                rayonPx, rayonPx,       // bas droit
                rayonPx, rayonPx        // bas gauche
            ),
            Path.Direction.CW
        )
        return chemin
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return

        val cadre = RectF(
            b.left.toFloat(), b.top.toFloat(),
            b.right.toFloat(), b.bottom.toFloat()
        )

        pinceau.shader = null
        pinceau.color = lisere
        canvas.drawPath(tracer(cadre), pinceau)

        // Le même tracé remonté de l'épaisseur du liséré : il recouvre tout
        // sauf la bande du bas, qui suit donc la courbe des angles. Son bord
        // haut sort des bornes, ce qui est sans effet, le haut étant à vif.
        cadre.offset(0f, -liserePx.toFloat())
        pinceau.color = fond
        canvas.drawPath(tracer(cadre), pinceau)

        // Ombre interne du bord haut. Un rectangle simple suffit : les angles
        // hauts sont à vif, il n'y a aucune courbe à épouser ici.
        val basDeLOmbre = (b.top + ombrePx).toFloat()
        val teinte = degrade ?: LinearGradient(
            0f, b.top.toFloat(), 0f, basDeLOmbre,
            ombre,
            // Même teinte, alpha nul, pour que le dégradé s'éteigne au lieu de
            // virer vers un noir transparent qui laisserait un voile.
            ombre and 0x00FFFFFF,
            Shader.TileMode.CLAMP
        ).also { degrade = it }

        // Opaque avant de poser le shader : l'alpha du pinceau multiplie celui
        // du dégradé, et il porte encore celui du fond dessiné juste avant.
        pinceau.color = Color.BLACK
        pinceau.shader = teinte
        canvas.drawRect(
            b.left.toFloat(), b.top.toFloat(),
            b.right.toFloat(), basDeLOmbre,
            pinceau
        )
        pinceau.shader = null
    }

    /**
     * Translucide, et il faut le dire : les angles bas laissent voir le fond du
     * clavier. Annoncer `OPAQUE` ferait sauter le dessin de ce qui est derrière
     * et les coins se peindraient en noir.
     */
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    // Ce fond n'est ni animé ni teinté par un appelant : les deux réglages que
    // Drawable impose d'exposer n'ont rien à piloter ici.
    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit
}
