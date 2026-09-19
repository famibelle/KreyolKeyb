package com.example.kreyolkeyboard

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Contrôles des grilles de chassé-croisé livrées (« Mo an plas »).
 *
 * L'actif est produit hors du build par
 * `Dictionnaires/generate_chassecroise.py`, donc rien dans la compilation ne
 * garantit sa forme. Ce test rejoue côté Kotlin la validation que le script
 * fait côté Python : la redondance est voulue, c'est la seule barrière qui
 * reste si quelqu'un régénère l'actif avec un script modifié.
 *
 * Le contrôle qui compte le plus est [chaque grille n'a qu'une solution]. Une
 * grille à deux solutions s'affiche normalement, se remplit normalement, et
 * refuse une réponse juste — le joueur en conclut qu'il s'est trompé, ou que
 * le jeu est cassé. Rien d'autre ne le signalerait.
 */
class ChasseCroiseAssetTest {

    private val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZÉÈÒ".toSet()

    private fun charger(): JSONObject {
        val fichier = File("src/main/assets/creole_chassecroise.json")
        assertTrue(
            "creole_chassecroise.json manquant — lancez " +
                "Dictionnaires/generate_chassecroise.py",
            fichier.exists()
        )
        return JSONObject(fichier.readText())
    }

    private fun grilles(): JSONArray = charger().getJSONArray("grilles")

    /** Les cases d'un mot, dans l'ordre de ses lettres. */
    private fun emprise(mot: JSONObject): List<Pair<Int, Int>> {
        val horizontal = mot.getString("d") == "H"
        return mot.getString("m").indices.map { k ->
            if (horizontal) mot.getInt("r") to mot.getInt("c") + k
            else mot.getInt("r") + k to mot.getInt("c")
        }
    }

    /** Les cases écrites d'une grille : (ligne, colonne) → lettre. */
    private fun cases(grille: JSONObject): Map<Pair<Int, Int>, Char> {
        val table = HashMap<Pair<Int, Int>, Char>()
        val mots = grille.getJSONArray("mots")
        for (i in 0 until mots.length()) {
            val mot = mots.getJSONObject(i)
            val texte = mot.getString("m")
            emprise(mot).forEachIndexed { j, case ->
                val presente = table[case]
                assertTrue(
                    "croisement contradictoire en $case : $presente puis ${texte[j]}",
                    presente == null || presente == texte[j]
                )
                table[case] = texte[j]
            }
        }
        return table
    }

    @Test
    fun `l'actif porte l'attribution de ses sources`() {
        val sources = charger().getJSONArray("attribution")
        val texte = (0 until sources.length()).joinToString(" ") { sources.getString(it) }
        // Les gloses viennent de Kreyolopedia et du Wiktionnaire (CC BY-SA,
        // dont la citation est une obligation de licence), les mots du corpus
        // PawolKreyol (Apache-2.0).
        assertTrue("Kreyolopedia non citée", texte.contains("Kreyolopedia"))
        assertTrue("Wiktionnaire non cité", texte.contains("Wiktionnaire"))
        assertTrue("PawolKreyol non cité", texte.contains("PawolKreyol"))
    }

    @Test
    fun `le volume livre couvre les trois difficultes`() {
        val grilles = grilles()
        assertTrue(
            "moins de 150 grilles livrées : ${grilles.length()}",
            grilles.length() >= 150
        )

        val parNiveau = mutableMapOf<Int, Int>()
        for (i in 0 until grilles.length()) {
            val niveau = grilles.getJSONObject(i).getInt("l")
            parNiveau[niveau] = (parNiveau[niveau] ?: 0) + 1
        }
        for (niveau in 1..3) {
            assertTrue(
                "difficulté $niveau presque vide : ${parNiveau[niveau] ?: 0} grilles",
                (parNiveau[niveau] ?: 0) >= 50
            )
        }
    }

