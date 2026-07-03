# Rapport d'analyse — Clavier prédictif Klavyé Kréyòl Karukera

**Date :** 3 juillet 2026
**Périmètre :** pipeline de suggestions Android (`SuggestionEngine.kt`, `InputProcessor.kt`, `LevenshteinDistance.kt`, `AccentTolerantMatcher.kt`, `FrenchDictionary.kt`, `KreyolInputMethodServiceRefactored.kt`, `BilingualSuggestion.kt`) et assets JSON associés.

---

## 1. Résumé exécutif

Le moteur de suggestions est fonctionnel et bien découpé en composants, mais l'analyse révèle six points majeurs, par ordre de priorité :

1. **Les prédictions bigram/trigram sont du code mort** : le modèle `creole_ngrams.json` ne contient que des clés à un seul mot (3 582 unigrammes, 0 bigrammes/trigrammes), alors que le code cherche des clés `"mot1 mot2"` et `"mot1 mot2 mot3"`. Deux des trois stratégies contextuelles ne se déclenchent donc jamais.
2. **Problème de performance sévère sur le chemin chaud** : à chaque frappe, `AccentTolerantMatcher.normalize()` compile ~10 objets `Regex` **par mot du dictionnaire** (3 680 mots), soit ~37 000 compilations de regex par caractère tapé. C'est la cause la plus probable des lenteurs constatées sur appareils low-end (Samsung A21s).
3. **Confidentialité** : les mots tapés par l'utilisateur sont journalisés en clair dans logcat en build release — un vrai problème pour un clavier.
4. **Biais de classement contre les mots accentués** : le bonus de préfixe (+50) est calculé sans normalisation des accents, ce qui défavorise précisément les graphies correctes (`fè`, `kréyòl`) que le clavier veut promouvoir.
5. **Le mode bilingue est incohérent** : désactivé dans le chemin normal mais réactivé dans le chemin de récupération A21s ; le dictionnaire français est chargé en mémoire pour rien.
6. **Aucun apprentissage utilisateur** : les mots choisis ne renforcent pas les fréquences, et les mots hors dictionnaire ne sont jamais appris.

---

## 2. Architecture actuelle du pipeline

```
Frappe → KeyboardLayoutManager → onKeyPress()
       → InputProcessor.processKeyPress()      (maintient currentWord)
       → onWordChanged(word)                   (service IME)
       → SuggestionEngine.generateDictionarySuggestions(word)
            1. AccentTolerantMatcher (préfixe insensible aux accents)
            2. Fallback Levenshtein si zéro résultat préfixe (≥ 3 lettres)
            3. Scoring (fréquence + bonus préfixe + longueur + accents)
       → onSuggestionsReady() → displaySuggestions() (3 boutons max)

Espace/Entrée → finalizeCurrentWord() → addWordToHistory()
             → generateContextualSuggestions() (N-grams, mot suivant)
```

Point important : le mode bilingue étant désactivé (`enableBilingualSupport()` commenté, `KreyolInputMethodServiceRefactored.kt:229`), le chemin réellement actif est `generateDictionarySuggestions()` — les chemins bilingues et `MIXED` décrits dans le code sont inertes.

---

## 3. Bugs et incohérences

