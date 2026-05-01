// ContentView.swift
// KreyolKeyb — iOS
//
// Navigation principale — 4 onglets (miroir de SettingsActivity)

import SwiftUI

struct ContentView: View {

    @StateObject private var appState = AppState()

    var body: some View {
        TabView {
            NavigationStack {
                KeyboardSetupView()
            }
            .tabItem {
                Label("Clavier", systemImage: "keyboard")
            }

            NavigationStack {
                VocabularyStatsView(stats: appState.vocabularyStats)
            }
            .tabItem {
                Label("Stats", systemImage: "chart.bar.fill")
            }

            NavigationStack {
                WordSearchView(dictionary: appState.kreyolDictionary)
            }
            .tabItem {
                Label("Mots mêlés", systemImage: "square.grid.3x3.fill")
            }

            NavigationStack {
                WordScrambleView(dictionary: appState.kreyolDictionary)
            }
            .tabItem {
                Label("Mots mélangés", systemImage: "shuffle")
            }
        }
        .tint(KeyboardColors.kreyolGreen)
        .preferredColorScheme(.dark)
        .task { await appState.initialize() }
    }
}

// MARK: — AppState

@MainActor
final class AppState: ObservableObject {

    @Published var kreyolDictionary: [(word: String, frequency: Int)] = []
    @Published var vocabularyStats = VocabularyStats(
        coveragePercentage: 0,
        wordsDiscovered: 0,
        totalWords: 0,
        totalUsages: 0,
        topWords: [],
        recentWords: [],
        masteredWords: 0
    )

    private var usageTracker: CreoleDictionaryWithUsage?

    func initialize() async {
        // Charger le dictionnaire depuis les assets
        if let url = Bundle.main.url(forResource: "creole_dict", withExtension: "json"),
           let data = try? Data(contentsOf: url),
           let rawArray = try? JSONSerialization.jsonObject(with: data) as? [[Any]] {
            kreyolDictionary = rawArray.compactMap { entry in
                guard let word = entry[0] as? String, let freq = entry[1] as? Int else { return nil }
                return (word: word, frequency: freq)
            }
        }

        // Initialiser le tracker d'utilisation
        let tracker = await CreoleDictionaryWithUsage()
        usageTracker = tracker
        await refreshStats()
    }

    func refreshStats() async {
        guard let tracker = usageTracker else { return }
        vocabularyStats = await tracker.getVocabularyStats()
    }

    func trackWord(_ word: String) async {
        await usageTracker?.incrementWordUsage(word)
        await refreshStats()
    }
}
