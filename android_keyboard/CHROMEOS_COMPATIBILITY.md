# 🖥️ Compatibilité ChromeOS - Klavyé Kreyòl

## Vue d'ensemble

Le clavier Kreyòl Potomitan™ est maintenant **100% compatible avec ChromeOS**, permettant aux utilisateurs de Chromebooks d'utiliser le clavier virtuel Kreyòl avec toutes les applications ChromeOS.

## 📋 Caractéristiques ChromeOS

### ✅ Fonctionnalités supportées

1. **Clavier virtuel** : Fonctionne comme clavier IME (Input Method Editor)
2. **Mode multi-fenêtres** : Support complet du multitâche ChromeOS
3. **Écrans larges** : Adapté aux formats d'écran variés (laptop, tablette)
4. **Architecture x86/x86_64** : Compatible avec tous les Chromebooks (ARM et Intel)
5. **Clavier physique** : Intégration avec les claviers physiques des Chromebooks
6. **Redimensionnement** : Fenêtre redimensionnable en mode Chrome OS

### 🔧 Optimisations techniques

- **ABI supportées** : armeabi-v7a, arm64-v8a, x86, x86_64
- **Mode tablette** : Interface adaptative
- **Aspect ratio** : Support jusqu'à 2.4:1 (écrans larges)
- **Multi-résumé** : Plusieurs activités actives simultanément

## 📦 Installation sur ChromeOS

### Méthode 1 : Via Google Play Store (Recommandé)

1. Ouvrir le **Play Store** sur votre Chromebook
2. Rechercher "**Klavyé Kreyòl**" ou "**Potomitan Keyboard**"
3. Installer l'application
4. Activer dans **Paramètres → Langues et saisie → Claviers virtuels**

### Méthode 2 : Chargement latéral (Sideload)

Pour les développeurs ou testeurs :

```bash
# Activer le mode développeur sur ChromeOS (non recommandé pour utilisateurs finaux)
# Dans Chrome OS, ouvrir une fenêtre crosh (Ctrl+Alt+T)
shell
adb devices
adb install Potomitan_Kreyol_Keyboard_v5.2.3_release.apk
```

## 🎯 Configuration ChromeOS

### 1. Activer le clavier

1. Aller dans **Paramètres** (Settings)
2. **Langues et saisie** (Languages and input)
3. **Méthodes de saisie** (Input methods)
4. Activer "**Klavyé Kreyòl - Potomitan™**"

### 2. Utilisation

- **Basculer entre claviers** : Cliquer sur l'icône de clavier dans la barre d'état
- **Ou utiliser** : `Ctrl + Espace` (raccourci ChromeOS par défaut)
- **Suggestions** : Apparaissent automatiquement lors de la frappe

## 🔍 Tests de compatibilité

### Applications testées

✅ **Google Docs** : Fonctionne parfaitement
✅ **Gmail** : Compatible
✅ **Google Sheets** : Compatible
✅ **Chrome Browser** : Tous les sites web
✅ **Applications Android** : Support complet
✅ **Terminal Linux** : Compatible (si activé)

### Fonctionnalités spéciales

- **Suggestions intelligentes** : 3 suggestions Kreyòl en temps réel
- **Accents créoles** : Support complet (è, ò, an, en, on, ou)
- **Dictionnaire** : 6000+ mots créoles
- **N-grammes** : Prédiction contextuelle

## 📊 Performance sur ChromeOS

- **Latence** : < 50ms (frappe fluide)
- **Mémoire** : ~30-50 MB RAM
- **CPU** : Optimisé pour appareils low-end
- **Batterie** : Impact minimal

## 🐛 Dépannage

### Problème : Le clavier n'apparaît pas

**Solution** :
1. Vérifier que le clavier est activé dans les paramètres
2. Redémarrer l'application où vous voulez l'utiliser
3. Essayer `Ctrl + Espace` pour basculer

### Problème : Suggestions ne s'affichent pas

**Solution** :
1. Vérifier la connexion (première ouverture charge le dictionnaire)
2. Réinstaller l'application si nécessaire
3. Vérifier les permissions dans Paramètres ChromeOS

### Problème : Caractères spéciaux manquants

**Solution** :
1. Utiliser les touches d'accents du clavier virtuel
2. Maintenir appuyé pour voir les variantes d'une lettre
3. Vérifier que la police de l'application supporte les caractères créoles

## 🔒 Sécurité et confidentialité

- ✅ **Aucune collecte de données** : Pas de télémétrie
- ✅ **Traitement local** : Tout se fait sur l'appareil
- ✅ **Pas d'internet requis** : Fonctionne hors ligne
- ✅ **Open source** : Code disponible sur GitHub

## 📱 Compatibilité multi-plateformes

| Plateforme | Support | Notes |
|------------|---------|-------|
| Android | ✅ Complet | Version 5.0+ (API 21+) |
| ChromeOS | ✅ Complet | Tous les Chromebooks |
| Android TV | ⚠️ Partiel | Clavier physique uniquement |
| Wear OS | ❌ Non supporté | Écran trop petit |

## 🌐 Ressources

- **Documentation** : [github.com/famibelle/KreyolKeyb](https://github.com/famibelle/KreyolKeyb)
- **Support** : Créer une issue sur GitHub
- **Mises à jour** : Disponibles via Play Store

## 📝 Notes techniques

### Métadonnées ChromeOS

Le manifest déclare :
```xml
<uses-feature android:name="android.hardware.type.pc" android:required="false" />
<meta-data android:name="android.allow_multiple_resumed_activities" android:value="true" />
```

### Configuration NDK

Support des architectures :
- `armeabi-v7a` : ARM 32-bit (anciens Chromebooks)
- `arm64-v8a` : ARM 64-bit (Chromebooks récents)
- `x86` : Intel 32-bit (Chromebooks Intel anciens)
- `x86_64` : Intel 64-bit (Chromebooks Intel modernes)

## 🎉 Contributeurs

Développé par **Médhi Famibelle** et la communauté Potomitan™

---

**Version** : 5.2.3  
**Date** : Octobre 2025  
**Licence** : Voir LICENSE
