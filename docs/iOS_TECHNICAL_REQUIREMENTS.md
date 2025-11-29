# 📱 Skills Techniques Requis - Transposition iOS de Klavyé Kréyòl

## 🎯 Compétences Fondamentales iOS

### 1. **Custom Keyboard Extension (Essentiel)**
- **App Extension Architecture** : Maîtrise du framework `Custom Keyboard Extension`
- **UIInputViewController** : Sous-classe et implémentation du contrôleur de clavier
- **Sandboxing** : Gestion des limitations de sécurité iOS pour les extensions
- **App Groups** : Partage de données entre l'app container et l'extension clavier
- **Auto Layout** : Interface responsive adaptée aux différentes tailles d'écran (iPhone/iPad)

### 2. **Langages & Frameworks**
- **Swift 5.9+** : Langage natif moderne (ou Objective-C legacy)
- **UIKit** : Construction d'interface pour le clavier personnalisé
- **SwiftUI** : Interface de configuration et statistiques (optionnel mais recommandé)
- **Combine** : Programmation réactive pour gestion des suggestions asynchrones
- **Concurrency** : `async/await`, `Task`, `Actor` pour opérations I/O

### 3. **Gestion des Données**
- **UserDefaults** avec App Groups : Partage de préférences
- **FileManager** : Lecture/écriture de fichiers JSON (dictionnaires, N-grams)
- **Codable Protocol** : Sérialisation/désérialisation des modèles de données
- **Core Data** (optionnel) : Stockage des statistiques d'utilisation si volume important

## 🧠 Fonctionnalités Spécifiques à Implémenter

### 4. **Moteur de Suggestions Intelligent**
- **Algorithms** : Implémentation de l'algorithme de Levenshtein (distance d'édition)
- **Trie/Prefix Tree** : Structure de données optimisée pour recherche par préfixe
- **N-grams Contextual Predictions** : 
  - Parsing du modèle JSON (~480 mots avec prédictions)
  - Calcul de probabilités conditionnelles P(mot₂|mot₁)
  - Historique des 5 derniers mots tapés (FIFO)

### 5. **Dictionnaires Bilingues**
- **Kreyòl Dictionary** : ~2,833 mots avec fréquences
- **French Dictionary** : Support du français (activation à partir de 3 lettres)
- **Accent Normalization** : Gestion des caractères créoles (ò, é, è, ù, à, â, ê, î, ô, û, ç)
- **Case Pattern Matching** : Préservation des majuscules/minuscules (ex: "kaBr" → "kaBrit")

### 6. **Interface Utilisateur du Clavier**
- **Custom Key Views** : Création de touches personnalisées avec accents longs pressés
- **Gesture Recognizers** : 
  - Tap simple pour saisie
  - Long press pour caractères alternatifs (espace → sélecteur de langue)
  - Swipe gestures (optionnel)
- **Dynamic Type** : Support des tailles de police accessibles iOS
- **Dark Mode** : Adaptation automatique au thème système
- **Haptic Feedback** : Retour tactile via `UIImpactFeedbackGenerator`

### 7. **Gestion des États du Clavier**
- **Caps Lock / Shift** : Gestion des modes majuscule/minuscule
- **Numeric Mode** : Basculement clavier alphabétique ↔ numérique
- **Special Characters** : Accès aux caractères spéciaux créoles
- **Autocapitalization** : Majuscule automatique après ponctuation

## 📊 Gamification & Statistiques

### 8. **Vocabulary Tracking System**
- **Word Usage Analytics** : Comptage des mots utilisés (respect vie privée)
- **Progress Calculation** : 
  - Pourcentage de couverture du dictionnaire (wordsDiscovered / totalWords)
  - Calcul des niveaux avec distribution gaussienne (8 niveaux culturels)
- **Data Persistence** : Sauvegarde incrémentale des statistiques
- **Batch Updates** : Optimisation I/O (sauvegarde toutes les 10 utilisations)

