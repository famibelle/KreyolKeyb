package com.example.kreyolkeyboard

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests du garde-fou qui empêche le clavier de retenir quoi que ce soit d'une
 * saisie sensible.
 *
 * Les statistiques de vocabulaire comptent les mots validés : elles ne doivent
 * jamais voir passer un mot de passe. La vérification a lieu avant toute
 * écriture.
 */
class SensitiveFieldTest {

    private fun sensitive(inputType: Int, imeOptions: Int = 0) =
        KreyolInputMethodServiceRefactored.isSensitiveInput(inputType, imeOptions)

    // ===== Champs à protéger =====

    @Test
    fun `un mot de passe texte est protege`() {
        assertTrue(sensitive(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD))
    }

    @Test
    fun `un mot de passe affiche en clair est protege aussi`() {
        // C'est bien un mot de passe, seul son affichage diffère
        assertTrue(sensitive(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD))
    }

    @Test
    fun `un mot de passe de formulaire web est protege`() {
        assertTrue(sensitive(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD))
    }

    @Test
    fun `un code numerique est protege`() {
        assertTrue(sensitive(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD))
    }

    @Test
    fun `un champ declare non memorisable est protege`() {
        // L'application signale elle-même qu'elle ne veut pas d'apprentissage
        assertTrue(
            sensitive(
                InputType.TYPE_CLASS_TEXT,
                EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            )
        )
    }

    @Test
    fun `le drapeau de non memorisation prime sur le type de champ`() {
        assertTrue(
            sensitive(
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE,
                EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            )
        )
    }

    // ===== Champs ordinaires, où l'apprentissage doit avoir lieu =====

    @Test
    fun `un champ de message ordinaire n'est pas protege`() {
        assertFalse(sensitive(InputType.TYPE_CLASS_TEXT))
        assertFalse(sensitive(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE))
        assertFalse(sensitive(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE))
    }

    @Test
    fun `un champ e-mail ou nom de personne n'est pas protege`() {
        // Rien de sensible ici : seuls les mots déjà présents au dictionnaire
        // kréyòl sont comptés, et l'adresse elle-même n'en fait pas partie
        assertFalse(sensitive(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PERSON_NAME))
        assertFalse(sensitive(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS))
    }

    @Test
    fun `un champ numerique ordinaire n'est pas protege`() {
        assertFalse(sensitive(InputType.TYPE_CLASS_NUMBER))
    }

    @Test
    fun `les variations de mot de passe d'une autre classe ne declenchent pas a tort`() {
        // TYPE_NUMBER_VARIATION_PASSWORD et TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        // partagent la même valeur de variation : la classe doit être vérifiée,
        // sinon un champ numérique ordinaire serait pris pour un code secret
        val numeroTelephone = InputType.TYPE_CLASS_PHONE
        assertFalse(sensitive(numeroTelephone))
    }

    @Test
    fun `un mot de passe de page web garde sa protection avec les drapeaux du navigateur`() {
        // Chrome ajoute à ses champs le drapeau de correction automatique : il
        // ne doit pas masquer la variation mot de passe
        val motDePasseWeb = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
        assertTrue(sensitive(motDePasseWeb))
    }

    @Test
    fun `un mot de passe ne propose ni ne retient aucun mot`() {
        // onWordChanged lit proposeDesMots, onWordCompleted lit en plus
        // isSensitiveField : les deux doivent dire non pour un mot de passe
        listOf(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        ).forEach {
            assertFalse(FieldKind.de(it).proposeDesMots)
            assertTrue(sensitive(it))
        }
    }
}