    @Test
    fun `chaque grille est jouable`() {
        val grilles = grilles()
        for (i in 0 until grilles.length()) {
            val grille = grilles.getJSONObject(i)
            val mots = grille.getJSONArray("mots")
            assertTrue("grille #$i : ${mots.length()} mots seulement", mots.length() >= 6)

            val vus = mutableSetOf<String>()
            for (j in 0 until mots.length()) {
                val mot = mots.getJSONObject(j)
                val reponse = mot.getString("m")
                val canonique = mot.getString("f")

                assertTrue("grille #$i : « $reponse » trop court", reponse.length >= 3)
                assertTrue(
                    "grille #$i : « $reponse » sort de l'alphabet de la grille",
                    reponse.all { it in alphabet }
                )
                assertEquals(
                    "grille #$i : « $reponse » ne majuscule pas « $canonique »",
                    canonique.uppercase(), reponse
                )
                // La glose est la récompense de ce jeu : un mot muet romprait
                // la promesse pour toute la grille. Rien n'exige en revanche
                // qu'elle ne contienne pas le mot — elle n'est montrée qu'une
                // fois le mot posé, où « Budget » → budget est une
                // confirmation et non un cadeau.
                assertTrue(
                    "grille #$i : « $reponse » sans récompense",
                    mot.getString("g").isNotBlank()
                )
                assertTrue("grille #$i : « $reponse » posé deux fois", vus.add(reponse))

                emprise(mot).forEach { (r, c) ->
                    assertTrue(
                        "grille #$i : « $reponse » déborde de la grille",
                        r >= 0 && c >= 0 && r < grille.getInt("h") && c < grille.getInt("w")
                    )
                }
            }
        }
    }

    /**
     * L'invariant propre à ce jeu : une grille n'a qu'une solution.
     *
     * Un chassé-croisé ne donne pas de définitions. La seule chose qui désigne
     * l'emplacement d'un mot est sa longueur et les lettres que ses croisements
     * lui imposent. Si deux mots peuvent s'échanger sans contredire un
     * croisement, la grille a deux solutions — et comme le jeu n'en connaît
     * qu'une, il refuse une réponse juste.
     *
     * L'énumération est un retour en arrière sur une bijection mots ↔
     * emplacements, identique à `solution_unique()` du script Python. Elle
     * tient parce qu'une grille compte au plus treize mots.
     */
    @Test
    fun `chaque grille n'a qu'une solution`() {
        val grilles = grilles()
        for (i in 0 until grilles.length()) {
            assertEquals(
                "grille #$i : la grille n'a pas exactement une solution",
                1, nombreDeSolutions(grilles.getJSONObject(i))
            )
        }
    }

    /** Compte les solutions, en s'arrêtant à deux. */
    private fun nombreDeSolutions(grille: JSONObject): Int {
        val mots = grille.getJSONArray("mots")
        val n = mots.length()
        val lettres = (0 until n).map { mots.getJSONObject(it).getString("m") }
        val emprises = (0 until n).map { emprise(mots.getJSONObject(it)) }

        // Croisements : pour chaque emplacement, (rang ici, autre emplacement,
        // rang là-bas).
        val occupants = HashMap<Pair<Int, Int>, MutableList<Pair<Int, Int>>>()
        emprises.forEachIndexed { i, suite ->
            suite.forEachIndexed { rang, case ->
                occupants.getOrPut(case) { mutableListOf() }.add(i to rang)
            }
        }
        val croisements = HashMap<Int, MutableList<Triple<Int, Int, Int>>>()
        occupants.values.filter { it.size == 2 }.forEach { (premier, second) ->
            val (a, i) = premier
            val (b, j) = second
            croisements.getOrPut(a) { mutableListOf() }.add(Triple(i, b, j))
            croisements.getOrPut(b) { mutableListOf() }.add(Triple(j, a, i))
        }

        val parLongueur = lettres.groupingBy { it.length }.eachCount()
        val ordre = (0 until n).sortedWith(
            compareBy(
                { parLongueur[lettres[it].length] ?: 0 },
                { -(croisements[it]?.size ?: 0) }
            )
        )

        val affecte = arrayOfNulls<Int>(n)
        val pris = BooleanArray(n)
        var total = 0

        fun poser(rang: Int) {
            if (total >= 2) return
            if (rang == n) {
                total++
                return
            }
            val emplacement = ordre[rang]
            val attendue = lettres[emplacement].length
            for (candidat in 0 until n) {
                if (pris[candidat] || lettres[candidat].length != attendue) continue
                val compatible = croisements[emplacement].orEmpty().none { (i, autre, j) ->
                    affecte[autre]?.let { lettres[it][j] != lettres[candidat][i] } ?: false
                }
                if (!compatible) continue
                affecte[emplacement] = candidat
                pris[candidat] = true
                poser(rang + 1)
                affecte[emplacement] = null
                pris[candidat] = false
                if (total >= 2) return
            }
        }

        poser(0)
        return total
    }