### 3.1 N-grams : stratégies bigram et trigram mortes — **critique**
`getNgramSuggestions()` (`SuggestionEngine.kt:620-687`) tente trois lookups : bigram `"mot1 mot2"`, unigram `"mot"`, trigram `"mot1 mot2 mot3"`. Or le fichier `creole_ngrams.json` ne contient **que des clés unigrammes** (vérifié : 3 582 clés, aucune ne contient d'espace). Les stratégies 1 et 3, ainsi que leurs bonus de probabilité (+0.2 / +0.4), ne s'exécutent jamais. La prédiction contextuelle se réduit donc à « mot suivant le plus probable après le dernier mot », sans contexte étendu.
**Correctif :** régénérer le modèle avec de vraies clés bigram/trigram dans `Dictionnaires/KreyolComplet.py`, ou supprimer le code mort.

### 3.2 Mode bilingue : activation incohérente — **majeur**
- Chemin normal : `enableBilingualSupport()` est commenté (`KreyolInputMethodServiceRefactored.kt:229`).
- Chemin de récupération A21s : il est **appelé** (`KreyolInputMethodServiceRefactored.kt:245`).

Résultat : un appareil low-end dont la première initialisation échoue se retrouve en mode bilingue, les autres non. Par ailleurs `FrenchDictionary` (700 mots) est systématiquement chargé (`SuggestionEngine.kt:151`) alors qu'il n'est jamais consulté dans le chemin actif, et `onBilingualSuggestionsReady()` est un no-op (`KreyolInputMethodServiceRefactored.kt:389-392`).
**Correctif :** trancher (réactiver partout ou nulle part), et ne charger le dictionnaire français que si le mode est actif.

### 3.3 Biais de classement contre les mots accentués — **majeur**
`calculateDictionaryScore()` (`SuggestionEngine.kt:735`) accorde +50 si `word.startsWith(input, ignoreCase = true)` — comparaison **brute, sans normalisation d'accents**. Tous les candidats proviennent pourtant d'un match par préfixe *normalisé*. Conséquence : en tapant `fe`, un mot comme `fenmé` reçoit +50 mais `fè` non ; le malus (-50 relatif) écrase largement le bonus « mot accentué » de +5 (`SuggestionEngine.kt:760`). Le classement pénalise donc les graphies créoles correctes, à rebours de l'objectif pédagogique du projet.
**Correctif :** appliquer le bonus sur les formes normalisées (`AccentTolerantMatcher.normalize(word).startsWith(normalize(input))`).

### 3.4 Bonus Levenshtein jamais appliqué — mineur
Le paramètre `levenshteinDistance` de `calculateDictionaryScore()` (`SuggestionEngine.kt:730`) n'est jamais transmis par aucun appelant : le bonus de correction (+30/+15) et son log sont du code mort. Les corrections orthographiques sont classées uniquement par (distance, fréquence) dans `LevenshteinDistance.findClosestMatches()`.

### 3.5 Désynchronisation du mot courant — **majeur**
`InputProcessor.currentWord` est la seule source de vérité pour les suggestions, mais :
- le service n'implémente pas `onUpdateSelection()` : si l'utilisateur déplace le curseur ou tape au milieu d'un mot, `currentWord` ne correspond plus au texte réel ;
- la suppression par mots (appui long ⌫, `KreyolInputMethodServiceRefactored.kt:800-841`) supprime du texte via `deleteSurroundingText()` sans mettre à jour `currentWord` (le commentaire ligne 832 l'admet) ;
- un collage de texte n'est pas détecté.

Les suggestions peuvent donc être calculées sur un préfixe fantôme, et `processSuggestionSelection()` (`InputProcessor.kt:293`) peut supprimer le mauvais nombre de caractères.
**Correctif :** implémenter `onUpdateSelection()` et reconstruire `currentWord` depuis `getTextBeforeCursor()`.

### 3.6 Race condition sur les suggestions — modéré
Chaque frappe lance une coroutine (`SuggestionEngine.kt:385`) sans annuler la précédente ni horodater les résultats. Une requête lente (fallback Levenshtein sur 3 680 mots) peut aboutir **après** la requête suivante plus rapide et afficher des suggestions périmées.
**Correctif :** conserver le `Job` courant et l'annuler avant chaque nouvelle génération (pattern `latestJob?.cancel()`), ou utiliser un `Flow` + `collectLatest`.

### 3.7 `wordHistory` non thread-safe — modéré
`wordHistory` (`SuggestionEngine.kt:75`) est une `mutableListOf` mutée depuis le thread main (`addWordToHistory`) et lue depuis `Dispatchers.Default` (`getNgramSuggestions`). Risque faible mais réel de `ConcurrentModificationException` ou de lecture incohérente.

### 3.8 Backspace et émojis — mineur
`handleBackspace()` (`InputProcessor.kt:121`) fait `deleteSurroundingText(1, 0)` : sur un émoji (paire de substitution UTF-16, 2 code units), cela n'efface que la moitié du caractère et produit un caractère invalide. Utiliser la longueur du dernier grapheme cluster (ou `sendKeyEvent(KEYCODE_DEL)`).

### 3.9 Divers
- `InputProcessor.applyCaseToSuggestion()` (`InputProcessor.kt:317-335`) : méthode privée jamais appelée (code mort, doublon de `applyCasingPattern`).
- `onFinishInput()` ne rappelle pas `super.onFinishInput()` (`KreyolInputMethodServiceRefactored.kt:643-651`) — contournement fragile du cycle de vie IME, susceptible de casser sur certaines versions d'Android.
- Double application de la casse dans le chemin bilingue : `getKreyolSuggestions()` applique `applyCasingPattern` (`SuggestionEngine.kt:296`), puis `generateBilingualSuggestions()` la réapplique (`SuggestionEngine.kt:235`).

---

## 4. Performance

Le chemin « une frappe → suggestions » est exécuté à chaque caractère ; c'est lui qu'il faut optimiser en priorité.

### 4.1 Compilation de regex massive à chaque frappe — **critique**
`AccentTolerantMatcher.normalize()` (`AccentTolerantMatcher.kt:26-48`) crée ~10 objets `Regex` à chaque appel. `findAccentTolerantSuggestions()` (`AccentTolerantMatcher.kt:82-85`) l'appelle sur **chacun des 3 680 mots** du dictionnaire, à chaque frappe. Ordre de grandeur : ~37 000 compilations de regex + autant de chaînes intermédiaires par caractère tapé — énorme pression GC, particulièrement sensible sur les appareils que le code tente déjà de ménager (fix A21s, monitoring mémoire).
**Correctifs, par ordre d'impact :**
1. **Précalculer la forme normalisée de chaque mot une seule fois au chargement** du dictionnaire (stocker `Triple(mot, motNormalisé, fréquence)`).
2. Remplacer les regex par une table `Char → Char` (un `when` ou un `IntArray` indexé) ou `java.text.Normalizer.normalize(s, NFD)` + filtrage des diacritiques.
3. À défaut, compiler les `Regex` une fois en `val` de l'objet.

### 4.2 Recherche linéaire — majeur
La recherche préfixe parcourt toute la liste (O(n) par frappe). Avec un dictionnaire trié alphabétiquement sur la forme normalisée, une recherche binaire donne la plage de préfixes en O(log n) ; un trie ferait encore mieux. Combiné au 4.1, le coût par frappe deviendrait négligeable.

### 4.3 Fallback Levenshtein coûteux — modéré
`findClosestMatchesNormalized()` (`LevenshteinDistance.kt:150-187`) normalise **tout le dictionnaire deux fois** (une fois pour le pré-filtre longueur, une fois pour la distance) avec les regex du 4.1, puis calcule une matrice DP complète par candidat. Le pré-calcul des formes normalisées (4.1) et une DP à deux lignes avec sortie anticipée (bande de Ukkonen) réduiraient fortement le pic de latence — précisément dans le cas « l'utilisateur a fait une faute », où la réactivité compte.

### 4.4 Cache Levenshtein non borné — mineur
`LevenshteinDistance.cache` (`LevenshteinDistance.kt:193`) croît sans limite et `clearCache()` n'est jamais appelé ; `calculateCached()` n'a d'ailleurs aucun appelant. Supprimer, ou remplacer par un `LruCache`.

### 4.5 Travaux parasites au démarrage du clavier — modéré
`runAccentTolerantTests()` et `testCreoleSpecificCases()` (`KreyolInputMethodServiceRefactored.kt:979-1065`) s'exécutent **en production à chaque création du service**. `testCreoleSpecificCases` remplace temporairement le listener de suggestions réel : si l'utilisateur tape pendant ces ~600 ms, ses suggestions partent dans le listener de test (UI figée) et la restauration peut écraser un listener légitime. À déplacer en tests unitaires JVM ou à gater derrière `BuildConfig.DEBUG`.

### 4.6 Divers
- `System.gc()` forcé dans le monitoring mémoire (`KreyolInputMethodServiceRefactored.kt:147`) : anti-pattern, provoque des pauses au lieu d'en éviter.
- `displaySuggestions()` (`KreyolInputMethodServiceRefactored.kt:505-541`) détruit et recrée 3 `Button` à chaque frappe ; recycler 3 vues fixes et changer leur `text` suffirait.
- `addWordToDictionary()` (`SuggestionEngine.kt:450-466`) recopie et retrie la liste entière à chaque ajout — acceptable seulement parce qu'il n'est jamais appelé (cf. 6.2).

---

## 5. Confidentialité

**Les frappes de l'utilisateur sont journalisées en clair en production** — pour un clavier, c'est le point de vigilance n°1 :

- chaque caractère et le mot courant : `InputProcessor.kt:97,112,129` ;
- chaque liste de suggestions : `SuggestionEngine.kt:243,399` ;
- le texte avant le curseur : `KreyolInputMethodServiceRefactored.kt:419,434`.

Sur un appareil connecté en ADB (ou via des outils de collecte de logs OEM), tout ce que tape l'utilisateur — y compris dans des champs sensibles — est reconstructible depuis logcat. Recommandation : envelopper tous les logs du chemin de saisie dans `if (BuildConfig.DEBUG)` (ou un utilitaire `KLog` central), et ne jamais logguer le contenu des champs marqués `TYPE_TEXT_VARIATION_PASSWORD` même en debug. Le volume de logs (plusieurs dizaines de lignes par frappe, avec les `Log.e("SHIFT_REAL_DEBUG", …)` de `InputProcessor.kt:220-242`) a aussi un coût de performance.

> **Mise à jour (application des quick wins)** : vérification faite, le build **release** supprime déjà tous les appels `Log.*` via R8 (`proguard-rules.pro:119-126` + `minifyEnabled = true`). Le risque décrit ci-dessus ne concerne donc que les builds **debug**. Le remplacement massif des ~150 appels de log n'a pas été fait ; seul le spam `SHIFT_REAL_DEBUG` (niveau `Log.e`, jamais strippé conceptuellement) a été supprimé. La journalisation des mots tapés en debug reste un point d'attention si des APK debug circulent hors développement.

---

## 6. Qualité des prédictions — axes d'amélioration produit

### 6.1 Le contexte n'influence pas les suggestions pendant la frappe
Le chemin actif `generateDictionarySuggestions()` classe uniquement par fréquence globale ; le bonus n-gram (+50) n'existe que dans `mergeAndRankSuggestions()`, appelé par le mode `MIXED` inutilisé. Après « an ka », taper « ma » devrait favoriser « manjé » si le corpus le suggère — ce n'est pas le cas aujourd'hui. Réintégrer le signal n-gram dans le scoring de frappe est une amélioration à fort impact et faible risque.

### 6.2 Aucun apprentissage utilisateur
- Choisir une suggestion n'augmente pas sa fréquence.
- Un mot tapé hors dictionnaire n'est jamais appris (`addWordToDictionary()` n'a aucun appelant, rien n'est persisté).
- `SuggestionSource.LEARNED` (`BilingualSuggestion.kt:49`) est prévu mais inutilisé.

L'infrastructure existe pourtant : la gamification (`CreoleDictionaryWithUsage`) compte déjà l'usage réel de chaque mot et le persiste. Brancher ce compteur comme signal de personnalisation (`score += k·log(usageCount)`) donnerait un clavier qui s'adapte à chaque utilisateur à moindre coût.

### 6.3 Pas de suggestion à la première lettre
`MIN_WORD_LENGTH = 2` (`SuggestionEngine.kt:23`) et le garde-fou de `findAccentTolerantSuggestions()` (`AccentTolerantMatcher.kt:75`) suppriment toute suggestion à 1 caractère. Or le kréyòl est riche en mots courts très fréquents (`ka` 15 519, `an` 10 729, `sé` 7 177 — têtes du dictionnaire). Autoriser les suggestions dès la première lettre (éventuellement limitées au top fréquence) améliorerait directement la vitesse de saisie.

### 6.4 Correction orthographique trop timide
Le fallback Levenshtein (`SuggestionEngine.kt:561-563`) ne se déclenche que si la recherche préfixe ne renvoie **rien**. Une faute en début de mot (« bpnjou ») est corrigée, mais « bonjoy » ne le sera jamais si « bonjo… » matche autre chose. Pistes : mélanger systématiquement 1-2 candidats de correction quand le meilleur score préfixe est faible, et proposer une auto-correction à la validation (espace) avec possibilité d'annuler — comportement standard des claviers modernes.

### 6.5 Régénérer le modèle n-gram
Cf. 3.1 : tant que `creole_ngrams.json` ne contient pas de clés multi-mots, la prédiction contextuelle reste limitée à un seul mot de contexte. Le pipeline `Dictionnaires/KreyolComplet.py` est l'endroit où produire de vraies entrées bigram/trigram (attention : le déclencheur CI `build-apk.yml` filtre sur `Dictionnaries/**` — chemin mal orthographié — les changements de dictionnaire ne déclenchent pas de build).

### 6.6 Note annexe — documentation
`CLAUDE.md` et le README annoncent ~1 867 mots ; le dictionnaire embarqué en contient 3 680. À mettre à jour.

---

## 7. Recommandations priorisées

### Quick wins (quelques heures, risque faible)

| # | Action | Impact | Réf. |
|---|--------|--------|------|
| 1 | Précalculer les formes normalisées du dictionnaire au chargement + supprimer les regex de `normalize()` | Latence par frappe divisée par un ordre de grandeur | §4.1 |
| 2 | Gater tous les logs du chemin de saisie derrière `BuildConfig.DEBUG` | Confidentialité + perf | §5 |
| 3 | Corriger le bonus préfixe pour comparer les formes normalisées | Classement cohérent des mots accentués | §3.3 |
| 4 | Annuler la coroutine de suggestion précédente à chaque frappe | Fin des suggestions périmées | §3.6 |
| 5 | Sortir les tests `runAccentTolerantTests`/`testCreoleSpecificCases` du démarrage production | Démarrage plus rapide, pas d'écrasement du listener | §4.5 |
| 6 | Supprimer le code mort (`applyCaseToSuggestion`, `calculateCached`, paramètre `levenshteinDistance`, stratégies bigram/trigram si non régénérées) | Lisibilité | §3.4, §3.9, §4.4 |
| 7 | Autoriser les suggestions dès 1 lettre (top fréquence) | Vitesse de saisie | §6.3 |

### Chantiers (impact produit fort, effort plus important)

| # | Action | Impact | Réf. |
|---|--------|--------|------|
| A | Régénérer `creole_ngrams.json` avec clés bigram/trigram (+ corriger le filtre CI `Dictionnaries`) | Prédiction contextuelle réelle | §3.1, §6.5 |
| B | Réintégrer le signal n-gram dans le scoring pendant la frappe | Suggestions contextuelles | §6.1 |
| C | Personnalisation : brancher les compteurs `CreoleDictionaryWithUsage` dans le score + apprendre les mots hors dictionnaire | Clavier qui s'adapte à l'utilisateur | §6.2 |
| D | Implémenter `onUpdateSelection()` et resynchroniser `currentWord` | Fiabilité des suggestions et du remplacement | §3.5 |
| E | Trancher le sort du mode bilingue (réactiver proprement ou retirer le chargement français) | Cohérence + mémoire | §3.2 |
| F | Index préfixe (tri alphabétique + recherche binaire, ou trie) | Scalabilité si le dictionnaire grossit | §4.2 |
| G | Auto-correction à l'espace + corrections mêlées aux suggestions préfixe | Rattrapage des fautes courantes | §6.4 |

---

## 8. Addendum — quick wins appliqués (3 juillet 2026)

Les 7 quick wins ont été appliqués et vérifiés (`compileDebugKotlin`, `assembleDebug` et 51 tests unitaires verts).

| # | Quick win | Ce qui a été fait |
|---|-----------|-------------------|
| 1 | Normalisation précalculée | `AccentTolerantMatcher.normalize()` réécrit avec une table char→char (zéro regex) ; `SuggestionEngine` précalcule les formes normalisées du dictionnaire au chargement (`normalizedWords`) et fait la recherche préfixe dessus, avec arrêt anticipé dès 10 résultats (le dictionnaire étant trié par fréquence). Bonus : `normalize()` gère désormais aussi les majuscules accentuées (`É`→`e`), bug de l'ancienne version. |
| 2 | Logs | Le remplacement massif n'était pas nécessaire : R8 supprime déjà tous les `Log.*` en release (cf. mise à jour du §5). Le spam `SHIFT_REAL_DEBUG` a été supprimé de `InputProcessor` et du service. |
| 3 | Bonus préfixe normalisé | `calculateDictionaryScore()` utilise `AccentTolerantMatcher.startsWith()` : taper « fe » favorise désormais `fè` au même titre que les mots en « fe… ». |
| 4 | Annulation coroutines | `suggestionJob?.cancel()` avant chaque génération dans les 4 points d'entrée (`generateSuggestions`, `generateBilingualSuggestions`, `generateDictionarySuggestions`, `generateContextualSuggestions`). |
| 5 | Tests hors production | `runAccentTolerantTests()` / `testCreoleSpecificCases()` supprimés du service ; `AccentTolerantMatchingTest.kt` (sourceset main) supprimé ; remplacés par un vrai test JVM `AccentTolerantMatcherTest.kt`. |
| 6 | Code mort | Supprimés : `applyCaseToSuggestion()`, cache Levenshtein (`calculateCached`/`clearCache`), paramètre `levenshteinDistance` de `calculateDictionaryScore`, stratégies bigram/trigram de `getNgramSuggestions()` (le modèle n'a que des clés unigrammes), `getSuggestionListener()`, `calculateMatchScore()`/`getDebugInfo()`/`runTests()` du matcher. |
| 7 | Suggestions dès 1 lettre | `MIN_WORD_LENGTH` passé de 2 à 1 : `ka`, `an`, `sé`… sont proposés dès la première frappe. |

**Chantier G (partie classement) appliqué également** : la distance de Levenshtein est désormais propagée jusqu'au score (`LevenshteinDistance` renvoie `(mot, fréquence, distance)`, `calculateDictionaryScore` donne un poids dominant à la distance). Vérifié sur émulateur : « mesli » propose désormais `mèsi` (distance 1) en première position, devant `mésyé`/`mépri` (distance 2, plus fréquents). Le second volet du chantier (auto-correction à l'espace, corrections mêlées aux résultats préfixe) reste à faire.

**Découverte en passant — la suite de tests n'a jamais été verte** (25 échecs préexistants sur `main`) pour deux raisons corrigées au passage :
- `android.util.Log` et `org.json` sont des stubs en test JVM → ajout de `unitTests.returnDefaultValues = true` et de la dépendance `testImplementation 'org.json:json:20240303'` (`app/build.gradle`) ;
- plusieurs assertions de distance étaient fausses (l'auteur supposait que les accents ne comptent pas comme édition : `calculate("mesli","mèsi")` vaut 2, pas 1) → attentes corrigées dans `LevenshteinDistanceTest.kt`.

À noter également : le script `android_keyboard/gradlew` est corrompu (il passe les arguments quotés à Gradle, `eval` manquant) — la CI n'est pas affectée car elle installe Gradle via `gradle-version: 8.7`, mais en local il faut invoquer `java -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain <tâche>` ou régénérer le wrapper.

---

*Rapport généré par analyse statique du code (branche `main`, commit `26e73bd`) et inspection des assets JSON embarqués.*
