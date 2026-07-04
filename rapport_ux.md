# Rapport d'audit UX — Écrans de l'application Klavyé Kréyòl Karukera

Ce rapport complète `rapport.md` (audit du moteur de suggestions) en couvrant cette fois les **écrans de l'application companion** : Paramètres (5 onglets), tableau de bord de progression, et les deux jeux de vocabulaire. Méthode : inspection du code (Activities, Fragments, layouts XML) croisée avec des captures d'écran live sur émulateur (Pixel 5, Android 14).

## 1. Résumé exécutif

1. **Un écran entier de gamification est mort-né** : `VocabularyStatsActivity` (dashboard complet, thème sombre) n'est reliée à aucun bouton de l'UI shippée — remplacée en doublon par un onglet Stats qui réimplémente la même chose en thème clair.
2. **Chaque jeu existe deux fois** (Activity standalone + Fragment embarqué dans les Paramètres), avec deux bases de code distinctes qui ont déjà divergé visuellement (thème clair/sombre) et fonctionnellement (boutons Indice/Thèmes présents ou absents selon la version).
3. **Un bug de contraste texte, dupliqué deux fois** (Fragment Kotlin + Activity XML) : trois libellés clés du jeu Mots Mélangés sont quasi invisibles (pas de `textColor` défini).
4. **La version affichée dans « À Propos » est figée en dur** (« 6.5.1 ») et ne suit pas le build réel (7.0.0 actuellement) — désynchronisée à chaque release.
5. **107 couleurs hexadécimales codées en dur** dans `SettingsActivity.kt` malgré une palette complète déjà définie dans `colors.xml`, sans aucun `themes.xml`/`styles.xml` — dette de cohérence visuelle qui grandit à chaque écran ajouté.
6. **Boutons non-fonctionnels qui ressemblent à des boutons actifs** : « Indice », « Thèmes », partage de fin de partie sont tous des TODO no-op dans le jeu Mots Mêlés standalone — l'utilisateur clique et rien ne se passe.
7. **Zéro accessibilité** : aucun `contentDescription` dans toute l'application.

## 2. Inventaire des écrans et navigation

| Écran | Fichier | Accessible depuis l'UI ? |
|---|---|---|
| **SettingsActivity** (launcher, 5 onglets) | `SettingsActivity.kt` (~3200 lignes, construit entièrement en code, sans layout XML) | Oui — écran d'accueil |
| **VocabularyStatsActivity** (dashboard autonome, thème sombre) | `gamification/VocabularyStatsActivity.kt` + `activity_vocabulary_stats.xml` | **Non** — orpheline, seule `MainActivity` (elle-même absente du manifest) y référence un lien |
| **WordSearchActivity** (Mots Mêlés standalone, thème sombre) | `wordsearch/WordSearchActivity.kt` + `activity_word_search.xml` | Oui, via `VocabularyStatsActivity.launchWordSearchGame()` — mais cette dernière est elle-même inatteignable ; dupliqué par `WordSearchFragment` (thème clair) dans les Paramètres |
| **WordScrambleActivity** (Mots Mélangés standalone, thème clair) | `wordscramble/WordScrambleActivity.kt` + `activity_word_scramble.xml` | Dupliqué par `WordScrambleFragment` dans les Paramètres, pas de lien direct depuis l'UI shippée |
| **MainActivity** + `activity_main.xml` / `activity_main_tabs.xml` | `MainActivity.kt` | **Code mort** — absente du manifest, ne peut pas être lancée |

En pratique, un utilisateur normal ne voit **que** `SettingsActivity` et ses 5 onglets. Les trois autres Activities ne sont accessibles que via `adb shell am start` direct (comme fait pour cet audit) — ce qui signifie que tout le travail de style/fonctionnalité qui y a été investi (thèmes sombres, boutons Indice/Thèmes, animations de score) n'est **jamais vu par un utilisateur réel**.

## 3. Bugs d'affichage et incohérences

### 3.1 Caractère corrompu (mojibake) visible en production
`SettingsActivity.kt:2460` — `text = "� Chargement..."` : le caractère d'encodage cassé s'affiche brièvement dans l'en-tête de l'onglet Mots Mêlés pendant le chargement du thème.

### 3.2 Version figée en dur, désynchronisée du build
`SettingsActivity.kt:1245` — `text = "Version : 6.5.1\n" + ...` : chaîne littérale jamais mise à jour, alors que `build.gradle` déclare `versionName "7.0.0"`. L'onglet « À Propos » ment sur la version installée à chaque release depuis au moins deux versions. Corriger avec `BuildConfig.VERSION_NAME`.

