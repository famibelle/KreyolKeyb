//
//  KeyboardViewController.swift
//  Klavyé Kréyòl Karukera Keyboard Extension
//
//  Created by Potomitan™ on 01/11/2025.
//

import UIKit

class KeyboardViewController: UIInputViewController {
    
    private var keyboardView: KeyboardView!
    private let dictionary = CreoleDictionary.shared
    private let tracker = VocabularyTracker.shared
    private var currentWord = ""
    private var suggestions: [String] = []
    
    override func viewDidLoad() {
        super.viewDidLoad()
        
        setupKeyboardView()
        setupNotifications()
    }
    
    private func setupKeyboardView() {
        keyboardView = KeyboardView()
        keyboardView.delegate = self
        
        view.addSubview(keyboardView)
        keyboardView.translatesAutoresizingMaskIntoConstraints = false
        
        NSLayoutConstraint.activate([
            keyboardView.topAnchor.constraint(equalTo: view.topAnchor),
            keyboardView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            keyboardView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            keyboardView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            keyboardView.heightAnchor.constraint(equalToConstant: 280)
        ])
    }
    
    private func setupNotifications() {
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(textDidChange),
            name: UITextInputMode.currentInputModeDidChangeNotification,
            object: nil
        )
    }
    
    @objc private func textDidChange() {
        updateCurrentWord()
        updateSuggestions()
    }
    
    private func updateCurrentWord() {
        guard let proxy = textDocumentProxy as? UITextDocumentProxy else { return }
        
        if let beforeContext = proxy.documentContextBeforeInput {
            let components = beforeContext.components(separatedBy: CharacterSet.whitespacesAndNewlines)
            currentWord = components.last ?? ""
        } else {
            currentWord = ""
        }
    }
    
    private func updateSuggestions() {
        if currentWord.isEmpty {
            suggestions = []
        } else {
            suggestions = dictionary.getSuggestions(for: currentWord, limit: 3)
        }
        
        keyboardView.updateSuggestions(suggestions)
    }
    
    override func textWillChange(_ textInput: UITextInput?) {
        // Called before text changes
    }
    
    override func textDidChange(_ textInput: UITextInput?) {
        updateCurrentWord()
        updateSuggestions()
    }
}

// MARK: - KeyboardViewDelegate
extension KeyboardViewController: KeyboardViewDelegate {
    
    func keyboardView(_ keyboardView: KeyboardView, didTapKey key: String) {
        let proxy = textDocumentProxy
        
        switch key {
        case "⌫": // Delete
            proxy.deleteBackward()
            
        case "↵": // Return
            proxy.insertText("\n")
            finalizeCurrentWord()
            
        case "␣": // Space
            proxy.insertText(" ")
            finalizeCurrentWord()
            
        case "⇪": // Shift
            keyboardView.toggleShift()
            
        case "🌐": // Globe (switch keyboard)
            advanceToNextInputMode()
            
        default:
            proxy.insertText(key)
        }
        
        textDidChange(proxy)
    }
    
    func keyboardView(_ keyboardView: KeyboardView, didLongPressKey key: String) {
        // Handle long press for accents
        let accentMap: [String: [String]] = [
            "e": ["è", "é", "ê", "ë"],
            "o": ["ò", "ó", "ô", "ö"],
            "a": ["à", "á", "â", "ä"],
            "i": ["ì", "í", "î", "ï"],
            "u": ["ù", "ú", "û", "ü"],
            "c": ["ç"],
            "n": ["ñ"]
        ]
        
        if let accents = accentMap[key.lowercased()] {
            keyboardView.showAccentPopup(for: key, with: accents)
        }
    }
    
    func keyboardView(_ keyboardView: KeyboardView, didSelectSuggestion suggestion: String) {
        let proxy = textDocumentProxy
        
        // Delete current word
        for _ in 0..<currentWord.count {
            proxy.deleteBackward()
        }
        
        // Insert suggestion
        proxy.insertText(suggestion)
        
        // Track the word
        tracker.addWord(suggestion)
        
        currentWord = suggestion
        suggestions = []
        keyboardView.updateSuggestions([])
    }
    
    func keyboardView(_ keyboardView: KeyboardView, didSelectAccent accent: String) {
        let proxy = textDocumentProxy
        
        // Delete the base character
        proxy.deleteBackward()
        
        // Insert the accent
        proxy.insertText(accent)
        
        textDidChange(proxy)
    }
    
    private func finalizeCurrentWord() {
        if !currentWord.isEmpty {
            // Clean the word (remove punctuation)
            let cleanWord = currentWord.trimmingCharacters(in: CharacterSet.punctuationCharacters)
            
            if !cleanWord.isEmpty {
                tracker.addWord(cleanWord)
            }
            
            currentWord = ""
            suggestions = []
            keyboardView.updateSuggestions([])
        }
    }
}
