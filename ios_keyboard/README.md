# 🍎 Klavyé Kréyòl Karukera - iOS

Version iOS native du clavier créole guadeloupéen.

## 📋 Structure du Projet

```
ios_keyboard/
├── KreyolKeyboard/              # App conteneur principale
│   ├── Views/                   # Vues SwiftUI/UIKit
│   │   ├── OnboardingView.swift
│   │   ├── StatsView.swift
│   │   └── AboutView.swift
│   ├── ViewModels/
│   │   └── VocabularyStatsViewModel.swift
│   └── Resources/
│       └── Assets.xcassets/
├── KeyboardExtension/           # Extension clavier système
│   ├── KeyboardViewController.swift
│   ├── Models/
│   │   ├── CreoleDictionary.swift
│   │   ├── NGramPredictor.swift
│   │   └── AccentMapper.swift
│   ├── Views/
│   │   ├── KeyboardView.swift
│   │   └── SuggestionBar.swift
│   └── Resources/
│       ├── creole_dict.json     # Copié depuis android_keyboard
│       └── creole_ngrams.json   # Copié depuis android_keyboard
└── Shared/                      # Code partagé (App Group)
    ├── Models/
    │   ├── Word.swift
    │   └── VocabularyStats.swift
    └── Managers/
        ├── UserDefaultsManager.swift
        └── VocabularyTracker.swift
```

## 🎯 Roadmap MVP (6 semaines)

### Phase 1 : Setup & Configuration (Semaine 1-2)
- [x] Créer branche `feature/ios-keyboard`
- [ ] Créer projet Xcode
- [ ] Configurer Keyboard Extension target
- [ ] Configurer App Groups
- [ ] Importer dictionnaire et n-grams

### Phase 2 : Clavier Core (Semaine 3-4)
- [ ] Layout AZERTY créole
- [ ] Système de suggestions (top 3)
- [ ] Appui long pour accents
- [ ] Gestion des majuscules/minuscules

### Phase 3 : Interface Settings (Semaine 5)
- [ ] Onboarding 3 étapes
- [ ] Page "À Propos"
- [ ] Instructions activation

### Phase 4 : Tests & Publication (Semaine 6)
- [ ] Tests sur iPhone physique
- [ ] Screenshots App Store
- [ ] Privacy Policy
- [ ] Soumission Apple

## 📱 Prérequis

- macOS 14.0+ (Sonoma)
- Xcode 15.0+
- iOS 15.0+ (cible minimale)
- Apple Developer Account (pour tests sur appareil)

## 🚀 Installation

### 1. Ouvrir le projet Xcode
```bash
cd ios_keyboard
open KreyolKeyboard.xcodeproj
```

### 2. Configurer App Groups
Dans Xcode → Signing & Capabilities :
- Ajouter "App Groups"
- Group ID : `group.com.potomitan.kreyolkeyboard`

### 3. Build & Run
- Sélectionner target "KreyolKeyboard"
- Choisir simulateur ou appareil
- Cmd+R pour build

## 📦 Assets Partagés

Le dictionnaire et les n-grams sont réutilisés depuis Android :
- `../android_keyboard/app/src/main/assets/creole_dict.json`
- `../android_keyboard/app/src/main/assets/creole_ngrams.json`

## 🔧 Configuration Technique

### App Groups (Partage de données)
```swift
let sharedDefaults = UserDefaults(
    suiteName: "group.com.potomitan.kreyolkeyboard"
)
```

### Limitations iOS
- Extension clavier : limite 30MB RAM
- Pas d'accès réseau depuis le clavier
- Stockage local uniquement (App Group)

## 📝 Notes de Développement

### Différences Android → iOS

| Fonctionnalité | Android | iOS |
|----------------|---------|-----|
| Classe clavier | InputMethodService | UIInputViewController |
| Stockage | SharedPreferences | UserDefaults + App Group |
| Suggestions | getSuggestions() | textWillChange() |
| Layout | XML layouts | Programmatique (UIKit/SwiftUI) |

### Accents Créoles
```
è ò à é ù ô ê â
```

Implémentés via appui long sur les touches de base.

## 🎮 Gamification (Phase 2)

Système de niveaux identique à Android :
1. 🌍 Pipirit
2. 🌱 Ti moun
3. 🔥 Débrouya
4. 💎 An mitan
5. 🐇 Kompè Lapen
6. 🐘 Kompè Zamba
7. 👑 Potomitan

## 📄 License

Même licence que le projet parent : voir [LICENSE](../LICENSE)

## 👨‍💻 Auteur

**Médhi Famibelle** - [Potomitan™](https://potomitan.io)

---

**Status** : 🚧 En cours de développement - MVP Phase 1