### 3.3 Grille Mots Mêlés tronquée à l'écran (standalone)
`activity_word_search.xml:57` déclare `android:numColumns="10"`, mais `WordSearchActivity.kt:85` génère toujours une grille 8×8 et écrase la valeur à l'exécution (`gridView.numColumns = puzzle.gridSize`, ligne 112). Conséquence **visible** : sur la capture live, la dernière ligne de lettres est coupée par le bas de la grille, le conteneur ayant été dimensionné pour 10 colonnes de large mais la grille réelle génère un ratio différent — rendu clairement moins propre que la version embarquée (`WordSearchFragment`), qui n'a pas ce problème.

### 3.4 Boutons d'apparence fonctionnelle qui ne font rien (jeu Mots Mêlés standalone)
Visibles et stylés sur la capture live (« 💡 INDICE », « 🎨 THÈMES »), mais tous des TODO no-op côté code :
- `WordSearchActivity.kt:181` — intégration gamification/XP jamais branchée
- `WordSearchActivity.kt:221` — sélecteur de thèmes non implémenté
- `WordSearchActivity.kt:225` — animation de score non implémentée (le conteneur `scoreAnimationContainer` de 80dp dans `activity_word_search.xml` reste en permanence vide)
- `WordSearchActivity.kt:236` — dialog de fin de partie avec partage non implémenté
- `WordSearchActivity.kt:240` — messages d'erreur non implémentés

Un utilisateur qui appuie sur « Indice » ou « Thèmes » ne reçoit **aucun retour visuel** — pas même un message d'erreur.

### 3.5 Grand espace vide dans l'onglet Statistiques
Capture live de l'onglet « Kréyòl an mwen » : un vide vertical d'environ 300px sépare la carte « Mots à Découvrir » de la section « Mots les plus utilisés », sans élément visible pour l'expliquer — probablement une marge/hauteur de conteneur mal calculée.

## 4. Accessibilité

Aucun `contentDescription` n'existe dans l'ensemble de `res/layout/*.xml` (grilles de jeu, boutons icône, barres de progression). Un utilisateur avec lecteur d'écran ne peut identifier aucun élément interactif de l'application par son nom.

### 4.1 Contraste texte quasi nul — bug dupliqué deux fois
Trois libellés du jeu Mots Mélangés n'ont **aucune couleur de texte définie** et héritent d'une couleur par défaut illisible sur le fond clair (`#F5F5F5`) :
- Onglet embarqué (Kotlin, `SettingsActivity.kt`) : `tvWordNumber` (l.2749, « Mot 1/10 »), `labelScrambled` (l.2785, « Lettres disponibles : »), `labelAnswer` (l.2815, « Ta réponse : »)
- Activity standalone (XML, `activity_word_scramble.xml`) : mêmes trois libellés, lignes 58, 87, 107

Le fait que le **même bug existe indépendamment dans les deux implémentations** illustre concrètement le risque de la duplication de code (constat §6). Comparer avec les éléments voisins qui, eux, définissent bien une couleur explicite (`title` l.2778 → `#1976D2`, `tvScore` → `#4CAF50`) : l'omission n'est pas un choix de design, c'est un oubli.

## 5. Cohérence visuelle / dette de style

- **107 appels à `Color.parseColor("#...")` en dur** dans `SettingsActivity.kt`, alors qu'une palette complète existe dans `colors.xml` (`bleu_caraibe`, `orange_coucher`, `vert_canne`...) et n'est presque jamais référencée via `@color/...` ou `R.color`. Même pratique dans les layouts XML des jeux/dashboard.
- **Aucun `styles.xml`/`themes.xml`** — toutes les Activities déclarent `android:theme="@style/Theme.AppCompat"` brut ; la cohérence visuelle repose entièrement sur la discipline de chaque développeur à réécrire les bons hex à la main.
- **Thème clair/sombre incohérent entre écrans jumeaux**, confirmé dans le code :
  - `activity_word_search.xml:5` → `android:background="#1E1E1E"` (sombre)
  - `activity_word_scramble.xml:5` → `android:background="#F5F5F5"` (clair)
  - `activity_vocabulary_stats.xml` → également sombre
  - Les deux Fragments embarqués dans les Paramètres (`WordSearchFragment`, `WordScrambleFragment`) sont, eux, tous les deux en thème clair.
  - Résultat visible : lancer le jeu Mots Mêlés standalone bascule brutalement vers un thème sombre inattendu par rapport au reste de l'application, alors que le jeu Mots Mélangés standalone reste clair.
