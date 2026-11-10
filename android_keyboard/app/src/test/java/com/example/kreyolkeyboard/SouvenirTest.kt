package com.example.kreyolkeyboard

import com.example.kreyolkeyboard.carnet.Fete
import com.example.kreyolkeyboard.carnet.Lieu
import com.example.kreyolkeyboard.carnet.Saison
import com.example.kreyolkeyboard.carnet.Souvenir
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Le calendrier de Guadeloupe des cartes, et le lieu tiré du mot. */
class SouvenirTest {

    @Test
    fun `le careme va de janvier a juin, l'hivernage de juillet a decembre`() {
        for (mois in 1..6) assertEquals(Saison.CAREME, Souvenir.du(2027, mois, 15).saison)
        for (mois in 7..12) assertEquals(Saison.HIVERNAGE, Souvenir.du(2027, mois, 15).saison)
    }

    @Test
    fun `les fetes fixes`() {
        assertEquals(Fete.NOUVEL_AN, Souvenir.du(2027, 1, 1).fete)
        assertEquals(Fete.VINGT_SEPT_MAI, Souvenir.du(2027, 5, 27).fete)
        assertEquals(Fete.TOUSSAINT, Souvenir.du(2027, 11, 1).fete)
        assertEquals(Fete.TOUSSAINT, Souvenir.du(2027, 11, 2).fete)
        assertEquals(Fete.CHANTE_NWEL, Souvenir.du(2027, 12, 1).fete)
        assertEquals(Fete.CHANTE_NWEL, Souvenir.du(2027, 12, 24).fete)
        assertNull(Souvenir.du(2027, 12, 25).fete)
        assertNull(Souvenir.du(2027, 5, 26).fete)
    }

    @Test
    fun `les jours gras suivent Paques`() {
        // Pâques 2027 tombe le 28 mars : mercredi des Cendres le 10 février.
        for (jour in 7..10) assertEquals(Fete.CARNAVAL, Souvenir.du(2027, 2, jour).fete)
        assertNull(Souvenir.du(2027, 2, 6).fete)
        assertNull(Souvenir.du(2027, 2, 11).fete)
        // Pâques 2026 tombait le 5 avril : mardi gras le 17 février.
        assertEquals(Fete.CARNAVAL, Souvenir.du(2026, 2, 17).fete)
    }

    @Test
    fun `un mot montre toujours le meme lieu, quelle que soit sa casse ou sa composition`() {
        assertEquals(Lieu.du("kréyòl"), Lieu.du("KRÉYÒL"))
        assertEquals(Lieu.du("kréyòl"), Lieu.du("kréyòl"))
    }

    @Test
    fun `l'empreinte est figee`() {
        // Changer l'ordre ou le nombre des lieux déplacerait les cartes déjà
        // gagnées : ce test casse avant.
        assertEquals(32, Lieu.values().size)
        assertEquals(30, Lieu.ANCIENS)
        assertEquals("8576:", Lieu.GRAINE)
        assertEquals(Lieu.values()[Math.floorMod("8576:lanmè".hashCode(), 30)], Lieu.du("lanmè"))
        assertEquals(Lieu.GRANDE_ANSE, Lieu.values()[0])
        assertEquals(Lieu.FIGUIER_MAUDIT, Lieu.values()[14])
        assertEquals(Lieu.MEMORIAL_ACTE, Lieu.values()[29])
        assertEquals(Lieu.DATCHA, Lieu.values()[30])
        assertEquals(Lieu.MAMELLES, Lieu.values()[31])
    }

    @Test
    fun `les cartes gagnees apres la bascule ont leur propre empreinte, figee`() {
        assertEquals("244:", Lieu.GRAINE_32)
        assertEquals(Lieu.values()[("244:lanmè".hashCode() * -0x61c88647) ushr 27], Lieu.nouveau("lanmè"))
        assertEquals(Lieu.nouveau("kréyòl"), Lieu.nouveau("KRÉYÒL"))
    }

    @Test
    fun `une carte gagnee avant la bascule garde son lieu sur trente`() {
        val bascule = 1_800_000_000_000L
        for (mot in listOf("lanmè", "kréyòl", "manjé", "zèl")) {
            assertEquals(Lieu.du(mot), Lieu.pour(mot, bascule - 1, bascule))
            assertEquals(Lieu.nouveau(mot), Lieu.pour(mot, bascule, bascule))
            assertTrue(Lieu.du(mot).ordinal < Lieu.ANCIENS)
        }
    }
}
