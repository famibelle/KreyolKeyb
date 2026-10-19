package com.example.kreyolkeyboard

import com.example.kreyolkeyboard.carnet.Booster
import com.example.kreyolkeyboard.carnet.Rarete
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * La règle « première fois » de la pochette : la version longue ne se joue
 * qu'une fois par palier distingué, pour la première neuve que le carnet ne
 * connaissait pas. Une faute ici ne casse rien de visible, elle rejoue la
 * longue cérémonie à chaque rare ou ne la joue jamais.
 */
class PremiereRareTest {

    private val ordre = listOf(
        "zèl" to Rarete.TRES_RARE,
        "fenfon" to Rarete.RARE,
        "kawo" to Rarete.RARE,
        "kréyòl" to Rarete.COMMUN
    )
    private val toutes = ordre.map { it.first }.toSet()

    @Test
    fun unCarnetSansRareFeteLaPremiereDeChaquePalier() {
        assertEquals(
            setOf("zèl", "fenfon"),
            Booster.premieres(ordre, toutes, possedes = setOf(Rarete.COMMUN))
        )
    }

    @Test
    fun unPalierDejaPossedeNeSeFeteplusLonguement() {
        assertEquals(
            setOf("zèl"),
            Booster.premieres(ordre, toutes, possedes = setOf(Rarete.RARE))
        )
    }

    @Test
    fun uneCarteDejaConnueNestJamaisUnePremiere() {
        // fenfon revient, déjà au carnet : seule kawo, neuve, compte.
        assertEquals(
            setOf("kawo"),
            Booster.premieres(ordre, setOf("kawo"), possedes = setOf(Rarete.TRES_RARE))
        )
    }

    @Test
    fun lesCommunesNOntPasDePremiere() {
        assertEquals(
            emptySet<String>(),
            Booster.premieres(listOf("kréyòl" to Rarete.COMMUN, "granbwa" to Rarete.PEU_COMMUN),
                setOf("kréyòl", "granbwa"), possedes = emptySet())
        )
    }
}
