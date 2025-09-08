package com.example.kreyolkeyboard

import android.inputmethodservice.InputMethodService
import android.view.View
import android.widget.TextView
import android.graphics.Color
import android.view.Gravity
import android.util.Log
import android.widget.LinearLayout
import android.widget.Button
import android.view.ViewGroup
import android.provider.UserDictionary
import android.content.ContentValues
import android.view.inputmethod.EditorInfo
import android.widget.HorizontalScrollView
import org.json.JSONArray
import java.io.IOException
import android.view.MotionEvent
import android.os.Handler
import android.os.Looper
import android.widget.PopupWindow
import android.view.LayoutInflater
import android.widget.FrameLayout

class KreyolInputMethodService : InputMethodService() {
    
    private val TAG = "KreyolIME-Potomitan™"
    
    // 🇬🇵 PALETTE COULEURS GUADELOUPE 🇬🇵
    companion object {
        // Couleurs principales - Palette "Pur Guadeloupe"
        const val BLEU_CARAIBE = "#0080FF"        // Bleu des eaux caribéennes
        const val JAUNE_SOLEIL = "#FFD700"        // Jaune du soleil tropical
        const val VERT_CANNE = "#228B22"          // Vert de la canne à sucre
        const val NOIR_VOLCANIQUE = "#1C1C1C"    // Noir des racines volcaniques
        const val BLANC_CORAL = "#F8F8FF"        // Blanc du corail
        
        // Couleurs secondaires pour nuances
        const val BLEU_LAGON = "#87CEEB"          // Bleu plus clair du lagon
        const val ORANGE_COUCHER = "#FF8C00"     // Orange du coucher de soleil
        const val ROUGE_HIBISCUS = "#DC143C"     // Rouge de l'hibiscus
        const val BEIGE_SABLE = "#F5F5DC"        // Beige du sable fin
    }
    
    private var dictionary: List<Pair<String, Int>> = emptyList()
    private var ngramModel: Map<String, List<Map<String, Any>>> = emptyMap()
    private var wordHistory = mutableListOf<String>() // Historique des mots pour N-grams
    private var currentWord = ""
    private var suggestionsView: LinearLayout? = null
    private var suggestionsViewId: Int = View.NO_ID
    
    // Variables pour l'appui long
    private val longPressHandler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null
    private var isLongPressTriggered = false
    
    // Gestion des majuscules/minuscules
    private var isCapitalMode = false
    private var isCapsLock = false
    private var keyboardButtons = mutableListOf<TextView>()
    private var isUpdatingKeyboard = false
    private var isNumericMode = false
    private var mainKeyboardLayout: LinearLayout? = null
    
