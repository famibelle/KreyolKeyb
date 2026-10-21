package com.example.kreyolkeyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La visite guidée (23.3.0) : quand elle se propose d'elle-même, et comment
 * elle avance. Le dessin du voile se vérifie à l'écran, pas ici.
 */
class VisiteGuideeTest {

    @Test
    fun `la visite de l'application se propose une fois, sur l'accueil du jour`() {
        assertTrue(VisiteGuidee.doitSeLancer(dejaFaite = false, accueilDuJour = true, autreChoseOuvert = false))
        // Faite ou passée : elle ne revient que par « Visite guidée »
        assertFalse(VisiteGuidee.doitSeLancer(dejaFaite = true, accueilDuJour = true, autreChoseOuvert = false))
        // Tant que le clavier n'est pas installé, l'installation passe d'abord
        assertFalse(VisiteGuidee.doitSeLancer(dejaFaite = false, accueilDuJour = false, autreChoseOuvert = false))
        // Jamais par-dessus la pochette de bienvenue, un jeu ou le carnet
        assertFalse(VisiteGuidee.doitSeLancer(dejaFaite = false, accueilDuJour = true, autreChoseOuvert = true))
    }

    @Test
    fun `la visite du carnet attend une carte a montrer`() {
        assertTrue(VisiteGuidee.carnetDoitSeLancer(dejaFaite = false, cartes = 1, carteDemandee = false))
        assertFalse(VisiteGuidee.carnetDoitSeLancer(dejaFaite = false, cartes = 0, carteDemandee = false))
        assertFalse(VisiteGuidee.carnetDoitSeLancer(dejaFaite = true, cartes = 12, carteDemandee = false))
        // Le carnet qui s'ouvre sur une carte reçue la montre d'abord
        assertFalse(VisiteGuidee.carnetDoitSeLancer(dejaFaite = false, cartes = 12, carteDemandee = true))
    }

    @Test
    fun `le deroule va jusqu'a la derniere etape et s'y arrete`() {
        val d = VisiteGuidee.Deroule(3)
        assertEquals(0, d.index)
        assertFalse(d.derniere)
        assertTrue(d.suivante())
        assertTrue(d.suivante())
        assertTrue(d.derniere)
        assertFalse(d.suivante())
        assertEquals(2, d.index)
    }

    @Test
    fun `une visite reprise apres rotation repart de son etape, bornee`() {
        assertEquals(4, VisiteGuidee.Deroule(7, depart = 4).index)
        assertEquals(6, VisiteGuidee.Deroule(7, depart = 40).index)
        assertEquals(0, VisiteGuidee.Deroule(7, depart = -3).index)
    }

    @Test
    fun `le compteur part de un`() {
        assertEquals("1 / 7", VisiteGuidee.compteur(0, 7))
        assertEquals("7 / 7", VisiteGuidee.compteur(6, 7))
    }
}
