//
//  VocabularyTracker.swift
//  Klavyé Kréyòl Karukera
//
//  Created by Potomitan™ on 01/11/2025.
//

import Foundation

class VocabularyTracker: ObservableObject {
    
    static let shared = VocabularyTracker()
    
    @Published var totalWords: Int = 0
    @Published var uniqueWords: Int = 0
    @Published var topWords: [WordStatistic] = []
    
    private var wordCounts: [String: Int] = [:]
    private let userDefaults: UserDefaults?
    
    private let kTotalWordsKey = "totalWords"
    private let kWordCountsKey = "wordCounts"
    
    private init() {
        // Use App Group shared UserDefaults
        self.userDefaults = UserDefaults(suiteName: "group.com.potomitan.kreyolkeyboard")
        loadStats()
    }
    
    func addWord(_ word: String) {
        let cleanWord = word.lowercased().trimmingCharacters(in: .punctuationCharacters)
        guard !cleanWord.isEmpty else { return }
        
        // Update counts
        totalWords += 1
        wordCounts[cleanWord, default: 0] += 1
        uniqueWords = wordCounts.count
        
        // Update top words
        updateTopWords()
        
        // Save to UserDefaults
        saveStats()
    }
    
    private func updateTopWords() {
        topWords = wordCounts
            .map { WordStatistic(word: $0.key, count: $0.value) }
            .sorted { $0.count > $1.count }
    }
    
    private func saveStats() {
        userDefaults?.set(totalWords, forKey: kTotalWordsKey)
        
        if let encoded = try? JSONEncoder().encode(wordCounts) {
            userDefaults?.set(encoded, forKey: kWordCountsKey)
        }
    }
    
    private func loadStats() {
        totalWords = userDefaults?.integer(forKey: kTotalWordsKey) ?? 0
        
        if let data = userDefaults?.data(forKey: kWordCountsKey),
           let decoded = try? JSONDecoder().decode([String: Int].self, from: data) {
            wordCounts = decoded
            uniqueWords = wordCounts.count
            updateTopWords()
        }
    }
    
    func resetStats() {
        totalWords = 0
        uniqueWords = 0
        wordCounts.removeAll()
        topWords.removeAll()
        
        userDefaults?.removeObject(forKey: kTotalWordsKey)
        userDefaults?.removeObject(forKey: kWordCountsKey)
    }
    
    func getWordCount(for word: String) -> Int {
        return wordCounts[word.lowercased()] ?? 0
    }
}
