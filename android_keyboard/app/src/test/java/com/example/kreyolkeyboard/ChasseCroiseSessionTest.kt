package com.example.kreyolkeyboard

import com.example.kreyolkeyboard.chassecroise.ChasseCroiseSession
import com.example.kreyolkeyboard.crossword.CrosswordDifficulty
import com.example.kreyolkeyboard.crossword.CrosswordGrid
import com.example.kreyolkeyboard.crossword.CrosswordWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La mécanique d'une partie de « Mo an plas », sur une grille écrite à la main.
 *
 * Deux règles portent tout le jeu et ne se voient pas à l'écran :
 *
 * - Un mot incompatible ne se pose pas. C'est le crayon, pas une correction :
 *   on n'écrit pas deux lettres dans la même case.
 * - Un mot ne se verrouille — et ne livre donc sa glose — qu'une fois *tous*
 *   ses croisements posés. Récompenser au dépôt ferait résoudre la grille par
 *   sondage : poser, regarder si la glose s'allume, retirer.
 *
 * Une régression sur la seconde ne casserait rien de visible ; elle rendrait
 * seulement le jeu trivial.
 */
class ChasseCroiseSessionTest {

    /**
     * Une grille de quatre sur quatre :
     *
     * ```
     * H A U S
     * A     E
     * N     E
     * D
     * ```
     *
     * HAUS à l'horizontale, HAND et SEE à la verticale, croisant le premier
     * sur son H et sur son S. Elle n'a qu'une solution : HAND ne peut pas
     * prendre la place de HAUS, car SEE ne trouverait plus son S.
     */
    private fun grille() = CrosswordGrid(
        width = 4,
        height = 4,
        difficulty = CrosswordDifficulty.FACILE,
        words = listOf(
            CrosswordWord("HAUS", "Haus", "maison", 0, 0, across = true),
            CrosswordWord("HAND", "Hand", "main", 0, 0, across = false),
            CrosswordWord("SEE", "See", "lac", 0, 3, across = false)
        )
    )

    private fun partie() = ChasseCroiseSession(grille()) { it }

    @Test
    fun `un mot d'une autre longueur ne se pose pas`() {
        val partie = partie()
        partie.choisir(2) // SEE, trois lettres
        assertFalse(
            "SEE ne devrait pas entrer dans un emplacement de quatre lettres",
            partie.peutPoser(0, 2)
        )
        assertFalse("la pose aurait dû être refusée", partie.poser(0))
    }

    @Test
    fun `un mot qui contredit une lettre posee ne se pose pas`() {
        val partie = partie()
        partie.choisir(0)
        assertTrue(partie.poser(0)) // HAUS à l'horizontale

        // L'emplacement vertical de gauche commence par le H de HAUS : HAND y
        // entre, et rien d'autre de quatre lettres ne commencerait par H ici.
        assertTrue(partie.peutPoser(1, 1))
        // L'emplacement vertical de droite commence par le S de HAUS ; SEE y
        // entre, mais HAND non — et c'est la lettre déjà écrite qui l'interdit,
        // pas une correction du jeu.
        assertFalse(partie.peutPoser(2, 1))
    }

    @Test
    fun `un mot pose ne se verrouille pas avant que ses croisements le soient`() {
        val partie = partie()
        partie.choisir(0)
        assertTrue(partie.poser(0))

        // HAUS est juste, mais ses deux croisements sont encore vides : rien ne
        // l'a confronté, donc pas de récompense.
        assertTrue("HAUS est pourtant au bon endroit", partie.juste(0))
        assertFalse(
            "la glose ne doit pas être livrée tant que les croisements sont vides",
            partie.verrouille(0)
        )

        partie.choisir(1)
        assertTrue(partie.poser(1))
        assertFalse(
            "il reste le croisement de droite à poser",
            partie.verrouille(0)
        )

        partie.choisir(2)
        assertTrue(partie.poser(2))
        assertTrue("tous les croisements sont posés", partie.verrouille(0))
        assertTrue(partie.verrouille(1))
        assertTrue(partie.verrouille(2))
        assertTrue(partie.termine())
    }

