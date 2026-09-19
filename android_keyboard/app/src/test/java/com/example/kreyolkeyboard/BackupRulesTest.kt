package com.example.kreyolkeyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Rien ne part dans la sauvegarde cloud.
 *
 * La politique de confidentialité promet que la progression, le carnet et les
 * préférences ne quittent jamais l'appareil. Jusqu'à la 22.1.0, une règle
 * `include path="."` envoyait pourtant tous les fichiers de préférences à la
 * sauvegarde Google : carnet, niveau célébré, date du premier mot, emojis récents.
 *
 * Rien de tout ça ne casse quoi que ce soit à l'écran, d'où ce test. Une section
 * sans `include` sauvegarde tout par défaut : elle est donc fermée en excluant
 * chaque domaine en entier, et le test refuse qu'un `include` réapparaisse dans
 * le cloud.
 */
class BackupRulesTest {

    private val domaines = setOf("root", "file", "database", "sharedpref", "external")

    private fun lire(nom: String): Element {
        val fichier = File("src/main/res/xml/$nom")
        assertTrue("$nom introuvable", fichier.exists())
        return DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(fichier).documentElement
    }

    private fun enfants(parent: Element, balise: String): List<Element> {
        val liste = parent.getElementsByTagName(balise)
        return (0 until liste.length).map { liste.item(it) as Element }
    }

    private fun verifierFermee(section: Element, ou: String) {
        assertTrue("$ou : un include ouvrirait la sauvegarde", enfants(section, "include").isEmpty())
        val exclus = enfants(section, "exclude")
            .filter { it.getAttribute("path") == "." }
            .map { it.getAttribute("domain") }
            .toSet()
        assertEquals("$ou : chaque domaine doit être exclu en entier", domaines, exclus)
    }

    @Test
    fun laSauvegardeAvantAndroid12EstFermee() {
        verifierFermee(lire("backup_rules.xml"), "backup_rules.xml")
    }

    @Test
    fun leCloudEstFerme() {
        val sections = enfants(lire("data_extraction_rules.xml"), "cloud-backup")
        assertEquals("une seule section cloud-backup attendue", 1, sections.size)
        verifierFermee(sections.single(), "cloud-backup")
    }

    @Test
    fun leTransfertDAppareilNeGardeQueLesPreferencesEtExclutLaFrappe() {
        val sections = enfants(lire("data_extraction_rules.xml"), "device-transfer")
        assertEquals(1, sections.size)
        val section = sections.single()
        enfants(section, "include").forEach {
            assertEquals("sharedpref", it.getAttribute("domain"))
        }
        val exclus = enfants(section, "exclude").map { it.getAttribute("path") }
        assertTrue("creole_dict_with_usage.json" in exclus)
        assertTrue("carnet_vu.json" in exclus)
    }

    @Test
    fun leManifesteUtiliseCesDeuxFichiers() {
        val manifeste = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifeste.contains("android:fullBackupContent=\"@xml/backup_rules\""))
        assertTrue(manifeste.contains("android:dataExtractionRules=\"@xml/data_extraction_rules\""))
    }
}
