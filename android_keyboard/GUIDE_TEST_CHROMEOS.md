# 🧪 Guide de Test ChromeOS - Klavyé Kreyòl

## Vue d'ensemble

Ce guide explique comment tester le clavier Kreyòl sur ChromeOS (ou émulateur ChromeOS).

## 📋 Prérequis

### Option 1 : Chromebook réel
- Un Chromebook avec ChromeOS version 80+ (2020 ou plus récent)
- Mode développeur activé (optionnel, pour sideloading)
- Accès au Play Store (activé par défaut sur la plupart des Chromebooks récents)

### Option 2 : Émulateur ChromeOS
```bash
# Utiliser Android Studio avec AVD Manager
# Créer un appareil de type "ChromeOS device"
# Ou utiliser l'image system officielle : chromeos_*
```

## 🚀 Installation

### Via ADB (mode développement)

1. **Activer le mode développeur sur ChromeOS** :
   ```
   Esc + Refresh + Power (au démarrage)
   Ctrl + D pour confirmer
   ATTENTION : Efface toutes les données !
   ```

2. **Activer le debugging** :
   ```
   Paramètres → Linux (Beta) → Développer des applications Android
   Activer "ADB debugging"
   ```

3. **Installer le clavier** :
   ```powershell
   # Compiler et installer
   .\gradlew assembleDebug
   adb install -r app\build\outputs\apk\debug\*.apk
   ```

### Via Play Store (production)
- Publier sur Play Store
- Installer normalement depuis le Chromebook

## ✅ Tests à effectuer

### Test 1 : Installation et activation

**Étapes** :
1. Ouvrir **Paramètres** → **Langues et saisie**
2. Aller dans **Méthodes de saisie**
3. Activer "**Klavyé Kreyòl - Potomitan™**"
4. Définir comme clavier par défaut (optionnel)

**Résultat attendu** :
- ✅ Le clavier apparaît dans la liste
- ✅ Peut être activé sans erreur
- ✅ Icône visible dans la barre d'état

### Test 2 : Basculement entre claviers

**Étapes** :
1. Ouvrir une application de texte (Google Docs, Gmail)
2. Cliquer dans un champ de saisie
3. Utiliser `Ctrl + Espace` ou l'icône de clavier

**Résultat attendu** :
- ✅ Le menu de sélection de clavier apparaît
- ✅ "Klavyé Kreyòl" est dans la liste
- ✅ Basculement fluide

### Test 3 : Frappe de base

**Étapes** :
1. Activer le clavier Kreyòl
2. Taper : "Bonjou mwen renmen pale kreyòl"

**Résultat attendu** :
- ✅ Toutes les lettres s'affichent correctement
- ✅ Pas de latence perceptible
- ✅ Caractères corrects

### Test 4 : Accents créoles

**Étapes** :
1. Taper "e" puis appuyer sur la touche d'accent → "è"
2. Taper "o" puis appuyer sur la touche d'accent → "ò"
3. Tester : an, en, on, ou

**Résultat attendu** :
- ✅ Accents graves fonctionnent (è, ò)
- ✅ Digrammes fonctionnent (an, en, on, ou)
- ✅ Pas de doublon de caractères

### Test 5 : Suggestions intelligentes

**Étapes** :
1. Taper "Bon" (3 lettres minimum)
2. Observer la barre de suggestions

**Résultat attendu** :
- ✅ 3 suggestions apparaissent
- ✅ Suggestions pertinentes : "bon", "bonjou", "bonnè"
- ✅ Cliquer sur une suggestion l'insère

### Test 6 : Mode multi-fenêtres

