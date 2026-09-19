package com.example.kreyolkeyboard

import com.example.kreyolkeyboard.carnet.CarteMot
import com.example.kreyolkeyboard.carnet.JeuCarte
import com.example.kreyolkeyboard.carnet.Verdict
import com.example.kreyolkeyboard.carnet.Sonje
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le calendrier de révision du carnet.
 *
 * Tout ce qui est testé ici casse **en silence** : une échéance mal comptée ne
 * lève rien, elle fait seulement revenir les mots au mauvais moment, ou jamais.
 * Trois pannes en particulier n'auraient aucune trace à l'écran : le mur de
 * cartes d'une première ouverture, le paquet verrouillé par une horloge
 * reculée, et une file qui ne plafonne plus.
 */
class SonjePlanTest {

    private val JOUR = 86_400_000L

    /** Minuit UTC d'un jour arbitraire, pour compter en écarts et non en dates. */
    private val minuit = 20_000 * JOUR

    private fun carte(
        forme: String,
        numero: Int,
        boite: Int = 0,
        echeance: Int = Sonje.JAMAIS_PLANIFIEE
    ) = CarteMot(
        forme = forme,
        premiereFois = 0L,
        rencontres = 1,
        numero = numero,
        jeux = setOf(JeuCarte.MOKWARE),
        boite = boite,
        jourEcheance = echeance
    )

    // ------------------------------------------------------------- les boîtes

    @Test
    fun `les intervalles croissent et il y en a un par boite`() {
        assertEquals(Sonje.INTERVALLES.size, Sonje.BOITE_ACQUISE)
        Sonje.INTERVALLES.toList().zipWithNext().forEach { (a, b) ->
            assertTrue("intervalles non croissants : $a puis $b", b > a)
        }
    }

    @Test
    fun `une reussite monte d'une boite et s'arrete a l'acquisition`() {
        assertEquals(1, Sonje.apresReussite(0))
        assertEquals(Sonje.BOITE_ACQUISE, Sonje.apresReussite(Sonje.BOITE_ACQUISE - 1))
        assertEquals(Sonje.BOITE_ACQUISE, Sonje.apresReussite(Sonje.BOITE_ACQUISE))
    }

    @Test
    fun `un echec ramene au depart, meme de tres haut`() {
        // Redescendre d'un cran seulement laisserait un mot qu'on ne sait pas
        // revenir dans seize jours.
        assertEquals(0, Sonje.apresEchec(4))
        assertEquals(0, Sonje.apresEchec(0))
    }

    @Test
    fun `l'echeance suit l'intervalle de la boite atteinte`() {
        assertEquals(100 + 1, Sonje.echeance(100, 0))
        assertEquals(100 + 3, Sonje.echeance(100, 1))
        // Une carte acquise reçoit tout de même une date : le plus long
        // intervalle, faute de boîte suivante.
        assertEquals(
            100 + Sonje.INTERVALLES.last(),
            Sonje.echeance(100, Sonje.BOITE_ACQUISE)
        )
    }

    // -------------------------------------------------------------- les jours

