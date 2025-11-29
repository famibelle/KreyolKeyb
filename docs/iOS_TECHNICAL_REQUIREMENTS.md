# 📱 Transposition iOS de Klavyé Kréyòl - Cahier des Charges

> **Clavier créole intelligent** avec suggestions bilingues (Kreyòl/Français), gamification et 100% offline

---

## 💼 RÉSUMÉ EXÉCUTIF (Pour Commercial)

### 🎯 Contexte du Projet
**Situation actuelle** : Application Android de clavier créole guadeloupéen **100% FONCTIONNELLE** avec 2,833 mots, suggestions intelligentes, correction orthographique et gamification.

**Objectif** : **ADAPTER** (pas créer) cette application sur iOS pour toucher 60% de nouveaux utilisateurs mobiles en Guadeloupe.

**Simplicité du projet** : 
- ✅ Un clavier = **interface très simple** (4 rangées de touches + barre de suggestions)
- ✅ Pas de backend, pas d'API, pas de multi-écrans complexes
- ✅ Tous les visuels (icônes, logos, captures) sont **déjà créés**
- ✅ Tout le code métier est **déjà écrit en Android** (juste à traduire en Swift)

### ✅ CE QUI EXISTE DÉJÀ (Gain de temps majeur)

| Asset | État | Avantage pour iOS |
|-------|------|-------------------|
| **Code source Android** | ✅ Complet & testé | Toute la logique métier à traduire (pas à concevoir) |
| **Dictionnaires** | ✅ 2,833 mots + fréquences | Fichiers JSON prêts à copier |
| **Algorithmes** | ✅ Levenshtein, N-grams, recherche | Code Kotlin à adapter en Swift (même logique) |
| **UI/UX Design** | ✅ Finalisé avec specs | Couleurs, dimensions, interactions déjà définies |
| **Visuels complets** | ✅ Icônes, logos, captures | Assets graphiques prêts (juste adaptation iOS) |
| **Gamification** | ✅ 8 niveaux culturels | Système complet à porter |
| **Documentation** | ✅ Code commenté | Facilite la compréhension |

**💡 Impact** : Le développeur iOS ne part PAS de zéro = **économie de 50-60% vs développement from scratch**

**🎯 Nature du projet** : Il s'agit d'un **clavier simple** (pas une app complexe avec backend/API), donc interface basique et prévisible.

### 💰 Estimation Budgétaire Réaliste

| Profil | Durée | TJM indicatif | Budget Total | Type de mission |
|--------|-------|--------------|--------------|-----------------|
| **iOS Senior** | **4-5 semaines** | 500-700€/jour | **10,000€ - 17,500€** | Adaptation rapide |
| **iOS Mid-level** | **5-6 semaines** | 350-500€/jour | **8,750€ - 15,000€** | Mission simple avec support |

**Comparaison** : 
- Développement iOS from scratch = 35,000€ - 50,000€ (8-12 semaines)
- **Économie réalisée** : ~60% grâce au code Android existant + visuels prêts

**Simplicité du projet** : Un clavier = interface très basique (touches + suggestions), pas de complexité backend/API/multi-écrans.

### 📅 Planning Simplifié (4-5 semaines)

```
Semaine 1 : Setup + Extension Keyboard
  ├─ Création projet Xcode (1 jour)
  ├─ Configuration Custom Keyboard Extension (2 jours)
  └─ Import dictionnaires JSON + visuels (1 jour)

Semaine 2-3 : Clavier + Moteur de Suggestions
  ├─ Layout clavier (touches simples) (3 jours)
  ├─ Adaptation algorithmes (Kotlin → Swift) (4 jours)
  └─ Intégration N-grams + suggestions (3 jours)

Semaine 4 : Interface & Gamification
  ├─ UI de configuration (2 jours)
  ├─ Statistiques (visuels déjà prêts) (2 jours)
  └─ Dark mode + polish (1 jour)

Semaine 5 : Tests & Publication
  ├─ Tests (2 jours)
  ├─ Beta TestFlight (1 jour)
  └─ Soumission App Store (2 jours)
```

**Avantage clavier** : Interface prévisible et standardisée (rangées de touches), pas de créativité UX/UI complexe requise.

### 🔑 Profil Développeur Recherché

#### ✅ MUST HAVE (Critères bloquants)
1. **Custom Keyboard Extension iOS** → Au moins 1 projet démontrable
2. **Swift + UIKit** → Maîtrise confirmée
3. **App Store** → Au moins 1 app publiée
4. **Algorithmes** → Capable d'adapter Levenshtein (fourni en Kotlin)

