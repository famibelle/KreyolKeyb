package com.example.kreyolkeyboard

import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.util.Log
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection

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
    
    fun setInputProcessorListener(listener: InputProcessorListener) {
        this.processorListener = listener
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
        finalizeCurrentWord()
        
        // Déterminer le type d'action selon le contexte
        val editorInfo = inputMethodService.currentInputEditorInfo
        val imeAction = editorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
        
        when (imeAction) {
            EditorInfo.IME_ACTION_SEND -> {
                inputConnection.performEditorAction(EditorInfo.IME_ACTION_SEND)
            }
            EditorInfo.IME_ACTION_SEARCH -> {
                inputConnection.performEditorAction(EditorInfo.IME_ACTION_SEARCH)
            }
            EditorInfo.IME_ACTION_GO -> {
                inputConnection.performEditorAction(EditorInfo.IME_ACTION_GO)
            }
            EditorInfo.IME_ACTION_NEXT -> {
                inputConnection.performEditorAction(EditorInfo.IME_ACTION_NEXT)
            }
            EditorInfo.IME_ACTION_DONE -> {
                inputConnection.performEditorAction(EditorInfo.IME_ACTION_DONE)
            }
            else -> {
                // Action par défaut - nouvelle ligne
                inputConnection.commitText("\n", 1)
            }
        }
        
        processorListener?.onSpecialKeyPressed("⏎")
        return true
    }
    
    /**
     * Traite la touche Majuscule
     */
    private fun handleShift(): Boolean {
        when {
            !isCapitalMode && !isCapsLock -> {
                // Première pression - majuscule simple
                isCapitalMode = true
                isCapsLock = false
            }
            isCapitalMode && !isCapsLock -> {
                // Deuxième pression - verrouillage majuscule
                isCapitalMode = true
                isCapsLock = true
            }
            else -> {
                // Troisième pression - retour normal
                isCapitalMode = false
                isCapsLock = false
            }
        }
        
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
     * Traite la sélection d'une suggestion
     */
    fun processSuggestionSelection(suggestion: String): Boolean {
        val inputConnection = inputMethodService.currentInputConnection ?: return false
        
        // Supprimer le mot partiel actuel
        if (currentWord.isNotEmpty()) {
            inputConnection.deleteSurroundingText(currentWord.length, 0)
        }
        
        // Insérer la suggestion avec un espace automatique
        inputConnection.commitText("$suggestion ", 1)
        
        // Finaliser le mot
        currentWord = suggestion
        finalizeCurrentWord()
        
        // Gérer la capitalisation automatique après l'espace
        handleAutoCapitalization()
        
        Log.d(TAG, "Suggestion sélectionnée: '$suggestion' (avec espace automatique)")
        return true
    }
    
    /**
     * Finalise le mot courant
     */
    private fun finalizeCurrentWord() {
        if (currentWord.isNotEmpty()) {
            processorListener?.onWordCompleted(currentWord)
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
