package com.example.kreyolkeyboard

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * Moteur de suggestions pour le clavier créole
 * Gère le dictionnaire kreyòl et les N-grams
 * 
 * À la mémoire de mon père, Saint-Ange Corneille Famibelle
 */
class SuggestionEngine(private val context: Context) {
    
    companion object {
        private const val TAG = "SuggestionEngine"
        private const val MAX_SUGGESTIONS = 3
        private const val MAX_WORD_HISTORY = 5
        private const val MIN_WORD_LENGTH = 2
    }
    
    // Données du moteur kreyòl
    private var dictionary: List<Pair<String, Int>> = emptyList()
    private var ngramModel: Map<String, List<Map<String, Any>>> = emptyMap()
    private val wordHistory = mutableListOf<String>()
    
    // Coroutines pour les opérations asynchrones
    private val suggestionScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // Modes de suggestion
    enum class SuggestionMode {
        DICTIONARY,    // Suggestions basées sur le dictionnaire (pendant frappe)
        CONTEXTUAL,    // Prédictions contextuelles N-gram (après espace)
        MIXED         // Mode mixte (comportement original)
    }
    
    private var currentMode = SuggestionMode.MIXED
    
    // Callbacks
    interface SuggestionListener {
        fun onSuggestionsReady(suggestions: List<String>)
        fun onDictionaryLoaded(wordCount: Int)
        fun onNgramModelLoaded()
        fun onModeChanged(newMode: SuggestionMode)
    }
    
    private var suggestionListener: SuggestionListener? = null
    
    fun setSuggestionListener(listener: SuggestionListener) {
        this.suggestionListener = listener
    }
    
    /**
     * Change le mode de suggestion
     */
    fun setSuggestionMode(mode: SuggestionMode) {
        if (currentMode != mode) {
            Log.d(TAG, "Changement de mode: $currentMode -> $mode")
            currentMode = mode
            suggestionListener?.onModeChanged(mode)
        }
    }
    
    /**
     * Obtient le mode actuel
     */
    fun getCurrentMode(): SuggestionMode = currentMode
    
    /**
     * Bascule automatiquement vers le mode approprié selon le contexte
     */
    fun switchToAppropriateMode(isTyping: Boolean) {
        val targetMode = if (isTyping) SuggestionMode.DICTIONARY else SuggestionMode.CONTEXTUAL
        setSuggestionMode(targetMode)
    }
    
    /**
     * Initialise le moteur de suggestions
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🚀 Initialisation du moteur Kreyòl...")
            
            // Chargement en parallèle
            val kreyolDictDeferred = async { loadDictionary() }
            val ngramDeferred = async { loadNgramModel() }
            
            // Attendre que tout soit chargé
            kreyolDictDeferred.await()
            ngramDeferred.await()
            
            Log.d(TAG, "✅ Moteur Kreyòl initialisé:")
            Log.d(TAG, "   🟢 Kreyòl: ${dictionary.size} mots + ${ngramModel.size} N-grams")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur lors de l'initialisation: ${e.message}", e)
        }
    }
    
    /**
     * Génère des suggestions pour un texte d'entrée
     */
    fun generateSuggestions(input: String) {
        if (input.length < MIN_WORD_LENGTH) {
            suggestionListener?.onSuggestionsReady(emptyList())
            return
        }
        
        suggestionScope.launch {
            val suggestions = withContext(Dispatchers.Default) {
                val dictionarySuggestions = getDictionarySuggestions(input)
                val ngramSuggestions = getNgramSuggestions()
                
                // Fusion et déduplication des suggestions
                mergeAndRankSuggestions(dictionarySuggestions, ngramSuggestions, input)
            }
            
            suggestionListener?.onSuggestionsReady(suggestions)
        }
    }

    /**
     * Génère des suggestions basées uniquement sur le dictionnaire (mode frappe)
     * Optimisé pour la saisie en temps réel pendant que l'utilisateur tape
     */
    fun generateDictionarySuggestions(input: String) {
        if (input.length < MIN_WORD_LENGTH) {
            suggestionListener?.onSuggestionsReady(emptyList())
            return
        }
        
        suggestionScope.launch {
            val suggestions = withContext(Dispatchers.Default) {
                val dictionaryMatches = getDictionarySuggestions(input)
                
                // Trier uniquement par score de dictionnaire (fréquence + proximité)
                dictionaryMatches
                    .map { (word, frequency) -> 
                        Pair(word, calculateDictionaryScore(word, input, frequency))
                    }
                    .sortedByDescending { it.second }
                    .take(MAX_SUGGESTIONS)
                    .map { it.first }
            }
            
            Log.d(TAG, "Suggestions dictionnaire: $suggestions")
            suggestionListener?.onSuggestionsReady(suggestions)
        }
    }
    
