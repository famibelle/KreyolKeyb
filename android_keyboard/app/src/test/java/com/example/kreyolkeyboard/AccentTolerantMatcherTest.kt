package com.example.kreyolkeyboard

import org.junit.Test
import org.junit.Assert.*

/**
 * Tests unitaires de la recherche insensible aux accents.
 * Remplace les anciens tests qui s'exécutaient en production au démarrage du clavier.
 */
class AccentTolerantMatcherTest {

    @Test
    fun testNormalizeRemovesCreoleAccents() {
        assertEquals("kreyol", AccentTolerantMatcher.normalize("kréyòl"))
        assertEquals("fe", AccentTolerantMatcher.normalize("fè"))
        assertEquals("te", AccentTolerantMatcher.normalize("té"))
        assertEquals("bon", AccentTolerantMatcher.normalize("bòn"))
        assertEquals("ou", AccentTolerantMatcher.normalize("où"))
        assertEquals("epi", AccentTolerantMatcher.normalize("épi"))
        assertEquals("ca", AccentTolerantMatcher.normalize("ça"))
    }

    @Test
    fun testNormalizeLowercases() {
        assertEquals("kreyol", AccentTolerantMatcher.normalize("KREYOL"))
        // Les majuscules accentuées doivent aussi être normalisées
        assertEquals("kreyol", AccentTolerantMatcher.normalize("KRÉYÒL"))
        assertEquals("epi", AccentTolerantMatcher.normalize("Épi"))
    }

    @Test
    fun testNormalizeLeavesPlainTextUntouched() {
        assertEquals("bonjou", AccentTolerantMatcher.normalize("bonjou"))
        assertEquals("", AccentTolerantMatcher.normalize(""))
    }

    @Test
    fun testMatches() {
        assertTrue(AccentTolerantMatcher.matches("creole", "créole"))
        assertTrue(AccentTolerantMatcher.matches("KREYOL", "kréyòl"))
        assertFalse(AccentTolerantMatcher.matches("abc", "xyz"))
    }

    @Test
    fun testStartsWith() {
        assertTrue(AccentTolerantMatcher.startsWith("kre", "kréyòl"))
        assertTrue(AccentTolerantMatcher.startsWith("fe", "fè"))
        assertTrue(AccentTolerantMatcher.startsWith("bon", "bòn"))
        assertFalse(AccentTolerantMatcher.startsWith("abc", "xyz"))
        // L'inverse : un input accentué doit matcher un mot sans accent
        assertTrue(AccentTolerantMatcher.startsWith("fè", "fenmen"))
    }

    @Test
    fun testHasAccents() {
        assertTrue(AccentTolerantMatcher.hasAccents("kréyòl"))
        assertTrue(AccentTolerantMatcher.hasAccents("fè"))
        assertFalse(AccentTolerantMatcher.hasAccents("bonjou"))
    }
}
