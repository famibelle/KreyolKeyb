//
//  StatsView.swift
//  Klavyé Kréyòl Karukera
//
//  Created by Potomitan™ on 01/11/2025.
//

import SwiftUI

struct StatsView: View {
    @StateObject private var tracker = VocabularyTracker.shared
    
    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    // Summary Cards
                    HStack(spacing: 16) {
                        StatCard(
                            title: "Mots tapés",
                            value: "\(tracker.totalWords)",
                            icon: "keyboard.fill",
                            color: Color("PotomitanRed")
                        )
                        
                        StatCard(
                            title: "Mots uniques",
                            value: "\(tracker.uniqueWords)",
                            icon: "text.book.closed.fill",
                            color: .blue
                        )
                    }
                    .padding(.horizontal)
                    
                    // Most Used Words
                    VStack(alignment: .leading, spacing: 16) {
                        Text("Mots les plus utilisés")
                            .font(.title2)
                            .fontWeight(.bold)
                            .padding(.horizontal)
                        
                        if tracker.topWords.isEmpty {
                            VStack(spacing: 12) {
                                Image(systemName: "text.bubble")
                                    .font(.system(size: 50))
                                    .foregroundColor(.gray)
                                
                                Text("Commencez à taper!")
                                    .font(.headline)
                                    .foregroundColor(.secondary)
                                
                                Text("Vos statistiques apparaîtront ici une fois que vous aurez commencé à utiliser le clavier.")
                                    .font(.subheadline)
                                    .foregroundColor(.secondary)
                                    .multilineTextAlignment(.center)
                                    .padding(.horizontal, 32)
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 40)
                        } else {
                            VStack(spacing: 0) {
                                ForEach(Array(tracker.topWords.prefix(10).enumerated()), id: \.element.word) { index, wordStat in
                                    WordStatRow(
                                        rank: index + 1,
                                        word: wordStat.word,
                                        count: wordStat.count,
                                        isTopFive: index < 5
                                    )
                                    
                                    if index < min(9, tracker.topWords.count - 1) {
                                        Divider()
                                            .padding(.leading, 60)
                                    }
                                }
                            }
                            .background(Color.gray.opacity(0.05))
                            .cornerRadius(12)
                            .padding(.horizontal)
                        }
                    }
                    
                    // Gamification Section
                    VStack(alignment: .leading, spacing: 16) {
                        Text("Progression")
                            .font(.title2)
                            .fontWeight(.bold)
                            .padding(.horizontal)
                        
                        VStack(spacing: 12) {
                            AchievementRow(
                                icon: "star.fill",
                                title: "Premier mot",
                                description: "Tapez votre premier mot en créole",
                                isUnlocked: tracker.totalWords > 0
                            )
                            
                            AchievementRow(
                                icon: "flame.fill",
                                title: "10 mots",
                                description: "Tapez 10 mots différents",
                                isUnlocked: tracker.uniqueWords >= 10
                            )
                            
                            AchievementRow(
                                icon: "crown.fill",
                                title: "Polyglotte",
                                description: "Tapez 50 mots différents",
                                isUnlocked: tracker.uniqueWords >= 50
                            )
                            
                            AchievementRow(
                                icon: "trophy.fill",
                                title: "Maître Créole",
                                description: "Tapez 100 mots différents",
                                isUnlocked: tracker.uniqueWords >= 100
                            )
                        }
                        .padding(.horizontal)
                    }
                    
                    Spacer(minLength: 20)
                }
                .padding(.vertical)
            }
            .navigationTitle("Kréyòl an mwen")
        }
    }
}

struct StatCard: View {
    let title: String
    let value: String
    let icon: String
    let color: Color
    
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Image(systemName: icon)
                    .foregroundColor(color)
                Spacer()
            }
            
            Text(value)
                .font(.title)
                .fontWeight(.bold)
            
            Text(title)
                .font(.caption)
                .foregroundColor(.secondary)
        }
        .padding()
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.gray.opacity(0.1))
        .cornerRadius(12)
    }
}

struct WordStatRow: View {
    let rank: Int
    let word: String
    let count: Int
    let isTopFive: Bool
    
    var body: some View {
        HStack(spacing: 16) {
            // Rank Badge
            ZStack {
                Circle()
                    .fill(isTopFive ? Color("PotomitanRed") : Color.gray.opacity(0.3))
                    .frame(width: 32, height: 32)
                
                Text("\(rank)")
                    .font(.system(size: isTopFive ? 16 : 14, weight: .bold))
                    .foregroundColor(isTopFive ? .white : .secondary)
            }
            
            // Word
            Text(word)
                .font(.system(size: isTopFive ? 20 : 16, weight: isTopFive ? .semibold : .regular))
                .foregroundColor(.primary)
            
            Spacer()
            
            // Count
            Text("\(count)")
                .font(.system(size: isTopFive ? 18 : 14, weight: isTopFive ? .semibold : .regular))
                .foregroundColor(.secondary)
        }
        .padding(.horizontal)
        .padding(.vertical, 12)
    }
}

struct AchievementRow: View {
    let icon: String
    let title: String
    let description: String
    let isUnlocked: Bool
    
    var body: some View {
        HStack(spacing: 16) {
            ZStack {
                Circle()
                    .fill(isUnlocked ? Color("PotomitanRed") : Color.gray.opacity(0.3))
                    .frame(width: 50, height: 50)
                
                Image(systemName: icon)
                    .font(.title3)
                    .foregroundColor(isUnlocked ? .white : .gray)
            }
            
            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.headline)
                    .foregroundColor(isUnlocked ? .primary : .secondary)
                
                Text(description)
                    .font(.subheadline)
                    .foregroundColor(.secondary)
            }
            
            Spacer()
            
            if isUnlocked {
                Image(systemName: "checkmark.circle.fill")
                    .foregroundColor(.green)
                    .font(.title3)
            }
        }
        .padding()
        .background(Color.gray.opacity(0.05))
        .cornerRadius(12)
    }
}

#Preview {
    StatsView()
}
