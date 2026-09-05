package com.example.kreyolkeyboard

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Contrôles du jeu de phrases à trous livré (« Fraz a twou »).
 *
 * L'actif est produit hors du build par `Dictionnaires/generate_cloze.py`, donc
 * rien dans la compilation ne garantit sa forme. Une phrase sans marqueur, une
 * réponse absente des propositions ou un leurre égal à la réponse ne casse rien
 * visiblement : le jeu se lance et pose une question sans solution.
 *
 * Contrairement aux tests d'assets voisins, celui-ci **échoue** quand le fichier
 * manque au lieu de se taire. Le fichier est versionné : son absence est une
 * régression, pas une configuration locale.
 */
class ClozeAssetTest {

    private val marqueur = "___"

    private fun charger(): JSONObject {
        val fichier = File("src/main/assets/creole_cloze.json")
        assertTrue(
            "creole_cloze.json manquant — lancez Dictionnaires/generate_cloze.py",
            fichier.exists()
        )
        return JSONObject(fichier.readText())
    }

    @Test
    fun `l'actif porte l'attribution du corpus`() {
        val sources = charger().getJSONArray("sources")
        assertTrue("aucune source citée", sources.length() >= 1)
        val texte = (0 until sources.length()).joinToString(" ") { sources.getString(it) }
        // Les phrases livrées sont des extraits directs d'œuvres d'auteurs
        // identifiés : la citation du corpus qui les rassemble voyage avec le
        // fichier, et celle de l'auteur est portée par le champ « src ».
        assertTrue("corpus non cité", texte.contains("PawolKreyol"))
    }

    @Test
    fun `le volume livre couvre les trois difficultes`() {
        val items = charger().getJSONArray("items")
        // Le corpus kréyòl compte 36 500 occurrences : la livraison se mesure
        // en centaines de questions, pas en milliers.
        assertTrue("moins de 300 questions livrées : ${items.length()}", items.length() >= 300)

        val parNiveau = mutableMapOf<Int, Int>()
        for (i in 0 until items.length()) {
            val niveau = items.getJSONObject(i).getInt("l")
            parNiveau[niveau] = (parNiveau[niveau] ?: 0) + 1
        }
        for (niveau in 1..3) {
            // Une manche fait dix questions : en dessous de soixante, une
            // difficulté se répète au bout de six parties.
            assertTrue(
                "difficulté $niveau presque vide : ${parNiveau[niveau] ?: 0} questions",
                (parNiveau[niveau] ?: 0) >= 60
            )
        }
    }

    @Test
    fun `chaque question est jouable`() {
        val items = charger().getJSONArray("items")

        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            val phrase = item.getString("s")
            val reponse = item.getString("a")
            val leurres = item.getJSONArray("d")

            assertEquals(
                "question #$i : le marqueur doit apparaître une fois exactement",
                1,
                phrase.split(marqueur).size - 1
            )
            assertTrue("question #$i : réponse vide", reponse.isNotBlank())
            assertEquals("question #$i : il faut trois leurres", 3, leurres.length())

            val propositions = mutableListOf(reponse)
            for (j in 0 until leurres.length()) {
                propositions.add(leurres.getString(j))
            }
            assertEquals(
                "question #$i : propositions en doublon — $propositions",
                4,
                propositions.toSet().size
            )

            // Un mot déjà présent dans la phrase se disqualifie tout seul : le
            // joueur voit qu'il est employé ailleurs. Vaut pour les leurres,
            // mais aussi pour la réponse — le trou serait déjà comblé sous ses
            // yeux.
            val motsDeLaPhrase = phrase.split(Regex("[^\\p{L}'-]+"))
                .map { it.lowercase() }
                .toSet()
            for (proposition in propositions) {
                assertTrue(
                    "question #$i : « $proposition » figure déjà dans la phrase",
                    !motsDeLaPhrase.contains(proposition.lowercase())
                )
            }

            assertTrue("question #$i : niveau hors bornes", item.getInt("l") in 1..3)
            assertTrue("question #$i : source absente", item.getString("src").isNotBlank())
        }
    }

    @Test
    fun `les reponses sont des mots du dictionnaire livre`() {
        val dictionnaire = File("src/main/assets/creole_dict.json")
        assertTrue("creole_dict.json manquant", dictionnaire.exists())
        val formes = org.json.JSONArray(dictionnaire.readText()).let { tableau ->
            (0 until tableau.length())
                .map { tableau.getJSONArray(it).getString(0) }
                .toSet()
        }

        val items = charger().getJSONArray("items")
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            assertTrue(
                "question #$i : réponse « ${item.getString("a")} » hors dictionnaire",
                formes.contains(item.getString("a"))
            )
            val leurres = item.getJSONArray("d")
            for (j in 0 until leurres.length()) {
                assertTrue(
                    "question #$i : leurre « ${leurres.getString(j)} » hors dictionnaire",
                    formes.contains(leurres.getString(j))
                )
            }
        }
    }

    @Test
    fun `aucune phrase n'est proposee deux fois`() {
        val items = charger().getJSONArray("items")
        val phrases = (0 until items.length()).map { items.getJSONObject(it).getString("s") }
        assertEquals(
            "des phrases sont livrées en double",
            phrases.size,
            phrases.toSet().size
        )
    }
}
