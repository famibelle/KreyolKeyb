# 📝 Changelog Klavyé Kréyòl

## 🎨 Version 6.1.9 (2025-10-26) - MODERNISATION UI & MATERIAL DESIGN

### ✨ Nouvelles Fonctionnalités
- **🎨 Icônes Material Design** : Remplacement des symboles Unicode par des vector drawables pour les touches spéciales
  - ⌫ Backspace : Icône animée avec dégradé
  - ⏎ Enter : Icône moderne avec flèche de retour
  - ⇧ Shift : Triple état (off/on/caps) avec icônes distinctes
  - Padding optimisé : 8dp (Enter), 10dp (Backspace), 12dp (Shift)
  
- **🎨 Schéma de couleurs épuré** : Migration vers tons neutres blanc/gris
  - Touches spéciales (⌫, ⏎, ⇧, virgule, point) : Blanc/gris neutre
  - Touches 123, ⌫, ⏎ : Blanc semi-transparent (#CCFFFFFF) pour effet moderne
  - Touches accentuées (é, è, ò) : Style cohérent avec reste du clavier
  - Meilleure lisibilité et esthétique professionnelle

- **📊 Interface statistiques améliorée** : Section "Mots à Découvrir" agrandie et repositionnée
  - Taille augmentée de 1.5x pour meilleure visibilité
    - Titre : 16f → 24f
    - Hauteur conteneur : 300px → 450px  
    - Taille chips : 13f → 19.5f
    - Padding chips : (10,5) → (15,7)
  - Repositionnement au-dessus de "Mots les plus utilisés" pour visibilité accrue
  - Optimisation espacement inter-sections (40dp au lieu de 64dp)

### 🔧 Corrections Bugs
- **⇧ Fix touche Shift** : La première pression ne bascule plus vers le mode numérique
  - Ajout de la méthode `isNumericMode()` pour lecture d'état sans modification
  - Correction de la logique dans `onModeChanged()` pour éviter basculements non désirés
  - Synchronisation parfaite entre `InputProcessor` et `KeyboardLayoutManager`

- **🔤 Support majuscules accents** : Les caractères accentués s'affichent correctement en majuscule
  - Clavier principal : é/è/ò → É/È/Ò en mode majuscule
  - Popups d'accents : tous les accents respectent maintenant le mode majuscule/minuscule
  - Synchronisation de `isCapitalMode` entre `KeyboardLayoutManager` et `AccentHandler`

### 🏗️ Refactoring Technique
- **Type System** : Migration de `TextView` vers `View` dans toute la codebase
  - Support simultané de `Button` (texte) et `ImageButton` (icônes)
  - 40+ signatures de méthodes mises à jour dans `AccentHandler` et `KeyboardLayoutManager`
  - Architecture flexible pour futures extensions UI

- **Assets Vector Drawables** : Création de 5 nouveaux fichiers d'icônes
  - `ic_backspace.xml` : Icône Backspace avec dégradé personnalisé
  - `ic_keyboard_return.xml` : Icône Enter style Material
  - `ic_shift_off.xml` : État normal du Shift
  - `ic_shift_on.xml` : État majuscule temporaire
  - `ic_shift_caps.xml` : État Caps Lock permanent

### 📦 Détails Techniques
- Fichiers modifiés : 
  - `KeyboardLayoutManager.kt` : Support ImageButton, gestion icônes, nouveau schéma couleurs
  - `AccentHandler.kt` : Support majuscules, migration View hierarchy
  - `KreyolInputMethodServiceRefactored.kt` : Synchronisation états majuscules
  - `SettingsActivity.kt` : Agrandissement section "Mots à Découvrir", réorganisation
  - 5 nouveaux vector drawables dans `res/drawable/`

### ✅ Tests et Validation
- ✅ Icônes Material Design affichées correctement sur tous les appareils
- ✅ Schéma de couleurs neutre cohérent et professionnel
- ✅ Majuscules fonctionnelles pour é/è/ò en clavier et popups
- ✅ Touche Shift ne bascule plus en mode numérique au premier appui
- ✅ Section "Mots à Découvrir" 1.5x plus grande et mieux positionnée
- ✅ Espacement optimisé entre sections statistiques

---

## 🐛 Version 6.1.8 (2025-10-21) - FIX ENCODAGE ACCENTS

### 🔧 Corrections
- **🔤 Correction encodage UTF-8 des accents** : Réparation de la corruption des caractères accentués dans `AccentHandler.kt`
  - Appui long sur "e" affiche maintenant correctement : `é`, `è`, `ê`, `ë` (au lieu de caractères corrompus)
  - Correction de tous les mappings d'accents : a, e, i, o, u, n, c, s, z, l, y
  - Impact : Toutes les touches avec accents fonctionnent maintenant correctement

### 📦 Détails Techniques
- Fichier corrigé : `AccentHandler.kt` ligne 33
- Cause : Encodage UTF-8 corrompu lors d'une sauvegarde précédente
- Solution : Remplacement de tous les caractères corrompus par leurs équivalents UTF-8 corrects

---

## 🐛 Version 6.1.7 (2025-10-20) - FIX CRITIQUE TOUCHE ENTRÉE

### 🔧 Corrections Critiques
- **⏎ Correction touche ENTRÉE** : Le clavier ne se ferme plus lors de l'appui sur Entrée
  - Vérification du flag `IME_FLAG_NO_ENTER_ACTION` (0x40000000)
  - Détection des champs multilignes avec `TYPE_TEXT_FLAG_MULTI_LINE` (0x00020000)
  - Insertion correcte du caractère newline (`\n`) dans les champs multilignes
  - Prévention de la fermeture intempestive du clavier
  - Logs détaillés pour diagnostic futur

### 📦 Détails Techniques
- Fichier modifié : `InputProcessor.kt` - fonction `handleEnter()`
- Documentation ajoutée : `DIAGNOSTIC_TOUCHE_ENTREE.md`, `QUICK_FIX_ENTREE.md`
- Tests validés sur émulateur Pixel 6 avec WhatsApp, Notes, formulaires

---

## 🎮 Version 6.0.0 (2025-10-11) - ÉDITION GAMIFICATION MAJEURE

### 🎯 Nouvelles Fonctionnalités Majeures

#### Système de Gamification Complet
- **✨ Tracking vocabulaire temps réel** : Suivi automatique de l'usage de chaque mot du dictionnaire créole (7000+ mots)
- **📊 Statistiques intelligentes** : 
  - Compteur mots découverts (utilisés exactement 1 fois)
  - Total utilisations avec historique
  - Top 5 mots les plus utilisés
  - Listes découverts vs à découvrir
- **🏆 Système de niveaux créoles** : "Pipirit" → "Ti moun" → "Débrouya" → "An mitan" → "Kompè Lapen" → "Kompè Zamba" → "Potomitan"
- **🌅 Mot du jour** : Sélection quotidienne avec statistiques d'usage personnalisées
- **🔒 Respect vie privée** : Seuls les mots du dictionnaire créole sont trackés (ignore mots de passe, URLs, emails)

#### Interface Utilisateur Moderne  
- **📱 Migration ViewPager2** : Architecture Fragment avec navigation swipe horizontale fluide
- **🎨 Onglets repositionnés** : Passage vertical droite → horizontal haut pour optimiser l'espace écran
- **✨ Design Material** : Indicateurs orange, animations de transition, interface épurée
- **🎯 Ergonomie optimisée** : Réduction espace inutile, compatibilité clavier tactile améliorée

### 🔧 Améliorations Techniques Majeures

#### Architecture Optimisée Mémoire
- **⚡ Gestion ultra-minimale** : ConcurrentHashMap (capacité 16, load factor 0.75f, concurrence 1)
- **💾 Sauvegarde intelligente** : SAVE_BATCH_SIZE = 1 pour synchronisation temps réel
- **🚀 Streaming I/O** : BufferedReader 8KB, écriture atomique via fichiers temporaires
- **🔒 Thread safety** : Opérations merge concurrentes, locks synchronisés
- **📋 Format JSON dual** : Compatibilité {"mot": 1} et {"mot": {"frequency": X, "user_count": Y}}

#### Optimisations Samsung A21s
- **📱 Détection low-end devices** : `ActivityManager.isLowRamDevice` avec adaptations automatiques
- **📈 Monitoring mémoire** : Surveillance continue avec seuils adaptatifs pour éviter crashes
- **⚙️ Coroutines lifecycle** : `serviceScope` avec `SupervisorJob()` pour stabilité maximale
- **💾 Gestion fichiers robuste** : Recovery automatique, migration formats, écriture atomique

### 🐛 Corrections Bugs Critiques

#### Fixes Fonctionnels
- **🔧 Double counting** : Fix duplicate `wordCommitListener?.onWordCommitted()` dans InputProcessor.kt
- **🔄 Refresh functionality** : Intégration `forceSave()` avant `recreate()` pour synchronisation parfaite
- **🔤 Casse preservation** : Fix bug majuscules intentionnelles dans suggestions (applyCaseToSuggestion)
- **📊 Format compatibility** : Migration automatique entre formats JSON via `getWordDataSafe()`
- **🧹 Demo data elimination** : Suppression contamination données de démonstration

#### Améliorations Stabilité
- **💾 Atomic writes** : Prévention corruption fichiers via `.tmp` → `rename()`
- **🔄 Error handling** : Recovery automatique fichiers corrompus avec recréation
- **📊 Statistics sync** : Synchronisation temps réel garantie après chaque mot tapé
- **🎯 Memory leaks** : Élimination fuites mémoire dans cycle de vie fragments

### 🎨 Interface Utilisateur Raffinée

#### Gamification Visible
- **📊 Sections word lists** : Affichage organisé mots découverts/à découvrir avec scroll
- **📈 Statistiques visuelles** : Grille 3 colonnes (Découverts | Utilisations | Dictionnaire)
- **🔄 Actualisation simple** : Bouton "Actualiser" avec feedback Toast utilisateur
- **🗑️ Interface épurée** : Suppression bouton Reset pour interface plus clean

#### Feedback Utilisateur
- **💬 Toast messages** : Messages informatifs pour actions synchronisation
- **📊 Affichage adaptatif** : Gestion intelligente cas vides avec messages informatifs
- **🎯 Logs détaillés** : Système debugging complet avec PID filtering ADB
- **⚡ Performance UI** : Chargement rapide, transitions fluides, pas de lag

### 📁 Fichiers Modifiés

#### Code Source Principal
- `InputProcessor.kt` : Fix double counting, intégration WordCommitListener gamification
- `KreyolInputMethodServiceRefactored.kt` : Initialisation CreoleDictionaryWithUsage, monitoring mémoire
- `SettingsActivity.kt` : ViewPager2 + Fragments, statistiques complètes, interface épurée
- `CreoleDictionaryWithUsage.kt` : Système tracking complet, forceSave(), migration formats

#### Configuration Projet
- `build.gradle` : Version 6.0.0, versionCode 60000, optimisations build
- `INSTALLATION_V6.0.0.md` : Documentation complète nouvelle version
- `CHANGELOG.md` : Historique détaillé des changements

### ✅ Tests et Validation

#### Fonctionnalités Validées
- ✅ **Tracking temps réel** : 6 mots tapés → 6 utilisations confirmées en statistiques
- ✅ **Synchronisation stats** : Actualisation immédiate après forceSave() + recreate()
- ✅ **Interface swipe** : Navigation horizontale fluide Accueil ↔ Statistiques
- ✅ **Gestion mémoire** : < 16MB overhead confirmé sur Samsung A21s
- ✅ **Compatibilité formats** : Migration automatique JSON ancien → nouveau format
- ✅ **Respect vie privée** : Filtrage automatique mots sensibles (mots de passe, URLs)

#### Performance Confirmée
- ✅ **Samsung A21s** : Tests approfondis sur appareil low-end, monitoring mémoire actif
- ✅ **Thread safety** : Opérations concurrentes sans crash ni corruption données
- ✅ **Atomic I/O** : Pas de corruption fichiers même en cas d'interruption brutale
- ✅ **Recovery automatique** : Reconstruction fichiers corrompus sans perte données utilisateur

### 🎯 Impact Version 6.0.0

**Transformation Majeure** : Evolution d'un clavier créole basique vers un **système gamifié intelligent** avec :
- **Tracking vocabulaire** respectueux vie privée 
- **Interface moderne** Material Design
- **Architecture optimisée** pour appareils low-end
- **Gamification motivante** pour apprentissage créole
- **Performance garantie** sur Samsung A21s et équivalents

**Utilisateurs Cibles** : 
- Apprenants créole guadeloupéen cherchant progression mesurable
- Utilisateurs quotidiens souhaitant interface moderne et fluide  
- Possesseurs appareils low-end nécessitant optimisation mémoire
- Communauté créole valorisant patrimoine linguistique

---

## 📋 Versions Précédentes

### Version 5.3.4 (2025-10-09)
- Interface onglets verticaux
- Système suggestions basique
- Tracking manuel utilisateur
- Bugs double counting et refresh

### Version 5.3.1 (2025-09-28) 
- Optimisations Samsung A21s
- Correction crashes mémoire
- Amélioration suggestions créoles
- Tests performance automatisés

### Version 5.2.0 (2025-08-15)
- Support accents automatiques
- Dictionnaire créole étendu
- Corrections bugs capitalisation
- Interface utilisateur améliorée

---

**Klavyé Kréyòl** - À la mémoire de Saint-Ange Corneille Famibelle  
*Potomitan - Préservation du patrimoine linguistique créole guadeloupéen*