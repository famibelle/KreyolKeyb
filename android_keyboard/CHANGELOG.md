# Changelog

Toutes les modifications notables de ce projet seront documentées dans ce fichier.

Le format est basé sur [Keep a Changelog](https://keepachangelog.com/fr/1.0.0/),
et ce projet adhère au [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [7.1.9] - 2026-07-19

### 🔎 Diagnostic local du parcours d'activation

- **Quatre jalons horodatés en local** : première ouverture de l'app, activation du clavier, sélection, premier mot tapé. Chaque jalon n'est enregistré qu'une fois, en SharedPreferences — rien ne quitte le téléphone, conformément à la promesse « zéro collecte » de l'app
- **Carte « Diagnostic d'activation » dans À Propos** : affiche la date de première ouverture puis, pour chaque jalon suivant, le délai écoulé (« moins d'une minute après l'ouverture », « 2 h après l'ouverture »...) ou « pas encore ». Utile pour comprendre où le parcours accroche quand un utilisateur en difficulté montre son téléphone, et pour vérifier soi-même que tout est en place
- **Premier mot horodaté par le clavier lui-même** au moment où un mot est réellement commité (suggestion tapée ou espace), champ de test compris — c'est le moment « aha » que tout le parcours cherche à atteindre

## [7.1.8] - 2026-07-19

### ✍️ Premier mot guidé et rappel en cas de désélection

- **Micro-tâche concrète au premier essai** : l'étape 3 ne dit plus vaguement « tapez quelques mots » mais propose « Essayez d'écrire “Bonjou tout moun” et regardez les suggestions vous aider » — un objectif précis qui fait rencontrer immédiatement la vraie valeur du clavier : les suggestions bilingues et les accents créoles
- **Rappel clair quand le clavier n'est plus actif** : après une mise à jour système ou un changement de réglages, Android peut désélectionner le clavier sans prévenir. L'utilisateur qui rouvre l'app ne retombe plus sur le « Bienvenue ! » de première installation : il voit « 🔔 Le clavier Kréyòl n'est plus sélectionné » (ou « n'est plus actif ») avec l'explication probable et l'étape exacte à refaire
- **Pas de redite pour ceux qui connaissent** : l'aperçu du clavier (image de motivation destinée aux nouveaux) n'est plus montré aux utilisateurs qui avaient déjà tout configuré, et la barre d'onglets reste accessible — seul le tout premier setup est en mode concentré

## [7.1.7] - 2026-07-19

### 📝 Instructions formulées par objectif, valables sur tous les téléphones

- **Des instructions qui décrivent le but, pas un chemin d'écran** : chaque constructeur (Samsung, Xiaomi...) réorganise les écrans de réglages à sa façon, donc décrire un chemin précis (« dans la liste Clavier à l'écran ») peut ne pas correspondre à ce que voit l'utilisateur. Les cartes disent maintenant quoi chercher : « Trouvez 'Klavyé Kréyòl Karukera' dans l'écran qui s'ouvre, activez l'interrupteur, puis revenez ici » — le libellé de l'app est la seule constante affichée partout
- **Préparation aux avertissements système** : l'activation d'un clavier tiers déclenche un ou deux dialogues de confirmation selon les téléphones, et abandonner en cours de route annule l'activation (constaté en test : valider le premier puis revenir en arrière laisse le clavier désactivé). La carte d'information annonce désormais « un ou deux avertissements de sécurité : validez-les tous pour terminer »
- **Fin des Toasts d'instruction** : les messages flottants qui s'affichaient par-dessus les écrans système (position et durée non maîtrisables) sont supprimés au profit des cartes, lisibles avant de partir vers les réglages. Seule exception conservée : l'écran de repli du correcteur orthographique, différent de celui attendu, garde son message d'orientation
- **Étape 4 plus claire** : la carte du correcteur indique directement quoi choisir dans l'écran (« choisissez 'Correcteur Kréyòl Karukera' »), instruction qui n'existait auparavant que dans un Toast fugace

## [7.1.6] - 2026-07-19

### 🚀 Première ouverture concentrée sur l'essentiel

- **Mode « première ouverture »** : tant que le clavier n'a jamais été entièrement configuré, la barre d'onglets (jeux, stats, guide...) et le swipe entre onglets sont masqués — l'utilisateur qui vient d'installer l'app voit uniquement le parcours de configuration, sans distraction. La navigation se révèle en fondu au moment où la configuration aboutit, comme une petite récompense
- **Le flag ne se pose qu'une seule fois** (`onboarding_completed` en local) : un utilisateur déjà configuré qui met à jour l'app ne voit jamais le mode restreint, et celui dont le clavier se retrouve désélectionné plus tard (changement de téléphone, mise à jour système) garde l'accès à tous les onglets
- **Aperçu du clavier avant l'effort** : en tête du parcours de configuration, une image du vrai clavier montre ce que l'utilisateur va obtenir — suggestions bilingues « Bonjou » (Kréyòl) / « Bonjour » (Français) au-dessus du clavier AZERTY créole avec ses accents ò, é, è. La motivation précède la demande d'aller accepter les avertissements système. L'aperçu disparaît une fois le clavier configuré

## [7.1.5] - 2026-07-19

### ⚡ Détection instantanée des changements de clavier

- **Réaction immédiate à la sélection du clavier** : l'onboarding sondait l'état du clavier toutes les 2 secondes (Handler périodique), donc après avoir choisi « Klavyé Kréyòl Karukera » dans le sélecteur, l'écran « Tout est prêt ! » et l'apparition automatique du clavier pouvaient traîner jusqu'à 2 secondes. Le polling est remplacé par un `ContentObserver` sur les réglages système (`DEFAULT_INPUT_METHOD`, `ENABLED_INPUT_METHODS`, correcteur orthographique) : la réaction est désormais instantanée, vérifié sur émulateur (interface à jour moins de 0,9 s après le tap, focus et clavier compris)
- **Moins de travail en arrière-plan** : plus de Handler qui interroge le système toutes les 2 secondes tant que l'onglet Démarrage est visible ; l'app ne fait plus rien tant qu'un réglage ne change pas réellement. L'observation démarre au `onResume` et s'arrête au `onPause` du fragment
- Ces réglages sont des clés publiques stables présentes sur tout Android (aucune dépendance à un constructeur particulier)

## [7.1.4] - 2026-07-19

### ⚡ Onboarding fluidifié : sélecteur immédiat et enchaînement automatique

- **Le sélecteur de clavier s'ouvre immédiatement** : le bouton « Ouvrir le sélecteur » attendait 2,2 secondes (le temps qu'un Toast d'instruction disparaisse) avant d'afficher le sélecteur système. Ce temps mort invitait au double-tap, avec sélection accidentelle d'un clavier possible (reproduit en test : le second tap atterrissait sur le dialogue en train de s'ouvrir). Le Toast, l'attente et l'EditText invisible qui servait de contexte de saisie sont supprimés — l'instruction est déjà portée par la carte de l'étape 2, visible derrière le dialogue
- **Enchaînement automatique des étapes** : au retour des réglages système avec le clavier fraîchement activé, le sélecteur s'ouvre tout seul ; une fois « Klavyé Kréyòl Karukera » sélectionné, le champ de test reçoit automatiquement le focus et le clavier Kréyòl apparaît — l'utilisateur peut taper son premier mot sans aucun tap de navigation
- **Robustesse** : l'appel `showInputMethodPicker()` est silencieusement ignoré par Android tant que l'activité n'a pas repris le focus fenêtre (`InputMethodManagerService: Ignoring showInputMethodPickerFromClient`, vérifié dans logcat). Nouveau garde `runWhenWindowFocused()` : attente du focus avec retries bornés avant l'appel. Parcours complet vérifié sur émulateur API 34 depuis un état vierge

