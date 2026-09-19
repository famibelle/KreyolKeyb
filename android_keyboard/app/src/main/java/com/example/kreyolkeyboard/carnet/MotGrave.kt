package com.example.kreyolkeyboard.carnet

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.text.TextUtils
import android.view.Gravity
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.max

/**
 * Le mot de la plaque, **gravé dans sa matière** plutôt qu'imprimé dessus
 * (demande du propriétaire, 2026-09-19).
 *
 * Une lettre creusée se lit à trois choses, toutes tirées de la lumière qui
 * vient d'en haut, celle que le cadre et les rivets supposent déjà :
 *
 * - **le fond du sillon** est la matière elle-même, assombrie, et non une
 *   encre noire posée dessus : du bois brûlé dans le chêne, de l'or bruni
 *   dans l'or. C'est ce qui fait « dans » plutôt que « sur » ;
 * - **la lèvre du haut porte ombre** dans le creux : le haut de chaque lettre
 *   est plus sombre que son pied, d'où le dégradé vertical du remplissage ;
 * - **la lèvre du bas prend la lumière** : un liseré clair décalé vers le bas,
 *   qui dépasse sous chaque trait. Sans lui, une lettre foncée se lit en
 *   relief aussi bien qu'en creux ; c'est lui qui tranche ;
 * - **la tranche du haut est dans le noir** : un filet sombre au-dessus de
 *   chaque trait, la paroi que la lumière ne touche pas. Avec le liseré, le
 *   creux est bordé des deux côtés et prend sa profondeur.
 *
 * Accentué le 2026-09-19 à la demande du propriétaire : lèvre à 7 % du
 * corps au lieu de 5 %, en bande continue, liseré presque opaque, fond plus
 * profond.
 *
 * Les couleurs changent en passant, sans `setTextColor` : celui-ci invalide
 * la vue, et l'appeler depuis `onDraw` la redessinerait sans fin. Un shader
 * prend le pas sur la couleur du pinceau, qui reste opaque.
 */
class MotGrave(context: Context, texte: String, private val support: Ornement.Support) :
    TextView(context) {

    private val fond = melange(support.lo, Color.BLACK, 0.62f)
    private val ombre = melange(support.lo, Color.BLACK, 0.9f)
    private val lumiere = (0xF0 shl 24) or (melange(support.hi, Color.WHITE, 0.85f) and 0xFFFFFF)
    private val arete = (0xB0 shl 24) or (ombre and 0xFFFFFF)
    private val lisere = LinearGradient(0f, 0f, 0f, 1f, lumiere, lumiere, Shader.TileMode.CLAMP)
    private val tranche = LinearGradient(0f, 0f, 0f, 1f, arete, arete, Shader.TileMode.CLAMP)
    private var creux: LinearGradient? = null
    private var creuxPour = Float.NaN

    init {
        text = texte
        setTextColor(Color.BLACK)
        gravity = Gravity.CENTER
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        includeFontPadding = false
    }

    override fun onDraw(canvas: Canvas) {
        val ligne = layout ?: return super.onDraw(canvas)
        val p = paint
        // Le pied de la lettre et le haut de ses capitales, dans le repère où
        // la mise en page dessine (padding déjà appliqué par le TextView).
        val base = ligne.getLineBaseline(0).toFloat()
        if (base != creuxPour) {
            val haut = base - textSize * 0.75f
            creux = LinearGradient(
                0f, haut, 0f, base, intArrayOf(ombre, ombre, fond),
                floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP
            )
            creuxPour = base
        }
        val levre = max(1.5f, textSize * 0.07f)

        // Chaque lèvre est une **bande** collée au trait, pas une copie
        // décalée : un seul décalage de cette taille détache le liseré, et la
        // lettre se lit doublée (deux barres au « e ») au lieu de creusée. On
        // empile donc la copie pixel par pixel jusqu'à la profondeur voulue.
        biseau(canvas, lisere, levre)
        biseau(canvas, tranche, -levre * 0.45f)

        p.shader = creux
        super.onDraw(canvas)
        p.shader = null
    }

    private fun biseau(canvas: Canvas, teinte: Shader, profondeur: Float) {
        paint.shader = teinte
        val pas = if (profondeur > 0) 1f else -1f
        var d = pas
        while (abs(d) <= abs(profondeur) + 0.01f) {
            canvas.save()
            canvas.translate(0f, d)
            super.onDraw(canvas)
            canvas.restore()
            d += pas
        }
    }

    private companion object {
        fun melange(a: Int, b: Int, t: Float): Int = Color.rgb(
            (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt()
        )
    }
}