    @Test
    fun `le numero de jour est un plancher, meme avant 1970`() {
        // `jour` divise par 86 400 000, et cette division doit arrondir **vers
        // le bas**, pas vers zéro. Une division ordinaire ferait rendre le même
        // numéro à deux instants séparés par la frontière de l'époque, et deux
        // jours voisins se confondraient.
        //
        // Ce test garde aussi ce que le calcul ne doit pas être : `Math.floorDiv`
        // dit exactement cela, mais il est apparu avec l'API 24 alors que le
        // projet descend à 21 et n'active pas le désucrage — l'appel plantait à
        // l'ouverture du carnet sur un Android 5 ou 6. La division est donc
        // écrite à la main, et il faut vérifier qu'elle est juste.
        val jour = 86_400_000L
        val coupure = Sonje.HEURE_COUPURE * 3_600_000L

        // Deux instants d'un même jour local rendent le même numéro, deux
        // instants de jours voisins des numéros qui se suivent — de part et
        // d'autre de zéro comme ailleurs.
        listOf(-3L, -1L, 0L, 1L, 3L, 700L).forEach { rang ->
            val debutDuJour = coupure + rang * jour
            assertEquals(
                "le jour $rang doit être d'un seul tenant",
                Sonje.jour(debutDuJour, 0),
                Sonje.jour(debutDuJour + jour - 1, 0)
            )
            assertEquals(
                "le jour ${rang + 1} suit immédiatement le jour $rang",
                Sonje.jour(debutDuJour, 0) + 1,
                Sonje.jour(debutDuJour + jour, 0)
            )
        }

        // Et le pas reste de un, seconde par seconde, autour de la frontière
        // que la troncature vers zéro casserait.
        assertEquals(
            Sonje.jour(coupure, 0) - 1,
            Sonje.jour(coupure - 1, 0)
        )
    }

    @Test
    fun `le verdict fait monter ou retomber la carte`() {
        assertEquals(3, Sonje.apresVerdict(2, Verdict.EXACT))
        assertEquals(0, Sonje.apresVerdict(2, Verdict.FAUX))
        assertEquals(
            Sonje.BOITE_ACQUISE,
            Sonje.apresVerdict(Sonje.BOITE_ACQUISE - 1, Verdict.EXACT)
        )
    }

    @Test
    fun `la coupure de quatre heures rattache la nuit a la veille`() {
        val veilleMidi = Sonje.jour(minuit - 12 * 3_600_000L, 0)
        val nuitTroisHeures = Sonje.jour(minuit + 3 * 3_600_000L, 0)
        val matinCinqHeures = Sonje.jour(minuit + 5 * 3_600_000L, 0)

        assertEquals("3 h appartient encore à la veille", veilleMidi, nuitTroisHeures)
        assertEquals("5 h ouvre le jour suivant", veilleMidi + 1, matinCinqHeures)
    }

    @Test
    fun `le decalage de fuseau est pris en compte`() {
        // 3 h UTC est encore la veille ; la même seconde, à UTC+2, il est 5 h,
        // donc le jour de révision a changé.
        val instant = minuit + 3 * 3_600_000L
        assertEquals(
            Sonje.jour(instant, 0) + 1,
            Sonje.jour(instant, 2 * 3_600_000)
        )
    }

    @Test
    fun `les jours se suivent sans trou ni doublon`() {
        val jours = (0 until 10).map { Sonje.jour(minuit + it * JOUR + 12 * 3_600_000L, 0) }
        assertEquals(jours.first() + 9, jours.last())
        assertEquals(10, jours.distinct().size)
    }

    // ------------------------------------------------------------ l'échéance

    @Test
    fun `une carte due aujourd'hui ou hier est a revoir, pas une carte de demain`() {
        assertTrue(Sonje.estDue(1, 100, 100))
        assertTrue(Sonje.estDue(1, 99, 100))
        assertFalse(Sonje.estDue(1, 101, 100))
    }

    @Test
    fun `une carte acquise ne revient jamais`() {
        assertFalse(Sonje.estDue(Sonje.BOITE_ACQUISE, 1, 10_000))
    }

    @Test
    fun `une carte jamais planifiee est due`() {
        // C'est le cas d'une carte gagnée depuis la dernière session : elle
        // n'a pas d'échéance, et l'étalement ne l'a pas encore touchée.
        assertTrue(Sonje.estDue(0, Sonje.JAMAIS_PLANIFIEE, 100))
    }

    @Test
    fun `une horloge reculee ne verrouille pas le paquet`() {
        // Changement de fuseau ou réglage manuel : toutes les échéances
        // passent dans un futur qui n'arrive jamais. Au-delà du plus long
        // intervalle, l'échéance est tenue pour aberrante.
        val aujourdHui = 100
        val aberrante = aujourdHui + Sonje.INTERVALLES.last() + 1
        assertTrue(Sonje.estDue(2, aberrante, aujourdHui))
        // Juste sous la limite, l'échéance reste crédible et la carte attend.
        assertFalse(Sonje.estDue(2, aujourdHui + Sonje.INTERVALLES.last(), aujourdHui))
    }

