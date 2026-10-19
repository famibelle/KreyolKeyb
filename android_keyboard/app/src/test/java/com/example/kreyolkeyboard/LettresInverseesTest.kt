package com.example.kreyolkeyboard

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Deux lettres voisines inversées valent une faute, pas deux (v23.0.0, repris
 * de LuxKeyb 37.2.0).
 *
 * Levenshtein seul comptait l'inversion double, et n'importe quelle forme à
 * une lettre près passait devant. Sur le banc de correction, une inversion ne
 * donnait le bon mot en tête qu'une fois sur cinq (19,8 %), contre 79,7 %
 * désormais.
 */
class LettresInverseesTest {

    private val dico: List<Pair<String, Int>> by lazy {
        val t = JSONArray(File("src/main/assets/creole_dict.json").readText())
        (0 until t.length())
            .map { t.getJSONArray(it).let { p -> p.getString(0) to p.getInt(1) } }
            .sortedByDescending { it.second }
    }

    private val normalise = { s: String -> AccentTolerantMatcher.normalize(s) }
    private val normalises: List<String> by lazy { dico.map { normalise(it.first) } }

    /** Référence écrite à part, matrice complète, sans bande ni abandon. */
    private fun reference(a: String, b: String): Int {
        val x = a.lowercase(); val y = b.lowercase()
        val d = Array(x.length + 1) { i -> IntArray(y.length + 1) { j -> if (i == 0) j else if (j == 0) i else 0 } }
        for (i in 1..x.length) for (j in 1..y.length) {
            val cout = if (x[i - 1] == y[j - 1]) 0 else 1
            d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cout)
            if (i > 1 && j > 1 && x[i - 1] == y[j - 2] && x[i - 2] == y[j - 1]) {
                d[i][j] = minOf(d[i][j], d[i - 2][j - 2] + 1)
            }
        }
        return d[x.length][y.length]
    }

    @Test
    fun uneInversionCouteUn() {
        assertEquals(1, LevenshteinDistance.calculate("tojuou", "toujou"))
        assertEquals(1, LevenshteinDistance.calculate("znami", "zanmi"))
        assertEquals(1, LevenshteinDistance.calculate("fnam", "fanm"))
        // Inversion plus accent oublié : deux fautes, toujours à portée.
        assertEquals(2, LevenshteinDistance.calculate("pael", "palé"))
    }

    @Test
    fun lesDeuxCalculsSuiventLaReference() {
        val paires = listOf(
            "tojuou" to "toujou", "znami" to "zanmi", "abcd" to "badc", "ca" to "abc",
            "konnèt" to "knonèt", "kréyl" to "kréyòl", "aa" to "aaa",
            "mwen" to "mnwe", "lanmou" to "lnamou", "x" to "", "" to "y"
        )
        for ((a, b) in paires) {
            val attendu = reference(a, b)
            assertEquals("$a / $b", attendu, LevenshteinDistance.calculate(a, b))
            for (max in 0..3) {
                val entree = CharArray(a.length) { a[it].lowercaseChar() }
                val l = { IntArray(a.length + 1) }
                val obtenu = LevenshteinDistance.distanceBornee(entree, b, max, l(), l(), l())
                assertEquals("$a / $b au seuil $max", minOf(attendu, max + 1), minOf(obtenu, max + 1))
            }
        }
    }

    @Test
    fun laBonneFormeVientEnTete() {
        for ((saisie, attendu) in listOf("tojuou" to "toujou", "znami" to "zanmi", "fnam" to "fanm", "lnamou" to "lanmou")) {
            val trouves = LevenshteinDistance.findClosestMatchesNormalized(
                saisie, dico, normalises, normalise, 2, 5)
            assertEquals("'$saisie' : $trouves", attendu, trouves.first().first)
        }
    }
}
