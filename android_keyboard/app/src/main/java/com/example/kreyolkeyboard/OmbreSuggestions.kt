package com.example.kreyolkeyboard

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Drawable

/**
 * Fond du clavier, avec la retombée d'ombre de la barre de suggestions sur ses
 * premiers millimètres (v16.0.0).
 *
 * La barre était de plain-pied avec les touches : rien ne disait qu'elle est une
 * surface à part, posée au-dessus du clavier. Les deux thèmes la peignaient déjà
 * plus claire que le fond des touches, ce qui est la moitié de l'effet ; il lui
 * manquait ce qu'une surface surélevée projette en dessous d'elle.
 *
 * ### Pourquoi un `Drawable` écrit à la main
 *
 * Les trois voies plus courtes ont toutes été écartées :
 *
 * - **`View.setElevation()`** laisse le système dessiner l'ombre, mais elle est
 *   noire, et du noir sur le `#131313` du thème sombre ne se voit pas. Elle
 *   n'est teintable qu'à partir de l'API 28, alors que ce clavier descend à 21.
 * - **Une vue dédiée** sous la barre coûterait sa hauteur au budget vertical,
 *   que `computeAvailableRowsHeight()` compte déjà au pixel près.
 * - **Un `LayerDrawable`** ne sait cantonner une couche au haut de ses bornes
 *   qu'avec `setLayerHeight`, API 23.
 *
 * Peindre soi-même coûte trente lignes et n'a aucune de ces limites : les
 * couleurs viennent de la palette, donc l'ombre se lit dans les deux thèmes, et
 * elle se pose sur le rembourrage que le clavier réserve déjà au-dessus de sa
 * première rangée, donc elle ne prend la place de rien.
 *
 * @param fond couleur derrière les touches, peinte sur toute la surface
 * @param filet trait qui marque le bord bas de la barre, la partie la plus dense
 *   de l'ombre
 * @param ombre couleur de départ du dégradé, qui s'éteint vers le bas ; son
 *   canal alpha porte toute l'intensité
 */
class OmbreSuggestions(
    private val fond: Int,
    private val filet: Int,
    private val ombre: Int,
    private val filetPx: Int,
    private val degradePx: Int
) : Drawable() {

    private val pinceau = Paint()

    // Reconstruit à chaque changement de bornes : ses deux extrémités sont
    // exprimées en coordonnées absolues du canevas, pas en proportions.
    private var degrade: Shader? = null

    override fun onBoundsChange(bounds: Rect) {
        degrade = null
    }

    override fun draw(canvas: Canvas) {
        val cadre = bounds
        if (cadre.isEmpty) return

        pinceau.shader = null
        pinceau.color = fond
        canvas.drawRect(cadre, pinceau)

        val basDuFilet = (cadre.top + filetPx).toFloat()
        pinceau.color = filet
        canvas.drawRect(
            cadre.left.toFloat(), cadre.top.toFloat(),
            cadre.right.toFloat(), basDuFilet,
            pinceau
        )

        val basDuDegrade = basDuFilet + degradePx
        val teinte = degrade ?: LinearGradient(
            0f, basDuFilet, 0f, basDuDegrade,
            ombre,
            // Même teinte, alpha nul : le dégradé s'éteint au lieu de virer au
            // noir transparent, ce qui laisserait un halo sur un fond clair.
            ombre and 0x00FFFFFF,
            Shader.TileMode.CLAMP
        ).also { degrade = it }

        // Opaque avant de poser le shader : l'alpha du pinceau multiplie celui
        // du dégradé, et il porte encore les 14 % du filet dessiné juste avant.
        pinceau.color = Color.BLACK
        pinceau.shader = teinte
        canvas.drawRect(
            cadre.left.toFloat(), basDuFilet,
            cadre.right.toFloat(), basDuDegrade,
            pinceau
        )
        pinceau.shader = null
    }

    /** Le fond couvre toute la surface : rien ne transparaît jamais. */
    override fun getOpacity(): Int = PixelFormat.OPAQUE

    // Ce fond n'est ni animé ni teinté par un appelant : les deux réglages que
    // Drawable impose d'exposer n'ont rien à piloter ici.
    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit
}
