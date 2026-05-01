// BilingualSuggestion.swift
// KreyolKeyb — iOS
//
// Portage de BilingualSuggestion.kt

import SwiftUI

// MARK: — Langue

enum SuggestionLanguage {
    case kreyol
    case french

    var displayName: String {
        switch self {
        case .kreyol: return "Kreyòl"
        case .french: return "Français"
        }
    }

    var color: Color {
        switch self {
        case .kreyol: return KeyboardColors.kreyolGreen
        case .french: return KeyboardColors.frenchBlue
        }
    }
}

// MARK: — Source

enum SuggestionSource {
    case dictionary
    case ngram
    case learned
    case hybrid
}

// MARK: — Suggestion bilingue

struct BilingualSuggestion: Identifiable, Equatable {
    let id = UUID()
    let word: String
    let score: Float
    let language: SuggestionLanguage
    let source: SuggestionSource

    init(word: String, score: Float, language: SuggestionLanguage, source: SuggestionSource = .dictionary) {
        self.word = word
        self.score = score
        self.language = language
        self.source = source
    }

    func withWord(_ newWord: String) -> BilingualSuggestion {
        BilingualSuggestion(word: newWord, score: score, language: language, source: source)
    }

    static func == (lhs: BilingualSuggestion, rhs: BilingualSuggestion) -> Bool {
        lhs.word == rhs.word && lhs.language == rhs.language
    }
}

// MARK: — Couleurs du clavier

enum KeyboardColors {
    static let kreyolGreen  = Color(hex: "#50C878")  // Vert émeraude
    static let frenchBlue   = Color(hex: "#4A90E2")  // Bleu France
    static let background   = Color(hex: "#F8F9FA")
    static let borderLight  = Color(hex: "#E9ECEF")
    static let textPrimary  = Color(hex: "#212529")
    static let textSecondary = Color(hex: "#6C757D")
}

// MARK: — Configuration bilingue

struct BilingualConfig {
    var frenchActivationThreshold: Int = 3
    var maxKreyolSuggestions: Int = 3
    var maxFrenchSuggestions: Int = 2
    var kreyolPriorityBoost: Float = 1.5
    var frenchPenalty: Float = 0.8
    var enableFrenchSupport: Bool = true
    var kreyolOnlyMode: Bool = false
    var showLanguageIndicators: Bool = true

    func shouldActivateFrench(input: String) -> Bool {
        enableFrenchSupport && !kreyolOnlyMode && input.count >= frenchActivationThreshold
    }

    func adjustScore(_ score: Float, for language: SuggestionLanguage) -> Float {
        switch language {
        case .kreyol: return score * kreyolPriorityBoost
        case .french: return score * frenchPenalty
        }
    }
}

// MARK: — Extension Color

extension Color {
    init(hex: String) {
        let hex = hex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
        var int: UInt64 = 0
        Scanner(string: hex).scanHexInt64(&int)
        let r = Double((int >> 16) & 0xFF) / 255
        let g = Double((int >> 8)  & 0xFF) / 255
        let b = Double(int         & 0xFF) / 255
        self.init(red: r, green: g, blue: b)
    }
}
