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
        
        // Lettres en trop
        assertEquals(1, LevenshteinDistance.calculate("mesli", "mèsi"))  // 'l' en trop
        assertEquals(1, LevenshteinDistance.calculate("souplle", "souplé"))  // 'l' en trop
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
        assertEquals(5, LevenshteinDistance.calculate("bonjou", "bonswa"))
        
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
        // Dictionnaire avec plusieurs mots à distance 1
        val dictionary = listOf(
            Pair("bonbon", 50),   // distance 1, fréquence moyenne
            Pair("bonjou", 100),  // distance 1, haute fréquence
            Pair("bondye", 30)    // distance 1, basse fréquence
        )
        
        val matches = LevenshteinDistance.findClosestMatches(
            input = "bonjo",
            dictionary = dictionary,
            maxDistance = 2,
            maxResults = 3
        )
        
        // Devrait trouver tous les mots à distance 1
        assertEquals(3, matches.size)
        
        // "bonjou" devrait être en premier (même distance mais fréquence plus haute)
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
    fun testCachedCalculation() {
        // Première calcul
        val distance1 = LevenshteinDistance.calculateCached("bonjou", "bonjo")
        
        // Deuxième calcul (devrait utiliser le cache)
        val distance2 = LevenshteinDistance.calculateCached("bonjou", "bonjo")
        
        // Les résultats doivent être identiques
        assertEquals(distance1, distance2)
        assertEquals(1, distance1)
        
        // Test avec l'ordre inversé (clé normalisée)
        val distance3 = LevenshteinDistance.calculateCached("bonjo", "bonjou")
        assertEquals(distance1, distance3)
    }

    @Test
    fun testClearCache() {
        // Ajouter quelques calculs au cache
        LevenshteinDistance.calculateCached("bonjou", "bonjo")
        LevenshteinDistance.calculateCached("mèsi", "mesi")
        
        // Vider le cache (ne devrait pas lever d'exception)
        LevenshteinDistance.clearCache()
        
        // Recalculer (devrait fonctionner)
        val distance = LevenshteinDistance.calculateCached("bonjou", "bonjo")
        assertEquals(1, distance)
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
        
        // "kounen" au lieu de "kounye a" (2 mots différents, distance importante)
        assertTrue(LevenshteinDistance.calculate("kounen", "kounye") > 2)
    }

    @Test
    fun testMaxDistanceFiltering() {
        val dictionary = listOf(
            Pair("bonjou", 100),    // distance 1
            Pair("bonswa", 80),     // distance 4
            Pair("bondye", 70),     // distance 2
            Pair("mèsi", 60)        // distance 6+
        )
        
        // maxDistance = 1 : devrait trouver seulement "bonjou"
        val matches1 = LevenshteinDistance.findClosestMatches(
            input = "bonjo",
            dictionary = dictionary,
            maxDistance = 1
        )
        assertEquals(1, matches1.size)
        assertEquals("bonjou", matches1.first().first)
        
        // maxDistance = 2 : devrait trouver "bonjou" et "bondye"
        val matches2 = LevenshteinDistance.findClosestMatches(
            input = "bonjo",
            dictionary = dictionary,
            maxDistance = 2
        )
        assertEquals(2, matches2.size)
        assertTrue(matches2.any { it.first == "bonjou" })
        assertTrue(matches2.any { it.first == "bondye" })
    }
}