## [7.1.3] - 2026-07-17

### 🐛 Toast recouvrant le sélecteur de clavier

- **Le message d'aide de l'onboarding recouvrait la liste des claviers** : signalé par un utilisateur (« le toaster de proposition de choix de clavier couvre le choix du clavier »), reproduit en testant avec seulement 2 claviers installés, ce qui place « Klavyé Kréyòl Karukera » en dernière position de la liste système, pile là où le Toast d'aide s'affichait. `openInputMethodPicker()` ouvrait le sélecteur seulement 100ms après avoir affiché le Toast (`LENGTH_LONG`, ~3,5s), donc les deux se chevauchaient forcément pendant plusieurs secondes. Le sélecteur ne s'ouvre désormais qu'une fois le Toast (`LENGTH_SHORT`, ~2s) complètement disparu (délai porté à 2200ms). Tentative de `setGravity(TOP)` pour repositionner le Toast en haut de l'écran : sans effet vérifié sur Android 14/API 34, laissé en place par précaution pour d'éventuels appareils plus anciens mais ce n'est plus le mécanisme de protection réel

## [7.1.2] - 2026-07-17

### 🐛 Corrections issues d'une campagne de tests approfondie sur émulateur

- **Suggestions kréyòl polluées par le contexte n-gram** : un mot sans aucune correspondance dans le dictionnaire (ex. « Ordinateur ») affichait quand même 3 suggestions kréyòl sans rapport, car le bonus contextuel n-gram (prédiction du mot suivant probable) était appliqué à tous les candidats sans vérifier qu'ils correspondaient au préfixe réellement tapé. `getKreyolSuggestions()` filtre désormais les candidats n-gram par préfixe avant de leur appliquer le bonus
- **Bouton correcteur orthographique ouvrait le mauvais écran** : `openSpellCheckerSettings()` lançait `ACTION_INPUT_METHOD_SETTINGS`, qui ouvre la liste des claviers et non le sélecteur de correcteur orthographique. Lance maintenant directement l'écran standard AOSP (`Settings$SpellCheckersSettingsActivity`), avec repli sur l'ancien comportement si l'écran est absent sur certaines ROM
- **Crash/ANR possible en changeant rapidement d'onglet Jeux** : `WordSearchFragment` et `WordScrambleFragment` planifiaient du travail (`generateNewPuzzle()`, `startNewGame()`) via `post {}`, qui peut s'exécuter après que le fragment a été détaché lors d'un changement d'onglet — provoquant une `IllegalStateException` sur `requireActivity()`/`requireContext()`. Ajout de gardes `isAdded` et remplacement de `requireContext()` par `context?.let {}` dans les blocs catch concernés
- **Score « Mots réussis » du Démêle-mots faussé** : `endGame()` affichait `currentWordIndex` comme nombre de mots réussis, ce qui comptait aussi les mots passés/abandonnés. Nouveau compteur dédié `wordsCorrect`, incrémenté uniquement sur une réponse correcte

