# Rapport de test — Suggestions du clavier Kréyòl Karukera en conditions réelles

**Date :** 09 juillet 2026
**Heure :** 15:47 – 16:08 (CEST)
**Testeur :** Claude Code (agent), à la demande de l'utilisateur
**Version testée :** 7.0.1 (`versionCode 70001`), compilée depuis `main` à jour

## Environnement de test

| Élément | Détail |
|---|---|
| Émulateur | AVD `kreyol_test` — Pixel 5, Android 14 (API 34), image `google_apis` x86_64 |
| Affichage | Fenêtre visible via WSLg (X11/Wayland) |
| APK | `Potomitan_Kreyol_Keyboard_v7.0.1_debug_2026-07-09.apk`, recompilé pour l'occasion |
| IME | `com.potomitan.kreyolkeyboard/com.example.kreyolkeyboard.KreyolInputMethodServiceRefactored` — activé et sélectionné par défaut |
| Application cible | Google Messages (`com.google.android.apps.messaging`), nouvelle conversation SMS vers un contact fictif `0690654321` |

L'émulateur n'a ni SIM ni réseau : les messages ne partent pas réellement, mais chaque envoi vide le champ de saisie et crée une bulle dans le fil, ce qui simule un véritable échange de messages continu (contexte n-gramme conservé d'un message à l'autre, comme dans une vraie conversation).

## Méthodologie

