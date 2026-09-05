# Astuce de la semaine

Contenu de la carte « Astuce de la semaine », affichée dans l'onglet
**Démarrage** une fois le clavier activé **et** sélectionné
(`SettingsActivity.kt:1158`).

## Fonctionnement

- Les 39 astuces vivent dans `WEEKLY_TIPS` (`SettingsActivity.kt:89`), et nulle
  part ailleurs : ce fichier documente et source cette liste, il n'est pas lu
  par l'application.
- `getTipOfTheWeek()` (`SettingsActivity.kt:3516`) prend le numéro de semaine
  modulo la taille de la liste. Rotation séquentielle et non tirage aléatoire
  seedé sur la date (contrairement à `getWordOfTheDay()`,
  `SettingsActivity.kt:3525`) : la liste est parcourue en entier et deux
  semaines de suite ne retombent jamais sur la même astuce. Le cycle complet
  dure donc 39 semaines, soit neuf mois.
- Le décalage de fuseau est ajouté au timestamp pour que le changement d'astuce
  ait lieu à minuit local, pas à minuit UTC. Le `+3` du calcul cale la bascule
  sur le lundi, le jour 0 de l'ère Unix étant un jeudi.
- L'ordre de la liste entrelace les thèmes : deux entrées voisines se suivent à
  l'écran, elles ne doivent donc pas traiter le même sujet.

## Règle d'écriture

Chaque astuce décrit une fonctionnalité **réellement présente** dans
l'application, vérifiable dans le code. La colonne « Source » ci-dessous est la
raison d'être de ce fichier : avant d'ajouter ou de modifier une astuce, il faut
pouvoir la remplir. Une astuce dont la source disparaît du code doit être
retirée de `WEEKLY_TIPS` en même temps.

Le texte est en français, comme le reste de l'onglet Démarrage. Aucun kréyòl n'y
est rédigé : les seuls mots kréyòl cités (`kréyòl`, noms des niveaux, noms des
onglets) sont repris tels quels de l'application ou du dictionnaire.

---

## Les 39 astuces

Les numéros correspondent à l'ordre dans `WEEKLY_TIPS`, donc à l'ordre de
passage : l'astuce n° 1 s'affiche la semaine où le compteur repart à zéro, puis
une par semaine dans cet ordre.

### Saisie et clavier

| # | Astuce | Source |
|---|--------|--------|
| 1 | Appui long sur une lettre pour les accents et caractères spéciaux (é, è, à, ò). Glisser vers celui voulu, puis relâcher. | `accentMap` (`AccentHandler.kt:63`), appui long à 500 ms (`AccentHandler.kt:25`) |
| 5 | Les accents affichés dans le coin d'une touche annoncent ce que cache l'appui long. | `getCornerHintsForKey()` (`AccentHandler.kt:399`), `cornerHintOverrides` (`AccentHandler.kt:86`) |
| 7 | « é » et « è » ont leur touche dédiée en bas, « ò » la sienne entre « o » et « p ». | `row4` (`KeyboardLayoutManager.kt:118`), `row1` (`KeyboardLayoutManager.kt:110`) |
| 21 | Digraphes en appui long : ch sous c, dj sous d, tj sous t, ng et ny sous n. | `accentMap` (`AccentHandler.kt:63`), digraphes GEREC documentés juste au-dessus |
| 11 | Appui long sur la virgule (`;` `:` `'`), sur le point (`!` `?` `…`). | `accentMap` (`AccentHandler.kt:63`), entrées `","` et `"."` |
| 3 | Appui long d'une seconde sur la barre d'espace (le petit 🌐) pour changer de clavier. | `SPACE_LONG_PRESS_DELAY = 1000L` (`KeyboardLayoutManager.kt:35`), indice 🌐 (`KeyboardLayoutManager.kt:306`), `processSpaceLongPress()` (`InputProcessor.kt:365`) |
| 9 | Majuscule à trois états : une majuscule, verrouillage, retour au normal. | `handleShift()` (`InputProcessor.kt:292`) |
| 32 | Première lettre de chaque phrase en majuscule automatique. | `shouldAutoCapitalize()` (`InputProcessor.kt:483`) |
| 13 | Bouton « 123 » pour chiffres et symboles, euro compris ; « ABC » pour revenir. | `handleModeSwitch()` (`InputProcessor.kt:321`), rangée numérique avec `€` (`KeyboardLayoutManager.kt:131`) |
| 15 | Touche emoji en bas à droite, près de 1900 emojis classés par catégories. | Layout emoji (`KeyboardLayoutManager.kt:142`), `EmojiData.categories` (`EmojiData.kt:28`), asset `emoji_data.json` |
| 18 | Appui long sur un emoji de personne pour choisir la couleur de peau. | `emojiSkinTones` / `loadEmojiSkinTones()` (`AccentHandler.kt:104`), `EmojiData.skinTones` (`EmojiData.kt:29`) |
| 29 | Le retour arrière efface un emoji en entier, couleur de peau comprise. | `calculateBackspaceLength()` (`InputProcessor.kt:191`) : paires de surrogates + modificateur de ton |
| 27 | La touche Entrée s'adapte au champ : Rechercher, Envoyer, ou retour à la ligne. | `handleEnter()` (`InputProcessor.kt:209`) |
| 39 | Glisser le doigt le long de la barre d'espace promène le curseur lettre par lettre. | `setupSpaceLongPress()` (`KeyboardLayoutManager.kt`), seuil `scaledTouchSlop` puis un cran tous les `SPACE_CURSOR_STEP_DP` (10 dp) ; `InputProcessor.moveCursorBy()`. Depuis la 14.0.0 |

