package com.example.kreyolkeyboard

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests de applyCasingPattern — reproduction du bug découvert lors de la
 * simulation « suggestions uniquement » du 2026-07-11 : un tap de suggestion
 * sous majuscule automatique (une seule lettre tapée, en majuscule) mettait
 * toute la suggestion en capitales ("B" → "BÈL" au lieu de "Bèl").
 */
class CasingPatternTest {

    @Test
    fun `une seule majuscule initiale donne une casse de titre, pas du tout-majuscules`() {
        assertEquals("Bonjou", SuggestionEngine.applyCasingPattern("B", "bonjou"))
        assertEquals("Bèl", SuggestionEngine.applyCasingPattern("B", "bèl"))
        assertEquals("An", SuggestionEngine.applyCasingPattern("A", "an"))
    }

    @Test
    fun `deux majuscules ou plus donnent du tout-majuscules`() {
        assertEquals("BONJOU", SuggestionEngine.applyCasingPattern("BO", "bonjou"))
        assertEquals("BONJOU", SuggestionEngine.applyCasingPattern("BONJ", "bonjou"))
        assertEquals("BÈL", SuggestionEngine.applyCasingPattern("BÈ", "bèl"))
    }

    @Test
    fun `premiere lettre majuscule suivie de minuscules donne une casse de titre`() {
        assertEquals("Bonjou", SuggestionEngine.applyCasingPattern("Bon", "bonjou"))
        assertEquals("Kréyòl", SuggestionEngine.applyCasingPattern("Kré", "kréyòl"))
    }

    @Test
    fun `tout minuscules reste minuscules`() {
        assertEquals("bonjou", SuggestionEngine.applyCasingPattern("bon", "bonjou"))
        assertEquals("bèl", SuggestionEngine.applyCasingPattern("b", "bèl"))
    }

    @Test
    fun `pattern mixte applique caractere par caractere`() {
        assertEquals("kaBrit", SuggestionEngine.applyCasingPattern("kaBr", "kabrit"))
    }

    @Test
    fun `input ou suggestion vide rend la suggestion inchangee`() {
        assertEquals("bonjou", SuggestionEngine.applyCasingPattern("", "bonjou"))
        assertEquals("", SuggestionEngine.applyCasingPattern("bon", ""))
    }

    @Test
    fun `apostrophe avec une seule majuscule reste en casse de titre`() {
        // "A'" : une lettre majuscule + ponctuation — ne doit pas passer en tout-majuscules
        assertEquals("An'w", SuggestionEngine.applyCasingPattern("A'", "an'w"))
    }
}
