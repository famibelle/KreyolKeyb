package com.example.kreyolkeyboard

import com.example.kreyolkeyboard.KeyboardPreferences.DelaiAppuiLong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests du réglage de délai d'appui long (v23.0.0, repris de LuxKeyb 29.5.0).
 *
 * Ce qu'ils couvrent : les crans proposés et la relecture de la préférence
 * enregistrée. Ce qu'ils ne peuvent pas couvrir : le minuteur lui-même, qui vit
 * sur le Looper principal dans KeyboardLayoutManager et se vérifie sur appareil.
 */
class DelaiAppuiLongTest {

    @Test
    fun `le defaut est le cran court, celui des retours utilisateurs`() {
        assertEquals(DelaiAppuiLong.COURT, DelaiAppuiLong.DEFAUT)
        assertEquals(300L, DelaiAppuiLong.DEFAUT.ms)
    }

    @Test
    fun `le defaut est bien plus court que l'ancien double delai`() {
        // Avant la 23.0.0 : long-clic natif (400 ms au minimum) + 500 ms.
        assertTrue(DelaiAppuiLong.DEFAUT.ms < 400L + 500L)
    }

    @Test
    fun `les crans vont du plus court au plus long`() {
        val durees = DelaiAppuiLong.entries.map { it.ms }
        assertEquals(durees.sorted(), durees)
        assertEquals(durees.distinct(), durees)
    }

    @Test
    fun `un cran long reste offert a qui relache lentement`() {
        // ACCESSIBILITE.md, point 4 : 1,2 s au cran le plus long.
        assertEquals(1200L, DelaiAppuiLong.entries.last().ms)
    }

    @Test
    fun `chaque cran se relit a l'identique`() {
        DelaiAppuiLong.entries.forEach { cran ->
            assertEquals(cran, DelaiAppuiLong.depuisMs(cran.ms))
        }
    }

    @Test
    fun `une valeur inconnue ou absente retombe sur le defaut`() {
        assertEquals(DelaiAppuiLong.DEFAUT, DelaiAppuiLong.depuisMs(null))
        assertEquals(DelaiAppuiLong.DEFAUT, DelaiAppuiLong.depuisMs(450L))
        assertEquals(DelaiAppuiLong.DEFAUT, DelaiAppuiLong.depuisMs(-1L))
    }
}