    // Mapping des accents pour appui long
    private val accentMap = mapOf(
        "A" to arrayOf("à", "á", "â", "ä", "ã", "å", "æ"),
        "E" to arrayOf("é", "è", "ê", "ë"),
        "I" to arrayOf("í", "ì", "î", "ï"),
        "O" to arrayOf("ó", "ò", "ô", "ö", "õ", "ø"),
        "U" to arrayOf("ú", "ù", "û", "ü"),
        "C" to arrayOf("ç", "ć", "č"),
        "N" to arrayOf("ñ"),
        "Y" to arrayOf("ý", "ÿ")
    )
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "=== KREYOL IME SERVICE onCreate() APPELÉ - Potomitan™ ===")
        
        try {
            Log.d(TAG, "Initialisation du dictionnaire...")
            dictionary = emptyList()
            currentWord = "" // Reset du mot actuel
            loadDictionary() // Activer le chargement du dictionnaire (inclut populatePersonalDictionary)
            loadNgramModel() // Charger le modèle N-grams
            
            Log.d(TAG, "Variables initialisées et dictionnaire chargé")
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de l'initialisation", e)
        }
    }
    
    private fun updateSuggestions(input: String) {
        Log.d(TAG, "=== updateSuggestions appelée avec input: '$input' ===")
        Log.d(TAG, "suggestionsView est null: ${suggestionsView == null}")
        Log.d(TAG, "isNumericMode: $isNumericMode")
        
        // Vérifier la validité de suggestionsView
        if (suggestionsView == null) {
            Log.e(TAG, "suggestionsView est null ! Tentative de récupération...")
            // Essayer de récupérer la vue depuis le layout principal
            val currentView = mainKeyboardLayout
            if (currentView != null && currentView.childCount > 1) {
                val suggestionsContainer = currentView.getChildAt(1) as? HorizontalScrollView
                suggestionsView = suggestionsContainer?.getChildAt(0) as? LinearLayout
                if (suggestionsView != null) {
                    suggestionsViewId = suggestionsView!!.id
                    Log.d(TAG, "suggestionsView récupérée avec succès !")
                }
            }
            
            if (suggestionsView == null) {
                Log.e(TAG, "Impossible de récupérer suggestionsView, abandon de la mise à jour")
                return
            }
        }
        
        // En mode numérique, vider les suggestions
        if (isNumericMode) {
            suggestionsView?.removeAllViews()
            Log.d(TAG, "Mode numérique - suggestions vidées")
            return
        }
        
        suggestionsView?.removeAllViews()
        
        try {
            val suggestions = if (input.isEmpty()) {
                // Quand pas d'input, utiliser les N-grams basés sur l'historique + mots fréquents
                getNgramSuggestions() + dictionary.take(6).map { it.first }
            } else {
                // SYSTÈME PRÉDICTIF AMÉLIORÉ: Combiner toutes les sources intelligemment
                
                // 1. Dictionnaire principal (déjà trié par fréquence) - PRIORITÉ 1
                val dictionarySuggestions = dictionary.filter { 
                    it.first.startsWith(input.lowercase(), ignoreCase = true) 
                }.take(4).map { it.first }
                
                // 2. Chercher dans TOUTES les clés du modèle N-grams qui commencent par le préfixe
                val ngramKeysWithPrefix = ngramModel.keys.filter { 
                    it.startsWith(input.lowercase(), ignoreCase = true)
                }.take(3)
                
                // 3. N-grams contextuelles basées sur l'historique
                val ngramSuggestions = getNgramSuggestions().filter {
                    it.startsWith(input.lowercase(), ignoreCase = true)
                }.take(2)
                
                // Combiner avec priorité : dictionnaire > mots N-grams > prédictions contextuelles
                val combined = (dictionarySuggestions + ngramKeysWithPrefix + ngramSuggestions)
                    .distinct()
                    .take(8) // Plus de suggestions pour un meilleur choix
                
                Log.d(TAG, "Suggestions détaillées pour '$input':")
                Log.d(TAG, "  - Dictionnaire: ${dictionarySuggestions.joinToString(", ")}")
                Log.d(TAG, "  - N-grams clés: ${ngramKeysWithPrefix.joinToString(", ")}")
                Log.d(TAG, "  - N-grams contexte: ${ngramSuggestions.joinToString(", ")}")
                
                combined
            }
            
            Log.d(TAG, "Suggestions trouvées pour '$input': ${suggestions.joinToString(", ")}")
            Log.d(TAG, "Nombre de suggestions: ${suggestions.size}")
            
            // Ajouter un indicateur Potomitan™ discret si pas de suggestions
            if (suggestions.isEmpty() && input.isEmpty()) {
                val brandButton = Button(this).apply {
                    text = "Potomitan™"
                    setBackgroundResource(android.R.color.transparent)
                    setTextColor(getColor(R.color.bleu_caraibe))
                    textSize = resources.getDimension(R.dimen.text_size_watermark) / resources.displayMetrics.density
                    alpha = 0.6f
                    setTypeface(null, android.graphics.Typeface.ITALIC)
                    setPadding(12, 8, 12, 8)
                    isClickable = false
                    isFocusable = false
                    
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    ).apply {
                        marginEnd = 16
                    }
                }
                suggestionsView?.addView(brandButton)
            }
            
            suggestions.forEach { suggestion ->
                val chipButton = Button(this).apply {
                    text = suggestion
                    
                    // � Style "chips" moderne selon le brief
                    setBackgroundResource(R.drawable.suggestion_chip_background)
                    setTextColor(getColor(R.color.blanc_coral))
                    textSize = resources.getDimension(R.dimen.text_size_suggestion) / resources.displayMetrics.density
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    elevation = resources.getDimension(R.dimen.suggestion_elevation)
                    
                    // Padding optimisé pour les chips
                    val chipPaddingH = resources.getDimensionPixelSize(R.dimen.suggestion_padding_horizontal)
                    val chipPaddingV = resources.getDimensionPixelSize(R.dimen.suggestion_padding_vertical)
                    setPadding(chipPaddingH, chipPaddingV, chipPaddingH, chipPaddingV)
                    
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    ).apply {
                        marginEnd = resources.getDimensionPixelSize(R.dimen.suggestion_margin)
                    }
                    
                    // Animation moderne pour les chips
                    addTouchAnimation(this)
                    
                    setOnClickListener {
                        Log.d(TAG, "Suggestion sélectionnée: $suggestion")
                        // Remplacer le mot actuel par la suggestion
                        val inputConnection = currentInputConnection
                        if (inputConnection != null && currentWord.isNotEmpty()) {
                            // Supprimer le mot partiel
                            inputConnection.deleteSurroundingText(currentWord.length, 0)
                        }
                        // Insérer la suggestion complète
                        inputConnection?.commitText("$suggestion ", 1)
                        addWordToHistory(suggestion) // Ajouter à l'historique N-grams
                        
                        // Ajouter automatiquement au dictionnaire personnel pour éviter soulignement rouge
                        try {
                            UserDictionary.Words.addWord(
                                this@KreyolInputMethodService,
                                suggestion.lowercase(),
                                255,
                                UserDictionary.Words.LOCALE_TYPE_ALL
                            )
                        } catch (e: Exception) {
                            Log.w(TAG, "Impossible d'ajouter '$suggestion' au dictionnaire: ${e.message}")
                        }
                        
                        currentWord = ""
                        updateSuggestions("")
                    }
                }
                
                suggestionsView?.addView(chipButton)
                Log.d(TAG, "Chip de suggestion ajouté: $suggestion")
            }
            
            Log.d(TAG, "=== updateSuggestions terminée avec succès ===")
            
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de la mise à jour des suggestions", e)
        }
    }
    
    private fun getNgramSuggestions(): List<String> {
        // Obtenir les prédictions basées sur le dernier mot tapé
        if (wordHistory.isEmpty()) {
            return emptyList()
        }
        
        val lastWord = wordHistory.lastOrNull()?.lowercase()
        if (lastWord == null) {
            return emptyList()
        }
        
        val predictions = ngramModel[lastWord] ?: return emptyList()
        
        // Extraire les mots prédits et les trier par probabilité
        val suggestedWords = predictions.map { prediction ->
            prediction["word"] as String
        }.take(3) // Limiter à 3 suggestions N-gram
        
        Log.d(TAG, "N-gram suggestions pour '$lastWord': ${suggestedWords.joinToString(", ")}")
        return suggestedWords
    }
    
    private fun addWordToHistory(word: String) {
        if (word.isNotBlank() && word.length > 1) {
            wordHistory.add(word.lowercase())
            // Garder seulement les 5 derniers mots pour performance
            if (wordHistory.size > 5) {
                wordHistory.removeAt(0)
            }
            Log.d(TAG, "Historique des mots: ${wordHistory.joinToString(" → ")}")
        }
    }

    private fun loadDictionary() {
        try {
            Log.d(TAG, "Chargement du dictionnaire créole enrichi...")
            val inputStream = assets.open("creole_dict_enrichi.json")
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            
            val jsonArray = JSONArray(jsonString)
            val tempList = mutableListOf<Pair<String, Int>>()
            
            for (i in 0 until jsonArray.length()) {
                val entry = jsonArray.getJSONArray(i)
                val word = entry.getString(0)
                val frequency = entry.getInt(1)
                tempList.add(Pair(word, frequency))
            }
            
            dictionary = tempList.sortedByDescending { it.second } // Trier par fréquence
            Log.d(TAG, "Dictionnaire chargé: ${dictionary.size} mots")
            
            // Peupler le dictionnaire personnel Android avec les mots créoles
            populatePersonalDictionary()
            
        } catch (e: IOException) {
            Log.e(TAG, "Erreur lors du chargement du dictionnaire", e)
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors du parsing du dictionnaire", e)
        }
    }
    
    private fun loadNgramModel() {
        Log.d(TAG, "Chargement du modèle N-grams...")
        try {
            val inputStream = assets.open("creole_ngrams.json")
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            inputStream.close()
            
            val jsonObject = org.json.JSONObject(jsonString)
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
            
            ngramModel = tempMap
            Log.d(TAG, "Modèle N-grams chargé: ${ngramModel.size} mots avec prédictions")
            
        } catch (e: IOException) {
            Log.e(TAG, "Erreur lors du chargement du modèle N-grams", e)
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors du parsing du modèle N-grams", e)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service onDestroy() appelé !")
    // Nettoyage basique
    dismissAccentPopup()
    longPressRunnable = null
    keyboardButtons.clear()
    suggestionsView = null
    mainKeyboardLayout = null
    }
    
    override fun onStartInput(info: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInput(info, restarting)
        Log.d(TAG, "=== KREYOL onStartInput appelé - restarting: $restarting ===")
        Log.d(TAG, "EditorInfo: $info")
        
        // SOLUTION RADICALE : Désactiver complètement le spell checking
        info?.let { editorInfo ->
            // Sauvegarder l'inputType original
            val originalInputType = editorInfo.inputType
            
            // Forcer un inputType qui désactive le spell checking
            editorInfo.inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
                android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD // Empêche le spell checking
                
            // Alternative : utiliser TYPE_TEXT_VARIATION_FILTER
            // editorInfo.inputType = android.text.InputType.TYPE_CLASS_TEXT or
            //     android.text.InputType.TYPE_TEXT_VARIATION_FILTER
            
            Log.d(TAG, "InputType modifié de $originalInputType à ${editorInfo.inputType} pour désactiver spell checking")
        }
    }
    
    override fun onStartInputView(info: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        Log.d(TAG, "=== KREYOL onStartInputView appelé - restarting: $restarting ===")
        
        // Vérifier et initialiser suggestionsView si nécessaire
        if (suggestionsView == null) {
            Log.d(TAG, "suggestionsView est null, tentative de récupération depuis la vue existante")
            val currentView = mainKeyboardLayout
            if (currentView != null && suggestionsViewId != View.NO_ID) {
                suggestionsView = currentView.findViewById<LinearLayout>(suggestionsViewId)
                Log.d(TAG, "suggestionsView récupérée par ID: ${suggestionsView != null}")
            }
        }
        
        // Initialiser avec les suggestions de base
        if (suggestionsView != null) {
            Log.d(TAG, "Initialisation des suggestions de base")
            updateSuggestions("")
        } else {
            Log.e(TAG, "Impossible de récupérer suggestionsView !")
        }
        
        // Vérifier si on a une vue
        Log.d(TAG, "Vue d'entrée disponible, clavier devrait être visible")
    }
    
    // Désactiver complètement l'extraction de texte pour éviter le spell checking
    override fun onUpdateExtractingViews(ei: android.view.inputmethod.EditorInfo?) {
        // Ne pas appeler super pour désactiver l'extraction
        Log.d(TAG, "onUpdateExtractingViews - désactivé pour éviter spell checking")
    }
    
    override fun onUpdateExtractingVisibility(ei: android.view.inputmethod.EditorInfo?) {
        // Forcer la vue d'extraction à être invisible
        setExtractViewShown(false)
        Log.d(TAG, "Vue d'extraction forcée invisible pour éviter spell checking")
    }
    
    override fun isExtractViewShown(): Boolean {
        // Toujours retourner false pour désactiver l'extraction
        return false
    }
    
    override fun onFinishInput() {
        super.onFinishInput()
        Log.d(TAG, "onFinishInput appelé")
    }
    
    override fun onEvaluateFullscreenMode(): Boolean {
        Log.d(TAG, "onEvaluateFullscreenMode appelé")
        return false // Désactivé le mode plein écran
    }
    
    override fun onEvaluateInputViewShown(): Boolean {
        Log.d(TAG, "onEvaluateInputViewShown appelé")
        val shouldShow = super.onEvaluateInputViewShown()
        return shouldShow || true // Force l'affichage du clavier ou utilise la logique parent
    }

    override fun onCreateInputView(): View? {
        Log.d(TAG, "=== KREYOL onCreateInputView appelé ! ===")
        
        try {
            Log.d(TAG, "Création du clavier AZERTY avec support majuscules/minuscules...")
            
            // Réinitialiser la liste des boutons
            keyboardButtons.clear()
            
            // Créer le layout principal avec design moderne
            val mainLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.parseColor("#1C1C1C")) // Noir volcanique direct
                // Padding augmenté pour un design plus aéré
                setPadding(12, 12, 12, 12)
            }
            
            // Stocker la référence pour les changements de mode
            mainKeyboardLayout = mainLayout
            
            // Titre du clavier - Style moderne et épuré selon le brief
            val titleView = TextView(this).apply {
                text = "Klavyé Kreyòl Karukera 🇬🇵 • Potomitan™"
                textSize = resources.getDimension(R.dimen.text_size_title) / resources.displayMetrics.density
                setBackgroundColor(Color.parseColor("#0080FF")) // Bleu caraïbe direct
                setTextColor(Color.parseColor("#FFFFFF")) // Blanc coral direct
                setPadding(16, 12, 16, 12)
                gravity = Gravity.CENTER
                setTypeface(null, android.graphics.Typeface.BOLD)
                elevation = 2f
            }
            mainLayout.addView(titleView)
            
            // Barre de suggestions - Style moderne avec fond épuré
            val suggestionsContainer = HorizontalScrollView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setBackgroundColor(Color.parseColor("#F5F5DC")) // Beige sable direct
                // Padding augmenté pour un meilleur espacement
                setPadding(16, 16, 16, 16)
                elevation = 1f
            }
            
            suggestionsView = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                id = View.generateViewId() // Donner un ID unique
            }
            
            // Stocker l'ID pour récupération ultérieure
            suggestionsViewId = suggestionsView!!.id
            
            suggestionsContainer.addView(suggestionsView)
            mainLayout.addView(suggestionsContainer)
            
            // Initialiser avec les mots les plus fréquents
            updateSuggestions("")
            
            // Créer le clavier selon le mode
            createKeyboardLayout(mainLayout)
            
            // Ajouter un watermark Potomitan™ discret et moderne
            val watermarkContainer = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            
            val watermark = TextView(this).apply {
                text = "Potomitan™"
                textSize = resources.getDimension(R.dimen.text_size_watermark) / resources.displayMetrics.density
                alpha = 0.4f
                setTextColor(Color.parseColor("#FFFFFF")) // Blanc coral direct
                setTypeface(null, android.graphics.Typeface.ITALIC)
                gravity = Gravity.END
                setPadding(0, 4, 12, 4)
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM or Gravity.END
                )
            }
            
            watermarkContainer.addView(watermark)
            mainLayout.addView(watermarkContainer)
            
            // Mettre à jour l'affichage initial du clavier - DÉSACTIVÉ pour debug
            // updateKeyboardDisplay()
            
            Log.d(TAG, "=== CLAVIER KREYÒL CRÉÉ AVEC SUCCÈS ! suggestionsView: ${suggestionsView != null} ===")
            return mainLayout
            
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de la création de la vue du clavier", e)
            return null
        }
    }
    
    // 🇬🇵 FONCTION DE STYLE GUADELOUPE SIMPLE ET EFFICACE
    private fun applyGuadeloupeStyle(button: Button, key: String) {
        // Configuration de base pour toutes les touches
        button.setTypeface(null, android.graphics.Typeface.BOLD)
        button.stateListAnimator = null // Désactiver l'animation par défaut
        
        // Padding selon les dimensions définies
        val horizontalPadding = resources.getDimensionPixelSize(R.dimen.key_padding_horizontal)
        val verticalPadding = resources.getDimensionPixelSize(R.dimen.key_padding_vertical)
        
        when {
            // 1. Touches de lettres - Priorité visuelle #1 - STYLE SIMPLE
            key.length == 1 && key.matches(Regex("[a-zA-Z]")) -> {
                button.setBackgroundColor(Color.parseColor("#FFFFFF")) // Blanc pur
                button.setTextColor(Color.parseColor("#000000")) // Noir pur pour contraste max
                button.setTypeface(null, android.graphics.Typeface.BOLD)
                button.textSize = 18f // Taille fixe pour test
                button.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
                button.elevation = 4f
            }
            
            // 2. Barre d'espace - Priorité visuelle #2
            key == "ESPACE" -> {
                button.setBackgroundColor(Color.parseColor("#228B22")) // Vert direct
                button.setTextColor(Color.parseColor("#FFFFFF")) // Blanc direct
                button.setTypeface(null, android.graphics.Typeface.BOLD)
                button.text = "ESPACE"
                button.textSize = 14f
                button.setPadding(horizontalPadding * 2, verticalPadding, horizontalPadding * 2, verticalPadding)
                button.elevation = 4f
            }
            
            // 3. Touches d'action importantes - Priorité visuelle #3
            key in arrayOf("⌫", "⏎", "⇧", "ABC", "123") -> {
                button.setBackgroundColor(Color.parseColor("#FFD700")) // Jaune direct
                button.setTextColor(Color.parseColor("#000000")) // Noir direct
                button.setTypeface(null, android.graphics.Typeface.BOLD)
                button.textSize = 16f
                button.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
                button.elevation = 4f
            }
            
            // 4. Touches numériques - Style spécial
            key.matches(Regex("[0-9]")) -> {
                button.setBackgroundColor(Color.parseColor("#87CEEB")) // Bleu lagon direct
                button.setTextColor(Color.parseColor("#000000")) // Noir direct
                button.setTypeface(null, android.graphics.Typeface.BOLD)
                button.textSize = 16f
                button.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
                button.elevation = 4f
            }
            
            // 5. Autres touches de ponctuation
            else -> {
                button.setBackgroundColor(Color.parseColor("#F5F5DC")) // Beige direct
                button.setTextColor(Color.parseColor("#000000")) // Noir direct
                button.setTypeface(null, android.graphics.Typeface.NORMAL)
                button.textSize = 14f
                button.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
                button.elevation = 4f
            }
        }
        
        // Les animations sont maintenant gérées directement dans createKeyboardRow
    }
    
    // ✨ ANIMATIONS TACTILES MODERNES (100-120ms comme demandé)
    private fun addTouchAnimation(button: Button) {
        button.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Animation d'appui (100ms)
                    view.animate()
                        .scaleX(0.95f)
                        .scaleY(0.95f)
                        .setDuration(100)
                        .start()
                    
                    // Feedback haptique léger
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        view.performHapticFeedback(
                            android.view.HapticFeedbackConstants.KEYBOARD_TAP,
                            android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                        )
                    }
                    false // Laisser passer l'événement
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // Animation de relâchement (120ms)
                    view.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(120)
                        .start()
                    false // Laisser passer l'événement
                }
                else -> false
            }
        }
    }
    
    private fun createKeyboardRow(keys: Array<String>): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            // Espacement amélioré entre les rangées
            setPadding(6, 6, 6, 6)
        }
        
        for (key in keys) {
            // ESSAYONS AVEC TEXTVIEW AU LIEU DE BUTTON
            val button = android.widget.TextView(this)
            
            // 1. CONFIGURATION DE BASE
            button.text = key
            button.tag = key
            button.gravity = android.view.Gravity.CENTER
            button.setTextColor(android.graphics.Color.BLACK)
            button.textSize = 18f
            button.setTypeface(null, android.graphics.Typeface.BOLD)
            
            // 2. STYLE GUADELOUPE MODERNE AVEC TOUCHES ARRONDIES 🇬🇵
            when {
                // Lettres - Jaune soleil tropical avec coins arrondis
                key.matches(Regex("[a-zA-Z]")) -> {
                    button.setBackgroundResource(R.drawable.key_rounded_letter)
                    button.setTextColor(android.graphics.Color.parseColor(NOIR_VOLCANIQUE))
                    button.textSize = 18f
                    button.setTypeface(null, android.graphics.Typeface.BOLD)
                }
                // Touches d'action spéciales - Bleu caraïbe arrondi
                key == "SUPPR" || key == "ENTER" || key == "SHIFT" -> {
                    button.setBackgroundResource(R.drawable.key_rounded_action)
                    button.setTextColor(android.graphics.Color.parseColor("#FFFFFF"))
                    button.textSize = 14f
                    button.setTypeface(null, android.graphics.Typeface.BOLD)
                }
                // Espace - Vert canne moderne arrondi
                key == "ESPACE" -> {
                    button.setBackgroundResource(R.drawable.key_rounded_space)
                    button.setTextColor(android.graphics.Color.parseColor("#FFFFFF"))
                    button.textSize = 16f
                    button.setTypeface(null, android.graphics.Typeface.BOLD)
                }
                // Chiffres - Bleu lagon arrondi
                key.matches(Regex("[0-9]")) -> {
                    button.setBackgroundResource(R.drawable.key_rounded_number)
                    button.setTextColor(android.graphics.Color.parseColor(NOIR_VOLCANIQUE))
                    button.textSize = 16f
                    button.setTypeface(null, android.graphics.Typeface.BOLD)
                }
                // Autres touches - Beige sable arrondi
                else -> {
                    button.setBackgroundResource(R.drawable.key_rounded_other)
                    button.setTextColor(android.graphics.Color.parseColor(NOIR_VOLCANIQUE))
                    button.textSize = 14f
                    button.setTypeface(null, android.graphics.Typeface.NORMAL)
                }
            }
            
            // 3. PADDING ET DIMENSIONS selon le brief UX
            val padding = when {
                key == "ESPACE" -> 16
                key.matches(Regex("[a-zA-Z]")) -> 12
                else -> 10
            }
            button.setPadding(padding, padding, padding, padding)
            button.minHeight = 120
            button.minWidth = if (key == "ESPACE") 200 else 80
            
            // 4. DEBUG
            Log.d(TAG, "=== TextView '$key' créé avec background arrondi ===")
            Log.d(TAG, "  Text: '${button.text}'")
            
            // 5. Paramètres de layout
            val params = LinearLayout.LayoutParams(
                if (key == "ESPACE") 0 else ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            if (key == "ESPACE") {
                params.weight = 3f
            } else {
                params.weight = 1f
            }
            params.setMargins(4, 4, 4, 4)
            button.layoutParams = params
            
            // 6. GESTION TACTILE OPTIMISÉE - Stabilité améliorée
            button.setOnTouchListener { view, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        isLongPressTriggered = false
                        
                        // Animation d'appui élégante
                        view.animate()
                            .scaleX(0.95f)
                            .scaleY(0.95f)
                            .setDuration(100)
                            .start()
                        
                        // Feedback haptique léger pour éviter les conflits
                        try {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                view.performHapticFeedback(
                                    android.view.HapticFeedbackConstants.KEYBOARD_TAP,
                                    android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                                )
                            }
                        } catch (e: Exception) {
                            Log.d(TAG, "Haptic feedback non disponible")
                        }
                        
                        // Vérifier si cette touche a des accents (uniquement pour les lettres)
                        if (key.length == 1 && key.matches(Regex("[a-zA-Z]")) && accentMap.containsKey(key.uppercase())) {
                            startLongPressTimer(key, button)
                        }
                        
                        false // Laisser d'autres gestionnaires traiter l'événement
                    }
                    MotionEvent.ACTION_UP -> {
                        cancelLongPress()
                        
                        // Animation de relâchement
                        view.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(120)
                            .start()
                        
                        // Si pas d'appui long, rien à faire ici
                        // L'OnClickListener se chargera de l'input
                        
                        false // Laisser d'autres gestionnaires traiter l'événement
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        cancelLongPress()
                        
                        // Animation de relâchement
                        view.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(120)
                            .start()
                        
                        false
                    }
                    else -> false
                }
            }
            
            // 7. CLICK ACTION - Gestionnaire unique pour l'input
            button.setOnClickListener {
                // Ne traiter que si ce n'est pas un appui long
                if (!isLongPressTriggered) {
                    Log.d(TAG, "Clic sur: '$key'")
                    handleKeyPress(key)
                }
            }
            
            // 8. AJOUTER à la liste pour gestion majuscules/minuscules
            keyboardButtons.add(button)
            
            // 9. FINAL - Ajouter à la vue
            Log.d(TAG, "Avant ajout à la vue - Text: '${button.text}'")
            row.addView(button)
        }
        
        return row
    }
    
    private fun updateKeyboardDisplay() {
        // Fonction réactivée pour supporter les majuscules
        Log.d(TAG, "updateKeyboardDisplay() - Mode majuscule: $isCapitalMode, Caps Lock: $isCapsLock")
        
        if (isUpdatingKeyboard) {
            Log.d(TAG, "Mise à jour du clavier déjà en cours, ignorée")
            return
        }
        
        isUpdatingKeyboard = true
        
        try {
            keyboardButtons.forEach { button ->
                val originalText = button.tag as? String ?: button.text.toString().lowercase()
                val displayText = if (isCapitalMode || isCapsLock) {
                    originalText.uppercase()
                } else {
                    originalText.lowercase()
                }
                
                // Mettre à jour l'affichage du bouton seulement si nécessaire
                val newText = when (originalText) {
                    "⇧" -> "⇧" // Toujours le même symbole
                    "⌫", "⏎", "ESPACE", "123", "ABC" -> originalText
                    "1", "2", "3", "4", "5", "6", "7", "8", "9", "0" -> originalText // Chiffres
                    "@", "#", "$", "%", "&", "-", "+", "(", ")", "/", "*", "\"", "'", ":", ";", "!", "?", ",", "." -> originalText // Symboles
                    else -> if (isNumericMode) originalText else displayText // En mode numérique, pas de changement de casse
                }
                
                // Mettre à jour seulement si le texte a changé
                if (button.text.toString() != newText) {
                    button.text = newText
                }
                
                // Gérer l'état visuel de la touche Shift avec les états Android
                if (originalText == "⇧") {
                    // Réinitialiser tous les états
                    button.isActivated = false
                    button.isSelected = false
                    
                    // Appliquer l'état approprié
                    when {
                        isCapsLock -> button.isSelected = true // Jaune pour caps lock
                        isCapitalMode -> button.isActivated = true // Vert pour majuscule simple
                        // Sinon état normal (bleu)
                    }
                }
            }
            
            Log.d(TAG, "Clavier mis à jour - Mode majuscule: $isCapitalMode, Caps Lock: $isCapsLock")
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de la mise à jour du clavier", e)
        } finally {
            isUpdatingKeyboard = false
        }
    }
    
    private fun createKeyboardLayout(mainLayout: LinearLayout) {
        // Sauvegarder la référence aux suggestions AVANT suppression
        val savedSuggestionsView = suggestionsView
        val savedSuggestionsViewId = suggestionsViewId
        
        // Supprimer les rangées existantes (garder titre et suggestions)
        val childCount = mainLayout.childCount
        for (i in childCount - 1 downTo 2) { // Garder les 2 premiers enfants (titre + suggestions)
            mainLayout.removeViewAt(i)
        }
        
        // Restaurer la référence aux suggestions après suppression
        if (savedSuggestionsView != null && savedSuggestionsViewId != View.NO_ID) {
            suggestionsView = savedSuggestionsView
            suggestionsViewId = savedSuggestionsViewId
            Log.d(TAG, "Référence suggestionsView restaurée: ${suggestionsView != null}")
        } else {
            // Fallback : chercher dans la hiérarchie
            if (mainLayout.childCount > 1) {
                val suggestionsContainer = mainLayout.getChildAt(1) as? HorizontalScrollView
                suggestionsView = suggestionsContainer?.getChildAt(0) as? LinearLayout
                if (suggestionsView != null) {
                    suggestionsViewId = suggestionsView!!.id
                }
                Log.d(TAG, "Référence suggestionsView trouvée par fallback: ${suggestionsView != null}")
            }
        }
        
        if (isNumericMode) {
            // Mode numérique
            val row1 = createKeyboardRow(arrayOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"))
            mainLayout.addView(row1)
            
            val row2 = createKeyboardRow(arrayOf("@", "#", "$", "%", "&", "-", "+", "(", ")", "/"))
            mainLayout.addView(row2)
            
            val row3 = createKeyboardRow(arrayOf("*", "\"", "'", ":", ";", "!", "?", "⌫"))
            mainLayout.addView(row3)
            
            val row4 = createKeyboardRow(arrayOf("ABC", ",", "ESPACE", ".", "⏎"))
            mainLayout.addView(row4)
        } else {
            // Mode alphabétique AZERTY
            val row1 = createKeyboardRow(arrayOf("a", "z", "e", "r", "t", "y", "u", "i", "o", "p"))
            mainLayout.addView(row1)
            
            val row2 = createKeyboardRow(arrayOf("q", "s", "d", "f", "g", "h", "j", "k", "l", "m"))
            mainLayout.addView(row2)
            
            val row3 = createKeyboardRow(arrayOf("⇧", "w", "x", "c", "v", "b", "n", "⌫"))
            mainLayout.addView(row3)
            
            val row4 = createKeyboardRow(arrayOf("123", "ESPACE", "⏎"))
            mainLayout.addView(row4)
        }
        
        // Rafraîchir les suggestions après reconstruction
        Log.d(TAG, "Reconstruction du clavier terminée, suggestionsView: ${suggestionsView != null}")
    }
    
    private fun switchKeyboardMode() {
        Log.d(TAG, "Basculement de mode - Actuel: ${if (isNumericMode) "Numérique" else "Alphabétique"}")
        
        isNumericMode = !isNumericMode
        
        // Réinitialiser le mode majuscule en passant au mode numérique
        if (isNumericMode) {
            isCapitalMode = false
            isCapsLock = false
            currentWord = "" // Réinitialiser le mot en cours
        }
        
        // Recréer le clavier avec le nouveau mode
        val currentView = mainKeyboardLayout
        if (currentView != null) {
            keyboardButtons.clear() // Nettoyer la liste des boutons
            createKeyboardLayout(currentView)
            updateKeyboardDisplay()
            
            // Forcer la mise à jour des suggestions après reconstruction
            Handler(Looper.getMainLooper()).post {
                Log.d(TAG, "Post-reconstruction: suggestionsView = ${suggestionsView != null}")
                if (!isNumericMode) {
                    // Mode alphabétique - restaurer les suggestions
                    updateSuggestions(currentWord)
                    Log.d(TAG, "Suggestions restaurées pour mode alphabétique avec mot: '$currentWord'")
                } else {
                    // Mode numérique - vider les suggestions
                    updateSuggestions("")
                    Log.d(TAG, "Suggestions vidées pour mode numérique")
                }
            }
        }
        
        Log.d(TAG, "Mode basculé vers: ${if (isNumericMode) "Numérique" else "Alphabétique"}")
    }
    
    private fun startLongPressTimer(key: String, button: TextView) {
        longPressRunnable = Runnable {
            isLongPressTriggered = true
            showAccentPopup(key, button)
        }
        longPressHandler.postDelayed(longPressRunnable!!, 500) // 500ms pour déclencher l'appui long
    }
    
    private fun cancelLongPress() {
        longPressRunnable?.let {
            longPressHandler.removeCallbacks(it)
        }
        longPressRunnable = null
    }
    
    private fun showAccentPopup(baseKey: String, anchorButton: TextView) {
        // Fermer tout popup existant avant d'en créer un nouveau
        dismissAccentPopup()
        
        val accents = accentMap[baseKey.uppercase()] ?: return
        
        Log.d(TAG, "Affichage popup accents pour $baseKey: ${accents.joinToString()}")
        
        // Créer un layout horizontal équilibré pour les accents
        val popupLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(getColor(R.color.blanc_coral))
            setPadding(6, 6, 6, 6) // Padding équilibré
            elevation = resources.getDimension(R.dimen.popup_elevation)
        }
        
        // Ajouter la lettre de base en premier - style équilibré
        val baseButton = Button(this).apply {
            text = baseKey.lowercase()
            setBackgroundResource(R.drawable.key_letter_background)
            setTextColor(getColor(R.color.bleu_caraibe))
            textSize = 14f // Taille lisible
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(10, 8, 10, 8) // Padding équilibré
            minHeight = 70 // Compact mais utilisable
            minWidth = 60
            addTouchAnimation(this)
            setOnClickListener {
                handleKeyPress(baseKey)
                dismissAccentPopup()
            }
        }
        popupLayout.addView(baseButton)
        
        // Ajouter les accents avec style chips ÉQUILIBRÉS
        accents.forEach { accent ->
            val accentButton = Button(this).apply {
                text = accent
                setBackgroundResource(R.drawable.suggestion_chip_background)
                setTextColor(getColor(R.color.blanc_coral))
                textSize = 12f // Taille lisible mais pas trop grande
                setTypeface(null, android.graphics.Typeface.NORMAL)
                setPadding(8, 6, 8, 6) // Padding équilibré
                minHeight = 65 // Compact mais utilisable
                minWidth = 55 // Largeur adaptée
                addTouchAnimation(this)
                setOnClickListener {
                    handleAccentSelection(accent)
                    dismissAccentPopup()
                }
            }
            popupLayout.addView(accentButton)
        }
        
        // Créer et afficher le popup avec position corrigée
        currentAccentPopup = PopupWindow(
            popupLayout,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            false // Non focusable pour éviter les conflits avec le clavier
        ).apply {
            elevation = resources.getDimension(R.dimen.popup_elevation)
            
            // Position simple et fiable : directement au-dessus de la touche
            showAsDropDown(anchorButton, 0, -(anchorButton.height + 120)) // Position au-dessus
        }
    }
    
    private var currentAccentPopup: PopupWindow? = null
    
    private fun dismissAccentPopup() {
        try {
            currentAccentPopup?.let { popup ->
                if (popup.isShowing) {
                    popup.dismiss()
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Erreur fermeture popup: ${e.message}")
        } finally {
            currentAccentPopup = null
        }
    }
    
    private fun handleAccentSelection(accent: String) {
        Log.d(TAG, "Accent sélectionné: $accent")
        
        val inputConnection = currentInputConnection
        if (inputConnection != null) {
            // Appliquer le mode majuscule/minuscule à l'accent si nécessaire
            val finalAccent = if (isCapitalMode || isCapsLock) {
                accent.uppercase()
            } else {
                accent.lowercase()
            }
            
            inputConnection.commitText(finalAccent, 1)
            currentWord += finalAccent
            Log.d(TAG, "Mot actuel après accent: '$currentWord' (longueur: ${currentWord.length})")
            Log.d(TAG, "Caractères dans currentWord: ${currentWord.map { it.code }.joinToString(",")}")
            updateSuggestions(currentWord)
            
            // Désactiver le mode majuscule après un accent (sauf si Caps Lock)
            if (isCapitalMode && !isCapsLock) {
                isCapitalMode = false
                // Post la mise à jour pour éviter les conflits
                Handler(Looper.getMainLooper()).post {
                    updateKeyboardDisplay()
                }
            }
        } else {
            Log.w(TAG, "InputConnection est null lors de la sélection d'accent !")
        }
    }
    
    private fun handleKeyPress(key: String) {
        Log.d(TAG, "Touche pressée: $key")
        
        val inputConnection = currentInputConnection
        if (inputConnection != null) {
            when (key) {
                "⌫" -> {
                    // Gestion du backspace améliorée
                    Log.d(TAG, "Backspace pressé - currentWord avant: '$currentWord'")
                    
                    // Supprimer le caractère de l'écran
                    inputConnection.deleteSurroundingText(1, 0)
                    
                    // Mettre à jour currentWord
                    if (currentWord.isNotEmpty()) {
                        currentWord = currentWord.dropLast(1)
                        Log.d(TAG, "Backspace - Mot après effacement: '$currentWord' (longueur: ${currentWord.length})")
                        
                        // Force la mise à jour des suggestions même si le mot est vide
                        Handler(Looper.getMainLooper()).post {
                            updateSuggestions(currentWord)
                        }
                    } else {
                        // Si currentWord est déjà vide, on essaie de récupérer le contexte actuel
                        Log.d(TAG, "Backspace - currentWord était déjà vide")
                        try {
                            // Essayer de récupérer le texte autour du curseur pour resynchroniser
                            val textBeforeCursor = inputConnection.getTextBeforeCursor(50, 0)?.toString() ?: ""
                            val lastWordMatch = Regex("\\b(\\w+)$").find(textBeforeCursor)
                            
                            if (lastWordMatch != null) {
                                currentWord = lastWordMatch.value
                                Log.d(TAG, "Backspace - Resynchronisation: currentWord = '$currentWord'")
                            } else {
                                currentWord = ""
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Erreur lors de la resynchronisation: ${e.message}")
                            currentWord = ""
                        }
                        
                        // Forcer la mise à jour des suggestions
                        Handler(Looper.getMainLooper()).post {
                            updateSuggestions(currentWord)
                        }
                    }
                }
                "⏎" -> {
                    // Touche Entrée
                    inputConnection.sendKeyEvent(
                        android.view.KeyEvent(
                            android.view.KeyEvent.ACTION_DOWN,
                            android.view.KeyEvent.KEYCODE_ENTER
                        )
                    )
                    inputConnection.sendKeyEvent(
                        android.view.KeyEvent(
                            android.view.KeyEvent.ACTION_UP,
                            android.view.KeyEvent.KEYCODE_ENTER
                        )
                    )
                    currentWord = ""
                    updateSuggestions("")
                }
                "ESPACE" -> {
                    // Espace termine le mot actuel
                    if (currentWord.isNotBlank()) {
                        addWordToHistory(currentWord) // Ajouter le mot à l'historique N-grams
                        
                        // Ajouter automatiquement les mots créoles au dictionnaire personnel
                        if (isCreoleWord(currentWord)) {
                            try {
                                UserDictionary.Words.addWord(
                                    this@KreyolInputMethodService,
                                    currentWord.lowercase(),
                                    255,
                                    UserDictionary.Words.LOCALE_TYPE_ALL
                                )
                            } catch (e: Exception) {
                                Log.w(TAG, "Impossible d'ajouter '$currentWord' au dictionnaire: ${e.message}")
                            }
                        }
                    }
                    inputConnection.commitText(" ", 1)
                    currentWord = ""
                    updateSuggestions("")
                }
                "123" -> {
                    // Basculer vers le mode numérique
                    switchKeyboardMode()
                }
                "ABC" -> {
                    // Basculer vers le mode alphabétique
                    switchKeyboardMode()
                }
                "⇧" -> {
                    // Gestion de la touche Shift (seulement en mode alphabétique)
                    if (!isNumericMode) {
                        handleShiftPress()
                    }
                }
                else -> {
                    if (isNumericMode) {
                        // Mode numérique - insérer chiffres et symboles directement
                        inputConnection.commitText(key, 1)
                        // En mode numérique, on ne fait pas de suggestions de mots
                        Log.d(TAG, "Caractère numérique/symbole inséré: $key")
                    } else {
                        // Mode alphabétique - appliquer le mode majuscule/minuscule
                        val textToInsert = if (isCapitalMode || isCapsLock) {
                            key.uppercase()
                        } else {
                            key.lowercase()
                        }
                        
                        inputConnection.commitText(textToInsert, 1)
                        currentWord += textToInsert
                        // Réduire les logs pour éviter le spam
                        if (currentWord.length <= 3) { // Log seulement pour les premiers caractères
                            Log.d(TAG, "Mot actuel: '$currentWord'")
                        }
                        updateSuggestions(currentWord)
                        
                        // Désactiver le mode majuscule après une lettre (sauf si Caps Lock)
                        if (isCapitalMode && !isCapsLock) {
                            isCapitalMode = false
                            // Post la mise à jour pour éviter les conflits
                            Handler(Looper.getMainLooper()).post {
                                updateKeyboardDisplay()
                            }
                        }
                    }
                }
            }
        } else {
            Log.w(TAG, "InputConnection est null !")
        }
    }
    
    private fun handleShiftPress() {
        Log.d(TAG, "Touche Shift pressée - Mode actuel: Capital=$isCapitalMode, CapsLock=$isCapsLock")
        
        val previousCapitalMode = isCapitalMode
        val previousCapsLock = isCapsLock
        
        when {
            isCapsLock -> {
                // Déjà en Caps Lock, désactiver complètement
                isCapsLock = false
                isCapitalMode = false
                Log.d(TAG, "Caps Lock désactivé")
            }
            isCapitalMode -> {
                // Déjà en mode majuscule, activer Caps Lock
                isCapsLock = true
                isCapitalMode = false
                Log.d(TAG, "Caps Lock activé")
            }
            else -> {
                // Mode normal, activer mode majuscule
                isCapitalMode = true
                isCapsLock = false
                Log.d(TAG, "Mode majuscule activé")
            }
        }
        
        // Mettre à jour seulement si l'état a changé
        if (previousCapitalMode != isCapitalMode || previousCapsLock != isCapsLock) {
            // Post la mise à jour pour éviter les conflits
            Handler(Looper.getMainLooper()).post {
                updateKeyboardDisplay()
            }
        }
    }
    
    // GESTION DICTIONNAIRE PERSONNEL POUR ÉVITER SOULIGNEMENT ROUGE
    private fun isCreoleWord(word: String): Boolean {
        // Vérifier si le mot est dans notre dictionnaire créole
    val lowercaseWord = word.lowercase()
        
        // 1. Vérifier dans le dictionnaire principal
        if (dictionary.any { it.first.toLowerCase() == lowercaseWord }) {
            return true
        }
        
        // 2. Patterns typiques du créole guadeloupéen
        val creolePatterns = listOf(
            ".*òl$", ".*è$", ".*ò$", ".*é$", ".*à$", // Finales avec accents créoles
            "^ki.*", "^ka.*", "^kè.*", "^ké.*", // Préfixes créoles
            ".*yan$", ".*yon$", ".*an$", ".*on$", // Finales créoles courantes
            ".*té$", ".*tè$", ".*pou$", ".*nou$" // Autres patterns créoles
        )
        
        return creolePatterns.any { pattern ->
            lowercaseWord.matches(Regex(pattern))
        }
    }
    
    private fun populatePersonalDictionary() {
        Log.d(TAG, "Population du dictionnaire personnel avec mots créoles du fichier JSON...")
        
        try {
            // WRITE_USER_DICTIONARY est protégé pour apps système; nous essayons sans bloquer l'exécution.
            
            // Utiliser le dictionnaire déjà chargé depuis creole_dict.json
            if (dictionary.isNotEmpty()) {
                Log.d(TAG, "Ajout de ${dictionary.size} mots créoles au dictionnaire personnel...")
                
                // Ajouter tous les mots du dictionnaire créole, en limitant aux plus fréquents
                // pour éviter de surcharger le dictionnaire personnel
                val wordsToAdd = dictionary.take(1000) // Augmentons à 1000 mots pour inclure plus de vocabulaire
                
                var addedCount = 0
                wordsToAdd.forEach { (word, frequency) ->
                    try {
                        UserDictionary.Words.addWord(
                            this,
                            word.lowercase(),
                            255, // Fréquence maximale pour prioriser les mots créoles
                            UserDictionary.Words.LOCALE_TYPE_ALL
                        )
                        addedCount++
                        
                        // Log pour quelques mots clés pour déboguer
                        if (word.lowercase() in listOf("mwen", "ou", "li", "nou", "yo", "bonjou", "mèsi")) {
                            Log.d(TAG, "Mot créole clé ajouté: '$word'")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Impossible d'ajouter '$word': ${e.message}")
                    }
                }
                
                Log.d(TAG, "Dictionnaire personnel populé avec $addedCount/${wordsToAdd.size} mots créoles")
            } else {
                Log.w(TAG, "Dictionnaire créole pas encore chargé, ajout de mots de base...")
                
                // Fallback avec quelques mots de base si le dictionnaire n'est pas encore chargé
                val fallbackWords = listOf(
                    "an", "ka", "la", "on", "té", "pou", "nou", "sé", "yo", "ki",
                    "mwen", "ou", "li", "bonjou", "bonswa", "mèsi", "kreyòl", "kay", "moun", "jou"
                )
                
                fallbackWords.forEach { word ->
                    try {
                        UserDictionary.Words.addWord(
                            this,
                            word.lowercase(),
                            255,
                            UserDictionary.Words.LOCALE_TYPE_ALL
                        )
                        Log.d(TAG, "Mot de base ajouté: '$word'")
                    } catch (e: Exception) {
                        Log.w(TAG, "Impossible d'ajouter '$word': ${e.message}")
                    }
                }
                
                Log.d(TAG, "Dictionnaire personnel populé avec ${fallbackWords.size} mots de base")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de la population du dictionnaire personnel", e)
        }
    }
    
    // SURCHARGE SYSTÈME DE CORRECTION ORTHOGRAPHIQUE
    override fun onDisplayCompletions(completions: Array<android.view.inputmethod.CompletionInfo>?) {
        // Filtrer les suggestions pour éviter de marquer les mots créoles comme incorrects
        if (completions != null) {
            val filteredCompletions = completions.filter { completion ->
                val text = completion.text.toString().lowercase()
                // Garder la suggestion si ce n'est pas un mot créole OU si c'est une vraie suggestion
                !isCreoleWord(text) || text in dictionary.map { it.first.lowercase() }
            }.toTypedArray()
            
            super.onDisplayCompletions(filteredCompletions)
        } else {
            super.onDisplayCompletions(completions)
        }
    }
    
    // Surcharger la gestion des suggestions pour éviter le soulignement rouge
    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        
        // Si on a un mot sélectionné, vérifier s'il s'agit d'un mot créole
        if (candidatesStart >= 0 && candidatesEnd > candidatesStart) {
            val inputConnection = currentInputConnection
            if (inputConnection != null) {
                try {
                    val selectedText = inputConnection.getTextBeforeCursor(candidatesEnd, 0)?.toString()
                    if (selectedText != null && selectedText.length >= candidatesEnd - candidatesStart) {
                        val candidateWord = selectedText.substring(
                            selectedText.length - (candidatesEnd - candidatesStart)
                        ).lowercase()
                        
                        // Si c'est un mot créole reconnu, l'ajouter au dictionnaire personnel
                        if (isCreoleWord(candidateWord)) {
                            try {
                                UserDictionary.Words.addWord(
                                    this,
                                    candidateWord,
                                    255,
                                    UserDictionary.Words.LOCALE_TYPE_ALL
                                )
                                Log.d(TAG, "Mot créole '$candidateWord' ajouté automatiquement au dictionnaire")
                            } catch (e: Exception) {
                                Log.w(TAG, "Erreur ajout automatique '$candidateWord': ${e.message}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Erreur lors de la vérification du mot sélectionné: ${e.message}")
                }
            }
        }
    }
}
