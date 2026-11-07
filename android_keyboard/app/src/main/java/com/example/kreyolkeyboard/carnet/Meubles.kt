package com.example.kreyolkeyboard.carnet

import android.graphics.Path
import android.graphics.RectF

/**
 * Les meubles : les silhouettes que porte une carte enluminée.
 *
 * ## Ce que cette bibliothèque n'essaie pas de faire
 *
 * Elle n'essaie pas de **couvrir le carnet**, et c'est le renversement qui la
 * rend possible. La mesure de l'exploration est sans appel : sur les 1 690
 * substantifs glosés, 1 504 têtes de glose distinctes, soit **1,12 mot par
 * dessin**, et 89 % des têtes ne concernent qu'un seul mot. Il n'y a pas de
 * Pareto — la courbe de couverture colle à la diagonale. Une bibliothèque de
 * cent vingt silhouettes ne couvrirait pas 16 % des substantifs.
 *
 * Alors on ne demande plus au meuble de désigner chaque mot. Dans une vraie
 * collection, toutes les cartes ne sont pas illustrées : l'illustration est ce
 * qui **distingue** quelques-unes. On ne dessine donc plus ce qu'il faudrait
 * couvrir, on dessine ce qu'on sait bien dessiner, et la carte enluminée
 * devient une propriété collectionnable en soi — indépendante de la rareté,
 * qui, elle, est fixée par la fréquence et n'est pas négociable.
 *
 * ## L'attribution est ailleurs, et elle est manuelle
 *
 * Le tableau mot → meuble n'est pas ici : il est dans
 * `Dictionnaires/generate_blasons.py`, écrit à la main, une ligne à la fois.
 * La raison est une contrainte de justesse. La carte affiche **toute** la
 * glose, et un dessin n'en illustre qu'un sens : sur la face réponse d'un
 * outil de révision, une image qui tranche une polysémie est activement
 * fausse. `Feier` est « feu, incendie, **fête** », `Bierg` « montagne, côte,
 * **garant** », `Nol` « clou, **ongle**, **aiguille** » — aucun des trois n'est
 * enluminé, et le générateur refuse de sortir si un meuble attribué n'existe
 * pas ici.
 *
 * ## D'où viennent les dessins
 *
 * Du **domaine public**, depuis le 2026-10-31 (propriétaire). Les silhouettes
 * dessinées ici à la main, d'après des emoji puis point par point, ne
 * tenaient pas la comparaison avec celles des banques d'images : un lion
 * lisait comme un soleil, un veau comme une chèvre. [MeublesDomaine] porte
 * maintenant presque toute la bibliothèque, tirée par
 * `scripts/meubles_domaine.py` de silhouettes publiées sous CC0 ou marquées
 * du domaine public : PhyloPic pour le vivant, Openclipart et les
 * pictogrammes CC0 (Pinhead, Temaki) pour le reste. La source, l'auteur et
 * la licence de chacune sont dans `scripts/meubles_sources.json`, la licence
 * vérifiée sur la page d'origine. Six meubles restent dessinés ici, faute
 * d'une silhouette libre qui vaille la leur : le coffre-fort, le miroir, la
 * rue, le beurre, la framboise et l'alphabet.
 *
 * ## Le dessin
 *
 * Chaque silhouette tient dans un **carré de cent unités centré sur
 * l'origine**, et [chemin] la met à l'échelle de la fenêtre. Ce qui se pose
 * (une bête, un bâtiment, un véhicule) touche le sol à y = 47 ; ce qui vole,
 * nage ou flotte (un oiseau, un poisson, un symbole) est centré.
 *
 * La silhouette est **pleine** : aucun trait intérieur, tout le détail est
 * dans le contour, comme dans les banques d'images qui ont servi de modèle.
 * Un trou ne reste que s'il dessine quelque chose (les vitres du bus, le
 * « ! » du panneau, la porte d'une maison) ; le générateur comble les autres,
 * l'œil d'une bête ou une pastille de couleur. Le remplissage est en
 * `EVEN_ODD`, ce qui suffit à creuser ces trous.
 *
 * **Un profil regarde à droite.** Tout ce qui a une tête ou un nez, bête ou
 * véhicule, regarde ou roule vers la droite (propriétaire, 2026-10-30) : le
 * générateur retourne les silhouettes qui regardaient à gauche. Un meuble de
 * face n'est pas concerné.
 *
 * Les chemins sont construits une fois et gardés : la carte se trace en unités
 * de carte, et rien de ceci ne dépend de la taille à l'écran.
 */
internal object Meubles {

    /** Le côté du carré de tracé. Tout meuble tient dedans, marges comprises. */
    const val COTE = 100f

    private val cache = HashMap<String, Path>()

