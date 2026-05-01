// WordUsageStats.swift
// KreyolKeyb — iOS
//
// Portage de WordUsageStats.kt + VocabularyStats.kt

import Foundation

// MARK: — Stats par mot

struct WordUsageStats: Identifiable, Codable {
    var id: String { word }
    let word: String
    var userCount: Int
    let frequency: Int

    /// Maîtrisé si utilisé 10 fois ou plus
    var isMastered: Bool { userCount >= 10 }

    /// Récemment découvert si utilisé entre 1 et 3 fois
    var isRecentlyDiscovered: Bool { (1...3).contains(userCount) }
}

// MARK: — Stats globales du vocabulaire

struct VocabularyStats {
    let coveragePercentage: Float
    let wordsDiscovered: Int
    let totalWords: Int
    let totalUsages: Int
    let topWords: [WordUsageStats]
    let recentWords: [String]
    let masteredWords: Int
}

// MARK: — Niveaux de gamification

enum KreyolLevel: Int, CaseIterable {
    case pipirit   = 0
    case tiMoun    = 1
    case debrouya  = 2
    case anMitan   = 3
    case kompeLapen = 4
    case kompeZamba = 5
    case potomitan = 6

    var displayName: String {
        switch self {
        case .pipirit:    return "Pipirit 🌍"
        case .tiMoun:     return "Ti moun 🌱"
        case .debrouya:   return "Débrouya 🔥"
        case .anMitan:    return "An mitan 💎"
        case .kompeLapen: return "Kompè Lapen 🐇"
        case .kompeZamba: return "Kompè Zamba 🐘"
        case .potomitan:  return "Potomitan 👑"
        }
    }

    var description: String {
        switch self {
        case .pipirit:    return "Débutant absolu"
        case .tiMoun:     return "Tu commences"
        case .debrouya:   return "Tu te débrouilles"
        case .anMitan:    return "Niveau intermédiaire"
        case .kompeLapen: return "Bonne connaissance"
        case .kompeZamba: return "Maîtrise avancée"
        case .potomitan:  return "Expert — pilier de la langue"
        }
    }

    /// Calcule le niveau selon le pourcentage de couverture (distribution gaussienne)
    static func level(for coveragePercentage: Float) -> KreyolLevel {
        switch coveragePercentage {
        case 0..<2:    return .pipirit
        case 2..<8:    return .tiMoun
        case 8..<20:   return .debrouya
        case 20..<40:  return .anMitan
        case 40..<60:  return .kompeLapen
        case 60..<85:  return .kompeZamba
        default:       return .potomitan
        }
    }
}
