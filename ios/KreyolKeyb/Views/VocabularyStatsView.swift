// VocabularyStatsView.swift
// KreyolKeyb — iOS
//
// Portage de VocabularyStatsActivity.kt

import SwiftUI

struct VocabularyStatsView: View {

    let stats: VocabularyStats

    private var level: KreyolLevel { KreyolLevel.level(for: stats.coveragePercentage) }

    var body: some View {
        ScrollView {
            VStack(spacing: 24) {
                levelCard
                progressCard
                topWordsSection
                recentWordsSection
            }
            .padding()
        }
        .background(Color(hex: "#1E1E1E").ignoresSafeArea())
        .navigationTitle("Mon Kreyòl")
        .navigationBarTitleDisplayMode(.large)
    }

    // MARK: — Carte niveau

    private var levelCard: some View {
        VStack(spacing: 12) {
            Text(level.displayName)
                .font(.system(size: 32, weight: .bold))
                .foregroundColor(.white)

            Text(level.description)
                .font(.subheadline)
                .foregroundColor(Color(hex: "#8b8fa8"))

            Text("\(stats.wordsDiscovered) / \(stats.totalWords) mots découverts")
                .font(.caption)
                .foregroundColor(KeyboardColors.kreyolGreen)
        }
        .frame(maxWidth: .infinity)
        .padding(24)
        .background(Color(hex: "#2E2E2E"))
        .cornerRadius(16)
    }

    // MARK: — Carte progression

    private var progressCard: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Progression")
                .font(.headline)
                .foregroundColor(.white)

            ProgressView(value: Double(stats.coveragePercentage), total: 100)
                .tint(KeyboardColors.kreyolGreen)
                .scaleEffect(x: 1, y: 2)

            HStack {
                statItem(value: String(format: "%.1f%%", stats.coveragePercentage), label: "Couverture")
                Spacer()
                statItem(value: "\(stats.masteredWords)", label: "Maîtrisés")
                Spacer()
                statItem(value: "\(stats.totalUsages)", label: "Utilisations")
            }
        }
        .padding(20)
        .background(Color(hex: "#2E2E2E"))
        .cornerRadius(16)
    }

    // MARK: — Top mots

    private var topWordsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Mots les plus utilisés")
                .font(.headline)
                .foregroundColor(.white)

            if stats.topWords.isEmpty {
                Text("Commence à taper en kreyòl !")
                    .foregroundColor(Color(hex: "#8b8fa8"))
                    .font(.subheadline)
            } else {
                ForEach(stats.topWords) { word in
                    wordRow(word)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(20)
        .background(Color(hex: "#2E2E2E"))
        .cornerRadius(16)
    }

    // MARK: — Mots récents

    private var recentWordsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Récemment découverts")
                .font(.headline)
                .foregroundColor(.white)

            if stats.recentWords.isEmpty {
                Text("Explore le vocabulaire kreyòl !")
                    .foregroundColor(Color(hex: "#8b8fa8"))
                    .font(.subheadline)
            } else {
                FlowLayout(spacing: 8) {
                    ForEach(stats.recentWords, id: \.self) { word in
                        Text(word)
                            .font(.caption)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 5)
                            .background(KeyboardColors.kreyolGreen.opacity(0.2))
                            .foregroundColor(KeyboardColors.kreyolGreen)
                            .cornerRadius(12)
                    }
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(20)
        .background(Color(hex: "#2E2E2E"))
        .cornerRadius(16)
    }

    // MARK: — Helpers

    private func statItem(value: String, label: String) -> some View {
        VStack(spacing: 4) {
            Text(value)
                .font(.title3)
                .fontWeight(.bold)
                .foregroundColor(KeyboardColors.kreyolGreen)
            Text(label)
                .font(.caption2)
                .foregroundColor(Color(hex: "#8b8fa8"))
        }
    }

    private func wordRow(_ stats: WordUsageStats) -> some View {
        HStack {
            Text(stats.word)
                .foregroundColor(.white)
                .font(.body)
            Spacer()
            Text("\(stats.userCount)×")
                .foregroundColor(stats.isMastered ? KeyboardColors.kreyolGreen : Color(hex: "#8b8fa8"))
                .font(.caption)
            if stats.isMastered {
                Text("✓")
                    .foregroundColor(KeyboardColors.kreyolGreen)
                    .font(.caption)
            }
        }
        .padding(.vertical, 4)
    }
}

// MARK: — FlowLayout (tags en ligne)

struct FlowLayout: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let rows = arrangeRows(proposal: proposal, subviews: subviews)
        let height = rows.last.map { $0.maxY } ?? 0
        return CGSize(width: proposal.width ?? 0, height: height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let rows = arrangeRows(proposal: ProposedViewSize(bounds.size), subviews: subviews)
        for row in rows {
            for item in row.items {
                subviews[item.index].place(at: CGPoint(x: bounds.minX + item.x, y: bounds.minY + item.y), proposal: .unspecified)
            }
        }
    }

    private struct Row {
        struct Item { let index: Int; let x: CGFloat; let y: CGFloat }
        var items: [Item] = []
        var maxY: CGFloat = 0
    }

    private func arrangeRows(proposal: ProposedViewSize, subviews: Subviews) -> [Row] {
        let maxWidth = proposal.width ?? .infinity
        var rows: [Row] = []
        var currentRow = Row()
        var x: CGFloat = 0
        var y: CGFloat = 0
        var rowHeight: CGFloat = 0

        for (i, subview) in subviews.enumerated() {
            let size = subview.sizeThatFits(.unspecified)
            if x + size.width > maxWidth && !currentRow.items.isEmpty {
                rows.append(currentRow)
                currentRow = Row()
                x = 0
                y += rowHeight + spacing
                rowHeight = 0
            }
            currentRow.items.append(Row.Item(index: i, x: x, y: y))
            currentRow.maxY = y + size.height
            x += size.width + spacing
            rowHeight = max(rowHeight, size.height)
        }
        if !currentRow.items.isEmpty { rows.append(currentRow) }
        return rows
    }
}
