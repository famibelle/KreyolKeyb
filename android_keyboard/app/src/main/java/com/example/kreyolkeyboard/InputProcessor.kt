package com.example.kreyolkeyboard

import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.util.Log
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import com.example.kreyolkeyboard.gamification.WordCommitListener

/**
 * Processeur d'entrées pour le clavier créole
 * Gère le traitement des touches, les modes de saisie et les interactions avec l'éditeur
 */
class InputProcessor(private val inputMethodService: InputMethodService) {
    
    companion object {
        private const val TAG = "InputProcessor"
    }
    
    // État du processeur
    private var currentWord = ""
    private var isCapitalMode = false
    private var isCapsLock = false
    private var isNumericMode = false
    
    // Callbacks
    interface InputProcessorListener {
        fun onWordChanged(word: String)
        fun onWordCompleted(word: String)
        fun onModeChanged(isNumeric: Boolean, isCapital: Boolean, isCapsLock: Boolean)
        fun onSpecialKeyPressed(key: String)
    }
    
    private var processorListener: InputProcessorListener? = null
    private var wordCommitListener: WordCommitListener? = null  // 🎮 Gamification: Tracking des mots
    
    fun setInputProcessorListener(listener: InputProcessorListener) {
        this.processorListener = listener
    }
    
    /**
     * 🎮 Gamification: Définit le listener pour le tracking des mots committés
     */
    fun setWordCommitListener(listener: WordCommitListener) {
        this.wordCommitListener = listener
    }
    
    /**
     * Traite une pression de touche
     */
    fun processKeyPress(key: String): Boolean {
        Log.d(TAG, "processKeyPress appelé avec: '$key'")
        val inputConnection = inputMethodService.currentInputConnection ?: return false
        
        return when (key) {
            "⌫" -> {
                Log.d(TAG, "Handling backspace")
                handleBackspace(inputConnection)
            }
            "⏎" -> {
                Log.d(TAG, "Handling enter")
                handleEnter(inputConnection)
            }
            "⇧" -> {
                Log.d(TAG, "Handling shift")
                handleShift()
            }
            "123", "ABC" -> {
                Log.d(TAG, "Handling mode switch")
                handleModeSwitch()
            }
            " " -> {
                Log.d(TAG, "Handling space")
                handleSpace(inputConnection)
            }
            else -> {
                Log.d(TAG, "Handling character input: '$key'")
                handleCharacterInput(key, inputConnection)
            }
        }
    }
    
    /**
     * Traite l'entrée d'un caractère normal
     */
    private fun handleCharacterInput(key: String, inputConnection: InputConnection): Boolean {
        val character = if (shouldCapitalize()) {
            key.uppercase()
        } else {
            key.lowercase()
        }
        
        // Ajouter le caractère au mot courant
        if (character.matches(Regex("[a-zA-Zàáâãäåèéêëìíîïòóôõöøùúûüýÿñçĉĝĥĵŝŭ]"))) {
            currentWord += character
            Log.d(TAG, "Caractère '$character' ajouté, mot courant: '$currentWord'")
            processorListener?.onWordChanged(currentWord)
            Log.d(TAG, "onWordChanged appelé avec: '$currentWord'")
        } else {
            // Caractère non alphabétique - finaliser le mot courant
            Log.d(TAG, "Caractère '$character' non alphabétique - finalisation du mot")
            finalizeCurrentWord()
        }
        
        // Envoyer le caractère à l'éditeur
        inputConnection.commitText(character, 1)
        
        // Gérer la capitalisation automatique
        handleAutoCapitalization()
        
        Log.d(TAG, "Caractère traité: '$character', mot courant: '$currentWord'")
        return true
    }
    
    /**
     * Traite la touche Retour arrière
     */
    private fun handleBackspace(inputConnection: InputConnection): Boolean {
        // Supprimer le caractère précédent dans l'éditeur
        val deleted = inputConnection.deleteSurroundingText(1, 0)
        
        // Mettre à jour le mot courant
        if (currentWord.isNotEmpty()) {
            currentWord = currentWord.dropLast(1)
            processorListener?.onWordChanged(currentWord)
        }
        
        Log.d(TAG, "Backspace traité, mot courant: '$currentWord'")
        return true
    }
    
