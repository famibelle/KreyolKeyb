package com.example.kreyolkeyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le clavier scindé d'une tablette couchée (v23.0.0, repris de LuxKeyb
 * 34.2.0) : où tombe la coupure, et ce que devient la barre d'espace.
 */
class ClavierScindeTest {

    private val poids = KeyboardLayoutManager::poidsTouche

    private val rangeesLettres = listOf(
        KeyboardLayoutManager.RANGEE_1,
        KeyboardLayoutManager.RANGEE_2,
        KeyboardLayoutManager.RANGEE_3
    )

    /** La rangée du bas d'un champ de texte, comme createAlphabeticLayout la pose. */
    private val rangeeDuBas = arrayOf("123", ",", "é", "-", " ", "è", ".", "EMOJI", "⏎")

    private fun moities(rangee: Array<String>): Pair<List<String>, List<String>> {
        val coupee = KeyboardLayoutManager.scinder(rangee, poids)
        val vide = coupee.indexOfFirst { it.first == null }
        return coupee.take(vide).map { it.first!! } to coupee.drop(vide + 1).map { it.first!! }
    }

    @Test
    fun chaqueRangeeNaQuUnVide() {
        (rangeesLettres + listOf(rangeeDuBas)).forEach { rangee ->
            val coupee = KeyboardLayoutManager.scinder(rangee, poids)
            assertEquals(rangee.joinToString(), 1, coupee.count { it.first == null })
        }
    }

    @Test
    fun laPremiereRangeeSeCoupeEntreTEtY() {
        val (gauche, droite) = moities(KeyboardLayoutManager.RANGEE_1)
        assertEquals(listOf("a", "z", "e", "r", "t"), gauche)
        assertEquals(listOf("y", "u", "i", "o", "p"), droite)
    }

    @Test
    fun laBarreDEspaceEstDedoubleeEtGardeSaLargeur() {
        val coupee = KeyboardLayoutManager.scinder(rangeeDuBas, poids)
        val espaces = coupee.filter { it.first == " " }
        assertEquals(2, espaces.size)
        assertEquals(poids(" "), espaces.sumOf { it.second.toDouble() }.toFloat(), 0.001f)
        val vide = coupee.indexOfFirst { it.first == null }
        assertEquals(" ", coupee[vide - 1].first)
        assertEquals(" ", coupee[vide + 1].first)
    }

    @Test
    fun aucuneToucheNePerdNiNeGagneDeLaLargeur() {
        (rangeesLettres + listOf(rangeeDuBas)).forEach { rangee ->
            val coupee = KeyboardLayoutManager.scinder(rangee, poids)
            val touches = coupee.filter { it.first != null }.sumOf { it.second.toDouble() }
            assertEquals(rangee.sumOf { poids(it).toDouble() }, touches, 0.001)
            val vide = coupee.first { it.first == null }.second
            val part = vide / (touches + vide)
            assertTrue(Math.abs(part - KeyboardLayoutManager.PART_VIDE_SCINDE) < 0.001)
        }
    }
}
