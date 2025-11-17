package com.example.kreyolkeyboard

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * Moteur de suggestions bilingue pour le clavier créole
 * Gère le dictionnaire kreyòl, les N-grams et le support français
 * 🎯 PRIORITÉ KREYÒL: Français activé seulement à partir de 3 lettres
 * 
 * À la mémoire de mon père, Saint-Ange Corneille Famibelle
 */
class SuggestionEngine(private val context: Context) {
    
    companion object {
        private const val TAG = "SuggestionEngine"
        private const val MAX_SUGGESTIONS = 5  // Augmenté pour bilingue (3 kreyòl + 2 français)
        private const val MAX_WORD_HISTORY = 5
        private const val MIN_WORD_LENGTH = 2
        

    }
    
    // Données du moteur kreyòl (existant)
    private var dictionary: List<Pair<String, Int>> = emptyList()
    private var ngramModel: Map<String, List<Map<String, Any>>> = emptyMap()
    private val wordHistory = mutableListOf<String>()
    
    // 🇫🇷 Support français (nouveau)
    private lateinit var frenchDictionary: FrenchDictionary
    private var bilingualConfig = BilingualConfig()
    private var isBilingualEnabled = false
    
    // Coroutines pour les opérations asynchrones
    private val suggestionScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // Modes de suggestion
    enum class SuggestionMode {
        DICTIONARY,    // Suggestions basées sur le dictionnaire (pendant frappe)
        CONTEXTUAL,    // Prédictions contextuelles N-gram (après espace)
        MIXED         // Mode mixte (comportement original)
    }
    
    private var currentMode = SuggestionMode.MIXED
    
    // Callbacks (étendus pour support bilingue)
    interface SuggestionListener {
        fun onSuggestionsReady(suggestions: List<String>)  // Compatibilité existante
        fun onBilingualSuggestionsReady(suggestions: List<BilingualSuggestion>) // Nouveau bilingue
        fun onDictionaryLoaded(wordCount: Int)
        fun onNgramModelLoaded()
        fun onFrenchDictionaryLoaded(wordCount: Int)  // Nouveau
        fun onModeChanged(newMode: SuggestionMode)
    }
    
    private var suggestionListener: SuggestionListener? = null
    
    fun setSuggestionListener(listener: SuggestionListener) {
        this.suggestionListener = listener
    }
    
    fun getSuggestionListener(): SuggestionListener? {
        return suggestionListener
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
     * Initialise le moteur de suggestions (kreyòl + français)
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🚀 Initialisation du moteur bilingue...")
            
            // 1. Initialiser dictionnaire français d'abord
            frenchDictionary = FrenchDictionary(context)
            
            // 2. Chargement en parallèle de tous les dictionnaires
            val kreyolDictDeferred = async { loadDictionary() }
            val ngramDeferred = async { loadNgramModel() }
            val frenchDictDeferred = async { frenchDictionary.initialize() }
            
            // 3. Attendre que tout soit chargé
            kreyolDictDeferred.await()
            ngramDeferred.await() 
            frenchDictDeferred.await()
            
            Log.d(TAG, "✅ Moteur bilingue initialisé:")
            Log.d(TAG, "   🟢 Kreyòl: ${dictionary.size} mots + ${ngramModel.size} N-grams")
            Log.d(TAG, "   🔵 Français: ${frenchDictionary.getStats()["word_count"]} mots")
            
            // Notifier le chargement du dictionnaire français
            withContext(Dispatchers.Main) {
                val frenchWordCount = frenchDictionary.getStats()["word_count"] as Int
                suggestionListener?.onFrenchDictionaryLoaded(frenchWordCount)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur lors de l'initialisation bilingue: ${e.message}", e)
        }
    }
    