### 9. **Interface de Configuration**
- **Onboarding Flow** : Guide d'activation en 3 étapes
- **Settings UI** : 
  - Onglet statistiques avec graphiques (optionnel: Charts framework)
  - Top 5 mots les plus utilisés
  - Mots à découvrir
  - Progression par niveau
- **Navigation** : TabView ou PageViewController pour onglets

## 🔧 Optimisations & Performance

### 10. **Performance iOS**
- **Lazy Loading** : Chargement différé des dictionnaires volumineux
- **Memory Management** : Gestion ARC, éviter les retain cycles
- **Background Processing** : Calculs intensifs sur queue background
- **Caching Strategy** : 
  - Cache en mémoire pour suggestions fréquentes
  - Invalidation intelligente du cache

### 11. **Testing & Quality**
- **XCTest** : Tests unitaires pour algorithmes (Levenshtein, N-grams)
- **XCUITest** : Tests d'interface pour le clavier
- **Performance Testing** : Instruments (Time Profiler, Allocations)
- **Accessibility** : VoiceOver support, Dynamic Type

## 🌐 Localisation & Internationalisation

### 12. **i18n/L10n**
- **NSLocalizedString** : Gestion des chaînes traduites (Français/Créole)
- **Locale Management** : Support multi-langue
- **Right-to-Left** : Bien que non nécessaire pour Créole (LTR)

## 📦 Distribution & CI/CD

### 13. **App Store & TestFlight**
- **App Store Connect** : Soumission app + extension keyboard
- **Privacy Policy** : Conformité GDPR (fonctionnement 100% offline)
- **App Review Guidelines** : Respect des règles Apple pour extensions
- **Fastlane** (optionnel) : Automatisation du build et déploiement

### 14. **Version Control & Collaboration**
- **Git/GitHub** : Workflow de développement
- **GitHub Actions** : CI/CD pour builds automatiques
- **Code Review** : Bonnes pratiques de revue de code

## 🔒 Sécurité & Vie Privée

### 15. **Privacy Compliance**
- **No Network Access** : Extension clavier fonctionnant 100% offline
- **Local Data Only** : Aucune synchronisation cloud
- **Open Full Access** : Gérer le prompt iOS (si nécessaire pour haptics)
- **Transparent Data Usage** : Communication claire sur le tracking local

## 📚 Connaissances Linguistiques (Bonus)

### 16. **Créole Guadeloupéen**
- Compréhension basique du créole pour tester les suggestions
- Connaissance des patterns orthographiques créoles
- Sensibilité culturelle (noms de niveaux: Pipirit, Kompè Lapen, Potomitan, etc.)

---

## 🎓 Niveau d'Expertise Recommandé

### Minimum (Junior/Mid-level)
✅ Swift/UIKit basique  
✅ Custom Keyboard Extension (tutoriels disponibles)  
✅ Gestion JSON et Codable  
✅ Auto Layout  

### Idéal (Senior)
✅ Architecture MVC/MVVM avancée  
✅ Algorithmes (Levenshtein, Trie)  
✅ Optimisation performance iOS  
✅ Tests automatisés (XCTest/XCUITest)  
✅ Publication App Store  

---

## 📖 Ressources Clés à Consulter

