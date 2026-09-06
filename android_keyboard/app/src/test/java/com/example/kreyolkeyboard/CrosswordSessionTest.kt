package com.example.kreyolkeyboard

import com.example.kreyolkeyboard.crossword.CrosswordDifficulty
import com.example.kreyolkeyboard.crossword.CrosswordGrid
import com.example.kreyolkeyboard.crossword.CrosswordSession
import com.example.kreyolkeyboard.crossword.CrosswordWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Règles de la partie de mots croisés, sans Android.
 *
 * Ce qui se joue ici décide de ce que le jeu enseigne : une case qui se valide
 * par erreur apprend une faute, une faute montrée trop tôt dicte la réponse.
 *
 * Les mots des deux grilles de test sont de vrais mots glosés de
 * `creole_translations.json` (pati, travay, gadé, dlo, dan, nou) : le jeu n'est
 * jamais alimenté par du kréyòl écrit à la main.
 *
 * `grilleDeTest()`, trois mots, un croisement partagé nulle part mais un accent
 * dans GADÉ :
 *
 * ```
 *     P . . . . .      PATI   (0,0) vertical
 *     A . . . G .      TRAVAY (2,0) horizontal, croise PATI sur le T
 *     T R A V A Y      GADÉ   (1,4) vertical, croise TRAVAY sur le A
 *     I . . . D .
 *     . . . . É .
 * ```
 */
class CrosswordSessionTest {

    private fun grilleDeTest() = CrosswordGrid(
        width = 6,
        height = 5,
        difficulty = CrosswordDifficulty.FACILE,
        words = listOf(
            CrosswordWord("PATI", "pati", "partir", 0, 0, across = false),
            CrosswordWord("TRAVAY", "travay", "travail", 2, 0, across = true),
            CrosswordWord("GADÉ", "gadé", "garder", 1, 4, across = false)
        )
    )

    /**
     * `grillePartagee()`, deux mots qui partent de la même case : c'est la
     * seule chose que la numérotation traite autrement que du texte.
     *
     * ```
     *     D L O      DLO (0,0) horizontal
     *     A . .      DAN (0,0) vertical, même case de départ
     *     N O U      NOU (2,0) horizontal, croise DAN sur le N
     * ```
     */
    private fun grillePartagee() = CrosswordGrid(
        width = 3,
        height = 3,
        difficulty = CrosswordDifficulty.FACILE,
        words = listOf(
            CrosswordWord("DLO", "dlo", "eau", 0, 0, across = true),
            CrosswordWord("DAN", "dan", "dent", 0, 0, across = false),
            CrosswordWord("NOU", "nou", "nous", 2, 0, across = true)
        )
    )

    @Test
    fun `la numerotation suit l'ordre de lecture et se partage`() {
        val grille = grillePartagee()
        // Deux mots qui partent de la même case portent le même numéro : c'est
        // la règle des mots croisés, et elle évite d'écrire deux fois « 1 »
        // dans un coin de case de trente pixels.
        assertEquals(1, grille.numeros[0]) // DLO
        assertEquals(1, grille.numeros[1]) // DAN, même case de départ
        assertEquals(2, grille.numeros[2]) // NOU

        // Et dans grilleDeTest, la numérotation suit bien l'ordre de lecture.
        val autre = grilleDeTest()
        assertEquals(1, autre.numeros[0]) // PATI part de (0,0)
        assertEquals(3, autre.numeros[1]) // TRAVAY part de (2,0)
        assertEquals(2, autre.numeros[2]) // GADÉ part de (1,4)
    }

    @Test
    fun `les cases hors des mots ne sont pas jouables`() {
        val grille = grilleDeTest()
        assertTrue(grille.estCaseJouable(0, 0))
        assertTrue(grille.estCaseJouable(4, 4))
        assertFalse("(0,4) n'est traversée par aucun mot", grille.estCaseJouable(0, 4))
        assertFalse("(4,0) n'est traversée par aucun mot", grille.estCaseJouable(4, 0))
        assertEquals('É', grille.solutionAt(4, 4))
        assertNull(grille.solutionAt(0, 4))
    }

    @Test
    fun `toucher deux fois une case croisee change de sens`() {
        val partie = CrosswordSession(grilleDeTest())
        // (2,0) porte le T de PATI (vertical) et le T de TRAVAY (horizontal).
        partie.selectionner(2, 0)
        val premier = partie.motSelectionne
        partie.selectionner(2, 0)
        val second = partie.motSelectionne
        assertTrue(
            "la seconde touche doit passer à l'autre mot de la case",
            premier != second
        )
        // Une case qui n'appartient qu'à un mot ne bascule nulle part.
        partie.selectionner(0, 0)
        partie.selectionner(0, 0)
        assertEquals(0, partie.motSelectionne) // PATI seul
    }

    @Test
    fun `ecrire avance et saute les cases deja remplies`() {
        val partie = CrosswordSession(grilleDeTest())
        // On remplit PATI par le vertical, ce qui donne son T à TRAVAY.
        partie.selectionnerMot(0)
        "PATI".forEach { partie.ecrire(it) }
        assertEquals('T', partie.lettreAt(2, 0))

        // En repartant sur TRAVAY, la première case vide est la deuxième lettre :
        // la sélection ne doit pas obliger à réécrire le T déjà acquis.
        partie.selectionnerMot(1)
        assertEquals(1, partie.caseSelectionnee)
        "RAVAY".forEach { partie.ecrire(it) }
        assertTrue(partie.motJuste(1))
        assertEquals(2, partie.motsJustes())
    }

    @Test
    fun `effacer recule quand la case courante est vide`() {
        val partie = CrosswordSession(grilleDeTest())
        partie.selectionnerMot(0)
        partie.ecrire('P')
        partie.ecrire('A')
        // Écrire avance : le curseur est sur la troisième case, qui est vide.
        // L'effacement recule donc et enlève le A, comme un retour arrière
        // partout ailleurs.
        partie.effacer()
        assertNull(partie.lettreAt(1, 0))
        partie.effacer()
        assertNull(partie.lettreAt(0, 0))
    }

    @Test
    fun `une faute ne se lit qu'une fois le mot rempli`() {
        val partie = CrosswordSession(grilleDeTest())
        partie.selectionnerMot(0)
        partie.ecrire('X')
        // La case est fausse, mais le mot n'est pas rempli : c'est l'écran qui
        // décide de ne rien montrer, et il s'appuie sur motRempli.
        assertTrue(partie.caseFausse(0, 0))
        assertFalse(partie.motRempli(0))

        "ATI".forEach { partie.ecrire(it) }
        assertTrue(partie.motRempli(0))
        assertFalse(partie.motJuste(0))
    }

    @Test
    fun `la partie se termine quand tous les mots sont justes`() {
        val partie = CrosswordSession(grilleDeTest())
        assertFalse(partie.termine())
        partie.reveler()
        assertTrue(partie.termine())
        assertEquals(3, partie.motsJustes())
        // Les accents font partie de la réponse : c'est tout l'objet du jeu.
        assertEquals('É', partie.lettreAt(4, 4))
    }
}
