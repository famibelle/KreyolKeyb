package com.example.kreyolkeyboard

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject

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

        // Poids d'une utilisation personnelle dans le score d'une suggestion.
        // Calibré sur la distribution réelle du dictionnaire : entre candidats
        // partageant un préfixe, l'écart de fréquence corpus se compte en dizaines
        // ("bon" 97 contre "bonjou" 17). À 5 points par utilisation, il faut une
        // quinzaine de frappes pour faire remonter le mot réellement employé.
        //
        // La valeur a été divisée par dix en même temps que les fréquences du
        // dictionnaire, ramenées à leur vraie échelle par la correction du cumul
        // dans creer_dictionnaire() : elles étaient jusque-là gonflées d'un facteur
        // douze par les exécutions successives du pipeline.
        private const val USAGE_WEIGHT = 5.0

        // Plafond du bonus d'usage, exprimé en nombre d'utilisations comptées.
        // Deux garanties : le bonus maximal (100) reste très en dessous du poids
        // d'une correction orthographique (100 000), donc une correction gagne
        // toujours ; et il ne dépasse pas le 99e centile des fréquences corpus (88),
        // donc les mots hyper-fréquents (ka 1800, pa 664) ne sont pas délogés par un
        // mot personnel rarement pertinent.
        private const val MAX_COUNTED_USAGES = 20

        // Nombre de correspondances par préfixe retenues avant scoring. Le
        // dictionnaire est parcouru par fréquence corpus décroissante : une fenêtre
        // trop étroite écarterait un mot rare dans le corpus mais très utilisé par
        // l'utilisateur avant même que son bonus d'usage puisse jouer.
        private const val CANDIDATE_POOL_SIZE = 40

        /**
         * Poids d'une édition en moins dans le score d'une correction : une
         * correction à une faute passe toujours avant une à deux.
         */
        internal const val EDIT_DISTANCE_WEIGHT = 100_000.0

        /**
         * Multiplicateur de fréquence d'une correction qui ne fait que
         * remplacer des lettres par leurs voisines sur le clavier
         * ([VoisinsClavier]) : le doigt tombé à côté, première faute de frappe
         * sur téléphone (v23.0.0, repris de LuxKeyb 37.2.0).
         *
         * Multiplicateur et non bonus fixe, mesuré côté luxembourgeois : un
         * bonus fixe supérieur à toute fréquence faisait bien passer la touche
         * voisine devant, mais aussi devant une lettre oubliée, faute tout
         * aussi courante, sur un mot vingt fois plus fréquent.
         *
         * La fréquence maximale multipliée doit rester sous
         * [EDIT_DISTANCE_WEIGHT] : `VoisinsClavierTest` le vérifie sur le
         * dictionnaire livré (« ka », 1 800 × 5 = 9 000).
         */
        internal const val VOISINAGE_FACTEUR = 5

        /**
         * Pas de correction pour un mot que le français reconnaît en entier
         * (v23.0.0, repris de LuxKeyb) : la rangée bleue le propose déjà, et
         * trois mots kréyòl sans rapport n'ont rien à faire au-dessus. Le
         * contrôle porte sur le mot entier : tant qu'on tape « maiso », le
         * français ne le reconnaît pas encore et le repli garde sa place.
         */
        internal fun devraitCorriger(input: String, estFrancaisConnu: Boolean): Boolean {
            if (input.length < 3) return false
            return !estFrancaisConnu
        }
        
        /**
         * Applique le pattern de casse (majuscules/minuscules) de l'input à un mot suggéré
         * Exemples:
         * - input="kaBr", suggestion="kabrit" → "kaBrit"
         * - input="BONJ", suggestion="bonjou" → "BONJOU"
         * - input="Bon", suggestion="bonjou" → "Bonjou"
         */
        internal fun applyCasingPattern(input: String, suggestion: String): String {
            if (input.isEmpty() || suggestion.isEmpty()) return suggestion

            val result = StringBuilder()

            // Cas 1: Tout en majuscules — au moins 2 lettres, sinon une seule
            // majuscule initiale (shift automatique) mettrait toute la
            // suggestion en capitales ("B" → "BÈL" au lieu de "Bèl")
            val letters = input.filter { it.isLetter() }
            if (letters.length >= 2 && letters.all { it.isUpperCase() }) {
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
         * @param usageCount nombre de fois que l'utilisateur a déjà validé ce mot,
         *        0 quand l'usage personnel n'est pas connu (mots français, tests)
         */
        internal fun calculateDictionaryScore(
            word: String,
            input: String,
            frequency: Int,
            levenshteinDistance: Int = 0,
            usageCount: Int = 0,
            parVoisines: Boolean = false
        ): Double {
            var score = frequency.toDouble()

            // Usage personnel : la fréquence corpus dit ce que le kréyòl écrit
            // emploie en général, pas ce que cet utilisateur-ci écrit. Le compteur
            // alimenté par la gamification (CreoleDictionaryWithUsage) corrige ce
            // décalage. Tout reste sur l'appareil : aucune donnée ne sort, et seuls
            // les mots déjà présents au dictionnaire sont comptés.
            score += minOf(usageCount, MAX_COUNTED_USAGES) * USAGE_WEIGHT

            // Corrections orthographiques : la distance prime sur tout le reste.
            // Le poids (100 000) dépasse toute fréquence du dictionnaire (~15 500 max) :
            // une correction à 1 édition bat toujours une correction à 2 éditions,
            // quelle que soit leur fréquence ("mesli" → "mèsi" avant "mésyé")
            if (levenshteinDistance > 0) {
                score += (3 - levenshteinDistance) * EDIT_DISTANCE_WEIGHT
                if (parVoisines) score += frequency.toDouble() * (VOISINAGE_FACTEUR - 1)
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

        /**
         * Choisit la clé à interroger dans le modèle N-gram : le contexte à deux mots
         * ("an ka") s'il est présent, sinon le dernier mot seul ("ka").
         * `internal` (et non private) pour être testable en JVM sans Context.
         *
         * @param hasKey test de présence dans le modèle chargé
         */
        internal fun resolveNgramContext(
            previousWord: String?,
            lastWord: String,
            hasKey: (String) -> Boolean
        ): String {
            val twoWordContext = previousWord?.let { "$it $lastWord" }
            return twoWordContext?.takeIf(hasKey) ?: lastWord
        }

        /**
         * Correspondance EXACTE (insensible aux accents) d'un mot dans une liste de formes
         * déjà normalisées. `internal` (et non private) pour être testable en JVM sans
         * Context — cœur logique de `isKnownWord()`.
         */
        internal fun isWordKnown(word: String, normalizedWords: List<String>): Boolean {
            if (word.isBlank()) return true // ponctuation/chiffres isolés : ne pas souligner
            return normalizedWords.contains(AccentTolerantMatcher.normalize(word))
        }
    }
    
    // Données du moteur kreyòl (existant)
    private var dictionary: List<Pair<String, Int>> = emptyList()
    // Formes normalisées (sans accents) alignées index à index avec `dictionary`,
    // précalculées au chargement pour éviter de normaliser 3600+ mots à chaque frappe
    private var normalizedWords: List<String> = emptyList()
    // Graphies du dictionnaire regroupées par forme sans accent, pour la
    // restauration des accents (AccentRestoration). Construit une fois au
    // chargement : la consulter à la validation d'un mot ne parcourt rien.
    private var accentGroups: Map<String, List<Pair<String, Int>>> = emptyMap()
    // Vrai si le repli des accents allonge une forme du dictionnaire (« ß »,
    // « œ » → deux lettres) : seul cas où le second passage de la correction
    // orthographique peut servir. Aucune aujourd'hui.
    private var dictionnaireQuiSAllonge = false
    // Forme repliée → graphie du dictionnaire la plus fréquente et sa
    // fréquence, pour recoller deux mots tapés sans espace ([coupure]).
    private var parForme: Map<String, Pair<String, Int>> = emptyMap()
    // Incrémenté à chaque nouvelle demande de suggestions : une recherche
    // lancée sous un numéro plus ancien sait qu'elle ne sera pas affichée.
    @Volatile private var generationFrappe = 0
    // Touches voisines de l'AZERTY créole, pour classer les corrections.
    private val voisins = VoisinsClavier.AZERTY
    private var ngramModel: Map<String, List<Map<String, Any>>> = emptyMap()
    private val wordHistory = mutableListOf<String>()

    // Compteur d'utilisations personnelles, fourni par le service IME plutôt
    // qu'instancié ici : le moteur n'a pas à dépendre du paquet gamification, et
    // reste ainsi testable sans Context. Absent (null) tant qu'il n'est pas
    // branché, auquel cas le score se réduit à son ancienne formule.
    private var usageCountProvider: ((String) -> Int)? = null

    /**
     * Branche la source des compteurs d'utilisation personnelle
     * (CreoleDictionaryWithUsage côté service).
     */
    fun setUsageCountProvider(provider: (String) -> Int) {
        usageCountProvider = provider
        Log.d(TAG, "📊 Compteurs d'usage personnel branchés sur le scoring")
    }

    private fun usageCountOf(word: String): Int = usageCountProvider?.invoke(word) ?: 0
    
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
    /**
     * Lecture des actifs. Remplaçable pour faire tourner le moteur hors
     * d'Android : le banc de correction (`BancCorrectionTest`) lit les mêmes
     * fichiers depuis `src/main/assets`.
     */
    internal var ouvrirActif: (String) -> java.io.InputStream = { context.assets.open(it) }

    /** Dictionnaire et n-grammes seuls, sans le français : pour le banc. */
    internal suspend fun chargerKreyol() {
        loadDictionary()
        loadNgramModel()
    }

    /**
     * Ce que la rangée kréyòl afficherait pour [input], calculé sur le fil
     * appelant. Même chemin que [generateBilingualSuggestions], sans les
     * coroutines ni le français : c'est ce que mesure le banc.
     */
    internal fun rangeeKreyol(input: String): List<String> {
        if (input.length < MIN_WORD_LENGTH) return emptyList()
        generationFrappe++
        return getKreyolSuggestions(input).map { it.word }
    }

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

            removeLegacyPersonalDictionary()

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
        generationFrappe++
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
        generationFrappe++
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
            val score = calculateDictionaryScore(word, input, frequency, distance, usageCountOf(word), parVoisines(input, word, distance))
            allKreyol[word] = score.toFloat()
        }
        
        // Ajouter suggestions n-gram avec bonus (uniquement si le mot correspond
        // au préfixe tapé : le n-gramme prédit le mot suivant probable d'après le
        // contexte, ce qui n'a aucun rapport avec ce que l'utilisateur est en train
        // de taper — sans ce filtre, un mot sans aucune correspondance kréyòl
        // (ex. "Ordinateur") affichait quand même 3 mots kréyòl sans rapport)
        ngramMatches
            .filter { it.startsWith(input, ignoreCase = true) }
            .forEach { word ->
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
        generationFrappe++
        suggestionJob = suggestionScope.launch {
            val suggestions = withContext(Dispatchers.Default) {
                val dictionaryMatches = getDictionarySuggestions(input)
                
                // Trier uniquement par score de dictionnaire (fréquence + proximité + distance)
                dictionaryMatches
                    .map { (word, frequency, distance) ->
                        Pair(word, calculateDictionaryScore(word, input, frequency, distance, usageCountOf(word), parVoisines(input, word, distance)))
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
        generationFrappe++
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
     * Efface le dictionnaire personnel laissé par la 10.5.0.
     *
     * Cette version apprenait les mots absents du corpus pour les suggérer. La
     * fonction a été retirée en 10.6.0 : un clavier qui conserve des mots tapés
     * par l'utilisateur, si encadré soit-il, reste un clavier qui conserve ce
     * qu'on écrit. Retirer le code ne suffit pas, il faut aussi effacer ce qui a
     * déjà été écrit sur les appareils l'ayant installée.
     */
    private fun removeLegacyPersonalDictionary() {
        try {
            val legacyFile = java.io.File(context.filesDir, "personal_dict.json")
            if (legacyFile.exists() && legacyFile.delete()) {
                Log.d(TAG, "🧹 Dictionnaire personnel de la 10.5.0 effacé")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Effacement du dictionnaire personnel impossible: ${e.message}", e)
        }
    }

    /**
     * Correspondance EXACTE (insensible aux accents) dans le dictionnaire créole OU
     * français — contrairement à getDictionarySuggestions() qui fait une recherche par
     * préfixe. Utilisé par KreyolSpellCheckerService pour décider si un mot doit être
     * souligné comme faute par le correcteur orthographique système.
     */
    fun isKnownWord(word: String): Boolean {
        if (isWordKnown(word, normalizedWords)) return true
        return ::frenchDictionary.isInitialized && frenchDictionary.containsWord(word)
    }

    /**
     * La graphie accentuée à substituer à [word] tapé sans accent, ou `null` quand
     * il faut le laisser tel quel. Toute la règle, et ses interdits, sont dans
     * [AccentRestoration] ; ce qui revient ici est seulement de lui fournir les
     * graphies du dictionnaire, de dire si le mot est du français, et si la forme
     * sans accent est glosée pour elle-même.
     */
    fun restoreAccents(word: String): String? {
        if (word.length < AccentRestoration.LONGUEUR_MINIMALE) return null
        val groupe = accentGroups[AccentTolerantMatcher.normalize(word)] ?: return null
        val estFrancais = ::frenchDictionary.isInitialized && frenchDictionary.containsWord(word)
        val nueAUnSens = TranslationDictionary.aUnSensPropre(context, word.lowercase())
        return AccentRestoration.choisir(word, groupe, estFrancais, nueAUnSens)
    }

    /**
     * Suggestions de correction pour un mot absent des deux dictionnaires, en réutilisant
     * telle quelle la logique Levenshtein + scoring existante (chantier G) — la casse de
     * `word` est reportée sur chaque suggestion, comme pour la frappe normale.
     */
    fun getSpellingSuggestions(word: String, maxResults: Int = MAX_SUGGESTIONS): List<String> {
        return getSpellCorrectionSuggestions(word)
            .take(maxResults)
            .map { applyCasingPattern(word, it.first) }
    }

    /**
     * Charge le dictionnaire depuis les assets
     */
    private suspend fun loadDictionary() = withContext(Dispatchers.IO) {
        try {
            val jsonString = ouvrirActif("creole_dict.json").bufferedReader().use { it.readText() }
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
            accentGroups = dictionary.indices.groupBy({ normalizedWords[it] }, { dictionary[it] })
            dictionnaireQuiSAllonge = dictionary.indices.any { normalizedWords[it].length != dictionary[it].first.length }
            parForme = HashMap<String, Pair<String, Int>>().also { table ->
                // `dictionary` est trié par fréquence décroissante : la première
                // graphie vue pour une forme est la plus fréquente.
                dictionary.forEachIndexed { i, entree -> table.putIfAbsent(normalizedWords[i], entree) }
            }

            if (suggestionListener != null) withContext(Dispatchers.Main) {
                suggestionListener?.onDictionaryLoaded(dictionary.size)
            }
            
            Log.d(TAG, "Dictionnaire chargé: ${dictionary.size} mots")

        } catch (e: Exception) {
            // Pas seulement IOException : un format inattendu (ex. objet {mot: fréquence}
            // au lieu du tableau [[mot, fréquence], ...] attendu) lève une JSONException,
            // qui n'est pas une IOException et passait donc au travers, laissant le
            // dictionnaire vide sans aucune suggestion ni erreur visible (v10.2.6).
            Log.e(TAG, "Erreur lors du chargement du dictionnaire: ${e.message}", e)
        }
    }
    
    /**
     * Charge le modèle N-gram depuis les assets
     */
    private suspend fun loadNgramModel() = withContext(Dispatchers.IO) {
        Log.d(TAG, "Chargement du modèle N-grams...")
        try {
            val inputStream = ouvrirActif("creole_ngrams.json")
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
            
            if (suggestionListener != null) withContext(Dispatchers.Main) {
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
        // `dictionary` est trié par fréquence décroissante, mais on ne peut plus
        // s'arrêter aux tout premiers matches depuis que l'usage personnel entre
        // dans le score : un mot rare dans le corpus et pourtant très employé par
        // l'utilisateur se trouve loin dans ce classement, et une fenêtre étroite
        // l'écarterait avant même que son bonus puisse jouer.
        // Distance 0 = correspondance par préfixe (pas une correction).
        val normalizedInput = AccentTolerantMatcher.normalize(input)
        val matches = mutableListOf<Triple<String, Int, Int>>()
        for (i in dictionary.indices) {
            if (normalizedWords[i].startsWith(normalizedInput)) {
                matches.add(Triple(dictionary[i].first, dictionary[i].second, 0))
                if (matches.size >= CANDIDATE_POOL_SIZE) break
            }
        }

        // ✨ Si aucune correspondance par préfixe, essayer la correction
        // orthographique, sauf si le mot tapé est du français reconnu.
        // La recherche s'abandonne dès qu'une frappe plus récente l'a rendue
        // inutile : `cancel()` sur la coroutine ne l'interromprait pas, la
        // boucle ne suspend jamais, et en tapant vite les balayages périmés
        // s'empilaient sur le processeur derrière celui qui compte.
        if (matches.isEmpty() && devraitCorriger(input, isFrenchWord(input))) {
            val frappe = generationFrappe
            val corrections = getSpellCorrectionSuggestions(input) { generationFrappe != frappe }
            val coupe = coupure(input) ?: return corrections
            return listOf(coupe) + corrections
        }

        // Une faute peut aussi être le début d'un autre mot : « rout » pour
        // « tout » commence « routyé ». Sur le banc de correction, c'était le
        // cas de 13 % des fautes de touche voisine et de 20 % sur les mots de
        // 3 ou 4 lettres, toutes ratées, parce que la correction ne se
        // cherchait que faute de complétion. Quand ce qui est tapé n'est pas
        // un mot connu, on cherche donc aussi le mot à une faute près, et il
        // passe en tête s'il pèse assez face à la meilleure complétion. Une
        // seule faute, pas deux : c'est le cas courant, et la recherche reste
        // deux fois moins chère.
        if (input.length >= longueurCorrectionSurPrefixe && devraitCorriger(input, isFrenchWord(input)) && !isKnownWord(input)) {
            val frappe = generationFrappe
            correctionSurPrefixe(input, normalizedInput, matches) { generationFrappe != frappe }
                ?.let { matches.add(0, it) }
        }

        return matches
    }

    private fun isFrenchWord(word: String): Boolean =
        ::frenchDictionary.isInitialized && frenchDictionary.containsWord(word)

    private fun parVoisines(input: String, word: String, distance: Int): Boolean =
        distance > 0 && voisins.parVoisines(input, word)

    /**
     * Deux mots tapés sans l'espace entre eux (v23.0.0, repris de LuxKeyb
     * 37.2.0).
     *
     * On essaie chaque coupure de ce qui est tapé et on ne retient que celles
     * que **le modèle de mots suivants atteste** : le second morceau doit
     * figurer parmi les mots qui suivent le premier dans le corpus. Second
     * cas, pour les suites que le modèle n'a pas retenues : l'un des deux
     * morceaux est un **mot-outil** (forme d'au moins [frequenceMotOutil]
     * occurrences : ka, an, sé, pou, nou…) et l'autre un mot connu. Le banc
     * de correction mesure à part combien de mots justes absents du
     * dictionnaire se feraient couper à tort.
     *
     * Les listes de terminaisons et de particules verbales de LuxKeyb ne sont
     * pas reprises : elles répondent aux composés soudés et aux verbes
     * séparables du luxembourgeois, que le kréyòl n'a pas.
     *
     * Le poids rendu estime le nombre de fois où la paire apparaît dans le
     * corpus, pour se comparer aux fréquences des corrections : probabilité
     * du second mot après le premier fois la fréquence du premier, ou, sans
     * n-gramme, le produit des fréquences rapporté au corpus. La coupure se
     * classe comme une correction à une faute, l'espace manquante. Les
     * morceaux d'une lettre sont exclus, et une paire que le dictionnaire
     * écrit avec un trait d'union n'est pas coupée.
     */
    private fun coupure(input: String): Triple<String, Int, Int>? {
        if (input.length < 4 || ngramModel.isEmpty()) return null
        val bas = input.lowercase()
        var meilleure: String? = null
        var meilleurPoids = 0.0
        for (i in 2..bas.length - 2) {
            val gauche = bas.substring(0, i)
            val droite = bas.substring(i)
            // L'orthographe GEREC lie au trait d'union ce que le dictionnaire
            // connaît ainsi (« kay-la ») : la correction le propose déjà, une
            // espace à la place serait une faute.
            if (AccentTolerantMatcher.normalize("$gauche-$droite") in parForme) continue
            val suite = ngramModel[gauche]?.firstOrNull { (it["word"] as? String)?.lowercase() == droite }
            val probabilite = (suite?.get("probability") as? Number)?.toDouble()
            val (poids, mot) = if (suite != null && probabilite != null) {
                probabilite * frequenceCorpus(gauche) to suite["word"] as String
            } else {
                val g = parForme[AccentTolerantMatcher.normalize(gauche)] ?: continue
                val d = parForme[AccentTolerantMatcher.normalize(droite)] ?: continue
                if (g.second < frequenceMotOutil && d.second < frequenceMotOutil) continue
                g.second.toDouble() * d.second / occurrencesCorpus to d.first
            }
            if (poids > meilleurPoids) {
                meilleurPoids = poids
                meilleure = input.substring(0, i) + " " + mot
            }
        }
        return meilleure?.let { Triple(it, meilleurPoids.toInt().coerceAtLeast(1), 1) }
    }

    /** Somme des fréquences du dictionnaire, pour estimer celle d'une paire. */
    private val occurrencesCorpus: Double by lazy { dictionary.sumOf { it.second.toDouble() }.coerceAtLeast(1.0) }

    /** Fréquence corpus d'une forme, 0 si absente. */
    private fun frequenceCorpus(motBas: String): Int =
        parForme[AccentTolerantMatcher.normalize(motBas)]?.second ?: 0

    /**
     * Seuil d'un mot-outil pour [coupure]. Réglé sur le banc de correction le
     * 2026-10-19 (mots collés recollés en tête / mots justes inconnus coupés
     * à tort) : 100 donne 67,3 / 1,3 %, 50 donne 71,5 / 1,7 %, 25 donne
     * 74,1 / 2,0 %, 10 donne 75,9 / 2,7 %. Le dictionnaire kréyòl compte
     * 36 502 occurrences, cent fois moins que le luxembourgeois : son seuil
     * de 1 000 ne se transpose pas.
     */
    internal var frequenceMotOutil = 50

    /**
     * Seuil de [correctionSurPrefixe] : la correction passe en tête quand sa
     * fréquence (multipliée pour une touche voisine) vaut au moins tant de
     * fois celle de la meilleure complétion.
     *
     * Réglé sur le banc de correction (`BancCorrectionTest`) le 2026-10-19,
     * 1re place / dans les 3 :
     *
     * ```
     * seuil   touche voisine   voisine, 3-4 lettres   mot juste en cours de frappe
     * jamais  78,4 / 85,7      67,0 / 77,2            72,3 / 89,9
     *  16     80,9 / 88,2      71,2 / 81,3            63,0 / 89,0
     *  64     80,2 / 87,5      70,2 / 80,4            69,2 / 89,6
     * 128     79,5 / 86,8      69,6 / 79,8            71,2 / 89,7   ← retenu
     * 256     79,3 / 86,6      69,0 / 79,2            71,9 / 89,8
     * ```
     *
     * LuxKeyb a retenu 64. Ici le mot en cours de frappe, qui revient à chaque
     * touche, y perdait trois points en tête pour un gain de moins de deux sur
     * les fautes : à 128, il n'en perd qu'un.
     */
    internal var ratioCorrectionSurPrefixe = 128.0

    /** Longueur minimale de ce qui est tapé pour [correctionSurPrefixe]. */
    internal var longueurCorrectionSurPrefixe = 3

    private fun correctionSurPrefixe(
        input: String,
        normalizedInput: String,
        completions: List<Triple<String, Int, Int>>,
        estAnnule: () -> Boolean
    ): Triple<String, Int, Int>? {
        val meilleureCompletion = completions.maxOf { it.second }
        val multiplicateur = { mot: String -> if (voisins.parVoisines(input, mot)) VOISINAGE_FACTEUR else 1 }
        val correction = LevenshteinDistance.findClosestMatchesNormalized(
            input = input,
            dictionary = dictionary,
            normalizedWords = normalizedWords,
            normalizer = { str -> AccentTolerantMatcher.normalize(str) },
            maxDistance = 1,
            maxResults = 10,
            estAnnule = estAnnule,
            multiplicateur = multiplicateur
        ).firstOrNull { !AccentTolerantMatcher.normalize(it.first).startsWith(normalizedInput) }
            ?: return null
        val poids = correction.second.toDouble() * multiplicateur(correction.first)
        return if (poids >= ratioCorrectionSurPrefixe * meilleureCompletion) correction else null
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
    private fun getSpellCorrectionSuggestions(
        input: String,
        estAnnule: () -> Boolean = { false }
    ): List<Triple<String, Int, Int>> {
        if (input.length < 3) return emptyList()
        val multiplicateur = { mot: String -> if (voisins.parVoisines(input, mot)) VOISINAGE_FACTEUR else 1 }
        
        // Essayer d'abord avec la normalisation des accents (combinaison puissante)
        val normalizedMatches = LevenshteinDistance.findClosestMatchesNormalized(
            input = input,
            dictionary = dictionary,
            normalizedWords = normalizedWords,
            normalizer = { str -> AccentTolerantMatcher.normalize(str) },
            maxDistance = 2,
            maxResults = MAX_SUGGESTIONS,
            estAnnule = estAnnule,
            multiplicateur = multiplicateur
        )
        
        // Si on trouve des correspondances normalisées, les retourner
        if (normalizedMatches.isNotEmpty()) {
            Log.d(TAG, "✓ Correction orthographique (normalisée) pour '$input': ${normalizedMatches.take(3).map { it.first }}")
            return normalizedMatches
        }
        
        // Le passage sans normalisation ne peut rien trouver de plus. La
        // normalisation remplace chaque caractère par un seul autre (é → e) :
        // elle ne peut que rapprocher deux mots, jamais les éloigner, donc
        // rien n'est à distance 2 en brut qui ne le soit aussi une fois replié.
        // Seuls « ß » et « œ », qui deviennent deux lettres, échappent à ce
        // raisonnement, et le dictionnaire n'en contient aucun. Ce second
        // balayage complet doublait le temps de toutes les frappes sans
        // correction possible : un nom propre, un mot étranger, un mot en cours.
        val saisieQuiSAllonge = AccentTolerantMatcher.normalize(input).length != input.length
        if (!saisieQuiSAllonge && !dictionnaireQuiSAllonge) return emptyList()

        val directMatches = LevenshteinDistance.findClosestMatches(
            input = input,
            dictionary = dictionary,
            maxDistance = 2,
            maxResults = MAX_SUGGESTIONS,
            estAnnule = estAnnule,
            multiplicateur = multiplicateur
        )
        
        if (directMatches.isNotEmpty()) {
            Log.d(TAG, "✓ Correction orthographique (directe) pour '$input': ${directMatches.take(3).map { it.first }}")
        }
        
        return directMatches
    }
    
    /**
     * Obtient les suggestions depuis le modèle N-gram (optimisé pour mode contextuel)
     *
     * Le modèle porte deux familles de clés : un mot ("ka") et deux mots séparés par
     * une espace ("an ka"). Le contexte à deux mots est essayé en premier car il est
     * nettement plus précis, avec repli sur un seul mot quand la paire est absente.
     * Aucune collision n'est possible entre les deux familles, la tokenisation du
     * pipeline excluant les espaces.
     *
     * L'historique retenait déjà cinq mots (MAX_WORD_HISTORY) alors que seul le
     * dernier servait ; le modèle n'exportait par ailleurs que des bigrammes, les
     * trigrammes étant calculés puis jetés côté pipeline.
     */
    private fun getNgramSuggestions(): List<String> {
        val lastWord = wordHistory.lastOrNull() ?: return emptyList()
        val previousWord = wordHistory.getOrNull(wordHistory.size - 2)

        val context = resolveNgramContext(previousWord, lastWord) { ngramModel.containsKey(it) }
        val ngramList = ngramModel[context] ?: return emptyList()

        Log.d(TAG, "N-gram contexte retenu: '$context'")

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
            val score = calculateDictionaryScore(word, input, frequency, distance, usageCountOf(word), parVoisines(input, word, distance))
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
