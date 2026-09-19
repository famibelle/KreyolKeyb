package com.example.kreyolkeyboard.carnet

import android.content.Context
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ScrollView
import kotlin.math.abs
import kotlin.math.sign

/**
 * Le voile d'une carte ouverte depuis l'éventail : on la chasse dans n'importe
 * quel sens pour revenir aux cartes, comme on repose une carte sur la pile.
 *
 * Le voile retient tous les gestes dès l'appui : sans cela, le glissé latéral
 * remontait jusqu'au pager des onglets, qui changeait d'onglet par-dessus la
 * carte au lieu de rendre l'éventail.
 *
 * Le geste vertical n'est pris que quand la carte ne peut plus défiler dans ce
 * sens : une glose plus haute que l'écran se lit d'abord jusqu'au bout, et c'est
 * le glissé suivant qui la renvoie. Le geste latéral est pris dès qu'il se
 * déclare, la carte ne défilant jamais de côté. L'appui simple reste un clic,
 * qui referme aussi.
 *
 * [surEnvol] reçoit le sens du jet sur chaque axe, l'un des deux restant nul :
 * (-1, 0) vers la gauche, (1, 0) vers la droite, (0, -1) vers le haut, (0, 1)
 * vers le bas.
 */
internal class VoileGlissable(
    context: Context,
    private val surEnvol: (Int, Int) -> Unit
) : FrameLayout(context) {

    private enum class Axe { AUCUN, VERTICAL, HORIZONTAL }

    private val seuil = ViewConfiguration.get(context).scaledTouchSlop
    private val vitesseJet = 900f * resources.displayMetrics.density
    private var x0 = 0f
    private var y0 = 0f
    private var axe = Axe.AUCUN
    private var suivi: VelocityTracker? = null

    private val contenu: View? get() = if (childCount > 0) getChildAt(0) else null

    override fun dispatchTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_DOWN) {
            x0 = e.rawX
            y0 = e.rawY
            axe = Axe.AUCUN
            suivi?.recycle()
            suivi = VelocityTracker.obtain()
        }
        // Coordonnées brutes : la carte suit le doigt, des coordonnées locales
        // mesureraient son propre déplacement.
        val brut = MotionEvent.obtain(e).apply { setLocation(e.rawX, e.rawY) }
        suivi?.addMovement(brut)
        brut.recycle()
        val pris = super.dispatchTouchEvent(e)
        // Après super : un ViewGroup remet ce drapeau à zéro à chaque appui.
        if (e.actionMasked == MotionEvent.ACTION_DOWN) {
            parent?.requestDisallowInterceptTouchEvent(true)
        }
        return pris
    }

    private fun axeDeclare(e: MotionEvent): Axe {
        val dx = e.rawX - x0
        val dy = e.rawY - y0
        if (abs(dy) > seuil && abs(dy) > 1.5f * abs(dx)) {
            val defile = contenu as? ScrollView
            val bloque = defile?.canScrollVertically(if (dy < 0) 1 else -1) != true
            return if (bloque) Axe.VERTICAL else Axe.AUCUN
        }
        if (abs(dx) > seuil && abs(dx) > 1.5f * abs(dy)) return Axe.HORIZONTAL
        return Axe.AUCUN
    }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_MOVE && axe == Axe.AUCUN) {
            axe = axeDeclare(e)
        }
        return axe != Axe.AUCUN
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (axe == Axe.AUCUN && e.actionMasked == MotionEvent.ACTION_MOVE) {
            axe = axeDeclare(e)
            if (axe != Axe.AUCUN) {
                // L'appui commencé sur le voile ne doit plus devenir un clic.
                super.onTouchEvent(MotionEvent.obtain(e).apply { action = MotionEvent.ACTION_CANCEL })
            }
        }
        if (axe == Axe.AUCUN) return super.onTouchEvent(e)

        val horizontal = axe == Axe.HORIZONTAL
        val d = if (horizontal) e.rawX - x0 else e.rawY - y0
        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE -> suivre(d, horizontal)
            MotionEvent.ACTION_UP -> {
                suivi?.computeCurrentVelocity(1000)
                val v = (if (horizontal) suivi?.xVelocity else suivi?.yVelocity) ?: 0f
                val etendue = if (horizontal) width else height
                val loin = abs(d) > etendue * 0.18f
                val jet = abs(v) > vitesseJet && sign(v) == sign(d)
                if ((loin || jet) && d != 0f) {
                    val s = sign(d).toInt()
                    if (horizontal) surEnvol(s, 0) else surEnvol(0, s)
                } else {
                    rappeler()
                }
                finir()
            }
            MotionEvent.ACTION_CANCEL -> {
                rappeler()
                finir()
            }
        }
        return true
    }

    private fun suivre(d: Float, horizontal: Boolean) {
        val etendue = (if (horizontal) width else height).coerceAtLeast(1)
        if (horizontal) contenu?.translationX = d else contenu?.translationY = d
        val part = (abs(d) / etendue).coerceIn(0f, 1f)
        background?.alpha = (255 * (1f - part)).toInt()
    }

    private fun rappeler() {
        contenu?.animate()?.translationX(0f)?.translationY(0f)?.setDuration(200)
            ?.setInterpolator(DecelerateInterpolator())?.start()
        background?.alpha = 255
    }

    private fun finir() {
        axe = Axe.AUCUN
        suivi?.recycle()
        suivi = null
    }
}