    /**
     * Génère des prédictions contextuelles basées sur les N-grams (mode prédiction)
     * Utilisé après qu'un mot soit complété pour prédire le mot suivant
     */
    fun generateContextualSuggestions() {
        suggestionScope.launch {
            val predictions = withContext(Dispatchers.Default) {
                if (wordHistory.isEmpty() || ngramModel.isEmpty()) {
                    emptyList()
                } else {
                    getNgramSuggestions()
                }
            }
            
            Log.d(TAG, "Prédictions contextuelles: $predictions")
            suggestionListener?.onSuggestionsReady(predictions)
        }
    }
    
    /**
     * Ajoute un mot à l'historique pour les N-grams
     */
    fun addWordToHistory(word: String) {
        val cleanWord = word.lowercase().trim()
        if (cleanWord.isNotEmpty() && cleanWord.length >= MIN_WORD_LENGTH) {
            wordHistory.add(cleanWord)
            
            // Maintenir l'historique à une taille raisonnable
            if (wordHistory.size > MAX_WORD_HISTORY) {
                wordHistory.removeAt(0)
            }
            
            Log.d(TAG, "Mot ajouté à l'historique: $cleanWord")
        }
    }
    
    /**
     * Efface l'historique des mots
     */
    fun clearHistory() {
        wordHistory.clear()
    }
    
