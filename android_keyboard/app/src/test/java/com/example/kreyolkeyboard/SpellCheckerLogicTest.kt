package com.example.kreyolkeyboard

import org.junit.Test
import org.junit.Assert.*

/**
 * Tests de la logique de reconnaissance de mots utilisée par KreyolSpellCheckerService
 * (via SuggestionEngine.isKnownWord() / isWordKnown()), sans dépendre du Context Android
 * — mêmes échantillons de dictionnaire normalisé que ceux réellement précalculés au
 * chargement (AccentTolerantMatcher.normalize appliqué à chaque mot).
 */
class SpellCheckerLogicTest {

    // Échantillon de formes normalisées (minuscules, sans accents), tel que produit par
    // `dictionary.map { AccentTolerantMatcher.normalize(it.first) }` au chargement réel
    private val normalizedSample = listOf(
        "ka", "an", "se", "on", "te", "yo", "pou", "nou", "pa", "ki",
        "mwen", "ou", "sa", "fe", "la", "moun", "tout",
        "bonjou", "bonswa", "mesi", "souple", "lanmou", "zanmi",
        "kreyol", "manman", "papa", "timoun", "lakay", "kabrit"
    )

    @Test
    fun testKnownWord_exactMatch() {
        assertTrue(SuggestionEngine.isWordKnown("bonjou", normalizedSample))
        assertTrue(SuggestionEngine.isWordKnown("kabrit", normalizedSample))
    }

    @Test
    fun testKnownWord_shortFrequentWords() {
        // Le kréyòl a des mots très courts et fréquents : ka, an, sé...
        listOf("ka", "an", "ou", "pa", "ki", "sa", "la", "yo").forEach { word ->
            assertTrue("'$word' devrait être reconnu", SuggestionEngine.isWordKnown(word, normalizedSample))
        }
    }

    @Test
    fun testKnownWord_accentInsensitive() {
        // "mèsi" et "sé" existent avec accent dans le vrai dictionnaire mais la forme
        // normalisée stockée est sans accent : le mot tapé avec accent doit matcher
        assertTrue(SuggestionEngine.isWordKnown("mèsi", normalizedSample))
        assertTrue(SuggestionEngine.isWordKnown("sé", normalizedSample))
        assertTrue(SuggestionEngine.isWordKnown("fè", normalizedSample))
        assertTrue(SuggestionEngine.isWordKnown("kréyòl", normalizedSample))
    }

    @Test
    fun testKnownWord_caseInsensitive() {
        assertTrue(SuggestionEngine.isWordKnown("BONJOU", normalizedSample))
        assertTrue(SuggestionEngine.isWordKnown("Bonjou", normalizedSample))
        assertTrue(SuggestionEngine.isWordKnown("Zanmi", normalizedSample))
    }

    @Test
    fun testUnknownWord_gibberish() {
        assertFalse(SuggestionEngine.isWordKnown("xyzwqk", normalizedSample))
        assertFalse(SuggestionEngine.isWordKnown("zzzzz", normalizedSample))
    }

    @Test
    fun testUnknownWord_typo() {
        // Une vraie faute ("bonjuo" au lieu de "bonjou") ne doit PAS être reconnue comme
        // mot connu — c'est ce qui doit déclencher le soulignement + les suggestions
        assertFalse(SuggestionEngine.isWordKnown("bonjuo", normalizedSample))
        assertFalse(SuggestionEngine.isWordKnown("zamni", normalizedSample))
    }

    @Test
    fun testBlankInput_neverFlagged() {
        // Ponctuation/espaces isolés : ne jamais souligner comme faute
        assertTrue(SuggestionEngine.isWordKnown("", normalizedSample))
        assertTrue(SuggestionEngine.isWordKnown("   ", normalizedSample))
    }

    @Test
    fun testEmptyDictionary_noFalsePositive() {
        assertFalse(SuggestionEngine.isWordKnown("bonjou", emptyList()))
    }
}
