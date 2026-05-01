// WordSearchModels.swift
// KreyolKeyb — iOS
//
// Portage de WordSearchModels.kt

import Foundation

// MARK: — Direction

enum WordDirection: CaseIterable {
    case horizontal, vertical
    case diagonalDownRight, diagonalDownLeft
    case horizontalReverse, verticalReverse
    case diagonalUpRight, diagonalUpLeft

    var delta: (row: Int, col: Int) {
        switch self {
        case .horizontal:        return (0,  1)
        case .vertical:          return (1,  0)
        case .diagonalDownRight: return (1,  1)
        case .diagonalDownLeft:  return (1, -1)
        case .horizontalReverse: return (0, -1)
        case .verticalReverse:   return (-1, 0)
        case .diagonalUpRight:   return (-1, 1)
        case .diagonalUpLeft:    return (-1,-1)
        }
    }
}

// MARK: — Difficulté

enum WordSearchDifficulty: String, CaseIterable, Identifiable {
    case easy   = "Facile"
    case normal = "Normal"
    case hard   = "Difficile"
    case expert = "Expert"

    var id: String { rawValue }

    var wordCount: Int {
        switch self { case .easy: return 3; case .normal: return 5; case .hard: return 6; case .expert: return 8 }
    }

    var allowedDirections: [WordDirection] {
        switch self {
        case .easy:   return [.horizontal, .vertical]
        case .normal: return [.horizontal, .vertical, .diagonalDownRight, .diagonalDownLeft]
        case .hard:   return [.horizontal, .vertical, .diagonalDownRight, .diagonalDownLeft, .horizontalReverse, .verticalReverse]
        case .expert: return WordDirection.allCases
        }
    }
}

// MARK: — Modèles

struct WordSearchWord: Identifiable {
    let id = UUID()
    let word: String
    let startRow: Int
    let startCol: Int
    let direction: WordDirection
    var isFound: Bool = false
}

struct WordSearchPuzzle {
    let theme: String
    let grid: [[Character]]
    let words: [WordSearchWord]
    let gridSize: Int
    let difficulty: WordSearchDifficulty
}
