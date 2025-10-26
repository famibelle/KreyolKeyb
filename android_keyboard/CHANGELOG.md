# Changelog

Toutes les modifications notables de ce projet seront documentées dans ce fichier.

Le format est basé sur [Keep a Changelog](https://keepachangelog.com/fr/1.0.0/),
et ce projet adhère au [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [6.2.0] - 2025-10-26

### 🎮 Gamification - Distribution Gaussienne

#### ✨ Nouveau
- **Système de niveaux dynamique** : Les seuils de niveaux s'adaptent automatiquement à la taille du dictionnaire
- **Distribution gaussienne** : Répartition mathématiquement correcte des niveaux basée sur une courbe normale
- **8 niveaux équilibrés** :
  - 🌍 Pipirit (< -3σ): ~0.15% - Les tout premiers pas (~4 mots)
  - 🌱 Ti moun (-3σ à -2σ): ~2% - Débutant (~57 mots)
  - 🔥 Débrouya (-2σ à -1σ): ~14% - Débutant avancé (~396 mots)
  - 💎 An mitan (-1σ à 0): ~34% - Intermédiaire (~963 mots)
  - 🐇 Kompè Lapen (0 à +1σ): ~34% - Avancé (~963 mots)
  - 🐘 Kompè Zamba (+1σ à +2σ): ~14% - Très avancé (~396 mots)
  - 👑 Potomitan (+2σ à +3σ): ~2% - Expert absolu (~57 mots)
  - 🧙🏿‍♀️ Benzo (+3σ): ~0.15% - Niveau secret - Tous les mots! (~4 mots)

#### 🔧 Amélioré
- **Cache du dictionnaire** : Comptage des mots mis en cache pour optimiser les performances
- **Calcul des seuils** : Basé sur une vraie distribution normale (μ = 50%, σ = 16.67%)
- **Adaptation automatique** : Si le dictionnaire évolue, les niveaux s'ajustent sans modification de code
- **Documentation enrichie** : Commentaires détaillés avec les pourcentages et approximations pour chaque niveau

#### 📊 Technique
- Nouvelle fonction `calculateGaussianThresholds()` : Calcule dynamiquement les 8 seuils (-3σ à +3σ)
- Nouvelle fonction `getTotalDictionaryWords()` : Récupère le nombre total de mots avec cache
- Modification de `getCurrentLevel()` : Utilise les seuils gaussiens au lieu de valeurs fixes
- Modification de `getNextLevelInfo()` : S'adapte aux seuils dynamiques
- Basé sur ~2833 mots actuellement dans le dictionnaire

### 🎨 Design

#### ✨ Nouveau
- **Page d'onboarding bêta-testeurs** : Nouvelle page `beta_onboarding.html` pour recruter des testeurs
  - Design cohérent avec `feedbacks_form.html`
  - Formulaire Formspree intégré
  - Switch FR/GCF (français par défaut)
  - Gradient rouge/violet thématique
  - Responsive mobile

#### 🔧 Amélioré
- **Switch de langue optimisé** : Taille réduite et positionné en bas à droite
- **Ergonomie** : Plus de superposition entre le titre et les contrôles
- **Accessibilité** : Checkbox de consentement clairement visible

### 🔐 Sécurité

#### 🔧 Corrigé
- **Rotation des mots de passe du keystore** : Changement des mots de passe après exposition accidentelle dans l'historique git
- **GitHub Secrets mis à jour** : STORE_PASSWORD, KEY_PASSWORD, KEYSTORE_BASE64 actualisés
- **Protection renforcée** : `.gitignore` mis à jour pour exclure `*keystore*base64*.txt`

#### 📝 Note de sécurité
- Le certificat de signature reste identique (aucun impact sur Google Play)
- Les anciens mots de passe exposés sont désormais inutilisables
- Historique git contient encore les traces (nettoyage optionnel disponible)

## [6.1.7] - 2025-10-20

### 🐛 Corrigé
- **Touche ENTRÉE** : Résolution du problème critique où la touche ENTRÉE fermait le clavier et provoquait une perte de focus
  - Respect du flag `IME_FLAG_NO_ENTER_ACTION` : Le clavier détecte maintenant quand une application souhaite que ENTRÉE insère une nouvelle ligne plutôt que d'exécuter une action
  - Détection des champs multilignes : Amélioration de la détection des champs de texte multiligne pour insérer correctement les nouvelles lignes
  - Fix validé sur l'application Potomitan et autres applications utilisant des champs multilignes
  - Plus de fermeture intempestive du clavier
  - Plus de perte de focus sur le champ de texte
  - Plus de redirection vers d'autres applications

### 📝 Technique
- Modification de `handleEnter()` dans `InputProcessor.kt` :
  - Vérification du flag `IME_FLAG_NO_ENTER_ACTION` avant d'exécuter les actions IME
  - Détection du flag `TYPE_TEXT_FLAG_MULTI_LINE` pour les champs multilignes
  - Logs détaillés pour faciliter le diagnostic futur
- Documentation complète :
  - `DIAGNOSTIC_TOUCHE_ENTREE.md` : Analyse des causes racines
  - `QUICK_FIX_ENTREE.md` : Documentation de l'implémentation
  - `tests/diagnostic-enter-key.ps1` : Script de diagnostic
  - `tests/reports/quick-fix-enter-test-report.md` : Rapport de validation

## [1.2.0] - 2025-09-07

### 🎉 Ajouté
- **Dictionnaire enrichi** : 1 867 mots créoles (+390 mots)
- **Sources littéraires** : Intégration de textes créoles authentiques
- **Script d'enrichissement** : `EnrichirDictionnaire.py` pour l'évolution du dictionnaire
- **Textes de Gisèle Pineau** : "L'Exil selon Julia"
- **Poésie de Sonny Rupaire** : "Cette igname brisée qu'est ma terre natale"
- **Chansons traditionnelles** : "La voix des Grands-Fonds"

### 🔧 Amélioré
- **Qualité des suggestions** : Plus précises grâce au corpus enrichi
- **Couverture lexicale** : +26% de mots créoles supportés
- **Performance** : Optimisation du chargement du dictionnaire

### 📚 Données
- **Mots les plus ajoutés** : ka, an, té, on, pou, nou, ou, sé
- **Format conservé** : Liste de listes [mot, fréquence]
- **Validation** : Tests sur textes littéraires créoles

## [1.1.0] - 2025-09-06

### 🎨 Ajouté
- **Design Guadeloupéen** : Palette de couleurs du drapeau
- **Logo Potomitan™** : Intégration respectueuse du branding culturel
- **Thème authentique** : Couleurs Caribbean (bleu, jaune, rouge, vert)

### 🔧 Amélioré
- **Interface utilisateur** : Plus moderne et culturellement appropriée
- **Visibilité** : Contraste optimisé pour tous les thèmes Android
- **Accessibilité** : Meilleure lisibilité des touches et suggestions

### 🐛 Corrigé
- **Texte blanc sur fond blanc** : Problème de contraste résolu
- **Affichage suggestions** : Visibilité améliorée
- **Icônes** : Restauration des icônes manquantes

## [1.0.0] - 2025-09-05

### 🎉 Première Version
- **Clavier AZERTY** : Layout français adapté au créole
- **1 477 mots créoles** : Dictionnaire initial basé sur le corpus Potomitan
- **Suggestions intelligentes** : Prédiction de texte en temps réel
- **Accents créoles** : Support complet des caractères spéciaux
- **Mode numérique** : Basculement alphabétique/numérique
- **Service IME** : Intégration native Android

### ⌨️ Fonctionnalités Clavier
- **Appui long** : Accès aux accents (à, è, ò, etc.)
- **Suggestions contextuelles** : Prédiction basée sur la fréquence
- **Interface native** : InputMethodService Android
- **Compatibilité** : Android 7.0+ (API 24)

### 📱 Applications Testées
- **Messagerie** : WhatsApp, Telegram, SMS
- **Email** : Gmail, Outlook
- **Réseaux sociaux** : Facebook, Twitter
- **Productivité** : Notes, Documents Google

### 🏗️ Architecture
- **Kotlin** : Langage de développement moderne
- **Material Design** : Guidelines UI/UX respectées
- **JSON** : Format optimisé pour le dictionnaire
- **Gradle** : Build system standard Android

### 📊 Métriques Initiales
- **Taille APK** : ~8 MB
- **RAM** : ~15 MB en utilisation
- **Démarrage** : <500ms chargement dictionnaire
- **Latence** : <50ms suggestions

## [Versions Futures]

### 🔮 Prévu v1.3.0
- [ ] **Mode hors-ligne complet**
- [ ] **Apprentissage personnalisé**
- [ ] **Sync cloud dictionnaire**
- [ ] **Thèmes personnalisables**
- [ ] **Raccourcis gestuels**

### 🌟 Roadmap v2.0.0
- [ ] **Support vocal**
- [ ] **Traduction français ↔ créole**
- [ ] **Correction orthographique**
- [ ] **API développeurs**
- [ ] **Extension autres créoles caribéens**

---

### Notes de Version

#### Format des Versions
- **Major.Minor.Patch** (SemVer)
- **Major** : Changements incompatibles
- **Minor** : Nouvelles fonctionnalités compatibles
- **Patch** : Corrections de bugs

#### Types de Changements
- **🎉 Ajouté** : Nouvelles fonctionnalités
- **🔧 Amélioré** : Fonctionnalités existantes
- **🐛 Corrigé** : Corrections de bugs
- **🚨 Déprécié** : Fonctionnalités obsolètes
- **❌ Supprimé** : Fonctionnalités retirées
- **🔒 Sécurité** : Correctifs de sécurité
