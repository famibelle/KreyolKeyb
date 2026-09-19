package com.example.kreyolkeyboard

import android.text.InputType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Classification des champs et aides à l'écriture qui en dépendent.
 */
class FieldKindTest {

    private fun champ(inputType: Int) = FieldKind.de(inputType)

    private val texte = InputType.TYPE_CLASS_TEXT

    // ===== Classification =====

    @Test
    fun `un champ de texte ordinaire est du texte`() {
        assertEquals(FieldKind.TEXT, champ(texte))
        assertEquals(FieldKind.TEXT, champ(texte or InputType.TYPE_TEXT_FLAG_MULTI_LINE))
        assertEquals(FieldKind.TEXT, champ(texte or InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE))
    }

    @Test
    fun `les adresses electroniques et web sont reconnues`() {
        assertEquals(FieldKind.EMAIL, champ(texte or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS))
        assertEquals(FieldKind.EMAIL, champ(texte or InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS))
        assertEquals(FieldKind.URI, champ(texte or InputType.TYPE_TEXT_VARIATION_URI))
    }

    @Test
    fun `les trois formes de mot de passe sont des mots de passe`() {
        assertEquals(FieldKind.PASSWORD, champ(texte or InputType.TYPE_TEXT_VARIATION_PASSWORD))
        assertEquals(FieldKind.PASSWORD, champ(texte or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD))
        assertEquals(FieldKind.PASSWORD, champ(texte or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD))
    }

    @Test
    fun `un code PIN numerique est un mot de passe avant d'etre un nombre`() {
        assertEquals(
            FieldKind.PASSWORD,
            champ(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        )
        assertEquals(FieldKind.NUMBER, champ(InputType.TYPE_CLASS_NUMBER))
    }

    @Test
    fun `telephone et date ouvrent aussi les chiffres`() {
        assertEquals(FieldKind.PHONE, champ(InputType.TYPE_CLASS_PHONE))
        assertEquals(FieldKind.DATETIME, champ(InputType.TYPE_CLASS_DATETIME))
        assertTrue(FieldKind.NUMBER.ouvreLesChiffres)
        assertTrue(FieldKind.PHONE.ouvreLesChiffres)
        assertTrue(FieldKind.DATETIME.ouvreLesChiffres)
        assertFalse(FieldKind.TEXT.ouvreLesChiffres)
        assertFalse(FieldKind.EMAIL.ouvreLesChiffres)
    }

    @Test
    fun `un nom de personne est distingue du texte courant`() {
        assertEquals(FieldKind.PERSON_NAME, champ(texte or InputType.TYPE_TEXT_VARIATION_PERSON_NAME))
    }

    @Test
    fun `une classe inconnue retombe sur le texte`() {
        assertEquals(FieldKind.TEXT, champ(0))
    }

    // ===== Ce que chaque type autorise =====

    @Test
    fun `seul le texte courant et les noms proposent des mots`() {
        assertTrue(FieldKind.TEXT.proposeDesMots)
        assertTrue(FieldKind.PERSON_NAME.proposeDesMots)
        listOf(
            FieldKind.EMAIL, FieldKind.URI, FieldKind.NUMBER, FieldKind.PHONE,
            FieldKind.DATETIME, FieldKind.PASSWORD
        ).forEach { assertFalse("$it ne devrait rien proposer", it.proposeDesMots) }
    }

    @Test
    fun `pas de majuscule automatique dans une adresse ni un nombre`() {
        assertTrue(FieldKind.EMAIL.interditLaMajusculeAuto)
        assertTrue(FieldKind.URI.interditLaMajusculeAuto)
        assertTrue(FieldKind.PASSWORD.interditLaMajusculeAuto)
        assertTrue(FieldKind.NUMBER.interditLaMajusculeAuto)
        assertFalse(FieldKind.TEXT.interditLaMajusculeAuto)
    }

    @Test
    fun `l'editeur qui refuse les suggestions est respecte`() {
        assertTrue(FieldKind.refuseLesSuggestions(texte or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS))
        assertFalse(FieldKind.refuseLesSuggestions(texte))
        // Le drapeau n'a de sens que pour la classe texte
        assertFalse(FieldKind.refuseLesSuggestions(InputType.TYPE_CLASS_NUMBER))
    }

    // ===== Aides à l'écriture =====

    private fun aides(
        champ: FieldKind,
        refuse: Boolean = false,
        accents: Boolean = true,
        doubleEspace: Boolean = true,
        espaceAuto: Boolean = true
    ) = AidesEcriture.pour(champ, refuse, accents, doubleEspace, espaceAuto)

    @Test
    fun `le texte courant recoit les trois aides`() {
        val a = aides(FieldKind.TEXT)
        assertTrue(a.restaurerLesAccents)
        assertTrue(a.doubleEspaceEnPoint)
        assertTrue(a.retirerEspaceAuto)
    }

    @Test
    fun `aucune aide hors du texte`() {
        listOf(
            FieldKind.EMAIL, FieldKind.URI, FieldKind.NUMBER, FieldKind.PHONE,
            FieldKind.DATETIME, FieldKind.PASSWORD
        ).forEach { assertEquals("$it", AidesEcriture.AUCUNE, aides(it)) }
    }

    @Test
    fun `un nom de personne n'est pas corrige mais garde la ponctuation`() {
        val a = aides(FieldKind.PERSON_NAME)
        assertFalse(a.restaurerLesAccents)
        assertTrue(a.doubleEspaceEnPoint)
    }

    @Test
    fun `l'interdiction de suggestions coupe la correction seulement`() {
        val a = aides(FieldKind.TEXT, refuse = true)
        assertFalse(a.restaurerLesAccents)
        assertTrue(a.doubleEspaceEnPoint)
        assertTrue(a.retirerEspaceAuto)
    }

    @Test
    fun `chaque reglage coupe son aide`() {
        assertFalse(aides(FieldKind.TEXT, accents = false).restaurerLesAccents)
        assertFalse(aides(FieldKind.TEXT, doubleEspace = false).doubleEspaceEnPoint)
        assertFalse(aides(FieldKind.TEXT, espaceAuto = false).retirerEspaceAuto)
    }
}
