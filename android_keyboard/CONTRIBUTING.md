# Guide de Contribution

Merci de votre intérêt pour contribuer au **Clavier Créole Guadeloupéen** ! 

## 🌟 Comment Contribuer

### Types de Contributions Bienvenues

1. **🐛 Rapports de Bugs**
   - Problèmes d'affichage ou de fonctionnement
   - Incompatibilités avec certaines applications
   - Erreurs dans le dictionnaire créole

2. **✨ Nouvelles Fonctionnalités**
   - Améliorations de l'interface utilisateur
   - Nouvelles méthodes de saisie
   - Extensions du dictionnaire

3. **📚 Améliorations Linguistiques**
   - Correction/enrichissement du dictionnaire
   - Ajout de variantes créoles
   - Validation culturelle et linguistique

4. **📖 Documentation**
   - Améliorations du README
   - Guides d'utilisation
   - Documentation technique

## 🚀 Processus de Contribution

### 1. Préparation

```bash
# Fork le projet sur GitHub
git clone https://github.com/votre-username/KreyolKeyb.git
cd KreyolKeyb/android_keyboard

# Créer une branche pour votre contribution
git checkout -b feature/ma-nouvelle-fonctionnalite
```

### 2. Développement

```bash
# Setup environnement
./gradlew build

# Lancer les tests
./gradlew test

# Installation sur émulateur/device
./gradlew installDebug
```

### 3. Standards de Code

