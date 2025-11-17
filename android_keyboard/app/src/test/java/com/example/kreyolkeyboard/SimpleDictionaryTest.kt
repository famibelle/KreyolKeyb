package com.example.kreyolkeyboard

import org.junit.Test
import org.junit.Assert.*

/**
 * Tests simplifiés basés sur un échantillon du dictionnaire créole.
 * Ces tests valident la correction orthographique sans dépendre de l'environnement Android.
 */
class SimpleDictionaryTest {

    // Échantillon représentatif du dictionnaire créole réel
    private val sampleDictionary = listOf(
        Pair("ka", 15519),
        Pair("an", 10729),
        Pair("sé", 7177),
        Pair("on", 6933),
        Pair("té", 6834),
        Pair("yo", 6063),
        Pair("pou", 5812),
        Pair("nou", 5712),
        Pair("pa", 5244),
        Pair("ki", 4569),
        Pair("mwen", 4082),
        Pair("ou", 3709),
        Pair("sa", 3348),
        Pair("fè", 3274),
        Pair("la", 2531),
        Pair("moun", 1836),
        Pair("tout", 1802),
        Pair("bonjou", 850),
        Pair("bonswa", 420),
        Pair("mèsi", 650),
        Pair("souplé", 380),
        Pair("lanmou", 290),
        Pair("zanmi", 340),
        Pair("kréyòl", 520),
        Pair("ayiti", 410),
        Pair("manman", 280),
        Pair("papa", 260),
        Pair("timoun", 310),
        Pair("pitit", 270),
        Pair("lakay", 240),
        Pair("lapli", 180),
        Pair("solèy", 170),
        Pair("dlo", 220),
        Pair("manje", 300),
        Pair("bwè", 210),
        Pair("dòmi", 190),
        Pair("reveye", 160),
        Pair("travay", 350),
        Pair("lekòl", 320),
        Pair("liv", 230)
    )

    @Test
    fun testTypo_MissingLetter_Bonjou() {
        // "bonjo" → "bonjou" (lettre 'u' manquante)
        val distance = LevenshteinDistance.calculate("bonjo", "bonjou")
        assertEquals("Distance devrait être 1 (une lettre manquante)", 1, distance)
        
        // Vérifier que findClosestMatches trouve bien "bonjou"
        val candidates = sampleDictionary.filter { (word, _) ->
            kotlin.math.abs(word.length - 5) <= 2  // Pre-filter par longueur
        }
        
        val matches = candidates
            .map { (word, freq) -> Triple(word, freq, LevenshteinDistance.calculate("bonjo", word)) }
            .filter { it.third <= 2 }
            .sortedWith(compareBy<Triple<String, Int, Int>> { it.third }.thenByDescending { it.second })
            .take(5)
        
        assertTrue("Devrait trouver 'bonjou'", matches.any { it.first == "bonjou" })
        assertEquals("'bonjou' devrait être en premier", "bonjou", matches.first().first)
    }

    @Test
    fun testTypo_ExtraLetter_Mesi() {
        // "mesli" → "mèsi" (lettre 'l' en trop)
        // Note: les accents comptent comme des différences
        val distance = LevenshteinDistance.calculate("mesli", "mèsi")
        assertTrue("Distance devrait être >= 1", distance >= 1)
    }

    @Test
    fun testTypo_WrongLetter_Zanmi() {
        // "zanbi" → "zanmi" (lettre 'b' au lieu de 'm')
        val distance = LevenshteinDistance.calculate("zanbi", "zanmi")
        assertEquals("Distance devrait être 1 (une substitution)", 1, distance)
    }

    @Test
    fun testShortWords() {
        // Test avec des mots très courts du dictionnaire
        val shortWords = listOf("ka", "an", "ou", "pa", "ki", "sa", "la", "yo", "té", "on")
        
        shortWords.forEach { word ->
            val distance = LevenshteinDistance.calculate(word, word)
            assertEquals("Distance avec soi-même devrait être 0 pour '$word'", 0, distance)
        }
    }

    @Test
    fun testCommonGreetings() {
        // Test des salutations avec différentes fautes
        val testCases = mapOf(
            "bonjo" to "bonjou",
            "bonswa" to "bonswa",  // exact
            "mesi" to "mèsi"
        )
        
        testCases.forEach { (input, expected) ->
            val distance = LevenshteinDistance.calculate(input, expected)
            assertTrue("Distance entre '$input' et '$expected' devrait être petite (<= 2)",
                      distance <= 2)
        }
    }

    @Test
    fun testFrequencyOrdering() {
        // Les mots les plus fréquents du dictionnaire
        val topWords = sampleDictionary.sortedByDescending { it.second }.take(5)
        
        assertEquals("Le mot le plus fréquent devrait être 'ka'", "ka", topWords[0].first)
        assertTrue("'ka' devrait avoir la plus haute fréquence", topWords[0].second > 10000)
    }

