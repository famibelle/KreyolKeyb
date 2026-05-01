// WordSearchGenerator.swift
// KreyolKeyb — iOS
//
// Portage de WordSearchGenerator.kt

import Foundation

enum WordSearchGenerator {

    private static let alphabet = Array("ABCDEFGHIJKLMNOPQRSTUVWXYZ")
    private static let gridSize = 8

    // MARK: — Point d'entrée

    static func generatePuzzle(
        words availableWords: [String],
        theme: String = "Kreyòl",
        difficulty: WordSearchDifficulty = .normal
    ) -> WordSearchPuzzle {
        let selected = selectWords(from: availableWords, count: difficulty.wordCount)
        var grid = Array(repeating: Array(repeating: Character(" "), count: gridSize), count: gridSize)
        let placed = placeWords(selected, in: &grid, difficulty: difficulty)
        fillEmpty(&grid)

        return WordSearchPuzzle(
            theme: theme,
            grid: grid,
            words: placed,
            gridSize: gridSize,
            difficulty: difficulty
        )
    }

    // MARK: — Sélection des mots

    private static func selectWords(from words: [String], count: Int) -> [String] {
        words
            .filter { $0.count >= 3 && $0.count <= gridSize - 1 }
            .shuffled()
            .prefix(count)
            .map { $0 }
    }

    // MARK: — Placement

    private static func placeWords(
        _ words: [String],
        in grid: inout [[Character]],
        difficulty: WordSearchDifficulty
    ) -> [WordSearchWord] {
        var placed: [WordSearchWord] = []

        for word in words {
            if let model = tryPlace(word, in: &grid, directions: difficulty.allowedDirections) {
                placed.append(model)
            }
        }
        return placed
    }

    private static func tryPlace(
        _ word: String,
        in grid: inout [[Character]],
        directions: [WordDirection]
    ) -> WordSearchWord? {
        let upper = word.uppercased()

        for _ in 0..<100 {
            guard let direction = directions.randomElement() else { continue }
            guard let (row, col) = randomPosition(for: upper.count, direction: direction) else { continue }

            if canPlace(upper, at: (row, col), direction: direction, in: grid) {
                place(upper, at: (row, col), direction: direction, in: &grid)
                return WordSearchWord(word: upper, startRow: row, startCol: col, direction: direction)
            }
        }
        return nil
    }

    private static func randomPosition(for length: Int, direction: WordDirection) -> (Int, Int)? {
        let size = gridSize
        switch direction {
        case .horizontal, .horizontalReverse:
            return (Int.random(in: 0..<size), Int.random(in: 0...(size - length)))
        case .vertical, .verticalReverse:
            return (Int.random(in: 0...(size - length)), Int.random(in: 0..<size))
        case .diagonalDownRight, .diagonalUpLeft:
            return (Int.random(in: 0...(size - length)), Int.random(in: 0...(size - length)))
        case .diagonalDownLeft, .diagonalUpRight:
            guard length - 1 < size else { return nil }
            return (Int.random(in: 0...(size - length)), Int.random(in: (length - 1)..<size))
        }
    }

    private static func canPlace(
        _ word: String,
        at start: (Int, Int),
        direction: WordDirection,
        in grid: [[Character]]
    ) -> Bool {
        let chars = Array(word)
        let d = direction.delta
        for i in 0..<chars.count {
            let r = start.0 + i * d.row
            let c = start.1 + i * d.col
            guard r >= 0, r < gridSize, c >= 0, c < gridSize else { return false }
            let current = grid[r][c]
            if current != " " && current != chars[i] { return false }
        }
        return true
    }

    private static func place(
        _ word: String,
        at start: (Int, Int),
        direction: WordDirection,
        in grid: inout [[Character]]
    ) {
        let chars = Array(word)
        let d = direction.delta
        for i in 0..<chars.count {
            grid[start.0 + i * d.row][start.1 + i * d.col] = chars[i]
        }
    }

    // MARK: — Remplissage

    private static func fillEmpty(_ grid: inout [[Character]]) {
        for r in 0..<gridSize {
            for c in 0..<gridSize {
                if grid[r][c] == " " {
                    grid[r][c] = alphabet.randomElement()!
                }
            }
        }
    }
}