    /**
     * L'invariant hérité de Mokwaré : rien ne se lit dans une grille qui ne
     * soit un mot posé.
     *
     * Il compte double ici. Le joueur d'un chassé-croisé lit la grille pour y
     * chercher les mots de sa liste ; une suite de lettres que personne n'a
     * posée le fait chercher un mot qui n'existe pas.
     */
    @Test
    fun `toute suite de deux lettres est un mot pose`() {
        val grilles = grilles()
        for (i in 0 until grilles.length()) {
            val grille = grilles.getJSONObject(i)
            val table = cases(grille)
            val mots = grille.getJSONArray("mots")
            val poses = (0 until mots.length())
                .map { mots.getJSONObject(it).getString("m") }
                .groupingBy { it }.eachCount()

            val lues = ArrayList<String>()
            for (horizontal in listOf(true, false)) {
                val externes = if (horizontal) grille.getInt("h") else grille.getInt("w")
                val internes = if (horizontal) grille.getInt("w") else grille.getInt("h")
                for (a in 0 until externes) {
                    val courant = StringBuilder()
                    for (b in 0..internes) {
                        val lettre = if (b == internes) null
                        else table[if (horizontal) a to b else b to a]
                        if (lettre != null) {
                            courant.append(lettre)
                        } else {
                            if (courant.length >= 2) lues.add(courant.toString())
                            courant.setLength(0)
                        }
                    }
                }
            }

            assertEquals(
                "grille #$i : ce qui se lit dans la grille ne correspond pas aux " +
                    "mots posés",
                poses, lues.groupingBy { it }.eachCount()
            )
        }
    }

    /**
     * Une grille doit être d'un seul tenant. Deux blocs séparés sont deux
     * demi-grilles : les mots posés dans l'un n'aident jamais à déduire ceux de
     * l'autre, alors que c'est toute la mécanique du jeu.
     */
    @Test
    fun `chaque grille est d'un seul tenant`() {
        val grilles = grilles()
        for (i in 0 until grilles.length()) {
            val mots = grilles.getJSONObject(i).getJSONArray("mots")
            val emprises = (0 until mots.length()).map {
                emprise(mots.getJSONObject(it)).toSet()
            }

            val atteints = mutableSetOf(0)
            val frontiere = ArrayDeque(listOf(0))
            while (frontiere.isNotEmpty()) {
                val courant = frontiere.removeLast()
                emprises.indices.forEach { autre ->
                    if (autre !in atteints && emprises[courant].any { it in emprises[autre] }) {
                        atteints.add(autre)
                        frontiere.addLast(autre)
                    }
                }
            }
            assertEquals(
                "grille #$i : la grille est en plusieurs morceaux",
                emprises.size, atteints.size
            )
        }
    }

    /**
     * Chaque mot croise au moins un autre.
     *
     * Un mot sans croisement ne se déduit pas : il ne reste qu'à essayer les
     * mots de la bonne longueur au hasard, et rien ne dira lequel est le bon
     * avant la fin. La grille d'un seul tenant l'interdit déjà pour la plupart
     * des cas, mais pas pour celui d'un mot qui ne toucherait la grille que par
     * une case partagée avec un mot de même sens — que `_placement_valide`
     * refuse, et qu'on vérifie ici sur l'actif.
     */
    @Test
    fun `chaque mot croise un autre mot`() {
        val grilles = grilles()
        for (i in 0 until grilles.length()) {
            val mots = grilles.getJSONObject(i).getJSONArray("mots")
            val emprises = (0 until mots.length()).map {
                emprise(mots.getJSONObject(it)).toSet()
            }
            emprises.indices.forEach { j ->
                val croise = emprises.indices.any { autre ->
                    autre != j && emprises[j].any { it in emprises[autre] }
                }
                assertTrue(
                    "grille #$i : « ${mots.getJSONObject(j).getString("m")} » " +
                        "ne croise rien",
                    croise
                )
            }
        }
    }

