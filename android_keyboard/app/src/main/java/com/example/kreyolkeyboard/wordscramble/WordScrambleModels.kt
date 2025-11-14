package com.example.kreyolkeyboard.wordscramble

import android.content.Context
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Modèles de données pour le jeu de mots mélangés
 */

data class ScrambledWord(
    val originalWord: String,
    val scrambledLetters: List<Char>,
    val currentAnswer: List<Char?>,
    val hintsUsed: Int = 0
)

data class WordScrambleGame(
    val words: List<String>,
    val currentWordIndex: Int = 0,
    val score: Int = 0,
    val timePerWord: Int = 30, // secondes
    val difficulty: ScrambleDifficulty = ScrambleDifficulty.NORMAL
)

enum class ScrambleDifficulty {
    EASY,    // 4-5 lettres, 45 secondes
    NORMAL,  // 5-7 lettres, 30 secondes
    HARD     // 7-10 lettres, 20 secondes
}

/**
 * Chargement des mots créoles depuis le dictionnaire
 */
object WordScrambleData {
    
    private var cachedWords: List<String>? = null
    
    /**
     * Charge les mots du dictionnaire selon la difficulté
     */
    fun loadWords(context: Context, difficulty: ScrambleDifficulty): List<String> {
        cachedWords?.let { 
            return filterByDifficulty(it, difficulty).shuffled().take(10)
        }
        
        val words = mutableListOf<String>()
        
        try {
            val inputStream = context.assets.open("creole_dict.json")
            val reader = BufferedReader(InputStreamReader(inputStream))
            val jsonContent = reader.readText()
            reader.close()
            
            val jsonArray = JSONArray(jsonContent)
            
            for (i in 0 until jsonArray.length()) {
                val wordArray = jsonArray.getJSONArray(i)
                val word = wordArray.getString(0)
                
                // Filtrer par longueur selon difficulté
                if (isValidForDifficulty(word, difficulty)) {
                    words.add(word)
                }
            }
            
            cachedWords = words
            
        } catch (e: Exception) {
            e.printStackTrace()
            // Mots par défaut en cas d'erreur
            return listOf("lakou", "soley", "lapli", "lanmè", "zwazo", "chat", "chen", "tab")
        }
        
        return filterByDifficulty(words, difficulty).shuffled().take(10)
    }
    
    private fun isValidForDifficulty(word: String, difficulty: ScrambleDifficulty): Boolean {
        return when (difficulty) {
            ScrambleDifficulty.EASY -> word.length in 4..5
            ScrambleDifficulty.NORMAL -> word.length in 5..7
            ScrambleDifficulty.HARD -> word.length in 7..10
        }
    }
    
    private fun filterByDifficulty(words: List<String>, difficulty: ScrambleDifficulty): List<String> {
        return words.filter { isValidForDifficulty(it, difficulty) }
    }
    
    fun getTimeForDifficulty(difficulty: ScrambleDifficulty): Int {
        return when (difficulty) {
            ScrambleDifficulty.EASY -> 45
            ScrambleDifficulty.NORMAL -> 30
            ScrambleDifficulty.HARD -> 20
        }
    }
    
    /**
     * Mélange les lettres d'un mot
     */
    fun scrambleWord(word: String): List<Char> {
        var scrambled = word.toList().shuffled()
        // S'assurer que le mot mélangé est différent de l'original
        var attempts = 0
        while (scrambled.joinToString("") == word && attempts < 10) {
            scrambled = word.toList().shuffled()
            attempts++
        }
        return scrambled
    }
}