- **Frappe simulée au niveau touche**, pas par injection de texte : le moteur de suggestions (`SuggestionEngine` / `InputProcessor.processKeyPress`) ne réagit qu'aux appuis sur les vraies touches du clavier custom, pas à un `adb shell input text` qui contournerait entièrement la logique de composition de mot. Les coordonnées de chaque touche ont été calculées à partir des poids définis dans `KeyboardLayoutManager.kt` (`getKeyWeight`), puis validées par calibration empirique (frappe de lettres de contrôle et vérification du texte obtenu).
- Pour chaque phrase : frappe complète des caractères, puis lecture du dernier événement `displaySuggestions appelé avec ...` du logcat — il reflète les suggestions affichées pour le dernier mot en cours de frappe.
- Un point final est ensuite tapé (finalise proprement le mot et réinitialise l'état interne du moteur), puis le message est envoyé, ce qui vide le champ avant la phrase suivante.
- Captures d'écran conservées pour les phrases 1, 2, 3, 10, 20, 30, 40 et 50 (dossier [`rapport_test_clavier_2026-07-09_screenshots/`](./rapport_test_clavier_2026-07-09_screenshots/)).

## Résultats — 50 phrases kréyòl du quotidien

| # | Phrase tapée | Texte final dans le champ | Suggestions affichées (dernier mot) |
|---|---|---|---|
| 1 | bèl bonjou | Bèl bonjou | **1** : bonjou |
| 2 | sa ou fè | Sa ou fè | **5** : fè, fèt, fèmé, fè-nou, fè-y |
| 3 | ka ou fè jòdi a | Ka ou fè jòdi a | **5** : an, adan, a-y, anba, asi |
| 4 | mwen la wi | Mwen la wi | **1** : wi |
| 5 | ou byen | Ou byen | **3** : byen, byendéfwa, byenmèsi |
| 6 | mèsi anpil | Mèsi anpil | **1** : anpil |
| 7 | an ka vini | An ka vini | **2** : vini, vini-zòt |
| 8 | ki lè i yé | Ki lè i yé | **5** : yé, yenki, yè, yépa, yen |
| 9 | ou vlé manjé | Ou vlé manjé | **2** : manjé, manjé-la |
| 10 | mwen fen | Mwen fen | **2** : fen, fenfon |
| 11 | an bwè an tigout dlo | An bwè an tigout dlo | **2** : dlo, dlo-la |
| 12 | koté ou yé | Koté ou yé | **5** : yé, yenki, yè, yépa, yen |
| 13 | an kaz mwen | An kaz mwen | **4** : mwen, mwenmenm, mwens, mwen-la |
| 14 | ou ka travay jòdi a | Ou ka travay jòdi a | **5** : an, adan, a-y, anba, asi |
| 15 | wi mwen ka travay | Wi mwen ka travay | **5** : travayè, travay, travayè-la, travay-la, travay-li |
| 16 | an ka alé lékòl | An ka alé lékòl | **2** : lékòl, lékòl-la |
| 17 | timoun yo la | Timoun yo la | **5** : la, lang, lavi, laplaj, larichès |
| 18 | gran moun ka palé | Gran moun ka palé | **4** : palé, palé-w, palé-la, palé-mwen |
| 19 | fanmi mwen la | Fanmi mwen la | **5** : la, lang, lavi, laplaj, larichès |
| 20 | an renmen'w | An renmen'w | **5** : wouvè, wayayay, woy, wi, woulé |
| 21 | sa ka fè plézi | Sa ka fè plézi | **1** : plézi |
| 22 | bon apeti | Bon apeti | **5** : avèti, apési, pati, péyi, pété |
| 23 | bon nwit | Bon nwit | **5** : nuit, ni, dwèt, wi, pit |
| 24 | dòmi byen | Dòmi byen | **3** : byen, byendéfwa, byenmèsi |
| 25 | rété la | Rété la | **5** : la, lang, lavi, laplaj, larichès |
| 26 | vini isi | Vini isi | **2** : isidan, isi |
| 27 | an ka vann fwi | An ka vann fwi | **2** : fwi, fwitaj |
| 28 | jaden an bel | Jaden an bel | **4** : bèl, bèl-la, bèlté, bel |
| 29 | soley ka bril jòdi a | Soley ka bril jòdi a | **5** : an, adan, a-y, anba, asi |
| 30 | lapli ka tonbé | Lapli ka tonbé | **1** : tonbé |
| 31 | lanmè bel toubannman | Lanmè bel toubannman | **1** : toupannan |
| 32 | an ka péché pwason | An ka péché pwason | **1** : pwason |
| 33 | sé bon manjé | Sé bon manjé | **2** : manjé, manjé-la |
| 34 | dokan an ouvè | Dokan an ouvè | **1** : ouvè |
| 35 | bous mwen vid | Bous mwen vid | **1** : vid |
| 36 | lajan ka manké | Lajan ka manké | **1** : manké |
| 37 | travay la red | Travay la red | **5** : rèd, rédé, rédé-yo, rédé-y, red |
| 38 | an fatigé anpil | An fatigé anpil | **1** : anpil |
| 39 | an bizwen répozé | An bizwen répozé | **5** : dépozé, réponn, pozé, opozé, répondè |
| 40 | telefòn mwen kasé | Telefòn mwen kasé | **1** : kasé |
| 41 | voiti an pann | Voiti an pann | **5** : pannan, pann, pannansitan, pannyé, pannyé-la |
| 42 | wout la blòké | Wout la blòké | **5** : baké, blo, blé, blagé, lòdè |
| 43 | lékòl fini | Lékòl fini | **1** : fini |
| 44 | lévé bonnè | Lévé bonnè | **1** : bonnè |
| 45 | kouché ta | Kouché ta | **5** : tan, tann, tanbou, ta, tata |
| 46 | fè cho jòdi a | Fè cho jòdi a | **5** : an, adan, a-y, anba, asi |
| 47 | van ka soufflé | Van ka soufflé | **3** : souflé, soufè, souplé |
| 48 | mizik la bon | Mizik la bon | **5** : bon, bonjou, bonmaten-la, bondyé, bonswa |
| 49 | dansé kréyòl | Dansé kréyòl | **4** : kréyòl, kréyòl-gwadloup, kréyol, kréyol-la |
| 50 | viv kréyòl gwadloup | Viv kréyòl gwadloup | **2** : gwadloup, gwadloupéyen |

## Observations

- **Bon comportement général** : sur 50 phrases, la frappe a produit le texte attendu à chaque fois (auto-capitalisation en début de phrase correcte, apostrophe et lettres accentuées `é`/`è`/`ò` gérées sans souci), et la majorité des suggestions sont pertinentes et utiles (`bonjou`, `manjé`, `travay`, `lékòl`, `kréyòl`, etc.).
- **Double forme accentuée/non-accentuée** (phrase 49, `kréyòl`) : le moteur suggère à la fois `kréyòl` et `kréyol`, bonne illustration du matching tolérant aux accents (`AccentTolerantMatcher`).
- **Article court `a` jamais suggéré seul** (phrases 3, 14, 29, 46 — "...jòdi a") : le moteur propose systématiquement `an, adan, a-y, anba, asi` mais jamais `a` lui-même, alors qu'il vient d'être tapé tel quel. À vérifier si `a` est absent du dictionnaire ou explicitement filtré (mot trop court ?).
- **Mot non reconnu → suggestions peu pertinentes** :
  - Phrase 22 « bon **apeti** » → suggestions `avèti, apési, pati, péyi, pété`, aucune ne correspond vraiment à « appétit ». **Confirmé** : ni `apeti` ni `lapeti` ne sont dans le dictionnaire.
  - Phrase 42 « wout la **blòké** » → suggestions `baké, blo, blé, blagé, lòdè`, sans jamais proposer `blòké` alors qu'il a été tapé lettre par lettre. **Confirmé** : ni `blòké` ni aucune variante `blok*` ne figurent dans `creole_dict.json` — trou de dictionnaire, pas un bug de scoring.
  - Phrase 23 « bon **nwit** » → suggestions faibles (`nuit, ni, dwèt, wi, pit`), mélangeant un mot français (`nuit`) à des correspondances Levenshtein peu convaincantes. **Confirmé** : ni `nwit` ni `lannwit` (forme kréyòl courante) ne sont dans le dictionnaire.
- **Correction Levenshtein en action** : phrase 31, le mot volontairement approximatif `toubannman` déclenche une correction fuzzy vers `toupannan` (1 seule suggestion, cohérent avec le mécanisme de tolérance aux fautes documenté dans `LevenshteinDistanceTest`).
- **Auto-complétion de mots composés** : plusieurs suggestions proposent des formes agglutinées avec pronom/déterminant (`manjé-la`, `palé-mwen`, `mwen-la`, `travay-li`), reflet du corpus littéraire kréyòl utilisé pour construire le dictionnaire.

## Conclusion (1ʳᵉ passe)

Le pipeline de suggestions fonctionne de façon fiable sur l'ensemble des 50 phrases testées, avec une bonne couverture du vocabulaire courant et une gestion correcte des accents. Piste d'amélioration concrète identifiée : enrichir `creole_dict.json` avec `blòké`, `apeti`/`lapeti` et `nwit`/`lannwit`, absents du dictionnaire actuel et donc mal servis par le fallback Levenshtein.

---

## Addendum — Rapidité et pertinence détaillée (09 juillet 2026, 20:53 CEST)

Second passage sur le même APK 7.0.1, cette fois avec l'émulateur affiché en fenêtre (WSLg) et deux angles d'analyse supplémentaires demandés : la **rapidité** des suggestions et la **progression caractère par caractère** de la pertinence sur la première phrase. Même méthodologie de frappe (taps réels sur le clavier custom), nouveau fil de conversation avec un contact fictif différent (`0690999888`) pour repartir sur un historique n-gramme propre.

### Rapidité — méthode et limite de mesure

Pour chaque phrase, le script clique sur la dernière touche du dernier mot en notant l'horodatage juste avant le tap (horloge hôte Python), puis sonde le logcat de l'émulateur (`adb logcat -v epoch`) jusqu'à l'apparition de la ligne `displaySuggestions appelé avec ...` correspondante, et calcule le delta.

**Limite importante** : cette mesure inclut l'aller-retour ADB (hôte WSL → émulateur) en plus du calcul réel côté clavier, et dépend de la synchronisation d'horloge hôte/émulateur. Sur les 50 phrases mesurées :

- **Moyenne : -41 ms, médiane : -61 ms, écart-type : 54 ms, plage : -91 ms à +165 ms**

Une partie des valeurs est **négative**, ce qui est physiquement impossible pour un vrai délai de calcul — cela révèle simplement que le bruit de mesure (gigue d'horloge + aller-retour ADB, de l'ordre de ±100 ms) est **supérieur au temps de calcul réel des suggestions**. Autrement dit : le calcul lui-même est trop rapide pour être isolé par cette méthode de mesure côté hôte. C'est en soi une conclusion positive et cohérente avec les correctifs de performance déjà appliqués le 3 juillet 2026 (normalisation précalculée du dictionnaire, arrêt anticipé de la recherche préfixe — voir le rapport d'audit du moteur de suggestions ci-dessus, §4.1 et §8) : aucun ralentissement perceptible n'a été observé sur les 3 680 mots du dictionnaire, y compris sur les phrases déclenchant un fallback Levenshtein (ex. phrase 31, `toubannman`, delta mesuré : +136 ms — dans le même ordre de grandeur bruité que le reste).

