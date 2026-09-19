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
 * Cent onze silhouettes pour cent vingt mots d'un carnet de deux mille huit
 * cents : à peu près quatre cartes sur cent. C'est la proportion visée, et
 * aucun calcul de couverture ne l'aurait trouvée.
 *
 * La seconde vague a été choisie autrement que la première. Plutôt que de
 * partir des mots, on est parti des emoji : là où le glyphe existe, le concept
 * a déjà un pictogramme conventionnel, ce qui est exactement ce qui rend une
 * silhouette lisible. Mais l'emoji ne pouvait pas être *livré* — sa couleur
 * concurrence la teinte, qui porte le champ, et rendu en pochoir il perd la
 * moitié des siens : un journal, un panneau P et un œuf deviennent un
 * rectangle, un carré et un ovale. Il a donc servi de crible et de croquis,
 * pas de dessin.
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
 * ## Le dessin
 *
 * Chaque silhouette est tracée dans un **carré de cent unités centré sur
 * l'origine**, et [chemin] la met à l'échelle de la fenêtre. Le remplissage
 * est en `EVEN_ODD` : une forme posée à l'intérieur d'une autre y creuse un
 * trou, ce qui donne la porte d'une maison ou l'œil d'un poisson sans avoir à
 * tracer le contour à l'envers. Deux chemins qui doivent rester pleins côte à
 * côte se **touchent** donc sans se chevaucher — le tronc de l'arbre est abouté
 * à sa couronne, jamais glissé dessous.
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
    fun chemin(nom: String): Path? = cache[nom] ?: TABLE[nom]?.let { trace ->
        Path().apply {
            fillType = Path.FillType.EVEN_ODD
            trace(this)
            cache[nom] = this
        }
    }

    /** Les noms dessinés, pour le test d'actif. */
    fun noms(): Set<String> = TABLE.keys

    // -- petit outillage de tracé ------------------------------------------
    // Il n'y a pas de bibliothèque de formes dans `android.graphics` : sans
    // ces quatre lignes, chaque rectangle arrondi coûterait un RectF nommé.

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

    // -- la bibliothèque ---------------------------------------------------

    private val TABLE: Map<String, (Path) -> Unit> = mapOf(

        // ---- la maison et ce qu'elle contient ----------------------------

        "maison" to { p ->
            p.pol(-48f, -4f, 0f, -44f, 48f, -4f, 36f, -4f, 36f, 44f, -36f, 44f, -36f, -4f)
            p.pol(-11f, 44f, -11f, 12f, 11f, 12f, 11f, 44f)   // la porte, en réserve
        },
        "porte" to { p ->
            p.pol(-30f, 46f, -30f, -30f, -22f, -42f, 22f, -42f, 30f, -30f, 30f, 46f)
            p.disque(16f, 6f, 5f)                              // la poignée
        },
        "fenetre" to { p ->
            p.pol(-36f, -40f, 36f, -40f, 36f, 40f, -36f, 40f)
            p.pol(-28f, -32f, -4f, -32f, -4f, -4f, -28f, -4f)
            p.pol(4f, -32f, 28f, -32f, 28f, -4f, 4f, -4f)
            p.pol(-28f, 4f, -4f, 4f, -4f, 32f, -28f, 32f)
            p.pol(4f, 4f, 28f, 4f, 28f, 32f, 4f, 32f)
        },
        "table" to { p ->
            p.pol(-46f, -18f, 46f, -18f, 46f, -6f, -46f, -6f)
            p.pol(-36f, -6f, -26f, -6f, -26f, 42f, -36f, 42f)
            p.pol(26f, -6f, 36f, -6f, 36f, 42f, 26f, 42f)
        },
        "banc" to { p ->
            p.pol(-46f, -34f, 46f, -34f, 46f, -24f, -46f, -24f)   // le dossier
            p.pol(-38f, -24f, -30f, -24f, -30f, 0f, -38f, 0f)     // ses montants
            p.pol(30f, -24f, 38f, -24f, 38f, 0f, 30f, 0f)
            p.pol(-46f, 0f, 46f, 0f, 46f, 10f, -46f, 10f)         // l'assise
            p.pol(-38f, 10f, -30f, 10f, -30f, 42f, -38f, 42f)     // les pieds
            p.pol(30f, 10f, 38f, 10f, 38f, 42f, 30f, 42f)
        },
        "lit" to { p ->
            p.pol(-48f, -6f, -48f, -34f, -36f, -34f, -36f, -6f)   // la tête de lit
            p.pol(-48f, -6f, 48f, -6f, 48f, 18f, -48f, 18f)
            p.pol(-32f, -4f, -8f, -4f, -8f, 8f, -32f, 8f)         // l'oreiller
            p.pol(-46f, 18f, -38f, 18f, -38f, 38f, -46f, 38f)
            p.pol(38f, 18f, 46f, 18f, 46f, 38f, 38f, 38f)
        },
        "tiroir" to { p ->
            p.pol(-44f, -36f, 44f, -36f, 44f, 36f, -44f, 36f)
            p.pol(-34f, -26f, 34f, -26f, 34f, -2f, -34f, -2f)
            p.pol(-34f, 6f, 34f, 6f, 34f, 30f, -34f, 30f)
        },
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
        "robinet" to { p ->
            p.pol(-24f, -44f, 22f, -44f, 22f, -34f, -24f, -34f)        // le volant
            p.pol(-4f, -34f, 4f, -34f, 4f, -26f, -4f, -26f)            // sa tige
            p.pol(-18f, -26f, 8f, -26f, 8f, 0f, 30f, 0f,
                  30f, 26f, 16f, 26f, 16f, 14f, -18f, 14f)             // le corps et le bec
            p.pol(19f, 32f, 27f, 32f, 27f, 46f, 19f, 46f)              // le filet d'eau
        },
        "quille" to { p ->
            p.moveTo(0f, -46f)
            p.cubicTo(10f, -46f, 14f, -30f, 8f, -18f)
            p.cubicTo(2f, -8f, 20f, 6f, 20f, 24f)
            p.cubicTo(20f, 38f, 10f, 46f, 0f, 46f)
            p.cubicTo(-10f, 46f, -20f, 38f, -20f, 24f)
            p.cubicTo(-20f, 6f, -2f, -8f, -8f, -18f)
            p.cubicTo(-14f, -30f, -10f, -46f, 0f, -46f)
            p.close()
        },

        // ---- ce qu'on bâtit ----------------------------------------------

        "village" to { p ->
            p.pol(-46f, 12f, -28f, -6f, -10f, 12f, -16f, 12f, -16f, 42f, -40f, 42f, -40f, 12f)
            p.pol(-2f, 20f, 14f, 4f, 30f, 20f, 24f, 20f, 24f, 42f, 4f, 42f, 4f, 20f)
            p.pol(34f, 4f, 40f, -22f, 46f, 4f, 46f, 42f, 34f, 42f)   // le clocher
        },
        "ville" to { p ->
            p.pol(-46f, 46f, -46f, -6f, -26f, -6f, -26f, 46f)
            p.pol(-20f, 46f, -20f, -34f, -2f, -34f, -2f, 46f)
            p.pol(4f, 46f, 4f, -16f, 22f, -16f, 22f, 46f)
            p.pol(28f, 46f, 28f, -44f, 44f, -44f, 44f, 46f)
            p.pol(-40f, 4f, -32f, 4f, -32f, 12f, -40f, 12f)          // quelques fenêtres
            p.pol(-14f, -24f, -6f, -24f, -6f, -16f, -14f, -16f)
            p.pol(32f, -34f, 40f, -34f, 40f, -26f, 32f, -26f)
        },
        "eglise" to { p ->
            p.pol(-4f, -50f, 4f, -50f, 4f, -42f, 12f, -42f, 12f, -34f, 4f, -34f,
                  4f, -26f, -4f, -26f, -4f, -34f, -12f, -34f, -12f, -42f, -4f, -42f)
            p.pol(-16f, -22f, 0f, -26f, 16f, -22f, 16f, 46f, -16f, 46f)  // le clocher
            p.pol(16f, 6f, 44f, 6f, 44f, 46f, 16f, 46f)                  // la nef
            p.pol(-44f, 6f, -16f, 6f, -16f, 46f, -44f, 46f)
            p.pol(-8f, 20f, 8f, 20f, 8f, 46f, -8f, 46f)                  // le portail
        },
        "ecole" to { p ->
            p.pol(-46f, -8f, 0f, -34f, 46f, -8f, 46f, 44f, -46f, 44f)
            p.pol(-2f, -50f, 2f, -50f, 2f, -36f, -2f, -36f)               // le mât
            p.pol(2f, -50f, 22f, -44f, 2f, -38f)                          // le fanion
            p.pol(-30f, 4f, -14f, 4f, -14f, 20f, -30f, 20f)
            p.pol(14f, 4f, 30f, 4f, 30f, 20f, 14f, 20f)
            p.pol(-10f, 16f, 10f, 16f, 10f, 44f, -10f, 44f)
        },
        "pilier" to { p ->
            p.pol(-32f, -46f, 32f, -46f, 32f, -36f, -32f, -36f)
            p.pol(-24f, -36f, 24f, -36f, 20f, 34f, -20f, 34f)
            p.pol(-34f, 34f, 34f, 34f, 34f, 46f, -34f, 46f)
        },
        "poteau" to { p ->
            p.pol(-6f, -24f, 6f, -24f, 6f, 46f, -6f, 46f)              // le mât
            p.pol(-34f, -46f, 26f, -46f, 40f, -35f, 26f, -24f, -34f, -24f)  // la plaque
        },
        "tunnel" to { p ->
            p.moveTo(-46f, 44f)
            p.lineTo(-46f, 4f)
            p.cubicTo(-46f, -30f, 46f, -30f, 46f, 4f)
            p.lineTo(46f, 44f)
            p.close()
            p.moveTo(-24f, 44f)
            p.lineTo(-24f, 8f)
            p.cubicTo(-24f, -12f, 24f, -12f, 24f, 8f)
            p.lineTo(24f, 44f)
            p.close()
        },
        "grue" to { p ->
            p.pol(-44f, -34f, 44f, -34f, 44f, -24f, -44f, -24f)        // la flèche
            p.pol(-40f, -24f, -28f, -24f, -28f, -8f, -40f, -8f)        // le contrepoids
            p.pol(-6f, -24f, 6f, -24f, 6f, 40f, -6f, 40f)              // le mât
            p.pol(-24f, 40f, 24f, 40f, 30f, 48f, -30f, 48f)            // l'embase
            p.pol(26f, -24f, 30f, -24f, 30f, 4f, 26f, 4f)              // le câble
            p.pol(20f, 4f, 36f, 4f, 36f, 16f, 20f, 16f)                // le crochet
        },
        "moulin" to { p ->
            // Les quatre ailes partent d'un moyeu et ne se touchent jamais :
            // deux ailes qui se croiseraient se perceraient l'une l'autre.
            p.pol(-22f, 46f, -13f, -6f, 13f, -6f, 22f, 46f)           // le corps
            p.disque(0f, -22f, 8f)                                    // le moyeu
            p.pol(-11f, -25f, -46f, -30f, -46f, -20f, -11f, -19f)     // les ailes
            p.pol(11f, -25f, 46f, -30f, 46f, -20f, 11f, -19f)
            p.pol(-3f, -33f, -8f, -50f, 2f, -50f, 3f, -33f)
            p.pol(-3f, -11f, -8f, 6f, 2f, 6f, 3f, -11f)
        },
        "tente" to { p ->
            p.pol(-48f, 40f, 0f, -44f, 48f, 40f)
            p.pol(-14f, 40f, 0f, 2f, 14f, 40f)                        // l'ouverture
        },

        "hopital" to { p ->
            p.pol(-40f, -34f, 40f, -34f, 40f, 46f, -40f, 46f)
            p.pol(-6f, -17f, 6f, -17f, 6f, -6f, 17f, -6f, 17f, 6f, 6f, 6f,
                  6f, 17f, -6f, 17f, -6f, 6f, -17f, 6f, -17f, -6f, -6f, -6f)  // la croix
            p.pol(-9f, 26f, 9f, 26f, 9f, 46f, -9f, 46f)                       // l'entrée
        },
        "banque" to { p ->
            p.pol(-46f, -14f, 0f, -40f, 46f, -14f)                     // le fronton
            p.pol(-42f, -14f, 42f, -14f, 42f, -4f, -42f, -4f)          // l'architrave
            p.pol(-35f, -4f, -25f, -4f, -25f, 34f, -35f, 34f)          // quatre colonnes
            p.pol(-15f, -4f, -5f, -4f, -5f, 34f, -15f, 34f)
            p.pol(5f, -4f, 15f, -4f, 15f, 34f, 5f, 34f)
            p.pol(25f, -4f, 35f, -4f, 35f, 34f, 25f, 34f)
            p.pol(-46f, 34f, 46f, 34f, 46f, 44f, -46f, 44f)            // le stylobate
        },
        "batiment" to { p ->
            p.pol(-30f, -46f, 30f, -46f, 30f, 46f, -30f, 46f)
            for (i in 0..5) {
                val y = -40f + i * 12f
                p.pol(-22f, y, -12f, y, -12f, y + 8f, -22f, y + 8f)
                p.pol(-5f, y, 5f, y, 5f, y + 8f, -5f, y + 8f)
                p.pol(12f, y, 22f, y, 22f, y + 8f, 12f, y + 8f)
            }
            p.pol(-8f, 34f, 8f, 34f, 8f, 46f, -8f, 46f)                // la porte
        },
        "prison" to { p ->
            // Les barreaux sont *dans* l'ouverture : profondeur trois, donc
            // pleins. C'est le seul endroit de la bibliothèque où la règle
            // pair-impair sert trois fois de suite.
            p.pol(-38f, -42f, 38f, -42f, 38f, 42f, -38f, 42f)
            p.pol(-30f, -34f, 30f, -34f, 30f, 34f, -30f, 34f)          // l'ouverture
            p.pol(-21f, -34f, -15f, -34f, -15f, 34f, -21f, 34f)
            p.pol(-9f, -34f, -3f, -34f, -3f, 34f, -9f, 34f)
            p.pol(3f, -34f, 9f, -34f, 9f, 34f, 3f, 34f)
            p.pol(15f, -34f, 21f, -34f, 21f, 34f, 15f, 34f)
        },
        "mur" to { p ->
            // Les joints verticaux s'arrêtent aux joints horizontaux : deux
            // réserves qui se croiseraient redeviendraient pleines.
            p.pol(-46f, -30f, 46f, -30f, 46f, 38f, -46f, 38f)
            p.pol(-46f, -14f, 46f, -14f, 46f, -11f, -46f, -11f)
            p.pol(-46f, 3f, 46f, 3f, 46f, 6f, -46f, 6f)
            p.pol(-46f, 20f, 46f, 20f, 46f, 23f, -46f, 23f)
            p.pol(-24.5f, -30f, -21.5f, -30f, -21.5f, -14f, -24.5f, -14f)
            p.pol(-1.5f, -30f, 1.5f, -30f, 1.5f, -14f, -1.5f, -14f)
            p.pol(21.5f, -30f, 24.5f, -30f, 24.5f, -14f, 21.5f, -14f)
            p.pol(-36.5f, -11f, -33.5f, -11f, -33.5f, 3f, -36.5f, 3f)
            p.pol(-13.5f, -11f, -10.5f, -11f, -10.5f, 3f, -13.5f, 3f)
            p.pol(10.5f, -11f, 13.5f, -11f, 13.5f, 3f, 10.5f, 3f)
            p.pol(33.5f, -11f, 36.5f, -11f, 36.5f, 3f, 33.5f, 3f)
            p.pol(-24.5f, 6f, -21.5f, 6f, -21.5f, 20f, -24.5f, 20f)
            p.pol(-1.5f, 6f, 1.5f, 6f, 1.5f, 20f, -1.5f, 20f)
            p.pol(21.5f, 6f, 24.5f, 6f, 24.5f, 20f, 21.5f, 20f)
            p.pol(-36.5f, 23f, -33.5f, 23f, -33.5f, 38f, -36.5f, 38f)
            p.pol(-13.5f, 23f, -10.5f, 23f, -10.5f, 38f, -13.5f, 38f)
            p.pol(10.5f, 23f, 13.5f, 23f, 13.5f, 38f, 10.5f, 38f)
            p.pol(33.5f, 23f, 36.5f, 23f, 36.5f, 38f, 33.5f, 38f)
        },
        "gare" to { p ->
            // Une seule grande arche sous une horloge ronde se lisait comme une
            // tête au-dessus d'un corps : trois portes le tuent net.
            p.pol(-48f, -20f, 0f, -44f, 48f, -20f)
            p.pol(-44f, -20f, 44f, -20f, 44f, 44f, -44f, 44f)
            p.disque(0f, -4f, 10f)
            p.pol(-34f, 14f, -14f, 14f, -14f, 44f, -34f, 44f)
            p.pol(-10f, 14f, 10f, 14f, 10f, 44f, -10f, 44f)
            p.pol(14f, 14f, 34f, 14f, 34f, 44f, 14f, 44f)
        },
        "parking" to { p ->
            p.arrondi(-40f, -40f, 40f, 40f, 10f)
            p.moveTo(-16f, -26f)                                       // le P, en réserve
            p.lineTo(6f, -26f)
            p.cubicTo(22f, -26f, 22f, 2f, 6f, 2f)
            p.lineTo(-5f, 2f)
            p.lineTo(-5f, 26f)
            p.lineTo(-16f, 26f)
            p.close()
            p.moveTo(-5f, -16f)                                        // sa contreforme
            p.lineTo(5f, -16f)
            p.cubicTo(12f, -16f, 12f, -8f, 5f, -8f)
            p.lineTo(-5f, -8f)
            p.close()
        },
        // ---- la table ------------------------------------------------------

        "pain" to { p ->
            p.moveTo(-46f, 26f)
            p.cubicTo(-46f, -18f, -30f, -34f, 0f, -34f)
            p.cubicTo(30f, -34f, 46f, -18f, 46f, 26f)
            p.cubicTo(46f, 34f, 40f, 36f, 32f, 36f)
            p.lineTo(-32f, 36f)
            p.cubicTo(-40f, 36f, -46f, 34f, -46f, 26f)
            p.close()
            for (i in -1..1) {
                val x = i * 22f
                p.pol(x - 12f, -18f, x + 2f, -22f, x + 5f, -13f, x - 9f, -9f)
            }
        },
        "fromage" to { p ->
            p.pol(-44f, 30f, -44f, -8f, 44f, -30f, 44f, 30f)
            p.disque(-20f, 12f, 7f)
            p.disque(4f, -2f, 5f)
            p.disque(24f, 14f, 6f)
        },
        "gateau" to { p ->
            p.pol(-40f, 44f, -40f, 4f, 40f, 4f, 40f, 44f)
            p.pol(-34f, 4f, -34f, -16f, 34f, -16f, 34f, 4f)
            p.pol(-3f, -16f, 3f, -16f, 3f, -34f, -3f, -34f)           // la bougie
            p.moveTo(0f, -50f)
            p.cubicTo(7f, -44f, 6f, -36f, 0f, -36f)
            p.cubicTo(-6f, -36f, -7f, -44f, 0f, -50f)
            p.close()
            p.pol(-40f, 16f, 40f, 16f, 40f, 22f, -40f, 22f)
        },
        "pizza" to { p ->
            // Une part, pas une galette : c'est la pointe qui la sépare d'un pain.
            p.moveTo(0f, -46f)
            p.lineTo(34f, 32f)
            p.cubicTo(22f, 42f, -22f, 42f, -34f, 32f)
            p.close()
            p.disque(0f, -10f, 7f)
            p.disque(-15f, 14f, 7f)
            p.disque(15f, 14f, 7f)
        },
        "fruits" to { p ->
            p.moveTo(-14f, -12f)                                       // la poire
            p.cubicTo(-4f, -12f, 2f, 0f, 2f, 16f)
            p.cubicTo(2f, 34f, -8f, 44f, -20f, 44f)
            p.cubicTo(-32f, 44f, -42f, 34f, -42f, 16f)
            p.cubicTo(-42f, 0f, -34f, -12f, -26f, -14f)
            p.cubicTo(-26f, -26f, -24f, -34f, -20f, -40f)
            p.lineTo(-14f, -38f)
            p.cubicTo(-18f, -32f, -20f, -24f, -20f, -14f)
            p.close()
            p.moveTo(24f, -6f)                                         // la pomme
            p.cubicTo(37f, -6f, 46f, 6f, 46f, 21f)
            p.cubicTo(46f, 34f, 37f, 44f, 26f, 44f)
            p.cubicTo(15f, 44f, 8f, 34f, 8f, 21f)
            p.cubicTo(8f, 6f, 13f, -6f, 24f, -6f)
            p.close()
            p.pol(24f, -8f, 30f, -8f, 30f, -24f, 24f, -24f)
        },
        "lait" to { p ->
            p.arrondi(-28f, -18f, 26f, 44f, 6f)                        // le pot
            p.pol(-18f, -34f, 16f, -34f, 18f, -18f, -20f, -18f)        // le col
            p.pol(-22f, -42f, 20f, -42f, 20f, -34f, -22f, -34f)        // le couvercle
            p.pol(26f, -8f, 46f, -2f, 46f, 20f, 26f, 26f)              // l'anse
            p.pol(29f, 2f, 39f, 5f, 39f, 13f, 29f, 16f)                // son ajour
        },
        "bouteille" to { p ->
            p.pol(-8f, -46f, 8f, -46f, 8f, -42f, -8f, -42f)
            p.moveTo(-8f, -42f)
            p.lineTo(8f, -42f)
            p.lineTo(8f, -20f)
            p.cubicTo(8f, -8f, 22f, -4f, 22f, 12f)
            p.lineTo(22f, 40f)
            p.cubicTo(22f, 45f, 18f, 46f, 12f, 46f)
            p.lineTo(-12f, 46f)
            p.cubicTo(-18f, 46f, -22f, 45f, -22f, 40f)
            p.lineTo(-22f, 12f)
            p.cubicTo(-22f, -4f, -8f, -8f, -8f, -20f)
            p.close()
            p.pol(-18f, 8f, 18f, 8f, 18f, 26f, -18f, 26f)              // l'étiquette
        },
        "vin" to { p ->
            p.moveTo(-26f, -42f)
            p.lineTo(26f, -42f)
            p.cubicTo(26f, -10f, 14f, 2f, 4f, 4f)
            p.lineTo(4f, 34f)
            p.lineTo(22f, 34f)
            p.lineTo(22f, 44f)
            p.lineTo(-22f, 44f)
            p.lineTo(-22f, 34f)
            p.lineTo(-4f, 34f)
            p.lineTo(-4f, 4f)
            p.cubicTo(-14f, 2f, -26f, -10f, -26f, -42f)
            p.close()
            p.pol(-22f, -22f, 22f, -22f, 20f, -12f, -20f, -12f)        // le vin
        },
        "cafe" to { p ->
            p.pol(-34f, -12f, 22f, -12f, 18f, 28f, -30f, 28f)          // la tasse
            p.moveTo(22f, -6f)
            p.cubicTo(44f, -6f, 46f, 20f, 24f, 20f)
            p.lineTo(24f, 10f)
            p.cubicTo(34f, 10f, 34f, 4f, 22f, 4f)
            p.close()
            p.pol(-42f, 28f, 34f, 28f, 34f, 40f, -42f, 40f)            // la soucoupe
            p.pol(-16f, -44f, -10f, -44f, -10f, -22f, -16f, -22f)      // la vapeur
            p.pol(2f, -44f, 8f, -44f, 8f, -22f, 2f, -22f)
        },
        "couteau" to { p ->
            // Lame, mitre et manche séparés par deux jours : collés, les trois
            // se lisent comme une seule flèche.
            p.moveTo(-46f, 2f)
            p.lineTo(8f, -20f)
            p.lineTo(8f, 6f)
            p.cubicTo(-10f, 12f, -30f, 10f, -46f, 2f)
            p.close()
            p.pol(11f, -20f, 17f, -20f, 17f, 8f, 11f, 8f)              // la mitre
            p.arrondi(20f, -18f, 46f, 10f, 6f)                         // le manche
        },
        "eau" to { p ->
            for (i in 0..2) {
                val y = -26f + i * 26f
                p.moveTo(-46f, y)
                p.cubicTo(-30f, y - 13f, -16f, y + 13f, 0f, y)
                p.cubicTo(16f, y - 13f, 30f, y + 13f, 46f, y)
                p.lineTo(46f, y + 9f)
                p.cubicTo(30f, y + 22f, 16f, y - 4f, 0f, y + 9f)
                p.cubicTo(-16f, y + 22f, -30f, y - 4f, -46f, y + 9f)
                p.close()
            }
        },

        // ---- le vivant -----------------------------------------------------

        "arbre" to { p ->
            // La couronne se ferme sur une base droite à y = 8, et le tronc part
            // de cette même ligne : aboutés, jamais superposés — sinon la règle
            // pair-impair creuse le tronc dans la couronne.
            p.moveTo(-32f, 8f)
            p.cubicTo(-46f, 2f, -46f, -22f, -28f, -30f)
            p.cubicTo(-24f, -48f, 8f, -52f, 20f, -38f)
            p.cubicTo(42f, -36f, 48f, -12f, 32f, -2f)
            p.cubicTo(36f, 4f, 35f, 8f, 30f, 8f)
            p.close()
            p.pol(-8f, 8f, 8f, 8f, 11f, 46f, -11f, 46f)
        },
        "coeur" to { p ->
            p.moveTo(0f, 44f)
            p.cubicTo(-46f, 14f, -46f, -20f, -24f, -32f)
            p.cubicTo(-10f, -40f, 0f, -30f, 0f, -20f)
            p.cubicTo(0f, -30f, 10f, -40f, 24f, -32f)
            p.cubicTo(46f, -20f, 46f, 14f, 0f, 44f)
            p.close()
        },
        "poisson" to { p ->
            p.moveTo(46f, 0f)
            p.cubicTo(30f, -26f, -6f, -30f, -22f, -14f)
            p.lineTo(-46f, -26f); p.lineTo(-36f, 0f); p.lineTo(-46f, 26f); p.lineTo(-22f, 14f)
            p.cubicTo(-6f, 30f, 30f, 26f, 46f, 0f)
            p.close()
            p.disque(22f, -6f, 4.5f)
        },
        "chat" to { p ->
            p.pol(-34f, -14f, -30f, -46f, -8f, -28f)
            p.pol(34f, -14f, 30f, -46f, 8f, -28f)
            p.moveTo(0f, -32f)
            p.cubicTo(26f, -32f, 38f, -14f, 38f, 4f)
            p.cubicTo(38f, 26f, 22f, 40f, 0f, 40f)
            p.cubicTo(-22f, 40f, -38f, 26f, -38f, 4f)
            p.cubicTo(-38f, -14f, -26f, -32f, 0f, -32f)
            p.close()
        },
        "chien" to { p ->
            // Tête et oreilles d'un seul contour. Une oreille posée par-dessus
            // le crâne s'y creuserait au lieu de s'y rattacher : c'est la règle
            // pair-impair, et elle vaut pour les cinq têtes qui suivent.
            p.moveTo(0f, -38f)
            p.cubicTo(16f, -38f, 26f, -28f, 28f, -16f)
            p.cubicTo(40f, -20f, 48f, -4f, 42f, 14f)
            p.cubicTo(38f, 26f, 30f, 30f, 25f, 26f)
            p.cubicTo(22f, 38f, 12f, 46f, 0f, 46f)
            p.cubicTo(-12f, 46f, -22f, 38f, -25f, 26f)
            p.cubicTo(-30f, 30f, -38f, 26f, -42f, 14f)
            p.cubicTo(-48f, -4f, -40f, -20f, -28f, -16f)
            p.cubicTo(-26f, -28f, -16f, -38f, 0f, -38f)
            p.close()
            p.disque(-12f, -4f, 4f)                                    // les yeux
            p.disque(12f, -4f, 4f)
            p.ovale(-12f, 18f, 12f, 34f)                               // le museau
            p.ovale(-6f, 20f, 6f, 28f)                                 // la truffe
        },
        "lion" to { p ->
            // La crinière EST la silhouette : dix pointes d'un seul contour. Un
            // mufle posé par-dessus se creuserait dedans, alors il est en
            // réserve — et le nez, posé dans le mufle, redevient plein.
            for (i in 0..19) {
                val a = i * Math.PI.toFloat() / 10f - Math.PI.toFloat() / 2f
                val r = if (i % 2 == 0) 49f else 31f
                val x = Math.cos(a.toDouble()).toFloat() * r
                val y = Math.sin(a.toDouble()).toFloat() * r
                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            p.close()
            p.disque(-13f, -9f, 4.5f)
            p.disque(13f, -9f, 4.5f)
            p.ovale(-16f, 4f, 16f, 24f)                                // le mufle
            p.pol(0f, 17f, 8f, 7f, -8f, 7f)                            // le nez
        },
        "cheval" to { p ->
            p.moveTo(-6f, -46f)                        // le toupet
            p.lineTo(4f, -30f)
            p.cubicTo(22f, -28f, 38f, -16f, 44f, 0f)
            p.lineTo(26f, 8f)
            p.cubicTo(22f, 0f, 12f, -4f, 4f, 0f)
            p.cubicTo(-4f, 10f, -8f, 26f, -8f, 46f)
            p.lineTo(-32f, 46f)
            p.cubicTo(-32f, 16f, -26f, -4f, -14f, -16f)
            p.lineTo(-26f, -22f)                       // la crinière, en dents
            p.lineTo(-14f, -26f)
            p.lineTo(-24f, -36f)
            p.lineTo(-10f, -32f)
            p.close()
            p.disque(16f, -8f, 3.5f)
        },
        "boeuf" to { p ->
            p.moveTo(0f, -14f)
            p.cubicTo(16f, -14f, 27f, -8f, 31f, 2f)
            p.lineTo(42f, -34f)                                        // la corne droite
            p.lineTo(50f, -28f)
            p.lineTo(36f, 10f)
            p.cubicTo(35f, 27f, 20f, 42f, 0f, 42f)
            p.cubicTo(-20f, 42f, -35f, 27f, -36f, 10f)
            p.lineTo(-50f, -28f)                                       // la corne gauche
            p.lineTo(-42f, -34f)
            p.lineTo(-31f, 2f)
            p.cubicTo(-27f, -8f, -16f, -14f, 0f, -14f)
            p.close()
            p.disque(-13f, 8f, 4f)
            p.disque(13f, 8f, 4f)
            p.ovale(-15f, 22f, 15f, 36f)                               // le mufle
        },
        "chevreuil" to { p ->
            p.moveTo(0f, -16f)
            p.lineTo(11f, -22f)                                        // le bois droit
            p.lineTo(13f, -40f)
            p.lineTo(23f, -32f)
            p.lineTo(17f, -50f)
            p.lineTo(28f, -46f)
            p.lineTo(22f, -20f)
            p.cubicTo(27f, -12f, 24f, 2f, 22f, 12f)
            p.cubicTo(20f, 30f, 12f, 44f, 0f, 44f)
            p.cubicTo(-12f, 44f, -20f, 30f, -22f, 12f)
            p.cubicTo(-24f, 2f, -27f, -12f, -22f, -20f)
            p.lineTo(-28f, -46f)                                       // le bois gauche
            p.lineTo(-17f, -50f)
            p.lineTo(-23f, -32f)
            p.lineTo(-13f, -40f)
            p.lineTo(-11f, -22f)
            p.close()
            p.disque(-9f, 2f, 3.5f)
            p.disque(9f, 2f, 3.5f)
            p.ovale(-8f, 28f, 8f, 38f)                                 // le mufle
        },
        "merle" to { p ->
            p.moveTo(-46f, -18f)                       // la pointe du bec
            p.lineTo(-20f, -26f)
            p.cubicTo(-16f, -36f, -2f, -38f, 6f, -30f)
            p.cubicTo(14f, -22f, 16f, -12f, 14f, -6f)
            p.cubicTo(30f, 0f, 40f, 12f, 40f, 24f)
            p.lineTo(48f, 40f)                         // la queue
            p.lineTo(24f, 34f)
            p.cubicTo(10f, 38f, -8f, 36f, -18f, 26f)
            p.cubicTo(-30f, 14f, -32f, -2f, -22f, -10f)
            p.close()
            p.disque(-9f, -22f, 3.5f)                  // l'œil
            p.pol(-7f, 37f, -1f, 37f, -3f, 47f, -10f, 47f)   // les pattes
            p.pol(9f, 35f, 15f, 35f, 14f, 47f, 7f, 47f)
        },
        "main" to { p ->
            // Doigts et paume s'aboutissent à y = -2 : superposés, chaque doigt
            // se creuserait dans la paume au lieu de s'y rattacher.
            p.arrondi(-30f, -2f, 30f, 46f, 9f)                          // la paume
            p.arrondi(-27f, -30f, -16f, -2f, 5.5f)
            p.arrondi(-13f, -42f, -2f, -2f, 5.5f)
            p.arrondi(1f, -40f, 12f, -2f, 5.5f)
            p.arrondi(15f, -28f, 26f, -2f, 5.5f)
            p.moveTo(-30f, 8f)                                          // le pouce
            p.cubicTo(-42f, 2f, -50f, 14f, -42f, 24f)
            p.lineTo(-30f, 24f)
            p.close()
        },
        "pied" to { p ->
            // Une empreinte, plante et orteils détachés : un profil de pied ne
            // se lit pas, une empreinte si.
            p.moveTo(-12f, -8f)
            p.cubicTo(10f, -8f, 22f, 6f, 22f, 22f)
            p.cubicTo(22f, 38f, 10f, 48f, -4f, 48f)
            p.cubicTo(-18f, 48f, -26f, 38f, -26f, 24f)
            p.cubicTo(-26f, 8f, -24f, -8f, -12f, -8f)
            p.close()
            p.ovale(-27f, -32f, -10f, -13f)                            // les orteils
            p.ovale(-7f, -40f, 5f, -23f)
            p.ovale(8f, -36f, 19f, -21f)
            p.ovale(21f, -28f, 31f, -15f)
            p.ovale(32f, -18f, 41f, -7f)
        },
        "tete" to { p ->
            p.moveTo(-6f, -46f)                                        // le profil
            p.cubicTo(18f, -46f, 30f, -30f, 30f, -10f)
            p.cubicTo(30f, 2f, 26f, 8f, 32f, 14f)
            p.cubicTo(36f, 18f, 30f, 22f, 24f, 22f)
            p.cubicTo(24f, 34f, 20f, 40f, 8f, 42f)
            p.lineTo(8f, 46f)
            p.lineTo(-16f, 46f)
            p.lineTo(-16f, 20f)
            p.cubicTo(-30f, 12f, -34f, -12f, -24f, -30f)
            p.cubicTo(-20f, -40f, -14f, -46f, -6f, -46f)
            p.close()
            p.disque(6f, -14f, 4f)
        },
        "dent" to { p ->
            p.moveTo(0f, -40f)
            p.cubicTo(20f, -40f, 34f, -30f, 34f, -12f)
            p.cubicTo(34f, 4f, 26f, 16f, 22f, 34f)
            p.cubicTo(20f, 44f, 8f, 46f, 6f, 32f)
            p.cubicTo(4f, 18f, -4f, 18f, -6f, 32f)
            p.cubicTo(-8f, 46f, -20f, 44f, -22f, 34f)
            p.cubicTo(-26f, 16f, -34f, 4f, -34f, -12f)
            p.cubicTo(-34f, -30f, -20f, -40f, 0f, -40f)
            p.close()
        },
        "ange" to { p ->
            p.ovale(-12f, -46f, 12f, -22f)                             // la tête
            p.ovale(-16f, -50f, 16f, -40f)                             // l'auréole
            p.ovale(-11f, -49f, 11f, -43f)
            p.pol(-18f, -18f, 18f, -18f, 26f, 46f, -26f, 46f)          // la robe
            p.moveTo(-18f, -14f)                                       // les ailes
            p.cubicTo(-40f, -22f, -50f, -4f, -44f, 16f)
            p.cubicTo(-34f, 8f, -24f, 4f, -18f, 4f)
            p.close()
            p.moveTo(18f, -14f)
            p.cubicTo(40f, -22f, 50f, -4f, 44f, 16f)
            p.cubicTo(34f, 8f, 24f, 4f, 18f, 4f)
            p.close()
        },

        "foret" to { p ->
            p.pol(-44f, 10f, -30f, -34f, -16f, 10f)
            p.pol(-33f, 10f, -27f, 10f, -27f, 24f, -33f, 24f)
            p.pol(-16f, 20f, 0f, -44f, 16f, 20f)
            p.pol(-3f, 20f, 3f, 20f, 3f, 36f, -3f, 36f)
            p.pol(16f, 10f, 30f, -34f, 44f, 10f)
            p.pol(27f, 10f, 33f, 10f, 33f, 24f, 27f, 24f)
        },
        "ile" to { p ->
            p.moveTo(-42f, 34f)                                        // l'îlot, à sommet plat
            p.cubicTo(-34f, 18f, -20f, 18f, -12f, 18f)
            p.lineTo(12f, 18f)
            p.cubicTo(20f, 18f, 34f, 18f, 42f, 34f)
            p.close()
            p.moveTo(-2f, -22f)                                        // le stipe, abouté au
            p.cubicTo(-8f, -6f, -8f, 6f, -6f, 18f)                     // sommet plat et à la
            p.lineTo(6f, 18f)                                          // base des palmes
            p.cubicTo(6f, 6f, 6f, -6f, 8f, -22f)
            p.close()
            // Les cinq palmes sont un seul contour, sommets rangés par angle :
            // cinq triangles séparés se recouvraient près du stipe.
            p.pol(-8f, -22f, -40f, -30f, -16f, -26f, -32f, -47f, -6f, -30f,
                  0f, -50f, 8f, -30f, 30f, -47f, 16f, -26f, 40f, -28f, 10f, -22f)
        },
        "virus" to { p ->
            // Le corps est un polygone à douze côtés et non un disque : les
            // spicules se posent sur ses arêtes plates, donc elles s'aboutent
            // au lieu de le tangenter.
            p.pol(28f, 0f, 24.25f, 14f, 14f, 24.25f, 0f, 28f, -14f, 24.25f,
                  -24.25f, 14f, -28f, 0f, -24.25f, -14f, -14f, -24.25f,
                  0f, -28f, 14f, -24.25f, 24.25f, -14f)
            for (i in 0..11) {
                val a = i * 0.5235988f + 0.2617994f
                val ux = Math.cos(a.toDouble()).toFloat()
                val uy = Math.sin(a.toDouble()).toFloat()
                p.pol(ux * 27.05f - uy * 3f, uy * 27.05f + ux * 3f,
                      ux * 42f - uy * 4.5f, uy * 42f + ux * 4.5f,
                      ux * 42f + uy * 4.5f, uy * 42f - ux * 4.5f,
                      ux * 27.05f + uy * 3f, uy * 27.05f - ux * 3f)
            }
            p.disque(-8f, -4f, 5f)
            p.disque(7f, 6f, 4f)
        },
        // ---- le ciel -------------------------------------------------------

        "soleil" to { p ->
            p.disque(0f, 0f, 21f)
            for (i in 0..11) {
                val a = i * Math.PI.toFloat() / 6f
                val c = Math.cos(a.toDouble()).toFloat()
                val s = Math.sin(a.toDouble()).toFloat()
                val r2 = if (i % 2 == 1) 40f else 47f
                p.pol(c * 28f - s * 4.5f, s * 28f + c * 4.5f,
                      c * r2, s * r2,
                      c * 28f + s * 4.5f, s * 28f - c * 4.5f)
            }
        },
        "lune" to { p ->
            // Un croissant est un seul contour à deux arcs. Deux disques ne se
            // soustraient pas : la part du second qui sort du premier n'est
            // recouverte qu'une fois, donc elle se remplit — c'est ce que
            // faisait ce meuble, avec un lobe plein hors cadre en prime.
            p.moveTo(12f, -38f)
            p.cubicTo(-24f, -44f, -44f, -20f, -40f, 4f)
            p.cubicTo(-36f, 30f, -8f, 44f, 12f, 38f)
            p.cubicTo(-8f, 22f, -8f, -22f, 12f, -38f)
            p.close()
        },
        "pluie" to { p ->
            p.moveTo(-40f, 2f)
            p.cubicTo(-40f, -14f, -26f, -22f, -14f, -18f)
            p.cubicTo(-8f, -34f, 16f, -36f, 22f, -20f)
            p.cubicTo(38f, -22f, 44f, -6f, 34f, 2f)
            p.close()
            for (i in -1..1) {
                val x = i * 24f
                p.moveTo(x, 14f)
                p.cubicTo(x + 10f, 26f, x + 8f, 40f, x, 40f)
                p.cubicTo(x - 8f, 40f, x - 10f, 26f, x, 14f)
                p.close()
            }
        },

        "flocon" to { p ->
            // Six barbillons compris dans le contour de chaque branche : posés
            // par-dessus, ils y auraient creusé leur croisement. Sans eux la
            // silhouette disait « étoile ».
            p.pol(6.93f, 4f, 0f, 8f, -6.93f, 4f, -6.93f, -4f, 0f, -8f, 6.93f, -4f)
            for (i in 0..5) {
                val a = i * 1.0471976f
                val ux = Math.cos(a.toDouble()).toFloat()
                val uy = Math.sin(a.toDouble()).toFloat()
                p.pol(ux * 6.93f - uy * 3.5f, uy * 6.93f + ux * 3.5f,
                      ux * 20f - uy * 3.5f, uy * 20f + ux * 3.5f,
                      ux * 30f - uy * 12f, uy * 30f + ux * 12f,
                      ux * 25f - uy * 3.2f, uy * 25f + ux * 3.2f,
                      ux * 45f - uy * 2f, uy * 45f + ux * 2f,
                      ux * 45f + uy * 2f, uy * 45f - ux * 2f,
                      ux * 25f + uy * 3.2f, uy * 25f - ux * 3.2f,
                      ux * 30f + uy * 12f, uy * 30f - ux * 12f,
                      ux * 20f + uy * 3.5f, uy * 20f - ux * 3.5f,
                      ux * 6.93f + uy * 3.5f, uy * 6.93f - ux * 3.5f)
            }
        },
        "globe" to { p ->
            p.disque(0f, 0f, 44f)
            p.pol(-43f, -4f, 43f, -4f, 43f, 4f, -43f, 4f)              // l'équateur
            p.pol(-38f, -22f, 38f, -22f, 38f, -18f, -38f, -18f)        // deux parallèles
            p.pol(-38f, 18f, 38f, 18f, 38f, 22f, -38f, 22f)
            p.pol(-4f, -43f, 4f, -43f, 4f, -22f, -4f, -22f)            // le méridien, en
            p.pol(-4f, -18f, 4f, -18f, 4f, -4f, -4f, -4f)              // quatre tronçons
            p.pol(-4f, 4f, 4f, 4f, 4f, 18f, -4f, 18f)                  // qui n'en croisent
            p.pol(-4f, 22f, 4f, 22f, 4f, 43f, -4f, 43f)                // aucun
        },
        "europe" to { p ->
            for (i in 0..11) {
                val a = i * 0.5235988f
                val cx = 33f * Math.sin(a.toDouble()).toFloat()
                val cy = -33f * Math.cos(a.toDouble()).toFloat()
                p.pol(cx, cy - 7f, cx + 1.65f, cy - 2.26f, cx + 6.66f, cy - 2.16f,
                      cx + 2.66f, cy + 0.87f, cx + 4.11f, cy + 5.66f, cx, cy + 2.8f,
                      cx - 4.11f, cy + 5.66f, cx - 2.66f, cy + 0.87f,
                      cx - 6.66f, cy - 2.16f, cx - 1.65f, cy - 2.26f)
            }
        },
        // ---- ce qui roule et ce qui vole -----------------------------------

        "voiture" to { p ->
            p.moveTo(-46f, 20f)
            p.lineTo(-42f, -2f)
            p.lineTo(-28f, -4f)
            p.lineTo(-16f, -24f)
            p.lineTo(20f, -24f)
            p.lineTo(30f, -4f)
            p.lineTo(46f, 0f)
            p.lineTo(46f, 20f)
            p.close()
            p.pol(-12f, -18f, 0f, -18f, 0f, -6f, -20f, -6f)            // les vitres
            p.pol(6f, -18f, 17f, -18f, 24f, -6f, 6f, -6f)
            p.disque(-26f, 22f, 12f)
            p.disque(26f, 22f, 12f)
            p.disque(-26f, 22f, 5f)
            p.disque(26f, 22f, 5f)
        },
        "velo" to { p ->
            p.disque(-26f, 16f, 22f)
            p.disque(-26f, 16f, 17f)
            p.disque(26f, 16f, 22f)
            p.disque(26f, 16f, 17f)
            // Le cadre est un seul triangle évidé : trois barres superposées se
            // perceraient l'une l'autre au moyeu.
            p.pol(-6f, -18f, 28f, -18f, -24f, 20f)
            p.pol(-2f, -10f, 15f, -10f, -14f, 11f)
            p.pol(-16f, -28f, 2f, -28f, 2f, -20f, -16f, -20f)          // la selle
            p.pol(18f, -36f, 38f, -36f, 38f, -28f, 18f, -28f)          // le guidon
            p.pol(24f, -28f, 30f, -28f, 30f, -18f, 24f, -18f)          // la potence
        },
        "avion" to { p ->
            p.moveTo(0f, -46f)
            p.cubicTo(8f, -46f, 12f, -34f, 12f, -18f)
            p.lineTo(46f, 6f)
            p.lineTo(46f, 18f)
            p.lineTo(12f, 8f)
            p.lineTo(12f, 28f)
            p.lineTo(24f, 38f)
            p.lineTo(24f, 46f)
            p.lineTo(0f, 40f)
            p.lineTo(-24f, 46f)
            p.lineTo(-24f, 38f)
            p.lineTo(-12f, 28f)
            p.lineTo(-12f, 8f)
            p.lineTo(-46f, 18f)
            p.lineTo(-46f, 6f)
            p.lineTo(-12f, -18f)
            p.cubicTo(-12f, -34f, -8f, -46f, 0f, -46f)
            p.close()
        },
        "helicoptere" to { p ->
            p.pol(-46f, -38f, 46f, -38f, 46f, -31f, -46f, -31f)        // le rotor
            p.pol(-3f, -31f, 3f, -31f, 3f, -22f, -3f, -22f)            // le mât
            p.moveTo(-24f, -22f)                                       // la cabine
            p.cubicTo(0f, -22f, 16f, -10f, 18f, 2f)
            p.lineTo(38f, 5f)
            p.lineTo(38f, 15f)
            p.lineTo(18f, 13f)
            p.cubicTo(12f, 23f, -2f, 27f, -16f, 27f)
            p.cubicTo(-34f, 27f, -44f, 16f, -44f, 3f)
            p.cubicTo(-44f, -10f, -36f, -22f, -24f, -22f)
            p.close()
            p.pol(38f, -8f, 48f, -12f, 48f, 22f, 38f, 15f)             // la dérive
            p.pol(-30f, 27f, -24f, 27f, -24f, 34f, -30f, 34f)          // les jambes
            p.pol(-6f, 27f, 0f, 27f, 0f, 34f, -6f, 34f)
            p.pol(-38f, 34f, 12f, 34f, 12f, 42f, -38f, 42f)            // les patins
        },
        "bateau" to { p ->
            p.moveTo(-46f, 20f)                                        // la coque
            p.lineTo(46f, 20f)
            p.lineTo(36f, 44f)
            p.lineTo(-36f, 44f)
            p.close()
            p.pol(-3f, -46f, 3f, -46f, 3f, 16f, -3f, 16f)              // le mât
            p.pol(6f, -40f, 38f, 14f, 6f, 14f)                         // les voiles
            p.pol(-6f, -34f, -6f, 14f, -32f, 14f)
        },

        "bus" to { p ->
            // Les roues sont des rectangles arrondis aboutés au bas de la
            // caisse : un disque ne peut toucher une droite qu'en un point, et
            // le moindre chevauchement creuserait la caisse.
            p.arrondi(-46f, -28f, 46f, 24f, 7f)
            p.pol(-40f, -20f, -27f, -20f, -27f, -4f, -40f, -4f)
            p.pol(-23f, -20f, -10f, -20f, -10f, -4f, -23f, -4f)
            p.pol(-6f, -20f, 7f, -20f, 7f, -4f, -6f, -4f)
            p.pol(11f, -20f, 24f, -20f, 24f, -4f, 11f, -4f)
            p.pol(28f, -20f, 40f, -20f, 40f, -4f, 28f, -4f)
            p.arrondi(-34f, 24f, -16f, 38f, 6f)
            p.arrondi(16f, 24f, 34f, 38f, 6f)
        },
        "tram" to { p ->
            p.arrondi(-40f, -22f, 40f, 24f, 6f)
            p.pol(-34f, -14f, -20f, -14f, -20f, 0f, -34f, 0f)
            p.pol(-16f, -14f, -2f, -14f, -2f, 0f, -16f, 0f)
            p.pol(2f, -14f, 16f, -14f, 16f, 0f, 2f, 0f)
            p.pol(20f, -14f, 34f, -14f, 34f, 0f, 20f, 0f)
            p.pol(-18f, -42f, 18f, -42f, 18f, -38f, -18f, -38f)        // la perche
            p.pol(-3f, -38f, 3f, -38f, 3f, -22f, -3f, -22f)
            p.arrondi(-28f, 24f, -14f, 34f, 4f)
            p.arrondi(14f, 24f, 28f, 34f, 4f)
            p.pol(-46f, 38f, 46f, 38f, 46f, 44f, -46f, 44f)            // le rail
        },
        "train" to { p ->
            p.arrondi(-44f, -8f, 20f, 26f, 5f)                         // la chaudière
            p.pol(20f, -30f, 44f, -30f, 44f, 26f, 20f, 26f)            // la cabine
            p.pol(26f, -22f, 40f, -22f, 40f, -8f, 26f, -8f)
            p.pol(-38f, -28f, -24f, -28f, -24f, -8f, -38f, -8f)        // la cheminée
            p.arrondi(-40f, 26f, -26f, 38f, 5f)
            p.arrondi(-18f, 26f, -4f, 38f, 5f)
            p.arrondi(24f, 26f, 40f, 38f, 6f)
        },
        "camion" to { p ->
            p.pol(-46f, -26f, 8f, -26f, 8f, 22f, -46f, 22f)            // la caisse
            p.pol(8f, -6f, 30f, -6f, 38f, 6f, 38f, 22f, 8f, 22f)       // la cabine
            p.pol(13f, 0f, 28f, 0f, 33f, 7f, 13f, 7f)                  // le pare-brise
            p.arrondi(-38f, 22f, -24f, 34f, 5f)
            p.arrondi(-14f, 22f, 0f, 34f, 5f)
            p.arrondi(18f, 22f, 34f, 34f, 6f)
        },
        "ambulance" to { p ->
            p.pol(-44f, -24f, 10f, -24f, 10f, 20f, -44f, 20f)
            p.pol(10f, -6f, 32f, -6f, 40f, 6f, 40f, 20f, 10f, 20f)
            p.pol(15f, -1f, 30f, -1f, 34f, 6f, 15f, 6f)
            p.pol(-24f, -14f, -16f, -14f, -16f, -6f, -8f, -6f, -8f, 2f, -16f, 2f,
                  -16f, 10f, -24f, 10f, -24f, 2f, -32f, 2f, -32f, -6f, -24f, -6f)
            p.pol(-24f, -32f, -8f, -32f, -8f, -24f, -24f, -24f)        // le gyrophare
            p.arrondi(-36f, 20f, -22f, 32f, 5f)
            p.arrondi(20f, 20f, 34f, 32f, 5f)
        },
        "moto" to { p ->
            // Les roues ne montent pas au-dessus de y = 1 : tout le reste vit
            // au-dessus d'elles, sans jamais les recouvrir — un cadre posé sur
            // une roue y creuserait son propre trou.
            p.disque(-30f, 16f, 15f)
            p.disque(-30f, 16f, 6.5f)
            p.disque(30f, 16f, 15f)
            p.disque(30f, 16f, 6.5f)
            p.pol(-40f, 0f, -40f, -12f, -22f, -12f, -12f, -22f, 6f, -22f,
                  14f, -12f, 22f, -12f, 22f, 0f)                       // cadre, réservoir, selle
            p.pol(22f, -24f, 32f, -24f, 40f, 0f, 30f, 0f)              // la fourche
            p.pol(24f, -32f, 32f, -32f, 32f, -24f, 24f, -24f)
            p.pol(18f, -40f, 42f, -40f, 42f, -32f, 18f, -32f)          // le guidon
        },
        "rue" to { p ->
            p.pol(-44f, 44f, -14f, -40f, 14f, -40f, 44f, 44f)
            p.pol(-2f, -36f, 2f, -36f, 2f, -24f, -2f, -24f)            // la bande axiale
            p.pol(-2.5f, -14f, 2.5f, -14f, 2.5f, 0f, -2.5f, 0f)
            p.pol(-3f, 12f, 3f, 12f, 3f, 30f, -3f, 30f)
        },
        "panneau_danger" to { p ->
            p.pol(0f, -44f, 46f, 38f, -46f, 38f)
            p.pol(0f, -30f, 34f, 30f, -34f, 30f)                       // le fond, en réserve
            p.pol(-4f, -14f, 4f, -14f, 3f, 12f, -3f, 12f)              // le point
            p.disque(0f, 21f, 4f)                                      // d'exclamation
        },
        "pompe_essence" to { p ->
            p.arrondi(-30f, -34f, 10f, 40f, 5f)
            p.pol(-22f, -24f, 2f, -24f, 2f, -8f, -22f, -8f)            // l'afficheur
            p.pol(10f, -24f, 30f, -24f, 30f, -14f, 22f, -14f, 22f, 6f,
                  14f, 6f, 14f, -14f, 10f, -14f)                       // potence et pistolet
            p.pol(-36f, 40f, 16f, 40f, 16f, 46f, -36f, 46f)            // le socle
        },
        "valise" to { p ->
            p.arrondi(-40f, -16f, 40f, 40f, 6f)
            p.pol(-12f, -34f, 12f, -34f, 12f, -26f, -12f, -26f)        // la poignée
            p.pol(-12f, -26f, -6f, -26f, -6f, -16f, -12f, -16f)
            p.pol(6f, -26f, 12f, -26f, 12f, -16f, 6f, -16f)
            p.pol(-22f, -16f, -14f, -16f, -14f, 40f, -22f, 40f)        // les deux sangles
            p.pol(14f, -16f, 22f, -16f, 22f, 40f, 14f, 40f)
            p.pol(-6f, -12f, 6f, -12f, 6f, -4f, -6f, -4f)              // la serrure
        },
        // ---- ce qui se lit et ce qui se dit --------------------------------

        "livre" to { p ->
            p.moveTo(0f, -26f)
            p.cubicTo(-14f, -36f, -34f, -38f, -48f, -34f)
            p.lineTo(-48f, 30f)
            p.cubicTo(-34f, 26f, -14f, 28f, 0f, 38f)
            p.cubicTo(14f, 28f, 34f, 26f, 48f, 30f)
            p.lineTo(48f, -34f)
            p.cubicTo(34f, -38f, 14f, -36f, 0f, -26f)
            p.close()
            p.pol(-3f, -20f, 3f, -20f, 3f, 34f, -3f, 34f)
        },
        "lettre" to { p ->
            p.pol(-46f, -30f, 46f, -30f, 46f, 30f, -46f, 30f)
            p.pol(-38f, -22f, 38f, -22f, 0f, 8f)                       // le rabat
        },
        "carte" to { p ->
            p.arrondi(-46f, -30f, 46f, 30f, 6f)
            p.pol(-46f, -18f, 46f, -18f, 46f, -6f, -46f, -6f)          // la bande
            p.pol(-36f, 6f, -4f, 6f, -4f, 12f, -36f, 12f)
            p.pol(-36f, 18f, 12f, 18f, 12f, 24f, -36f, 24f)
        },
        "photo" to { p ->
            p.pol(-42f, -38f, 42f, -38f, 42f, 44f, -42f, 44f)          // le cadre
            p.pol(-34f, -30f, 34f, -30f, 34f, 20f, -34f, 20f)
            p.pol(-34f, 20f, -12f, -12f, 2f, 8f, 12f, -4f, 34f, 20f)   // le paysage
            p.disque(16f, -18f, 7f)
        },
        "ticket" to { p ->
            p.moveTo(-46f, -26f)
            p.lineTo(46f, -26f)
            p.lineTo(46f, -6f)
            p.cubicTo(38f, -6f, 38f, 6f, 46f, 6f)
            p.lineTo(46f, 26f)
            p.lineTo(-46f, 26f)
            p.lineTo(-46f, 6f)
            p.cubicTo(-38f, 6f, -38f, -6f, -46f, -6f)
            p.close()
            p.pol(-30f, -14f, 10f, -14f, 10f, -8f, -30f, -8f)
            p.pol(-30f, 4f, -4f, 4f, -4f, 10f, -30f, 10f)
        },
        "loupe" to { p ->
            p.disque(-8f, -14f, 32f)
            p.disque(-8f, -14f, 24f)
            p.pol(8f, 12f, 22f, 2f, 46f, 34f, 34f, 44f)
        },
        "lunettes" to { p ->
            p.disque(-24f, 4f, 22f)
            p.disque(-24f, 4f, 15f)
            p.disque(24f, 4f, 22f)
            p.disque(24f, 4f, 15f)
            p.pol(-6f, -2f, 6f, -2f, 6f, 4f, -6f, 4f)                  // le pont
            p.pol(-46f, -14f, -38f, -6f, -42f, -2f, -50f, -10f)        // les branches
            p.pol(46f, -14f, 38f, -6f, 42f, -2f, 50f, -10f)
        },
        "micro" to { p ->
            p.arrondi(-16f, -46f, 16f, 4f, 16f)
            p.moveTo(-28f, -6f)                                        // l'arceau
            p.cubicTo(-28f, 18f, 28f, 18f, 28f, -6f)
            p.lineTo(20f, -6f)
            p.cubicTo(20f, 8f, -20f, 8f, -20f, -6f)
            p.close()
            p.pol(-4f, 14f, 4f, 14f, 4f, 38f, -4f, 38f)
            p.pol(-20f, 38f, 20f, 38f, 20f, 46f, -20f, 46f)
        },
        "camera" to { p ->
            p.arrondi(-44f, -20f, 16f, 26f, 6f)
            p.pol(20f, -14f, 44f, -28f, 44f, 34f, 20f, 20f)            // l'objectif en saillie
            p.disque(-14f, 3f, 14f)
            p.disque(-14f, 3f, 7f)
            p.pol(-34f, -30f, -14f, -30f, -14f, -20f, -34f, -20f)      // la bobine
            p.pol(-30f, 26f, -18f, 26f, -18f, 38f, -30f, 38f)
        },
        "telephone" to { p ->
            p.arrondi(-26f, -46f, 26f, 46f, 8f)
            p.pol(-19f, -32f, 19f, -32f, 19f, 30f, -19f, 30f)          // l'écran
            p.pol(-7f, -42f, 7f, -42f, 7f, -37f, -7f, -37f)            // l'écouteur
            p.disque(0f, 38f, 5f)
        },
        "antenne" to { p ->
            p.pol(-6f, -20f, 6f, -20f, 12f, 46f, -12f, 46f)            // le pylône
            p.pol(-16f, 4f, 16f, 4f, 18f, 12f, -18f, 12f)
            p.disque(0f, -30f, 8f)
            p.moveTo(-20f, -46f)                                       // les ondes
            p.cubicTo(-32f, -34f, -32f, -18f, -20f, -6f)
            p.lineTo(-27f, -1f)
            p.cubicTo(-42f, -16f, -42f, -36f, -27f, -50f)
            p.close()
            p.moveTo(20f, -46f)
            p.cubicTo(32f, -34f, 32f, -18f, 20f, -6f)
            p.lineTo(27f, -1f)
            p.cubicTo(42f, -16f, 42f, -36f, 27f, -50f)
            p.close()
        },
        "drapeau" to { p ->
            p.pol(-38f, -48f, -30f, -48f, -30f, 48f, -38f, 48f)        // la hampe
            p.moveTo(-30f, -44f)
            p.cubicTo(-6f, -54f, 18f, -34f, 42f, -44f)
            p.lineTo(42f, 0f)
            p.cubicTo(18f, 10f, -6f, -10f, -30f, 0f)
            p.close()
        },
        "horloge" to { p ->
            p.disque(0f, 2f, 42f)
            p.disque(0f, 2f, 34f)
            p.pol(-4f, 2f, 4f, 2f, 4f, -24f, -4f, -24f)                // les aiguilles
            p.pol(-4f, -2f, -4f, 6f, 22f, 6f, 22f, -2f)
            p.disque(0f, 2f, 5f)
            p.pol(-10f, -48f, 10f, -48f, 10f, -40f, -10f, -40f)        // le remontoir
        },

        "journal" to { p ->
            p.pol(-44f, -34f, 44f, -34f, 44f, 36f, -44f, 36f)
            p.pol(-36f, -28f, 36f, -28f, 36f, -16f, -36f, -16f)        // le bandeau-titre
            for (i in 0..5) {
                val y = -8f + i * 8f
                p.pol(-36f, y, -4f, y, -4f, y + 4f, -36f, y + 4f)
            }
            p.pol(4f, -8f, 36f, -8f, 36f, 16f, 4f, 16f)                // la photo
            for (i in 0..1) {
                val y = 22f + i * 6f
                p.pol(4f, y, 36f, y, 36f, y + 3f, 4f, y + 3f)
            }
        },
        "document" to { p ->
            p.moveTo(-30f, -44f)
            p.lineTo(12f, -44f)
            p.lineTo(34f, -22f)
            p.lineTo(34f, 44f)
            p.lineTo(-30f, 44f)
            p.close()
            p.pol(12f, -44f, 34f, -22f, 12f, -22f)                     // le coin corné
            for (i in 0..4) {
                val y = -10f + i * 10f
                p.pol(-20f, y, 24f, y, 24f, y + 5f, -20f, y + 5f)
            }
        },
        "dossier" to { p ->
            p.pol(-44f, -26f, -6f, -26f, 2f, -16f, 44f, -16f, 44f, 36f, -44f, 36f)
            p.pol(-40f, -2f, 40f, -2f, 40f, 1f, -40f, 1f)              // le bord de la pochette
        },
        "film" to { p ->
            p.pol(-40f, -44f, 40f, -44f, 40f, 44f, -40f, 44f)
            for (i in 0..4) {
                val y = -38f + i * 15f
                p.pol(-34f, y, -24f, y, -24f, y + 9f, -34f, y + 9f)
                p.pol(24f, y, 34f, y, 34f, y + 9f, 24f, y + 9f)
            }
            p.pol(-18f, -20f, 18f, -20f, 18f, -16f, -18f, -16f)        // deux inter-images
            p.pol(-18f, 14f, 18f, 14f, 18f, 18f, -18f, 18f)
        },
        "ordinateur" to { p ->
            p.arrondi(-44f, -38f, 44f, 16f, 5f)
            p.pol(-37f, -31f, 37f, -31f, 37f, 9f, -37f, 9f)            // l'écran
            p.pol(-8f, 16f, 8f, 16f, 8f, 30f, -8f, 30f)                // le pied
            p.pol(-26f, 30f, 26f, 30f, 28f, 38f, -28f, 38f)
        },
        "note" to { p ->
            // Tête et hampe sont un seul contour : posées l'une sur l'autre,
            // elles se creuseraient à leur recouvrement.
            p.moveTo(4f, -42f)
            p.lineTo(11f, -42f)
            p.lineTo(11f, 20f)
            p.cubicTo(11f, 34f, -4f, 42f, -18f, 42f)
            p.cubicTo(-32f, 42f, -38f, 32f, -30f, 23f)
            p.cubicTo(-22f, 14f, -6f, 15f, 4f, 22f)
            p.close()
            p.pol(11f, -42f, 28f, -32f, 30f, -14f, 11f, -24f)          // le crochet
        },
        // ---- l'outil et le métal -------------------------------------------

        "marteau" to { p ->
            // Une tête franche et un manche droit. Toute tentative de panne
            // fendue se lisait comme un crochet, pas comme un marteau.
            p.pol(-34f, -44f, 34f, -44f, 34f, -16f, -34f, -16f)        // la tête
            p.pol(-9f, -16f, 9f, -16f, 6f, 46f, -6f, 46f)              // le manche
        },
        "or" to { p ->
            // Trois lingots empilés, chacun abouté au suivant : côte à côte
            // ils se recouvraient — et un recouvrement se creuse.
            p.pol(-20f, -30f, 18f, -30f, 24f, -8f, -26f, -8f)
            p.pol(-30f, -8f, 26f, -8f, 32f, 14f, -36f, 14f)
            p.pol(-40f, 14f, 36f, 14f, 42f, 36f, -46f, 36f)
        },
        "casque" to { p ->
            p.moveTo(-44f, 18f)
            p.cubicTo(-44f, -26f, -22f, -44f, 0f, -44f)
            p.cubicTo(22f, -44f, 44f, -26f, 44f, 18f)
            p.lineTo(30f, 18f)
            p.cubicTo(30f, -18f, 16f, -30f, 0f, -30f)
            p.cubicTo(-16f, -30f, -30f, -18f, -30f, 18f)
            p.close()
            p.pol(-48f, 18f, 48f, 18f, 48f, 30f, -48f, 30f)            // le bord
            p.pol(-4f, -44f, 4f, -44f, 4f, 16f, -4f, 16f)              // la crête
        },
        "jean" to { p ->
            p.moveTo(-30f, -42f)
            p.lineTo(30f, -42f)
            p.lineTo(34f, 46f)
            p.lineTo(8f, 46f)
            p.lineTo(0f, -6f)
            p.lineTo(-8f, 46f)
            p.lineTo(-34f, 46f)
            p.close()
            p.pol(-30f, -42f, 30f, -42f, 30f, -32f, -30f, -32f)        // la ceinture
            p.pol(-26f, -26f, -14f, -26f, -16f, -12f, -26f, -12f)      // la poche
        },
        "chaussure" to { p ->
            p.moveTo(-42f, 16f)
            p.lineTo(-42f, -18f)
            p.lineTo(-20f, -18f)
            p.lineTo(-14f, 0f)
            p.cubicTo(4f, 4f, 30f, 10f, 42f, 18f)
            p.lineTo(42f, 30f)
            p.lineTo(-42f, 30f)
            p.close()
            p.pol(-42f, 30f, 44f, 30f, 44f, 40f, -42f, 40f)            // la semelle
            p.pol(-36f, -12f, -22f, -12f, -20f, -4f, -36f, -4f)
        },
        "cigarette" to { p ->
            p.pol(-46f, 16f, 26f, 16f, 26f, 34f, -46f, 34f)            // le corps
            p.pol(26f, 16f, 46f, 16f, 46f, 34f, 26f, 34f)              // le filtre
            p.moveTo(-42f, 12f)                                        // la fumée, sur la braise
            p.cubicTo(-50f, -4f, -32f, -14f, -42f, -30f)
            p.lineTo(-32f, -34f)
            p.cubicTo(-22f, -16f, -40f, -6f, -32f, 8f)
            p.close()
        },
        "lampe" to { p ->
            p.pol(-30f, -20f, -18f, -42f, 18f, -42f, 30f, -20f)        // l'abat-jour
            p.pol(-4f, -20f, 4f, -20f, 4f, 30f, -4f, 30f)
            p.pol(-24f, 30f, 24f, 30f, 26f, 40f, -26f, 40f)
        },
        "regle" to { p ->
            p.pol(-46f, -14f, 46f, -14f, 46f, 14f, -46f, 14f)
            for (i in 0..8) {
                val x = -40f + i * 10f
                val h = if (i % 2 == 0) 2f else -6f
                p.pol(x - 1.5f, -14f, x + 1.5f, -14f, x + 1.5f, h, x - 1.5f, h)
            }
        },
        "balance" to { p ->
            p.pol(-38f, -34f, 38f, -34f, 38f, -28f, -38f, -28f)        // le fléau
            p.pol(-3f, -28f, 3f, -28f, 3f, 32f, -3f, 32f)
            p.pol(-33f, -28f, -29f, -28f, -29f, -14f, -33f, -14f)      // les deux suspentes
            p.pol(29f, -28f, 33f, -28f, 33f, -14f, 29f, -14f)
            p.pol(-46f, -14f, -20f, -14f, -24f, -4f, -42f, -4f)        // les deux plateaux
            p.pol(20f, -14f, 46f, -14f, 42f, -4f, 24f, -4f)
            p.pol(-24f, 32f, 24f, 32f, 28f, 40f, -28f, 40f)
        },
        "seringue" to { p ->
            p.pol(-20f, -8f, 22f, -8f, 22f, 10f, -20f, 10f)            // le corps
            p.pol(-24f, -14f, -20f, -14f, -20f, 16f, -24f, 16f)        // la collerette
            p.pol(-42f, -2f, -24f, -2f, -24f, 4f, -42f, 4f)            // la tige
            p.pol(-46f, -12f, -42f, -12f, -42f, 14f, -46f, 14f)        // le poussoir
            p.pol(22f, -2f, 46f, -2f, 46f, 2f, 22f, 2f)                // l'aiguille
            for (i in 0..3) {
                val x = -12f + i * 8f
                p.pol(x, -8f, x + 2f, -8f, x + 2f, -2f, x, -2f)
            }
        },
        "piece" to { p ->
            p.disque(0f, 0f, 42f)
            p.disque(0f, 0f, 35f)
            p.disque(0f, 0f, 28f)
            p.pol(0f, -16f, 4f, -5f, 15f, -5f, 6f, 3f, 10f, 14f, 0f, 7f,
                  -10f, 14f, -6f, 3f, -15f, -5f, -4f, -5f)             // l'étoile frappée
        },
        "restaurant" to { p ->
            p.pol(-34f, -44f, -14f, -44f, -14f, -6f, -34f, -6f)        // la fourchette
            p.pol(-30f, -44f, -27f, -44f, -27f, -20f, -30f, -20f)
            p.pol(-25.5f, -44f, -22.5f, -44f, -22.5f, -20f, -25.5f, -20f)
            p.pol(-21f, -44f, -18f, -44f, -18f, -20f, -21f, -20f)
            p.pol(-30f, -6f, -18f, -6f, -20f, 44f, -28f, 44f)
            p.pol(16f, -6f, 16f, -32f, 26f, -46f, 32f, -32f, 32f, -6f)  // le couteau
            p.pol(18f, -6f, 30f, -6f, 28f, 44f, 20f, 44f)
        },
        "robe" to { p ->
            p.pol(-13f, -40f, 13f, -40f, 22f, -26f, 14f, -20f, 34f, 42f,
                  -34f, 42f, -14f, -20f, -22f, -26f)
            p.pol(-15f, -2f, 15f, -2f, 15f, 4f, -15f, 4f)              // la ceinture
        },
        "tour_eiffel" to { p ->
            p.moveTo(-4f, -48f)
            p.lineTo(4f, -48f)
            p.lineTo(7f, -34f)
            p.lineTo(11f, -12f)
            p.lineTo(20f, 16f)
            p.lineTo(40f, 46f)
            p.lineTo(-40f, 46f)
            p.lineTo(-20f, 16f)
            p.lineTo(-11f, -12f)
            p.lineTo(-7f, -34f)
            p.close()
            p.moveTo(0f, 4f)                                           // l'arche, en réserve
            p.cubicTo(-14f, 12f, -22f, 28f, -26f, 46f)
            p.lineTo(26f, 46f)
            p.cubicTo(22f, 28f, 14f, 12f, 0f, 4f)
            p.close()
            // Les plateformes sont deux pièces par étage, aboutées au flanc du
            // fût : une seule barre en travers y aurait ouvert une fente.
            p.pol(-18f, -30f, -7.73f, -30f, -8.45f, -26f, -18f, -26f)
            p.pol(7.73f, -30f, 18f, -30f, 18f, -26f, 8.45f, -26f)
            p.pol(-32f, 14f, -19.36f, 14f, -22.67f, 20f, -32f, 20f)
            p.pol(19.36f, 14f, 32f, 14f, 32f, 20f, 22.67f, 20f)
        },
    )
}
