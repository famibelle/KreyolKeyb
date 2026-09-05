package com.example.kreyolkeyboard.mokarenaj

import android.content.Context
import android.graphics.Color
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Modèles de données pour le jeu "Mo an Karénaj"
 */

enum class LetterState {
    EMPTY, ABSENT, PRESENT, CORRECT
}

fun LetterState.color(): Int = when (this) {
    LetterState.CORRECT -> Color.parseColor("#4CAF50")
    LetterState.PRESENT -> Color.parseColor("#FFC107")
    LetterState.ABSENT -> Color.parseColor("#9E9E9E")
    LetterState.EMPTY -> Color.parseColor("#E0E0E0")
}

data class MoKarenajRow(
    val letters: List<Char?>,
    val states: List<LetterState>
)

object MoKarenajData {

    const val WORD_LENGTH = 5
    const val MAX_ATTEMPTS = 6

    private var cachedWords: List<String>? = null

    /**
     * Charge et met en cache les mots de 5 lettres (lettres accentuées créoles autorisées,
     * mots composés avec tiret exclus) depuis le dictionnaire.
     */
    fun loadWords(context: Context): List<String> {
        cachedWords?.let { return it }

        val words = mutableListOf<String>()
        try {
            val inputStream = context.assets.open("creole_dict.json")
            val reader = BufferedReader(InputStreamReader(inputStream))
            val jsonContent = reader.readText()
            reader.close()

            val jsonArray = JSONArray(jsonContent)
            for (i in 0 until jsonArray.length()) {
                val word = jsonArray.getJSONArray(i).getString(0).lowercase()
                if (word.length == WORD_LENGTH && word.all { it.isLetter() }) {
                    words.add(word)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return listOf("lakou", "manjé", "solèy", "lapli", "kaptè")
        }

        cachedWords = words
        return words
    }

    /**
     * Tire le mot à deviner parmi ceux dont on peut dire le sens : la partie se
     * termine sur sa traduction, et un mot sans glose la ferait finir sur rien.
     *
     * Le filtre ne s'applique qu'ici, jamais à [isValidWord] : une proposition
     * du joueur doit rester acceptée dès lors qu'elle est au dictionnaire, même
     * si aucune source ne la glose. Sur les 882 mots de cinq lettres du
     * dictionnaire, 93 portent une glose.
     */
    fun pickRandomWord(context: Context): String {
        val words = com.example.kreyolkeyboard.TranslationDictionary
            .filtrerMotsTraduits(context, loadWords(context))
        return words.random()
    }

    fun isValidWord(context: Context, word: String): Boolean {
        return loadWords(context).contains(word.lowercase())
    }

    /**
     * Évalue une proposition par rapport au mot cible, à la façon du jeu Mo an Karénaj :
     * deux passes pour gérer correctement les lettres répétées.
     */
    fun evaluateGuess(target: String, guess: String): List<LetterState> {
        val targetLower = target.lowercase()
        val guessLower = guess.lowercase()
        val n = targetLower.length
        val states = MutableList(n) { LetterState.ABSENT }
        val targetUsed = BooleanArray(n)
        val guessMatched = BooleanArray(n)

        for (i in 0 until n) {
            if (guessLower[i] == targetLower[i]) {
                states[i] = LetterState.CORRECT
                targetUsed[i] = true
                guessMatched[i] = true
            }
        }

        for (i in 0 until n) {
            if (guessMatched[i]) continue
            for (j in 0 until n) {
                if (!targetUsed[j] && guessLower[i] == targetLower[j]) {
                    states[i] = LetterState.PRESENT
                    targetUsed[j] = true
                    break
                }
            }
        }

        return states
    }
}
