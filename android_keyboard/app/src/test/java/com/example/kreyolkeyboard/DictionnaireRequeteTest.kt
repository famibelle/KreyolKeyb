package com.example.kreyolkeyboard

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * La recherche du Dictionnaire ignore ce que le clavier ou le doigt ajoutent
 * autour du mot (22.5.0, repris de LuxKeyb 29.3.0) : « kaz . » ne trouvait rien.
 */
class DictionnaireRequeteTest {

    private fun nettoyer(requete: String) = TranslationDictionary.nettoyerRequete(requete)

    @Test
    fun `la ponctuation autour du mot est retiree`() {
        assertEquals("kaz", nettoyer("kaz . "))
        assertEquals("kaz", nettoyer("kaz "))
        assertEquals("maison", nettoyer("« maison » ?"))
        assertEquals("dlo", nettoyer("  ,dlo!"))
        // Tiret ou apostrophe au bord : du bruit, pas le mot
        assertEquals("ba", nettoyer("-ba-"))
    }

    @Test
    fun `l'interieur du mot est garde`() {
        assertEquals("pomme de terre", nettoyer("pomme  de terre."))
        assertEquals("an-nou", nettoyer("an-nou"))
        assertEquals("ba-w", nettoyer(" ba-w,"))
        assertEquals("l'eau", nettoyer("l'eau"))
    }

    @Test
    fun `une parenthese fermante qui referme une ouvrante reste`() {
        // Une forme de la table de gloses s'écrit ainsi
        assertEquals("kalòj (a poul)", nettoyer("kalòj (a poul)"))
        assertEquals("kalòj (a poul)", nettoyer("kalòj (a poul)."))
        // Seule, elle part comme le reste
        assertEquals("kaz", nettoyer("kaz)"))
    }

    @Test
    fun `une requete sans lettre devient vide`() {
        assertEquals("", nettoyer(" . , "))
        assertEquals("", nettoyer(""))
    }
}
