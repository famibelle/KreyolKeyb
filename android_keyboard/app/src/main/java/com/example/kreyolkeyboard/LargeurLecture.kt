package com.example.kreyolkeyboard

import android.content.Context
import android.view.View

/**
 * Une colonne de lecture centrée sur les grands écrans.
 *
 * Sur une tablette couchée, les écrans de texte s'étalaient sur 1 280 dp : des
 * lignes de vingt-cinq centimètres, que l'œil perd en revenant à la ligne, et
 * des boutons de réponse aussi larges que l'écran. Au-delà de [MAX_DP], le
 * contenu est ramené à une colonne centrée par des marges latérales.
 *
 * Des marges sur la vue racine plutôt qu'une largeur imposée au contenu : le
 * contenu de certains écrans est refait en entier (l'onglet de progression à
 * chaque retour), et la marge survit à ce remplacement sans que l'écran ait à
 * y penser.
 *
 * Les téléphones ne sont jamais concernés, même couchés.
 */
object LargeurLecture {

    /** Largeur maximale de la colonne, de l'ordre de 70 signes à 16 sp. */
    const val MAX_DP = 720

    private fun active(context: Context): Boolean {
        val configuration = context.resources.configuration
        return configuration.smallestScreenWidthDp >= 600 &&
            configuration.screenWidthDp > MAX_DP
    }

    /**
     * La largeur que l'écran laisse au contenu des onglets, en pixels : celle
     * de l'écran, moins le rail d'onglets d'une tablette couchée.
     *
     * Tous les écrans qui calculent eux-mêmes une taille (grilles des jeux,
     * colonne de lecture, carnet) partent de celle-ci et non de
     * `displayMetrics.widthPixels` : avec le rail, ils débordaient de sa
     * largeur.
     */
    fun largeurEcran(context: Context): Int {
        val metriques = context.resources.displayMetrics
        val rail = if (DeuxColonnes.actives(context)) {
            (SettingsActivity.RAIL_LARGEUR_DP * metriques.density).toInt() + 2
        } else 0
        return metriques.widthPixels - rail
    }

    /** Marge à ajouter de chaque côté, en pixels ; zéro sur un téléphone. */
    fun marge(context: Context): Int {
        if (!active(context)) return 0
        val metriques = context.resources.displayMetrics
        return ((largeurEcran(context) - MAX_DP * metriques.density) / 2).toInt()
            .coerceAtLeast(0)
    }

    /**
     * La largeur dont dispose le contenu, à utiliser à la place de
     * `displayMetrics.widthPixels` par un écran qui calcule lui-même ses
     * retours à la ligne.
     */
    fun largeur(context: Context): Int =
        largeurEcran(context) - 2 * marge(context)

    /** Ajoute la marge de part et d'autre de [vue]. */
    fun borner(vue: View) {
        val marge = marge(vue.context)
        if (marge == 0) return
        vue.setPadding(
            vue.paddingLeft + marge, vue.paddingTop,
            vue.paddingRight + marge, vue.paddingBottom
        )
    }
}