#### Kotlin
- Suivre les [conventions Kotlin officielles](https://kotlinlang.org/docs/coding-conventions.html)
- Utiliser les coroutines pour les opérations asynchrones
- Documenter les fonctions publiques

```kotlin
/**
 * Charge le dictionnaire créole depuis les assets
 * @return Liste de mots avec fréquences
 */
private suspend fun loadCreoleDictionary(): List<Pair<String, Int>> {
    // Implementation...
}
```

#### Architecture
- Respecter le pattern **InputMethodService**
- Séparer logique métier et interface utilisateur
- Utiliser **ViewBinding** pour les layouts

### 4. Tests

#### Tests Obligatoires
- Tests unitaires pour la logique dictionnaire
- Tests d'interface pour les layouts
- Tests d'intégration avec applications courantes

```kotlin
@Test
fun testCreoleSuggestions() {
    val suggestions = suggestionEngine.getSuggestions("ka")
    assertTrue(suggestions.contains("ka"))
    assertTrue(suggestions.contains("kay"))
}
```

### 5. Validation

```bash
# Compiler sans erreurs
./gradlew assembleDebug

# Tests passants
./gradlew test

# Lint sans warnings critiques
./gradlew lint
```

## 📝 Conventions de Commit

### Format des Messages

```
type(scope): description courte

Description détaillée optionnelle

Fixes #123
```

### Types de Commits
- **feat**: Nouvelle fonctionnalité
- **fix**: Correction de bug
- **docs**: Documentation uniquement
- **style**: Formatage, point-virgules manquants, etc.
- **refactor**: Refactoring de code
- **test**: Ajout/modification de tests
- **chore**: Maintenance, build, etc.

### Exemples
```bash
feat(dictionary): add 400 new Creole words from literary sources

- Extract words from Gisèle Pineau texts
- Improve suggestion accuracy for common expressions
- Update frequency rankings

Fixes #15
```

```bash
fix(keyboard): resolve white text on white background issue

- Update text colors for better contrast
- Test on dark and light themes
- Ensure accessibility compliance

Fixes #23
```

## 🎯 Zones de Contribution Prioritaires

### 1. Dictionnaire et Linguistique
- **Enrichissement lexical** : Nouveaux mots créoles authentiques
- **Validation culturelle** : Vérification par locuteurs natifs
- **Variantes régionales** : Support autres créoles caribéens

### 2. Interface Utilisateur
- **Accessibilité** : Support lecteurs d'écran, contrastes
- **Thèmes** : Nouveaux designs respectueux de la culture
- **Responsive** : Adaptation tablettes et grands écrans

### 3. Performance
- **Optimisation mémoire** : Chargement dictionnaire
- **Latence suggestions** : Algorithmes plus rapides
- **Taille APK** : Compression assets et ressources

### 4. Compatibilité
- **Applications populaires** : WhatsApp, Instagram, TikTok
- **Versions Android** : Support Android 14+
- **Langues système** : Interface multilingue

## 🧪 Tests et Validation

### Tests Fonctionnels
1. **Saisie de base**
   - Frappe normale en créole
   - Accents et caractères spéciaux
   - Mode numérique

2. **Suggestions**
   - Prédictions correctes
   - Performance temps réel
   - Mémorisation contextuelle

3. **Compatibilité**
   - Applications courantes
   - Différentes versions Android
   - Thèmes sombres/clairs

### Tests Culturels
- **Validation linguistique** par locuteurs natifs
- **Respect culturel** des représentations
- **Authenticité** du vocabulaire

## 🌍 Considérations Culturelles

### Respect de la Langue Créole
- Utiliser les **normes d'écriture** établies
- Respecter les **variantes dialectales**
- Consulter la **communauté créolophone**

### Sensibilité Culturelle
- Éviter les **stéréotypes**
- Valoriser le **patrimoine linguistique**
- Collaborer avec des **experts culturels**

### Sources Authentiques
- Privilégier les **textes littéraires** créoles
- Collaborer avec **institutions culturelles**
- Citer les **auteurs et sources**

## 📋 Checklist Pull Request

Avant de soumettre votre PR, vérifiez :

### Code
- [ ] ✅ Code compile sans erreurs
- [ ] ✅ Tests passent tous
- [ ] ✅ Lint sans warnings critiques
- [ ] ✅ Documentation à jour

### Fonctionnalité
- [ ] ✅ Fonctionnalité testée manuellement
- [ ] ✅ Compatible avec apps courantes
- [ ] ✅ Performance acceptable
- [ ] ✅ Pas de régression

### Linguistique (si applicable)
- [ ] ✅ Mots créoles validés
- [ ] ✅ Sources authentiques citées
- [ ] ✅ Respect des normes d'écriture
- [ ] ✅ Validation par locuteur natif

### Documentation
- [ ] ✅ README mis à jour si nécessaire
- [ ] ✅ CHANGELOG mis à jour
- [ ] ✅ Commentaires de code ajoutés
- [ ] ✅ Exemples d'utilisation fournis

## 🤝 Code de Conduite

### Nos Engagements
- **Respect** de tous les contributeurs
- **Inclusivité** et diversité
- **Bienveillance** dans les échanges
- **Professionnalisme** dans les discussions

### Comportements Attendus
- Langage respectueux et constructif
- Patience avec les nouveaux contributeurs
- Focus sur l'amélioration du projet
- Ouverture aux feedback et critiques

### Sanctions
Les comportements inappropriés peuvent mener à :
- Avertissement formel
- Suspension temporaire
- Exclusion permanente du projet

## 📞 Contact et Support

### Canaux de Communication
- **GitHub Issues** : Bugs et fonctionnalités
- **GitHub Discussions** : Questions générales
- **Email** : contact@potomitan-kreyol.gp

### Équipe de Maintenance
- **@medhi** - Développeur principal
- **@potomitan** - Expert culturel et linguistique

## 🎉 Reconnaissance

### Contributeurs
Tous les contributeurs sont mentionnés dans :
- README principal
- CHANGELOG des versions
- Hall of Fame du projet

### Types de Reconnaissance
- **Badge contributeur** GitHub
- **Mention** dans les releases
- **Certificat** de contribution culturelle
- **Invitation** événements communautaires

---

**Merci de contribuer à la préservation et à la modernisation du kreyòl guadeloupéen ! **
