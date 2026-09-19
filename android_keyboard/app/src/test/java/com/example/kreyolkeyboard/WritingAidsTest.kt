package com.example.kreyolkeyboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les décisions du processeur de saisie sur l'espace et la ponctuation, testées
 * sans éditeur : elles reposent sur le texte avant le curseur et sur un délai.
 */
class WritingAidsTest {

    private fun point(avant: String, ms: Long) = InputProcessor.doubleEspaceEnPoint(avant, ms)
    private fun retire(avant: String, touche: String) = InputProcessor.retireEspaceAuto(avant, touche)

    // ===== Deux espaces, un point =====

    @Test
    fun `deux espaces rapproches apres un mot posent un point`() {
        assertTrue(point("bonjou ", 200))
        assertTrue(point("a ", 0))
        assertTrue(point("an 2 ", 300))
    }

    @Test
    fun `un delai trop long ne pose pas de point`() {
        assertFalse(point("bonjou ", InputProcessor.DELAI_DOUBLE_ESPACE_MS + 1))
        assertTrue(point("bonjou ", InputProcessor.DELAI_DOUBLE_ESPACE_MS))
    }

    @Test
    fun `un espace apres une ponctuation ne pose pas de point`() {
        assertFalse(point("bonjou, ", 100))
        assertFalse(point("fini. ", 100))
        assertFalse(point("quoi ? ", 100))
    }

    @Test
    fun `un espace apres un espace ou en debut de champ ne pose pas de point`() {
        assertFalse(point(" ", 100))
        assertFalse(point("", 100))
        assertFalse(point("mot  ", 100))
    }

    @Test
    fun `un chronometre negatif est refuse`() {
        assertFalse(point("bonjou ", -1))
    }

    // ===== Espace automatique avant la ponctuation =====

    @Test
    fun `la virgule et le point retirent l'espace pose apres une suggestion`() {
        assertTrue(retire("bonjou ", ","))
        assertTrue(retire("bonjou ", "."))
        assertTrue(retire("an 2 ", "."))
    }

    @Test
    fun `l'interrogation et l'exclamation gardent leur espace`() {
        // Le corpus écrit « Sa ou fè ? » et « Nou kay bengné ! »
        assertFalse(retire("fè ", "?"))
        assertFalse(retire("bengné ", "!"))
        assertFalse(retire("mot ", ";"))
        assertFalse(retire("mot ", ":"))
    }

    @Test
    fun `sans espace avant le curseur rien n'est retire`() {
        assertFalse(retire("bonjou", ","))
        assertFalse(retire("", "."))
        assertFalse(retire(" ", "."))
    }

    @Test
    fun `un espace qui ne suit pas un mot n'est pas retire`() {
        assertFalse(retire("bonjou, ", "."))
        assertFalse(retire("mot  ", ","))
    }
}
