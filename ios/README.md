# KreyolKeyb — iOS Port

Portage de l'app Android **Klavyé Kréyòl Karukera • Potomitan™** vers iOS.

## Structure

```
ios/
└── KreyolKeyb/
    ├── Core/
    │   ├── AccentTolerantMatcher.swift   ← Recherche insensible aux accents
    │   ├── LevenshteinDistance.swift     ← Correction orthographique
    │   └── SuggestionEngine.swift        ← Moteur de suggestions bilingue (Kreyòl + FR)
    │
    ├── Models/
    │   └── BilingualSuggestion.swift     ← Types : suggestion, langue, couleurs, config
    │
    ├── Gamification/
    │   ├── WordUsageStats.swift          ← Stats par mot + niveaux (Pipirit → Potomitan)
    │   └── CreoleDictionaryWithUsage.swift ← Suivi vocabulaire local (actor Swift)
    │
    ├── Games/
    │   ├── WordSearchModels.swift        ← Modèles mots mêlés
    │   ├── WordSearchGenerator.swift     ← Générateur de grilles
    │   └── WordScrambleModels.swift      ← Modèles mots mélangés
    │
    ├── Views/
    │   ├── ContentView.swift             ← Navigation 4 onglets (TabView)
    │   ├── KeyboardSetupView.swift       ← Onglet 1 : activation + zone de test
    │   ├── VocabularyStatsView.swift     ← Onglet 2 : stats vocabulaire
    │   ├── WordSearchView.swift          ← Onglet 3 : jeu mots mêlés
    │   └── WordScrambleView.swift        ← Onglet 4 : jeu mots mélangés
    │
    ├── KeyboardExtension/
    │   └── KeyboardViewController.swift  ← Squelette UIInputViewController (Phase 2)
    │
    └── Resources/
        └── (creole_dict.json, creole_ngrams.json, french_simple_dict.json — copier depuis android_keyboard/app/src/main/assets/)
```

## Phases

### Phase 1 — Terminée (sans Mac)
- [x] Portage de tous les algorithmes Kotlin → Swift
- [x] Modèles de données
- [x] Système de gamification (7 niveaux)
- [x] Générateur mots mêlés + mots mélangés
- [x] Vues SwiftUI : stats, jeux, setup
- [x] Navigation principale (TabView)
- [x] Squelette de la Keyboard Extension

### Phase 2 — Nécessite Mac + Xcode
- [ ] Créer le projet Xcode avec deux targets (App + KeyboardExtension)
- [ ] Copier les fichiers Swift dans le projet
- [ ] Copier les assets JSON dans les ressources
- [ ] Implémenter KeyboardView (touches + barre suggestions)
- [ ] Implémenter AccentPopupView (long-press)
- [ ] Configurer App Group pour partage Extension ↔ App
- [ ] Tester sur simulateur puis device réel

### Phase 3 — Publication
- [ ] App Store Connect
- [ ] TestFlight (bêta)
- [ ] Soumission App Store

## Créer le projet Xcode (Phase 2)

```
1. Xcode → File → New → Project → App
   - Product Name: KreyolKeyb
   - Bundle ID: com.potomitan.kreyolkeyb
   - Language: Swift
   - Interface: SwiftUI

2. File → New → Target → Custom Keyboard Extension
   - Product Name: KreyolKeybExtension
   - Bundle ID: com.potomitan.kreyolkeyb.keyboard

3. Activer App Group sur les deux targets :
   Signing & Capabilities → + → App Groups
   → group.com.potomitan.kreyolkeyb

4. Copier les fichiers Swift dans les dossiers correspondants

5. Copier les JSON depuis android_keyboard/app/src/main/assets/
   dans le dossier Resources/ et les ajouter aux deux targets
```

## Correspondances Android → iOS

| Android | iOS |
|---------|-----|
| `InputMethodService` | `UIInputViewController` |
| `Activity` + XML layout | `View` SwiftUI |
| `ViewModel` (Android) | `@StateObject` / `ObservableObject` |
| `Context` | `Bundle.main` / `FileManager` |
| `SharedPreferences` | `UserDefaults` |
| `filesDir` | `FileManager.urls(.documentDirectory)` |
| `Assets` | `Bundle.main.url(forResource:)` |
| `Coroutines` | `async/await` + `Task` |
| `CoroutineScope` | `Task.detached` |
| `GridView` | `LazyVGrid` SwiftUI |
| `RecyclerView` | `List` / `LazyVStack` SwiftUI |