    /**
     * Génère des suggestions pour un texte d'entrée (méthode générale - conservée pour compatibilité)
     */
    fun generateSuggestions(input: String) {
        // 🎯 REDIRECTION: Si mode bilingue activé, utiliser la logique bilingue
        if (isBilingualEnabled) {
            generateBilingualSuggestions(input)
            return
        }
        
        // Logique originale pour rétrocompatibilité
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
     * 🎯 Active le support bilingue Kreyòl + Français
     */
    fun enableBilingualSupport() {
        isBilingualEnabled = true
        Log.d(TAG, "🟢🔵 Support bilingue activé - Dictionnaire français: ${frenchDictionary.getLoadedWordCount()} mots")
    }

    /**
     * 🎯 NOUVELLE MÉTHODE PRINCIPALE: Génère des suggestions bilingues intelligentes
     * Logique: Kreyòl prioritaire, Français à partir de 3 lettres
     */
    fun generateBilingualSuggestions(input: String) {
        if (input.length < MIN_WORD_LENGTH) {
            suggestionListener?.onSuggestionsReady(emptyList())
            suggestionListener?.onBilingualSuggestionsReady(emptyList())
            return
        }
        
        suggestionScope.launch {
            val suggestions = withContext(Dispatchers.Default) {
                createBilingualSuggestions(input)
            }
            
            // Notifier avec les deux formats pour compatibilité
            val simpleWords = suggestions.map { it.word }
            suggestionListener?.onSuggestionsReady(simpleWords)
            suggestionListener?.onBilingualSuggestionsReady(suggestions)
            
            Log.d(TAG, "🎯 Suggestions bilingues pour '$input': ${simpleWords}")
        }
    }
    
    /**
     * Crée les suggestions bilingues selon la stratégie Kreyòl-First
     * 💙 PRIORITÉ ABSOLUE: Détection séquences mémoire pour papa Saint-Ange
     */
    private fun createBilingualSuggestions(input: String): List<BilingualSuggestion> {
        val suggestions = mutableListOf<BilingualSuggestion>()
        

        
        // 1. 🟢 TOUJOURS obtenir suggestions kreyòl (priorité absolue)
        val kreyolSuggestions = getKreyolSuggestions(input)
        
        // 2. 🔵 Obtenir suggestions françaises SEULEMENT si 3+ lettres
        val frenchSuggestions = if (bilingualConfig.shouldActivateFrench(input)) {
            getFrenchSuggestions(input)
        } else {
            Log.d(TAG, "Français désactivé pour '$input' (${input.length} < ${bilingualConfig.frenchActivationThreshold} lettres)")
            emptyList()
        }
        
        // 3. 🎯 Fusion avec priorité kreyòl stricte
        return mergeSuggestionsKreyolFirst(kreyolSuggestions, frenchSuggestions)
    }
    
    /**
     * Obtient les suggestions kreyòl (existant + adapté)
     */
    private fun getKreyolSuggestions(input: String): List<BilingualSuggestion> {
        val dictionaryMatches = getDictionarySuggestions(input)
        val ngramMatches = if (wordHistory.isNotEmpty()) getNgramSuggestions() else emptyList()
        
        // Fusionner dictionnaire + n-grams kreyòl
        val allKreyol = mutableMapOf<String, Float>()
        
        // Ajouter suggestions dictionnaire
        dictionaryMatches.forEach { (word, frequency) ->
            val score = calculateDictionaryScore(word, input, frequency)
            allKreyol[word] = score.toFloat()
        }
        
        // Ajouter suggestions n-gram avec bonus
        ngramMatches.forEach { word ->
            val currentScore = allKreyol[word] ?: 0f
            allKreyol[word] = currentScore + 50f  // Bonus contextuel
        }
        
        // Convertir en BilingualSuggestion et appliquer boost kreyòl
        return allKreyol.entries
            .map { (word, score) ->
                val adjustedScore = bilingualConfig.adjustScoreByLanguage(score, SuggestionLanguage.KREYOL)
                BilingualSuggestion(word, adjustedScore, SuggestionLanguage.KREYOL, SuggestionSource.HYBRID)
            }
            .sortedByDescending { it.score }
            .take(bilingualConfig.maxKreyolSuggestions)
    }
    
    /**
     * Obtient les suggestions françaises (nouveau)
     */
    private fun getFrenchSuggestions(input: String): List<BilingualSuggestion> {
        if (!::frenchDictionary.isInitialized) {
            Log.w(TAG, "Dictionnaire français non initialisé")
            return emptyList()
        }
        
        val frenchWords = frenchDictionary.getSuggestions(input)
        
        return frenchWords.map { word ->
            val frequency = frenchDictionary.getWordFrequency(word)
            val baseScore = calculateDictionaryScore(word, input, frequency)
            val adjustedScore = bilingualConfig.adjustScoreByLanguage(baseScore.toFloat(), SuggestionLanguage.FRENCH)
            
            BilingualSuggestion(word, adjustedScore, SuggestionLanguage.FRENCH, SuggestionSource.DICTIONARY)
        }.sortedByDescending { it.score }
    }
    
    /**
     * 🎯 FUSION KREYÒL-FIRST: Positions 1-3 réservées kreyòl, 4-5 français optionnel
     */
    private fun mergeSuggestionsKreyolFirst(
        kreyolSuggs: List<BilingualSuggestion>,
        frenchSuggs: List<BilingualSuggestion>
    ): List<BilingualSuggestion> {
        
        val result = mutableListOf<BilingualSuggestion>()
        val usedWords = mutableSetOf<String>()
        
        // 1. 🟢 POSITIONS 1-3: Toujours kreyòl d'abord
        kreyolSuggs.take(3).forEach { suggestion ->
            if (!usedWords.contains(suggestion.word.lowercase())) {
                result.add(suggestion)
                usedWords.add(suggestion.word.lowercase())
            }
        }
        
        // 2. 🔵 POSITIONS 4-5: Français si disponible et pertinent
        frenchSuggs.take(2).forEach { suggestion ->
            if (result.size < MAX_SUGGESTIONS && 
                !usedWords.contains(suggestion.word.lowercase())) {
                result.add(suggestion)
                usedWords.add(suggestion.word.lowercase())
            }
        }
        
        // 3. 🟢 COMPLÉTER avec plus de kreyòl si pas assez de français
        kreyolSuggs.drop(3).forEach { suggestion ->
            if (result.size < MAX_SUGGESTIONS && 
                !usedWords.contains(suggestion.word.lowercase())) {
                result.add(suggestion)
                usedWords.add(suggestion.word.lowercase())
            }
        }
        
        Log.d(TAG, "🎯 Fusion finale: ${result.size} suggestions (Kreyòl: ${result.count { it.language == SuggestionLanguage.KREYOL }}, Français: ${result.count { it.language == SuggestionLanguage.FRENCH }})")
        
        return result
    }

    /**
     * Génère des suggestions basées uniquement sur le dictionnaire (mode frappe)
     * Optimisé pour la saisie en temps réel pendant que l'utilisateur tape
     * ⚠️  DEPRECATED: Utiliser generateBilingualSuggestions() à la place
     */
    fun generateDictionarySuggestions(input: String) {
        // 🎯 REDIRECTION: Si mode bilingue activé, utiliser la logique bilingue
        if (isBilingualEnabled) {
            generateBilingualSuggestions(input)
            return
        }
        
        // Logique originale pour rétrocompatibilité
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
            
            // Le fichier JSON est directement {mot: [{word, probability}]}
            // Pas besoin de wrapper "predictions"
            val tempMap = mutableMapOf<String, List<Map<String, Any>>>()
            
            val keys = jsonObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val predictionsArray = jsonObject.getJSONArray(key)
                val predictions = mutableListOf<Map<String, Any>>()
                
                for (i in 0 until predictionsArray.length()) {
                    val predictionObj = predictionsArray.getJSONObject(i)
                    val prediction = mapOf(
                        "word" to predictionObj.getString("word"),
                        "probability" to predictionObj.getDouble("probability")
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
     * Obtient les suggestions depuis le dictionnaire avec support AccentTolerantMatcher
     * 🎯 NOUVELLE FONCTIONNALITÉ: Recherche insensible aux accents + correction orthographique
     */
    private fun getDictionarySuggestions(input: String): List<Pair<String, Int>> {
        if (input.length < MIN_WORD_LENGTH) return emptyList()
        
        // 🎯 Utiliser AccentTolerantMatcher pour recherche insensible aux accents
        val accentTolerantMatches = AccentTolerantMatcher.findAccentTolerantSuggestions(
            input, 
            dictionary, 
            MAX_SUGGESTIONS * 2
        )
        
        // ✨ Si aucune correspondance par préfixe, essayer la correction orthographique
        if (accentTolerantMatches.isEmpty() && input.length >= 3) {
            Log.d(TAG, "🔍 Aucune correspondance par préfixe pour '$input', tentative de correction orthographique...")
            return getSpellCorrectionSuggestions(input)
        }
        
        Log.d(TAG, "🔍 Recherche '$input': ${accentTolerantMatches.size} suggestions trouvées")
        
        return accentTolerantMatches
    }
    
    /**
     * Obtient les suggestions de correction orthographique en utilisant la distance de Levenshtein
     * 🔧 CORRECTION ORTHOGRAPHIQUE: Trouve les mots les plus proches même avec des fautes
     * 
     * Utilisé comme solution de secours lorsque la recherche par préfixe ne retourne rien.
     * Détecte et corrige:
     * - Les lettres mélangées: "bonjo" → "bonjou"
     * - Les fautes d'orthographe: "mesli" → "mèsi"
     * - Les lettres manquantes/en trop: "kreyol" → "kréyòl"
     * 
     * @param input Le mot saisi par l'utilisateur (potentiellement mal orthographié)
     * @return Liste de suggestions triées par pertinence (distance + fréquence)
     */
    private fun getSpellCorrectionSuggestions(input: String): List<Pair<String, Int>> {
        if (input.length < 3) return emptyList()
        
        // Essayer d'abord avec la normalisation des accents (combinaison puissante)
        val normalizedMatches = LevenshteinDistance.findClosestMatchesNormalized(
            input = input,
            dictionary = dictionary,
            normalizer = { str -> AccentTolerantMatcher.normalize(str) },
            maxDistance = 2,
            maxResults = MAX_SUGGESTIONS
        )
        
        // Si on trouve des correspondances normalisées, les retourner
        if (normalizedMatches.isNotEmpty()) {
            Log.d(TAG, "✓ Correction orthographique (normalisée) pour '$input': ${normalizedMatches.take(3).map { it.first }}")
            return normalizedMatches
        }
        
        // Sinon, essayer sans normalisation (peut détecter d'autres types d'erreurs)
        val directMatches = LevenshteinDistance.findClosestMatches(
            input = input,
            dictionary = dictionary,
            maxDistance = 2,
            maxResults = MAX_SUGGESTIONS
        )
        
        if (directMatches.isNotEmpty()) {
            Log.d(TAG, "✓ Correction orthographique (directe) pour '$input': ${directMatches.take(3).map { it.first }}")
        }
        
        return directMatches
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
                        val prob = (ngramEntry["probability"] as? Number)?.toDouble() ?: 0.0
                        
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
                    val prob = (ngramEntry["probability"] as? Number)?.toDouble() ?: 0.0
                    
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
                        val prob = (ngramEntry["probability"] as? Number)?.toDouble() ?: 0.0
                        
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
     * ✨ Amélioration: Prend en compte la distance de Levenshtein pour les corrections orthographiques
     */
    private fun calculateDictionaryScore(
        word: String, 
        input: String, 
        frequency: Int,
        levenshteinDistance: Int = 0
    ): Double {
        var score = frequency.toDouble()
        
        // Bonus si le mot commence exactement par l'input (correspondance par préfixe)
        if (word.startsWith(input, ignoreCase = true)) {
            score += 50.0  // Augmenté pour favoriser les correspondances par préfixe
        }
        
        // Bonus pour les corrections orthographiques (basé sur la distance de Levenshtein)
        if (levenshteinDistance > 0) {
            // Plus la distance est petite, meilleur est le match
            // Distance 1 (1 lettre de différence) = +30 points
            // Distance 2 (2 lettres de différence) = +15 points
            val correctionBonus = (3 - levenshteinDistance) * 15.0
            score += correctionBonus
            Log.d(TAG, "📝 Correction '$input' → '$word' (distance=$levenshteinDistance, bonus=$correctionBonus)")
        }
        
        // Bonus pour les mots courts (plus faciles à taper)
        if (word.length <= 6) {
            score += 10.0
        }
        
        // Malus pour les mots très longs
        if (word.length > 12) {
            score -= 10.0
        }
        
        // Bonus pour les mots avec accents (encourage l'apprentissage de l'orthographe correcte)
        if (AccentTolerantMatcher.hasAccents(word)) {
            score += 5.0
        }
        
        return score
    }
    
    /**
     * 🔧 Configuration du mode bilingue
     */
    fun setBilingualConfig(config: BilingualConfig) {
        bilingualConfig = config
        Log.d(TAG, "Configuration bilingue mise à jour: français activé=${config.enableFrenchSupport}, seuil=${config.frenchActivationThreshold}")
    }
    
    fun getBilingualConfig(): BilingualConfig = bilingualConfig
    
    /**
     * Active/désactive le support français
     */
    fun setFrenchSupport(enabled: Boolean) {
        bilingualConfig = bilingualConfig.copy(enableFrenchSupport = enabled)
        Log.d(TAG, "Support français: $enabled")
    }
    
    /**
     * Active/désactive le mode Kreyòl uniquement
     */
    fun setKreyolOnlyMode(kreyolOnly: Boolean) {
        bilingualConfig = bilingualConfig.copy(kreyolOnlyMode = kreyolOnly)
        Log.d(TAG, "Mode Kreyòl seul: $kreyolOnly")
    }
    
    /**
     * Définit le seuil d'activation du français (nombre de lettres)
     */
    fun setFrenchActivationThreshold(threshold: Int) {
        bilingualConfig = bilingualConfig.copy(frenchActivationThreshold = threshold)
        Log.d(TAG, "Seuil activation français: $threshold lettres")
    }
    
    /**
     * Obtient les statistiques du moteur bilingue
     */
    fun getBilingualStats(): Map<String, Any> {
        val frenchStats = if (::frenchDictionary.isInitialized) {
            frenchDictionary.getStats()
        } else {
            mapOf("loaded" to false, "word_count" to 0)
        }
        
        return mapOf(
            "kreyol_words" to dictionary.size,
            "kreyol_ngrams" to ngramModel.size,
            "french_loaded" to (frenchStats["loaded"] as Boolean),
            "french_words" to (frenchStats["word_count"] as Int),
            "config" to mapOf(
                "french_support" to bilingualConfig.enableFrenchSupport,
                "activation_threshold" to bilingualConfig.frenchActivationThreshold,
                "kreyol_only" to bilingualConfig.kreyolOnlyMode
            )
        )
    }

    /**
     * Nettoie les ressources (kreyòl + français)
     */
    fun cleanup() {
        suggestionScope.cancel()
        dictionary = emptyList()
        ngramModel = emptyMap()
        wordHistory.clear()
        
        // Nettoyer ressources françaises
        if (::frenchDictionary.isInitialized) {
            frenchDictionary.cleanup()
        }
        
        suggestionListener = null
        Log.d(TAG, "Moteur bilingue nettoyé")
    }
}
