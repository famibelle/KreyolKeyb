package com.example.kreyolkeyboard.carnet

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.View

/**
 * L'écran ne tourne plus tant qu'une carte est montrée.
 *
 * La pochette, la carte ouverte, l'éventail d'un casier, la révision et la
 * cession sont des vues posées par-dessus l'écran. Une rotation recrée
 * l'activité et elles disparaissaient avec elle : la pochette et ses cartes,
 * la carte qu'on lisait, la carte de révision qu'on venait de retourner
 * (2026-10-12). Et c'est en inclinant le téléphone pour faire jouer une carte
 * qu'on le fait tourner : on la perdait au moment même où on la regardait.
 */
internal object FigerOrientation {

    /**
     * Fige l'orientation de l'activité qui porte [vue], et la rend quand [vue]
     * quitte la fenêtre. Rendue au détachement et pas à une fermeture : les
     * écrans retirent aussi ces vues eux-mêmes (nouvelle partie, écran quitté),
     * et l'appli resterait figée.
     */
    fun tantQue(vue: View) {
        val activite = activiteDe(vue.context) ?: return
        val avant = activite.requestedOrientation
        activite.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
        vue.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) {
                v.removeOnAttachStateChangeListener(this)
                // Deux vues figées l'une sur l'autre (la carte ouverte depuis
                // l'éventail) : la dernière refermée rend la main, pas la
                // première, qui rendrait un verrou que l'autre tient encore.
                if (--tenues == 0) activite.requestedOrientation = rendue
            }
        })
        if (tenues++ == 0) rendue = avant
    }

    /**
     * Sur téléphone, l'appli ne se tient qu'en portrait (décision du
     * propriétaire, 2026-10-13). À l'italienne, l'en-tête, les onglets et le
     * bandeau d'un jeu prenaient presque toute la hauteur : la révision n'y
     * montrait plus qu'une icône à la place de la carte. Une tablette garde
     * les deux sens, elle a la place.
     *
     * À appeler avant `super.onCreate` : l'activité ouverte à l'italienne n'est
     * alors recréée qu'une fois, avant d'avoir rien montré.
     */
    fun portraitSurTelephone(activite: Activity) {
        if (activite.resources.configuration.smallestScreenWidthDp < 600) {
            activite.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
        }
    }

    private var tenues = 0
    private var rendue = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

    private fun activiteDe(ctx: Context): Activity? {
        var c: Context? = ctx
        while (c is ContextWrapper) {
            if (c is Activity) return c
            c = c.baseContext
        }
        return null
    }
}
