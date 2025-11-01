//
//  KeyboardView.swift
//  Klavyé Kréyòl Karukera Keyboard Extension
//
//  Created by Potomitan™ on 01/11/2025.
//

import UIKit

protocol KeyboardViewDelegate: AnyObject {
    func keyboardView(_ keyboardView: KeyboardView, didTapKey key: String)
    func keyboardView(_ keyboardView: KeyboardView, didLongPressKey key: String)
    func keyboardView(_ keyboardView: KeyboardView, didSelectSuggestion suggestion: String)
    func keyboardView(_ keyboardView: KeyboardView, didSelectAccent accent: String)
}

class KeyboardView: UIView {
    
    weak var delegate: KeyboardViewDelegate?
    
    private var suggestionBar: SuggestionBar!
    private var keyRows: [UIStackView] = []
    private var isShifted = false
    private var accentPopup: AccentPopupView?
    
    // Keyboard layout
    private let keyboardRows: [[String]] = [
        ["q", "w", "e", "r", "t", "y", "u", "i", "o", "p"],
        ["a", "s", "d", "f", "g", "h", "j", "k", "l"],
        ["⇪", "z", "x", "c", "v", "b", "n", "m", "⌫"],
        ["🌐", "␣", "↵"]
    ]
    
    override init(frame: CGRect) {
        super.init(frame: frame)
        setupUI()
    }
    
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    private func setupUI() {
        backgroundColor = UIColor(red: 0.82, green: 0.84, blue: 0.86, alpha: 1.0)
        
        // Suggestion Bar
        suggestionBar = SuggestionBar()
        suggestionBar.delegate = self
        addSubview(suggestionBar)
        suggestionBar.translatesAutoresizingMaskIntoConstraints = false
        
        // Container for keys
        let keysContainer = UIStackView()
        keysContainer.axis = .vertical
        keysContainer.spacing = 8
        keysContainer.distribution = .fillEqually
        addSubview(keysContainer)
        keysContainer.translatesAutoresizingMaskIntoConstraints = false
        
        // Create key rows
        for row in keyboardRows {
            let rowStack = createKeyRow(keys: row)
            keysContainer.addArrangedSubview(rowStack)
            keyRows.append(rowStack)
        }
        
        NSLayoutConstraint.activate([
            suggestionBar.topAnchor.constraint(equalTo: topAnchor),
            suggestionBar.leadingAnchor.constraint(equalTo: leadingAnchor),
            suggestionBar.trailingAnchor.constraint(equalTo: trailingAnchor),
            suggestionBar.heightAnchor.constraint(equalToConstant: 40),
            
            keysContainer.topAnchor.constraint(equalTo: suggestionBar.bottomAnchor, constant: 8),
            keysContainer.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 4),
            keysContainer.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -4),
            keysContainer.bottomAnchor.constraint(equalTo: bottomAnchor, constant: -4)
        ])
    }
    
    private func createKeyRow(keys: [String]) -> UIStackView {
        let stack = UIStackView()
        stack.axis = .horizontal
        stack.spacing = 6
        stack.distribution = .fillEqually
        
        for key in keys {
            let button = createKeyButton(key: key)
            stack.addArrangedSubview(button)
            
            // Special sizing for space and shift
            if key == "␣" {
                button.widthAnchor.constraint(equalTo: stack.widthAnchor, multiplier: 0.5).isActive = true
            } else if key == "⇪" || key == "⌫" || key == "🌐" || key == "↵" {
                button.widthAnchor.constraint(equalToConstant: 60).isActive = true
            }
        }
        
        return stack
    }
    
    private func createKeyButton(key: String) -> UIButton {
        let button = UIButton(type: .system)
        button.setTitle(key, for: .normal)
        button.titleLabel?.font = UIFont.systemFont(ofSize: 20, weight: .regular)
        button.backgroundColor = getKeyColor(for: key)
        button.setTitleColor(.black, for: .normal)
        button.layer.cornerRadius = 5
        button.layer.shadowColor = UIColor.black.cgColor
        button.layer.shadowOffset = CGSize(width: 0, height: 1)
        button.layer.shadowOpacity = 0.3
        button.layer.shadowRadius = 0
        
        button.addTarget(self, action: #selector(keyTapped(_:)), for: .touchUpInside)
        
        // Add long press gesture for vowels
        if "aeiou".contains(key.lowercased()) {
            let longPress = UILongPressGestureRecognizer(target: self, action: #selector(keyLongPressed(_:)))
            longPress.minimumPressDuration = 0.5
            button.addGestureRecognizer(longPress)
        }
        
        return button
    }
    
    private func getKeyColor(for key: String) -> UIColor {
        switch key {
        case "⇪", "⌫", "🌐":
            return UIColor(red: 0.67, green: 0.70, blue: 0.73, alpha: 1.0)
        case "␣", "↵":
            return .white
        default:
            return .white
        }
    }
    
    @objc private func keyTapped(_ sender: UIButton) {
        guard let key = sender.title(for: .normal) else { return }
        
        // Visual feedback
        UIView.animate(withDuration: 0.1, animations: {
            sender.transform = CGAffineTransform(scaleX: 0.95, y: 0.95)
        }) { _ in
            UIView.animate(withDuration: 0.1) {
                sender.transform = .identity
            }
        }
        
        var finalKey = key
        if isShifted && key.count == 1 && CharacterSet.letters.contains(key.unicodeScalars.first!) {
            finalKey = key.uppercased()
            // Auto-disable shift after letter
            if key != "⇪" {
                toggleShift()
            }
        }
        
        delegate?.keyboardView(self, didTapKey: finalKey)
    }
    
    @objc private func keyLongPressed(_ sender: UILongPressGestureRecognizer) {
        if sender.state == .began {
            guard let button = sender.view as? UIButton,
                  let key = button.title(for: .normal) else { return }
            
            delegate?.keyboardView(self, didLongPressKey: key)
        }
    }
    
    func toggleShift() {
        isShifted.toggle()
        
        // Update visual state of shift key and letters
        for rowStack in keyRows {
            for case let button as UIButton in rowStack.arrangedSubviews {
                if let title = button.title(for: .normal) {
                    if title == "⇪" {
                        button.backgroundColor = isShifted ? 
                            UIColor(red: 0.85, green: 0.27, blue: 0.23, alpha: 1.0) : 
                            UIColor(red: 0.67, green: 0.70, blue: 0.73, alpha: 1.0)
                    } else if title.count == 1 && CharacterSet.letters.contains(title.unicodeScalars.first!) {
                        button.setTitle(isShifted ? title.uppercased() : title.lowercased(), for: .normal)
                    }
                }
            }
        }
    }
    
    func updateSuggestions(_ suggestions: [String]) {
        suggestionBar.updateSuggestions(suggestions)
    }
    
    func showAccentPopup(for key: String, with accents: [String]) {
        // Remove existing popup
        accentPopup?.removeFromSuperview()
        
        // Create and show new popup
        accentPopup = AccentPopupView(baseKey: key, accents: accents)
        accentPopup?.delegate = self
        
        if let popup = accentPopup {
            addSubview(popup)
            popup.translatesAutoresizingMaskIntoConstraints = false
            
            NSLayoutConstraint.activate([
                popup.centerXAnchor.constraint(equalTo: centerXAnchor),
                popup.centerYAnchor.constraint(equalTo: centerYAnchor),
                popup.widthAnchor.constraint(equalToConstant: 280),
                popup.heightAnchor.constraint(equalToConstant: 60)
            ])
        }
    }
    
    func hideAccentPopup() {
        accentPopup?.removeFromSuperview()
        accentPopup = nil
    }
}

// MARK: - SuggestionBarDelegate
extension KeyboardView: SuggestionBarDelegate {
    func suggestionBar(_ bar: SuggestionBar, didSelectSuggestion suggestion: String) {
        delegate?.keyboardView(self, didSelectSuggestion: suggestion)
    }
}

// MARK: - AccentPopupDelegate
extension KeyboardView: AccentPopupDelegate {
    func accentPopup(_ popup: AccentPopupView, didSelectAccent accent: String) {
        delegate?.keyboardView(self, didSelectAccent: accent)
        hideAccentPopup()
    }
    
    func accentPopupDidCancel(_ popup: AccentPopupView) {
        hideAccentPopup()
    }
}