**Étapes** :
1. Ouvrir Google Docs
2. Ouvrir Gmail en fenêtre séparée (Alt + [ ou drag)
3. Taper alternativement dans les deux fenêtres

**Résultat attendu** :
- ✅ Le clavier fonctionne dans les deux fenêtres
- ✅ Pas de crash lors du basculement
- ✅ Suggestions persistent

### Test 7 : Écran large / Redimensionnement

**Étapes** :
1. Ouvrir une application en plein écran
2. Redimensionner la fenêtre (drag depuis les coins)
3. Tester en mode portrait et paysage

**Résultat attendu** :
- ✅ Layout s'adapte à la taille
- ✅ Touches restent cliquables
- ✅ Pas de débordement visuel

### Test 8 : Clavier physique + virtuel

**Étapes** :
1. Taper avec le clavier physique du Chromebook
2. Afficher le clavier virtuel
3. Utiliser les deux simultanément

**Résultat attendu** :
- ✅ Les deux claviers coexistent
- ✅ Pas de conflit d'entrée
- ✅ Accents accessibles depuis les deux

### Test 9 : Applications ChromeOS natives

Tester dans :
- ✅ Google Docs
- ✅ Gmail
- ✅ Google Sheets
- ✅ Chrome browser (formulaires web)
- ✅ Android Messages
- ✅ Keep Notes

**Résultat attendu** :
- ✅ Fonctionne partout
- ✅ Suggestions adaptées au contexte

### Test 10 : Performance

**Métrique** :
- Taper rapidement pendant 30 secondes
- Observer l'utilisation CPU/RAM
- Vérifier la température

**Résultat attendu** :
- ✅ CPU < 10% (frappe normale)
- ✅ RAM < 100 MB
- ✅ Pas de ralentissement
- ✅ Pas de surchauffe

## 🐛 Problèmes connus et solutions

### Problème : "App incompatible"

**Cause** : Architecture CPU non supportée

**Solution** :
```gradle
// Dans app/build.gradle, vérifier :
ndk {
    abiFilters 'armeabi-v7a', 'arm64-v8a', 'x86', 'x86_64'
}
```

### Problème : Clavier ne s'affiche pas

**Causes possibles** :
1. Pas activé dans les paramètres
2. Conflit avec autre IME
3. Permissions manquantes

**Solution** :
```bash
# Forcer l'arrêt et relancer
adb shell am force-stop com.potomitan.kreyolkeyboard
adb shell am start -n com.potomitan.kreyolkeyboard/.SettingsActivity
```

### Problème : Suggestions vides

**Cause** : Dictionnaire non chargé

**Solution** :
```kotlin
// Vérifier les logs
adb logcat -s SuggestionEngine:D | Select-String "dictionnaire"
```

## 📊 Checklist de compatibilité

| Fonctionnalité | ChromeOS | Android | Notes |
|----------------|----------|---------|-------|
| Frappe de base | ✅ | ✅ | |
| Accents | ✅ | ✅ | |
| Suggestions | ✅ | ✅ | |
| Multi-fenêtres | ✅ | ⚠️ | Android 7+ |
| Redimensionnement | ✅ | ⚠️ | ChromeOS spécifique |
| Clavier physique | ✅ | ⚠️ | ChromeOS prioritaire |
| x86/x86_64 | ✅ | ❌ | ChromeOS uniquement |

## 🔍 Logs de débogage

### Voir les logs en temps réel
```powershell
adb logcat -s KreyolIME-Potomitan™:D SuggestionEngine:D
```

### Vérifier l'architecture
```bash
adb shell getprop ro.product.cpu.abi
# Devrait retourner : x86_64 ou arm64-v8a
```

### Tester les performances
```bash
adb shell dumpsys input_method
```

## 📝 Rapport de test

Après les tests, documenter :

- ✅ **Version ChromeOS** : _____________
- ✅ **Modèle Chromebook** : _____________
- ✅ **Architecture CPU** : _____________
- ✅ **Tests réussis** : ____/10
- ✅ **Problèmes rencontrés** : _____________

## 🎯 Critères de validation

Pour considérer ChromeOS comme "supporté" :

- ✅ 10/10 tests passent
- ✅ Pas de crash en utilisation normale
- ✅ Performance acceptable (< 100ms latence)
- ✅ Compatible avec ≥ 90% des applications

---

**Version** : 5.2.3  
**Dernière mise à jour** : Octobre 2025