- **En-tête redondant** sur `VocabularyStatsActivity` : la toolbar système affiche déjà une flèche retour + le titre « Mon Kreyòl », et le contenu de l'écran répète une seconde flèche « ← » suivie de « MON KREYÒL » en toutes lettres juste en dessous.
- **Icône thématique incohérente** : le niveau de maîtrise « Pipirit » (un petit oiseau du folklore antillais, cf. `RAPPORT_LINGUISTIQUE.md`) est illustré par un émoji 🌍 (globe terrestre) sans rapport avec le thème.
- **`strings.xml` quasi inutilisé** (9 entrées) — tout le texte UI (dizaines de libellés + emoji) est écrit en dur dans le Kotlin/XML, aucun chemin de traduction possible, libellés dupliqués entre fragments embarqués et Activities standalone (déjà partiellement en train de diverger, cf. §5).

## 6. Code mort et duplication

- **`MainActivity.kt` + `activity_main.xml` + `activity_main_tabs.xml`** : aucune référence dans le manifest, aucun autre fichier ne les utilise — entièrement mort.
- **`VocabularyStatsActivity`** : dashboard complet et fonctionnel (progression, Top 5, niveaux de maîtrise), mais inatteignable depuis l'UI réelle (cf. §2) — double maintenance avec `StatsFragment` sans bénéfice utilisateur actuel.
- **Duplication complète des deux jeux** (Activity standalone + Fragment embarqué), avec deux chemins de construction de vue distincts qui ont déjà divergé sur : le thème (clair/sombre), la présence de boutons (Indice/Thèmes présents seulement côté standalone), et un bug de contraste texte dupliqué indépendamment dans les deux versions.

## 7. Autres constats visuels (issus des captures live)

- **Onglet Statistiques** : icône 🌍 pour "Pipirit" (voir §5), gros espace vide non expliqué (§3.5).
- **Jeu Mots Mêlés standalone** : le bouton « ❌ FERMER » et la zone d'animation de score (toujours vide, 80dp) ne sont visibles qu'après défilement — écran dense qui pourrait bénéficier d'un tri des priorités visuelles.
- **Onboarding (onglet Démarrage)** : bien conçu, cartes claires, statuts « ✓ ACTIVÉ »/« ✓ SÉLECTIONNÉ » lisibles — mais leur style de bouton grisé peut laisser penser qu'ils sont encore cliquables/à activer alors qu'ils ne sont que des indicateurs d'état.
- **Icônes d'onglets hétérogènes** : mélange d'emoji illustratifs (🚀🎲) et de pictogrammes plats colorés (« abc », « i ») — cohérence de style à unifier.

## 8. Recommandations priorisées

| Priorité | Action | Effort |
|---|---|---|
| **Quick win** | Corriger le mojibake `SettingsActivity.kt:2460` | Trivial |
| **Quick win** | Remplacer `"Version : 6.5.1"` par `BuildConfig.VERSION_NAME` (`SettingsActivity.kt:1245`) | Trivial |
| **Quick win** | Ajouter `setTextColor(...)` aux 3 libellés illisibles, dans les 2 fichiers concernés | Trivial |
| **Quick win** | Fixer `android:numColumns` dans `activity_word_search.xml` pour refléter la grille 8×8 réelle | Trivial |
| **Quick win** | Supprimer `MainActivity.kt` + `activity_main.xml` + `activity_main_tabs.xml` (code mort confirmé) | Trivial |
| Chantier | Décider du sort de `VocabularyStatsActivity` : soit la relier réellement (bouton dans l'onglet Stats), soit la supprimer au profit de `StatsFragment` | Moyen |
| Chantier | Dédupliquer les jeux : garder une seule implémentation par jeu (Fragment réutilisé dans l'Activity standalone, ou inversement) pour arrêter la divergence déjà observée | Important |
| Chantier | Unifier le thème clair/sombre entre tous les écrans de jeu/dashboard | Moyen |
| Chantier | Créer un `themes.xml`/`styles.xml` et migrer progressivement les `Color.parseColor` vers `@color/...` | Important |
| Chantier | Implémenter ou retirer les boutons no-op (Indice, Thèmes, partage) du jeu Mots Mêlés standalone | Moyen |
| Chantier | Ajouter les `contentDescription` sur les éléments interactifs (accessibilité) | Important |
| Chantier | Externaliser les libellés UI vers `strings.xml` (prérequis à toute traduction future) | Important |
