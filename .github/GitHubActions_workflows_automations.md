# 🤖 GitHub Actions - Clavier Créole Guadeloupéen

Ce dossier contient les workflows d'automatisation pour le projet **Clavier Créole Guadeloupéen Potomitan™**.

## 🔄 Workflows Disponibles

### 1. `android-build.yml` - Build Automatisé
**Déclencheurs :**
- Push sur `main` ou `develop`
- Pull Request vers `main`
- Déclenchement manuel

**Actions :**
- 🔨 **Build Debug/Release APK**
- 🧪 **Tests unitaires**
- 🔍 **Analyse de code (Lint)**
- 📚 **Validation dictionnaire créole**
- 🔒 **Scan de sécurité**
- 📦 **Upload des artifacts**

### 2. `release-creator.yml` - Création de Releases
**Déclencheurs :**
- Push de tags `v*` (ex: `v3.1.0`)
- Déclenchement manuel avec version

**Actions :**
- 🏷️ **Création automatique de release GitHub**
- 📝 **Génération des notes de version**
- 📱 **Upload APK de production**
- 🎯 **Publication sur GitHub Releases**

## 🚀 Utilisation

### Build Automatique
Chaque push déclenche automatiquement :
```bash
git push origin main
# → Déclenche android-build.yml
# → APK disponible dans "Actions" > "Artifacts"
```

### Créer une Release
```bash
# Méthode 1: Via tag
git tag v3.1.0
git push origin v3.1.0

# Méthode 2: Via GitHub UI
# Actions > Release Clavier Créole > Run workflow
```

## 📊 Monitoring

### Status Badges
Ajoutez ces badges dans le README principal :

```markdown
![Build Status](https://github.com/famibelle/KreyolKeyb/workflows/%20Build%20Clavier%20Créole%20Guadeloupéen/badge.svg)
![Release](https://img.shields.io/github/v/release/famibelle/KreyolKeyb)
![License](https://img.shields.io/github/license/famibelle/KreyolKeyb)
```

### Artifacts Générés
- **Debug APK** : Build de développement (toutes les branches)
- **Release APK** : Build de production (main uniquement)
- **Coverage Reports** : Rapports de couverture de code
- **Lint Reports** : Rapports d'analyse statique

## 🛠️ Configuration

### Secrets Requis
Aucun secret spécial requis pour l'instant. Le workflow utilise :
- `GITHUB_TOKEN` (automatique)

### Variables d'Environnement
- `JAVA_VERSION`: 17 (OpenJDK Temurin)
- `GRADLE_VERSION`: wrapper
- `ANDROID_COMPILE_SDK`: 34

##  Spécificités Créoles

Le workflow inclut des validations spécifiques au créole :
- ✅ **Validation du dictionnaire JSON** (`creole_dict.json`)
- 📊 **Statistiques des mots créoles**
- 🔤 **Vérification des caractères accentués**
- 🏝️ **Contrôles de qualité culturelle**

## 🔧 Maintenance

### Mise à jour des Workflows
1. Modifier les fichiers `.yml`
2. Tester sur une branche de développement
3. Merger vers `main`

### Debugging
- Consultez l'onglet "Actions" sur GitHub
- Vérifiez les logs détaillés de chaque étape
- Utilisez `workflow_dispatch` pour tests manuels

---

**Potomitan™** - Automatisation CI/CD pour le clavier créole guadeloupéen 