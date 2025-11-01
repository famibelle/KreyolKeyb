//
//  AccentPopupView.swift
//  Klavyé Kréyòl Karukera Keyboard Extension
//
//  Created by Potomitan™ on 01/11/2025.
//

import UIKit

protocol AccentPopupDelegate: AnyObject {
    func accentPopup(_ popup: AccentPopupView, didSelectAccent accent: String)
    func accentPopupDidCancel(_ popup: AccentPopupView)
}

class AccentPopupView: UIView {
    
    weak var delegate: AccentPopupDelegate?
    private let baseKey: String
    private let accents: [String]
    private var accentButtons: [UIButton] = []
    
    init(baseKey: String, accents: [String]) {
        self.baseKey = baseKey
        self.accents = accents
        super.init(frame: .zero)
        setupUI()
    }
    
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    private func setupUI() {
        backgroundColor = .white
        layer.cornerRadius = 10
        layer.shadowColor = UIColor.black.cgColor
        layer.shadowOffset = CGSize(width: 0, height: 2)
        layer.shadowOpacity = 0.3
        layer.shadowRadius = 4
        
        let stackView = UIStackView()
        stackView.axis = .horizontal
        stackView.distribution = .fillEqually
        stackView.spacing = 8
        addSubview(stackView)
        
        stackView.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            stackView.topAnchor.constraint(equalTo: topAnchor, constant: 8),
            stackView.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 8),
            stackView.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -8),
            stackView.bottomAnchor.constraint(equalTo: bottomAnchor, constant: -8)
        ])
        
        // Create buttons for each accent
        for accent in accents {
            let button = UIButton(type: .system)
            button.setTitle(accent, for: .normal)
            button.titleLabel?.font = UIFont.systemFont(ofSize: 24, weight: .medium)
            button.setTitleColor(.black, for: .normal)
            button.backgroundColor = UIColor(red: 0.95, green: 0.95, blue: 0.97, alpha: 1.0)
            button.layer.cornerRadius = 5
            button.addTarget(self, action: #selector(accentTapped(_:)), for: .touchUpInside)
            
            accentButtons.append(button)
            stackView.addArrangedSubview(button)
        }
        
        // Add tap gesture to dismiss
        let tapGesture = UITapGestureRecognizer(target: self, action: #selector(backgroundTapped))
        tapGesture.delegate = self
        addGestureRecognizer(tapGesture)
    }
    
    @objc private func accentTapped(_ sender: UIButton) {
        guard let accent = sender.title(for: .normal) else { return }
        delegate?.accentPopup(self, didSelectAccent: accent)
    }
    
    @objc private func backgroundTapped() {
        delegate?.accentPopupDidCancel(self)
    }
}

extension AccentPopupView: UIGestureRecognizerDelegate {
    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldReceive touch: UITouch) -> Bool {
        // Only handle taps on the background, not on buttons
        return !(touch.view is UIButton)
    }
}
