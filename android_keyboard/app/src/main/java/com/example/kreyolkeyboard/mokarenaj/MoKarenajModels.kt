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

    /** Mots de cinq lettres retenus pour le tirage, avec leur fréquence au corpus. */
    private var cachedPool: List<String>? = null

    /**
     * Fréquence minimale au corpus pour qu'un mot non traduit entre au tirage.
     *
     * La réserve n'était faite que des mots glosés depuis la 12.0.0, soit 103
     * mots : à une partie par jour, la répétition devenait sensible en trois
     * mois. Le seuil de 5 la porte à 220 mots tout en gardant 45 % de parties
     * qui se terminent sur une traduction. C'est le point où la courbe
     * s'infléchit : en dessous, on ne ramène plus que des formes que le corpus
     * n'atteste qu'une ou deux fois, et la part traduite s'effondre (30 % à
     * un seuil de 3, 12 % sans seuil du tout).
     */
    private const val FREQUENCE_MINIMALE = 5

    /**
     * Noms propres du corpus, écartés du tirage.
     *
     * Le kréyòl ne capitalise que les noms propres, mais `creole_dict.json` ne
     * garde pas la casse : la détection ne peut pas se faire ici. Cette liste
     * est donc relevée sur le corpus par la règle de `generate_cloze.py`,
     * resserrée pour cet usage : un mot dont **40 % des occurrences portent la
     * majuscule ailleurs qu'en tête de phrase**. Le seuil de 50 % laissait
     * passer `viktò`, personnage d'une pièce de Sonny Rupaire vu 95 fois, dont
     * la plupart des majuscules ouvrent une réplique.
     *
     * La règle ratisse un peu large : `konpè`, `louwa`, `lapen`, `milat` et
     * `trant` sont des noms communs que ce corpus emploie aussi comme noms de
     * personnages. Les perdre coûte cinq mots sur 225 ; laisser passer un nom
     * propre coûte une partie entière.
     *
     * Relevée le 5 septembre 2026 sur 4 878 phrases. À refaire si le corpus
     * grossit nettement, avec la même règle.
     */
    private val NOMS_PROPRES = setOf(
        "adlin", "bastè", "bouko", "cholo", "dèdèt", "frans", "fwans", "féran",
        "konpè", "kévin", "lapen", "louwa", "manzè", "milat", "trant", "vaval",
        "viktò", "zanba"
    )

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
     * La réserve de mots à deviner : ceux dont on peut dire le sens, plus ceux
     * que le corpus emploie assez pour être devinables, les noms propres en
     * moins.
     *
     * Un mot traduit vaut mieux, la partie se terminant sur sa traduction, mais
     * n'en garder que ceux-là laissait 103 mots. Voir [FREQUENCE_MINIMALE].
     *
     * Rien de tout cela ne touche [isValidWord] : une proposition du joueur
     * reste acceptée dès lors qu'elle est au dictionnaire, traduite ou non.
     * Sans cette distinction, le jeu se mettrait à refuser des mots kréyòl
     * corrects.
     */
    private fun pool(context: Context): List<String> {
        cachedPool?.let { return it }

        val traduction = com.example.kreyolkeyboard.TranslationDictionary
        val retenus = mutableListOf<String>()
        try {
            val contenu = context.assets.open("creole_dict.json")
                .bufferedReader().use { it.readText() }
            val tableau = JSONArray(contenu)
            for (i in 0 until tableau.length()) {
                val entree = tableau.getJSONArray(i)
                val mot = entree.getString(0).lowercase()
                if (mot.length != WORD_LENGTH || !mot.all { it.isLetter() }) continue
                if (mot in NOMS_PROPRES) continue
                val frequence = entree.optInt(1, 0)
                if (frequence >= FREQUENCE_MINIMALE ||
                    traduction.estProposable(context, mot)) {
                    retenus.add(mot)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Un dictionnaire illisible ne doit pas rendre le jeu injouable : on
        // retombe sur la liste complète, qui a son propre repli codé en dur.
        val reserve = if (retenus.size >= 50) retenus else loadWords(context)
        cachedPool = reserve
        return reserve
    }

    fun pickRandomWord(context: Context): String = pool(context).random()

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
