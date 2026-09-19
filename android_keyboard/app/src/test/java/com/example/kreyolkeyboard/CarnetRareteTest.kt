package com.example.kreyolkeyboard

import com.example.kreyolkeyboard.carnet.Rarete
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.text.Normalizer

/**
 * La rareté des cartes du carnet.
 *
 * Elle est lue sur le **rang de fréquence** d'une forme dans
 * `creole_dict.json`, qui est trié par fréquence décroissante : le rang est
 * donc gratuit et vérifiable, là où des « points de vie » inventés
 * apprendraient au joueur quelque chose de faux sur sa langue.
 *
 * Les seuils ont été **mesurés sur le vivier réel** avant d'être écrits, et
 * non transposés du carnet luxembourgeois, dont le dictionnaire est sept fois
 * plus gros (38 442 formes contre 5 296) : y recopier 3 000 / 6 500 / 9 000
 * rendrait toutes les cartes communes. Ce test rejoue la mesure sur les actifs
 * livrés — une régénération du dictionnaire, des gloses ou des grilles qui
 * déplacerait la courbe passerait autrement inaperçue.
 */
class CarnetRareteTest {

    private fun plier(mot: String): String =
        Normalizer.normalize(mot.lowercase(), Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")

    private fun actif(nom: String): String {
        val fichier = File("src/main/assets/$nom")
        assertTrue("$nom manquant", fichier.exists())
        return fichier.readText()
    }

    /** Les formes du dictionnaire, dans l'ordre : la position est le rang. */
    private fun rangs(): Map<String, Int> {
        val tableau = JSONArray(actif("creole_dict.json"))
        val rangs = LinkedHashMap<String, Int>(tableau.length())
        for (i in 0 until tableau.length()) {
            rangs[tableau.getJSONArray(i).getString(0)] = i
        }
        return rangs
    }

    /** Les formes pliées que la table de gloses couvre. */
    private fun glosees(): Set<String> {
        val table = JSONObject(actif("creole_translations.json"))
            .getJSONObject("translations")
        val formes = HashSet<String>(table.length())
        val cles = table.keys()
        while (cles.hasNext()) formes.add(plier(cles.next()))
        return formes
    }

    /**
     * Le vivier du carnet : les mots du dictionnaire qui portent une glose.
     *
     * C'est exactement ce que `Carnet.ajouter` accepte. Mesurer la courbe sur
     * autre chose — le dictionnaire entier, ou les mots que les jeux font
     * trouver sans les encarter — décrirait une distribution qu'aucune
     * collection n'aura jamais.
     */
    private fun vivier(rangs: Map<String, Int>): List<Int> {
        val glosees = glosees()
        return rangs.filterKeys { plier(it) in glosees }.values.toList()
    }

    private fun repartition(rangs: List<Int>): List<Int> {
        val n = rangs.size
        val paliers = rangs.map { Rarete.pourRang(it) }
        return Rarete.values().map { palier ->
            Math.round(100f * paliers.count { it == palier } / n)
        }
    }

    @Test
    fun `les seuils rangent dans l'ordre attendu`() {
        assertEquals(Rarete.COMMUN, Rarete.pourRang(0))
        assertEquals(Rarete.COMMUN, Rarete.pourRang(Rarete.SEUIL_COMMUN - 1))
        assertEquals(Rarete.PEU_COMMUN, Rarete.pourRang(Rarete.SEUIL_COMMUN))
        assertEquals(Rarete.PEU_COMMUN, Rarete.pourRang(Rarete.SEUIL_PEU_COMMUN - 1))
        assertEquals(Rarete.RARE, Rarete.pourRang(Rarete.SEUIL_PEU_COMMUN))
        assertEquals(Rarete.RARE, Rarete.pourRang(Rarete.SEUIL_RARE - 1))
        assertEquals(Rarete.TRES_RARE, Rarete.pourRang(Rarete.SEUIL_RARE))
    }

    /**
     * Un mot que le dictionnaire de fréquences ne connaît pas est le plus rare
     * de tous, et non le plus commun : c'est la lecture juste, une forme que le
     * corpus ignore est plus rare que tout ce qu'il connaît.
     */
    @Test
    fun `un rang inconnu est traite comme le plus rare`() {
        assertEquals(Rarete.TRES_RARE, Rarete.pourRang(null))
    }

    /**
     * La courbe d'un jeu de cartes : le commun domine, la dernière catégorie
     * se mérite. Mesurée à 42 / 28 / 20 / 10 % sur les 622 formes du vivier ;
     * les bornes laissent respirer une régénération normale et ne survivent
     * pas à un effondrement.
     */
    @Test
    fun `la courbe de rarete du vivier reste jouable`() {
        val vivier = vivier(rangs())
        assertTrue("vivier trop maigre : ${vivier.size}", vivier.size >= 500)

        val (commun, peuCommun, rare, tresRare) = repartition(vivier)
        val message = "répartition $commun/$peuCommun/$rare/$tresRare"

        assertTrue("$message : le commun ne domine plus", commun in 30..55)
        assertTrue("$message : le peu commun a fondu", peuCommun in 18..38)
        assertTrue("$message : les rares ont fondu", rare in 12..30)
        assertTrue("$message : les très rares ne se méritent plus", tresRare in 4..20)
    }

    /**
     * Mokwaré est le pourvoyeur de cartes rares, et doit le rester.
     *
     * C'est le seul jeu où le joueur **écrit** le mot, et ses grilles tirent
     * dans tout ce qui est glosé et écrivable au pavé : si sa distribution
     * s'aplatissait sur les mots fréquents, plus aucun jeu ne donnerait de
     * carte au-delà du peu commun (voir le test suivant pour Fraz a twou).
     */
    @Test
    fun `Mokware donne encore des cartes rares`() {
        val rangs = rangs()
        val grilles = JSONObject(actif("creole_crossword.json")).getJSONArray("grilles")
        val formes = HashSet<String>()
        for (i in 0 until grilles.length()) {
            val mots = grilles.getJSONObject(i).getJSONArray("mots")
            for (j in 0 until mots.length()) formes.add(mots.getJSONObject(j).getString("f"))
        }
        val connus = formes.mapNotNull { rangs[it] }
        assertTrue("trop peu de formes de grille : ${connus.size}", connus.size >= 300)

        val distinguees = connus.count { Rarete.pourRang(it).distinguee }
        assertTrue(
            "Mokwaré ne donne plus que ${100 * distinguees / connus.size} % de cartes rares",
            100 * distinguees / connus.size >= 12
        )
    }

    /**
     * Fraz a twou ne donne jamais de carte rare, et ce n'est pas un défaut.
     *
     * Ses réponses sont choisies dans une bande de fréquence (5 ≤ freq ≤ 150)
     * par `generate_cloze.py` : aucune ne peut dépasser le rang 1 800. Le test
     * fige cette conséquence plutôt que de la laisser surprendre quelqu'un qui
     * chercherait pourquoi ce jeu-là ne donne que du commun.
     */
    @Test
    fun `Fraz a twou ne donne que des cartes modestes`() {
        val rangs = rangs()
        val glosees = glosees()
        val items = JSONObject(actif("creole_cloze.json")).getJSONArray("items")
        val reponses = HashSet<String>()
        for (i in 0 until items.length()) reponses.add(items.getJSONObject(i).getString("a"))

        // Seules les réponses glosées deviennent des cartes : voir Carnet.ajouter.
        val encartables = reponses.filter { plier(it) in glosees }.mapNotNull { rangs[it] }
        assertTrue("trop peu de réponses encartables : ${encartables.size}", encartables.size >= 80)
        assertTrue(
            "Fraz a twou donne maintenant des cartes rares : la bande de " +
                "fréquence de generate_cloze.py a bougé",
            encartables.none { Rarete.pourRang(it).distinguee }
        )
    }
}
