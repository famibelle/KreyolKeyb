# 🚀 Guide de Release v2.5.0 - Potomitan Kreyol Keyboard

## 📋 Vue d'ensemble

Ce document explique comment utiliser les GitHub Actions pour créer et publier la version 2.5.0 du clavier Potomitan Kreyol.

## 🔄 Workflows disponibles

### 1. 🧪 Test Build v2.5.0
**Fichier**: `.github/workflows/test-build-v2.5.0.yml`
**Déclenchement**: Automatique sur push/PR, ou manuel
**Objectif**: Tester la compilation avant la release officielle

### 2. 🏷️ Create Tag v2.5.0  
**Fichier**: `.github/workflows/create-tag-v2.5.0.yml`
**Déclenchement**: Manuel uniquement
**Objectif**: Créer le tag v2.5.0 qui déclenche automatiquement la release

### 3. 🚀 Release v2.5.0
**Fichier**: `.github/workflows/release-v2.5.0.yml`
**Déclenchement**: Automatique sur tag v2.5.0, ou manuel forcé
**Objectif**: Construire les APKs et créer la release GitHub

## 📱 Process de Release

### Étape 1: Préparation
1. ✅ Vérifiez que `android_keyboard/app/build.gradle` contient :
   ```gradle
   versionCode = 6
   versionName "2.5.0"
   ```

2. ✅ Commitez et pushez tous les changements

### Étape 2: Test Build
1. Allez dans **Actions** → **🧪 Test Build v2.5.0**
2. Cliquez **Run workflow**
3. Attendez la compilation (3-5 min)
4. Vérifiez que les APKs sont générés correctement

### Étape 3: Création du Tag et Release
1. Allez dans **Actions** → **🏷️ Create Tag v2.5.0**
2. Cliquez **Run workflow**
3. Le workflow va :
   - Vérifier la version dans build.gradle
   - Créer le tag v2.5.0
   - Déclencher automatiquement la release

### Étape 4: Vérification Release
1. La release se déclenche automatiquement
2. Allez dans **Releases** après 5-10 minutes
3. Vérifiez que la release **v2.5.0** est créée avec les APKs

## 📦 Artifacts générés

### Debug APK
- **Nom**: `Potomitan_Kreyol_Keyboard_v2.5.0_DEBUG_YYYY-MM-DD.apk`
- **Taille**: ~3.4 MB
- **Usage**: Installation facile, tests

### Release APK
- **Nom**: `Potomitan_Kreyol_Keyboard_v2.5.0_RELEASE_YYYY-MM-DD.apk`  
- **Taille**: ~2.6 MB
- **Usage**: Distribution production

## 🛠️ Caractéristiques techniques v2.5.0

- **ApplicationId**: `com.potomitan.kreyolkeyboard`
- **Version Code**: 6
- **Version Name**: 2.5.0
- **Target SDK**: 33 (Android 13)
- **Min SDK**: 21 (Android 5.0)
- **Architectures**: arm64-v8a, armeabi-v7a
- **Signature**: Debug (pour faciliter l'installation)

## 🎯 Nouveautés v2.5.0

- 🔧 **Amélioration majeure système Shift**: Diagnostics complets pour résoudre affichage majuscules
- 🐛 **Correction barre espace**: Résolution problème "espace" vs caractère espace  
- 📊 **Logs débogage étendus**: Système diagnostic complet pour identifier problèmes
- 🔄 **Optimisation mode clavier**: Amélioration détection mode alphabétique/numérique
- 🎨 **Stabilité interface**: Corrections diverses pour expérience utilisateur plus fluide

## 🚨 Troubleshooting

### Erreur "Version mismatch"
```bash
❌ Version mismatch! Expected 2.5.0, got X.X.X
```
**Solution**: Mettez à jour `versionName` dans `android_keyboard/app/build.gradle`

### Tag déjà existant
Le workflow supprime automatiquement l'ancien tag v2.5.0 s'il existe.

### Build échoué
1. Vérifiez les logs dans l'onglet Actions
2. Assurez-vous que les dépendances Gradle sont à jour
3. Relancez le workflow après correction

## 📞 Support

- **Repo**: https://github.com/famibelle/KreyolKeyb
- **Issues**: https://github.com/famibelle/KreyolKeyb/issues
- **Développeur**: @famibelle

---
**Alé douvan épi klavié kreyòl-la !** ⌨️✨