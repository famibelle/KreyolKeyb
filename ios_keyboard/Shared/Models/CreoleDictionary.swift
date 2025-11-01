//
//  CreoleDictionary.swift
//  Klavyé Kréyòl Karukera
//
//  Created by Potomitan™ on 01/11/2025.
//

import Foundation

class CreoleDictionary {
    
    static let shared = CreoleDictionary()
    
    private var words: [Word] = []
    private var wordSet: Set<String> = []
    private var ngrams: [String: [String]] = [:]
    
    private init() {
        loadDictionary()
        loadNGrams()
    }
    
    private func loadDictionary() {
        guard let url = Bundle.main.url(forResource: "creole_dict", withExtension: "json") else {
            print("❌ Dictionary file not found")
            return
        }
        
        do {
            let data = try Data(contentsOf: url)
            let decoder = JSONDecoder()
            
            // Decode dictionary structure
            let dict = try decoder.decode([String: [Word]].self, from: data)
            
            if let wordList = dict["mots"] {
                words = wordList.sorted { $0.frequency > $1.frequency }
                wordSet = Set(wordList.map { $0.word.lowercased() })
                print("✅ Loaded \(words.count) words from dictionary")
            }
        } catch {
            print("❌ Error loading dictionary: \(error)")
        }
    }
    
    private func loadNGrams() {
        guard let url = Bundle.main.url(forResource: "creole_ngrams", withExtension: "json") else {
            print("❌ N-grams file not found")
            return
        }
        
        do {
            let data = try Data(contentsOf: url)
            let decoder = JSONDecoder()
            ngrams = try decoder.decode([String: [String]].self, from: data)
            print("✅ Loaded \(ngrams.count) n-gram patterns")
        } catch {
            print("❌ Error loading n-grams: \(error)")
        }
    }
    
    func getSuggestions(for prefix: String, limit: Int = 3) -> [String] {
        guard !prefix.isEmpty else { return [] }
        
        let lowercasePrefix = prefix.lowercased()
        var suggestions: [String] = []
        
        // 1. Check n-grams first (most common patterns)
        if let ngramSuggestions = ngrams[lowercasePrefix] {
            suggestions.append(contentsOf: ngramSuggestions.prefix(limit))
        }
        
        // 2. If we need more, search dictionary
        if suggestions.count < limit {
            let remaining = limit - suggestions.count
            let dictionaryMatches = words
                .filter { $0.word.lowercased().hasPrefix(lowercasePrefix) && !suggestions.contains($0.word) }
                .prefix(remaining)
                .map { $0.word }
            
            suggestions.append(contentsOf: dictionaryMatches)
        }
        
        return Array(suggestions.prefix(limit))
    }
    
    func isCreoleWord(_ word: String) -> Bool {
        return wordSet.contains(word.lowercased())
    }
    
    func getWordFrequency(_ word: String) -> Int? {
        return words.first { $0.word.lowercased() == word.lowercased() }?.frequency
    }
    
    func getAllWords() -> [Word] {
        return words
    }
}