    /**
     * Le chemin d'un meuble, dans son carré de cent unités, ou `null` s'il
     * n'existe pas.
     *
     * Un nom inconnu n'est pas une erreur à faire remonter : la carte retombe
     * simplement sur son tracé, et c'est un état parfaitement présentable —
     * c'est même celui de 97 % du carnet.
     */
    fun chemin(nom: String): Path? {
        cache[nom]?.let { return it }
        val svg = MeublesDomaine.TRACES[nom]
        val trace = TABLE[nom]
        if (svg == null && trace == null) return null
        return Path().apply {
            fillType = Path.FillType.EVEN_ODD
            if (svg != null) {
                for (anneau in anneaux(svg)) {
                    moveTo(anneau[0], anneau[1])
                    var i = 2
                    while (i < anneau.size) { lineTo(anneau[i], anneau[i + 1]); i += 2 }
                    close()
                }
            } else {
                trace!!(this)
            }
            cache[nom] = this
        }
    }

    /** Les noms dessinés, pour le test d'actif. */
    fun noms(): Set<String> = MeublesDomaine.TRACES.keys + TABLE.keys

    /**
     * Les anneaux d'un chemin de [MeublesDomaine] : `M`, puis des paires de
     * coordonnées absolues jusqu'au `Z`. C'est tout ce que le générateur
     * écrit, et c'est tout ce qu'on lit ; sans `Path`, pour que le test JVM
     * puisse relire chaque chemin livré.
     */
    fun anneaux(svg: String): List<FloatArray> {
        val out = ArrayList<FloatArray>()
        var cour = ArrayList<Float>()
        var i = 0
        while (i < svg.length) {
            val c = svg[i]
            when {
                c == 'M' -> { cour = ArrayList(); i++ }
                c == 'Z' -> { out.add(cour.toFloatArray()); i++ }
                c == ' ' -> i++
                else -> {
                    var j = i + 1
                    while (j < svg.length && svg[j] != ' ' && svg[j] != 'Z' && svg[j] != 'M') j++
                    cour.add(svg.substring(i, j).toFloat())
                    i = j
                }
            }
        }
        return out
    }

    // -- petit outillage de tracé, pour les six meubles dessinés ici --------

    private fun Path.pol(vararg v: Float) {
        moveTo(v[0], v[1])
        var i = 2
        while (i < v.size) { lineTo(v[i], v[i + 1]); i += 2 }
        close()
    }

    private fun Path.disque(cx: Float, cy: Float, r: Float) =
        addCircle(cx, cy, r, Path.Direction.CW)

    private fun Path.arrondi(l: Float, t: Float, r: Float, b: Float, rayon: Float) =
        addRoundRect(RectF(l, t, r, b), rayon, rayon, Path.Direction.CW)

    private fun Path.ovale(l: Float, t: Float, r: Float, b: Float) =
        addOval(RectF(l, t, r, b), Path.Direction.CW)

    // -- les meubles dessinés à la main -------------------------------------
    // Ceux pour lesquels aucune silhouette du domaine public ne valait la
    // leur. Les trois derniers sortent de scripts/silhouettes_boite.py.