## [7.1.1] - 2026-07-15

### 🐛 Correction du compteur de mots découverts

- **Statistiques de vocabulaire corrigées** : l'onglet Stats affichait « 0 mots découverts » malgré des centaines d'utilisations enregistrées, découvert en rejouant une conversation complète sur émulateur. `loadVocabularyStats()` ne comptait un mot comme « découvert » que s'il avait été tapé exactement une fois (`userCount == 1`) ; dès qu'un mot était réutilisé, il disparaissait du compteur et de la liste « Mots Découverts ». Aligné sur la définition déjà correcte utilisée ailleurs dans le code (`CreoleDictionaryWithUsage.getDiscoveredWordsCount()` : un mot est découvert dès qu'il a été utilisé au moins une fois)

## [7.1.0] - 2026-07-15

### 🌍 Bilinguisme Kreyòl + Français

Le clavier propose désormais des suggestions en français en plus du kréyòl, avec un rendu visuel unifié sur tout le clavier. Cette version regroupe et clôt le chantier ouvert en 7.0.12/7.0.13 :

- **Suggestions bilingues actives** : français à partir de 3 lettres, kréyòl toujours prioritaire. Cette fonctionnalité existait dans le code depuis la v5.3.1 mais n'avait jamais été activée — il fallait changer complètement de clavier (Play Store) pour écrire en français
- **Deux rangées séparées** (Kreyòl en haut, Français en dessous) : le français ne peut plus être poussé hors écran par un mot kreyòl long ("Bonmaten-la"), un souci réel du premier rendu à rangée unique
- **Puces pleines à contraste renforcé**, texte blanc, micro-label KR/FR groupé par langue (pas répété sur chaque puce)
- **Prédictions contextuelles unifiées** : le mode « mot suivant » (n-grams) affichait encore l'ancien rectangle bleu pastel, découvert en observant une conversation tapée en direct sur émulateur — même rendu que les suggestions bilingues désormais
- **Dictionnaire français nettoyé** : 700 entrées réduites à 662 mots uniques (38 doublons qui pouvaient faire perdre une suggestion pertinente au profit d'un doublon)

## [7.0.13] - 2026-07-15

### 🎨 Look & feel des suggestions bilingues

- **Puces pleines à contraste renforcé** : les suggestions Kreyòl (vert) et Français (bleu) passent d'un texte coloré sur fond gris à des puces pleines arrondies avec texte blanc — plus lisible en vision périphérique et en plein soleil
- **Micro-label KR/FR groupé** : un seul repère de langue avant chaque groupe de suggestions (pas répété sur chaque puce)
- **Suggestions Français toujours visibles** : la barre de suggestions passe de un à deux rangées empilées (Kreyòl en haut, Français en dessous). Auparavant, un mot kreyòl un peu long ("Bonmaten-la") poussait le français hors de l'écran, derrière un scroll horizontal peu découvrable — le français, censé être mis en avant, restait en pratique invisible. La rangée française se masque automatiquement quand elle est vide (< 3 lettres tapées)

## [7.0.12] - 2026-07-15

### 🌍 Bilinguisme Kreyòl + Français

- **Suggestions bilingues réactivées** : le clavier propose désormais aussi des suggestions en français (en bleu), en plus du kréyòl (en vert), à partir de 3 lettres tapées. Le kréyòl reste toujours prioritaire (3 premières positions). Jusqu'ici cette fonctionnalité existait dans le code depuis la v5.3.1 mais n'avait jamais été activée : il fallait changer complètement de clavier (Play Store) pour écrire en français.
- **Dictionnaire français nettoyé** : 700 entrées réduites à 662 mots uniques (38 doublons supprimés, ex. « dire », « professeur », « riche » comptés deux fois), qui pouvaient faire perdre une suggestion pertinente au profit d'un doublon.

## [7.0.10] - 2026-07-13

### 📣 Croissance et gamification

- **Bouton « Noter l'application »** dans l'onglet À Propos, à côté du partage : ouvre la fiche Play Store (lien direct, avec repli automatique si la Play Store n'est pas disponible)
- **Carte de niveau partageable** : à chaque passage de niveau (Pipirit → Benzo), une carte illustrée générée à la volée célèbre la progression et peut être partagée en un clic
- **Correction du créole** : le titre de la carte de partage disait littéralement « faire l'amour pour le créole ». Remplacé par « Ba kréyòl la lanmou'w ! » (donne ton amour au créole)

## [7.0.9] - 2026-07-12

### ✏️ Correction linguistique

- **Message de partage en créole corrigé** (version validée par un locuteur) : « Mwen ka sèvi épi Klavyé Kréyòl Karukera pou ékri kréyòl asi téléfòn an mwen ! Sé on klavyé Android gratui ki ba'w sigjesyon mo an kréyòl Gwadloup. »

## [7.0.8] - 2026-07-12

### ⭐ Avis et mesure d'audience

- **Demande d'avis Google Play in-app** (API officielle In-App Review) : la boîte de notation s'affiche après un vrai usage du clavier et à partir de la 2ᵉ ouverture de l'app
- **Lien de partage tracké** : le bouton « Partager l'application » ajoute `utm_source=in_app_share` pour mesurer les installations issues du bouche-à-oreille dans la Play Console

## [7.0.7] - 2026-07-12

### 📣 Partage

- **Bouton « Partager l'application »** dans l'onglet À Propos : ouvre le sélecteur de partage natif Android avec un message pré-rempli (créole + lien Play Store), pour encourager le bouche-à-oreille

## [7.0.6] - 2026-07-11

### ✨ Guide illustré et navigation

- **Captures d'écran du clavier fonctionnel** ajoutées au guide de l'utilisateur : popup d'accents, barre de suggestions active, mode chiffres/symboles
- **Onglet À Propos déplacé en dernière position** (après Guide) dans la barre d'onglets

## [7.0.5] - 2026-07-11

### ✨ Guide de l'utilisateur

- **Nouvel onglet « Guide »** (6ᵉ onglet, 📖) : écriture en kréyòl, accents par appui long, suggestions et autocomplétion, correction orthographique système, chiffres/symboles, les 2 jeux de vocabulaire, les 8 niveaux de progression (Pipirit → Benzo), et une FAQ courte (clavier invisible, changer de clavier, confidentialité)

## [7.0.4] - 2026-07-11

### ✨ Tunnel d'activation amélioré

- **Carte explicative avant l'avertissement système Android** : prévient l'utilisateur que l'avertissement de collecte de données est standard pour tout clavier tiers, avec un lien direct vers la politique de confidentialité ("zéro collecte")
- **Lien vers la politique de confidentialité** ajouté également dans l'onglet À Propos
- **Nom du clavier raccourci** dans les paramètres système : n'est plus tronqué dans la liste des claviers ni dans le sélecteur
- **Confirmation + astuce accents au premier usage réel** du clavier en dehors de l'app (une seule fois)
- Nettoyage de code mort (`createActivationBanner`/`createStatusBar`, jamais utilisés)

## [7.0.3] - 2026-07-11

### 🐛 Correction

- **Casse des suggestions sous majuscule automatique corrigée** : taper une suggestion juste après la première lettre d'un message (majuscule automatique active) mettait le mot entier en MAJUSCULES ("B" → "BÈL" au lieu de "Bèl"). Une seule lettre majuscule initiale applique désormais une casse de titre, comme attendu.
- Découvert par une simulation automatisée de frappe s'appuyant exclusivement sur les suggestions (982 mots) : 54 messages sur 134 étaient concernés.

## [7.0.2] - 2026-07-10

### 📚 Dictionnaire enrichi

- **Dictionnaire créole passé de 3 680 à 4 911 mots** (+33%) grâce à un enrichissement du corpus source (427 → 2 383 textes)
- **Vocabulaire de sécurité et premiers secours** ajouté : blesé, doktè, rimèd, évakwasyon, vitman et bien d'autres, pour être compris même dans les situations urgentes
- **Prédictions contextuelles enrichies** : 3 582 → 4 232 suggestions basées sur le contexte de la phrase
- Qualité des suggestions validée sur un test de 50 phrases créoles du quotidien : aucune régression, temps de réponse toujours instantané

## [7.0.0] - 2026-07-03

### ✨ Nouvelles fonctionnalités

#### 🎯 Intégration de la distance de Levenshtein dans le scoring
- **Propagation complète de la distance** : `LevenshteinDistance` retourne désormais `(mot, fréquence, distance)` au lieu de perdre la distance
- **Formule de score améliorée** : `(3-distance)×100000` — une correction à 1 édition bat toujours une correction à 2 éditions
- **Exemple concret** : "mesli" propose désormais "mèsi" (d=1) avant "mésyé" (d=2 plus fréquent)
- **Testabilité** : `calculateDictionaryScore` déplacé dans companion object, 4 tests `SuggestionScoringTest` ajoutés

### 🚀 Performances

#### ⚡ Optimisations du moteur de suggestions
- **Normalisation accents optimisée** : Table char→char au lieu de regex (~37 000 compilations de regex évitées par frappe)
- **Formes normalisées précalculées** : au chargement du dictionnaire
- **Bonus préfixe insensible aux accents** : "fe" favorise désormais "fè"
- **Annulation des suggestions précédentes** : à chaque frappe (plus de résultats périmés)
- **Suggestions dès la 1ère lettre** : `MIN_WORD_LENGTH` passé de 2 à 1 (ka, an, sé…)
- **Tests retirés du démarrage production** : remplacés par des tests JVM (`AccentTolerantMatcherTest`)

### 📚 Documentation

- **Rapport d'audit complet** : Analyse du pipeline de suggestions (bugs, performance, confidentialité, qualité prédictive) avec addendum sur les quick wins appliqués
- **CLAUDE.md** : Guide pour Claude Code (architecture, commandes de build, pièges du build local)

### 🧹 Nettoyage

- **Code mort supprimé** : stratégies bigram/trigram (modèle unigramme uniquement), cache Levenshtein, `applyCaseToSuggestion`
- **Suite de tests réparée** : `returnDefaultValues`, dépendance org.json de test, assertions de distance corrigées
- **659 lignes supprimées, 182 ajoutées**

## [6.5.1] - 2025-11-19

### 🐛 Corrections de bugs

#### 🔤 Préservation des majuscules/minuscules dans les suggestions
- **Correction majeure** : Les suggestions respectent maintenant le pattern de casse de votre saisie
  - Si vous tapez "kaBr", les suggestions affichent "kaBrit" (pas "kabrit")
  - Si vous tapez "BONJ", les suggestions affichent "BONJOU" (tout en majuscules)
  - Si vous tapez "Zan", les suggestions affichent "Zanmi" (première lettre en majuscule)
  - La casse est préservée à l'insertion du mot sélectionné
  - Fonctionne dans tous les modes (dictionnaire, bilingue, contextuel)

### 🔧 Améliorations techniques
- Ajout de `applyCasingPattern()` dans `SuggestionEngine.kt` pour appliquer intelligemment la casse
- Correction dans `mergeAndRankSuggestions()`, `getKreyolSuggestions()`, `getFrenchSuggestions()`
- Modification de `InputProcessor.processSuggestionSelection()` pour ne plus écraser la casse

## [6.5.0] - 2025-11-17

### ✨ Nouvelles fonctionnalités

#### 🔤 Correction orthographique intelligente
- **Détection automatique des fautes de frappe** : Le système de suggestions propose maintenant des corrections même quand vous faites des erreurs
  - Algorithme de distance de Levenshtein intégré pour détecter les mots similaires
  - Correction des lettres manquantes : "bonjo" → suggère "bonjou"
  - Correction des lettres en trop : "mesli" → suggère "mèsi"
  - Correction des lettres inversées ou incorrectes : "zanbi" → suggère "zanmi"
  - Combinaison avec la tolérance aux accents : "kreyol" → suggère "kréyòl"
  - Distance maximale de 2 modifications pour éviter les faux positifs
  - Priorisation par fréquence d'utilisation des mots

#### 🎯 Système de suggestions amélioré - Stratégie en cascade
Le moteur de suggestions utilise maintenant une approche intelligente en 6 étapes :

**1️⃣ Capture de saisie** (`InputProcessor.kt`)
- Détection caractère par caractère lors de la frappe
- Construction progressive du mot : "b" → "bo" → "bon" → "bonj" → "bonjo"
- Déclenchement des suggestions via `onWordChanged()`

**2️⃣ Recherche par préfixe** (Étape A - Rapide)
- Recherche de mots commençant par l'input saisi
- Tolérance aux accents intégrée (`AccentTolerantMatcher`)
- Si des résultats trouvés → Retour immédiat

**3️⃣ Correction orthographique** (Étape B - Nouvelle fonctionnalité)
- Activée uniquement si recherche préfixe ne retourne rien ET input ≥ 3 lettres
- Essai 1 : Calcul de distance avec normalisation des accents
- Essai 2 : Calcul de distance sans normalisation
- Algorithme de Levenshtein pour trouver mots à distance ≤ 2 modifications

**4️⃣ Enrichissement contextuel** (N-grams)
- Analyse de l'historique des mots précédemment saisis
- Consultation du modèle `creole_ngrammes.json`
- Bonus de +50 points pour suggestions contextuelles

**5️⃣ Calcul des scores** (Formule avancée)
```
Score = Fréquence du mot
      + 50 (si préfixe exact)
      + (3 - distance_Levenshtein) × 15  [NOUVEAU]
      + 10 (si mot court ≤ 6 lettres)
      - 10 (si mot long > 12 lettres)
      + 5 (si mot avec accents)
```

**6️⃣ Tri et affichage**
- Fusion dictionnaire + N-grams + corrections
- Tri par score décroissant
- Limitation aux meilleures suggestions (5-10 résultats)
- Affichage dans la barre de suggestions

**Optimisations de performance** :
- ⚡ Pré-filtrage par longueur de mot (±2 caractères)
- 🔄 Traitement asynchrone (`CoroutineScope`) pour ne pas bloquer l'UI
- 💾 Cache pour calculs répétés (`calculateCached()`)
- 🎯 Recherche intelligente : rapide d'abord, puissante ensuite

### 🧪 Tests et qualité

#### ✅ Suite de tests complète
- **16 tests unitaires** validant la correction orthographique avec le dictionnaire créole réel
- Tests de fautes courantes : lettres manquantes, en trop, inversées
- Tests de mots typiques : salutations, famille, verbes, mots courants
- Validation des performances (< 100ms pour recherche)
- Couverture des cas limites et edge cases

### 🛠️ Technique

#### 📦 Nouveaux fichiers
- `LevenshteinDistance.kt` : Utilitaire de calcul de distance et recherche de corrections
- `SimpleDictionaryTest.kt` : Suite de tests basée sur le dictionnaire créole
- Dépendances de test ajoutées : JUnit 4.13.2, Kotlin Test 1.9.22

#### 🔧 Modifications
- `SuggestionEngine.kt` : Intégration de la correction orthographique automatique
- `build.gradle` : Incrémentation de version et ajout des dépendances de test

## [6.4.1] - 2025-11-14

### ✨ Nouvelles fonctionnalités

#### 🔤 Jeu de mots mélangés (Word Scramble)
- **Nouveau jeu intégré** : Retrouve l'ordre des lettres pour former des mots créoles
  - Sélection aléatoire de 10 mots parmi les 3,680 du dictionnaire
  - 3 niveaux de difficulté : Facile (4-5 lettres), Normal (5-7 lettres), Difficile (7-10 lettres)
  - Indices visuels : première et dernière lettre pré-remplies automatiquement
  - Score simple : 100 points par mot réussi
  - Système d'indices : révèle la prochaine lettre (-20 points)
  - Interface épurée sans pression temporelle

#### 🎲 Amélioration jeu de mots cachés (Word Search)
- **Interface optimisée** : Expérience de jeu améliorée
  - Fix sélection diagonale : possibilité de croiser des mots déjà trouvés
  - Meilleure réactivité tactile sur la grille 8×8

### 📊 Statistiques et Progression

#### 🔧 Corrections critiques
- **Comptage précis du dictionnaire** : Affichage correct de "3,680 mots" au lieu de "0 mots"
  - Le total est maintenant toujours chargé depuis `creole_dict.json`
  - Plus de confusion avec le fichier d'usage utilisateur vide
- **Niveau initial correct** : Fix affichage "Pipirit" avec 0 mots découverts
  - Avant : affichait "Benzo (niveau maximum)" à tort
  - Après : affiche correctement "Pipirit" et "55 mots restants pour Ti moun"
- **Message de progression intelligent** : 
  - Affiche "niveau maximum atteint" uniquement si vraiment à Benzo (100% du dictionnaire)
  - Sinon affiche le niveau actuel avec progression vers le suivant

### 🎨 Interface et Navigation

#### 🔧 Réorganisation des onglets
- **Nouvel ordre** : Démarrage → Kréyòl an mwen → Mots Mêlés → Mots Mélangés → À Propos
  - L'onglet "À Propos" déplacé en dernière position pour meilleure ergonomie
  - Les jeux regroupés au centre pour faciliter l'accès
- **Navigation améliorée** : 5 onglets avec swipe cyclique maintenu

### 🧹 Refactoring

#### 🎯 Code
- **Suppression du timer** : Jeu de mots mélangés sans contrainte de temps
  - Retrait de `CountDownTimer` et toutes ses références
  - Simplification du scoring (plus de bonus de temps)
  - Interface header épurée : score centré uniquement
- **Optimisation mémoire** : Meilleure gestion des lettres pré-remplies
  - Les lettres de début et fin ne sont plus dupliquées dans les choix
  - Fix restauration correcte après validation incorrecte
- **Code cleaning** : -98 lignes, +70 insertions
  - Suppression du code redondant lié au timer
  - Simplification de la logique de validation

## [6.4.0] - 2025-11-12

### ✨ Nouvelles fonctionnalités

#### 🔤 Jeu de mots cachés (Word Search)
- **Intégration dictionnaire** : Les mots sont maintenant pris directement depuis `creole_dict.json`
  - Sélection aléatoire parmi les 14,722 mots disponibles
  - Filtrage automatique des mots de 3 à 8 lettres pour compatibilité avec la grille 8×8
  - Cache en mémoire pour optimiser les performances
- **Interface simplifiée** : Affichage unifié "🎯 Mots Créoles"
  - Suppression du système de catégorisation par thèmes
  - Variété maximale grâce à la sélection aléatoire dans tout le dictionnaire

### 🧹 Refactoring

#### 🎯 Word Search
- **Nettoyage code** : Simplification de l'architecture
  - Suppression des listes de mots statiques par thème (ANIMAUX, FRUITS, etc.)
  - Suppression des fonctions `getAllThemes()` et `getThemeDisplayName()`
  - Fusion de `loadWordsFromDictionary()` dans `getThemeWords()`
  - Retrait de la logique de filtrage par mots-clés
  - Résultat : code plus simple, maintenance facilitée, variété maximale

## [6.3.0] - 2025-11-06

### ✨ Nouvelles fonctionnalités

#### 🎮 Système de gamification dynamique
- **Niveaux adaptatifs** : Les seuils de progression s'ajustent automatiquement à la taille du dictionnaire
  - Ti moun : 1.5% du dictionnaire (~55 mots pour 3680 mots)
  - Débrouya : 5% (~184 mots)
  - An mitan : 12% (~441 mots)
  - Kompè Lapen : 25% (~920 mots)
  - Kompè Zamba : 45% (~1656 mots)
  - Potomitan : 70% (~2576 mots)
  - Benzo : 100% (tous les mots !)
- **Progression motivante** : Écarts entre niveaux croissants (facile au début, plus difficile à la fin)
- **Évolutivité** : Si le dictionnaire grandit (ex: 5000 mots), les seuils restent proportionnels

### 🔧 Corrections

#### 🐛 Suggestions N-grams
- **Fix regression critique** : Correction du format JSON incompatible avec le moteur de suggestions
  - Suppression du wrapper `"predictions"` attendu mais absent dans le fichier
  - Correction de la clé `"prob"` → `"probability"` dans 3 emplacements
  - Les suggestions contextuelles fonctionnent à nouveau correctement

### 🧹 Refactoring

#### 🎯 Gamification
- **Nettoyage code** : Suppression de 2 systèmes de niveaux redondants
  - Retrait de `MasteryLevel` enum dans VocabularyStats.kt (6 niveaux)
  - Retrait de `getCurrentLevel()` dans VocabularyStatsActivity.kt (7 niveaux)
  - Conservation du système Gaussian dans SettingsActivity.kt (8 niveaux Créoles)
  - Résultat : -50 lignes de code, logique unifiée

## [6.2.9] - 2025-11-05

### 🎨 Interface et UX

#### 🧹 Améliorations
- **Réduction des espaces blancs** : Suppression des espaces blancs inutilisés
  - Retrait de l'espace au-dessus de "Mots à Découvrir"
  - Réduction de l'espace au-dessus de "Mots les plus utilisés"
  - Interface plus compacte et mieux organisée

#### 🔧 Technique
- Suppression du conteneur vide `buttonsContainer` avec padding de 32dp
- Réduction du padding supérieur de `top5Container` de 16dp à 0dp

## [6.2.8] - 2025-11-05

### 🎨 Interface et UX

#### ✨ Nouveau
- **Navigation cyclique** : Swipe infini entre les onglets
  - Swipe vers la droite sur "À Propos" → retour à "Démarrage"
  - Swipe vers la gauche sur "Démarrage" → accès à "À Propos"
  - Navigation fluide dans les deux sens sans limite
- **Réintégration bandeau bleu** : Retour du header "Klavyé Kréyòl" en haut de l'écran pour une meilleure identification de l'app

#### 🔧 Technique
- Implémentation d'un adapter avec nombre virtuel de pages (`Int.MAX_VALUE`)
- Utilisation du modulo pour mapper les positions virtuelles aux 3 onglets réels
- Démarrage au milieu de la plage virtuelle pour permettre le swipe bidirectionnel
- Calcul intelligent de la distance la plus courte lors des clics sur onglets
- Conservation de l'animation Tinder swipe sur tous les déplacements

## [6.2.7] - 2025-11-04

### 🎨 Interface et UX

#### ✨ Nouveau
- **Animation Tinder swipe** : Effet de swipe style Tinder entre les onglets avec :
  - Rotation dynamique -15° à +15° pendant le swipe
  - Translation verticale (carte qui se soulève)
  - Scale progressif jusqu'à 80%
  - Fade out doux avec élévation
  - Animation fluide et moderne pour une navigation tactile plus engageante

#### 🧹 Interface épurée
- **Suppression bandeau bleu** : Retrait du header "Klavyé Kréyòl" en haut de l'écran
- **Suppression logo Potomitan** : Retrait du logo dans l'onglet "À Propos"
- **Design minimaliste** : Interface focalisée sur le contenu essentiel avec navigation par onglets uniquement

#### 🔧 Technique
- Ajout de la classe `TinderSwipeTransformer` implémentant `ViewPager2.PageTransformer`
- Application du transformer via `setPageTransformer()` sur le ViewPager2
- Transformation basée sur 6 propriétés animées : rotation, translationX, translationY, scale, alpha, elevation

## [6.2.3] - 2025-10-29

### 🔧 Corrections

#### 📊 Onglet Statistiques
- **Espacement optimisé** : Suppression du padding top (24dp) dans `createWordListSection()` pour éliminer l'espace vide entre "Mots à Découvrir" et "Mots les plus utilisés"
- **Lisibilité améliorée** : Augmentation de la taille du texte de 16f à 20f dans la liste des top 5 mots (rang, nom du mot et compteur)

Ces ajustements rendent l'onglet "Kréyòl an mwen" plus compact et lisible.

## [6.2.2] - 2025-10-28

### 🔧 Corrections

#### 🎯 Ergonomie et défilement
- **Scroll fonctionnel dans tous les onglets** : Ajout des `LayoutParams` appropriés (MATCH_PARENT, WRAP_CONTENT) dans les 3 méthodes de création de contenu
- **ScrollView optimisé** : Configuration de `isFillViewport=true` pour permettre le calcul correct de la zone défilante
- **Gestion du clavier virtuel** : 
  - Ajout de `windowSoftInputMode="adjustPan|stateHidden"` dans AndroidManifest.xml
  - Le clavier ne couvre plus le contenu important
  - Scroll automatique vers l'EditText de test quand il obtient le focus
- **Interface simplifiée** : 
  - Suppression de la barre de statut redondante (verte/rouge)
  - Carte de progression compacte avec layout horizontal
  - Design plus épuré et moderne

#### 🛠️ Technique
- `createOnboardingContent()` : LayoutParams + OnFocusChangeListener sur EditText
- `createStatsContent()` : LayoutParams pour permettre le scroll
- `createAboutContent()` : LayoutParams pour permettre le scroll
- `OnboardingFragment` : ScrollView avec isFillViewport=true
- `AndroidManifest.xml` : windowSoftInputMode pour SettingsActivity

## [6.2.1] - 2025-10-27

###  Corrections

####  Interface d'onboarding
- **Sélecteur de clavier fonctionnel** : Le bouton "Ouvrir le sélecteur" affiche maintenant correctement la liste des claviers Android
- **Rafraîchissement dynamique** : L'interface se met à jour automatiquement quand on revient à l'app après avoir sélectionné le clavier
- **Détection d'état en temps réel** : 
  - La barre de statut passe instantanément au vert  après sélection
  - Le bouton devient " Sélectionné" automatiquement
  - L'étape 3 se déverrouille immédiatement
  - La barre de progression atteint 100% sans recharger l'app

####  Technique
- Restauration du `onResume()` dans `SettingsActivity` avec délai de 300ms
- Ajout du `onResume()` dans `OnboardingFragment` pour recréer le contenu dynamiquement
- Amélioration de la détection des changements d'état clavier
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
