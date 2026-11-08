package com.example.kreyolkeyboard.carnet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le tracé d'un mot est toujours centré sur la tige de son pied
 * (propriétaire, 2026-11-07) : une plaque posée de travers sur son pied se
 * lit comme une erreur.
 */
class TraceCentreTest {

    @Test
    fun `le tracé est centré sur la tige pour toutes les longueurs`() {
        for (n in 1..25) {
            val xs = AbscissesDuTrace.de(n, 68f)
            assertEquals("longueur $n", AbscissesDuTrace.AXE, (xs.first() + xs.last()) / 2f, 0.001f)
        }
    }

    @Test
    fun `le pas est constant et le tracé reste dans sa largeur`() {
        for (n in 2..25) {
            val xs = AbscissesDuTrace.de(n, 68f)
            val pas = xs[1] - xs[0]
            for (i in 1 until n) assertEquals(pas, xs[i] - xs[i - 1], 0.001f)
            assertTrue("longueur $n", xs.first() >= 16f - 0.001f && xs.last() <= 84f + 0.001f)
        }
    }
}
