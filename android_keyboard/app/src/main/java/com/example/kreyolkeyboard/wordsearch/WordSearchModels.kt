package com.example.kreyolkeyboard.wordsearch

import android.content.Context
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Classes de données pour le système de mots mêlés
 */

data class WordSearchPuzzle(
    val theme: String,
    val grid: Array<CharArray>,
    val words: List<WordSearchWord>,
    val gridSize: Int,
    val difficulty: WordSearchDifficulty
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as WordSearchPuzzle

        if (theme != other.theme) return false
        if (!grid.contentDeepEquals(other.grid)) return false
        if (words != other.words) return false
        if (gridSize != other.gridSize) return false
        if (difficulty != other.difficulty) return false

        return true
    }

    override fun hashCode(): Int {
        var result = theme.hashCode()
        result = 31 * result + grid.contentDeepHashCode()
        result = 31 * result + words.hashCode()
        result = 31 * result + gridSize
        result = 31 * result + difficulty.hashCode()
        return result
    }
}

data class WordSearchWord(
    val word: String,
    val startRow: Int,
    val startCol: Int,
    val direction: WordDirection,
    var isFound: Boolean = false
)

enum class WordDirection {
    HORIZONTAL,
    VERTICAL,
    DIAGONAL_DOWN_RIGHT,
    DIAGONAL_DOWN_LEFT,
    HORIZONTAL_REVERSE,
    VERTICAL_REVERSE,
    DIAGONAL_UP_RIGHT,
    DIAGONAL_UP_LEFT
}

enum class WordSearchDifficulty {
    EASY,      // 6x6, 4 mots, horizontal/vertical seulement
    NORMAL,    // 8x8, 6 mots, + diagonales
    HARD,      // 10x10, 8 mots, toutes directions + mots inversés
    EXPERT     // 12x12, 10 mots, mots qui se croisent
}

/**
 * Chargement des mots créoles depuis le dictionnaire
 */
object WordSearchThemes {
    
    // Cache pour les mots chargés depuis le dictionnaire
    private var cachedWords: List<String>? = null
    
    /**
     * Charge tous les mots du dictionnaire créole (3 à 8 lettres)
     */
    fun getThemeWords(theme: String, context: Context): List<String> {
        // Utiliser le cache si disponible
        cachedWords?.let { return it.shuffled() }
        
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
                
                // Ne garder que les mots de 3 à 8 lettres (pour tenir dans la grille 8x8)
                if (word.length in 3..8) {
                    words.add(word)
                }
            }
            
            // Mettre en cache
            cachedWords = words
            
        } catch (e: Exception) {
            e.printStackTrace()
            // En cas d'erreur, retourner quelques mots par défaut
            return listOf("mwen", "nou", "yo", "kay", "lakou", "soley", "lapli", "van")
        }
        
        return words.shuffled()
    }
}