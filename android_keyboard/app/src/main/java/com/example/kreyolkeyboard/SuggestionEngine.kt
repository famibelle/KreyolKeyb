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
        private const val MIN_WORD_LENGTH = 1  // Le kréyòl a des mots très fréquents dès 1-2 lettres (ka, an, sé)
        
        /**
         * Applique le pattern de casse (majuscules/minuscules) de l'input à un mot suggéré
         * Exemples:
         * - input="kaBr", suggestion="kabrit" → "kaBrit"
         * - input="BONJ", suggestion="bonjou" → "BONJOU"
         * - input="Bon", suggestion="bonjou" → "Bonjou"
         */
        private fun applyCasingPattern(input: String, suggestion: String): String {
            if (input.isEmpty() || suggestion.isEmpty()) return suggestion
            
            val result = StringBuilder()
            
            // Cas 1: Tout en majuscules
            if (input.all { it.isUpperCase() || !it.isLetter() }) {
                return suggestion.uppercase()
            }
            
            // Cas 2: Première lettre majuscule seulement
            if (input.length >= 1 && input[0].isUpperCase() && 
                input.drop(1).all { it.isLowerCase() || !it.isLetter() }) {
                return suggestion.replaceFirstChar { it.uppercase() }
            }
            
            // Cas 3: Pattern mixte - appliquer caractère par caractère
            for (i in suggestion.indices) {
                if (i < input.length) {
                    val inputChar = input[i]
                    val suggestionChar = suggestion[i]
                    
                    result.append(
                        when {
                            inputChar.isUpperCase() -> suggestionChar.uppercase()
                            inputChar.isLowerCase() -> suggestionChar.lowercase()
                            else -> suggestionChar
                        }
                    )
                } else {
                    // Au-delà de la longueur de l'input, garder la casse originale
                    result.append(suggestion[i])
                }
            }
            
            return result.toString()
        }

        /**
         * Calcule un score de pertinence pour une suggestion du dictionnaire
         * `internal` (et non private) pour être testable en JVM sans Context
         *
         * @param levenshteinDistance 0 pour une correspondance par préfixe ;
         *        > 0 pour une correction orthographique (distance d'édition)
         */
        internal fun calculateDictionaryScore(
            word: String,
            input: String,
            frequency: Int,
            levenshteinDistance: Int = 0
        ): Double {
            var score = frequency.toDouble()

            // Corrections orthographiques : la distance prime sur tout le reste.
            // Le poids (100 000) dépasse toute fréquence du dictionnaire (~15 500 max) :
            // une correction à 1 édition bat toujours une correction à 2 éditions,
            // quelle que soit leur fréquence ("mesli" → "mèsi" avant "mésyé")
            if (levenshteinDistance > 0) {
                score += (3 - levenshteinDistance) * 100_000.0
            }

            // Bonus si le mot commence par l'input, comparaison insensible aux accents :
            // taper "fe" doit favoriser "fè" autant que "fenmen", sinon les graphies
            // créoles correctes sont systématiquement déclassées
            if (AccentTolerantMatcher.startsWith(input, word)) {
                score += 50.0  // Augmenté pour favoriser les correspondances par préfixe
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
    }
    
    // Données du moteur kreyòl (existant)
    private var dictionary: List<Pair<String, Int>> = emptyList()
    // Formes normalisées (sans accents) alignées index à index avec `dictionary`,
    // précalculées au chargement pour éviter de normaliser 3600+ mots à chaque frappe
    private var normalizedWords: List<String> = emptyList()
    private var ngramModel: Map<String, List<Map<String, Any>>> = emptyMap()
    private val wordHistory = mutableListOf<String>()
    
    // 🇫🇷 Support français (nouveau)
    private lateinit var frenchDictionary: FrenchDictionary
    private var bilingualConfig = BilingualConfig()
    private var isBilingualEnabled = false
    
    // Coroutines pour les opérations asynchrones
    private val suggestionScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Job de la génération en cours : annulé à chaque nouvelle frappe pour
    // qu'un calcul lent (ex: Levenshtein) ne puisse pas écraser un résultat plus récent
    private var suggestionJob: Job? = null
    
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
        
        suggestionJob?.cancel()
        suggestionJob = suggestionScope.launch {
            val suggestions = withContext(Dispatchers.Default) {
                val dictionarySuggestions = getDictionarySuggestions(input)
                val ngramSuggestions = getNgramSuggestions()

                // Fusion et déduplication des suggestions
                mergeAndRankSuggestions(dictionarySuggestions, ngramSuggestions, input)
            }
            
            // Appliquer la casse de l'input aux suggestions
            val casedSuggestions = suggestions.map { applyCasingPattern(input, it) }
            
            suggestionListener?.onSuggestionsReady(casedSuggestions)
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
        
        suggestionJob?.cancel()
        suggestionJob = suggestionScope.launch {
            val suggestions = withContext(Dispatchers.Default) {
                createBilingualSuggestions(input)
            }
            
            // Appliquer la casse de l'input aux suggestions
            val casedSuggestions = suggestions.map { suggestion ->
                suggestion.copy(word = applyCasingPattern(input, suggestion.word))
            }
            
            // Notifier avec les deux formats pour compatibilité
            val simpleWords = casedSuggestions.map { it.word }
            suggestionListener?.onSuggestionsReady(simpleWords)
            suggestionListener?.onBilingualSuggestionsReady(casedSuggestions)
            
            Log.d(TAG, "🎯 Suggestions bilingues pour '$input': ${simpleWords}")
        }
    }
    
    /**
     * Crée les suggestions bilingues selon la stratégie Kreyòl-First
     * 💙 PRIORITÉ ABSOLUE: Détection séquences mémoire pour papa Saint-Ange
     */
    private fun createBilingualSuggestions(input: String): List<BilingualSuggestion> {
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
        dictionaryMatches.forEach { (word, frequency, distance) ->
            val score = calculateDictionaryScore(word, input, frequency, distance)
            allKreyol[word] = score.toFloat()
        }
        
        // Ajouter suggestions n-gram avec bonus
        ngramMatches.forEach { word ->
            val currentScore = allKreyol[word] ?: 0f
            allKreyol[word] = currentScore + 50f  // Bonus contextuel
        }
        
        // Convertir en BilingualSuggestion et appliquer boost kreyòl + casse
        return allKreyol.entries
            .map { (word, score) ->
                val casedWord = applyCasingPattern(input, word)
                val adjustedScore = bilingualConfig.adjustScoreByLanguage(score, SuggestionLanguage.KREYOL)
                BilingualSuggestion(casedWord, adjustedScore, SuggestionLanguage.KREYOL, SuggestionSource.HYBRID)
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
            val casedWord = applyCasingPattern(input, word)
            val frequency = frenchDictionary.getWordFrequency(word)
            val baseScore = calculateDictionaryScore(word, input, frequency)
            val adjustedScore = bilingualConfig.adjustScoreByLanguage(baseScore.toFloat(), SuggestionLanguage.FRENCH)
            
            BilingualSuggestion(casedWord, adjustedScore, SuggestionLanguage.FRENCH, SuggestionSource.DICTIONARY)
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
        
        suggestionJob?.cancel()
        suggestionJob = suggestionScope.launch {
            val suggestions = withContext(Dispatchers.Default) {
                val dictionaryMatches = getDictionarySuggestions(input)
                
                // Trier uniquement par score de dictionnaire (fréquence + proximité + distance)
                dictionaryMatches
                    .map { (word, frequency, distance) ->
                        Pair(word, calculateDictionaryScore(word, input, frequency, distance))
                    }
                    .sortedByDescending { it.second }
                    .take(MAX_SUGGESTIONS)
                    .map { applyCasingPattern(input, it.first) }
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
        suggestionJob?.cancel()
        suggestionJob = suggestionScope.launch {
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
                normalizedWords = dictionary.map { AccentTolerantMatcher.normalize(it.first) }

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
            normalizedWords = dictionary.map { AccentTolerantMatcher.normalize(it.first) }

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
     * Obtient les suggestions depuis le dictionnaire (recherche par préfixe insensible aux accents)
     * avec correction orthographique en secours
     */
    private fun getDictionarySuggestions(input: String): List<Triple<String, Int, Int>> {
        if (input.length < MIN_WORD_LENGTH) return emptyList()

        // Recherche préfixe sur les formes normalisées précalculées.
        // `dictionary` est trié par fréquence décroissante : les premiers matches
        // sont les meilleurs, on peut s'arrêter dès qu'on en a assez.
        // Distance 0 = correspondance par préfixe (pas une correction).
        val normalizedInput = AccentTolerantMatcher.normalize(input)
        val matches = mutableListOf<Triple<String, Int, Int>>()
        for (i in dictionary.indices) {
            if (normalizedWords[i].startsWith(normalizedInput)) {
                matches.add(Triple(dictionary[i].first, dictionary[i].second, 0))
                if (matches.size >= MAX_SUGGESTIONS * 2) break
            }
        }

        // ✨ Si aucune correspondance par préfixe, essayer la correction orthographique
        if (matches.isEmpty() && input.length >= 3) {
            return getSpellCorrectionSuggestions(input)
        }

        return matches
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
     * @return Liste de (mot, fréquence, distance) triée par pertinence (distance + fréquence)
     */
    private fun getSpellCorrectionSuggestions(input: String): List<Triple<String, Int, Int>> {
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
     * Le modèle ne contient que des clés unigrammes (un mot → mots suivants probables) :
     * la prédiction se base donc sur le dernier mot saisi uniquement
     */
    private fun getNgramSuggestions(): List<String> {
        val lastWord = wordHistory.lastOrNull() ?: return emptyList()
        val ngramList = ngramModel[lastWord] ?: return emptyList()

        val suggestions = mutableListOf<Pair<String, Double>>()
        ngramList.forEach { ngramEntry ->
            val word = ngramEntry["word"] as? String
            val prob = (ngramEntry["probability"] as? Number)?.toDouble() ?: 0.0

            if (word != null && suggestions.none { it.first == word }) {
                suggestions.add(Pair(word, prob))
            }
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
        dictionarySuggestions: List<Triple<String, Int, Int>>,
        ngramSuggestions: List<String>,
        input: String
    ): List<String> {
        val allSuggestions = mutableMapOf<String, Double>()

        // Ajouter les suggestions du dictionnaire avec score basé sur la fréquence et la position
        dictionarySuggestions.forEach { (word, frequency, distance) ->
            val casedWord = applyCasingPattern(input, word)
            val score = calculateDictionaryScore(word, input, frequency, distance)
            allSuggestions[casedWord] = score
        }
        
        // Ajouter les suggestions N-gram avec un bonus de contexte
        ngramSuggestions.forEach { word ->
            val casedWord = applyCasingPattern(input, word)
            val currentScore = allSuggestions[casedWord] ?: 0.0
            val ngramBonus = 50.0 // Bonus pour les suggestions contextuelles
            allSuggestions[casedWord] = currentScore + ngramBonus
        }
        
        // Trier par score et retourner les meilleures
        return allSuggestions
            .entries
            .sortedByDescending { it.value }
            .take(MAX_SUGGESTIONS)
            .map { it.key }
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
        normalizedWords = emptyList()
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
