package com.example.kreyolkeyboard.carnet

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * L'écran d'un échange : un code QR, ce qu'il faut en faire, deux boutons.
 *
 * Le même pour les deux côtés, parce que c'est le même geste : le donneur
 * montre son offre puis scanne la réception, le receveur montre la réception.
 * Le code est dessiné en grand, sur fond blanc, sans rien qui le recouvre :
 * c'est un autre téléphone qui doit le lire, parfois de biais.
 */
internal class VueCession(private val hote: FrameLayout) {

    private val ctx = hote.context
    private val d = ctx.resources.displayMetrics.density
    private var voile: FrameLayout? = null

    val ouverte: Boolean get() = voile != null

    fun montrer(
        titre: String,
        etapes: String,
        code: String,
        principal: String,
        surPrincipal: () -> Unit,
        secondaire: String,
        surSecondaire: () -> Unit
    ) {
        fermer()
        fun dp(v: Float) = (v * d).toInt()
        val v = FrameLayout(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(0xE6000000.toInt())
            isClickable = true // le voile ne laisse rien passer au carnet dessous
        }
        val colonne = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(22f), dp(22f), dp(22f), dp(18f))
            background = GradientDrawable().apply {
                cornerRadius = 20f * d
                setColor(Color.WHITE)
            }
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER
            ).apply { setMargins(dp(20f), dp(20f), dp(20f), dp(20f)) }
        }
        colonne.addView(TextView(ctx).apply {
            text = titre
            textSize = 19f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Carnet.COULEUR)
            gravity = Gravity.CENTER
        })

        val cote = minOf(ctx.resources.displayMetrics.widthPixels, ctx.resources.displayMetrics.heightPixels) -
            dp(100f)
        colonne.addView(ImageView(ctx).apply {
            setImageBitmap(Cession.codeQR(code, cote))
            contentDescription = titre
            layoutParams = LinearLayout.LayoutParams(cote, cote).apply {
                topMargin = dp(14f); bottomMargin = dp(10f)
            }
        })

        colonne.addView(TextView(ctx).apply {
            text = etapes
            textSize = 14.5f
            setLineSpacing(0f, 1.2f)
            setTextColor(Color.parseColor("#424242"))
        })

        colonne.addView(bouton(principal, plein = true, action = surPrincipal).apply {
            (layoutParams as LinearLayout.LayoutParams).topMargin = dp(18f)
        })
        colonne.addView(bouton(secondaire, plein = false, action = surSecondaire))

        v.addView(colonne)
        voile = v
        hote.addView(v)
        FigerOrientation.tantQue(v)
        v.alpha = 0f
        v.animate().alpha(1f).setDuration(160).start()
    }

    private fun bouton(libelle: String, plein: Boolean, action: () -> Unit) = TextView(ctx).apply {
        text = libelle
        textSize = 15f
        setTypeface(null, Typeface.BOLD)
        gravity = Gravity.CENTER
        setTextColor(if (plein) Color.WHITE else Carnet.COULEUR)
        val v = (12 * d).toInt()
        setPadding(v, v, v, v)
        if (plein) background = GradientDrawable().apply {
            cornerRadius = 24f * d
            setColor(Carnet.COULEUR)
        }
        isClickable = true
        isFocusable = true
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = (6 * d).toInt() }
    }

    fun fermer() {
        val v = voile ?: return
        voile = null
        v.animate().alpha(0f).setDuration(140).withEndAction { hote.removeView(v) }.start()
    }

    /** Pour le bouton Retour : rend vrai si un échange était à l'écran. */
    fun fermerSiOuverte(): Boolean {
        if (!ouverte) return false
        fermer()
        return true
    }
}