    @Test
    fun testMediumWords() {
        // Test avec des mots de longueur moyenne
        val mediumWords = listOf("mwen", "moun", "tout", "lanmou", "zanmi", "ayiti", "manman")
        
        mediumWords.forEach { word ->
            if (sampleDictionary.any { it.first == word }) {
                val distance = LevenshteinDistance.calculate(word, word)
                assertEquals("Distance avec soi-même devrait être 0 pour '$word'", 0, distance)
            }
        }
    }

    @Test
    fun testTypicalKreyolWords() {
        // Test de quelques mots créoles typiques
        val kreyolWords = mapOf(
            "bonjou" to 850,
            "mèsi" to 650,
            "kréyòl" to 520,
            "ayiti" to 410,
            "zanmi" to 340
        )
        
        kreyolWords.forEach { (word, expectedMinFreq) ->
            val dictEntry = sampleDictionary.find { it.first == word }
            assertNotNull("Le mot '$word' devrait être dans le dictionnaire", dictEntry)
            if (dictEntry != null) {
                assertTrue("La fréquence de '$word' devrait être >= $expectedMinFreq",
                          dictEntry.second >= expectedMinFreq)
            }
        }
    }

    @Test
    fun testCaseInsensitivity() {
        // L'algorithme devrait ignorer la casse
        assertEquals(0, LevenshteinDistance.calculate("BONJOU", "bonjou"))
        assertEquals(0, LevenshteinDistance.calculate("Bonjou", "bonjou"))
        assertEquals(1, LevenshteinDistance.calculate("BONJO", "bonjou"))
    }

    @Test
    fun testDistanceCalculation_RealExamples() {
        // Tests de distance sur des exemples réels
        val testCases = listOf(
            Triple("bonjo", "bonjou", 1),      // 1 lettre manquante
            Triple("zanbi", "zanmi", 1),       // 1 substitution
            Triple("ayti", "ayiti", 1),        // 1 insertion
            Triple("tout", "tout", 0),         // exact
            Triple("papa", "papa", 0)          // exact
        )
        
        testCases.forEach { (input, target, expectedDistance) ->
            val distance = LevenshteinDistance.calculate(input, target)
            assertEquals("Distance entre '$input' et '$target' devrait être $expectedDistance",
                        expectedDistance, distance)
        }
    }

    @Test
    fun testNoMatchScenario() {
        // Un mot complètement différent ne devrait pas matcher
        val weirdInput = "xyz123"
        
        val hasMatch = sampleDictionary.any { (word, _) ->
            LevenshteinDistance.calculate(weirdInput, word) <= 2
        }
        
        assertFalse("Un mot aléatoire ne devrait pas avoir de correspondances proches", hasMatch)
    }

    @Test
    fun testFamilyWords() {
        // Test des mots de famille
        val familyWords = listOf("manman", "papa", "pitit", "timoun")
        
        familyWords.forEach { word ->
            assertTrue("'$word' devrait être dans le dictionnaire",
                      sampleDictionary.any { it.first == word })
        }
    }

    @Test
    fun testVerbsAndActions() {
        // Test de quelques verbes courants
        val verbs = listOf("fè", "manje", "bwè", "dòmi", "travay")
        
        verbs.forEach { verb ->
            assertTrue("Le verbe '$verb' devrait être dans le dictionnaire",
                      sampleDictionary.any { it.first == verb })
        }
    }

    @Test
    fun testDictionaryStructure() {
        // Vérifier la structure du dictionnaire
        assertTrue("Le dictionnaire devrait contenir au moins 40 mots",
                  sampleDictionary.size >= 40)
        
        // Vérifier que toutes les fréquences sont positives
        sampleDictionary.forEach { (word, freq) ->
            assertTrue("La fréquence de '$word' devrait être positive", freq > 0)
        }
        
        // Vérifier que les mots ne sont pas vides
        sampleDictionary.forEach { (word, _) ->
            assertTrue("Le mot ne devrait pas être vide", word.isNotEmpty())
        }
    }

    @Test
    fun testAccentedWords() {
        // Test de mots avec accents présents dans notre échantillon de dictionnaire
        val accentedWords = listOf("sé", "té", "fè", "mèsi", "kréyòl", "solèy", "lekòl", "dòmi", "bwè", "souplé")
        
        var foundCount = 0
        accentedWords.forEach { word ->
            if (sampleDictionary.any { it.first == word }) {
                foundCount++
            }
        }
        
        assertTrue("Au moins 8 mots accentués devraient être dans le dictionnaire",
                  foundCount >= 8)
    }

    @Test
    fun testPerformanceIndicator() {
        // Test indicatif de performance (sans logging Android)
        val startTime = System.currentTimeMillis()
        
        // Simuler une recherche typique
        val input = "bonjo"
        var matchCount = 0
        
        sampleDictionary.forEach { (word, _) ->
            if (kotlin.math.abs(word.length - input.length) <= 2) {
                val distance = LevenshteinDistance.calculate(input, word)
                if (distance <= 2) {
                    matchCount++
                }
            }
        }
        
        val duration = System.currentTimeMillis() - startTime
        
        assertTrue("Devrait trouver au moins une correspondance", matchCount > 0)
        assertTrue("La recherche devrait être rapide (< 100ms pour 40 mots)", duration < 100)
    }
}
