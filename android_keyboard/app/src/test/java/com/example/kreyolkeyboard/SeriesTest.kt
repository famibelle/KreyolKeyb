package com.example.kreyolkeyboard

import com.example.kreyolkeyboard.carnet.CarteMot
import com.example.kreyolkeyboard.carnet.JeuCarte
import com.example.kreyolkeyboard.carnet.Serie
import com.example.kreyolkeyboard.carnet.Series
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Les séries du carnet : finies, complétables, et comptées par emplacement.
 *
 * Une série qu'on ne peut pas finir ne casse rien à l'écran : elle affiche
 * simplement un emplacement qui reste vide pour toujours. C'est pour cela que
 * la preuve est ici, et pas dans un essai à la main.
 */
class SeriesTest {

    private fun carte(
        forme: String,
        numero: Int,
        jeux: Set<JeuCarte> = setOf(JeuCarte.MOTS_MELES),
        nombre: Int? = null
    ) = CarteMot(forme, 0L, 1, numero, jeux, nombre)

    /** Pas de jeu de nombres en kréyòl : la série est vide et n'est pas affichée. */
    @Test
    fun laSerieDesNombresEstVide() {
        assertTrue(Series.NOMBRES.isEmpty())
    }

    /** Un dessin porté par trois mots est un seul emplacement. */
    @Test
    fun unDessinPartageNeCompteQuUneFois() {
        val cartes = listOf(carte("bwa", 1), carte("pyébwa", 2), carte("lanmè", 3))
        val meubles = mapOf("bwa" to "arbre", "pyébwa" to "arbre", "lanmè" to "vague")
        val a = Series.avancement(
            Serie.ENLUMINURES, cartes, { it }, { meubles[it.forme] },
            listOf("arbre", "vague", "soleil")
        )
        assertEquals(3, a.total)
        assertEquals(2, a.remplis)
        assertFalse(a.complete)
        // La première gagnée occupe la place.
        assertEquals("bwa", a.emplacements.first { it.cle == "arbre" }.cartes.first().forme)
    }

    /** La carte de bienvenue n'est pas un jeu : elle ne remplit rien. */
    @Test
    fun laSerieDesJeuxIgnoreLaBienvenue() {
        val cartes = listOf(carte("kréyòl", 1, setOf(JeuCarte.ACCUEIL)))
        val a = Series.avancement(Serie.JEUX, cartes, { it }, { null }, emptyList())
        assertEquals(6, a.total)
        assertEquals(0, a.remplis)
    }

    /**
     * Sans actif des enluminures (il suppose la classification des blasons,
     * pas faite pour le kréyòl), la série est vide et le carnet ne l'affiche
     * pas. Le jour où l'actif arrive, ce test tombe et rappelle d'écrire celui
     * qui vérifie sa cohérence, comme côté LuxKeyb.
     */
    @Test
    fun pasDActifDesEnluminuresPourLInstant() {
        assertFalse(File("src/main/assets/creole_series.json").exists())
    }

    /** Une étape se fête une fois, et seulement la plus haute franchie d'un coup. */
    @Test
    fun lesEtapesSeFranchissentUneFois() {
        assertEquals(null, com.example.kreyolkeyboard.carnet.Etapes.franchi(9, 0))
        assertEquals(10, com.example.kreyolkeyboard.carnet.Etapes.franchi(10, 0))
        assertEquals(null, com.example.kreyolkeyboard.carnet.Etapes.franchi(11, 10))
        // Un carnet de 300 cartes à la mise à jour : une seule fête, celle des 250.
        assertEquals(250, com.example.kreyolkeyboard.carnet.Etapes.franchi(300, 0))
        assertEquals(null, com.example.kreyolkeyboard.carnet.Etapes.franchi(300, 250))
    }
}