    private val TABLE: Map<String, (Path) -> Unit> = mapOf(
        "coffre" to { p ->
            p.pol(-42f, -38f, 42f, -38f, 42f, 38f, -42f, 38f)
            p.disque(-6f, 0f, 18f)                                // le cadran
            p.disque(-6f, 0f, 6f)
            p.pol(26f, -8f, 32f, -8f, 32f, 8f, 26f, 8f)           // la poignée
        },
        "miroir" to { p ->
            p.ovale(-30f, -46f, 30f, 22f)
            p.ovale(-22f, -38f, 22f, 14f)
            p.pol(-6f, 22f, 6f, 22f, 6f, 38f, -6f, 38f)           // le pied
            p.pol(-22f, 38f, 22f, 38f, 22f, 46f, -22f, 46f)
        },
        "rue" to { p ->
            p.pol(-44f, 44f, -14f, -40f, 14f, -40f, 44f, 44f)
            p.pol(-2f, -36f, 2f, -36f, 2f, -24f, -2f, -24f)            // la bande axiale
            p.pol(-2.5f, -14f, 2.5f, -14f, 2.5f, 0f, -2.5f, 0f)
            p.pol(-3f, 12f, 3f, 12f, 3f, 30f, -3f, 30f)
        },
        "beurre" to { p ->                                   // Botter
            p.pol(-36f, -7f, -12f, -22f, 38f, -22f, 14f, -7f)          // le dessus
            p.pol(-36f, -4f, 13f, -4f, 13f, 22f, -36f, 22f)            // la face
            p.pol(16f, -5f, 38f, -19f, 38f, 7f, 16f, 21f)              // le côté
            p.ovale(-46f, 25f, 46f, 37f)                               // l'assiette
        },
        "framboise" to { p ->                                   // Hammbier
            p.disque(-21.3f, -16f, 6.6f)
            p.disque(-7.1f, -16f, 6.6f)
            p.disque(7.1f, -16f, 6.6f)
            p.disque(21.3f, -16f, 6.6f)
            p.disque(-28.4f, -3.6f, 6.6f)
            p.disque(-14.2f, -3.6f, 6.6f)
            p.disque(0f, -3.6f, 6.6f)
            p.disque(14.2f, -3.6f, 6.6f)
            p.disque(28.4f, -3.6f, 6.6f)
            p.disque(-21.3f, 8.8f, 6.6f)
            p.disque(-7.1f, 8.8f, 6.6f)
            p.disque(7.1f, 8.8f, 6.6f)
            p.disque(21.3f, 8.8f, 6.6f)
            p.disque(-14.2f, 21.2f, 6.6f)
            p.disque(0f, 21.2f, 6.6f)
            p.disque(14.2f, 21.2f, 6.6f)
            p.disque(-7.1f, 33.6f, 6.6f)
            p.disque(7.1f, 33.6f, 6.6f)                                // les grains
            p.pol(0f, -40.2f, 2.9f, -34.2f, 14.3f, -34.5f, 4.8f, -31.2f, 8.8f, -25.3f, 0f, -29.2f, -8.8f, -25.3f, -4.8f, -31.2f, -14.3f, -34.5f, -2.9f, -34.2f) // le calice
            p.pol(-1.5f, -38f, 1.5f, -38f, 3f, -48f, 0f, -48f)         // la queue
        },
        "alphabet" to { p ->                                   // Alphabet
            p.arrondi(-20f, -44f, 20f, -4f, 4f)                        // les trois cubes
            p.arrondi(-44f, 2f, -4f, 42f, 4f)
            p.arrondi(4f, 2f, 44f, 42f, 4f)
            p.pol(-13f, -11f, -3.5f, -37f, 3.5f, -37f, 13f, -11f, 6.6f, -11f, 4.7f, -16.5f, -4.7f, -16.5f, -6.6f, -11f)
            p.pol(-2.9f, -22f, 0f, -30.5f, 2.9f, -22f)                 // A, B, C, en réserve
            p.moveTo(-35f, 9f)
            p.lineTo(-22f, 9f)
            p.cubicTo(-15f, 9f, -12.5f, 12.5f, -12.5f, 15.5f)
            p.cubicTo(-12.5f, 18.5f, -14f, 20.5f, -16f, 21.5f)
            p.cubicTo(-13f, 22.5f, -11f, 25f, -11f, 28.5f)
            p.cubicTo(-11f, 32.5f, -14.5f, 35f, -21f, 35f)
            p.lineTo(-35f, 35f)
            p.close()
            p.moveTo(-28.6f, 14.2f)
            p.lineTo(-22.5f, 14.2f)
            p.cubicTo(-19.6f, 14.2f, -18.4f, 15.4f, -18.4f, 17f)
            p.cubicTo(-18.4f, 18.7f, -19.6f, 19.6f, -22.5f, 19.6f)
            p.lineTo(-28.6f, 19.6f)
            p.close()
            p.moveTo(-28.6f, 24.6f)
            p.lineTo(-21.5f, 24.6f)
            p.cubicTo(-18.6f, 24.6f, -17.2f, 26f, -17.2f, 27.4f)
            p.cubicTo(-17.2f, 28.8f, -18.6f, 29.9f, -21.5f, 29.9f)
            p.lineTo(-28.6f, 29.9f)
            p.close()
            p.pol(34.3f, 30.7f, 32.7f, 32.3f, 30.8f, 33.7f, 28.6f, 34.7f, 26.3f, 35.3f, 24f, 35.5f, 21.7f, 35.3f, 19.4f, 34.7f, 17.3f, 33.7f, 15.3f, 32.3f, 13.7f, 30.7f, 12.3f, 28.8f, 11.3f, 26.6f, 10.7f, 24.3f, 10.5f, 22f, 10.7f, 19.7f, 11.3f, 17.4f, 12.3f, 15.2f, 13.7f, 13.3f, 15.3f, 11.7f, 17.2f, 10.3f, 19.4f, 9.3f, 21.7f, 8.7f, 24f, 8.5f, 26.3f, 8.7f, 28.6f, 9.3f, 30.8f, 10.3f, 32.7f, 11.7f, 34.3f, 13.3f, 29.7f, 17.2f, 28.8f, 16.3f, 27.7f, 15.6f, 26.5f, 15f, 25.3f, 14.7f, 24f, 14.6f, 22.7f, 14.7f, 21.5f, 15f, 20.3f, 15.6f, 19.2f, 16.3f, 18.3f, 17.2f, 17.6f, 18.3f, 17f, 19.5f, 16.7f, 20.7f, 16.6f, 22f, 16.7f, 23.3f, 17f, 24.5f, 17.6f, 25.7f, 18.3f, 26.8f, 19.2f, 27.7f, 20.3f, 28.4f, 21.5f, 29f, 22.7f, 29.3f, 24f, 29.4f, 25.3f, 29.3f, 26.5f, 29f, 27.7f, 28.4f, 28.8f, 27.7f, 29.7f, 26.8f)
        },
    )
}
