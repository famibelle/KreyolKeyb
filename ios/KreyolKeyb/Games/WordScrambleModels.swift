// WordScrambleModels.swift
// KreyolKeyb — iOS
//
// Portage de WordScrambleModels.kt

import Foundation

// MARK: — Difficulté

enum ScrambleDifficulty: String, CaseIterable, Identifiable {
    case easy   = "Facile"
    case normal = "Normal"
    case hard   = "Difficile"

    var id: String { rawValue }

    var letterRange: ClosedRange<Int> {
        switch self { case .easy: return 4...5; case .normal: return 5...7; case .hard: return 7...10 }
    }

    var timeLimit: Int {
        switch self { case .easy: return 45; case .normal: return 30; case .hard: return 20 }
    }
}

// MARK: — Modèles

struct ScrambledWord {
    let originalWord: String
    let scrambledLetters: [Character]
    var currentAnswer: [Character?]
    var hintsUsed: Int = 0

    init(originalWord: String) {
        self.originalWord = originalWord
        self.scrambledLetters = WordScrambleData.scramble(originalWord)
        self.currentAnswer = Array(repeating: nil, count: originalWord.count)
    }

    var isComplete: Bool {
        currentAnswer.compactMap { $0 }.count == originalWord.count
    }

    var isSolved: Bool {
        String(currentAnswer.compactMap { $0 }) == originalWord
    }
}

struct WordScrambleGame {
    let words: [String]
    var currentWordIndex: Int = 0
    var score: Int = 0
    let difficulty: ScrambleDifficulty

    var currentWord: String? {
        guard currentWordIndex < words.count else { return nil }
        return words[currentWordIndex]
    }

    var isFinished: Bool { currentWordIndex >= words.count }
}

// MARK: — Données

enum WordScrambleData {

    static func loadWords(from dictionary: [(word: String, frequency: Int)], difficulty: ScrambleDifficulty) -> [String] {
        dictionary
            .map { $0.word }
            .filter { difficulty.letterRange.contains($0.count) }
            .shuffled()
            .prefix(10)
            .map { $0 }
    }

    static func scramble(_ word: String) -> [Character] {
        var letters = Array(word)
        for _ in 0..<10 {
            letters.shuffle()
            if String(letters) != word { break }
        }
        return letters
    }
}
