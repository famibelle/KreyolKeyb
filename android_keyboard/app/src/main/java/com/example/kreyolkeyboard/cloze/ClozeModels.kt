package com.example.kreyolkeyboard.cloze

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Modèles de données pour le jeu « Fraz a twou » : une phrase kréyòl
 * authentique dont un mot a été retiré, et quatre propositions.
 *
 * L'actif `creole_cloze.json` est produit par `Dictionnaires/generate_cloze.py`,
 * qui choisit le mot masqué et fabrique les leurres à partir du dictionnaire et
 * du modèle n-grammes livrés. Rien n'est recalculé ici : ce fichier ne fait que
 * lire, tirer au sort et mélanger.
 */

enum class ClozeDifficulty(val level: Int, val label: String) {
    FACILE(1, "Facile"),
    NORMALE(2, "Normal"),
    DIFFICILE(3, "Difficile");

    companion object {
        fun fromLevel(level: Int): ClozeDifficulty =
            values().firstOrNull { it.level == level } ?: NORMALE
    }
}

/**
 * Une question. `sentence` porte le marqueur [ClozeData.MARKER] à l'emplacement
 * du trou ; `options` contient la réponse et ses trois leurres, déjà mélangés.
 */
data class ClozeQuestion(
    val sentence: String,
    val answer: String,
    val options: List<String>,
    val difficulty: ClozeDifficulty,
    val source: String
) {
    /** Texte avant le trou, tel qu'il doit être affiché. */
    val before: String get() = sentence.substringBefore(ClozeData.MARKER)

    /** Texte après le trou. */
    val after: String get() = sentence.substringAfter(ClozeData.MARKER)

    /** La phrase telle que son auteur l'a écrite, trou comblé. */
    val completed: String get() = sentence.replace(ClozeData.MARKER, answer)
}

object ClozeData {

    /** Marqueur du trou dans les phrases livrées. Même valeur côté Python. */
    const val MARKER = "___"

    /** Nombre de questions d'une manche. */
    const val QUESTIONS_PER_ROUND = 10

    private const val ASSET = "creole_cloze.json"
    private const val TAG = "ClozeData"

    private var cachedQuestions: List<ClozeQuestion>? = null
    private var cachedAttribution: String? = null

    /**
     * Charge et met en cache toutes les questions livrées.
     *
     * Aucun repli sur un jeu de secours codé en dur, contrairement aux autres
     * jeux : dix phrases écrites à la main donneraient une partie jouable, donc
     * un actif manquant passerait inaperçu jusqu'en production. Ici la liste
     * revient vide et l'écran le dit.
     */
    fun loadQuestions(context: Context): List<ClozeQuestion> {
        cachedQuestions?.let { return it }

        val questions = try {
            val contenu = BufferedReader(
                InputStreamReader(context.assets.open(ASSET))
            ).use { it.readText() }

            val racine = JSONObject(contenu)
            val sources = racine.optJSONArray("sources")
            cachedAttribution = if (sources == null) "" else
                (0 until sources.length()).joinToString("\n") { sources.getString(it) }

            val items = racine.getJSONArray("items")
            val liste = ArrayList<ClozeQuestion>(items.length())
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                val phrase = item.getString("s")
                val reponse = item.getString("a")
                // Une phrase sans marqueur afficherait une question sans trou :
                // mieux vaut la sauter que la montrer telle quelle.
                if (!phrase.contains(MARKER)) continue

                val leurres = item.getJSONArray("d")
                val propositions = ArrayList<String>(leurres.length() + 1)
                propositions.add(reponse)
                for (j in 0 until leurres.length()) {
                    propositions.add(leurres.getString(j))
                }

                liste.add(
                    ClozeQuestion(
                        sentence = phrase,
                        answer = reponse,
                        options = propositions,
                        difficulty = ClozeDifficulty.fromLevel(item.optInt("l", 2)),
                        source = item.optString("src", "")
                    )
                )
            }
            liste
        } catch (e: Exception) {
            Log.e(TAG, "Actif $ASSET illisible: ${e.message}", e)
            emptyList()
        }

        cachedQuestions = questions
        Log.d(TAG, "${questions.size} questions chargées")
        return questions
    }

    /**
     * Tire une manche de [QUESTIONS_PER_ROUND] questions de la difficulté
     * demandée, propositions mélangées.
     *
     * Le mélange se fait ici et non à la génération : l'ordre du fichier place
     * toujours la réponse en premier, et le figer signifierait qu'un joueur
     * revoyant une phrase retrouve la bonne case au même endroit.
     */
    fun newRound(context: Context, difficulty: ClozeDifficulty): List<ClozeQuestion> {
        val disponibles = loadQuestions(context).filter { it.difficulty == difficulty }
        if (disponibles.isEmpty()) return emptyList()
        return disponibles.shuffled()
            .take(QUESTIONS_PER_ROUND)
            .map { it.copy(options = it.options.shuffled()) }
    }

    /** Combien de questions sont livrées pour chaque difficulté. */
    fun countByDifficulty(context: Context): Map<ClozeDifficulty, Int> =
        loadQuestions(context).groupingBy { it.difficulty }.eachCount()

    /**
     * Crédits du corpus, tels qu'ils voyagent dans l'actif lui-même.
     *
     * Les phrases sont des extraits d'œuvres d'auteurs identifiés : la citation
     * n'est pas une politesse. Le jeu affiche ce texte, et la source de chaque
     * phrase sous la phrase elle-même ; ne retirer ni l'un ni l'autre.
     */
    fun attribution(context: Context): String {
        loadQuestions(context)
        return cachedAttribution ?: ""
    }
}