    /**
     * Les mots sont des formes du dictionnaire livré, accents compris.
     *
     * Même exigence que pour Mokwaré : afficher « kreyol » là où le corpus
     * écrit « kréyòl » apprendrait une faute, et le jeu montre justement cette
     * forme en récompense. La casse, elle, ne dit rien ici — le dictionnaire
     * créole est tout en minuscules.
     */
    @Test
    fun `les mots sont des formes du dictionnaire livre`() {
        val dictionnaire = File("src/main/assets/creole_dict.json")
        assertTrue("creole_dict.json manquant", dictionnaire.exists())
        val formes = JSONArray(dictionnaire.readText()).let { tableau ->
            (0 until tableau.length())
                .map { tableau.getJSONArray(it).getString(0) }
                .toSet()
        }

        val grilles = grilles()
        for (i in 0 until grilles.length()) {
            val mots = grilles.getJSONObject(i).getJSONArray("mots")
            for (j in 0 until mots.length()) {
                val canonique = mots.getJSONObject(j).getString("f")
                assertTrue(
                    "grille #$i : « $canonique » hors dictionnaire",
                    formes.contains(canonique)
                )
            }
        }
    }

    /**
     * Aucun nom propre dans les grilles.
     *
     * Un chassé-croisé de communes n'apprend rien : le mot est donné, et la
     * récompense « Bastè : Basse-Terre » ne fait que retraduire une carte.
     *
     * Le fork luxembourgeois les repère à la capitalisation de la glose, parce
     * que le LOD marque ses articles `NP` et écrit ses gloses en français. Ni
     * Kreyolopedia ni le Wiktionnaire ne donnent cette prise, donc la barrière
     * créole est la liste `NOMS_PROPRES` de `generate_crossword.py`, tenue à la
     * main. Ce test la rejoue sur l'actif, parce qu'un vivier reconstruit sans
     * elle repasserait sans rien casser.
     */
    @Test
    fun `aucun mot n'est un nom propre`() {
        val nomsPropres = setOf(
            "gwadloup", "matnik", "gwiyàn", "ayiti", "lafrans", "pari",
            "bastè", "pwentapit", "kayenn", "viktò", "sentàn"
        )
        val grilles = grilles()
        val fautifs = mutableSetOf<String>()
        for (i in 0 until grilles.length()) {
            val mots = grilles.getJSONObject(i).getJSONArray("mots")
            for (j in 0 until mots.length()) {
                val canonique = mots.getJSONObject(j).getString("f")
                if (canonique.lowercase() in nomsPropres) fautifs.add(canonique)
            }
        }
        assertTrue(
            "noms propres casés : " + fautifs.sorted().joinToString(" · "),
            fautifs.isEmpty()
        )
    }

    /**
     * La difficulté est bien portée par la géométrie.
     *
     * Les mots étant affichés, leur rareté ne fait plus la difficulté : ce qui
     * la fait est le nombre de mots partageant une même longueur, puisqu'une
     * longueur unique désigne son emplacement toute seule. Si ce taux
     * s'effondrait, les trois niveaux se vaudraient sans que rien n'échoue.
     */
    @Test
    fun `la difficulte croit avec les longueurs partagees`() {
        val grilles = grilles()
        val taux = mutableMapOf<Int, MutableList<Double>>()
        for (i in 0 until grilles.length()) {
            val grille = grilles.getJSONObject(i)
            val mots = grille.getJSONArray("mots")
            val longueurs = (0 until mots.length())
                .map { mots.getJSONObject(it).getString("m").length }
            val partages = longueurs.count { longueur ->
                longueurs.count { it == longueur } > 1
            }
            taux.getOrPut(grille.getInt("l")) { mutableListOf() }
                .add(partages.toDouble() / longueurs.size)
        }

        val moyennes = (1..3).map { niveau ->
            taux[niveau]!!.average()
        }
        assertTrue(
            "Facile n'est pas le plus simple : ${moyennes[0]} contre ${moyennes[1]}",
            moyennes[0] < moyennes[1]
        )
        assertTrue(
            "Difficile n'est pas le plus dur : ${moyennes[2]} contre ${moyennes[1]}",
            moyennes[2] > moyennes[1]
        )
        assertTrue(
            "Difficile ne croise presque aucune longueur : ${moyennes[2]}",
            moyennes[2] >= 0.6
        )
    }
}
