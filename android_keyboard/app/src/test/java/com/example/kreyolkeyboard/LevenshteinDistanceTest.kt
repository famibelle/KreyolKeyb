package com.example.kreyolkeyboard

import org.junit.Test
import org.junit.Assert.*

/**
 * Tests unitaires pour la classe LevenshteinDistance
 * 
 * Vérifie le bon fonctionnement de l'algorithme de distance de Levenshtein
 * et de la recherche de corrections orthographiques.
 */
class LevenshteinDistanceTest {

    @Test
    fun testExactMatch() {
        // Deux mots identiques doivent avoir une distance de 0
        assertEquals(0, LevenshteinDistance.calculate("bonjou", "bonjou"))
        assertEquals(0, LevenshteinDistance.calculate("kréyòl", "kréyòl"))
        assertEquals(0, LevenshteinDistance.calculate("mèsi", "mèsi"))
    }

    @Test
    fun testEmptyStrings() {
        // Distance avec chaîne vide = longueur de l'autre chaîne
        assertEquals(6, LevenshteinDistance.calculate("", "bonjou"))
        assertEquals(6, LevenshteinDistance.calculate("bonjou", ""))
        assertEquals(0, LevenshteinDistance.calculate("", ""))
    }

    @Test
    fun testSingleCharacterDifference() {
        // 1 caractère de différence = distance 1
        assertEquals(1, LevenshteinDistance.calculate("bonjo", "bonjou"))  // 'u' manquant
        assertEquals(1, LevenshteinDistance.calculate("bonjour", "bonjou"))  // 'r' en trop
        assertEquals(1, LevenshteinDistance.calculate("konjou", "bonjou"))  // substitution k→b
    }

    @Test
    fun testKreyolCommonTypos() {
        // Fautes de frappe courantes en créole
        
        // Lettres mélangées
        assertEquals(2, LevenshteinDistance.calculate("bonoju", "bonjou"))  // j et o inversés
        
        // Accents manquants (distance > 0 car caractères différents)
        assertTrue(LevenshteinDistance.calculate("kreyol", "kréyòl") > 0)
        assertTrue(LevenshteinDistance.calculate("mesi", "mèsi") > 0)
        
        // Lettres en trop (l'accent manquant compte aussi pour 1 édition)
        assertEquals(2, LevenshteinDistance.calculate("mesli", "mèsi"))  // 'l' en trop + e→è
        assertEquals(2, LevenshteinDistance.calculate("souplle", "souplé"))  // 'l' en trop + e→é
    }

    @Test
    fun testCaseInsensitive() {
        // L'algorithme doit ignorer la casse
        assertEquals(0, LevenshteinDistance.calculate("BONJOU", "bonjou"))
        assertEquals(0, LevenshteinDistance.calculate("Bonjou", "bonjou"))
        assertEquals(1, LevenshteinDistance.calculate("BONJO", "bonjou"))
    }

    @Test
    fun testLargerDistances() {
        // Distances plus grandes (mots très différents)
        assertEquals(3, LevenshteinDistance.calculate("chat", "chien"))
        assertEquals(3, LevenshteinDistance.calculate("bonjou", "bonswa"))  // j→s, o→w, u→a
        
        // Mots complètement différents
        assertTrue(LevenshteinDistance.calculate("bonjou", "mèsi") > 3)
    }

    @Test
    fun testNormalizedCalculation() {
        // Test avec normalisation des accents
        val normalizer = { s: String -> s.replace("é", "e").replace("ò", "o") }
        
        val distance = LevenshteinDistance.calculateNormalized("kreyol", "kréyòl", normalizer)
        
        // Après normalisation, les deux mots sont identiques
        assertEquals(0, distance)
    }

    @Test
    fun testFindClosestMatches() {
        // Dictionnaire de test
        val dictionary = listOf(
            Pair("bonjou", 100),
            Pair("bonswa", 80),
            Pair("mèsi", 90),
            Pair("souplé", 70),
            Pair("bonbon", 60),
            Pair("bondye", 50)
        )
        
        // Rechercher "bonjo" (faute de frappe de "bonjou")
        val matches = LevenshteinDistance.findClosestMatches(
            input = "bonjo",
            dictionary = dictionary,
            maxDistance = 2,
            maxResults = 3
        )
        
        // Devrait trouver "bonjou" en premier (distance 1)
        assertTrue(matches.isNotEmpty())
        assertEquals("bonjou", matches.first().first)
        assertEquals(100, matches.first().second)
    }

    @Test
    fun testFindClosestMatchesNoResults() {
        // Dictionnaire de test
        val dictionary = listOf(
            Pair("bonjou", 100),
            Pair("mèsi", 90)
        )
        
        // Mot trop différent, ne devrait rien trouver avec maxDistance=2
        val matches = LevenshteinDistance.findClosestMatches(
            input = "zwazo",
            dictionary = dictionary,
            maxDistance = 2,
            maxResults = 5
        )
        
        assertTrue(matches.isEmpty())
    }

