// SuggestionEngine.swift
// KreyolKeyb — iOS
//
// Portage de SuggestionEngine.kt
// À la mémoire de Saint-Ange Corneille Famibelle

import Foundation

@MainActor
final class SuggestionEngine: ObservableObject {

    // MARK: — Constantes

    private static let maxSuggestions = 5
    private static let maxWordHistory = 5
    private static let minWordLength = 2

    // MARK: — État

    private var kreyolDictionary: [(word: String, frequency: Int)] = []
    private var ngramModel: [String: [(word: String, probability: Double)]] = [:]
    private var wordHistory: [String] = []

    private var frenchDictionary: [(word: String, frequency: Int)] = []
    private(set) var bilingualConfig = BilingualConfig()
    private(set) var isLoaded = false

    @Published var suggestions: [BilingualSuggestion] = []

    // MARK: — Chargement

    func initialize() async {
        async let kreyol: Void = loadKreyolDictionary()
        async let ngram:  Void = loadNgramModel()
        async let french: Void = loadFrenchDictionary()
        _ = await (kreyol, ngram, french)
        isLoaded = true
    }

    private func loadKreyolDictionary() async {
        guard let url = Bundle.main.url(forResource: "creole_dict", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let rawArray = try? JSONSerialization.jsonObject(with: data) as? [[Any]] else { return }

        kreyolDictionary = rawArray.compactMap { entry in
            guard let word = entry[0] as? String, let freq = entry[1] as? Int else { return nil }
            return (word: word.lowercased(), frequency: freq)
        }.sorted { $0.frequency > $1.frequency }
    }

    private func loadNgramModel() async {
        guard let url = Bundle.main.url(forResource: "creole_ngrams", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: [[String: Any]]] else { return }

        ngramModel = json.mapValues { entries in
            entries.compactMap { entry -> (word: String, probability: Double)? in
                guard let word = entry["word"] as? String,
                      let prob = entry["probability"] as? Double else { return nil }
                return (word: word, probability: prob)
            }
        }
    }

    private func loadFrenchDictionary() async {
        guard let url = Bundle.main.url(forResource: "french_simple_dict", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let rawArray = try? JSONSerialization.jsonObject(with: data) as? [[Any]] else { return }

        frenchDictionary = rawArray.compactMap { entry in
            guard let word = entry[0] as? String else { return nil }
            let freq = entry.count > 1 ? (entry[1] as? Int ?? 1) : 1
            return (word: word.lowercased(), frequency: freq)
        }
    }

    // MARK: — Génération de suggestions

    func generateSuggestions(for input: String) {
        guard input.count >= Self.minWordLength else {
            suggestions = []
            return
        }

        Task.detached(priority: .userInitiated) { [weak self] in
            guard let self else { return }
            let result = await self.buildBilingualSuggestions(input: input)
            await MainActor.run { self.suggestions = result }
        }
    }

    private func buildBilingualSuggestions(input: String) async -> [BilingualSuggestion] {
        let kreyol = getKreyolSuggestions(input: input)
        let french = bilingualConfig.shouldActivateFrench(input: input)
            ? getFrenchSuggestions(input: input)
            : []
        return mergeSuggestionsKreyolFirst(kreyol: kreyol, french: french)
            .map { $0.withWord(Self.applyCasingPattern(input: input, suggestion: $0.word)) }
    }

    // MARK: — Suggestions Kreyòl

    private func getKreyolSuggestions(input: String) -> [BilingualSuggestion] {
        var scores: [String: Float] = [:]

        // Recherche par préfixe insensible aux accents
        let prefixMatches = AccentTolerantMatcher.findSuggestions(
            input: input,
            dictionary: kreyolDictionary,
            maxResults: Self.maxSuggestions * 2
        )

        // Si aucun préfixe, correction orthographique
        let dictMatches: [(word: String, frequency: Int)]
        if prefixMatches.isEmpty && input.count >= 3 {
            dictMatches = LevenshteinDistance.findClosestMatchesNormalized(
                input: input,
                dictionary: kreyolDictionary,
                normalizer: AccentTolerantMatcher.normalize,
                maxDistance: 2,
                maxResults: Self.maxSuggestions
            )
        } else {
            dictMatches = prefixMatches
        }

        for match in dictMatches {
            scores[match.word] = Float(calculateDictionaryScore(word: match.word, input: input, frequency: match.frequency))
        }

        // Bonus N-gram contextuel
        for word in getNgramSuggestions() {
            scores[word, default: 0] += 50
        }

        return scores
            .map { word, score in
                let adjusted = bilingualConfig.adjustScore(score, for: .kreyol)
                return BilingualSuggestion(word: word, score: adjusted, language: .kreyol, source: .hybrid)
            }
            .sorted { $0.score > $1.score }
            .prefix(bilingualConfig.maxKreyolSuggestions)
            .map { $0 }
    }

    // MARK: — Suggestions Français

    private func getFrenchSuggestions(input: String) -> [BilingualSuggestion] {
        let matches = AccentTolerantMatcher.findSuggestions(
            input: input,
            dictionary: frenchDictionary,
            maxResults: bilingualConfig.maxFrenchSuggestions
        )
        return matches.map { match in
            let base = Float(calculateDictionaryScore(word: match.word, input: input, frequency: match.frequency))
            let adjusted = bilingualConfig.adjustScore(base, for: .french)
            return BilingualSuggestion(word: match.word, score: adjusted, language: .french, source: .dictionary)
        }.sorted { $0.score > $1.score }
    }

    // MARK: — Fusion Kreyòl-First

    private func mergeSuggestionsKreyolFirst(
        kreyol: [BilingualSuggestion],
        french: [BilingualSuggestion]
    ) -> [BilingualSuggestion] {
        var result: [BilingualSuggestion] = []
        var used = Set<String>()

        for s in kreyol.prefix(3) where !used.contains(s.word.lowercased()) {
            result.append(s); used.insert(s.word.lowercased())
        }
        for s in french.prefix(2) where result.count < Self.maxSuggestions && !used.contains(s.word.lowercased()) {
            result.append(s); used.insert(s.word.lowercased())
        }
        for s in kreyol.dropFirst(3) where result.count < Self.maxSuggestions && !used.contains(s.word.lowercased()) {
            result.append(s); used.insert(s.word.lowercased())
        }

        return result
    }

    // MARK: — N-grams

    private func getNgramSuggestions() -> [String] {
        guard !wordHistory.isEmpty, !ngramModel.isEmpty else { return [] }

        var candidates: [(word: String, probability: Double)] = []

        // Bigram
        if wordHistory.count >= 2 {
            let bigram = "\(wordHistory[wordHistory.count - 2]) \(wordHistory.last!)"
            ngramModel[bigram]?.forEach { entry in
                if !candidates.contains(where: { $0.word == entry.word }) {
                    candidates.append((word: entry.word, probability: entry.probability + 0.2))
                }
            }
        }

        // Unigram
        if let last = wordHistory.last {
            ngramModel[last]?.forEach { entry in
                if !candidates.contains(where: { $0.word == entry.word }) {
                    candidates.append((word: entry.word, probability: entry.probability))
                }
            }
        }

        // Trigram
        if wordHistory.count >= 3 {
            let trigram = "\(wordHistory[wordHistory.count - 3]) \(wordHistory[wordHistory.count - 2]) \(wordHistory.last!)"
            ngramModel[trigram]?.forEach { entry in
                if !candidates.contains(where: { $0.word == entry.word }) {
                    candidates.append((word: entry.word, probability: entry.probability + 0.4))
                }
            }
        }

        return candidates.sorted { $0.probability > $1.probability }.prefix(Self.maxSuggestions).map { $0.word }
    }

    // MARK: — Score

    private func calculateDictionaryScore(word: String, input: String, frequency: Int) -> Double {
        var score = Double(frequency)
        if word.lowercased().hasPrefix(input.lowercased()) { score += 50 }
        if word.count <= 6  { score += 10 }
        if word.count > 12  { score -= 10 }
        if AccentTolerantMatcher.hasAccents(word) { score += 5 }
        return score
    }

    // MARK: — Historique

    func addWordToHistory(_ word: String) {
        let clean = word.lowercased().trimmingCharacters(in: .whitespaces)
        guard clean.count >= Self.minWordLength else { return }
        wordHistory.append(clean)
        if wordHistory.count > Self.maxWordHistory { wordHistory.removeFirst() }
    }

    func clearHistory() { wordHistory.removeAll() }

    // MARK: — Casse

    private static func applyCasingPattern(input: String, suggestion: String) -> String {
        guard !input.isEmpty, !suggestion.isEmpty else { return suggestion }

        let inputChars = Array(input)
        let suggChars  = Array(suggestion)

        if inputChars.allSatisfy({ !$0.isLetter || $0.isUppercase }) {
            return suggestion.uppercased()
        }
        if inputChars.first?.isUppercase == true && inputChars.dropFirst().allSatisfy({ !$0.isLetter || $0.isLowercase }) {
            return suggestion.prefix(1).uppercased() + suggestion.dropFirst().lowercased()
        }

        var result = ""
        for (i, char) in suggChars.enumerated() {
            if i < inputChars.count {
                result += inputChars[i].isUppercase ? String(char).uppercased() : String(char).lowercased()
            } else {
                result.append(char)
            }
        }
        return result
    }

    // MARK: — Configuration

    func setBilingualConfig(_ config: BilingualConfig) {
        bilingualConfig = config
    }

    func setFrenchSupport(_ enabled: Bool) {
        bilingualConfig.enableFrenchSupport = enabled
    }

    func setKreyolOnlyMode(_ kreyolOnly: Bool) {
        bilingualConfig.kreyolOnlyMode = kreyolOnly
    }
}
