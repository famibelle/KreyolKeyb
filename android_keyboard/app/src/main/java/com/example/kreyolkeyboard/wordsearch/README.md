# Mots Mêlés Kreyòl 🎲

## Vue d'ensemble

Module de jeu de mots mêlés intégré au clavier créole KreyolKeyb. Cette fonctionnalité gamifiée permet aux utilisateurs d'apprendre le vocabulaire créole de manière ludique.

## Structure des fichiers

```
wordsearch/
├── WordSearchActivity.kt          # Activité principale du jeu
├── WordSearchModels.kt           # Classes de données et thèmes
├── WordSearchGenerator.kt        # Générateur de grilles
├── WordSearchGridAdapter.kt      # Adaptateur pour la grille tactile
└── WordSearchTest.kt            # Tests de validation
```

## Fonctionnalités

### 🎯 Thèmes disponibles
- **Animaux** : krab, kochon, bèf, chat, chyen...
- **Fruits** : zanana, korosòl, mango, papay...
- **Famille** : manman, papa, granmoun, timoun...
- **Couleurs** : wouj, vè, jòn, ble, nwa...
- **Météo** : soley, lapli, van, cyclone...
- **Corps** : tèt, je, bouch, nen, zòrèy...
- **Maison** : kay, chanm, kizin, salon...
- **Transport** : machin, bis, moto, bisiklèt...

### 🎮 Niveaux de difficulté
1. **EASY** (6x6) : 4 mots, horizontal/vertical uniquement
2. **NORMAL** (8x8) : 6 mots, + diagonales
3. **HARD** (10x10) : 8 mots, toutes directions + mots inversés
4. **EXPERT** (12x12) : 10 mots, mots qui se croisent

### 🏆 Système de points
- **+10 points** par mot trouvé
- **+2 points** par lettre du mot (bonus longueur)
- **+5 points** bonus vitesse si < 60 secondes
- **Score final** : basé sur le temps total et les mots trouvés

## Interface utilisateur

### Écran principal
- **Grille tactile** : Sélection par glissement du doigt
- **Liste des mots** : Affichage dynamique avec statut trouvé/non trouvé
- **Timer** : Chronomètre en temps réel
- **Score** : Progression des mots trouvés

### Contrôles
- **🎲 Nouveau Jeu** : Génère une nouvelle grille
- **💡 Indice** : Affiche un hint pour le prochain mot
- **🎨 Thèmes** : Sélecteur de thème
- **❌ Fermer** : Retour à l'écran précédent

## Intégration

### Lancement depuis l'app
L'activité est accessible depuis `VocabularyStatsActivity` via le bouton :
```kotlin
🎲 JOUER AUX MOTS MÊLÉS
```

### Manifest Android
```xml
<activity
    android:name=".wordsearch.WordSearchActivity"
    android:label="Mots Mêlés Kreyòl"
    android:theme="@style/Theme.AppCompat"
    android:screenOrientation="portrait"
    android:exported="true">
</activity>
```

## Algorithme de génération

### Placement des mots
1. Sélection aléatoire des mots selon le thème
2. Tentative de placement dans toutes les directions autorisées
3. Vérification des collisions (lettres identiques autorisées)
4. Remplissage des cases vides avec lettres aléatoires

### Détection des mots trouvés
1. Capture du geste de glissement
2. Calcul de la ligne droite entre début et fin
3. Extraction du mot formé
4. Validation contre la liste des mots cachés

## Tests

Le module `WordSearchTest` valide :
- Génération correcte des grilles
- Placement des mots dans toutes les directions
- Fonctionnement de tous les thèmes
- Intégrité de la grille finale

## TODO / Améliorations futures

### Version 1.1
- [ ] Animation de découverte des mots
- [ ] Sons de feedback tactile
- [ ] Sauvegarde des meilleurs temps
- [ ] Intégration XP avec le système de gamification

### Version 1.2
- [ ] Mode multijoueur local (tour par tour)
- [ ] Grilles personnalisées
- [ ] Nouveaux thèmes selon les saisons
- [ ] Mode "apprentissage" avec définitions

### Version 1.3
- [ ] Générateur de grilles avancé
- [ ] Statistiques détaillées par thème
- [ ] Achievements et badges
- [ ] Partage de captures d'écran

## Performance

- **Génération** : ~100ms pour une grille 10x10
- **Mémoire** : ~2MB par grille active
- **CPU** : Optimisé pour batteries faibles
- **Stockage** : Aucune sauvegarde persistante (pour l'instant)

## Compatibilité

- **Android** : API 21+ (Android 5.0)
- **Orientation** : Portrait uniquement
- **Résolution** : Adaptatif 320dp à 1080dp+
- **Accessibilité** : Compatible TalkBack (à améliorer)

---

*Développé pour KreyolKeyb - Potomitan™ 🇬🇵*