#### ⭐ NICE TO HAVE (Bonus appréciés)
- SwiftUI (UI moderne)
- Expérience Android (lecture du code source facilitée)
- Localisation français/créole
- Connaissances en NLP (Natural Language Processing)

### 📦 Livrables Attendus

| Livrable | Description | Délai |
|----------|-------------|-------|
| ✅ **Code iOS** | Repo GitHub Swift avec architecture claire | Fin mission |
| ✅ **App Store** | App publiée et visible au public | Semaine 7 |
| ✅ **TestFlight** | Beta testing (10-50 testeurs) | Semaine 6 |
| ✅ **Documentation** | README + guide technique iOS | Fin mission |
| ✅ **Assets** | Icônes iOS + captures App Store | Semaine 6 |

### 🎁 Avantages Compétitifs du Projet

| Avantage | Bénéfice |
|----------|----------|
| 🚀 **Code Android existant** | Développement 2-3x plus rapide qu'un projet from scratch |
| 🎨 **Visuels fournis** | Zéro temps de design (icônes, logos, captures déjà créés) |
| 📱 **Nature simple : clavier** | Interface basique et standardisée (pas d'UX complexe) |
| 💚 **Open Source** | Visibilité publique pour portfolio développeur |
| 🌍 **Impact culturel** | Préservation langue créole (UNESCO) |
| 🎯 **Niche** | Peu de concurrence sur ce segment |
| 📊 **Risque faible** | Fonctionnalités déjà validées sur Android |

### 📞 Points de Contact

- **Repo GitHub** : [github.com/famibelle/KreyolKeyb](https://github.com/famibelle/KreyolKeyb)
- **Branche iOS** : `feature/ios-keyboard`
- **Code source Android** : `android_keyboard/app/src/main/`
- **Assets réutilisables** : `android_keyboard/app/src/main/assets/*.json`

---

## 🎯 Détails Techniques (Pour Développeur iOS)

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

### 💰 Modèle Économique
- **Application 100% gratuite** (pas de freemium)
- **Open Source** (GitHub public)
- **Aucune publicité**
- **Financement** : Subventions culturelles & mécénat

### 🏆 Valeur Ajoutée pour le Développeur
- **Portfolio culturel** : Projet à impact social (préservation linguistique)
- **Visibilité** : Crédits dans l'app + GitHub (3K+ lignes de code)
- **Open Source** : Contribution publique reconnue
- **Techniquement challengeant** : Algorithmes NLP, optimisation performance
- **Référence** : Témoignage client pour futurs contrats

## 🚀 Estimation de Charge Détaillée

### Planning Prévisionnel (7 semaines)

| Phase | Durée | Détail | Compétence |
|-------|-------|--------|------------|
| **Setup & Architecture** | 3 jours | Xcode project, App Groups, imports assets | Mid-level |
| **Keyboard Extension** | 7 jours | UIInputViewController, layout touches, gestures | Senior |
| **Moteur Suggestions** | 7 jours | Levenshtein, Trie, N-grams, scoring | Senior |
| **Dictionnaires & Accents** | 4 jours | JSON parsing, accent handling, case preservation | Mid-level |
| **UI/UX Configuration** | 5 jours | Onboarding, settings, dark mode, haptics | Mid-level |
| **Gamification** | 4 jours | Tracking vocabulaire, statistiques, niveaux | Mid-level |
| **Testing & QA** | 5 jours | XCTest, XCUITest, Instruments, optimisation | Senior |
| **App Store Prep** | 2 jours | Captures, descriptions, privacy policy, review | Mid-level |
| **TOTAL** | **37 jours** | **~7-8 semaines temps plein** | **Mix Senior/Mid** |

### Répartition Budget Indicatif

| Profil | TJM | Jours | Total |
|--------|-----|-------|-------|
| **iOS Senior** | 600-700€ | 19 jours | 11 400€ - 13 300€ |
| **iOS Mid-level** | 400-500€ | 18 jours | 7 200€ - 9 000€ |
| **TOTAL HT** | - | 37 jours | **18 600€ - 22 300€** |

_Marge de sécurité recommandée : +15% (imprévus) = **21 390€ - 25 645€ TTC**_

### Jalons de Validation (Milestones)

| Semaine | Livrable | Critère d'acceptation |
|---------|----------|----------------------|
| **S2** | Keyboard Extension v0.1 | Saisie alphabétique basique fonctionnelle |
| **S4** | Suggestions intelligentes | Top 5 suggestions en temps réel < 50ms |
| **S5** | UI complète | Onboarding + stats navigables, dark mode OK |
| **S6** | Beta TestFlight | 10 beta-testeurs valident stabilité |
| **S7** | App Store Ready | Package complet prêt à soumettre |

---

## 📋 Checklist Recrutement Développeur

### Questions à Poser en Entretien

#### Technique - Custom Keyboard (CRITIQUE)
- [ ] "Avez-vous déjà développé une Custom Keyboard Extension iOS ?"
  - ⚠️ **Red flag** si "Non" → Formation nécessaire (risque délais)
  - ✅ **Green flag** si "Oui + app publiée" → Demander lien App Store
- [ ] "Quelles sont les principales limitations des keyboard extensions ?"
  - Réponse attendue : Mémoire 48MB, pas de réseau sans Full Access, cycle de vie
- [ ] "Comment partagez-vous des données entre l'app et l'extension ?"
  - Réponse attendue : App Groups avec UserDefaults/FileManager

#### Algorithmes - NLP (IMPORTANT)
- [ ] "Expliquez la distance de Levenshtein et son utilité."
  - Réponse attendue : Mesure similarité entre chaînes, correction typos
- [ ] "Quelle structure de données utiliseriez-vous pour recherche par préfixe ?"
  - Réponse attendue : Trie (arbre préfixe) pour O(k) au lieu de O(n)

#### Performance (IMPORTANT)
- [ ] "Comment optimiser la mémoire d'une extension limitée à 48MB ?"
  - Réponse attendue : Lazy loading, compression, cache intelligent
- [ ] "Outils de profiling iOS que vous maîtrisez ?"
  - Réponse attendue : Instruments (Time Profiler, Allocations, Leaks)

#### Process (BON À SAVOIR)
- [ ] "Expérience avec CI/CD iOS ?"
  - Bonus : GitHub Actions, Fastlane, TestFlight automation
- [ ] "Démarche de publication App Store ?"
  - Réponse attendue : Connaît le process de review, gestion des rejets

### Exercice Technique (1h max)

**Énoncé** : Implémenter en Swift une fonction qui trouve les 3 mots les plus proches d'un input donné dans un dictionnaire de 1000 mots, en utilisant la distance de Levenshtein. Optimiser pour < 50ms.

**Critères d'évaluation** :
- ✅ Algorithme correct (DP Levenshtein)
- ✅ Pré-filtrage par longueur de mot
- ✅ Early exit si distance > seuil
- ✅ Code propre et testé
- ⭐ Bonus : Utilisation de Trie pour préfixe

### Vérification Références

- [ ] **Portfolio App Store** : Vérifier apps publiées (nombre, notes, complexité)
- [ ] **GitHub** : Code public disponible ? Qualité ? Activité récente ?
- [ ] **LinkedIn Recommandations** : Projets similaires validés ?
- [ ] **Ancien Client** : Contacter 1-2 références (délais respectés ?)

---

## 💼 Informations Contractuelles

### Format de Collaboration

| Option | Avantages | Inconvénients | Recommandé pour |
|--------|-----------|---------------|------------------|
| **Freelance** | Flexibilité, TJM négociable | Disponibilité partielle | Budget < 25K€ |
| **ESN/Agence** | Garantie de livraison, équipe | Coût +30%, moins d'implication | Budget > 30K€ |
| **CDD/CDI** | Engagement long terme | Coûts fixes élevés | Roadmap > 1 an |

**Recommandation** : **Freelance senior iOS** avec 50% de temps dédié = ~14 semaines calendaires

### Modalités de Paiement Suggérées

1. **30% à la signature** (7 000€) - Réservation développeur
2. **30% à mi-parcours** (7 000€) - Livraison keyboard fonctionnel (S4)
3. **30% en recette** (7 000€) - Beta TestFlight validée (S6)
4. **10% après publication** (2 300€) - App Store en ligne (S8)

**Total** : 23 300€ TTC (base moyenne)

### Clauses Importantes

- **Propriété intellectuelle** : Code sous licence open source (MIT/Apache 2.0)
- **Garantie** : 3 mois après livraison pour bugs critiques
- **Maintenance** : Option TMA 2j/an pour updates iOS majeures
- **Confidentialité** : NDA si données sensibles (ici : N/A, projet open source)
- **Pénalités** : -5% par semaine de retard (max 15%)

---

## 📞 Contacts & Ressources

### Informations Projet
- **Repo GitHub** : [github.com/famibelle/KreyolKeyb](https://github.com/famibelle/KreyolKeyb)
- **App Android** : [Play Store - Potomitan Kreyol Keyboard](https://play.google.com/store/apps/details?id=com.potomitan.kreyolkeyboard)
- **Utilisateurs actifs** : ~500 (Android), potentiel iOS : 1000+

### Ressources Techniques
- **Documentation Android** : `android_keyboard/README.md`
- **Dictionnaires** : `clavier_creole/assets/*.json` (prêts à l'emploi)
- **Algorithmes** : `LevenshteinDistance.kt`, `SuggestionEngine.kt` (à adapter)

### Point de Contact Commercial
- **Email** : [À DÉFINIR]
- **Disponibilité** : [À DÉFINIR]
- **Délai de réponse** : 48h max

---

## ❓ FAQ Commercial

**Q: Pourquoi 5-7 semaines alors que c'est "juste un clavier" ?**  
R: La complexité réside dans l'intelligence artificielle (N-grams, Levenshtein) et les contraintes iOS (mémoire limitée, optimisations). Un clavier "bête" = 2 semaines, un clavier intelligent = 7 semaines.

**Q: Peut-on réduire le budget en prenant un junior ?**  
R: ⚠️ Risqué. Custom Keyboard Extension est un domaine très spécifique. Un junior mettra 2x plus de temps et risque des bugs de performance. Économie illusoire.

**Q: Faut-il un Mac pour développer iOS ?**  
R: Oui, obligatoire. Xcode ne fonctionne que sur macOS. Le développeur doit avoir un Mac (ou location cloud type MacStadium).

**Q: L'app sera-t-elle rejetée par Apple ?**  
R: Risque faible si bonnes pratiques respectées :
  - ✅ Fonctionnement offline (pas de collecte de données)
  - ✅ Privacy policy claire
  - ✅ Pas de crash ni de ralentissement système
  - ⚠️ Délai review Apple : 24h à 7 jours (imprévisible)

**Q: Compatibilité avec quelle version iOS ?**  
R: iOS 14+ (couvre 95% des iPhones). Testé sur iPhone SE 2020 à iPhone 15 Pro Max.

**Q: Peut-on réutiliser du code Android ?**  
R: Logique métier uniquement (algorithmes). L'UI iOS est 100% à réécrire (langages différents : Kotlin ≠ Swift).

**Q: Maintenance après livraison ?**  
R: 2-3 jours/an recommandés :
  - Updates iOS majeures (ex: iOS 18 → iOS 19)
  - Nouveaux modèles iPhone (tailles d'écran)
  - Bugs remontés par utilisateurs

---

## 🎯 Critères de Succès du Projet

| Critère | Objectif | Mesure |
|---------|----------|--------|
| **Performance** | Suggestions < 50ms | Instruments Time Profiler |
| **Stabilité** | 0 crash sur 1000 frappes | Crashlytics/Sentry |
| **Adoption** | 500 DL dans 3 mois | App Store Analytics |
| **Notes** | Moyenne ≥ 4.5/5 | App Store reviews |
| **Délai** | Livraison en S7 | Planning respecté |
| **Budget** | ± 10% de l'estimé | Facturation finale |

---

## 🏆 Pourquoi ce Projet est Intéressant pour un Dev iOS

1. **Portfolio premium** : Projet culturel avec impact réel (préservation linguistique)
2. **Complexité technique** : NLP, algorithmes, optimisation - parfait pour senior
3. **Open Source** : Visibilité GitHub, contribution reconnue
4. **Technos modernes** : Swift 5.9, SwiftUI, Combine, async/await
5. **Autonomie** : Pas de product owner tatillon, specs claires
6. **Reconnaissance** : Crédits dans l'app, témoignage client, cas d'étude

---

**Document rédigé le** : 29 novembre 2025  
**Version** : 2.0 (orientée commercial)  
**Validité de l'estimation** : 3 mois (jusqu'au 28 février 2026)

---

*An kreyòl sou iOS, sé pou démen ! 🇬🇵📱*

---

# 🔧 ANNEXE TECHNIQUE (pour développeurs)

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
