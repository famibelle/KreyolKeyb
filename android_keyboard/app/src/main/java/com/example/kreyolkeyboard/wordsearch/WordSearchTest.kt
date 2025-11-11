package com.example.kreyolkeyboard.wordsearch

import android.util.Log
import com.example.kreyolkeyboard.wordsearch.WordSearchGenerator
import com.example.kreyolkeyboard.wordsearch.WordSearchDifficulty

/**
 * Test simple pour valider la génération de mots mêlés
 */
object WordSearchTest {
    
    private val TAG = "WordSearchTest"
    
    fun runBasicTest(): Boolean {
        return try {
            Log.d(TAG, "🔧 Test de génération de mots mêlés...")
            
            // Test 1: Génération basique
            val puzzle = WordSearchGenerator.generatePuzzle(
                theme = "animaux",
                gridSize = 8,
                difficulty = WordSearchDifficulty.EASY
            )
            
            Log.d(TAG, "✅ Grille générée: ${puzzle.gridSize}x${puzzle.gridSize}")
            Log.d(TAG, "✅ Thème: ${puzzle.theme}")
            Log.d(TAG, "✅ Mots placés: ${puzzle.words.size}")
            
            puzzle.words.forEach { word ->
                Log.d(TAG, "   📝 ${word.word} à (${word.startRow},${word.startCol}) direction ${word.direction}")
            }
            
            // Test 2: Vérification de la grille
            val gridContent = StringBuilder()
            for (i in puzzle.grid.indices) {
                for (j in puzzle.grid[i].indices) {
                    gridContent.append("${puzzle.grid[i][j]} ")
                }
                gridContent.append("\n")
            }
            Log.d(TAG, "📋 Grille générée:\n$gridContent")
            
            // Test 3: Thèmes disponibles
            val themes = WordSearchThemes.getAllThemes()
            Log.d(TAG, "🎨 Thèmes disponibles: $themes")
            
            Log.d(TAG, "🎉 Tous les tests passent!")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur dans les tests: ${e.message}", e)
            false
        }
    }
    
    fun testAllThemes(): Boolean {
        return try {
            val themes = WordSearchThemes.getAllThemes()
            
            themes.forEach { theme ->
                val words = WordSearchThemes.getThemeWords(theme)
                Log.d(TAG, "🎯 Thème '$theme': ${words.size} mots disponibles")
                
                val puzzle = WordSearchGenerator.generatePuzzle(
                    theme = theme,
                    gridSize = 10,
                    difficulty = WordSearchDifficulty.NORMAL
                )
                
                Log.d(TAG, "   ✅ Puzzle généré avec ${puzzle.words.size} mots")
            }
            
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur test thèmes: ${e.message}", e)
            false
        }
    }
}