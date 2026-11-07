package com.example.kreyolkeyboard

import com.example.kreyolkeyboard.carnet.Meubles
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.text.Normalizer

/**
 * Contrôles des dessins des cartes (`creole_blasons.json`, `creole_series.json`),
 * produits par `Dictionnaires/generate_blasons.py`.
 *
 * `Armorial` et `Series` avalent exprès toute erreur de lecture, pour qu'un
 * actif abîmé n'éteigne pas le carnet. Trois régressions passeraient donc
 * inaperçues :
 *
 * - un **meuble non dessiné** : la fenêtre retombe sur le tracé du mot, ce
 *   qu'on ne voit pas en relisant le seul JSON ;
 * - un **mot sans glose** : aucune carte ne peut le porter (`Carnet.ajouter`
 *   refuse les mots non glosés), le dessin serait du travail perdu ;
 * - une **case de série qu'aucun jeu ne remplit** : la série ne se finirait
 *   jamais.
 */
class BlasonsAssetTest {

    private fun actif(nom: String): JSONObject {
        val fichier = File("src/main/assets/$nom")
        assertTrue("$nom manquant : lancez Dictionnaires/generate_blasons.py", fichier.exists())
        return JSONObject(fichier.readText())
    }

    private fun blasons(): Map<String, String> {
        val b = actif("creole_blasons.json").getJSONObject("blasons")
        return b.keys().asSequence().associateWith { b.getJSONObject(it).getString("m") }
    }

    private fun plier(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}"), "")

    @Test
    fun `chaque meuble attribue est dessine`() {
        val dessines = Meubles.noms()
        for ((mot, meuble) in blasons()) {
            assertTrue("« $mot » porte « $meuble », qui n'est pas dessiné", meuble in dessines)
        }
    }

    @Test
    fun `aucun feu d'artifice`() {
        // Le propriétaire n'en veut nulle part dans l'application (2026-10-19),
        // et la bibliothèque reprise de LuxKeyb en contient un.
        assertFalse("feux_artifice" in blasons().values)
    }

    @Test
    fun `chaque mot dessine est glose`() {
        val gloses = actif("creole_translations.json").getJSONObject("translations")
        for (mot in blasons().keys) {
            assertTrue("« $mot » n'a pas de glose : aucune carte ne peut le porter", gloses.has(mot))
        }
    }

    @Test
    fun `chaque case de la serie des enluminures se remplit`() {
        val jouables = HashSet<String>()
        val gloses = actif("creole_translations.json").getJSONObject("translations")
        val glosees = gloses.keys().asSequence().map { plier(it) }.toSet()
        val dico = org.json.JSONArray(File("src/main/assets/creole_dict.json").readText())
        for (i in 0 until dico.length()) {
            val m = dico.getJSONArray(i).getString(0)
            if (m.length in 3..10 && plier(m) in glosees) jouables.add(m)
        }
        val cloze = actif("creole_cloze.json").getJSONArray("items")
        for (i in 0 until cloze.length()) jouables.add(cloze.getJSONObject(i).getString("a"))
        for (nom in listOf("creole_crossword.json", "creole_chassecroise.json")) {
            val grilles = actif(nom).getJSONArray("grilles")
            for (i in 0 until grilles.length()) {
                val mots = grilles.getJSONObject(i).getJSONArray("mots")
                for (j in 0 until mots.length()) jouables.add(mots.getJSONObject(j).getString("f"))
            }
        }
        val parMeuble = blasons().entries.groupBy({ it.value }, { it.key })
        val serie = actif("creole_series.json").getJSONArray("enluminures")
        assertTrue("série des enluminures vide", serie.length() > 0)
        for (i in 0 until serie.length()) {
            val m = serie.getString(i)
            val mots = parMeuble[m].orEmpty()
            assertTrue("la case « $m » n'a aucun mot qu'un jeu donne ($mots)", mots.any { it in jouables })
        }
    }
}
