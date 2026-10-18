package com.example.kreyolkeyboard

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Le doigt tombé sur la touche d'à côté passe devant la fréquence (v23.0.0,
 * repris de LuxKeyb 37.2.0).
 *
 * Mesuré sur le dictionnaire livré : « poun » proposait « pou » (727) avant
 * « moun » (267), alors que le p est juste au-dessus du m et que le n final
 * en plus fait de « pou » une autre faute.
 */
class VoisinsClavierTest {

    private val v = VoisinsClavier.AZERTY

    private val dico: List<Pair<String, Int>> by lazy {
        val t = JSONArray(File("src/main/assets/creole_dict.json").readText())
        (0 until t.length())
            .map { t.getJSONArray(it).let { p -> p.getString(0) to p.getInt(1) } }
            .sortedByDescending { it.second }
    }

    @Test
    fun lesVoisinesSontCellesDeLEcran() {
        assertTrue(v.sontVoisines('d', 's'))
        assertTrue(v.sontVoisines('d', 'f'))
        assertTrue(v.sontVoisines('d', 'r'))      // rangée du dessus
        assertTrue(v.sontVoisines('p', 'm'))      // AZERTY : le m sous le p
        assertTrue(v.sontVoisines('a', 'q'))
        assertTrue(v.sontVoisines('s', 'd'))      // symétrique
        assertFalse(v.sontVoisines('d', 'y'))
        assertFalse(v.sontVoisines('a', 'l'))
        assertFalse(v.sontVoisines('a', 'p'))
    }

    @Test
    fun uneCorrectionParVoisinesSeReconnait() {
        assertTrue(v.parVoisines("poun", "moun"))
        assertFalse(v.parVoisines("poun", "pou"))       // longueurs différentes
        assertFalse(v.parVoisines("moun", "moun"))      // rien à corriger
        assertTrue(v.parVoisines("rout", "tout"))       // r et t se touchent
        assertFalse(v.parVoisines("bout", "tout"))
        // Un accent seul ne compte ni pour ni contre.
        assertTrue(v.parVoisines("dio", "dlò"))
    }

    @Test
    fun leBonusRespecteLaDistance() {
        val max = dico.first().second
        // Deux fautes voisines sur le mot le plus fréquent, contre une faute
        // quelconque du mot le plus rare : la distance garde le dernier mot.
        val deuxVoisines = SuggestionEngine.calculateDictionaryScore("ka", "xx", max, 2, 20, parVoisines = true)
        val uneFaute = SuggestionEngine.calculateDictionaryScore("rare", "xxxx", 1, 1)
        assertTrue("$uneFaute vs $deuxVoisines", uneFaute > deuxVoisines)
        assertTrue(max.toDouble() * SuggestionEngine.VOISINAGE_FACTEUR < SuggestionEngine.EDIT_DISTANCE_WEIGHT)
    }

    @Test
    fun laToucheVoisinePasseDevant() {
        val normalise = { s: String -> AccentTolerantMatcher.normalize(s) }
        val normalises = dico.map { normalise(it.first) }
        fun trouves(saisie: String) = LevenshteinDistance.findClosestMatchesNormalized(
            saisie, dico, normalises, normalise, 2, 5,
            multiplicateur = { if (v.parVoisines(saisie, it)) SuggestionEngine.VOISINAGE_FACTEUR else 1 })
        assertEquals("moun", trouves("poun").first().first)
        assertEquals("dlo", trouves("dio").first().first)
        assertEquals("pran", trouves("pdan").first().first)
    }
}
