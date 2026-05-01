// KeyboardSetupView.swift
// KreyolKeyb — iOS
//
// Guide d'activation du clavier + zone de test
// (remplace l'onglet "Clavier" de SettingsActivity)

import SwiftUI

struct KeyboardSetupView: View {

    @State private var testText = ""
    @State private var keyboardEnabled = false

    var body: some View {
        ScrollView {
            VStack(spacing: 24) {
                statusCard
                stepsCard
                testAreaCard
            }
            .padding()
        }
        .background(Color(hex: "#1E1E1E").ignoresSafeArea())
        .navigationTitle("Klavier Kréyòl")
        .navigationBarTitleDisplayMode(.large)
        .onAppear { checkKeyboardStatus() }
    }

    // MARK: — Statut

    private var statusCard: some View {
        HStack(spacing: 16) {
            Image(systemName: keyboardEnabled ? "checkmark.circle.fill" : "exclamationmark.circle.fill")
                .font(.title)
                .foregroundColor(keyboardEnabled ? KeyboardColors.kreyolGreen : .orange)

            VStack(alignment: .leading, spacing: 4) {
                Text(keyboardEnabled ? "Clavier activé ✓" : "Clavier non activé")
                    .font(.headline)
                    .foregroundColor(.white)
                Text(keyboardEnabled
                     ? "Klavyé Kréyòl est prêt à l'emploi"
                     : "Suis les étapes ci-dessous pour l'activer")
                    .font(.caption)
                    .foregroundColor(Color(hex: "#8b8fa8"))
            }
            Spacer()
        }
        .padding(20)
        .background(Color(hex: "#2E2E2E"))
        .cornerRadius(16)
    }

    // MARK: — Étapes d'activation

    private var stepsCard: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Comment activer le clavier")
                .font(.headline)
                .foregroundColor(.white)

            ForEach(Array(activationSteps.enumerated()), id: \.offset) { i, step in
                HStack(alignment: .top, spacing: 12) {
                    Text("\(i + 1)")
                        .font(.caption.weight(.bold))
                        .foregroundColor(.black)
                        .frame(width: 22, height: 22)
                        .background(KeyboardColors.kreyolGreen)
                        .clipShape(Circle())

                    VStack(alignment: .leading, spacing: 2) {
                        Text(step.title).font(.subheadline.weight(.medium)).foregroundColor(.white)
                        Text(step.detail).font(.caption).foregroundColor(Color(hex: "#8b8fa8"))
                    }
                }
            }

            Button {
                openKeyboardSettings()
            } label: {
                HStack {
                    Image(systemName: "gear")
                    Text("Ouvrir les Réglages")
                }
                .frame(maxWidth: .infinity)
            }
            .buttonStyle(KreyolButtonStyle(color: KeyboardColors.kreyolGreen))
            .padding(.top, 8)
        }
        .padding(20)
        .background(Color(hex: "#2E2E2E"))
        .cornerRadius(16)
    }

    // MARK: — Zone de test

    private var testAreaCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Zone de test")
                .font(.headline)
                .foregroundColor(.white)

            Text("Teste ton clavier ici — sélectionne Klavyé Kréyòl avec le globe 🌐")
                .font(.caption)
                .foregroundColor(Color(hex: "#8b8fa8"))

            TextField("Tape en kreyòl...", text: $testText, axis: .vertical)
                .lineLimit(4...8)
                .padding(12)
                .background(Color(hex: "#1E1E1E"))
                .foregroundColor(.white)
                .cornerRadius(10)
                .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color(hex: "#333"), lineWidth: 1))

            if !testText.isEmpty {
                Button("Effacer") { testText = "" }
                    .foregroundColor(.orange)
                    .font(.caption)
            }
        }
        .padding(20)
        .background(Color(hex: "#2E2E2E"))
        .cornerRadius(16)
    }

    // MARK: — Helpers

    private let activationSteps: [(title: String, detail: String)] = [
        ("Ouvrir Réglages", "Appuie sur le bouton ci-dessous"),
        ("Général → Clavier", "Puis « Claviers »"),
        ("Ajouter un clavier", "Cherche « Klavyé Kréyòl »"),
        ("Autoriser l'accès complet", "Pour les suggestions et le suivi vocabulaire"),
        ("Changer de clavier", "Appuie sur 🌐 dans n'importe quelle app"),
    ]

    private func checkKeyboardStatus() {
        // Sur iOS, on ne peut pas détecter directement si le clavier est actif
        // L'utilisateur doit le vérifier manuellement
        keyboardEnabled = false
    }

    private func openKeyboardSettings() {
        if let url = URL(string: UIApplication.openSettingsURLString) {
            UIApplication.shared.open(url)
        }
    }
}
