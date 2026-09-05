package com.example.kreyolkeyboard

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests du glissement de curseur sur la barre d'espace (v14.0.0).
 *
 * Seul le calcul des crans est testable en JVM : le reste du geste vit dans un
 * `OnTouchListener` et demande un MotionEvent. Ce calcul est pourtant la partie
 * où une erreur se voit, puisqu'elle décale le curseur d'un caractère à chaque
 * cran et que l'écart se cumule sur toute la longueur du geste.
 */
class SpaceCursorGestureTest {

    /** Dix dp sur un écran à densité 3 (Pixel, Galaxy S), soit le cas courant. */
    private val pas = 30f

    @Test
    fun `un deplacement inferieur a un cran ne bouge pas le curseur`() {
        assertEquals(0, KeyboardLayoutManager.cursorStepsFor(0f, pas))
        assertEquals(0, KeyboardLayoutManager.cursorStepsFor(29f, pas))
        assertEquals(0, KeyboardLayoutManager.cursorStepsFor(-29f, pas))
    }

    @Test
    fun `un cran plein deplace le curseur d'un caractere`() {
        assertEquals(1, KeyboardLayoutManager.cursorStepsFor(30f, pas))
        assertEquals(-1, KeyboardLayoutManager.cursorStepsFor(-30f, pas))
    }

    @Test
    fun `le geste est symetrique a gauche et a droite`() {
        // La troncature vers zéro doit traiter les deux sens de la même façon :
        // avec un arrondi vers le bas, -31 px donnerait -2 crans là où +31 px en
        // donne 1, et un aller-retour du doigt décalerait le curseur
        for (px in listOf(31f, 45f, 59f, 60f, 91f, 300f)) {
            assertEquals(
                -KeyboardLayoutManager.cursorStepsFor(px, pas),
                KeyboardLayoutManager.cursorStepsFor(-px, pas)
            )
        }
    }

    @Test
    fun `un balayage de tout l'ecran couvre une ligne de texte`() {
        // 360 dp de large à densité 3 : le geste doit rester utile sur une phrase
        // entière, sinon il ne remplace pas le tap dans le texte qu'il vise
        assertEquals(36, KeyboardLayoutManager.cursorStepsFor(1080f, pas))
    }

    @Test
    fun `les crans ne se cumulent pas en erreur d'arrondi`() {
        // L'ancre avance du nombre exact de pixels consommés, jamais de la
        // position du doigt : trois fois 45 px doivent donner trois crans au
        // total, et non trois fois un cran perdu à mi-chemin
        var ancre = 0f
        var total = 0
        for (position in listOf(45f, 90f, 135f)) {
            val crans = KeyboardLayoutManager.cursorStepsFor(position - ancre, pas)
            ancre += crans * pas
            total += crans
        }
        assertEquals(4, total)
    }

    @Test
    fun `un pas nul ne provoque pas de division par zero`() {
        assertEquals(0, KeyboardLayoutManager.cursorStepsFor(100f, 0f))
    }
}
