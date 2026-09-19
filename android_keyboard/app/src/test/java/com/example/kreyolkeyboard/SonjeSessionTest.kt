package com.example.kreyolkeyboard

import com.example.kreyolkeyboard.carnet.CarteMot
import com.example.kreyolkeyboard.carnet.ContenuCarte
import com.example.kreyolkeyboard.carnet.JeuCarte
import com.example.kreyolkeyboard.carnet.Rarete
import com.example.kreyolkeyboard.carnet.SessionSonje
import com.example.kreyolkeyboard.carnet.Sonje
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les règles d'une session de révision.
 *
 * Aucune ne se voit à l'écran quand elle casse : un échec qui ne repasse pas,
 * un repassage noté deux fois, un compteur qui dépasse le total. Le jeu
 * continue de tourner, il enseigne simplement de travers.
 */
class SonjeSessionTest {

    private fun contenu(
        forme: String,
        boite: Int = 0,
        glose: String = "sens de $forme",
        exemple: String? = null,
        numero: Int = 1
    ) = ContenuCarte(
        carte = CarteMot(
            forme = forme,
            premiereFois = 0L,
            rencontres = 1,
            numero = numero,
            jeux = setOf(JeuCarte.MOKWARE),
            boite = boite,
            jourEcheance = 0
        ),
        rarete = Rarete.COMMUN,
        rang = 42,
        glose = glose,
        exemple = exemple,
        creditExemple = if (exemple == null) null else "Corpus Potomitan"
    )

    // ------------------------------------------------------ une flashcard

    @Test
    fun `toute boite montre le mot de la carte`() {
        // Une flashcard pour toutes les boîtes : le dos porte le mot, rien
        // d'autre. Les boîtes 2 et plus faisaient taper le mot jusqu'à la
        // 23.0.0 ; ce test garde la décision.
        val session = SessionSonje(
            (0 until Sonje.BOITE_ACQUISE).map {
                contenu("kaz", boite = it, exemple = "An kaz-la ni on bèl jaden", numero = it + 1)
            }
        )
        repeat(Sonje.BOITE_ACQUISE) {
            assertEquals("kaz", session.courante?.mot)
            session.repondre(true)
        }
        assertTrue(session.fini)
    }

    // -------------------------------------------------------------- la file

    @Test
    fun `un echec repasse une fois, en fin de session`() {
        val session = SessionSonje(
            listOf(
                contenu("rate", numero = 1),
                contenu("juste", numero = 2)
            )
        )
        assertEquals(2, session.total)

        assertEquals("rate", session.courante?.mot)
        assertTrue("un premier échec doit être noté au carnet", session.repondre(false))

        assertEquals("juste", session.courante?.mot)
        assertTrue(session.repondre(true))

        // Le mot raté revient, mais en fin de file : le redemander aussitôt ne
        // testerait que la mémoire de l'instant.
        assertEquals("rate", session.courante?.mot)
        assertFalse("un repassage ne se note pas deux fois", session.repondre(true))

        assertTrue(session.fini)
        assertEquals(listOf("juste"), session.reussies)
        assertEquals(listOf("rate"), session.ratees)
    }

    @Test
    fun `un echec au repassage ne relance pas la carte indefiniment`() {
        val session = SessionSonje(listOf(contenu("rate")))
        session.repondre(false)
        assertEquals("rate", session.courante?.mot)
        session.repondre(false)
        // Rejouer jusqu'à réussite ferait durer une session d'une minute et
        // demie jusqu'à l'abandon.
        assertTrue(session.fini)
        assertEquals(1, session.ratees.size)
    }

    @Test
    fun `une session vide est finie d'emblee`() {
        val session = SessionSonje(emptyList())
        assertTrue(session.fini)
        assertEquals(0, session.total)
        assertNull(session.courante)
        assertFalse(session.repondre(true))
    }

    @Test
    fun `le rang avance avec les reponses et ne depasse pas le total`() {
        val session = SessionSonje(
            listOf(contenu("un", numero = 1), contenu("deux", numero = 2))
        )
        assertEquals(1, session.rang)
        session.repondre(false)
        assertEquals(2, session.rang)
        session.repondre(true)
        // Le repassage du premier mot n'ajoute pas une question au compteur.
        assertEquals(2, session.rang)
        assertEquals(2, session.total)
    }

    @Test
    fun `une carte de la file arrive dans l'ordre du calendrier`() {
        // La session prend la file telle que [Sonje.file] la rend : ce
        // test est là pour que le lien entre les deux ne soit pas coupé par
        // une refonte de l'une sans l'autre.
        val cartes = listOf(
            contenu("ancienne", numero = 1).carte.copy(jourEcheance = 90),
            contenu("recente", numero = 2).carte.copy(jourEcheance = 100)
        )
        val file = Sonje.file(cartes, 100)
        assertEquals(listOf("ancienne", "recente"), file.map { it.forme })
    }
}
