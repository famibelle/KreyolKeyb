// WordScrambleView.swift
// KreyolKeyb — iOS
//
// Portage de WordScrambleActivity.kt

import SwiftUI

struct WordScrambleView: View {

    @StateObject private var viewModel: WordScrambleViewModel

    init(dictionary: [(word: String, frequency: Int)]) {
        _viewModel = StateObject(wrappedValue: WordScrambleViewModel(dictionary: dictionary))
    }

    var body: some View {
        VStack(spacing: 20) {
            header
            Spacer()
            if let current = viewModel.currentScramble {
                scoreAndTimer
                scrambledLetters(current)
                answerSlots(current)
                actionButtons
            } else {
                gameOverView
            }
            Spacer()
        }
        .padding()
        .background(Color(hex: "#1E1E1E").ignoresSafeArea())
        .navigationTitle("Mots Mélangés")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { viewModel.newGame() }
    }

    // MARK: — Header

    private var header: some View {
        HStack {
            Menu {
                ForEach(ScrambleDifficulty.allCases) { diff in
                    Button(diff.rawValue) { viewModel.difficulty = diff; viewModel.newGame() }
                }
            } label: {
                HStack(spacing: 4) {
                    Text(viewModel.difficulty.rawValue).foregroundColor(.white).font(.subheadline)
                    Image(systemName: "chevron.down").foregroundColor(Color(hex: "#8b8fa8")).font(.caption)
                }
            }
            Spacer()
            Button("Nouveau") { viewModel.newGame() }
                .foregroundColor(KeyboardColors.kreyolGreen)
                .font(.subheadline.weight(.semibold))
        }
    }

    // MARK: — Score & Timer

    private var scoreAndTimer: some View {
        HStack {
            VStack {
                Text("\(viewModel.score)")
                    .font(.title2.weight(.bold))
                    .foregroundColor(KeyboardColors.kreyolGreen)
                Text("Score").font(.caption).foregroundColor(Color(hex: "#8b8fa8"))
            }
            Spacer()
            Text(viewModel.timeString)
                .font(.system(.title2, design: .monospaced).weight(.semibold))
                .foregroundColor(viewModel.timeRemaining < 10 ? .red : .white)
            Spacer()
            VStack {
                Text("\(viewModel.wordIndex + 1)/\(viewModel.totalWords)")
                    .font(.title2.weight(.bold))
                    .foregroundColor(.white)
                Text("Mots").font(.caption).foregroundColor(Color(hex: "#8b8fa8"))
            }
        }
        .padding(16)
        .background(Color(hex: "#2E2E2E"))
        .cornerRadius(12)
    }

    // MARK: — Lettres mélangées

    private func scrambledLetters(_ scramble: ScrambledWord) -> some View {
        VStack(spacing: 12) {
            Text("Trouve le mot").font(.subheadline).foregroundColor(Color(hex: "#8b8fa8"))
            HStack(spacing: 8) {
                ForEach(Array(scramble.scrambledLetters.enumerated()), id: \.offset) { i, char in
                    let isUsed = viewModel.usedLetterIndices.contains(i)
                    Button {
                        viewModel.selectLetter(at: i)
                    } label: {
                        Text(String(char).uppercased())
                            .font(.title2.weight(.bold))
                            .foregroundColor(isUsed ? Color(hex: "#555") : .white)
                            .frame(width: 48, height: 48)
                            .background(isUsed ? Color(hex: "#333") : Color(hex: "#2E2E2E"))
                            .cornerRadius(10)
                            .overlay(RoundedRectangle(cornerRadius: 10).stroke(isUsed ? Color.clear : Color(hex: "#555"), lineWidth: 1))
                    }
                    .disabled(isUsed)
                }
            }
        }
    }

    // MARK: — Cases réponse

    private func answerSlots(_ scramble: ScrambledWord) -> some View {
        VStack(spacing: 12) {
            Text("Ta réponse").font(.subheadline).foregroundColor(Color(hex: "#8b8fa8"))
            HStack(spacing: 8) {
                ForEach(0..<scramble.originalWord.count, id: \.self) { i in
                    let char = scramble.currentAnswer[i]
                    Button {
                        viewModel.removeLetterFromAnswer(at: i)
                    } label: {
                        Text(char.map { String($0).uppercased() } ?? "_")
                            .font(.title2.weight(.bold))
                            .foregroundColor(char != nil ? KeyboardColors.kreyolGreen : Color(hex: "#555"))
                            .frame(width: 48, height: 48)
                            .background(Color(hex: "#2E2E2E"))
                            .cornerRadius(10)
                            .overlay(RoundedRectangle(cornerRadius: 10).stroke(
                                char != nil ? KeyboardColors.kreyolGreen : Color(hex: "#333"),
                                lineWidth: char != nil ? 1.5 : 1
                            ))
                    }
                    .disabled(char == nil)
                }
            }
        }
    }

    // MARK: — Boutons

