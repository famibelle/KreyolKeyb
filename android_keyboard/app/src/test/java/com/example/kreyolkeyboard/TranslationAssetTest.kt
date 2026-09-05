package com.example.kreyolkeyboard

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Contrôles de la table de gloses livrée (`creole_translations.json`).
 *
 * L'actif est produit hors du build par `Dictionnaires/generate_translations.py`,
 * donc rien dans la compilation ne garantit sa forme. Une glose vide, un code de
 * source inconnu ou une attribution disparue ne casse rien visiblement :
 * l'onglet Dictionnaire s'ouvre et montre des lignes muettes, et l'attribution
 * manquante est une infraction à la licence que personne ne verra.
 *
 * Comme `ClozeAssetTest`, celui-ci **échoue** quand le fichier manque au lieu de
 * se taire : le fichier est versionné, son absence est une régression.
 */
class TranslationAssetTest {

    private val codesSource = setOf("K", "W", "T")

    private fun charger(): JSONObject {
        val fichier = File("src/main/assets/creole_translations.json")
        assertTrue(
            "creole_translations.json manquant — lancez " +
                "Dictionnaires/generate_translations.py",
            fichier.exists()
        )
        return JSONObject(fichier.readText())
    }

    private fun table(): JSONObject = charger().getJSONObject("translations")

    private fun motsDuDictionnaire(): Set<String> {
        val fichier = File("src/main/assets/creole_dict.json")
        assertTrue("creole_dict.json manquant", fichier.exists())
        val tableau = JSONArray(fichier.readText())
        return (0 until tableau.length())
            .mapTo(HashSet()) { tableau.getJSONArray(it).getString(0) }
    }

    @Test
    fun `l'actif porte l'attribution et la licence des deux sources`() {
        val racine = charger()
        // CC BY-SA impose de citer la source et de partager à l'identique. Les
        // deux voyagent dans le fichier pour qu'il reste réutilisable même lu
        // hors du dépôt, et s'affichent en pied de l'onglet Dictionnaire.
        assertTrue(
            "licence absente",
            racine.optString("licence").contains("CC BY-SA")
        )
        val sources = racine.getJSONArray("attribution")
        val texte = (0 until sources.length()).joinToString(" ") { sources.getString(it) }
        assertTrue("Kreyolopedia non citée", texte.contains("Kreyolopedia"))
        assertTrue("Wiktionnaire non cité", texte.contains("Wiktionnaire"))
    }

    @Test
    fun `le volume livre reste au-dessus du plancher`() {
        val table = table()
        // Mesuré à 1 145 formes le 5 septembre 2026. En dessous de 800, une des
        // deux passes du Wiktionnaire a échoué sans le dire : la catégorie
        // guadeloupéenne en donne 350, les sections de traduction 775.
        assertTrue("seulement ${table.length()} formes glosées", table.length() >= 800)
    }

    @Test
    fun `chaque entree est affichable`() {
        val table = table()
        val cles = table.keys()
        while (cles.hasNext()) {
            val mot = cles.next()
            val entree = table.getJSONObject(mot)

            val glose = entree.optString("g")
            assertTrue("« $mot » : glose vide", glose.isNotBlank())
            // La glose tient sous le mot, sur une ligne d'écran de téléphone.
            // Le générateur coupe à 60 caractères ; le double laisse la place à
            // une évolution du seuil sans rendre le test bavard.
            assertTrue("« $mot » : glose de ${glose.length} caractères", glose.length <= 120)

            val source = entree.optString("s")
            assertTrue("« $mot » : source « $source » inconnue", source in codesSource)

            // C'est le code de source qui décide de l'adresse de l'article :
            // Kreyolopedia livre une URL complète, une forme atteinte par les
            // traductions porte le titre de la page française qui la cite.
            // Sans référence, ces deux-là mènent nulle part.
            if (source == "K") {
                assertTrue(
                    "« $mot » : fiche Kreyolopedia sans URL",
                    entree.optString("u").startsWith("http")
                )
            }
        }
    }

    @Test
    fun `aucune glose ne repete le mot qu'elle traduit`() {
        val table = table()
        val cles = table.keys()
        val fautives = mutableListOf<String>()
        while (cles.hasNext()) {
            val mot = cles.next()
            val glose = table.getJSONObject(mot).getString("g")
            // « pou → pou » est exact et sans le moindre intérêt : dans un
            // onglet qui s'appelle Dictionnaire, cela se lit comme une panne.
            // Le générateur écarte ces entrées ; le repli se fait sur la
            // source suivante quand elle en a une.
            val plie = AccentTolerantMatcher.normalize(mot)
            if (glose.split(",").all { AccentTolerantMatcher.normalize(it.trim()) == plie }) {
                fautives.add(mot)
            }
        }
        assertTrue("glosés par eux-mêmes : $fautives", fautives.isEmpty())
    }

    @Test
    fun `la table couvre une part utile du dictionnaire du clavier`() {
        val table = table()
        val dictionnaire = motsDuDictionnaire()
        val glosees = dictionnaire.count { table.has(it) }
        // 513 mesurés. Le plancher protège de la panne, pas de la stagnation :
        // les jeux tirent dans cette intersection, et Mo an Karénaj n'y trouve
        // déjà que 93 mots de cinq lettres.
        assertTrue(
            "seulement $glosees mots du dictionnaire sont glosés",
            glosees >= 400
        )
    }
}
