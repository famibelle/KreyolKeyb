# Configuration Xcode - Klavyé Kréyòl Karukera iOS

## Prérequis
- macOS 12.0 ou supérieur
- Xcode 14.0 ou supérieur
- Compte développeur Apple (pour tester sur appareil)

## Étape 1: Créer le Projet Xcode

1. **Ouvrir Xcode** et créer un nouveau projet:
   - File > New > Project
   - Sélectionner **iOS** > **App**
   - Cliquer sur **Next**

2. **Configuration du projet principal**:
   - **Product Name**: `KreyolKeyboard`
   - **Team**: Sélectionner votre équipe de développement
   - **Organization Identifier**: `com.potomitan`
   - **Bundle Identifier**: `com.potomitan.kreyolkeyboard`
   - **Interface**: SwiftUI
   - **Language**: Swift
   - **Minimum Deployments**: iOS 14.0

3. **Sauvegarder** dans le dossier `ios_keyboard/`

## Étape 2: Ajouter l'Extension Clavier

1. **File** > **New** > **Target**
2. Sélectionner **Custom Keyboard Extension**
3. Configuration:
   - **Product Name**: `KeyboardExtension`
   - **Team**: Même que l'app principale
   - **Language**: Swift
   - **Bundle Identifier**: `com.potomitan.kreyolkeyboard.keyboard`
   - Cliquer sur **Finish**
   - Quand demandé d'activer le scheme, cliquer sur **Activate**

## Étape 3: Configurer App Groups

### 3.1 Pour l'App Principale
1. Sélectionner le target **KreyolKeyboard**
2. Onglet **Signing & Capabilities**
3. Cliquer sur **+ Capability**
4. Ajouter **App Groups**
5. Cliquer sur **+** et créer: `group.com.potomitan.kreyolkeyboard`
6. Cocher la case du groupe créé

### 3.2 Pour l'Extension Clavier
1. Sélectionner le target **KeyboardExtension**
2. Onglet **Signing & Capabilities**
3. Cliquer sur **+ Capability**
4. Ajouter **App Groups**
5. Sélectionner le même groupe: `group.com.potomitan.kreyolkeyboard`

## Étape 4: Organiser les Fichiers

### 4.1 Supprimer les fichiers par défaut
- Supprimer `ContentView.swift` généré par Xcode (on a le nôtre)
- Supprimer `KeyboardViewController.swift` dans l'extension (on a le nôtre)

### 4.2 Ajouter nos fichiers au projet

#### App Principale (Target: KreyolKeyboard)
Glisser-déposer dans Xcode et cocher "Copy items if needed" + Target "KreyolKeyboard":
- `KreyolKeyboard/KreyolKeyboardApp.swift`
- `KreyolKeyboard/Views/ContentView.swift`
- `KreyolKeyboard/Views/OnboardingView.swift`
- `KreyolKeyboard/Views/StatsView.swift`
- `KreyolKeyboard/Views/AboutView.swift`
- `KreyolKeyboard/Info.plist`

#### Extension Clavier (Target: KeyboardExtension)
Glisser-déposer et cocher Target "KeyboardExtension":
- `KeyboardExtension/KeyboardViewController.swift`
- `KeyboardExtension/Views/KeyboardView.swift`
- `KeyboardExtension/Views/SuggestionBar.swift`
- `KeyboardExtension/Views/AccentPopupView.swift`
- `KeyboardExtension/Info.plist`

#### Ressources (Target: KeyboardExtension)
- `KeyboardExtension/Resources/creole_dict.json`
- `KeyboardExtension/Resources/creole_ngrams.json`

#### Shared (Targets: BOTH KreyolKeyboard + KeyboardExtension)
⚠️ **Important**: Cocher les DEUX targets lors de l'ajout:
- `Shared/Models/Word.swift`
- `Shared/Models/CreoleDictionary.swift`
- `Shared/Managers/VocabularyTracker.swift`
- `Shared/Managers/UserDefaultsManager.swift`

## Étape 5: Configurer les Assets

1. **Créer le Color Asset "PotomitanRed"**:
   - Ouvrir `Assets.xcassets`
   - Clic droit > New Color Set
   - Nommer: `PotomitanRed`
   - Sélectionner Universal > Any Appearance
   - Définir la couleur: 
     - Hex: `#D94539`
     - RGB: R:217, G:69, B:57
     - ou RGB décimal: R:0.85, G:0.27, B:0.23

2. **Ajouter l'icône de l'app** (optionnel pour MVP):
   - Ajouter des images PNG dans `Assets.xcassets/AppIcon`
   - Tailles requises: 20pt, 29pt, 40pt, 60pt, 76pt, 83.5pt (@2x et @3x)

## Étape 6: Configurer Build Settings

### Pour KeyboardExtension Target:
1. Sélectionner target **KeyboardExtension**
2. Onglet **Build Settings**
3. Rechercher "Allow App Extension API Only"
4. Définir à **YES**