    /**
     * Traite la touche Entrée
     */
    private fun handleEnter(inputConnection: InputConnection): Boolean {
        Log.d(TAG, "🔵 === DEBUT handleEnter() ===")
        finalizeCurrentWord()
        Log.d(TAG, "🔵 Mot finalisé")
        
        // Déterminer le type d'action selon le contexte
        val editorInfo = inputMethodService.currentInputEditorInfo
        val imeOptions = editorInfo?.imeOptions ?: 0
        val imeAction = imeOptions and EditorInfo.IME_MASK_ACTION
        
        Log.d(TAG, "🔵 EditorInfo: $editorInfo")
        Log.d(TAG, "🔵 IME Options: $imeOptions")
        Log.d(TAG, "🔵 IME Action détectée: $imeAction")
        
        // 🔧 QUICK FIX: Vérifier si l'action ENTER est explicitement désactivée
        val noEnterAction = (imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        
        if (noEnterAction) {
            Log.d(TAG, "🔵 ⚠️ Flag IME_FLAG_NO_ENTER_ACTION détecté - Action ENTER désactivée")
            Log.d(TAG, "🔵 → Insertion nouvelle ligne au lieu d'exécuter l'action")
            inputConnection.commitText("\n", 1)
            processorListener?.onSpecialKeyPressed("⏎")
            Log.d(TAG, "🔵 === FIN handleEnter() (action désactivée) ===")
            return true
        }
        
        // 🔧 AMÉLIORATION: Détecter les champs multilignes
        val inputType = editorInfo?.inputType ?: 0
        val isMultiline = (inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0
        
        if (isMultiline && imeAction == EditorInfo.IME_ACTION_UNSPECIFIED) {
            Log.d(TAG, "🔵 📝 Champ multiligne détecté - Insertion nouvelle ligne")
            inputConnection.commitText("\n", 1)
            processorListener?.onSpecialKeyPressed("⏎")
            Log.d(TAG, "🔵 === FIN handleEnter() (multiligne) ===")
            return true
        }
        
        Log.d(TAG, "🔵 🎯 Exécution de l'action IME selon le contexte")
        
        when (imeAction) {
            EditorInfo.IME_ACTION_SEND -> {
                Log.d(TAG, "🔵 → Action SEND - Envoi du message")
                inputConnection.performEditorAction(EditorInfo.IME_ACTION_SEND)
                Log.d(TAG, "🔵 → performEditorAction(SEND) exécuté")
            }
            EditorInfo.IME_ACTION_SEARCH -> {
                Log.d(TAG, "🔵 → Action SEARCH - Recherche")
                inputConnection.performEditorAction(EditorInfo.IME_ACTION_SEARCH)
                Log.d(TAG, "🔵 → performEditorAction(SEARCH) exécuté")
            }
            EditorInfo.IME_ACTION_GO -> {
                Log.d(TAG, "🔵 → Action GO")
                inputConnection.performEditorAction(EditorInfo.IME_ACTION_GO)
                Log.d(TAG, "🔵 → performEditorAction(GO) exécuté")
            }
            EditorInfo.IME_ACTION_NEXT -> {
                Log.d(TAG, "🔵 → Action NEXT - Champ suivant")
                inputConnection.performEditorAction(EditorInfo.IME_ACTION_NEXT)
                Log.d(TAG, "🔵 → performEditorAction(NEXT) exécuté")
            }
            EditorInfo.IME_ACTION_DONE -> {
                Log.d(TAG, "🔵 → Action DONE - Terminé")
                inputConnection.performEditorAction(EditorInfo.IME_ACTION_DONE)
                Log.d(TAG, "🔵 → performEditorAction(DONE) exécuté")
            }
            else -> {
                Log.d(TAG, "🔵 → Action PAR DÉFAUT - Nouvelle ligne")
                // Action par défaut - nouvelle ligne
                inputConnection.commitText("\n", 1)
                Log.d(TAG, "🔵 → Nouvelle ligne insérée")
            }
        }
        
        Log.d(TAG, "🔵 Notification listener touche spéciale")
        processorListener?.onSpecialKeyPressed("⏎")
        Log.d(TAG, "🔵 === FIN handleEnter() ===")
        return true
    }
    
    /**
     * Traite la touche Majuscule
     */
    private fun handleShift(): Boolean {
        Log.e("SHIFT_REAL_DEBUG", "🚨🚨🚨 HANDLESHIFT CALLED IN INPUTPROCESSOR! 🚨🚨🚨")
        when {
            !isCapitalMode && !isCapsLock -> {
                // Première pression - majuscule simple
                isCapitalMode = true
                isCapsLock = false
                Log.e("SHIFT_REAL_DEBUG", "🚨 MODE: CAPITAL SIMPLE")
            }
            isCapitalMode && !isCapsLock -> {
                // Deuxième pression - verrouillage majuscule
                isCapitalMode = true
                isCapsLock = true
                Log.e("SHIFT_REAL_DEBUG", "🚨 MODE: CAPS LOCK")
            }
            else -> {
                // Troisième pression - retour normal
                isCapitalMode = false
                isCapsLock = false
                Log.e("SHIFT_REAL_DEBUG", "🚨 MODE: NORMAL")
            }
        }
        
        Log.e("SHIFT_REAL_DEBUG", "🚨 Calling processorListener?.onModeChanged()")
        processorListener?.onModeChanged(isNumericMode, isCapitalMode, isCapsLock)
        Log.d(TAG, "Shift traité - Capital: $isCapitalMode, CapsLock: $isCapsLock")
        return true
    }
    
    /**
     * Traite le changement de mode (123/ABC)
     */
    private fun handleModeSwitch(): Boolean {
        isNumericMode = !isNumericMode
        processorListener?.onModeChanged(isNumericMode, isCapitalMode, isCapsLock)
        
        Log.d(TAG, "Mode changé - Numérique: $isNumericMode")
        return true
    }
    
    /**
     * Traite la barre d'espace
     */
    private fun handleSpace(inputConnection: InputConnection): Boolean {
        finalizeCurrentWord()
        inputConnection.commitText(" ", 1)
        
        // Activer la capitalisation automatique après certains signes
        handleAutoCapitalization()
        
        return true
    }
    
    /**
     * 🌐 Traite l'appui long sur la barre d'espace
     * Utilisé pour changer de clavier IME
     * 
     * @return true pour indiquer qu'il faut changer de clavier
     */
    fun processSpaceLongPress(): Boolean {
        Log.d(TAG, "🌐 Appui long sur barre d'espace détecté")
        // Ne pas finaliser le mot courant (contrairement à l'espace court)
        // L'utilisateur veut juste changer de clavier, pas terminer sa saisie
        return true
    }
    
    /**
     * Traite la sélection d'une suggestion
     */
    fun processSuggestionSelection(suggestion: String): Boolean {
        val inputConnection = inputMethodService.currentInputConnection ?: return false
        
        // Supprimer le mot partiel actuel
        if (currentWord.isNotEmpty()) {
            inputConnection.deleteSurroundingText(currentWord.length, 0)
        }
        
        // ✅ La suggestion arrive déjà avec la bonne casse depuis SuggestionEngine
        Log.d(TAG, "Suggestion avec casse préservée: '$currentWord' -> '$suggestion'")
        
        // Insérer la suggestion avec un espace automatique
        inputConnection.commitText("$suggestion ", 1)
        
        // Finaliser le mot (le tracking se fera dans finalizeCurrentWord)
        currentWord = suggestion
        finalizeCurrentWord()
        
        // Gérer la capitalisation automatique après l'espace
        handleAutoCapitalization()
        
        Log.d(TAG, "Suggestion sélectionnée: '$suggestion' (avec espace automatique)")
        return true
    }
    
    /**
     * 🔥 CORRECTION BUG CASSE : Applique la casse intentionnelle de l'utilisateur à la suggestion
     * Préserve la majuscule intentionnelle (Shift/Caps) lors de l'application des suggestions
     */
    private fun applyCaseToSuggestion(suggestion: String, currentInput: String): String {
        if (suggestion.isEmpty() || currentInput.isEmpty()) {
            return suggestion
        }
        
        // Analyser la casse du premier caractère tapé par l'utilisateur
        val firstInputChar = currentInput.first()
        val isIntentionalCapital = firstInputChar.isUpperCase()
        
        Log.d(TAG, "🔍 Analyse casse InputProcessor: input='$currentInput', premier char='$firstInputChar', majuscule intentionnelle=$isIntentionalCapital")
        
        return if (isIntentionalCapital) {
            // L'utilisateur a volontairement commencé en majuscule → capitaliser la suggestion
            suggestion.lowercase().replaceFirstChar { it.uppercase() }
        } else {
            // L'utilisateur a tapé en minuscule → garder la suggestion en minuscule
            suggestion.lowercase()
        }
    }
    
    /**
     * Finalise le mot courant
     */
    private fun finalizeCurrentWord() {
        if (currentWord.isNotEmpty()) {
            processorListener?.onWordCompleted(currentWord)
            
            // 🎮 Gamification: Notifier le tracking du mot committé
            wordCommitListener?.onWordCommitted(currentWord)
            Log.d(TAG, "🎮 Mot committé pour tracking: '$currentWord'")
            
            currentWord = ""
            processorListener?.onWordChanged("")
        }
    }
    
    /**
     * Détermine si le prochain caractère doit être en majuscule
     */
    private fun shouldCapitalize(): Boolean {
        return when {
            isCapsLock -> true
            isCapitalMode -> true
            shouldAutoCapitalize() -> true
            else -> false
        }
    }
    
    /**
     * Détermine si la capitalisation automatique doit s'appliquer
     */
    private fun shouldAutoCapitalize(): Boolean {
        val inputConnection = inputMethodService.currentInputConnection ?: return false
        
        // Vérifier le contexte de l'éditeur
        val editorInfo = inputMethodService.currentInputEditorInfo ?: return false
        val inputType = editorInfo.inputType
        
        // Pas de capitalisation automatique en mode mot de passe ou numérique
        if (inputType and InputType.TYPE_TEXT_VARIATION_PASSWORD != 0 ||
            inputType and InputType.TYPE_CLASS_NUMBER != 0) {
            return false
        }
        
        // Obtenir le texte précédent pour détecter le début de phrase
        try {
            val textBefore = inputConnection.getTextBeforeCursor(100, 0)?.toString() ?: ""
            
            // Capitaliser au début du texte
            if (textBefore.isEmpty() || textBefore.isBlank()) {
                return true
            }
            
            // Capitaliser après un point, un point d'exclamation ou d'interrogation
            val lastSentenceEnd = textBefore.indexOfLast { it in ".!?" }
            if (lastSentenceEnd != -1) {
                val afterPunctuation = textBefore.substring(lastSentenceEnd + 1)
                if (afterPunctuation.isBlank()) {
                    return true
                }
            }
            
        } catch (e: Exception) {
            Log.w(TAG, "Erreur lors de la vérification de la capitalisation automatique: ${e.message}")
        }
        
        return false
    }
    
    /**
     * Gère la capitalisation automatique après certains événements
     */
    private fun handleAutoCapitalization() {
        if (shouldAutoCapitalize()) {
            isCapitalMode = true
            processorListener?.onModeChanged(isNumericMode, isCapitalMode, isCapsLock)
        } else if (isCapitalMode && !isCapsLock) {
            // Désactiver la majuscule simple après utilisation
            isCapitalMode = false
            processorListener?.onModeChanged(isNumericMode, isCapitalMode, isCapsLock)
        }
    }
    
    /**
     * Traite les événements de touches système
     */
    fun processSystemKey(keyCode: Int, keyEvent: KeyEvent): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DEL -> {
                if (keyEvent.action == KeyEvent.ACTION_DOWN) {
                    val inputConnection = inputMethodService.currentInputConnection
                    inputConnection?.let { handleBackspace(it) } ?: false
                } else false
            }
            KeyEvent.KEYCODE_ENTER -> {
                if (keyEvent.action == KeyEvent.ACTION_DOWN) {
                    val inputConnection = inputMethodService.currentInputConnection
                    inputConnection?.let { handleEnter(it) } ?: false
                } else false
            }
            else -> false
        }
    }
    
