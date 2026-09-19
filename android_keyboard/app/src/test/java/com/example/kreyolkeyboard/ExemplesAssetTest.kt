package com.example.kreyolkeyboard

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.text.Normalizer

/**
 * `creole_exemples.json` : les phrases au dos des cartes du carnet.
 *
 * L'actif est produit hors du build par `Dictionnaires/generate_exemples.py`,
 * qui **consomme** le dictionnaire et la table de gloses. Une régénération qui
 * le tronquerait ne casserait rien au lancement : les cartes se retourneraient
 * simplement sur une glose seule, et la révision continuerait de tourner. Ce
 * sont exactement les pannes qu'un test d'actif existe pour attraper.
 *
 * Trois choses sont vérifiées, et la troisième est une obligation et non un
 * confort : chaque phrase porte le crédit de son auteur.
 */
class ExemplesAssetTest {

    private fun plier(mot: String): String =
        Normalizer.normalize(mot.lowercase(), Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")

    private fun actif(nom: String): JSONObject {
        val fichier = File("src/main/assets/$nom")
        assertTrue("$nom manquant", fichier.exists())
        return JSONObject(fichier.readText())
    }

    private fun exemples(): JSONObject = actif("creole_exemples.json").getJSONObject("exemples")

    /**
     * Le volume livré : 479 mots illustrés à la génération du 19 septembre
     * 2026, sur les 622 du vivier. Le plancher est le même que celui de
     * `--strict` et de l'étape *Verify Generated Assets*.
     */
    @Test
    fun `l'actif couvre assez de mots pour que le carnet parle`() {
        val exemples = exemples()
        assertTrue("seulement ${exemples.length()} mots illustrés", exemples.length() >= 400)
    }

    /**
     * Chaque phrase porte son mot, son crédit court et sa source entière.
     *
     * La source complète reste dans l'actif même quand l'écran ne peut
     * afficher que le crédit : l'attribution doit être exacte là où elle est
     * stockée, pas seulement là où elle est lue.
     */
    @Test
    fun `chaque phrase porte son mot et son attribution`() {
        val exemples = exemples()
        val cles = exemples.keys()
        var phrases = 0
        while (cles.hasNext()) {
            val mot = cles.next()
            val liste = exemples.getJSONArray(mot)
            assertTrue("$mot : aucune phrase", liste.length() > 0)
            assertTrue("$mot : plus de trois phrases", liste.length() <= 3)
            for (i in 0 until liste.length()) {
                val entree = liste.getJSONObject(i)
                val phrase = entree.optString("p")
                val credit = entree.optString("crd")
                val source = entree.optString("src")
                assertTrue("$mot : phrase vide", phrase.isNotEmpty())
                assertTrue("$mot : phrase sans crédit affichable", credit.isNotEmpty())
                assertTrue("$mot : phrase sans source complète", source.isNotEmpty())
                assertTrue(
                    "$mot : crédit de $credit caractères, il déborde de la carte",
                    credit.length <= 36
                )
                assertTrue(
                    "$mot absent de sa propre phrase : « $phrase »",
                    plier(mot) in plier(phrase)
                )
                val mots = phrase.split(" ").size
                assertTrue("$mot : phrase de $mots mots, hors bande", mots in 4..14)
                phrases++
            }
        }
        assertTrue("seulement $phrases phrases livrées", phrases >= 900)
    }

    /**
     * L'attribution du corpus voyage dans le fichier, pas seulement dans le
     * dépôt : un actif relu hors du projet doit dire d'où viennent ses
     * phrases. Même règle que `creole_cloze.json`, qui montre les mêmes
     * textes.
     */
    @Test
    fun `l'actif porte l'attribution du corpus`() {
        val sources = actif("creole_exemples.json").getJSONArray("sources")
        assertTrue("aucune source déclarée", sources.length() > 0)
        val texte = (0 until sources.length()).joinToString(" ") { sources.getString(it) }
        assertTrue("le corpus n'est pas nommé", "PawolKreyol" in texte)
        assertTrue("la licence n'est pas nommée", "Apache-2.0" in texte)
    }

    /**
     * Les mots illustrés sont tous glosés, donc tous encartables.
     *
     * `Carnet.ajouter` refuse un mot sans glose : illustrer une forme qui ne
     * deviendra jamais une carte ne ferait que grossir l'actif. Le test tient
     * la génération et le filtre alignés — si l'un des deux bouge, l'actif
     * porte du poids mort ou une carte perd sa phrase.
     */
    @Test
    fun `tout mot illustre peut devenir une carte`() {
        val gloses = actif("creole_translations.json").getJSONObject("translations")
        val glosees = HashSet<String>(gloses.length())
        val cles = gloses.keys()
        while (cles.hasNext()) glosees.add(plier(cles.next()))

        val exemples = exemples()
        val orphelins = ArrayList<String>()
        val mots = exemples.keys()
        while (mots.hasNext()) {
            val mot = mots.next()
            if (plier(mot) !in glosees) orphelins.add(mot)
        }
        assertEquals("des mots illustrés n'ont pas de glose : $orphelins", 0, orphelins.size)
    }
}