    private var actionButtons: some View {
        HStack(spacing: 16) {
            Button("Indice") { viewModel.useHint() }
                .buttonStyle(KreyolButtonStyle(color: .orange))
            Button("Effacer") { viewModel.clearAnswer() }
                .buttonStyle(KreyolButtonStyle(color: Color(hex: "#555")))
            Button("Valider") { viewModel.validateAnswer() }
                .buttonStyle(KreyolButtonStyle(color: KeyboardColors.kreyolGreen))
        }
    }

    // MARK: — Game Over

    private var gameOverView: some View {
        VStack(spacing: 20) {
            Text("Bravo ! 🎉").font(.largeTitle.weight(.bold)).foregroundColor(.white)
            Text("Score final : \(viewModel.score)").font(.title2).foregroundColor(KeyboardColors.kreyolGreen)
            Button("Rejouer") { viewModel.newGame() }
                .buttonStyle(KreyolButtonStyle(color: KeyboardColors.kreyolGreen))
        }
    }
}

// MARK: — Style bouton

struct KreyolButtonStyle: ButtonStyle {
    let color: Color
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.subheadline.weight(.semibold))
            .foregroundColor(.white)
            .padding(.horizontal, 20)
            .padding(.vertical, 12)
            .background(color.opacity(configuration.isPressed ? 0.7 : 1))
            .cornerRadius(10)
    }
}

// MARK: — ViewModel

@MainActor
final class WordScrambleViewModel: ObservableObject {

    @Published var currentScramble: ScrambledWord?
    @Published var score = 0
    @Published var wordIndex = 0
    @Published var timeRemaining: Int = 30
    @Published var usedLetterIndices: Set<Int> = []
    @Published var difficulty: ScrambleDifficulty = .normal

    private let dictionary: [(word: String, frequency: Int)]
    private var words: [String] = []
    private var timer: Timer?

    var totalWords: Int { words.count }
    var timeString: String { String(format: "0:%02d", timeRemaining) }

    init(dictionary: [(word: String, frequency: Int)]) {
        self.dictionary = dictionary
    }

    func newGame() {
        timer?.invalidate()
        score = 0
        wordIndex = 0
        words = WordScrambleData.loadWords(from: dictionary, difficulty: difficulty)
        loadNextWord()
    }

    private func loadNextWord() {
        guard wordIndex < words.count else { currentScramble = nil; return }
        currentScramble = ScrambledWord(originalWord: words[wordIndex])
        usedLetterIndices = []
        timeRemaining = difficulty.timeLimit
        startTimer()
    }

    func selectLetter(at index: Int) {
        guard var scramble = currentScramble else { return }
        guard !usedLetterIndices.contains(index) else { return }

        let slotIndex = scramble.currentAnswer.firstIndex(where: { $0 == nil })
        guard let slot = slotIndex else { return }

        scramble.currentAnswer[slot] = scramble.scrambledLetters[index]
        usedLetterIndices.insert(index)
        currentScramble = scramble

        if scramble.currentAnswer.compactMap({ $0 }).count == scramble.originalWord.count {
            validateAnswer()
        }
    }

    func removeLetterFromAnswer(at slot: Int) {
        guard var scramble = currentScramble else { return }
        guard let char = scramble.currentAnswer[slot] else { return }

        // Retrouver l'index de la lettre dans les lettres mélangées
        for i in 0..<scramble.scrambledLetters.count {
            if usedLetterIndices.contains(i) && scramble.scrambledLetters[i] == char {
                usedLetterIndices.remove(i)
                break
            }
        }
        scramble.currentAnswer[slot] = nil
        currentScramble = scramble
    }

    func clearAnswer() {
        guard var scramble = currentScramble else { return }
        scramble.currentAnswer = Array(repeating: nil, count: scramble.originalWord.count)
        usedLetterIndices = []
        currentScramble = scramble
    }

    func useHint() {
        guard var scramble = currentScramble else { return }
        let word = Array(scramble.originalWord)

        // Révèle la prochaine lettre manquante dans la réponse
        for i in 0..<word.count where scramble.currentAnswer[i] == nil {
            scramble.currentAnswer[i] = word[i]
            // Marquer la lettre utilisée dans les lettres mélangées
            for j in 0..<scramble.scrambledLetters.count where
                !usedLetterIndices.contains(j) && scramble.scrambledLetters[j] == word[i] {
                usedLetterIndices.insert(j)
                break
            }
            break
        }
        currentScramble = scramble
    }

    func validateAnswer() {
        guard let scramble = currentScramble else { return }
        let answer = String(scramble.currentAnswer.compactMap { $0 })

        if answer == scramble.originalWord {
            let timeBonus = timeRemaining * 2
            score += 100 + timeBonus
            timer?.invalidate()
            wordIndex += 1
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) {
                self.loadNextWord()
            }
        } else {
            // Mauvaise réponse — on efface
            clearAnswer()
        }
    }

    private func startTimer() {
        timer = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { [weak self] _ in
            guard let self else { return }
            if self.timeRemaining > 0 {
                self.timeRemaining -= 1
            } else {
                self.timer?.invalidate()
                self.wordIndex += 1
                self.loadNextWord()
            }
        }
    }
}