### Pour les deux targets:
1. Vérifier **iOS Deployment Target** = 14.0 minimum
2. Vérifier **Swift Language Version** = Swift 5

## Étape 7: Vérifier Info.plist

### App Principale (KreyolKeyboard/Info.plist)
Remplacer le contenu par notre fichier `Info.plist` créé.

### Extension (KeyboardExtension/Info.plist)
Remplacer par notre fichier avec:
- `RequestsOpenAccess` = YES (pour suggestions)
- `PrimaryLanguage` = fr-GP (créole guadeloupéen)
- `IsASCIICapable` = NO

## Étape 8: Build et Test

### 8.1 Build
1. Sélectionner le scheme **KreyolKeyboard**
2. Choisir une destination (Simulateur iOS ou appareil)
3. Cmd + B pour build

### 8.2 Résoudre les erreurs courantes
- **"No such module"**: Vérifier que les fichiers Shared ont les deux targets cochés
- **"Cannot find 'CreoleDictionary' in scope"**: Build Settings > Import Paths
- **Assets manquants**: Ajouter la couleur PotomitanRed dans Assets.xcassets

### 8.3 Exécuter sur Simulateur
1. Cmd + R pour lancer l'app
2. Dans l'app, cliquer sur "Ouvrir les Réglages"
3. Aller dans: Réglages > Général > Clavier > Claviers
4. Cliquer "Ajouter un clavier..."
5. Sélectionner "Klavyé Kréyòl"
6. Activer "Autoriser l'accès complet"
7. Ouvrir Notes ou Messages et tester le clavier (🌐 pour basculer)

### 8.4 Test sur Appareil Réel
1. Brancher l'iPhone/iPad
2. Sélectionner l'appareil dans Xcode
3. Dans **Signing & Capabilities**, sélectionner votre Team
4. Lancer l'app (Cmd + R)
5. Sur l'appareil: **Réglages** > **Général** > **VPN et gestion de l'appareil**
6. Approuver le certificat de développement
7. Suivre les étapes 8.3 pour activer le clavier

## Étape 9: Debugging

### Logs de l'Extension
1. Dans Xcode: **Window** > **Devices and Simulators**
2. Sélectionner votre appareil
3. Cliquer sur **Open Console**
4. Filtrer par "Klavyé" pour voir les logs

### Breakpoints
1. Ouvrir `KeyboardViewController.swift`
2. Mettre un breakpoint dans `keyboardView(_:didTapKey:)`
3. Déboguer avec le scheme **KeyboardExtension**
4. Dans la popup, sélectionner "Messages" ou "Notes"

## Structure Finale du Projet

```
ios_keyboard.xcodeproj
├── KreyolKeyboard/
│   ├── KreyolKeyboardApp.swift
│   ├── Views/
│   │   ├── ContentView.swift
│   │   ├── OnboardingView.swift
│   │   ├── StatsView.swift
│   │   └── AboutView.swift
│   ├── Info.plist
│   └── Assets.xcassets
├── KeyboardExtension/
│   ├── KeyboardViewController.swift
│   ├── Views/
│   │   ├── KeyboardView.swift
│   │   ├── SuggestionBar.swift
│   │   └── AccentPopupView.swift
│   ├── Resources/
│   │   ├── creole_dict.json
│   │   └── creole_ngrams.json
│   └── Info.plist
└── Shared/ (linked to both targets)
    ├── Models/
    │   ├── Word.swift
    │   └── CreoleDictionary.swift
    └── Managers/
        ├── VocabularyTracker.swift
        └── UserDefaultsManager.swift
```

## Problèmes Connus

### 1. Dictionnaire ne charge pas
**Cause**: Fichier JSON non ajouté au target KeyboardExtension
**Solution**: Sélectionner `creole_dict.json` dans le navigateur de fichiers, puis dans File Inspector cocher "Target Membership" > KeyboardExtension

### 2. Statistiques ne persistent pas
**Cause**: App Group non configuré
**Solution**: Vérifier que les deux targets ont le même App Group ID activé

### 3. Clavier ne s'affiche pas
**Cause**: Extension non signée correctement
**Solution**: Vérifier Signing & Capabilities pour KeyboardExtension

## Prochaines Étapes

Une fois le projet configuré et fonctionnel:
1. ✅ Tester toutes les fonctionnalités de base
2. ✅ Vérifier les accents (è, ò, ö)
3. ✅ Valider les suggestions
4. ✅ Tester les statistiques
5. 📱 Préparer pour TestFlight (Phase 2)

## Support

Pour toute question sur la configuration Xcode:
- 📧 contact@potomitan.com
- 📖 Documentation officielle Apple: [Custom Keyboard Extension](https://developer.apple.com/documentation/uikit/keyboards_and_input)
