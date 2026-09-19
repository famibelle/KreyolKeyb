package com.example.kreyolkeyboard

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * La règle de restauration des accents n'a de valeur que par ce qu'elle
 * s'interdit : ces tests fixent les interdits autant que les cas d'usage.
 */
class AccentRestorationTest {

    private fun choisir(
        tape: String,
        groupe: List<Pair<String, Int>>,
        francais: Boolean = false
    ): String? = AccentRestoration.choisir(tape, groupe, francais)

    // ===== Ce qu'elle corrige =====

    @Test
    fun `une graphie accentuee unique est retablie`() {
        // palé : 75 occurrences, aucune autre graphie
        assertEquals("palé", choisir("pale", listOf("palé" to 75)))
    }

    @Test
    fun `la casse tapee est reportee`() {
        val groupe = listOf("palé" to 75)
        assertEquals("Palé", choisir("Pale", groupe))
        assertEquals("PALÉ", choisir("PALE", groupe))
    }

    @Test
    fun `une graphie majoritaire nettement l'emporte`() {
        // kréyòl 45 contre kréyol 5 : neuf fois plus
        assertEquals("kréyòl", choisir("kreyol", listOf("kréyòl" to 45, "kréyol" to 5)))
    }

    @Test
    fun `la graphie accentuee domine la forme nue attestee`() {
        // zot est vu 6 fois, zòt 145 : plus de huit fois
        assertEquals("zòt", choisir("zot", listOf("zòt" to 145, "zot" to 6)))
    }

    // ===== Ce qu'elle refuse de faire =====

    @Test
    fun `deux graphies rivales proches ne sont pas departagees`() {
        // pé 244 contre pè 90 : c'est une question de norme, pas une correction
        assertNull(choisir("pe", listOf("pé" to 244, "pè" to 90)))
    }

    @Test
    fun `un mot du lexique francais n'est jamais touche`() {
        assertNull(choisir("se", listOf("sé" to 885, "sè" to 7), francais = true))
    }

    @Test
    fun `un accent tape est respecte meme faux`() {
        // « kréyol » est attesté ; « kreyòl » ne l'est pas, mais l'utilisateur a mis un accent
        assertNull(choisir("kreyòl", listOf("kréyòl" to 45, "kréyol" to 5)))
        assertNull(choisir("kréyol", listOf("kréyòl" to 45, "kréyol" to 5)))
    }

    @Test
    fun `une forme nue attestee qui n'est pas nettement dominee reste`() {
        // 10 contre 6 : rien ne dit que la forme nue est une faute
        assertNull(choisir("mot", listOf("mòt" to 10, "mot" to 6)))
    }

    @Test
    fun `une graphie trop rare est ecartee`() {
        assertNull(choisir("kreten", listOf("kréten" to 1)))
    }

    @Test
    fun `un mot d'une seule lettre n'est pas reecrit`() {
        assertNull(choisir("a", listOf("à" to 300)))
    }

    @Test
    fun `un mot sans graphie accentuee n'est pas touche`() {
        assertNull(choisir("kay", listOf("kay" to 200)))
    }

    @Test
    fun `un mot avec un chiffre ou un tiret n'est pas touche`() {
        assertNull(choisir("pale2", listOf("palé" to 75)))
        assertNull(choisir("pa-le", listOf("palé" to 75)))
    }

    // ===== Propriété sur le vrai dictionnaire =====

    private fun dictionnaire(): List<Pair<String, Int>> {
        val fichier = File("src/main/assets/creole_dict.json")
        assertTrue("creole_dict.json manquant", fichier.exists())
        val tableau = JSONArray(fichier.readText())
        return (0 until tableau.length()).map {
            val ligne = tableau.getJSONArray(it)
            ligne.getString(0).lowercase() to ligne.optInt(1, 1)
        }
    }

    private fun groupes(): Map<String, List<Pair<String, Int>>> =
        dictionnaire().groupBy { AccentTolerantMatcher.normalize(it.first) }

    @Test
    fun `sur tout le dictionnaire, le mot restitue n'a que les lettres du mot tape`() {
        // L'interdit central : jamais un autre mot. On tape la forme sans accent de
        // chaque mot du dictionnaire ; quand la règle propose quelque chose, cela
        // ne peut différer de ce qui est tapé que par les accents.
        val groupes = groupes()
        var corriges = 0
        for ((nu, groupe) in groupes) {
            val restitue = choisir(nu, groupe) ?: continue
            corriges++
            assertEquals(
                "« $nu » a été changé en « $restitue » : autre chose que des accents",
                nu, AccentTolerantMatcher.normalize(restitue)
            )
        }
        // Et la règle sert : plus de huit cents groupes sont couverts.
        assertTrue("trop peu de mots restitués : $corriges", corriges > 800)
    }

    @Test
    fun `sur le vrai dictionnaire, les mots cites en exemple se comportent comme prevu`() {
        val groupes = groupes()
        fun sur(nu: String) = choisir(nu, groupes.getValue(nu))
        assertEquals("palé", sur("pale"))
        assertEquals("kréyòl", sur("kreyol"))
        assertNotNull(sur("zot"))
        // pé et pè cohabitent : pas de choix à la place de l'utilisateur
        assertNull(sur("pe"))
    }
}
