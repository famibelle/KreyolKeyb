package com.example.kreyolkeyboard

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Ce qui rend sûr le repli de correction allégé (v23.0.0, repris de LuxKeyb
 * 30.2.1).
 *
 * Trois changements, chacun avec sa preuve : la distance en bande sans
 * allocation donne les mêmes valeurs que le calcul complet, le second passage
 * sans normalisation ne trouve jamais rien que le premier aurait manqué, et
 * une recherche annulée s'arrête.
 */
class RepliAllegeTest {

    private val dico: List<Pair<String, Int>> by lazy {
        val t = JSONArray(File("src/main/assets/creole_dict.json").readText())
        (0 until t.length())
            .map { t.getJSONArray(it).let { p -> p.getString(0) to p.getInt(1) } }
            .sortedByDescending { it.second }
    }

    private val normalise = { s: String -> AccentTolerantMatcher.normalize(s) }
    private val normalises: List<String> by lazy { dico.map { normalise(it.first) } }

    /** Fautes, mots français, noms : ce qui déclenche le repli. */
    private val saisies = listOf(
        "bonjo", "mesli", "kreyol", "tojuou", "znami", "poun", "Gwadloup", "ordinateur",
        "maison", "KA", "Moun", "pawolseryé", "lnamou", "konènt", "bgaay", "dikonsa",
        "souplle", "kounen", "aprannkréyòl", "évèpli"
    )

    @Test
    fun laBandeDonneLaMemeDistanceQueLeCalculComplet() {
        val formes = dico.map { it.first }.filterIndexed { i, _ -> i % 7 == 0 }
        for (max in 0..3) {
            for (saisie in saisies) {
                val entree = CharArray(saisie.length) { saisie[it].lowercaseChar() }
                val l1 = IntArray(saisie.length + 1)
                val l2 = IntArray(saisie.length + 1)
                val l3 = IntArray(saisie.length + 1)
                for (forme in formes) {
                    val attendu = LevenshteinDistance.calculateBounded(saisie, forme, max)
                        .coerceAtMost(max + 1)
                    val obtenu = LevenshteinDistance.distanceBornee(entree, forme, max, l1, l2, l3)
                    assertEquals("'$saisie' / '$forme' au seuil $max", attendu, obtenu)
                }
            }
        }
    }

    @Test
    fun leSecondPassageNeTrouveRienQuandLePremierEchoue() {
        // Le raisonnement du moteur suppose qu'aucune forme ne s'allonge une
        // fois repliée (« ß », « œ ») ; sinon le moteur referait le second
        // passage de lui-même.
        assertTrue(dico.none { normalise(it.first).length != it.first.length })
        for (saisie in saisies) {
            val normalisee = LevenshteinDistance.findClosestMatchesNormalized(
                saisie, dico, normalises, normalise, 2, 5)
            if (normalisee.isNotEmpty()) continue
            val directe = LevenshteinDistance.findClosestMatches(saisie, dico, 2, 5)
            assertTrue("'$saisie' : le passage direct trouve $directe", directe.isEmpty())
        }
    }

    @Test
    fun uneRechercheAnnuleeSArreteSansRien() {
        val resultat = LevenshteinDistance.findClosestMatchesNormalized(
            "bonjo", dico, normalises, normalise, 2, 5, estAnnule = { true })
        assertTrue(resultat.isEmpty())
    }
}
