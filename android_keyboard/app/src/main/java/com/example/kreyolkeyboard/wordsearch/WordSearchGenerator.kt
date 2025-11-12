package com.example.kreyolkeyboard.wordsearch

import android.content.Context
import kotlin.random.Random

/**
 * Générateur de grilles de mots mêlés créoles
 */
object WordSearchGenerator {
    
    private val random = Random.Default
    private val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    
    fun generatePuzzle(
        context: Context,
        theme: String, 
        gridSize: Int = 8, 
        difficulty: WordSearchDifficulty = WordSearchDifficulty.NORMAL
    ): WordSearchPuzzle {
        
        // Limiter la taille de grille à 8x8 maximum
        val actualGridSize = minOf(gridSize, 8)
        
        // Récupérer les mots du thème depuis le dictionnaire
        val availableWords = WordSearchThemes.getThemeWords(theme, context)
        
        // Déterminer le nombre de mots selon la difficulté (réduit pour grille 8x8)
        val wordCount = when (difficulty) {
            WordSearchDifficulty.EASY -> 3
            WordSearchDifficulty.NORMAL -> 5
            WordSearchDifficulty.HARD -> 6
            WordSearchDifficulty.EXPERT -> 8
        }
        
        // Sélectionner des mots aléatoires qui rentrent dans la grille
        val selectedWords = selectWordsForGrid(availableWords, actualGridSize, wordCount)
        
        // Créer la grille vide
        val grid = Array(actualGridSize) { CharArray(actualGridSize) { ' ' } }
        
        // Placer les mots dans la grille
        val placedWords = placeWordsInGrid(grid, selectedWords, difficulty)
        
        // Remplir les cases vides avec des lettres aléatoires
        fillEmptySpaces(grid)
        
        return WordSearchPuzzle(
            theme = theme,
            grid = grid,
            words = placedWords,
            gridSize = actualGridSize,
            difficulty = difficulty
        )
    }
    
    private fun selectWordsForGrid(words: List<String>, gridSize: Int, count: Int): List<String> {
        // Filtrer les mots qui peuvent rentrer dans la grille (pour 8x8, mots de 3-7 lettres max)
        val fittingWords = words.filter { 
            it.length >= 3 && it.length <= minOf(gridSize - 1, 7) 
        }
        
        // Mélanger et prendre le nombre demandé
        return fittingWords.shuffled(random).take(count)
    }
    
    private fun placeWordsInGrid(
        grid: Array<CharArray>, 
        words: List<String>, 
        difficulty: WordSearchDifficulty
    ): List<WordSearchWord> {
        
        val placedWords = mutableListOf<WordSearchWord>()
        val gridSize = grid.size
        
        // Déterminer les directions possibles selon la difficulté
        val allowedDirections = getAllowedDirections(difficulty)
        
        for (word in words) {
            var placed = false
            var attempts = 0
            val maxAttempts = 100
            
            while (!placed && attempts < maxAttempts) {
                val direction = allowedDirections.random(random)
                val position = generateRandomPosition(gridSize, word.length, direction)
                
                if (canPlaceWord(grid, word, position.first, position.second, direction)) {
                    placeWord(grid, word, position.first, position.second, direction)
                    placedWords.add(WordSearchWord(
                        word = word.uppercase(),
                        startRow = position.first,
                        startCol = position.second,
                        direction = direction
                    ))
                    placed = true
                }
                attempts++
            }
        }
        
        return placedWords
    }
    
    private fun getAllowedDirections(difficulty: WordSearchDifficulty): List<WordDirection> {
        return when (difficulty) {
            WordSearchDifficulty.EASY -> listOf(
                WordDirection.HORIZONTAL,
                WordDirection.VERTICAL
            )
            WordSearchDifficulty.NORMAL -> listOf(
                WordDirection.HORIZONTAL,
                WordDirection.VERTICAL,
                WordDirection.DIAGONAL_DOWN_RIGHT,
                WordDirection.DIAGONAL_DOWN_LEFT
            )
            WordSearchDifficulty.HARD -> listOf(
                WordDirection.HORIZONTAL,
                WordDirection.VERTICAL,
                WordDirection.DIAGONAL_DOWN_RIGHT,
                WordDirection.DIAGONAL_DOWN_LEFT,
                WordDirection.HORIZONTAL_REVERSE,
                WordDirection.VERTICAL_REVERSE
            )
            WordSearchDifficulty.EXPERT -> WordDirection.values().toList()
        }
    }
    
