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

## Conclusion

Le pipeline de suggestions fonctionne de façon fiable sur l'ensemble des 50 phrases testées, avec une bonne couverture du vocabulaire courant et une gestion correcte des accents. Piste d'amélioration concrète identifiée : enrichir `creole_dict.json` avec `blòké`, `apeti`/`lapeti` et `nwit`/`lannwit`, absents du dictionnaire actuel et donc mal servis par le fallback Levenshtein.
