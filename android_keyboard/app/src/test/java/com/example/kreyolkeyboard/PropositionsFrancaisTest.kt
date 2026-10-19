package com.example.kreyolkeyboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests du réglage « Propositions en français » (v23.0.0, repris de LuxKeyb 30.1.0).
 *
 * Ce qu'ils couvrent : la règle qui décide si le moteur cherche des mots
 * français. Ce qu'ils ne peuvent pas couvrir : la rangée bleue qui disparaît et
 * le clavier qui raccourcit, posés dans la vue du service et vérifiés sur
 * l'émulateur.
 */
class PropositionsFrancaisTest {

    @Test
    fun `par defaut le francais est propose des trois lettres`() {
        val config = BilingualConfig()
        assertTrue(config.enableFrenchSupport)
        assertFalse(config.shouldActivateFrench("ma"))
        assertTrue(config.shouldActivateFrench("mai"))
    }

    @Test
    fun `coupe, le francais n'est plus propose quelle que soit la longueur`() {
        val config = BilingualConfig().copy(enableFrenchSupport = false)
        listOf("m", "mai", "maison", "malheureusement").forEach {
            assertFalse(it, config.shouldActivateFrench(it))
        }
    }
}
