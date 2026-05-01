// AccentTolerantMatcher.swift
// KreyolKeyb — iOS
//
// Portage de AccentTolerantMatcher.kt
// Permet de taper "kre" et obtenir "kréyòl"

import Foundation

enum AccentTolerantMatcher {

    // MARK: — Normalisation

    /// Supprime tous les accents et met en minuscules
    static func normalize(_ text: String) -> String {
        guard !text.isEmpty else { return text }
        return text
            .folding(options: .diacriticInsensitive, locale: .current)
            .lowercased()
    }

    /// Vérifie si deux mots correspondent après normalisation
    static func matches(_ input: String, _ target: String) -> Bool {
        normalize(input) == normalize(target)
    }

    /// Vérifie si un mot du dictionnaire commence par l'input normalisé
    static func startsWith(_ input: String, dictionaryWord: String) -> Bool {
        normalize(dictionaryWord).hasPrefix(normalize(input))
    }

    // MARK: — Recherche

    /// Trouve toutes les suggestions insensibles aux accents
    static func findSuggestions(
        input: String,
        dictionary: [(word: String, frequency: Int)],
        maxResults: Int = 10
    ) -> [(word: String, frequency: Int)] {
        guard input.count >= 2 else { return [] }

        let normalizedInput = normalize(input)

        return dictionary
            .filter { normalize($0.word).hasPrefix(normalizedInput) }
            .sorted { $0.frequency > $1.frequency }
            .prefix(maxResults)
            .map { $0 }
    }

    // MARK: — Score

    /// Calcule un score de pertinence pour un match insensible aux accents
    static func calculateMatchScore(input: String, matchedWord: String, frequency: Int) -> Double {
        let normalizedInput = normalize(input)
        let normalizedMatch = normalize(matchedWord)

        var score = Double(frequency)

        if normalizedInput == normalizedMatch {
            score += 100.0
        } else if normalizedMatch.hasPrefix(normalizedInput) {
            let prefixBonus = (Double(normalizedInput.count) / Double(normalizedMatch.count)) * 50.0
            score += prefixBonus
        }

        if matchedWord.count <= 6  { score += 10.0 }
        if matchedWord.count > 12  { score -= 5.0  }
        if hasAccents(matchedWord) { score += 5.0  }

        return score
    }

    /// Vérifie si un mot contient des accents
    static func hasAccents(_ word: String) -> Bool {
        word != normalize(word)
    }
}
