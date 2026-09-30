package com.example.kreyolkeyboard

import com.example.kreyolkeyboard.gamification.CreoleLevels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L'accueil de l'onglet Dékolaj (22.5.0) : quand il remplace l'installation, et
 * ce qu'il dit de la progression.
 */
class AccueilDuJourTest {

    // La taille réelle du dictionnaire, à quelques mots près : les seuils en
    // dépendent, pas la logique testée ici
    private val total = 5296

    @Test
    fun `l'accueil du jour attend un clavier installe, active et selectionne`() {
        assertEquals(ModeAccueil.DU_JOUR, AccueilDuJour.mode(true, true, true))
        // Premier passage : le parcours d'installation reste, même clavier prêt
        assertEquals(ModeAccueil.INSTALLATION, AccueilDuJour.mode(false, true, true))
        // Clavier désélectionné (autre clavier choisi, mise à jour système)
        assertEquals(ModeAccueil.INSTALLATION, AccueilDuJour.mode(true, true, false))
        assertEquals(ModeAccueil.INSTALLATION, AccueilDuJour.mode(true, false, false))
    }

    @Test
    fun `zero mot annonce le premier palier et non un echec`() {
        val phrase = AccueilDuJour.phraseProgression(0, total)
        val seuil = CreoleLevels.thresholds(total)[1]
        assertEquals("Encore ${AccueilDuJour.nombre(seuil)} mots avant Ti moun", phrase)
        assertFalse(phrase.contains("%"))
    }

    @Test
    fun `un seul mot restant s'ecrit au singulier`() {
        val seuil = CreoleLevels.thresholds(total)[2]
        assertEquals("Encore 1 mot avant Débrouya", AccueilDuJour.phraseProgression(seuil - 1, total))
    }

    @Test
    fun `le huitieme niveau n'est jamais nomme avant d'etre atteint`() {
        val seuils = CreoleLevels.thresholds(total)
        // Au seuil de Potomitan : le palier suivant est le secret
        val phrase = AccueilDuJour.phraseProgression(seuils[6], total)
        val secret = CreoleLevels.LEVELS[CreoleLevels.MAX_INDEX].name
        assertFalse(phrase.contains(secret))
        assertTrue(phrase.endsWith("avant le huitième niveau"))
        // Nulle part en chemin
        for (mots in 0 until seuils[CreoleLevels.MAX_INDEX] step 37) {
            assertFalse("$mots mots", AccueilDuJour.phraseProgression(mots, total).contains(secret))
        }
    }

    @Test
    fun `au plus haut niveau il n'y a plus de palier a annoncer`() {
        assertEquals(
            "Vous avez atteint le plus haut niveau. 👑",
            AccueilDuJour.phraseProgression(total, total)
        )
    }

    @Test
    fun `les milliers sont separes`() {
        val formate = AccueilDuJour.nombre(5296)
        assertEquals("5296", formate.filter { it.isDigit() })
        assertEquals(5, formate.length)
        assertEquals("42", AccueilDuJour.nombre(42))
    }
}
