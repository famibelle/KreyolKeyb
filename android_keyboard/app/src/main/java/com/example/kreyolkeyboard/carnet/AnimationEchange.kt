package com.example.kreyolkeyboard.carnet

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import android.view.animation.LinearInterpolator
import com.example.kreyolkeyboard.R
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * L'échange d'une carte, mimé par deux téléphones côte à côte : l'illustration
 * de la bulle « Donner, étape par étape » de la visite du carnet (23.3.3).
 *
 * Demandée par le propriétaire : la bulle seule passait vite sur ce qui est
 * le geste le moins évident de l'application. La boucle suit les quatre temps
 * numérotés du texte, et le numéro du temps en cours s'allume dessous :
 * 1. le téléphone qui reçoit affiche son code ;
 * 2. celui qui donne le vise, le code passe au vert ;
 * 3. la carte quitte le téléphone qui donne, qui affiche à son tour un code ;
 * 4. celui qui reçoit le vise, et la carte passe d'un écran à l'autre.
 *
 * Animations désactivées dans le système : la dernière image, carte arrivée.
 */
class AnimationEchange(context: Context) : View(context) {

    private val d = resources.displayMetrics.density
    private var t = 0f

    private val animateur = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = DUREE_MS
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { t = it.animatedValue as Float; invalidate() }
    }

    private val corps = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2B2B2B") }
    private val ecran = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F4F1EA") }
    private val module = Paint().apply { color = Color.parseColor("#1C1C1C") }
    private val cadreCarte = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#B8862B") }
    private val ciel = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F6C177") }
    private val mer = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#3F8FB5") }
    private val plaque = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E9DFC8") }
    private val faisceau = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0080FF") }
    private val ligne = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0080FF")
        strokeWidth = 2f * d
    }
    private val vert = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#27864D") }
    private val coche = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * d
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val legende = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6B6B6B")
        textSize = 11f * resources.displayMetrics.scaledDensity
        textAlign = Paint.Align.CENTER
    }
    private val pastille = Paint(Paint.ANTI_ALIAS_FLAG)
    private val chiffre = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 11f * resources.displayMetrics.scaledDensity
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val chemin = Path()
    private val r = RectF()

    private val vous = context.getString(R.string.echange_vous)
    private val autre = context.getString(R.string.echange_autre)

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        if (!ValueAnimator.areAnimatorsEnabled()) t = FIGEE
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (HAUTEUR_DP * d).toInt())
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (ValueAnimator.areAnimatorsEnabled()) animateur.start()
    }

    override fun onDetachedFromWindow() {
        animateur.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val tw = 46f * d
        val th = 80f * d
        val haut = 4f * d
        val ecart = min(w * 0.42f, 170f * d)
        val gauche = RectF(w / 2 - ecart / 2 - tw / 2, haut, w / 2 - ecart / 2 + tw / 2, haut + th)
        val droite = RectF(w / 2 + ecart / 2 - tw / 2, haut, w / 2 + ecart / 2 + tw / 2, haut + th)
        val eg = ecranDe(gauche)
        val ed = ecranDe(droite)

        telephone(canvas, gauche)
        telephone(canvas, droite)

        // Écran du téléphone qui donne : la carte, puis son code.
        if (t < T_DEPART) carte(canvas, eg, 1f)
        else if (t < T_CODE_B) carte(canvas, eg, 1f - (t - T_DEPART) / (T_CODE_B - T_DEPART))
        if (t >= T_CODE_B && t < T_VOL) code(canvas, eg, GRAINE_B, fondu(t, T_CODE_B, 0.04f))

        // Écran du téléphone qui reçoit : son code, puis la carte arrivée.
        if (t < T_VOL) code(canvas, ed, GRAINE_A, fondu(t, 0f, 0.05f))
        if (t >= T_ARRIVEE) carte(canvas, ed, fondu(t, T_ARRIVEE, 0.04f))

        // 2. Vous visez son code ; 4. elle vise le vôtre.
        if (t >= T_SCAN_A && t < T_DEPART) {
            val p = (t - T_SCAN_A) / (T_DEPART - T_SCAN_A)
            if (p < 0.75f) viser(canvas, gauche, ed, p / 0.75f) else valide(canvas, ed)
        }
        if (t >= T_SCAN_B && t < T_VOL) {
            val p = (t - T_SCAN_B) / (T_VOL - T_SCAN_B)
            if (p < 0.75f) viser(canvas, droite, eg, p / 0.75f) else valide(canvas, eg)
        }

        // La carte passe d'un écran à l'autre, en arc au-dessus des deux.
        if (t >= T_VOL && t < T_ARRIVEE) {
            val p = lisse((t - T_VOL) / (T_ARRIVEE - T_VOL))
            val cx = eg.centerX() + (ed.centerX() - eg.centerX()) * p
            val cy = eg.centerY() - sin(p * Math.PI).toFloat() * 14f * d
            val vol = RectF(cx - eg.width() / 2, cy - eg.height() / 2, cx + eg.width() / 2, cy + eg.height() / 2)
            carte(canvas, vol, 1f)
        }

        val bas = haut + th + 15f * d
        canvas.drawText(vous, gauche.centerX(), bas, legende)
        canvas.drawText(autre, droite.centerX(), bas, legende)
        temps(canvas, w / 2, bas + 16f * d)
    }

    private fun ecranDe(tel: RectF) =
        RectF(tel.left + 4f * d, tel.top + 9f * d, tel.right - 4f * d, tel.bottom - 9f * d)

    private fun telephone(c: Canvas, tel: RectF) {
        c.drawRoundRect(tel, 8f * d, 8f * d, corps)
        c.drawRoundRect(ecranDe(tel), 3f * d, 3f * d, ecran)
    }

    /** Une carte du carnet en miniature : cadre doré, paysage, plaque du mot. */
    private fun carte(c: Canvas, ecr: RectF, f: Float) {
        if (f <= 0f) return
        val s = 0.6f + 0.4f * f
        val cw = (ecr.width() - 8f * d) * s
        val ch = cw * 1.45f
        r.set(ecr.centerX() - cw / 2, ecr.centerY() - ch / 2, ecr.centerX() + cw / 2, ecr.centerY() + ch / 2)
        val a = (255 * f).toInt().coerceIn(0, 255)
        listOf(cadreCarte, ciel, mer, plaque).forEach { it.alpha = a }
        c.drawRoundRect(r, 3f * d, 3f * d, cadreCarte)
        val m = 2.5f * d
        val fen = RectF(r.left + m, r.top + m, r.right - m, r.top + r.height() * 0.58f)
        c.drawRect(fen, ciel)
        c.drawRect(fen.left, fen.bottom - fen.height() * 0.35f, fen.right, fen.bottom, mer)
        c.drawRect(r.left + m, r.top + r.height() * 0.66f, r.right - m, r.top + r.height() * 0.8f, plaque)
    }

    /** Un code dessiné : trois carrés pleins aux coins et un semis fixe de modules. */
    private fun code(c: Canvas, ecr: RectF, graine: Int, f: Float) {
        if (f <= 0f) return
        val cote = ecr.width() - 8f * d
        val n = 11
        val u = cote / n
        val x0 = ecr.centerX() - cote / 2
        val y0 = ecr.centerY() - cote / 2
        module.alpha = (255 * f).toInt().coerceIn(0, 255)
        var g = graine
        for (i in 0 until n) for (j in 0 until n) {
            val repere = (i < 3 && j < 3) || (i >= n - 3 && j < 3) || (i < 3 && j >= n - 3)
            g = g * 1103515245 + 12345
            val plein = repere || ((g ushr 16) and 3) == 0
            if (plein) c.drawRect(x0 + i * u, y0 + j * u, x0 + (i + 1) * u, y0 + (j + 1) * u, module)
        }
    }

    /** Le faisceau qui part du haut du téléphone qui vise, et la ligne qui balaie le code. */
    private fun viser(c: Canvas, tel: RectF, cible: RectF, p: Float) {
        val sx = tel.centerX()
        val sy = tel.top + 4f * d
        chemin.reset()
        chemin.moveTo(sx, sy)
        chemin.lineTo(cible.left + 2f * d, cible.top + 4f * d)
        chemin.lineTo(cible.right - 2f * d, cible.top + 4f * d)
        chemin.lineTo(cible.right - 2f * d, cible.bottom - 4f * d)
        chemin.lineTo(cible.left + 2f * d, cible.bottom - 4f * d)
        chemin.close()
        faisceau.alpha = 40
        c.drawPath(chemin, faisceau)
        val y = cible.top + 6f * d + (cible.height() - 12f * d) * (0.5f - 0.5f * cos(p * 2 * Math.PI).toFloat())
        c.drawLine(cible.left + 2f * d, y, cible.right - 2f * d, y, ligne)
    }

    /** Le code lu : une pastille verte cochée par-dessus. */
    private fun valide(c: Canvas, ecr: RectF) {
        val cx = ecr.centerX()
        val cy = ecr.centerY()
        val rr = 11f * d
        c.drawCircle(cx, cy, rr, vert)
        chemin.reset()
        chemin.moveTo(cx - 5f * d, cy)
        chemin.lineTo(cx - 1.5f * d, cy + 4f * d)
        chemin.lineTo(cx + 5.5f * d, cy - 4f * d)
        c.drawPath(chemin, coche)
    }

    /** Les quatre temps du texte, le temps en cours allumé. */
    private fun temps(c: Canvas, cx: Float, cy: Float) {
        val courant = when {
            t < T_SCAN_A -> 1
            t < T_DEPART -> 2
            t < T_SCAN_B -> 3
            else -> 4
        }
        val pas = 26f * d
        for (k in 1..4) {
            val x = cx + (k - 2.5f) * pas
            val allume = k == courant
            pastille.color = Color.parseColor(if (allume) "#0080FF" else "#E3E3E3")
            c.drawCircle(x, cy, 9f * d, pastille)
            chiffre.color = if (allume) Color.WHITE else Color.parseColor("#6B6B6B")
            c.drawText(k.toString(), x, cy - (chiffre.descent() + chiffre.ascent()) / 2, chiffre)
        }
    }

    private fun fondu(t: Float, debut: Float, duree: Float) = ((t - debut) / duree).coerceIn(0f, 1f)

    private fun lisse(p: Float) = p * p * (3 - 2 * p)

    private companion object {
        const val DUREE_MS = 10_000L
        const val HAUTEUR_DP = 124f
        const val GRAINE_A = 20261021
        const val GRAINE_B = 8576

        // Le déroulé, en fraction de la boucle.
        const val T_SCAN_A = 0.12f
        const val T_DEPART = 0.34f
        const val T_CODE_B = 0.42f
        const val T_SCAN_B = 0.50f
        const val T_VOL = 0.70f
        const val T_ARRIVEE = 0.82f

        /** L'image montrée sans animation : la carte arrivée chez l'autre. */
        const val FIGEE = 0.95f
    }
}
