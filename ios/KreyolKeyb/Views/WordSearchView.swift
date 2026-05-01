// WordSearchView.swift
// KreyolKeyb — iOS
//
// Portage de WordSearchActivity.kt

import SwiftUI

struct WordSearchView: View {

    @StateObject private var viewModel: WordSearchViewModel

    init(dictionary: [(word: String, frequency: Int)]) {
        _viewModel = StateObject(wrappedValue: WordSearchViewModel(dictionary: dictionary))
    }

    var body: some View {
        VStack(spacing: 0) {
            header
            if let puzzle = viewModel.puzzle {
                gridView(puzzle)
                wordListView(puzzle)
            } else {
                Spacer()
                ProgressView().tint(KeyboardColors.kreyolGreen)
                Spacer()
            }
        }
        .background(Color(hex: "#1E1E1E").ignoresSafeArea())
        .navigationTitle("Mots Mêlés")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { viewModel.newGame() }
    }

    // MARK: — Header

    private var header: some View {
        HStack {
            difficultyPicker
            Spacer()
            timerView
            Spacer()
            Button("Nouveau") { viewModel.newGame() }
                .foregroundColor(KeyboardColors.kreyolGreen)
                .font(.subheadline.weight(.semibold))
        }
        .padding()
        .background(Color(hex: "#2E2E2E"))
    }

    private var difficultyPicker: some View {
        Menu {
            ForEach(WordSearchDifficulty.allCases) { diff in
                Button(diff.rawValue) { viewModel.difficulty = diff; viewModel.newGame() }
            }
        } label: {
            HStack(spacing: 4) {
                Text(viewModel.difficulty.rawValue)
                    .foregroundColor(.white)
                    .font(.subheadline)
                Image(systemName: "chevron.down")
                    .foregroundColor(Color(hex: "#8b8fa8"))
                    .font(.caption)
            }
        }
    }

    private var timerView: some View {
        Text(viewModel.timeString)
            .font(.system(.title3, design: .monospaced).weight(.semibold))
            .foregroundColor(viewModel.timeRemaining < 30 ? .orange : .white)
    }

    // MARK: — Grille

    private func gridView(_ puzzle: WordSearchPuzzle) -> some View {
        GeometryReader { geo in
            let cellSize = min(geo.size.width, geo.size.height) / CGFloat(puzzle.gridSize)
            VStack(spacing: 0) {
                ForEach(0..<puzzle.gridSize, id: \.self) { row in
                    HStack(spacing: 0) {
                        ForEach(0..<puzzle.gridSize, id: \.self) { col in
                            cellView(char: puzzle.grid[row][col], row: row, col: col, size: cellSize)
                        }
                    }
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .aspectRatio(1, contentMode: .fit)
        .padding(12)
    }

    private func cellView(char: Character, row: Int, col: Int, size: CGFloat) -> some View {
        let isSelected = viewModel.selectedCells.contains(where: { $0 == (row, col) })
        let isFound    = viewModel.foundCells.contains(where: { $0 == (row, col) })

        return Text(String(char))
            .font(.system(size: size * 0.55, weight: .semibold, design: .monospaced))
            .frame(width: size, height: size)
            .background(
                isFound    ? KeyboardColors.kreyolGreen.opacity(0.4) :
                isSelected ? Color.white.opacity(0.15) :
                Color.clear
            )
            .foregroundColor(isFound ? KeyboardColors.kreyolGreen : .white)
            .onTapGesture { viewModel.selectCell(row: row, col: col) }
    }

    // MARK: — Liste des mots

    private func wordListView(_ puzzle: WordSearchPuzzle) -> some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 12) {
                ForEach(puzzle.words) { word in
                    Text(word.word)
                        .font(.subheadline.weight(.medium))
                        .strikethrough(word.isFound)
                        .foregroundColor(word.isFound ? KeyboardColors.kreyolGreen : .white)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(Color(hex: "#2E2E2E"))
                        .cornerRadius(8)
                }
            }
            .padding(.horizontal)
        }
        .padding(.vertical, 12)
    }
}

// MARK: — ViewModel

@MainActor
final class WordSearchViewModel: ObservableObject {

    @Published var puzzle: WordSearchPuzzle?
    @Published var selectedCells: [(Int, Int)] = []
    @Published var foundCells: [(Int, Int)] = []
    @Published var timeRemaining: Int = 180
    @Published var difficulty: WordSearchDifficulty = .normal

    private let dictionary: [(word: String, frequency: Int)]
    private var timer: Timer?
    private var selectionStart: (Int, Int)?

    var timeString: String {
        let m = timeRemaining / 60, s = timeRemaining % 60
        return String(format: "%d:%02d", m, s)
    }

    init(dictionary: [(word: String, frequency: Int)]) {
        self.dictionary = dictionary
    }

    func newGame() {
        timer?.invalidate()
        selectedCells = []
        foundCells = []
        timeRemaining = 180
        selectionStart = nil

        let words = dictionary.map { $0.word }
        puzzle = WordSearchGenerator.generatePuzzle(words: words, difficulty: difficulty)
        startTimer()
    }

    func selectCell(row: Int, col: Int) {
        guard let puzzle else { return }

        if selectionStart == nil {
            selectionStart = (row, col)
            selectedCells = [(row, col)]
        } else {
            let start = selectionStart!
            selectedCells = cellsBetween(start, (row, col))
            checkSelection(in: puzzle)
            selectionStart = nil
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                self.selectedCells = []
            }
        }
    }

    private func cellsBetween(_ a: (Int, Int), _ b: (Int, Int)) -> [(Int, Int)] {
        let dr = b.0 - a.0, dc = b.1 - a.1
        let steps = max(abs(dr), abs(dc))
        guard steps > 0 else { return [a] }
        let sr = dr / steps, sc = dc / steps
        return (0...steps).map { (a.0 + $0 * sr, a.1 + $0 * sc) }
    }

    private func checkSelection(in puzzle: WordSearchPuzzle) {
        let selected = String(selectedCells.compactMap { r, c in
            guard r >= 0, r < puzzle.gridSize, c >= 0, c < puzzle.gridSize else { return nil }
            return puzzle.grid[r][c]
        })

        for word in puzzle.words where !word.isFound {
            if selected == word.word || selected == String(word.word.reversed()) {
                foundCells.append(contentsOf: selectedCells)
                self.puzzle?.words[self.puzzle!.words.firstIndex(where: { $0.id == word.id })!].isFound = true
            }
        }
    }

    private func startTimer() {
        timer = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { [weak self] _ in
            guard let self else { return }
            if self.timeRemaining > 0 { self.timeRemaining -= 1 }
            else { self.timer?.invalidate() }
        }
    }
}

// MARK: — Extensions mots mutables

extension WordSearchPuzzle {
    subscript(wordIndex: Int) -> WordSearchWord {
        get { words[wordIndex] }
    }
}