    /**
     * Ajoute un mot au dictionnaire personnel
     */
    suspend fun addWordToDictionary(word: String, frequency: Int = 1) = withContext(Dispatchers.IO) {
        try {
            // Vérifier si le mot existe déjà
            val existingWord = dictionary.find { it.first.equals(word, ignoreCase = true) }
            
            if (existingWord == null) {
                val newWord = Pair(word.lowercase(), frequency)
                dictionary = (dictionary + newWord).sortedByDescending { it.second }
                
                Log.d(TAG, "Mot ajouté au dictionnaire: $word")
            } else {
                Log.d(TAG, "Mot déjà présent: $word")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de l'ajout du mot: ${e.message}", e)
        }
    }
    
    /**
     * Charge le dictionnaire depuis les assets
     */
    private suspend fun loadDictionary() = withContext(Dispatchers.IO) {
        try {
            val jsonString = context.assets.open("creole_dict.json").bufferedReader().use { it.readText() }
            val wordsArray = JSONArray(jsonString)
            
            val loadedDictionary = mutableListOf<Pair<String, Int>>()
            
            for (i in 0 until wordsArray.length()) {
                val wordArray = wordsArray.getJSONArray(i)
                val word = wordArray.getString(0).lowercase()
                val frequency = wordArray.optInt(1, 1)
                loadedDictionary.add(Pair(word, frequency))
            }
            
            // Trier par fréquence décroissante
            dictionary = loadedDictionary.sortedByDescending { it.second }
            
            withContext(Dispatchers.Main) {
                suggestionListener?.onDictionaryLoaded(dictionary.size)
            }
            
            Log.d(TAG, "Dictionnaire chargé: ${dictionary.size} mots")
            
        } catch (e: IOException) {
            Log.e(TAG, "Erreur lors du chargement du dictionnaire: ${e.message}", e)
        }
    }
    
    /**
     * Charge le modèle N-gram depuis les assets
     */
    private suspend fun loadNgramModel() = withContext(Dispatchers.IO) {
        Log.d(TAG, "Chargement du modèle N-grams...")
        try {
            val inputStream = context.assets.open("creole_ngrams.json")
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            inputStream.close()

            val jsonObject = JSONObject(jsonString)
            val predictionsObject = jsonObject.getJSONObject("predictions")
            
            val tempMap = mutableMapOf<String, List<Map<String, Any>>>()
            
            val keys = predictionsObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val predictionsArray = predictionsObject.getJSONArray(key)
                val predictions = mutableListOf<Map<String, Any>>()
                
                for (i in 0 until predictionsArray.length()) {
                    val predictionObj = predictionsArray.getJSONObject(i)
                    val prediction = mapOf(
                        "word" to predictionObj.getString("word"),
                        "prob" to predictionObj.getDouble("prob")
                    )
                    predictions.add(prediction)
                }
                
                tempMap[key] = predictions
            }
            
            ngramModel = tempMap.toMap()
            
            Log.d(TAG, "Modèle N-grams chargé avec ${ngramModel.size} entrées")
            
            withContext(Dispatchers.Main) {
                suggestionListener?.onNgramModelLoaded()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors du chargement des N-grams", e)
        }
    }
    
    /**
     * Obtient les suggestions depuis le dictionnaire
     */
    private fun getDictionarySuggestions(input: String): List<Pair<String, Int>> {
        val inputLower = input.lowercase()
        
        return dictionary
            .filter { it.first.startsWith(inputLower) }
            .take(MAX_SUGGESTIONS * 2) // Prendre plus pour avoir des options après fusion
    }
    
    /**
     * Obtient les suggestions depuis le modèle N-gram (optimisé pour mode contextuel)
     */
    private fun getNgramSuggestions(): List<String> {
        if (wordHistory.isEmpty() || ngramModel.isEmpty()) {
            Log.d(TAG, "Pas de suggestions N-gram: historique vide ou modèle non chargé")
            return emptyList()
        }
        
        val suggestions = mutableListOf<Pair<String, Double>>()
        
        try {
            // Stratégie 1: Essayer avec les 2 derniers mots (bigram) - plus précis
            if (wordHistory.size >= 2) {
                val bigram = "${wordHistory[wordHistory.size - 2]} ${wordHistory.last()}"
                
                if (ngramModel.containsKey(bigram)) {
                    val ngramList = ngramModel[bigram] ?: emptyList()
                    ngramList.forEach { ngramEntry ->
                        val word = ngramEntry["word"] as? String
                        val prob = (ngramEntry["prob"] as? Number)?.toDouble() ?: 0.0
                        
                        if (word != null && suggestions.none { it.first == word }) {
                            suggestions.add(Pair(word, prob + 0.2)) // Bonus pour bigram
                        }
                    }
                }
            }
            
            // Stratégie 2: Essayer avec le dernier mot seulement (unigram)
            val lastWord = wordHistory.lastOrNull()
            if (lastWord != null && ngramModel.containsKey(lastWord)) {
                
                val ngramList = ngramModel[lastWord] ?: emptyList()
                ngramList.forEach { ngramEntry ->
                    val word = ngramEntry["word"] as? String
                    val prob = (ngramEntry["prob"] as? Number)?.toDouble() ?: 0.0
                    
                    if (word != null && suggestions.none { it.first == word }) {
                        suggestions.add(Pair(word, prob))
                    }
                }
            }
            
            // Stratégie 3: Si on a 3+ mots, essayer trigram
            if (wordHistory.size >= 3 && suggestions.size < MAX_SUGGESTIONS) {
                val trigram = "${wordHistory[wordHistory.size - 3]} ${wordHistory[wordHistory.size - 2]} ${wordHistory.last()}"
                
                if (ngramModel.containsKey(trigram)) {
                    val ngramList = ngramModel[trigram] ?: emptyList()
                    ngramList.forEach { ngramEntry ->
                        val word = ngramEntry["word"] as? String
                        val prob = (ngramEntry["prob"] as? Number)?.toDouble() ?: 0.0
                        
                        if (word != null && suggestions.none { it.first == word }) {
                            suggestions.add(Pair(word, prob + 0.4)) // Bonus maximal pour trigram
                        }
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.w(TAG, "Erreur lors de la génération des suggestions N-gram: ${e.message}")
        }
        
        // Trier par probabilité décroissante et retourner les meilleures
        return suggestions
            .sortedByDescending { it.second }
            .take(MAX_SUGGESTIONS)
            .map { it.first }
    }
    
    /**
     * Fusionne et classe les suggestions par pertinence
     */
    private fun mergeAndRankSuggestions(
        dictionarySuggestions: List<Pair<String, Int>>,
        ngramSuggestions: List<String>,
        input: String
    ): List<String> {
        val allSuggestions = mutableMapOf<String, Double>()
        
        // Ajouter les suggestions du dictionnaire avec score basé sur la fréquence et la position
        dictionarySuggestions.forEach { (word, frequency) ->
            val score = calculateDictionaryScore(word, input, frequency)
            allSuggestions[word] = score
        }
        
        // Ajouter les suggestions N-gram avec un bonus de contexte
        ngramSuggestions.forEach { word ->
            val currentScore = allSuggestions[word] ?: 0.0
            val ngramBonus = 50.0 // Bonus pour les suggestions contextuelles
            allSuggestions[word] = currentScore + ngramBonus
        }
        
        // Trier par score et retourner les meilleures
        return allSuggestions
            .entries
            .sortedByDescending { it.value }
            .take(MAX_SUGGESTIONS)
            .map { it.key }
    }
    
    /**
     * Calcule un score de pertinence pour une suggestion du dictionnaire
     */
    private fun calculateDictionaryScore(word: String, input: String, frequency: Int): Double {
        var score = frequency.toDouble()
        
        // Bonus si le mot commence exactement par l'input
        if (word.startsWith(input, ignoreCase = true)) {
            score += 20.0
        }
        
        // Bonus pour les mots courts (plus faciles à taper)
        if (word.length <= 6) {
            score += 5.0
        }
        
        // Malus pour les mots très longs
        if (word.length > 12) {
            score -= 10.0
        }
        
        return score
    }
    
    /**
     * Nettoie les ressources
     */
    fun cleanup() {
        suggestionScope.cancel()
        dictionary = emptyList()
        ngramModel = emptyMap()
        wordHistory.clear()
        suggestionListener = null
        Log.d(TAG, "Moteur de suggestions nettoyé")
    }
}
