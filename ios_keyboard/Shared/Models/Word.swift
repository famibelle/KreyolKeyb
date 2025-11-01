//
//  Word.swift
//  Klavyé Kréyòl Karukera
//
//  Created by Potomitan™ on 01/11/2025.
//

import Foundation

struct Word: Codable, Hashable {
    let word: String
    let frequency: Int
    
    enum CodingKeys: String, CodingKey {
        case word = "mot"
        case frequency = "frequence"
    }
}

struct WordStatistic: Identifiable {
    let id = UUID()
    let word: String
    let count: Int
}
