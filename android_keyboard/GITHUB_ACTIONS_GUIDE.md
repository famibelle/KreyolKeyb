# 🤖 Guide GitHub Actions - Build ChromeOS APK/AAB

## Vue d'ensemble

Ce guide explique comment utiliser **GitHub Actions** pour compiler automatiquement les APK et AAB avec support ChromeOS.

---

## 📋 Table des matières

1. [Workflows disponibles](#workflows-disponibles)
2. [Déclenchement automatique](#déclenchement-automatique)
3. [Déclenchement manuel](#déclenchement-manuel)
4. [Récupération des builds](#récupération-des-builds)
5. [Configuration des secrets](#configuration-des-secrets)
6. [Troubleshooting](#troubleshooting)

---

## 🔧 Workflows disponibles

### 1. **`build-chromeos.yml`** (Nouveau)
**Fichier** : `.github/workflows/build-chromeos.yml`

**Spécialisé ChromeOS** avec :
- ✅ Vérification configuration ChromeOS
- ✅ Validation architectures x86/x86_64
- ✅ Build 4 architectures (ARM + Intel)
- ✅ Rapport de compatibilité détaillé

**Jobs** :
1. `verify-chromeos-config` - Validation manifeste et gradle
2. `build-chromeos-debug-apk` - APK debug avec 4 arch
3. `build-chromeos-debug-aab` - AAB debug
4. `build-chromeos-release-apk` - APK release signé
5. `build-chromeos-release-aab` - AAB release pour Play Store
6. `validate-chromeos-builds` - Rapport de validation
7. `create-chromeos-release` - Release GitHub (si tag)

### 2. **`build-apk.yml`** (Existant)
**Fichier** : `.github/workflows/build-apk.yml`

**Build standard** avec :
- ✅ Génération dictionnaire Hugging Face
- ✅ Build APK et AAB
- ✅ Multiples jobs parallèles

---

## 🚀 Déclenchement automatique

### Sur la branche `feature/chromeos-support`

Le workflow ChromeOS se déclenche automatiquement lors de :

```bash
# 1. Push de code
git add .
git commit -m "fix: Amélioration compatibilité ChromeOS"
git push origin feature/chromeos-support
```

**Résultat** : GitHub Actions démarre automatiquement et compile les APK/AAB

### Sur modification de fichiers spécifiques

Le workflow se déclenche si vous modifiez :
- `android_keyboard/**` (tout fichier dans le projet Android)
- `.github/workflows/**` (workflows eux-mêmes)

### Via tags (pour releases)

```bash
# Créer un tag ChromeOS
git tag -a v5.2.3-chromeos -m "Release ChromeOS v5.2.3"
git push origin v5.2.3-chromeos
```

**Résultat** : Crée une release GitHub avec tous les artifacts

---

## 🎮 Déclenchement manuel

### Via l'interface GitHub

1. **Aller sur GitHub** :
   ```
   https://github.com/famibelle/KreyolKeyb/actions
   ```

2. **Sélectionner le workflow** :
   - Cliquer sur "🖥️ Build ChromeOS Compatible Keyboard"

3. **Run workflow** :
   - Cliquer sur le bouton **"Run workflow"** (à droite)
   - Sélectionner la branche : `feature/chromeos-support`
   - Choisir le type de build :
     - `all` - Tous les builds (APK + AAB)
     - `apk-only` - Uniquement les APK
     - `aab-only` - Uniquement les AAB
   - Cliquer **"Run workflow"**

4. **Attendre** :
   - Le workflow apparaît dans la liste
   - Durée estimée : 5-10 minutes

### Via GitHub CLI (gh)

```bash
# Installer gh si nécessaire
# https://cli.github.com/

# Déclencher le workflow
gh workflow run build-chromeos.yml \
  --ref feature/chromeos-support \
  -f build_type=all

# Voir le statut
gh run list --workflow=build-chromeos.yml

# Voir les logs
gh run view --log
```

### Via l'API GitHub

```powershell
# Déclencher via API REST
$token = "ghp_YOUR_TOKEN"
$headers = @{
    "Authorization" = "Bearer $token"
    "Accept" = "application/vnd.github.v3+json"
}

$body = @{
    ref = "feature/chromeos-support"
    inputs = @{
        build_type = "all"
    }
} | ConvertTo-Json

Invoke-RestMethod `
    -Uri "https://api.github.com/repos/famibelle/KreyolKeyb/actions/workflows/build-chromeos.yml/dispatches" `
    -Method POST `
    -Headers $headers `
    -Body $body
```

---

## 📥 Récupération des builds

### Pendant le workflow (Artifacts)

1. **Aller sur l'exécution** :
   ```
   https://github.com/famibelle/KreyolKeyb/actions
   ```

2. **Cliquer sur l'exécution** (ex: "feat: Add ChromeOS support")

3. **Scroller jusqu'à "Artifacts"** (en bas)

4. **Télécharger** :
   - `chromeos-keyboard-debug-apk` (APK debug)
   - `chromeos-keyboard-release-apk` (APK release)
   - `chromeos-keyboard-debug-aab` (AAB debug)
   - `chromeos-keyboard-release-aab` (AAB release)

### Via GitHub Releases (si tag)

1. **Aller sur Releases** :
   ```
   https://github.com/famibelle/KreyolKeyb/releases
   ```

2. **Sélectionner la version** (ex: `v5.2.3-chromeos`)

3. **Télécharger dans "Assets"** :
   - `KreyolKeyboard-ChromeOS-Release-v5.2.3-chromeos.apk`
   - `KreyolKeyboard-ChromeOS-Debug-v5.2.3-chromeos.apk`
   - `KreyolKeyboard-ChromeOS-Release-v5.2.3-chromeos.aab`

### Via GitHub CLI

```bash
# Lister les artifacts
gh run list --workflow=build-chromeos.yml

# Télécharger les artifacts d'une exécution
gh run download <RUN_ID>

# Télécharger tous les artifacts
gh run download --pattern "chromeos-*"
```

---

## 🔐 Configuration des secrets

Pour signer les builds **release**, configurez les secrets GitHub :

### 1. Aller dans les settings

```
https://github.com/famibelle/KreyolKeyb/settings/secrets/actions
```

### 2. Ajouter les secrets

Cliquer **"New repository secret"** pour chaque :

| Nom | Valeur | Description |
|-----|--------|-------------|
| `STORE_PASSWORD` | `votre_mot_de_passe` | Mot de passe du keystore |
| `KEY_ALIAS` | `potomitan_key` | Alias de la clé |
| `KEY_PASSWORD` | `votre_mot_de_passe_cle` | Mot de passe de la clé |

**Note** : Sans ces secrets, le workflow utilise la signature debug.

### 3. Vérifier la configuration

```yaml
# Dans build-chromeos.yml
env:
  STORE_PASSWORD: ${{ secrets.STORE_PASSWORD }}
  KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
  KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
```

---

## 📊 Monitoring des builds

### Voir les logs en temps réel

1. **Aller sur Actions** :
   ```
   https://github.com/famibelle/KreyolKeyb/actions
   ```

2. **Cliquer sur l'exécution en cours**

3. **Cliquer sur un job** (ex: "Build ChromeOS Release APK")

4. **Voir les logs** en temps réel

### Notifications

GitHub envoie automatiquement des notifications :
- ✅ Succès du workflow
- ❌ Échec du workflow
- 📧 Par email (configurable dans Settings → Notifications)

### Build Status Badge

Ajouter dans `README.md` :

```markdown
![ChromeOS Build](https://github.com/famibelle/KreyolKeyb/actions/workflows/build-chromeos.yml/badge.svg?branch=feature/chromeos-support)
```

Résultat : ![ChromeOS Build](https://img.shields.io/badge/build-passing-brightgreen)

---

## 🐛 Troubleshooting

### Problème : Workflow ne se déclenche pas

**Solution** :
1. Vérifier que le fichier est dans `.github/workflows/`
2. Vérifier la syntaxe YAML (indentation correcte)
3. Vérifier les branches configurées dans `on.push.branches`

### Problème : Build échoue (architectures manquantes)

**Erreur** :
```
❌ ERREUR: Architectures x86/x86_64 manquantes
```

**Solution** :
```gradle
// Dans app/build.gradle
ndk {
    abiFilters 'armeabi-v7a', 'arm64-v8a', 'x86', 'x86_64'
}
```

### Problème : Signature release échoue

**Erreur** :
```
⚠️ Production secrets not available
```

**Solution** :
Configurer les secrets GitHub (voir [Configuration des secrets](#-configuration-des-secrets))

### Problème : Artifact non trouvé

**Erreur** :
```
❌ APK release non trouvé
```

**Solution** :
1. Vérifier que le job de build a réussi
2. Vérifier le path dans `upload-artifact`
3. Attendre la fin complète du job

### Problème : Quota GitHub Actions dépassé

**Erreur** :
```
You have exceeded your quota
```

**Solution** :
- GitHub Free : 2000 minutes/mois
- Optimiser le workflow (moins de jobs)
- Upgrader vers GitHub Pro (3000 min/mois)

---

## 🎯 Exemples d'utilisation

### Scénario 1 : Build rapide pour test

```bash
# 1. Modifier le code localement
# 2. Commit et push
git add android_keyboard/app/src/
git commit -m "test: Test nouvelle fonctionnalité"
git push origin feature/chromeos-support

# 3. Attendre ~5 minutes
# 4. Télécharger l'APK debug depuis Artifacts
```

### Scénario 2 : Release officielle

```bash
# 1. Finaliser les modifications
git add .
git commit -m "chore: Prepare release v5.3.0"

# 2. Créer un tag
git tag -a v5.3.0-chromeos -m "Release ChromeOS v5.3.0"

# 3. Push avec le tag
git push origin feature/chromeos-support --tags

# 4. GitHub Actions crée automatiquement la release
# 5. Télécharger depuis GitHub Releases
```

### Scénario 3 : Build uniquement APK (pas AAB)

Via l'interface GitHub :
1. Actions → Build ChromeOS Compatible Keyboard
2. Run workflow
3. Sélectionner `apk-only`
4. Run

**Gain de temps** : ~50% plus rapide

---

## 📚 Ressources

### Documentation GitHub Actions
- [Workflows](https://docs.github.com/en/actions/using-workflows)
- [Artifacts](https://docs.github.com/en/actions/using-workflows/storing-workflow-data-as-artifacts)
- [Secrets](https://docs.github.com/en/actions/security-guides/encrypted-secrets)

### Documentation Gradle
- [Build APK](https://developer.android.com/studio/build/building-cmdline)
- [Build AAB](https://developer.android.com/guide/app-bundle)
- [Signing](https://developer.android.com/studio/publish/app-signing)

### Fichiers du projet
- `.github/workflows/build-chromeos.yml` - Workflow ChromeOS
- `android_keyboard/app/build.gradle` - Configuration build
- `android_keyboard/app/src/main/AndroidManifest.xml` - Manifeste

---

## ✅ Checklist de build

Avant de déclencher un build :

- [ ] Code compilé localement (`.\gradlew assembleDebug`)
- [ ] Tests passés
- [ ] AndroidManifest.xml contient les déclarations ChromeOS
- [ ] build.gradle contient x86/x86_64
- [ ] Secrets GitHub configurés (pour release)
- [ ] Branche pushée sur GitHub
- [ ] Commit message clair

---

## 🎉 Résumé

**GitHub Actions** permet de :
- ✅ Compiler automatiquement à chaque push
- ✅ Builder APK et AAB pour ChromeOS
- ✅ Valider les 4 architectures
- ✅ Créer des releases GitHub
- ✅ Distribuer facilement les builds

**Commande la plus simple** :
```bash
git push origin feature/chromeos-support
# Et c'est tout ! GitHub Actions fait le reste
```

---

**Auteur** : Saint-Ange Corneille Famibelle  
**Date** : 4 octobre 2025  
**Branche** : `feature/chromeos-support`
