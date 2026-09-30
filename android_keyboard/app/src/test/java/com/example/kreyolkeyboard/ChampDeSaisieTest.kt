package com.example.kreyolkeyboard

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ce que le clavier déduit du champ où l'on tape (v22.4.0, repris de LuxKeyb
 * 29.3 et 29.4) : la majuscule d'office, et l'icône et l'action de la touche
 * Entrée. Tous les `inputType` sont des entiers réels, relevés sur émulateur
 * côté LuxKeyb (Chrome, Google Contacts).
 */
class ChampDeSaisieTest {

    private val texte = InputType.TYPE_CLASS_TEXT
    private fun texteVariation(v: Int) = InputType.TYPE_CLASS_TEXT or v

    // ===== Touche Entrée =====

    @Test
    fun `la touche Entree suit l'action du champ`() {
        assertEquals(EditorInfo.IME_ACTION_SEARCH, InputProcessor.actionEntree(texte, EditorInfo.IME_ACTION_SEARCH))
        assertEquals(EditorInfo.IME_ACTION_SEND, InputProcessor.actionEntree(texte, EditorInfo.IME_ACTION_SEND))
        assertEquals(EditorInfo.IME_ACTION_DONE, InputProcessor.actionEntree(texte, EditorInfo.IME_ACTION_DONE))
        assertEquals(EditorInfo.IME_ACTION_NEXT, InputProcessor.actionEntree(texte, EditorInfo.IME_ACTION_NEXT))
        assertEquals(EditorInfo.IME_ACTION_GO, InputProcessor.actionEntree(texte, EditorInfo.IME_ACTION_GO))
    }

    @Test
    fun `la touche Entree va a la ligne quand le champ le demande`() {
        // Multiligne sans action : un message long
        assertNull(InputProcessor.actionEntree(
            texte or InputType.TYPE_TEXT_FLAG_MULTI_LINE, EditorInfo.IME_ACTION_UNSPECIFIED))
        // Action désactivée explicitement par l'application
        assertNull(InputProcessor.actionEntree(
            texte, EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_ENTER_ACTION))
        assertNull(InputProcessor.actionEntree(texte, EditorInfo.IME_ACTION_NONE))
        assertNull(InputProcessor.actionEntree(texte, EditorInfo.IME_ACTION_UNSPECIFIED))
    }

    @Test
    fun `un champ multiligne qui declare une action l'execute`() {
        // Une messagerie qui envoie d'un appui sur Entrée malgré la zone multiligne
        assertEquals(EditorInfo.IME_ACTION_SEND, InputProcessor.actionEntree(
            texte or InputType.TYPE_TEXT_FLAG_MULTI_LINE, EditorInfo.IME_ACTION_SEND))
    }

    // ===== Majuscule automatique =====

    @Test
    fun `la majuscule auto ne vaut que pour du texte courant`() {
        assertTrue(FieldKind.accepteLaMajusculeAuto(texte))
        assertTrue(FieldKind.accepteLaMajusculeAuto(
            texteVariation(InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE) or InputType.TYPE_TEXT_FLAG_MULTI_LINE))
        assertTrue(FieldKind.accepteLaMajusculeAuto(texteVariation(InputType.TYPE_TEXT_VARIATION_PERSON_NAME)))

        listOf(
            texteVariation(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS),
            texteVariation(InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS),
            texteVariation(InputType.TYPE_TEXT_VARIATION_URI),
            texteVariation(InputType.TYPE_TEXT_VARIATION_PASSWORD),
            texteVariation(InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD),
            texteVariation(InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD),
            texteVariation(InputType.TYPE_TEXT_VARIATION_FILTER),
            texteVariation(InputType.TYPE_TEXT_VARIATION_PHONETIC),
            InputType.TYPE_CLASS_NUMBER,
            InputType.TYPE_CLASS_PHONE,
            InputType.TYPE_CLASS_DATETIME,
            InputType.TYPE_NULL
        ).forEach { assertFalse("0x${Integer.toHexString(it)}", FieldKind.accepteLaMajusculeAuto(it)) }
    }

    @Test
    fun `la majuscule est attendue en debut de champ et de phrase`() {
        assertTrue(InputProcessor.majusculeAttendue(texte, ""))
        assertTrue(InputProcessor.majusculeAttendue(texte, "Bonjou. "))
        assertTrue(InputProcessor.majusculeAttendue(texte, "Sa ou fè ? "))
        assertFalse(InputProcessor.majusculeAttendue(texte, "Bonjou "))
        assertFalse(InputProcessor.majusculeAttendue(
            texteVariation(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS), ""))
        assertFalse(InputProcessor.majusculeAttendue(
            texteVariation(InputType.TYPE_TEXT_VARIATION_URI), ""))
        assertFalse(InputProcessor.majusculeAttendue(
            texteVariation(InputType.TYPE_TEXT_VARIATION_PASSWORD), ""))
        assertFalse(InputProcessor.majusculeAttendue(InputType.TYPE_CLASS_NUMBER, ""))
    }

    @Test
    fun `un champ de nom met une majuscule a chaque mot`() {
        val nom = texteVariation(InputType.TYPE_TEXT_VARIATION_PERSON_NAME) or
            InputType.TYPE_TEXT_FLAG_CAP_WORDS
        assertTrue(InputProcessor.majusculeAttendue(nom, "Marie "))
        assertFalse(InputProcessor.majusculeAttendue(nom, "Marie"))
        // Sans le drapeau, une espace ne suffit pas
        assertFalse(InputProcessor.majusculeAttendue(texte, "Marie "))
        // Un champ qui veut des capitales les met partout
        assertTrue(InputProcessor.majusculeAttendue(
            texte or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS, "ab"))
    }

    @Test
    fun `un champ sans classe mais avec des drapeaux de texte est du texte`() {
        // Le prénom de Google Contacts : 0x2060, nom de personne sans classe
        val prenomContacts = 0x2060
        assertEquals(FieldKind.PERSON_NAME, FieldKind.de(prenomContacts))
        assertTrue(InputProcessor.majusculeAttendue(prenomContacts, ""))
        assertTrue(InputProcessor.majusculeAttendue(prenomContacts, "Marie "))
        // Un champ vraiment nu (terminal) ne prend pas de majuscule
        assertFalse(InputProcessor.majusculeAttendue(InputType.TYPE_NULL, ""))
    }

    @Test
    fun `les champs du navigateur suivent la demande de la page`() {
        // Relevés sur Chrome (émulateur, LuxKeyb, 2026-09-29)
        val textarea = 0x2c0a1      // multiligne + phrases
        val parDefaut = 0xc0a1      // <input> : phrases
        val mots = 0xa0a1           // autocapitalize=words
        val sans = 0x80a1           // autocapitalize=off
        listOf(textarea, parDefaut, mots, sans).forEach {
            assertEquals(FieldKind.TEXT, FieldKind.de(it))
        }
        assertTrue(InputProcessor.majusculeAttendue(textarea, ""))
        assertTrue(InputProcessor.majusculeAttendue(parDefaut, "Bonjou. "))
        assertFalse(InputProcessor.majusculeAttendue(parDefaut, "Bonjou "))
        assertTrue(InputProcessor.majusculeAttendue(mots, "Marie "))
        // Une page qui désactive les majuscules est respectée
        assertFalse(InputProcessor.majusculeAttendue(sans, ""))
    }
}