    // ----------------------------------------------------------------- la file

    @Test
    fun `la file est plafonnee`() {
        val cartes = (1..40).map { carte("mot$it", it, boite = 0, echeance = 100) }
        assertEquals(Sonje.PLAFOND_SESSION, Sonje.file(cartes, 100).size)
    }

    @Test
    fun `les plus anciennement dues passent devant, puis les boites les plus basses`() {
        val cartes = listOf(
            carte("recent", 1, boite = 0, echeance = 100),
            carte("ancien", 2, boite = 3, echeance = 90),
            carte("memeJourBoiteHaute", 3, boite = 4, echeance = 100),
            carte("memeJourBoiteBasse", 4, boite = 1, echeance = 100)
        )
        val file = Sonje.file(cartes, 100).map { it.forme }
        assertEquals(listOf("ancien", "recent", "memeJourBoiteBasse", "memeJourBoiteHaute"), file)
    }

    @Test
    fun `la file ignore les cartes non dues et les acquises`() {
        val cartes = listOf(
            carte("due", 1, boite = 0, echeance = 100),
            carte("demain", 2, boite = 1, echeance = 101),
            carte("acquise", 3, boite = Sonje.BOITE_ACQUISE, echeance = 50)
        )
        assertEquals(listOf("due"), Sonje.file(cartes, 100).map { it.forme })
    }

    // ------------------------------------------------------------ l'étalement

    @Test
    fun `l'etalement evite le mur de la premiere ouverture`() {
        // Le piège que cette fonction existe pour désamorcer : un carnet de
        // deux cents cartes, toutes dues le même jour, à la première
        // ouverture. La collection doit revenir au rythme où elle a été faite.
        val aujourdHui = 100
        val plafond = Sonje.PLAFOND_SESSION
        assertEquals(aujourdHui, Sonje.echeanceDEtalement(aujourdHui, 0))
        assertEquals(aujourdHui, Sonje.echeanceDEtalement(aujourdHui, plafond - 1))
        assertEquals(aujourdHui + 1, Sonje.echeanceDEtalement(aujourdHui, plafond))
        assertEquals(aujourdHui + 2, Sonje.echeanceDEtalement(aujourdHui, 2 * plafond))

        // Et, bout à bout : deux cents cartes étalées ne rendent jamais plus
        // d'une session due le même jour.
        val etalees = (0 until 200).map { Sonje.echeanceDEtalement(aujourdHui, it) }
        etalees.groupingBy { it }.eachCount().forEach { (jour, nombre) ->
            assertTrue("jour $jour surchargé : $nombre cartes", nombre <= plafond)
        }
    }

    @Test
    fun `un sans-faute acquiert la carte au bout du compte`() {
        // Une carte est révisée tous les INTERVALLES[sa boîte] jours, donc le
        // trajet d'un sans-faute dure la somme des intervalles des boîtes
        // traversées, la première exceptée : elle est le délai d'avant la
        // toute première révision.
        var boite = 0
        var jour = 0
        repeat(Sonje.BOITE_ACQUISE - 1) {
            assertTrue("carte due au jour $jour", Sonje.estDue(boite, jour, jour))
            boite = Sonje.apresReussite(boite)
            jour = Sonje.echeance(jour, boite)
        }
        assertEquals(Sonje.BOITE_ACQUISE - 1, boite)
        assertEquals(Sonje.INTERVALLES.drop(1).sum(), jour)

        // La dernière réussite acquiert la carte, qui ne revient plus.
        boite = Sonje.apresReussite(boite)
        assertEquals(Sonje.BOITE_ACQUISE, boite)
        assertFalse(Sonje.estDue(boite, jour, jour + 10_000))
    }
}
