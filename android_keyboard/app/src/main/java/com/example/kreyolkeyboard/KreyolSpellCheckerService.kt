package com.example.kreyolkeyboard

import android.service.textservice.SpellCheckerService
import android.util.Log
import android.view.textservice.SuggestionsInfo
import android.view.textservice.TextInfo
import kotlinx.coroutines.runBlocking

/**
 * Service système de vérification orthographique (SpellCheckerService), distinct du
 * clavier IME : n'importe quel champ de texte (Messages, Notes, WhatsApp...) peut
 * interroger ce service une fois sélectionné dans Paramètres > Langues et saisie >
 * Vérification orthographique.
 *
 * Réutilise SuggestionEngine (dictionnaires kréyòl + français, correction Levenshtein)
 * sans dupliquer ni le chargement du JSON ni le scoring.
 */
class KreyolSpellCheckerService : SpellCheckerService() {

    companion object {
        private const val TAG = "KreyolSpellChecker"
        private const val DEFAULT_SUGGESTIONS_LIMIT = 5
    }

    override fun createSession(): Session = KreyolSpellCheckerSession()

    private inner class KreyolSpellCheckerSession : Session() {

        private lateinit var suggestionEngine: SuggestionEngine

        override fun onCreate() {
            suggestionEngine = SuggestionEngine(applicationContext)
            // onCreate()/onGetSuggestions*() tournent sur un handler thread interne à
            // SpellCheckerService (jamais le thread UI de l'app dans laquelle on tape) :
            // un chargement bloquant du dictionnaire ici est le pattern standard.
            runBlocking { suggestionEngine.initialize() }
            Log.d(TAG, "Session créée, dictionnaires chargés")
        }

        override fun onGetSuggestions(textInfo: TextInfo?, suggestionsLimit: Int): SuggestionsInfo {
            return buildSuggestionsInfo(textInfo, suggestionsLimit)
        }

        override fun onGetSuggestionsMultiple(
            textInfos: Array<TextInfo>?,
            suggestionsLimit: Int,
            sequentialWords: Boolean
        ): Array<SuggestionsInfo> {
            return textInfos?.map { buildSuggestionsInfo(it, suggestionsLimit) }?.toTypedArray()
                ?: emptyArray()
        }

        // onGetSentenceSuggestionsMultiple() n'est PAS surchargée : l'implémentation par
        // défaut de la classe de base tokenize la phrase et délègue déjà à
        // onGetSuggestionsMultiple() ci-dessus — c'est le chemin réel emprunté par
        // TextView via SpellCheckerSession.getSentenceSuggestions() sur API 21+.

        private fun buildSuggestionsInfo(textInfo: TextInfo?, suggestionsLimit: Int): SuggestionsInfo {
            val word = textInfo?.text?.trim().orEmpty()

            if (word.isEmpty() || word.none { it.isLetter() }) {
                return SuggestionsInfo(SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY, emptyArray())
            }
            if (suggestionEngine.isKnownWord(word)) {
                return SuggestionsInfo(SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY, emptyArray())
            }

            val limit = if (suggestionsLimit > 0) suggestionsLimit else DEFAULT_SUGGESTIONS_LIMIT
            val suggestions = suggestionEngine.getSpellingSuggestions(word, limit)
            return SuggestionsInfo(SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO, suggestions.toTypedArray())
        }
    }
}