### Suggestions

| # | Astuce | Source |
|---|--------|--------|
| 2 | Toucher une suggestion la complète, espace inclus. | `processSuggestionSelection()` (`InputProcessor.kt:412`) |
| 26 | Kréyòl prioritaire, français à partir de 3 lettres. | `frenchActivationThreshold = 3` (`BilingualSuggestion.kt:85`), `mergeSuggestionsKreyolFirst()` (`SuggestionEngine.kt:463`) |
| 6 | Taper sans accent fonctionne : « kreyol » propose « kréyòl ». | `AccentTolerantMatcher.startsWith()` (`SuggestionEngine.kt:134`). Vérifié : `kréyòl` est dans `creole_dict.json`, `kreyol` non |
| 10 | Lettre oubliée, en trop ou à côté : les suggestions arrivent quand même. | Distance de Levenshtein dans le scoring (`SuggestionEngine.kt:127`). Formulation volontairement sans « lettres inversées » : `LevenshteinDistance.kt` ne gère pas la transposition |
| 14 | Après un espace, suite probable d'après les deux mots précédents. | `getNgramSuggestions()` (`SuggestionEngine.kt:799`), modèle décrit dans `NGRAMS.md` |
| 16 | Plus un mot est employé, plus il remonte dans les suggestions. | Bonus d'usage personnel (`SuggestionEngine.kt:121`), plafonné à `MAX_COUNTED_USAGES = 20` (`SuggestionEngine.kt:42`) |
| 19 | La suggestion respecte la casse commencée. | `applyCasingPattern()` (`SuggestionEngine.kt:57`) |
| 24 | Replacer le curseur dans un mot déjà écrit relance les suggestions dessus. | `syncWordWithCursor()` (`InputProcessor.kt:389`) |
| 31 | Sources littéraires : Telchid, Rupaire, Rippon et d'autres, onglet À Propos. | Carte « 📚 Sources littéraires » (`SettingsActivity.kt:1566`), liste des auteurs (`SettingsActivity.kt:1575`) |

### Progression

| # | Astuce | Source |
|---|--------|--------|
| 4 | Chaque mot tapé fait progresser le niveau. | `WordCommitListener` (`gamification/WordCommitListener.kt`), `CreoleDictionaryWithUsage` (`gamification/CreoleDictionaryWithUsage.kt`) |
| 20 | Part du dictionnaire kréyòl déjà employée. | `coveragePercentage` affiché (`SettingsActivity.kt:2549`) |
| 25 | Mot du jour en haut de « Kréyòl an mwen ». | Bloc « MOT DU JOUR » (`SettingsActivity.kt:2579`), `getWordOfTheDay()` (`SettingsActivity.kt:3525`) |
| 28 | Classement des mots les plus utilisés. | Section « Mots les plus utilisés » (`SettingsActivity.kt:2615`) |
| 30 | Sept niveaux de Pipirit à Potomitan, plus un huitième à découvrir. | `getCurrentLevel()` (`SettingsActivity.kt:2971`) : huit niveaux, seuils gaussiens. Le huitième (Benzo) reste volontairement non nommé, c'est un easter egg |
| 33 | Partage de la carte de niveau. | `shareLevelCard()` (`SettingsActivity.kt:3137`), `buildLevelCardBitmap()` (`SettingsActivity.kt:3053`) |

