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

        // Nombre de caractères lus de part et d'autre du curseur pour reconstituer
        // le mot en cours. Large devant le plus long mot du dictionnaire créole.
        private const val WORD_LOOKAROUND = 64

        /**
         * Définit ce qui appartient à un mot, pour la frappe comme pour la
         * reconstitution du mot autour du curseur : les deux doivent partager la
         * même définition, sinon `currentWord` divergerait du texte réel à chaque
         * resynchronisation.
         *
         * `isLetter()` plutôt qu'une classe de caractères explicite : l'ancienne
         * regex ne listait que les minuscules accentuées, si bien qu'une majuscule
         * accentuée (« É » en début de phrase, fréquent en kréyòl) était traitée
         * comme un séparateur et coupait le mot en cours.
         */
        internal fun isWordCharacter(character: Char): Boolean = character.isLetter()

        /**
         * Mot en cours de frappe déduit du texte précédant le curseur.
         * `internal` (et non private) pour être testable en JVM sans InputConnection.
         *
         * Une sélection active ne correspond à aucun mot en cours : la remplacer
         * par une suggestion effacerait un texte que l'utilisateur a désigné
         * explicitement, ce qui n'est pas ce que la barre de suggestions promet.
         */
        internal fun resolveCurrentWord(textBeforeCursor: String, hasSelection: Boolean): String {
            if (hasSelection) return ""
            return textBeforeCursor.takeLastWhile { isWordCharacter(it) }
        }

        /**
         * Nombre de caractères de mot situés juste après le curseur, c'est-à-dire
         * la fin du mot que l'utilisateur est en train d'éditer par le milieu.
         * `internal` pour la même raison que ci-dessus.
         */
        internal fun trailingWordLength(textAfterCursor: String): Int =
            textAfterCursor.takeWhile { isWordCharacter(it) }.length
    }
    
    // État du processeur
    private var currentWord = ""
    private var isCapitalMode = false
    private var isCapsLock = false
    private var isNumericMode = false
    private var isEmojiMode = false

    // Callbacks
    interface InputProcessorListener {
        fun onWordChanged(word: String)
        fun onWordCompleted(word: String)
        fun onModeChanged(isNumeric: Boolean, isEmoji: Boolean, isCapital: Boolean, isCapsLock: Boolean)
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
            "EMOJI" -> {
                Log.d(TAG, "Handling emoji panel switch")
                handleEmojiSwitch()
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
        if (character.isNotEmpty() && character.all { isWordCharacter(it) }) {
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
        // Supprimer le(s) caractère(s) précédent(s) dans l'éditeur. La plupart des
        // emojis (panneau emoji, mais aussi ceux tapés via un autre clavier avant de
        // basculer sur Kréyòl) sont hors du plan de base Unicode et occupent une
        // paire de surrogates UTF-16 : supprimer une seule unité laissait un
        // demi-caractère orphelin qui s'affiche comme un glyphe cassé (❓). Les
        // emojis à ton de peau (ex. 💪🏿) vont plus loin : ce sont deux points de
        // code distincts (emoji de base + modificateur de ton), donc 4 unités
        // UTF-16 à supprimer ensemble pour ne pas laisser le modificateur seul.
        val textBeforeCursor = inputConnection.getTextBeforeCursor(4, 0)?.toString() ?: ""
        val deleteLength = calculateBackspaceLength(textBeforeCursor)
        val deleted = inputConnection.deleteSurroundingText(deleteLength, 0)
        
        // Mettre à jour le mot courant
        if (currentWord.isNotEmpty()) {
            currentWord = currentWord.dropLast(1)
            processorListener?.onWordChanged(currentWord)
        }
        
        Log.d(TAG, "Backspace traité, mot courant: '$currentWord'")
        return true
    }

    /**
     * Détermine combien d'unités UTF-16 supprimer avant le curseur pour retirer
     * proprement un seul glyphe : un caractère normal, un emoji hors plan de base
     * (paire de surrogates), ou un emoji + modificateur de ton de peau (deux
     * points de code consécutifs, ex. 💪🏿 = U+1F4AA U+1F3FF).
     */
    private fun calculateBackspaceLength(textBeforeCursor: String): Int {
        if (textBeforeCursor.isEmpty()) return 1
        val end = textBeforeCursor.length
        val lastCodePoint = textBeforeCursor.codePointBefore(end)
        val lastCodePointLength = Character.charCount(lastCodePoint)
        val isSkinToneModifier = lastCodePoint in 0x1F3FB..0x1F3FF

        val precedingEnd = end - lastCodePointLength
        if (isSkinToneModifier && precedingEnd > 0) {
            val precedingCodePoint = textBeforeCursor.codePointBefore(precedingEnd)
            return lastCodePointLength + Character.charCount(precedingCodePoint)
        }
        return lastCodePointLength
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

        processorListener?.onModeChanged(isNumericMode, isEmojiMode, isCapitalMode, isCapsLock)
        Log.d(TAG, "Shift traité - Capital: $isCapitalMode, CapsLock: $isCapsLock")
        return true
    }

    /**
     * Traite le changement de mode (123/ABC). Depuis le panneau emoji, "ABC"
     * remonte ce même chemin : on revient à l'alphabétique plutôt que de
     * togglerNumericMode, ce qui rouvrirait le mode 123 au lieu des lettres.
     */
    private fun handleModeSwitch(): Boolean {
        if (isEmojiMode) {
            isEmojiMode = false
            isNumericMode = false
        } else {
            isNumericMode = !isNumericMode
        }
        processorListener?.onModeChanged(isNumericMode, isEmojiMode, isCapitalMode, isCapsLock)

        Log.d(TAG, "Mode changé - Numérique: $isNumericMode, Emoji: $isEmojiMode")
        return true
    }

    /**
     * Traite l'ouverture du panneau emoji (touche EMOJI du mode 123)
     */
    private fun handleEmojiSwitch(): Boolean {
        isEmojiMode = true
        isNumericMode = false
        processorListener?.onModeChanged(isNumericMode, isEmojiMode, isCapitalMode, isCapsLock)

        Log.d(TAG, "Mode changé - Emoji activé")
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
     * Déplace le curseur de [steps] caractères, négatif vers la gauche
     * (glissement sur la barre d'espace, v14.0.0).
     *
     * Le déplacement passe par des événements de touche directionnelle et non
     * par `setSelection()`. Ce dernier demanderait la position absolue du
     * curseur, que l'IME ne connaît que par `onUpdateSelection()`, c'est-à-dire
     * avec un retard : pendant un glissement rapide on écrirait une position
     * calculée depuis une valeur périmée, et le curseur sauterait. La touche
     * directionnelle laisse au contraire l'éditeur faire le calcul sur son
     * propre état, ce qui règle du même coup les bornes du texte, les retours à
     * la ligne et la sélection en cours, qu'elle réduit comme partout ailleurs.
     * C'est aussi ce que fait le clavier de l'AOSP pour ce même geste.
     */
    fun moveCursorBy(steps: Int) {
        if (steps == 0) return

        val keyCode = if (steps > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
        repeat(kotlin.math.abs(steps)) {
            inputMethodService.sendDownUpKeyEvents(keyCode)
        }
    }

    /**
     * Resynchronise le mot courant avec le texte réellement présent avant le
     * curseur, appelé à chaque déplacement signalé par onUpdateSelection().
     *
     * `currentWord` n'est alimenté que par les frappes : sans cette remise à
     * niveau il divergeait silencieusement du texte dès que le curseur bougeait
     * autrement qu'en tapant (tap dans le texte, retour arrière remontant dans un
     * mot déjà validé, modification par l'application elle-même), et le moteur de
     * suggestions travaillait alors sur un préfixe périmé ou vide.
     *
     * Le mot est relu depuis l'InputConnection plutôt que déduit des positions
     * rapportées : celles-ci peuvent être en retard sur une frappe rapide, alors
     * que le texte lu est toujours l'état courant. Quand la valeur relue est déjà
     * celle attendue, cas de très loin le plus fréquent puisque nos propres
     * commitText/deleteSurroundingText déclenchent aussi ce rappel, on ne touche
     * à rien : le trajet de frappe normal reste intact.
     */
    fun syncWordWithCursor(selectionStart: Int, selectionEnd: Int) {
        val inputConnection = inputMethodService.currentInputConnection ?: return

        val hasSelection = selectionStart != selectionEnd
        val textBefore = if (hasSelection) {
            ""
        } else {
            inputConnection.getTextBeforeCursor(WORD_LOOKAROUND, 0)?.toString() ?: ""
        }
        val wordBeforeCursor = resolveCurrentWord(textBefore, hasSelection)

        if (wordBeforeCursor == currentWord) return

        Log.d(TAG, "Resynchronisation curseur: '$currentWord' -> '$wordBeforeCursor'")
        // setCurrentWord() et non updateCurrentWordSilently() : le rappel est
        // justement ce qui régénère les suggestions pour le nouveau préfixe, ou
        // les vide quand le curseur quitte un mot
        setCurrentWord(wordBeforeCursor)
    }

    /**
     * Traite la sélection d'une suggestion
     */
    fun processSuggestionSelection(suggestion: String): Boolean {
        val inputConnection = inputMethodService.currentInputConnection ?: return false

        // Supprimer le mot partiel actuel
        if (currentWord.isNotEmpty()) {
            // Le curseur peut se trouver au milieu d'un mot, l'utilisateur ayant
            // tapé dedans pour le corriger : la fin du mot doit partir avec le
            // début, sinon la suggestion s'insère devant le reliquat
            // ("bon|jou" + suggestion "bonjou" donnerait "bonjoujou").
            val textAfter = inputConnection.getTextAfterCursor(WORD_LOOKAROUND, 0)?.toString() ?: ""
            inputConnection.deleteSurroundingText(currentWord.length, trailingWordLength(textAfter))
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
     * Finalise le mot courant suite à la sélection d'un emoji via la popup
     * d'appui long (choix d'un ton de peau) : cette popup est partagée avec
     * les accents de lettres, qui eux finalisent différemment (l'accent
     * rejoint le mot en cours au lieu de le clore).
     */
    fun finalizeCurrentWordFromEmoji() {
        finalizeCurrentWord()
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
            processorListener?.onModeChanged(isNumericMode, isEmojiMode, isCapitalMode, isCapsLock)
        } else if (isCapitalMode && !isCapsLock) {
            // Désactiver la majuscule simple après utilisation
            isCapitalMode = false
            processorListener?.onModeChanged(isNumericMode, isEmojiMode, isCapitalMode, isCapsLock)
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
        // Le panneau emoji, lui, ne doit pas persister sur un nouveau champ
        isEmojiMode = false

        processorListener?.onWordChanged("")
        processorListener?.onModeChanged(isNumericMode, isEmojiMode, isCapitalMode, isCapsLock)
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
            isEmojiMode = isEmojiMode,
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
        isEmojiMode = state.isEmojiMode
        currentWord = state.currentWord

        processorListener?.onWordChanged(currentWord)
        processorListener?.onModeChanged(isNumericMode, isEmojiMode, isCapitalMode, isCapsLock)
    }

    /**
     * Classe de données pour l'état du processeur
     */
    data class InputState(
        val isCapitalMode: Boolean = false,
        val isCapsLock: Boolean = false,
        val isNumericMode: Boolean = false,
        val isEmojiMode: Boolean = false,
        val currentWord: String = ""
    )
}