    @Test
    fun testFindClosestMatchesWithFrequency() {
        // Dictionnaire avec plusieurs mots proches de "bonjo"
        val dictionary = listOf(
            Pair("bonbon", 50),   // distance 2, fréquence moyenne
            Pair("bonjou", 100),  // distance 1, haute fréquence
            Pair("bondye", 30)    // distance 3, exclu (maxDistance = 2)
        )

        val matches = LevenshteinDistance.findClosestMatches(
            input = "bonjo",
            dictionary = dictionary,
            maxDistance = 2,
            maxResults = 3
        )

        // Devrait trouver les mots à distance ≤ 2
        assertEquals(2, matches.size)

        // "bonjou" devrait être en premier (distance la plus faible)
        assertEquals("bonjou", matches[0].first)
    }

    @Test
    fun testLengthFilter() {
        // Dictionnaire avec des mots de différentes longueurs
        val dictionary = listOf(
            Pair("ki", 100),        // longueur 2, très différent
            Pair("bonjou", 100),    // longueur 6, proche
            Pair("felisitasyon", 50) // longueur 13, très différent
        )
        
        val matches = LevenshteinDistance.findClosestMatches(
            input = "bonjo",  // longueur 5
            dictionary = dictionary,
            maxDistance = 2,
            maxResults = 5,
            lengthTolerance = 2
        )
        
        // Devrait trouver "bonjou" (longueur 6, différence 1 ≤ 2)
        // Devrait ignorer "ki" (longueur 2, différence 3 > 2)
        // Devrait ignorer "felisitasyon" (longueur 13, différence 8 > 2)
        assertTrue(matches.any { it.first == "bonjou" })
        assertFalse(matches.any { it.first == "ki" })
        assertFalse(matches.any { it.first == "felisitasyon" })
    }

    @Test
    fun testFindClosestMatchesNormalized() {
        // Dictionnaire avec accents
        val dictionary = listOf(
            Pair("kréyòl", 100),
            Pair("mèsi", 90),
            Pair("souplé", 80)
        )
        
        // Normalizer simple qui enlève les accents
        val normalizer = { s: String ->
            s.replace("é", "e")
             .replace("è", "e")
             .replace("ò", "o")
        }
        
        // Rechercher "kreyol" (sans accents)
        val matches = LevenshteinDistance.findClosestMatchesNormalized(
            input = "kreyol",
            dictionary = dictionary,
            normalizer = normalizer,
            maxDistance = 1,
            maxResults = 3
        )
        
        // Devrait trouver "kréyòl" (distance 0 après normalisation)
        assertTrue(matches.isNotEmpty())
        assertEquals("kréyòl", matches.first().first)
    }

    @Test
    fun testRealWorldKreyolExamples() {
        // Exemples réels de fautes en créole
        
        // "mesi" au lieu de "mèsi"
        assertEquals(1, LevenshteinDistance.calculate("mesi", "mèsi"))
        
        // "suplee" au lieu de "souplé"
        assertEquals(3, LevenshteinDistance.calculate("suplee", "souplé"))
        
        // "lanmou" au lieu de "lanmou" (correct)
        assertEquals(0, LevenshteinDistance.calculate("lanmou", "lanmou"))
        
        // "zanmi" au lieu de "zanmi" (correct)
        assertEquals(0, LevenshteinDistance.calculate("zanmi", "zanmi"))
        
        // "kounen" au lieu de "kounye" (e→y puis n→e)
        assertEquals(2, LevenshteinDistance.calculate("kounen", "kounye"))
    }

    @Test
    fun testMaxDistanceFiltering() {
        val dictionary = listOf(
            Pair("bonjou", 100),    // distance 1
            Pair("bonswa", 80),     // distance 3
            Pair("bondye", 70),     // distance 3
            Pair("mèsi", 60)        // distance 5
        )

        // maxDistance = 1 : devrait trouver seulement "bonjou"
        val matches1 = LevenshteinDistance.findClosestMatches(
            input = "bonjo",
            dictionary = dictionary,
            maxDistance = 1
        )
        assertEquals(1, matches1.size)
        assertEquals("bonjou", matches1.first().first)

        // maxDistance = 3 : devrait trouver "bonjou", "bonswa" et "bondye"
        val matches3 = LevenshteinDistance.findClosestMatches(
            input = "bonjo",
            dictionary = dictionary,
            maxDistance = 3
        )
        assertEquals(3, matches3.size)
        assertTrue(matches3.any { it.first == "bonswa" })
        assertTrue(matches3.any { it.first == "bondye" })
    }
}
