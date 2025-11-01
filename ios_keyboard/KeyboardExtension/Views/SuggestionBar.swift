//
//  SuggestionBar.swift
//  Klavyé Kréyòl Karukera Keyboard Extension
//
//  Created by Potomitan™ on 01/11/2025.
//

import UIKit

protocol SuggestionBarDelegate: AnyObject {
    func suggestionBar(_ bar: SuggestionBar, didSelectSuggestion suggestion: String)
}

class SuggestionBar: UIView {
    
    weak var delegate: SuggestionBarDelegate?
    private var suggestionButtons: [UIButton] = []
    private let stackView = UIStackView()
    
    override init(frame: CGRect) {
        super.init(frame: frame)
        setupUI()
    }
    
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    private func setupUI() {
        backgroundColor = UIColor(red: 0.95, green: 0.95, blue: 0.97, alpha: 1.0)
        
        stackView.axis = .horizontal
        stackView.distribution = .fillEqually
        stackView.spacing = 8
        addSubview(stackView)
        
        stackView.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            stackView.topAnchor.constraint(equalTo: topAnchor, constant: 4),
            stackView.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 8),
            stackView.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -8),
            stackView.bottomAnchor.constraint(equalTo: bottomAnchor, constant: -4)
        ])
        
        // Create 3 suggestion buttons
        for _ in 0..<3 {
            let button = createSuggestionButton()
            suggestionButtons.append(button)
            stackView.addArrangedSubview(button)
        }
    }
    
    private func createSuggestionButton() -> UIButton {
        let button = UIButton(type: .system)
        button.titleLabel?.font = UIFont.systemFont(ofSize: 16, weight: .medium)
        button.setTitleColor(.black, for: .normal)
        button.backgroundColor = .white
        button.layer.cornerRadius = 5
        button.layer.borderWidth = 0.5
        button.layer.borderColor = UIColor.lightGray.cgColor
        button.isHidden = true
        button.addTarget(self, action: #selector(suggestionTapped(_:)), for: .touchUpInside)
        return button
    }
    
    func updateSuggestions(_ suggestions: [String]) {
        // Hide all buttons first
        suggestionButtons.forEach { $0.isHidden = true }
        
        // Update with new suggestions
        for (index, suggestion) in suggestions.prefix(3).enumerated() {
            suggestionButtons[index].setTitle(suggestion, for: .normal)
            suggestionButtons[index].isHidden = false
        }
    }
    
    @objc private func suggestionTapped(_ sender: UIButton) {
        guard let suggestion = sender.title(for: .normal) else { return }
        
        // Visual feedback
        UIView.animate(withDuration: 0.1, animations: {
            sender.transform = CGAffineTransform(scaleX: 0.95, y: 0.95)
        }) { _ in
            UIView.animate(withDuration: 0.1) {
                sender.transform = .identity
            }
        }
        
        delegate?.suggestionBar(self, didSelectSuggestion: suggestion)
    }
}