### Jeux

| # | Astuce | Source |
|---|--------|--------|
| 8 | Mo an Karénaj : 5 lettres, 6 essais, vert bien placé / jaune mal placé. | `WORD_LENGTH = 5`, `MAX_ATTEMPTS = 6` (`mokarenaj/MoKarenajModels.kt:31`), `evaluateGuess()` (`mokarenaj/MoKarenajModels.kt:79`), couleurs `LetterState.color()` (`mokarenaj/MoKarenajModels.kt:17`). Le mot est tiré au hasard à chaque partie (`pickRandomWord()`), d'où l'absence de « mot du jour » dans le texte |
| 17 | Mots Mélangés : 10 mots chronométrés, bouton Indice. | `take(10)` (`wordscramble/WordScrambleModels.kt:76`), `getTimeForDifficulty()` 45/30/20 s (`wordscramble/WordScrambleModels.kt:91`), bouton « 💡 Indice » (`SettingsActivity.kt:4120`) |
| 23 | Mots Mêlés : diagonales et mots à l'envers selon la difficulté. | `WordSearchDifficulty` (`wordsearch/WordSearchModels.kt:63`), `WordDirection` (`wordsearch/WordSearchModels.kt:52`) |
| 37 | Fraz a twou : une phrase kréyòl réelle, quatre propositions, une seule écrite par l'auteur. | Actif `creole_cloze.json` produit par `Dictionnaires/generate_cloze.py`, lu par `cloze/ClozeData.loadQuestions()`. Les leurres viennent du modèle n-grammes pour le même contexte, d'où « elles sonnent juste tant qu'on ne lit pas toute la phrase » |

### Dictionnaire

| # | Astuce | Source |
|---|--------|--------|
| 38 | L'onglet Dictionnaire cherche dans les deux sens ; les jeux disent ce que veut dire leur mot. | `DictionaryFragment` (`SettingsActivity.kt`), quatrième onglet depuis la 12.0.0. Recherche bidirectionnelle : `TranslationDictionary.rechercher()`, dont le premier rang ne se déclenche que si une glose vaut exactement la requête. Côté jeux, `TranslationDictionary.libelle()` aux quatre points de révélation et `filtrerMotsTraduits()` aux quatre tirages |

### Correcteur, confidentialité, aide

| # | Astuce | Source |
|---|--------|--------|
| 12 | Activer le correcteur pour supprimer le soulignement rouge. | `KreyolSpellCheckerService.kt`, étape 4 de l'onboarding (`SettingsActivity.kt:1128`) |
| 34 | Il se choisit sous « Clavier », pas sous « Langues ». | `openSpellCheckerSettings()` (`SettingsActivity.kt:2277`), chemin système documenté dans `CLAUDE.md` |
| 35 | Après une mise à jour, il peut rester muet jusqu'au redémarrage du téléphone. | Comportement Android vérifié sur émulateur (`dumpsys textservices` : `mSpellChecker=null` après réinstallation), documenté dans `CLAUDE.md` |
| 22 | Fonctionnement entièrement hors ligne. | Carte « 🔒 Confidentialité » (`SettingsActivity.kt:1627`), aucune permission réseau au manifeste |
| 36 | Le guide, en bas de l'onglet Démarrage, reprend les étapes en images et les questions fréquentes. | `createGuideContent()`, ouvert par `SheetFragment.pour(PAGE_GUIDE)` depuis `createReferenceLink()`. Le guide n'est plus un onglet depuis la 11.0.0 |

---

## Ajouter une astuce

1. Trouver la source dans le code et vérifier le comportement, au besoin sur
   un appareil ou l'émulateur.
2. Insérer le texte dans `WEEKLY_TIPS` (`SettingsActivity.kt:89`) à une position
   dont les voisines traitent d'autres thèmes.
3. Ajouter la ligne correspondante ici, avec sa source, et corriger les numéros
   des astuces décalées.
4. La longueur du cycle suit automatiquement la taille de la liste : rien
   d'autre à changer dans `getTipOfTheWeek()`. Chaque astuce ajoutée rallonge le
   tour complet d'une semaine.
