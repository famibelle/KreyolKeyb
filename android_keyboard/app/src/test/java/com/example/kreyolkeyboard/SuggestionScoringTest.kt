package com.example.kreyolkeyboard

import org.junit.Test
import org.junit.Assert.*

/**
 * Tests du score de pertinence des suggestions, en particulier la propagation
 * de la distance de Levenshtein : une correction à 1 édition doit toujours
 * battre une correction à 2 éditions, quelle que soit la fréquence.
 */
class SuggestionScoringTest {

    @Test
    fun testCorrectionCloserDistanceBeatsHigherFrequency() {
        // Cas réel observé : "mesli" → "mèsi" (distance 1, fréquence modeste)
        // doit battre "mésyé" (distance 2, fréquence plus élevée)
        val scoreMesi = SuggestionEngine.calculateDictionaryScore("mèsi", "mesli", 650, 1)
        val scoreMesye = SuggestionEngine.calculateDictionaryScore("mésyé", "mesli", 15_000, 2)

        assertTrue(
            "mèsi (d=1, f=650) devrait battre mésyé (d=2, f=15000) : $scoreMesi vs $scoreMesye",
            scoreMesi > scoreMesye
        )
    }

    @Test
    fun testSameDistanceRankedByFrequency() {
        // À distance égale, la fréquence départage
        val scoreFrequent = SuggestionEngine.calculateDictionaryScore("mès", "mesli", 900, 2)
        val scoreRare = SuggestionEngine.calculateDictionaryScore("mélé", "mesli", 100, 2)

        assertTrue(scoreFrequent > scoreRare)
    }

    @Test
    fun testPrefixMatchesRankedByFrequency() {
        // Correspondances par préfixe (distance 0) : la fréquence domine
        val scoreKa = SuggestionEngine.calculateDictionaryScore("ka", "k", 15_519, 0)
        val scoreKijan = SuggestionEngine.calculateDictionaryScore("kijan", "k", 500, 0)

        assertTrue(scoreKa > scoreKijan)
    }

    @Test
    fun testAccentedWordGetsPrefixBonus() {
        // "fe" tapé : "fè" doit recevoir le bonus préfixe malgré l'accent,
        // et donc battre un mot sans accent nettement moins fréquent
        val scoreFe = SuggestionEngine.calculateDictionaryScore("fè", "fe", 3274, 0)
        val scoreFenmen = SuggestionEngine.calculateDictionaryScore("fenmen", "fe", 100, 0)

        assertTrue(scoreFe > scoreFenmen)
    }
}
