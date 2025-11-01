//
//  KreyolKeyboardApp.swift
//  Klavyé Kréyòl Karukera
//
//  Created by Potomitan™ on 01/11/2025.
//  Copyright © 2025 Potomitan. All rights reserved.
//

import SwiftUI

@main
struct KreyolKeyboardApp: App {
    
    init() {
        // Configuration initiale de l'app
        setupAppearance()
    }
    
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
    
    /// Configure l'apparence globale de l'app
    private func setupAppearance() {
        // Couleur primaire : rouge créole Potomitan
        UINavigationBar.appearance().tintColor = UIColor(red: 0.75, green: 0.22, blue: 0.17, alpha: 1.0)
    }
}
