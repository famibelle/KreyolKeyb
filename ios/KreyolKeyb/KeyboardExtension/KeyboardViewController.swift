// KeyboardViewController.swift
// KreyolKeyb — KeyboardExtension
//
// Équivalent iOS de KreyolInputMethodServiceRefactored.kt
// À compléter en Phase 2 sous Xcode (nécessite UIKit + UIInputViewController)

import UIKit

// NOTE Phase 2 :
// Ce fichier sera le cœur de la Keyboard Extension.
// Il hérite de UIInputViewController (équivalent Android IME).
//
// Architecture prévue :
//   KeyboardViewController          ← UIInputViewController (ce fichier)
//   ├── KeyboardView (SwiftUI)       ← vue du clavier (touches, rangées)
//   ├── SuggestionBar (SwiftUI)      ← barre de suggestions au-dessus
//   ├── SuggestionEngine             ← algorithme (déjà porté)
//   └── AccentPopupView              ← popup long-press accents
//
// API iOS clé :
//   textDocumentProxy.insertText()   ← insérer un caractère
//   textDocumentProxy.deleteBackward() ← supprimer
//   textDocumentProxy.documentContextBeforeInput ← contexte avant curseur
//   advanceToNextInputMode()         ← changer de clavier (bouton 🌐)

class KeyboardViewController: UIInputViewController {

    private var hostingController: UIHostingControllerBridge?
    private let suggestionEngine = SuggestionEngine()

    override func viewDidLoad() {
        super.viewDidLoad()
        setupKeyboardView()
        Task { await suggestionEngine.initialize() }
    }

    private func setupKeyboardView() {
        // Phase 2 : intégrer KeyboardView SwiftUI ici
        // let keyboardView = KeyboardView(engine: suggestionEngine, proxy: textDocumentProxy)
        // let host = UIHostingController(rootView: keyboardView)
        // addChild(host)
        // view.addSubview(host.view)
        // host.didMove(toParent: self)
    }

    // MARK: — Actions clavier (appelées depuis KeyboardView)

    func insertCharacter(_ char: String) {
        textDocumentProxy.insertText(char)
        updateSuggestions()
    }

    func deleteBackward() {
        textDocumentProxy.deleteBackward()
        updateSuggestions()
    }

    func insertSuggestion(_ word: String) {
        // Effacer le mot en cours de frappe
        if let before = textDocumentProxy.documentContextBeforeInput {
            let currentWord = before.components(separatedBy: .whitespaces).last ?? ""
            for _ in 0..<currentWord.count {
                textDocumentProxy.deleteBackward()
            }
        }
        textDocumentProxy.insertText(word + " ")
        suggestionEngine.addWordToHistory(word)
        updateSuggestions()
    }

    func nextKeyboard() {
        advanceToNextInputMode()
    }

    // MARK: — Suggestions

    private func updateSuggestions() {
        let context = textDocumentProxy.documentContextBeforeInput ?? ""
        let currentWord = context.components(separatedBy: .whitespaces).last ?? ""
        suggestionEngine.generateSuggestions(for: currentWord)
    }
}

// MARK: — Stub pour compilation sans UIKit complet

class UIHostingControllerBridge {}
