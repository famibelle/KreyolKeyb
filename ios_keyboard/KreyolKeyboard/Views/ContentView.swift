//
//  ContentView.swift
//  Klavyé Kréyòl Karukera
//
//  Created by Potomitan™ on 01/11/2025.
//

import SwiftUI

struct ContentView: View {
    @State private var selectedTab = 0
    
    var body: some View {
        TabView(selection: $selectedTab) {
            OnboardingView()
                .tabItem {
                    Label("Démarrage", systemImage: "rocket.fill")
                }
                .tag(0)
            
            StatsView()
                .tabItem {
                    Label("Kréyòl an mwen", systemImage: "chart.bar.fill")
                }
                .tag(1)
            
            AboutView()
                .tabItem {
                    Label("À Propos", systemImage: "info.circle.fill")
                }
                .tag(2)
        }
        .accentColor(Color("PotomitanRed"))
    }
}

#Preview {
    ContentView()
}
