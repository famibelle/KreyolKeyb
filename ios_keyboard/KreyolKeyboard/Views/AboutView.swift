//
//  AboutView.swift
//  Klavyé Kréyòl Karukera
//
//  Created by Potomitan™ on 01/11/2025.
//

import SwiftUI

struct AboutView: View {
    @Environment(\.openURL) var openURL
    
    var body: some View {
        NavigationView {
            ScrollView {
                VStack(spacing: 24) {
                    // App Icon and Title
                    VStack(spacing: 12) {
                        Image(systemName: "keyboard.fill")
                            .font(.system(size: 80))
                            .foregroundColor(Color("PotomitanRed"))
                        
                        Text("Klavyé Kréyòl Karukera")
                            .font(.title2)
                            .fontWeight(.bold)
                        
                        Text("Version 1.0.0")
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                    }
                    .padding()
                    
                    // Description
                    VStack(alignment: .leading, spacing: 12) {
                        Text("À Propos")
                            .font(.title3)
                            .fontWeight(.semibold)
                        
                        Text("Klavyé Kréyòl Karukera est le premier clavier dédié au créole guadeloupéen. Il facilite l'écriture des accents spécifiques et propose des suggestions intelligentes basées sur un dictionnaire de plus de 1 800 mots.")
                            .font(.body)
                            .foregroundColor(.secondary)
                    }
                    .padding()
                    .background(Color.gray.opacity(0.1))
                    .cornerRadius(12)
                    .padding(.horizontal)
                    
                    // Features
                    VStack(alignment: .leading, spacing: 16) {
                        Text("Fonctionnalités")
                            .font(.title3)
                            .fontWeight(.semibold)
                            .padding(.horizontal)
                        
                        VStack(spacing: 12) {
                            FeatureRow(
                                icon: "character.textbox",
                                title: "Accents créoles",
                                description: "è, ò, ö et tous les accents spécifiques"
                            )
                            
                            FeatureRow(
                                icon: "text.magnifyingglass",
                                title: "Suggestions intelligentes",
                                description: "Prédiction basée sur 1 867 mots"
                            )
                            
                            FeatureRow(
                                icon: "chart.line.uptrend.xyaxis",
                                title: "Statistiques",
                                description: "Suivez vos mots les plus utilisés"
                            )
                            
                            FeatureRow(
                                icon: "trophy.fill",
                                title: "Gamification",
                                description: "Débloquez des réussites"
                            )
                        }
                        .padding(.horizontal)
                    }
                    
                    // Links
                    VStack(spacing: 12) {
                        Button(action: {
                            openURL(URL(string: "https://potomitan.com")!)
                        }) {
                            LinkButton(
                                icon: "globe",
                                title: "Site Web Potomitan™",
                                color: Color("PotomitanRed")
                            )
                        }
                        
                        Button(action: {
                            openURL(URL(string: "https://github.com/medMelehi/KreyolKeyb")!)
                        }) {
                            LinkButton(
                                icon: "chevron.left.forwardslash.chevron.right",
                                title: "Code Source (GitHub)",
                                color: .blue
                            )
                        }
                        
                        Button(action: {
                            openURL(URL(string: "mailto:contact@potomitan.com")!)
                        }) {
                            LinkButton(
                                icon: "envelope.fill",
                                title: "Nous Contacter",
                                color: .orange
                            )
                        }
                    }
                    .padding(.horizontal)
                    
                    // Credits
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Développé avec ❤️ par")
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                        
                        Text("Potomitan™")
                            .font(.headline)
                            .fontWeight(.bold)
                            .foregroundColor(Color("PotomitanRed"))
                    }
                    .frame(maxWidth: .infinity)
                    .padding()
                    
                    // Footer
                    Text("© 2025 Potomitan™. Tous droits réservés.")
                        .font(.caption)
                        .foregroundColor(.secondary)
                        .multilineTextAlignment(.center)
                        .padding()
                }
                .padding(.vertical)
            }
            .navigationTitle("À Propos")
        }
    }
}

struct FeatureRow: View {
    let icon: String
    let title: String
    let description: String
    
    var body: some View {
        HStack(alignment: .top, spacing: 16) {
            Image(systemName: icon)
                .font(.title2)
                .foregroundColor(Color("PotomitanRed"))
                .frame(width: 32)
            
            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.headline)
                
                Text(description)
                    .font(.subheadline)
                    .foregroundColor(.secondary)
            }
            
            Spacer()
        }
        .padding()
        .background(Color.gray.opacity(0.05))
        .cornerRadius(12)
    }
}

struct LinkButton: View {
    let icon: String
    let title: String
    let color: Color
    
    var body: some View {
        HStack {
            Image(systemName: icon)
            Text(title)
                .fontWeight(.semibold)
            Spacer()
            Image(systemName: "arrow.up.right")
                .font(.caption)
        }
        .foregroundColor(.white)
        .padding()
        .background(color)
        .cornerRadius(12)
    }
}

#Preview {
    AboutView()
}
