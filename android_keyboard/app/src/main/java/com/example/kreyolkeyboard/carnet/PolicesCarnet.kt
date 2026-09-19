package com.example.kreyolkeyboard.carnet

import android.content.Context
import android.graphics.Typeface

/**
 * La police du nom sur la plaque : elle monte avec la rareté, comme la plaque
 * elle-même monte du bois à l'or.
 *
 * - **Commun** : la police du système, en gras. C'est la plaque de bois, et
 *   elle n'a rien à prouver.
 * - **Peu commun** : Lora, une sérif chaleureuse au dessin franc.
 * - **Rare** : Cormorant Garamond, une sérif de gravure aux empattements fins
 *   et au contraste marqué.
 * - **Très rare** : EB Garamond, la Garamond de la Renaissance. Elle est sur
 *   l'or et ne perd pas la casse : une police de capitales gravées aurait
 *   écrasé la majuscule du substantif, que le carnet enseigne et que la
 *   révision refuse quand elle manque.
 *
 * Les trois fichiers sont sous licence OFL, réduits au latin et fixés au gras
 * (`assets/fonts`, 12 à 20 Ko dans l'APK chacun). Ils sont chargés depuis les
 * assets et non depuis `res/font`, qui demande l'API 26 quand le minSdk est 21.
 * Lora est renommée « Lora Carnet » dans le fichier embarqué : son nom est
 * réservé et une version réduite n'a pas le droit de le porter.
 */
object PolicesCarnet {
    private val FICHIERS = arrayOf(
        null,
        "fonts/carnet_peu_commun.ttf",
        "fonts/carnet_rare.ttf",
        "fonts/carnet_tres_rare.ttf"
    )

    /**
     * Le corps corrigé de la police du palier.
     *
     * Les Garamond ont une hauteur d'x bien plus petite que la police du
     * système : au même corps, « Fräiheet » se lirait deux tailles au-dessous.
     * Les facteurs viennent de la comparaison des hauteurs d'x, pas d'un
     * réglage à l'œil.
     */
    private val ECHELLES = floatArrayOf(1f, 1.05f, 1.28f, 1.14f)

    private val cache = arrayOfNulls<Typeface>(FICHIERS.size)

    /**
     * Le caractère du palier. Un fichier absent ou illisible retombe sur le
     * gras du système plutôt que de faire échouer l'ouverture de la carte.
     */
    @Synchronized
    fun pour(context: Context, rarete: Rarete): Typeface {
        val i = rarete.ordinal
        cache[i]?.let { return it }
        val police = FICHIERS[i]?.let {
            try {
                Typeface.createFromAsset(context.applicationContext.assets, it)
            } catch (e: RuntimeException) {
                null
            }
        } ?: Typeface.DEFAULT_BOLD
        cache[i] = police
        return police
    }

    fun echelle(rarete: Rarete): Float = ECHELLES[rarete.ordinal]
}