    @Test
    fun `un mot mal place ne se signale qu'une fois confronte`() {
        val partie = partie()
        // HAND dans l'emplacement horizontal : compatible, puisque la grille
        // est vide, et pourtant faux.
        partie.choisir(1)
        assertTrue(partie.poser(0))
        assertFalse(partie.juste(0))
        assertFalse(
            "rien ne l'a encore confronté : le dénoncer dicterait la solution",
            partie.fautif(0)
        )

        // On pose HAUS à la verticale gauche — son H concorde avec le H de
        // HAND. Le croisement de droite reste vide, donc toujours rien.
        partie.choisir(0)
        assertTrue(partie.poser(1))
        assertFalse(partie.fautif(0))

        // Le troisième emplacement attend un D : plus aucun mot n'y entre, et
        // c'est là que l'erreur devient visible.
        assertTrue(
            "aucun mot ne devrait entrer dans l'emplacement restant",
            partie.emplacementsPossibles(2).isEmpty()
        )
    }

    @Test
    fun `retirer un mot le rend a la liste`() {
        val partie = partie()
        partie.choisir(0)
        assertTrue(partie.poser(0))
        assertTrue(partie.estPose(0))
        assertEquals('H', partie.lettreAt(0, 0))

        assertTrue(partie.retirer(0))
        assertFalse(partie.estPose(0))
        assertEquals(null, partie.lettreAt(0, 0))
        assertFalse(partie.retirer(0))
    }

    /**
     * Un mot gagné est acquis : il ne se retire plus.
     *
     * Ce n'est pas une commodité d'interface mais la contrepartie de la
     * récompense. Le jeu a versé la glose et l'a inscrite dans la liste des
     * sens acquis ; rendre le mot à la liste rouvrirait un emplacement dont le
     * joueur connaît déjà la réponse, et obligerait cette liste à reprendre ce
     * qu'elle a donné.
     */
    @Test
    fun `un mot gagne ne se retire plus`() {
        val partie = partie()
        partie.choisir(0)
        assertTrue(partie.poser(0)) // HAUS
        partie.choisir(1)
        assertTrue(partie.poser(1)) // HAND

        // HAND n'a qu'un croisement, le H de HAUS : il est déjà confronté,
        // donc gagné. HAUS attend encore SEE.
        assertTrue(partie.verrouille(1))
        assertFalse(partie.verrouille(0))

        assertFalse("un mot gagné ne se retire pas", partie.retirer(1))
        assertTrue(partie.estPose(1))
        assertEquals('A', partie.lettreAt(1, 0))

        assertTrue("HAUS n'est pas prouvé, il se reprend", partie.retirer(0))
    }

    /**
     * Le gain ne dépend plus de l'état courant de la grille.
     *
     * Sans mémoire, retirer un mot voisin ferait retomber
     * [croisementsCouverts] à faux et le sens déjà lu disparaîtrait de la
     * liste : le joueur verrait sa récompense reprise pour un geste qui ne le
     * concernait pas.
     */
    @Test
    fun `un mot gagne le reste quand son voisin s'en va`() {
        val partie = partie()
        partie.choisir(0)
        assertTrue(partie.poser(0))
        partie.choisir(1)
        assertTrue(partie.poser(1))
        assertTrue(partie.verrouille(1))

        assertTrue(partie.retirer(0)) // HAUS s'en va, HAND perd son croisement
        assertFalse(partie.croisementsCouverts(1))
        assertTrue("le sens déjà versé ne se reprend pas", partie.verrouille(1))
    }

    @Test
    fun `la solution remplit la grille`() {
        val partie = partie()
        partie.reveler()
        assertTrue(partie.termine())
        // Tout est posé, donc tout est confronté : la solution affichée
        // alimente la liste des sens comme une partie gagnée.
        assertTrue(partie.grid.words.indices.all { partie.verrouille(it) })
        assertEquals(3, partie.motsJustes())
        assertEquals('S', partie.lettreAt(0, 3))
        assertEquals('E', partie.lettreAt(2, 3))
    }

    /**
     * Choisir un mot puis le rechoisir le désélectionne : sans cela, un joueur
     * qui change d'avis n'a aucun moyen de reposer son doigt sans poser le mot
     * quelque part.
     */
    @Test
    fun `rechoisir le meme mot le deselectionne`() {
        val partie = partie()
        partie.choisir(0)
        assertEquals(0, partie.motChoisi)
        partie.choisir(0)
        assertEquals(-1, partie.motChoisi)
    }
}
