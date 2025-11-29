# 📱 Transposition iOS - Klavyé Kréyòl

## 💰 Budget
**9,000€ - 15,000€** (économie de 60% vs développement from scratch)

---

## ⏱️ Durée
**4-5 semaines** (temps plein)

---

## 👤 Profil
- ✅ **Custom Keyboard Extension iOS** (1 projet minimum)
- ✅ **Swift + UIKit**
- ✅ **Publication App Store**
- ⭐ Bonus : SwiftUI, expérience Android

---

## 📅 Planning

| Semaine | Tâches |
|---------|--------|
| **1** | Setup Xcode + import dictionnaires JSON + visuels |
| **2-3** | Clavier (4 rangées touches) + moteur suggestions (adapter Kotlin→Swift) |
| **4** | Interface configuration + statistiques/gamification |
| **5** | Tests + Beta TestFlight + Soumission App Store |

---

## 🔧 Technique

**Stack iOS** : UIInputViewController, App Groups, Codable (JSON), FileManager

**3 fichiers principaux à adapter** :
1. `SuggestionEngine.kt` → Swift (recherche préfixe + Levenshtein + N-grams)
2. `KeyboardLayoutManager.kt` → UIKit (layout 4 rangées + touches)
3. `VocabularyStats.kt` → SwiftUI/UIKit (gamification 8 niveaux)

**Assets fournis** : `creole_dict.json` (2,833 mots), `creole_ngrams.json`, icônes, captures

---

## 📦 Livrables

1. ✅ Code iOS sur GitHub
2. ✅ App publiée sur App Store
3. ✅ Beta TestFlight (10+ testeurs)
4. ✅ Documentation (README + guide)

---

**Repo** : [github.com/famibelle/KreyolKeyb](https://github.com/famibelle/KreyolKeyb) · Branch `feature/ios-keyboard` · **Tout le code Android est fourni**

*v3.1 - 29 nov 2025*

#### ⭐ Compétences FORTEMENT RECOMMANDÉES
- SwiftUI pour interface de configuration
- Combine/async-await pour réactivité
- Tests automatisés (XCTest, XCUITest)
- CI/CD avec GitHub Actions ou Fastlane
- Expérience avec dictionnaires linguistiques

#### 🎁 BONUS
- Connaissance du créole guadeloupéen
- Sensibilité aux projets culturels/patrimoniaux
- Portfolio avec apps publiées (App Store reviews)

### 📦 Livrables Attendus

| Livrable | Description |
|----------|-------------|
| **App iOS Container** | Interface de configuration & onboarding (3 étapes) |
| **Keyboard Extension** | Clavier personnalisé avec touches créoles |
| **Moteur de suggestions** | Intelligence artificielle : N-grams + Levenshtein |
| **Dictionnaires** | ~2,833 mots créoles + français (déjà fournis) |
| **Gamification** | 8 niveaux culturels avec tracking vocabulaire |
| **Tests** | Suite de tests unitaires & UI |
| **Documentation** | Guide de maintenance & architecture |
| **Package App Store** | Prêt pour soumission (captures, description, etc.) |
