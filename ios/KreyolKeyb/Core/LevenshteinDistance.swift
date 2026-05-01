// LevenshteinDistance.swift
// KreyolKeyb — iOS
//
// Portage de LevenshteinDistance.kt
// Correction orthographique par distance d'édition

import Foundation

enum LevenshteinDistance {

    // MARK: — Calcul de base

    /// Calcule la distance de Levenshtein entre deux chaînes (programmation dynamique)
    static func calculate(_ s1: String, _ s2: String) -> Int {
        let a = Array(s1.lowercased())
        let b = Array(s2.lowercased())
        let m = a.count, n = b.count

        if m == 0 { return n }
        if n == 0 { return m }

        var dp = Array(repeating: Array(repeating: 0, count: n + 1), count: m + 1)

        for i in 0...m { dp[i][0] = i }
        for j in 0...n { dp[0][j] = j }

        for i in 1...m {
            for j in 1...n {
                let cost = a[i - 1] == b[j - 1] ? 0 : 1
                dp[i][j] = Swift.min(
                    dp[i - 1][j] + 1,         // suppression
                    dp[i][j - 1] + 1,         // insertion
                    dp[i - 1][j - 1] + cost   // substitution
                )
            }
        }

        return dp[m][n]
    }

    /// Distance avec normalisation des accents
    static func calculateNormalized(
        _ s1: String,
        _ s2: String,
        normalizer: (String) -> String = { $0 }
    ) -> Int {
        calculate(normalizer(s1), normalizer(s2))
    }

    // MARK: — Recherche dans le dictionnaire

    /// Trouve les mots les plus proches dans un dictionnaire
    static func findClosestMatches(
        input: String,
        dictionary: [(word: String, frequency: Int)],
        maxDistance: Int = 2,
        maxResults: Int = 5,
        lengthTolerance: Int = 2
    ) -> [(word: String, frequency: Int)] {
        guard !input.isEmpty else { return [] }

        return dictionary
            .filter { abs($0.word.count - input.count) <= lengthTolerance }
            .compactMap { entry -> (word: String, frequency: Int, distance: Int)? in
                let d = calculate(input, entry.word)
                return d <= maxDistance ? (entry.word, entry.frequency, d) : nil
            }
            .sorted {
                if $0.distance != $1.distance { return $0.distance < $1.distance }
                return $0.frequency > $1.frequency
            }
            .prefix(maxResults)
            .map { (word: $0.word, frequency: $0.frequency) }
    }

    /// Trouve les mots les plus proches avec normalisation des accents
    static func findClosestMatchesNormalized(
        input: String,
        dictionary: [(word: String, frequency: Int)],
        normalizer: @escaping (String) -> String,
        maxDistance: Int = 2,
        maxResults: Int = 5
    ) -> [(word: String, frequency: Int)] {
        guard !input.isEmpty else { return [] }

        let normalizedInput = normalizer(input)

        return dictionary
            .filter { abs(normalizer($0.word).count - normalizedInput.count) <= 2 }
            .compactMap { entry -> (word: String, frequency: Int, distance: Int)? in
                let d = calculate(normalizedInput, normalizer(entry.word))
                return d <= maxDistance ? (entry.word, entry.frequency, d) : nil
            }
            .sorted {
                if $0.distance != $1.distance { return $0.distance < $1.distance }
                return $0.frequency > $1.frequency
            }
            .prefix(maxResults)
            .map { (word: $0.word, frequency: $0.frequency) }
    }

    // MARK: — Cache

    private static var cache: [String: Int] = [:]

    static func calculateCached(_ s1: String, _ s2: String) -> Int {
        let key = "\(s1.lowercased())|\(s2.lowercased())"
        if let cached = cache[key] { return cached }
        let result = calculate(s1, s2)
        cache[key] = result
        return result
    }

    static func clearCache() {
        cache.removeAll()
    }
}
