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
        francais: Boolean = false,
        nueGlosee: Boolean = false
    ): String? = AccentRestoration.choisir(tape, groupe, francais, nueGlosee)

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
    fun `une forme nue qui porte un sens propre n'est pas supplantee`() {
        // « bo » est le baiser, « bò » le côté : deux mots, pas deux graphies.
        // La glose de la forme nue suffit à le dire, quelles que soient les
        // fréquences.
        assertNull(choisir("bo", listOf("bò" to 40, "bo" to 6), nueGlosee = true))
    }

    @Test
    fun `une forme nue de deux lettres n'est jamais supplantee`() {
        // La table de gloses ne couvre que 622 mots : « mo » n'y est pas, et
        // c'est pourtant le mot du jeu « Mo an plas ». À deux lettres, les
        // homographes sont trop nombreux pour trancher sur la fréquence.
        assertNull(choisir("mo", listOf("mò" to 40, "mo" to 6)))
    }

    @Test
    fun `une graphie accentuee trop rare ne supplante pas une forme nue attestee`() {
        // « grandè » n'est vu que 3 fois : sous le plancher, on laisse « grande »,
        // qui est aussi un mot français que le lexique livré ne connaît pas.
        assertNull(choisir("grande", listOf("grandè" to 3, "grande" to 1)))
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

    // ===== Ce que la graphie nue muette débloque =====

    @Test
    fun `une graphie nue sans sens propre cede a la forme accentuee majoritaire`() {
        // bèf 13 contre bef 7, siklòn 8 contre siklon 7 : « bef » et « siklon »
        // ne sont pas des mots, ce sont les mêmes mots tapés sans accent.
        assertEquals("bèf", choisir("bef", listOf("bèf" to 13, "bef" to 7)))
        assertEquals("siklòn", choisir("siklon", listOf("siklòn" to 8, "siklon" to 7)))
    }

    @Test
    fun `la forme nue reste quand elle est la plus frequente`() {
        assertNull(choisir("siklon", listOf("siklòn" to 7, "siklon" to 8)))
        assertNull(choisir("siklon", listOf("siklòn" to 7, "siklon" to 7)))
    }

    @Test
    fun `une graphie rivale vue deux fois est tenue pour une coquille`() {
        // doktè 4 contre dòktè 2 : à ce volume, la seconde est une coquille du
        // corpus, pas une norme concurrente.
        assertEquals("doktè", choisir("dokte", listOf("doktè" to 4, "dòktè" to 2)))
    }

    @Test
    fun `une rivale rare reste quand la graphie de tete est elle aussi rare`() {
        // 3 contre 2 : la graphie de tête n'atteint pas le plancher, on s'abstient.
        assertNull(choisir("dokte", listOf("doktè" to 3, "dòktè" to 2)))
    }

    @Test
    fun `une rivale assez vue n'est pas ecartee comme une coquille`() {
        // pé et pè, déjà vu plus haut, et le cas général : 90 occurrences ne sont
        // pas une coquille.
        assertNull(choisir("pe", listOf("pé" to 244, "pè" to 90)))
        assertNull(choisir("swe", listOf("swé" to 10, "swè" to 8)))
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
        // Et la règle sert : plus de neuf cents groupes sont couverts.
        assertTrue("trop peu de mots restitués : $corriges", corriges > 900)
    }

    @Test
    fun `sur le vrai dictionnaire, les mots cites en exemple se comportent comme prevu`() {
        val groupes = groupes()
        fun sur(nu: String) = choisir(nu, groupes.getValue(nu))
        assertEquals("palé", sur("pale"))
        assertEquals("kréyòl", sur("kreyol"))
        assertEquals("bèf", sur("bef"))
        assertEquals("siklòn", sur("siklon"))
        assertEquals("doktè", sur("dokte"))
        assertNotNull(sur("zot"))
        // pé et pè cohabitent : pas de choix à la place de l'utilisateur
        assertNull(sur("pe"))
    }
}