Seul un point dépasse nettement le lot : la frappe de l'**espace** après « Bèl » (voir tableau de progression ci-dessous) a montré +319 ms — à surveiller si le phénomène se reproduit sur d'autres mesures, mais un seul échantillon ne permet pas de conclure à une régression.

**Verdict rapidité** : aucun lag perceptible constaté sur l'émulateur (Pixel 5 / Android 14) — les suggestions sont prêtes essentiellement au moment où la frappe est traitée, sans décalage visible à l'écran ni dans les captures.

<details>
<summary>Détail des 50 latences mesurées</summary>

| # | Phrase | Latence mesurée (dernier mot) |
|---|---|---|
| 1 | bèl bonjou | -39 ms |
| 2 | sa ou fè | +7 ms |
| 3 | ka ou fè jòdi a | -37 ms |
| 4 | mwen la wi | -81 ms |
| 5 | ou byen | +165 ms |
| 6 | mèsi anpil | -6 ms |
| 7 | an ka vini | -23 ms |
| 8 | ki lè i yé | -12 ms |
| 9 | ou vlé manjé | -74 ms |
| 10 | mwen fen | -73 ms |
| 11 | an bwè an tigout dlo | -52 ms |
| 12 | koté ou yé | -73 ms |
| 13 | an kaz mwen | -58 ms |
| 14 | ou ka travay jòdi a | -78 ms |
| 15 | wi mwen ka travay | -85 ms |
| 16 | an ka alé lékòl | -79 ms |
| 17 | timoun yo la | -52 ms |
| 18 | gran moun ka palé | -60 ms |
| 19 | fanmi mwen la | -49 ms |
| 20 | an renmen'w | -56 ms |
| 21 | sa ka fè plézi | -91 ms |
| 22 | bon apeti | +27 ms |
| 23 | bon nwit | +14 ms |
| 24 | dòmi byen | -67 ms |
| 25 | rété la | -77 ms |
| 26 | vini isi | -64 ms |
| 27 | an ka vann fwi | -41 ms |
| 28 | jaden an bel | -65 ms |
| 29 | soley ka bril jòdi a | -87 ms |
| 30 | lapli ka tonbé | -68 ms |
| 31 | lanmè bel toubannman | +136 ms |
| 32 | an ka péché pwason | -66 ms |
| 33 | sé bon manjé | -68 ms |
| 34 | dokan an ouvè | -75 ms |
| 35 | bous mwen vid | -63 ms |
| 36 | lajan ka manké | -18 ms |
| 37 | travay la red | -63 ms |
| 38 | an fatigé anpil | -44 ms |
| 39 | an bizwen répozé | +35 ms |
| 40 | telefòn mwen kasé | -76 ms |
| 41 | voiti an pann | -74 ms |
| 42 | wout la blòké | -51 ms |
| 43 | lékòl fini | -84 ms |
| 44 | lévé bonnè | -44 ms |
| 45 | kouché ta | -13 ms |
| 46 | fè cho jòdi a | -75 ms |
| 47 | van ka soufflé | +7 ms |
| 48 | mizik la bon | -79 ms |
| 49 | dansé kréyòl | -83 ms |
| 50 | viv kréyòl gwadloup | +96 ms |

