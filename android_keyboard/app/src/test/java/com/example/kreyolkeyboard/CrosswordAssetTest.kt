package com.example.kreyolkeyboard

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Contrôles des grilles de mots croisés livrées (« Mokwaré »).
 *
 * L'actif est produit hors du build par `Dictionnaires/generate_crossword.py`,
 * donc rien dans la compilation ne garantit sa forme. Les défauts d'une grille
 * ne se voient pas à l'œil sur un fichier JSON valide : deux mots parallèles
 * collés fabriquent une colonne que personne n'a écrite, un croisement
 * contradictoire rend une grille insoluble, une définition qui contient sa
 * réponse en fait un cadeau. Tout cela s'affiche normalement.
 *
 * Ce test rejoue donc côté Kotlin la validation que le script fait côté Python.
 * La redondance est voulue : c'est la seule barrière qui reste si quelqu'un
 * régénère l'actif avec un script modifié.
 */
class CrosswordAssetTest {

    private val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZÉÈÒ".toSet()

    private fun charger(): JSONObject {
        val fichier = File("src/main/assets/creole_crossword.json")
        assertTrue(
            "creole_crossword.json manquant, lancez " +
                "Dictionnaires/generate_crossword.py",
            fichier.exists()
        )
        return JSONObject(fichier.readText())
    }

    private fun grilles(): JSONArray = charger().getJSONArray("grilles")

    /** Les cases écrites d'une grille : (ligne, colonne) → lettre. */
    private fun cases(grille: JSONObject): Map<Pair<Int, Int>, Char> {
        val table = HashMap<Pair<Int, Int>, Char>()
        val mots = grille.getJSONArray("mots")
        for (i in 0 until mots.length()) {
            val mot = mots.getJSONObject(i)
            val texte = mot.getString("m")
            val horizontal = mot.getString("d") == "H"
            for (j in texte.indices) {
                val ligne = if (horizontal) mot.getInt("r") else mot.getInt("r") + j
                val colonne = if (horizontal) mot.getInt("c") + j else mot.getInt("c")
                val presente = table[ligne to colonne]
                assertTrue(
                    "croisement contradictoire en ($ligne,$colonne) : " +
                        "$presente puis ${texte[j]}",
                    presente == null || presente == texte[j]
                )
                table[ligne to colonne] = texte[j]
            }
        }
        return table
    }

    @Test
    fun `l'actif porte l'attribution de ses sources`() {
        val sources = charger().getJSONArray("attribution")
        val texte = (0 until sources.length()).joinToString(" ") { sources.getString(it) }
        // Les définitions viennent de Kreyolopedia et du Wiktionnaire (CC BY-SA,
        // dont la citation est une obligation de licence), les mots du corpus
        // PawolKreyol (Apache-2.0).
        assertTrue("Kreyolopedia non citée", texte.contains("Kreyolopedia"))
        assertTrue("Wiktionnaire non cité", texte.contains("Wiktionnaire"))
        assertTrue("PawolKreyol non cité", texte.contains("PawolKreyol"))
    }

    @Test
    fun `le volume livre couvre les trois difficultes`() {
        val grilles = grilles()
        assertTrue("moins de 90 grilles livrées : ${grilles.length()}", grilles.length() >= 90)

        val parNiveau = mutableMapOf<Int, Int>()
        for (i in 0 until grilles.length()) {
            val niveau = grilles.getJSONObject(i).getInt("l")
            parNiveau[niveau] = (parNiveau[niveau] ?: 0) + 1
        }
        for (niveau in 1..3) {
            assertTrue(
                "difficulté $niveau presque vide : ${parNiveau[niveau] ?: 0} grilles",
                (parNiveau[niveau] ?: 0) >= 25
            )
        }
    }

    @Test
    fun `chaque grille est jouable`() {
        val grilles = grilles()
        for (i in 0 until grilles.length()) {
            val grille = grilles.getJSONObject(i)
            val mots = grille.getJSONArray("mots")
            assertTrue("grille #$i : ${mots.length()} mots seulement", mots.length() >= 5)

            val vus = mutableSetOf<String>()
            for (j in 0 until mots.length()) {
                val mot = mots.getJSONObject(j)
                val reponse = mot.getString("m")
                val canonique = mot.getString("f")
                val definition = mot.getString("g")

                assertTrue("grille #$i : « $reponse » trop court", reponse.length >= 3)
                assertTrue(
                    "grille #$i : « $reponse » sort de l'alphabet du pavé",
                    reponse.all { it in alphabet }
                )
                // La grille est en capitales, la forme canonique est ce que le
                // jeu enseigne : les deux doivent désigner le même mot.
                assertEquals(
                    "grille #$i : « $reponse » ne majuscule pas « $canonique »",
                    canonique.uppercase(), reponse
                )
                assertTrue("grille #$i : « $reponse » sans définition", definition.isNotBlank())
                assertTrue(
                    "grille #$i : la définition de « $reponse » la contient, $definition",
                    !contientLeMot(definition, canonique)
                )
                assertTrue("grille #$i : « $reponse » posé deux fois", vus.add(reponse))

                val horizontal = mot.getString("d") == "H"
                val finLigne = mot.getInt("r") + if (horizontal) 0 else reponse.length - 1
                val finColonne = mot.getInt("c") + if (horizontal) reponse.length - 1 else 0
                assertTrue(
                    "grille #$i : « $reponse » déborde de la grille",
                    mot.getInt("r") >= 0 && mot.getInt("c") >= 0 &&
                        finLigne < grille.getInt("h") && finColonne < grille.getInt("w")
                )
            }
        }
    }

    /**
     * L'invariant central : rien ne se lit dans une grille qui ne soit un mot
     * posé.
     *
     * Deux mots parallèles collés produisent, dans l'autre sens, des paires de
     * lettres que le joueur lit comme des mots, et il les corrige, croyant
     * s'être trompé. Rien d'autre ne le signalerait : la grille est valide, les
     * définitions sont bonnes, les croisements concordent.
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
     * demi-grilles : les lettres trouvées dans l'un n'aident jamais dans
     * l'autre, ce qui est précisément ce qu'on attend de mots croisés.
     */
    @Test
    fun `chaque grille est d'un seul tenant`() {
        val grilles = grilles()
        for (i in 0 until grilles.length()) {
            val mots = grilles.getJSONObject(i).getJSONArray("mots")
            val emprises = (0 until mots.length()).map { j ->
                val mot = mots.getJSONObject(j)
                val horizontal = mot.getString("d") == "H"
                (mot.getString("m").indices).map { k ->
                    if (horizontal) mot.getInt("r") to mot.getInt("c") + k
                    else mot.getInt("r") + k to mot.getInt("c")
                }.toSet()
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
     * Les réponses sont des formes du dictionnaire livré.
     *
     * Proposer une graphie que le corpus n'écrit pas apprendrait une faute, et
     * c'est ici plus grave puisque le jeu affiche cette forme comme la bonne
     * orthographe.
     */
    @Test
    fun `les reponses sont des mots du dictionnaire livre`() {
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

    /** La requête apparaît-elle comme mot entier dans le texte, accents pliés ? */
    private fun contientLeMot(texte: String, mot: String): Boolean {
        val cible = AccentTolerantMatcher.normalize(mot)
        return AccentTolerantMatcher.normalize(texte)
            .split(Regex("[^\\p{L}]+"))
            .any { it == cible }
    }
}
