package com.example.kreyolkeyboard

import com.example.kreyolkeyboard.carnet.Lieu
import com.example.kreyolkeyboard.carnet.Rarete
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.text.Normalizer

/**
 * L'album des paysages peut être rempli : chacune de ses cases (trente
 * lieux, quatre raretés) a au moins un mot du vivier qui y tombe.
 *
 * Une case sans mot serait un album qu'on ne finit jamais, sans que rien ne
 * le dise. C'est le vivier qui décide, pas le code : une régénération du
 * dictionnaire ou des gloses peut vider une case, et ce test la voit.
 */
class PaysagesAlbumTest {

    private fun plier(mot: String): String =
        Normalizer.normalize(mot.lowercase(), Normalizer.Form.NFD).replace("\\p{Mn}+".toRegex(), "")

    private fun actif(nom: String): String {
        val fichier = File("src/main/assets/$nom")
        assertTrue("$nom manquant", fichier.exists())
        return fichier.readText()
    }

    @Test
    fun `chaque case de l'album a au moins un mot`() {
        val table = JSONObject(actif("creole_translations.json")).getJSONObject("translations")
        val glosees = HashSet<String>()
        table.keys().forEach { glosees.add(plier(it)) }
        val dico = JSONArray(actif("creole_dict.json"))
        val cases = HashSet<Pair<Lieu, Rarete>>()
        for (i in 0 until dico.length()) {
            val forme = dico.getJSONArray(i).getString(0)
            if (plier(forme) in glosees) cases += Lieu.du(forme) to Rarete.pourRang(i)
        }
        val manquantes = Lieu.values().flatMap { l -> Rarete.values().map { l to it } }.filter { it !in cases }
        assertEquals("cases de l'album sans aucun mot : $manquantes", emptyList<Pair<Lieu, Rarete>>(), manquantes)
    }
}
