package com.example.kreyolkeyboard

import android.content.Context
import android.content.res.Configuration
import android.view.View
import android.widget.LinearLayout

/**
 * Les jeux à grille sur une tablette tenue en paysage : la grille à gauche, le
 * pavé, les boutons et les listes à droite.
 *
 * Empilés comme sur un téléphone, ils ne tenaient pas dans les 800 dp de
 * hauteur d'une tablette couchée : le pavé de Mokwaré et ses
 * boutons passaient sous le bord de l'écran, et les atteindre faisait sortir
 * la grille par le haut. Côte à côte, tout ce qui sert à chaque coup tient sur
 * un seul écran.
 *
 * Les téléphones ne sont jamais concernés, même couchés : leur hauteur en
 * paysage ne laisse pas la place d'une grille lisible à côté d'un pavé, et
 * c'est la disposition qu'ils ont toujours eue.
 *
 * Les écrans de jeu sont construits en code, d'une seule colonne : plutôt que
 * de doubler chaque constructeur, [repartir] reprend la colonne une fois
 * bâtie et range ses enfants. Chacun garde ses paramètres de mise en page.
 */
object DeuxColonnes {

    /** Part de la largeur donnée à la grille. */
    const val PART_GRILLE = 0.5f

    /**
     * Part de la hauteur de l'écran que la grille peut prendre en deux
     * colonnes : elle n'a plus le pavé sous elle, seulement la barre
     * d'onglets et l'en-tête du jeu au-dessus.
     */
    const val PART_HAUTEUR = 0.55f

    /** Vrai sur une tablette tenue en paysage, jamais sur un téléphone. */
    fun actives(context: Context): Boolean {
        val configuration = context.resources.configuration
        return configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
            configuration.smallestScreenWidthDp >= 600
    }

    /**
     * Range les enfants de [colonne] : [enHaut] restent en tête sur toute la
     * largeur, [aGauche] passent dans la colonne de la grille, tous les autres
     * dans celle de droite, chacun dans son ordre d'origine.
     */
    fun repartir(colonne: LinearLayout, enHaut: List<View>, aGauche: List<View>) {
        val context = colonne.context
        val enfants = (0 until colonne.childCount).map { colonne.getChildAt(it) }
        colonne.removeAllViews()

        val ecart = (12 * context.resources.displayMetrics.density).toInt()
        val gauche = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, PART_GRILLE
            ).apply { rightMargin = ecart }
        }
        val droite = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f - PART_GRILLE
            ).apply { leftMargin = ecart }
        }

        enfants.forEach { enfant ->
            when (enfant) {
                in enHaut -> colonne.addView(enfant)
                in aGauche -> gauche.addView(enfant)
                else -> droite.addView(enfant)
            }
        }

        colonne.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            addView(gauche)
            addView(droite)
        })
    }

    /**
     * Range par deux, côte à côte, les cartes de [colonne] à partir de
     * l'indice [aPartirDe] ; une carte restée seule garde toute la largeur.
     *
     * Pour les écrans faits d'une pile de cartes courtes, comme l'accueil :
     * empilées sur une tablette couchée, elles obligeaient à défiler pour voir
     * la quatrième. Les deux cartes d'une rangée prennent la hauteur de la
     * plus haute, pour que la rangée se lise comme une seule ligne.
     */
    fun parPaires(colonne: LinearLayout, aPartirDe: Int) {
        val context = colonne.context
        val cartes = (aPartirDe until colonne.childCount).map { colonne.getChildAt(it) }
        cartes.forEach { colonne.removeView(it) }
        val ecart = (6 * context.resources.displayMetrics.density).toInt()

        cartes.chunked(2).forEach { paire ->
            if (paire.size == 1) {
                colonne.addView(paire[0])
                return@forEach
            }
            val rangee = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                isBaselineAligned = false
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            paire.forEachIndexed { i, carte ->
                val ancien = carte.layoutParams as? LinearLayout.LayoutParams
                rangee.addView(carte, LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.MATCH_PARENT, 1f
                ).apply {
                    bottomMargin = ancien?.bottomMargin ?: 0
                    if (i == 0) rightMargin = ecart else leftMargin = ecart
                })
            }
            colonne.addView(rangee)
        }
    }
}
