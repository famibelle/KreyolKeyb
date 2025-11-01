//
//  OnboardingView.swift
//  Klavyé Kréyòl Karukera
//
//  Created by Potomitan™ on 01/11/2025.
//

import SwiftUI

struct OnboardingView: View {
    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    // Welcome Section
                    VStack(alignment: .leading, spacing: 12) {
                        Text("Bienvenue!")
                            .font(.largeTitle)
                            .fontWeight(.bold)
                        
                        Text("Merci d'utiliser Klavyé Kréyòl Karukera, le premier clavier pour écrire facilement en créole guadeloupéen.")
                            .font(.body)
                            .foregroundColor(.secondary)
                    }
                    .padding()
                    .background(Color("PotomitanRed").opacity(0.1))
                    .cornerRadius(12)
                    
                    // Installation Steps
                    VStack(alignment: .leading, spacing: 16) {
                        Text("Installation")
                            .font(.title2)
                            .fontWeight(.semibold)
                        
                        OnboardingStep(
                            number: "1",
                            title: "Activer le Clavier",
                            description: "Allez dans Réglages > Général > Clavier > Claviers > Ajouter un clavier..."
                        )
                        
                        OnboardingStep(
                            number: "2",
                            title: "Sélectionner Klavyé Kréyòl",
                            description: "Recherchez et activez 'Klavyé Kréyòl Karukera' dans la liste"
                        )
                        
                        OnboardingStep(
                            number: "3",
                            title: "Autoriser l'accès complet",
                            description: "Pour activer les suggestions et la prédiction, autorisez l'accès complet (optionnel mais recommandé)"
                        )
                        
                        OnboardingStep(
                            number: "4",
                            title: "Commencez à écrire!",
                            description: "Appuyez sur l'icône 🌐 pour basculer vers Klavyé Kréyòl"
                        )
                    }
                    .padding()
                    
                    // Settings Button
                    Button(action: {
                        if let url = URL(string: UIApplication.openSettingsURLString) {
                            UIApplication.shared.open(url)
                        }
                    }) {
                        HStack {
                            Image(systemName: "gearshape.fill")
                            Text("Ouvrir les Réglages")
                                .fontWeight(.semibold)
                        }
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(Color("PotomitanRed"))
                        .foregroundColor(.white)
                        .cornerRadius(12)
                    }
                    .padding(.horizontal)
                    
                    // Accents Reference
                    VStack(alignment: .leading, spacing: 12) {
                        Text("Accents Créoles")
                            .font(.title3)
                            .fontWeight(.semibold)
                        
                        VStack(alignment: .leading, spacing: 8) {
                            AccentRow(key: "è", description: "Appui long sur 'e'")
                            AccentRow(key: "ò", description: "Appui long sur 'o'")
                            AccentRow(key: "ö", description: "Appui long sur 'o'")
                            AccentRow(key: "é", description: "Appui long sur 'e'")
                        }
                        .padding()
                        .background(Color.gray.opacity(0.1))
                        .cornerRadius(8)
                    }
                    .padding()
                }
                .padding(.vertical)
            }
            .navigationTitle("Démarrage")
        }
    }
}

struct OnboardingStep: View {
    let number: String
    let title: String
    let description: String
    
    var body: some View {
        HStack(alignment: .top, spacing: 16) {
            ZStack {
                Circle()
                    .fill(Color("PotomitanRed"))
                    .frame(width: 36, height: 36)
                
                Text(number)
                    .font(.headline)
                    .foregroundColor(.white)
                    .fontWeight(.bold)
            }
            
            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.headline)
                
                Text(description)
                    .font(.subheadline)
                    .foregroundColor(.secondary)
            }
        }
    }
}

struct AccentRow: View {
    let key: String
    let description: String
    
    var body: some View {
        HStack {
            Text(key)
                .font(.title2)
                .fontWeight(.bold)
                .foregroundColor(Color("PotomitanRed"))
                .frame(width: 40)
            
            Text(description)
                .font(.subheadline)
        }
    }
}

#Preview {
    OnboardingView()
}