### Documentation Apple
1. **[App Extension Programming Guide - Custom Keyboard](https://developer.apple.com/library/archive/documentation/General/Conceptual/ExtensibilityPG/CustomKeyboard.html)**
2. **[UIInputViewController Reference](https://developer.apple.com/documentation/uikit/uiinputviewcontroller)**
3. **[App Groups Entitlement](https://developer.apple.com/documentation/bundleresources/entitlements/com_apple_security_application-groups)**

### Code Source Android à Adapter
1. **`SuggestionEngine.kt`** : Logique de suggestions bilingues
   - Algorithme de Levenshtein
   - Recherche par préfixe avec tolérance aux accents
   - N-grams contextuels
   - Scoring et ranking des suggestions

2. **`KeyboardLayoutManager.kt`** : Layout et gestion des touches
   - Création dynamique des rangées de touches
   - Gestion des modes (alphabétique/numérique)
   - Long press pour caractères alternatifs
   - Stylisme des touches

3. **`InputProcessor.kt`** : Traitement de la saisie
   - Gestion du mot courant
   - Détection de fin de mot
   - Application de la casse
   - Auto-capitalisation

4. **`BilingualSuggestion.kt`** : Modèle de données
   - Structure des suggestions bilingues
   - Couleurs par langue (vert Kreyòl / bleu Français)
   - Sources des suggestions (dictionnaire/N-gram/hybride)

5. **`gamification/VocabularyStats.kt`** : Système de gamification
   - Calcul de progression
   - Distribution gaussienne des niveaux
   - Tracking d'utilisation des mots

### Assets à Réutiliser
1. **`creole_dict.json`** : Dictionnaire principal (~2,833 mots)
   ```json
   [
     ["bonjou", 450],
     ["kréyòl", 89],
     ["mèsi", 200]
   ]
   ```

2. **`creole_ngrams.json`** : Modèle de prédictions contextuelles
   ```json
   {
     "ka": [
       {"word": "fè", "probability": 0.08},
       {"word": "di", "probability": 0.04}
     ]
   }
   ```

3. **`french_dict.json`** : Dictionnaire français (si support bilingue)

### Algorithmes Clés

#### Distance de Levenshtein (Kotlin → Swift)
```kotlin
// Android (Kotlin) - LevenshteinDistance.kt
fun calculate(s1: String, s2: String, maxDistance: Int = 2): Int {
    val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
    // ... logique DP
}
```

Adapter en Swift avec optimisation mémoire :
```swift
// iOS (Swift)
func levenshteinDistance(_ s1: String, _ s2: String, maxDistance: Int = 2) -> Int {
    let dp = [[Int]](repeating: [Int](repeating: 0, count: s2.count + 1), 
                     count: s1.count + 1)
    // ... même logique DP
}
```

#### Recherche par Préfixe avec Trie
```swift
class TrieNode {
    var children: [Character: TrieNode] = [:]
    var word: String?
    var frequency: Int = 0
}

class DictionaryTrie {
    private let root = TrieNode()
    
    func insert(_ word: String, frequency: Int) { /* ... */ }
    func search(prefix: String) -> [String] { /* ... */ }
}
```

#### N-grams Contextuels
```swift
struct NGramModel {
    private var predictions: [String: [(word: String, probability: Double)]]
    
    func predict(afterWord word: String, count: Int = 5) -> [String] {
        return predictions[word.lowercased()]?
            .sorted { $0.probability > $1.probability }
            .prefix(count)
            .map { $0.word } ?? []
    }
}
```

---

## 📋 Checklist de Développement

### Phase 1 : Setup (Semaine 1)
- [ ] Créer projet Xcode avec App + Keyboard Extension
- [ ] Configurer App Groups pour partage de données
- [ ] Importer assets JSON (dictionnaires, N-grams)
- [ ] Setup architecture MVC/MVVM

### Phase 2 : Core Features (Semaines 2-3)
- [ ] Implémenter UIInputViewController
- [ ] Créer layout de clavier personnalisé
- [ ] Implémenter moteur de suggestions (Levenshtein + Trie)
- [ ] Parser et intégrer modèle N-grams
- [ ] Gestion des accents créoles (long press)
- [ ] Préservation de la casse (case pattern matching)

### Phase 3 : UI & UX (Semaine 4)
- [ ] Interface de configuration (onboarding)
- [ ] Dark mode support
- [ ] Haptic feedback
- [ ] Animations et transitions
- [ ] Accessibilité (VoiceOver)

### Phase 4 : Gamification (Semaine 5)
- [ ] Système de tracking vocabulaire
- [ ] Calcul distribution gaussienne des niveaux
- [ ] Interface statistiques avec graphiques
- [ ] Persistence des données utilisateur

### Phase 5 : Testing & Polish (Semaine 6)
- [ ] Tests unitaires (XCTest)
- [ ] Tests UI (XCUITest)
- [ ] Optimisation performance (Instruments)
- [ ] Beta testing (TestFlight)
- [ ] Préparation App Store (captures, description, etc.)

---

## 🚀 Estimation de Charge

| Tâche | Effort (jours) | Profil |
|-------|----------------|--------|
| Setup projet + architecture | 2-3 | Mid-level |
| Custom keyboard extension | 5-7 | Mid-level |
| Moteur de suggestions | 5-7 | Senior |
| Dictionnaires + N-grams | 3-4 | Mid-level |
| UI/UX (config + stats) | 4-5 | Mid-level |
| Gamification | 3-4 | Mid-level |
| Testing + optimisation | 3-5 | Senior |
| **TOTAL** | **25-35 jours** | **~5-7 semaines** |

**Note** : Estimation pour un développeur iOS expérimenté travaillant à temps plein.

---

## 💡 Défis Spécifiques iOS

### 1. **Limitations des Keyboard Extensions**
- Pas d'accès réseau (sauf avec "Allow Full Access")
- Mémoire limitée (~48 MB)
- Pas de UIAlertController
- Cycle de vie différent de l'app principale

**Solutions** :
- Pré-charger dictionnaires en mémoire avec optimisation
- Utiliser App Groups pour communication avec app container
- Compression/lazy loading des gros assets

### 2. **Haptic Feedback**
- Nécessite "Allow Full Access" activé par l'utilisateur
- Gestion du prompt de permission

**Solution** :
- Dégradation gracieuse (fallback sans haptics)
- Communication claire dans l'onboarding

### 3. **Dark Mode**
- Support obligatoire depuis iOS 13
- Adaptation dynamique des couleurs

**Solution** :
- Utiliser `UIColor.systemBackground` et semantic colors
- Tester en mode clair et sombre

### 4. **Multitâche & État**
- L'extension peut être tuée à tout moment par iOS
- Sauvegarder l'état fréquemment

**Solution** :
- Persistence incrémentale (batch updates)
- Restauration automatique de l'état

---

## 🎨 Design System

### Couleurs Clés
```swift
// Couleurs du clavier (depuis Android)
struct KeyboardColors {
    static let kreyolGreen = UIColor(hex: "#50C878")  // Vert émeraude (Kreyòl)
    static let frenchBlue = UIColor(hex: "#4A90E2")   // Bleu France
    static let backgroundNeutral = UIColor(hex: "#F8F9FA")
    
    // Adaptation Dark Mode
    static let keyBackground = UIColor { traitCollection in
        traitCollection.userInterfaceStyle == .dark
            ? UIColor(hex: "#2C2C2E")
            : UIColor(hex: "#FFFFFF")
    }
}
```

### Dimensions
```swift
struct KeyboardDimensions {
    static let keyHeight: CGFloat = 48
    static let keySpacing: CGFloat = 4
    static let cornerRadius: CGFloat = 8
    static let fontSize: CGFloat = 20
}
```

---

## 📞 Support & Questions

Pour toute question technique sur la transposition iOS :
- **Repo Android** : [github.com/famibelle/KreyolKeyb](https://github.com/famibelle/KreyolKeyb)
- **Issues** : Créer une issue sur le repo avec tag `[iOS]`
- **Email** : [à définir pour questions spécifiques]

---

**Document créé le** : 29 novembre 2025  
**Version** : 1.0  
**Auteur** : GitHub Copilot (basé sur l'architecture Android existante)  
**Licence** : Open Source (à définir - même que Android)

---

*Pou an kreyòl ki ka viv é ka evolyé sou tout platfòm! 🇬🇵📱*