</details>

### Pertinence — progression caractère par caractère sur « Bèl bonjou »

Contrairement au reste du test (qui ne capture que l'état final des suggestions par mot), la première phrase a été tapée avec une capture après **chaque caractère individuel**, pour observer concrètement comment les propositions évoluent pendant la frappe :

| Caractère tapé | Champ | Latence | Suggestions affichées |
|---|---|---|---|
| `b` | B | +18 ms | BA, BYEN, BÈL, BITEN, BON |
| `è` | Bè | -20 ms | Bèl, Bèf, Bésé, Bèf-la, Bennyé |
| `l` | Bèl | +3 ms | Bèl, Bèl-la, Bèlté, Bel |
| ` ` | Bèl  | +319 ms |  |
| `b` | Bèl b | -25 ms | ba, byen, bèl, biten, bon |
| `o` | Bèl bo | +14 ms | bon, bout, bouch, boug, bouko |
| `n` | Bèl bon | +36 ms | bon, bonjou, bonmaten-la, bondyé, bonswa |
| `j` | Bèl bonj | +45 ms | bonjou |
| `o` | Bèl bonjo | +17 ms | bonjou |
| `u` | Bèl bonjou | -39 ms | bonjou |

Observations :

- **Suggestions dès la 1ʳᵉ lettre** : taper juste « b » propose déjà 5 candidats (`ba, byen, bèl, biten, bon`), conforme au quick win « suggestions dès 1 lettre » du 3 juillet 2026 (`MIN_WORD_LENGTH = 1`).
- **Anomalie de casse repérée** : au tout premier caractère (« B » majuscule en début de message), les suggestions s'affichent **entièrement en majuscules** (`BA, BYEN, BÈL, BITEN, BON`) au lieu d'une casse capitalisée normale (`Ba, Byen, Bèl...`). Dès le deuxième caractère (« Bè »), la casse redevient correcte (`Bèl, Bèf, Bésé...`). Cela suggère que `applyCasingPattern()` traite un mot d'une seule lettre majuscule comme un motif « tout en majuscules » plutôt que « première lettre capitalisée » — cas limite mineur mais visible dès la toute première frappe d'un message.
- **Convergence rapide et pertinente** : après « bonj » (4 lettres du 2ᵉ mot), une seule suggestion reste (`bonjou`), et elle reste stable jusqu'à la fin du mot — bon signal de précision une fois le préfixe suffisamment discriminant.
- **Confirmation visuelle de la limite à 3 boutons** : la capture d'écran après « Bèl bon » montre bien seulement 3 boutons affichés (`bon`, `bonjou`, `bonmaten-la`) alors que le logcat interne en scorait 5 (`+ bondyé, bonswa`) — cohérent avec la limite documentée dans `CLAUDE.md` (« 3 suggestions affichées, 5 scorées en interne »).

Captures d'écran de la progression : [`progression_05.png`](./rapport_test_clavier_2026-07-09_screenshots/progression_05.png) (« Bèl b »), [`progression_07.png`](./rapport_test_clavier_2026-07-09_screenshots/progression_07.png) (« Bèl bon »), [`progression_10.png`](./rapport_test_clavier_2026-07-09_screenshots/progression_10.png) (« Bèl bonjou »).

### Conclusion mise à jour

Les deux nouveaux axes confirment et complètent la première passe :

- **Rapidité** : pas de lag perceptible ; le temps de calcul des suggestions est en dessous du plancher de mesure de cette méthode (~100 ms de bruit ADB/horloge), ce qui valide indirectement les optimisations de performance appliquées début juillet.
- **Pertinence** : le comportement caractère par caractère est cohérent et converge vite vers la bonne suggestion, avec un boîtier UI qui respecte bien la limite de 3 boutons. Un petit bug de casse est identifié (majuscules intempestives sur la toute première suggestion d'un message) — cosmétique, à corriger dans `applyCasingPattern()`.
- Les 3 trous de dictionnaire identifiés en 1ʳᵉ passe (`blòké`, `apeti`/`lapeti`, `nwit`/`lannwit`) restent d'actualité sur ce second passage (mêmes suggestions non pertinentes reproduites à l'identique).