    /**
     * Réinitialise l'état du processeur
     */
    fun resetState() {
        currentWord = ""
        isCapitalMode = false
        isCapsLock = false
        // Ne pas réinitialiser isNumericMode pour conserver le mode choisi
        
        processorListener?.onWordChanged("")
        processorListener?.onModeChanged(isNumericMode, isCapitalMode, isCapsLock)
    }
    
    /**
     * Met à jour le mot courant (utilisé par les suggestions)
     */
    fun setCurrentWord(word: String) {
        currentWord = word
        processorListener?.onWordChanged(word)
    }
    
    /**
     * Met à jour le mot courant SANS déclencher onWordChanged() 
     * Utilisé pour éviter les cascades d'événements (ex: onAccentSelected)
     */
    fun updateCurrentWordSilently(word: String) {
        currentWord = word
        Log.d(TAG, "updateCurrentWordSilently: '$word' (pas de callback)")
    }
    
    /**
     * Obtient le mot courant
     */
    fun getCurrentWord(): String = currentWord
    
    /**
     * Obtient l'état des modes
     */
    fun getState(): InputState {
        return InputState(
            isCapitalMode = isCapitalMode,
            isCapsLock = isCapsLock,
            isNumericMode = isNumericMode,
            currentWord = currentWord
        )
    }
    
    /**
     * Définit l'état des modes
     */
    fun setState(state: InputState) {
        isCapitalMode = state.isCapitalMode
        isCapsLock = state.isCapsLock
        isNumericMode = state.isNumericMode
        currentWord = state.currentWord
        
        processorListener?.onWordChanged(currentWord)
        processorListener?.onModeChanged(isNumericMode, isCapitalMode, isCapsLock)
    }
    
    /**
     * Classe de données pour l'état du processeur
     */
    data class InputState(
        val isCapitalMode: Boolean = false,
        val isCapsLock: Boolean = false,
        val isNumericMode: Boolean = false,
        val currentWord: String = ""
    )
}