    private fun generateRandomPosition(
        gridSize: Int, 
        wordLength: Int, 
        direction: WordDirection
    ): Pair<Int, Int> {
        
        return when (direction) {
            WordDirection.HORIZONTAL, WordDirection.HORIZONTAL_REVERSE -> {
                Pair(
                    random.nextInt(gridSize),
                    random.nextInt(gridSize - wordLength + 1)
                )
            }
            WordDirection.VERTICAL, WordDirection.VERTICAL_REVERSE -> {
                Pair(
                    random.nextInt(gridSize - wordLength + 1),
                    random.nextInt(gridSize)
                )
            }
            WordDirection.DIAGONAL_DOWN_RIGHT, WordDirection.DIAGONAL_UP_LEFT -> {
                Pair(
                    random.nextInt(gridSize - wordLength + 1),
                    random.nextInt(gridSize - wordLength + 1)
                )
            }
            WordDirection.DIAGONAL_DOWN_LEFT, WordDirection.DIAGONAL_UP_RIGHT -> {
                Pair(
                    random.nextInt(gridSize - wordLength + 1),
                    random.nextInt(wordLength - 1, gridSize)
                )
            }
        }
    }
    
    private fun canPlaceWord(
        grid: Array<CharArray>, 
        word: String, 
        startRow: Int, 
        startCol: Int, 
        direction: WordDirection
    ): Boolean {
        
        val deltas = getDirectionDeltas(direction)
        val deltaRow = deltas.first
        val deltaCol = deltas.second
        
        for (i in word.indices) {
            val row = startRow + i * deltaRow
            val col = startCol + i * deltaCol
            
            // Vérifier les limites
            if (row < 0 || row >= grid.size || col < 0 || col >= grid[0].size) {
                return false
            }
            
            // Vérifier si la case est libre ou contient déjà la même lettre
            val currentChar = grid[row][col]
            val wordChar = word[i].uppercaseChar()
            
            if (currentChar != ' ' && currentChar != wordChar) {
                return false
            }
        }
        
        return true
    }
    
    private fun placeWord(
        grid: Array<CharArray>, 
        word: String, 
        startRow: Int, 
        startCol: Int, 
        direction: WordDirection
    ) {
        val deltas = getDirectionDeltas(direction)
        val deltaRow = deltas.first
        val deltaCol = deltas.second
        
        for (i in word.indices) {
            val row = startRow + i * deltaRow
            val col = startCol + i * deltaCol
            grid[row][col] = word[i].uppercaseChar()
        }
    }
    
    private fun getDirectionDeltas(direction: WordDirection): Pair<Int, Int> {
        return when (direction) {
            WordDirection.HORIZONTAL -> Pair(0, 1)
            WordDirection.VERTICAL -> Pair(1, 0)
            WordDirection.DIAGONAL_DOWN_RIGHT -> Pair(1, 1)
            WordDirection.DIAGONAL_DOWN_LEFT -> Pair(1, -1)
            WordDirection.HORIZONTAL_REVERSE -> Pair(0, -1)
            WordDirection.VERTICAL_REVERSE -> Pair(-1, 0)
            WordDirection.DIAGONAL_UP_RIGHT -> Pair(-1, 1)
            WordDirection.DIAGONAL_UP_LEFT -> Pair(-1, -1)
        }
    }
    
    private fun fillEmptySpaces(grid: Array<CharArray>) {
        for (i in grid.indices) {
            for (j in grid[i].indices) {
                if (grid[i][j] == ' ') {
                    grid[i][j] = ALPHABET.random(random)
                }
            }
        }
    }
}