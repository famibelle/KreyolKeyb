// CreoleDictionaryWithUsage.swift
// KreyolKeyb — iOS
//
// Portage de CreoleDictionaryWithUsage.kt
// Suivi d'utilisation du vocabulaire créole — données 100% locales

import Foundation

actor CreoleDictionaryWithUsage {

    // MARK: — Constantes

    private static let dictFile = "creole_dict_with_usage"
    private static let originalDictAsset = "creole_dict"
    private static let minWordLength = 3

    // MARK: — État interne

    // word → (frequency, userCount)
    private var dictionary: [String: (frequency: Int, userCount: Int)] = [:]
    private var unsavedChanges = 0

    // MARK: — Init

    init() async {
        await loadDictionary()
    }

    // MARK: — Chargement

    private func loadDictionary() async {
        let fileURL = Self.usageFileURL()

        if FileManager.default.fileExists(atPath: fileURL.path),
           let data = try? Data(contentsOf: fileURL),
           let decoded = try? JSONDecoder().decode([String: WordEntry].self, from: data) {
            dictionary = decoded.mapValues { (frequency: $0.frequency, userCount: $0.userCount) }
        } else {
            await migrateDictionary()
        }
    }

    private func migrateDictionary() async {
        guard let url = Bundle.main.url(forResource: Self.originalDictAsset, withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let rawArray = try? JSONSerialization.jsonObject(with: data) as? [[Any]] else {
            return
        }

        var migrated: [String: (frequency: Int, userCount: Int)] = [:]
        for entry in rawArray {
            if let word = entry[0] as? String, let freq = entry[1] as? Int {
                migrated[word] = (frequency: freq, userCount: 0)
            }
        }
        dictionary = migrated
        await saveDictionary()
    }

    // MARK: — Suivi d'utilisation

    @discardableResult
    func incrementWordUsage(_ word: String) -> Bool {
        let normalized = word.lowercased().trimmingCharacters(in: .whitespaces)
        guard isValidForTracking(normalized) else { return false }
        guard dictionary[normalized] != nil else { return false }

        dictionary[normalized]!.userCount += 1
        unsavedChanges += 1

        if unsavedChanges >= 1 {
            Task { await saveDictionary() }
            unsavedChanges = 0
        }
        return true
    }

    private func isValidForTracking(_ word: String) -> Bool {
        guard word.count >= Self.minWordLength else { return false }
        guard !word.contains(where: { $0.isNumber }) else { return false }
        guard !word.contains("http"), !word.contains("www"), !word.contains(".com") else { return false }
        guard !word.contains("@") else { return false }
        return true
    }

    // MARK: — Statistiques

    func getWordUsageCount(_ word: String) -> Int {
        dictionary[word.lowercased()]?.userCount ?? 0
    }

    func getCoveragePercentage() -> Float {
        guard !dictionary.isEmpty else { return 0 }
        let used = dictionary.values.filter { $0.userCount > 0 }.count
        return Float(used) / Float(dictionary.count) * 100
    }

    func getDiscoveredWordsCount() -> Int {
        dictionary.values.filter { $0.userCount > 0 }.count
    }

    func getTotalUsageCount() -> Int {
        dictionary.values.reduce(0) { $0 + $1.userCount }
    }

    func getMasteredWordsCount() -> Int {
        dictionary.values.filter { $0.userCount >= 10 }.count
    }

    func getTopUsedWords(limit: Int = 10) -> [WordUsageStats] {
        dictionary
            .filter { $0.value.userCount > 0 }
            .map { WordUsageStats(word: $0.key, userCount: $0.value.userCount, frequency: $0.value.frequency) }
            .sorted { $0.userCount > $1.userCount }
            .prefix(limit)
            .map { $0 }
    }

    func getRecentlyDiscoveredWords(limit: Int = 5) -> [String] {
        dictionary
            .filter { (1...3).contains($0.value.userCount) }
            .map { $0.key }
            .prefix(limit)
            .map { $0 }
    }

    func getVocabularyStats() -> VocabularyStats {
        VocabularyStats(
            coveragePercentage: getCoveragePercentage(),
            wordsDiscovered: getDiscoveredWordsCount(),
            totalWords: dictionary.count,
            totalUsages: getTotalUsageCount(),
            topWords: getTopUsedWords(),
            recentWords: getRecentlyDiscoveredWords(),
            masteredWords: getMasteredWordsCount()
        )
    }

    // MARK: — Persistance

    func saveDictionary() async {
        let encoded = dictionary.mapValues { WordEntry(frequency: $0.frequency, userCount: $0.userCount) }
        guard let data = try? JSONEncoder().encode(encoded) else { return }
        try? data.write(to: Self.usageFileURL(), options: .atomic)
    }

    func resetAllUserCounts() async {
        for key in dictionary.keys {
            dictionary[key]!.userCount = 0
        }
        await saveDictionary()
    }

    // MARK: — Helpers

    private static func usageFileURL() -> URL {
        // En production : App Group partagé Extension ↔ App
        // let container = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: "group.com.potomitan.kreyolkeyb")!
        let container = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        return container.appendingPathComponent("\(dictFile).json")
    }

    // MARK: — Type interne pour la sérialisation

    private struct WordEntry: Codable {
        let frequency: Int
        var userCount: Int
    }
}
