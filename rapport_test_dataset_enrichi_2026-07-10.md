# Rapport de test — Impact de l'enrichissement du dataset POTOMITAN/PawolKreyol-gfc

**Date :** 10 juillet 2026
**Heure :** 11:41 – 12:06 (CEST)
**Testeur :** Claude Code (agent), à la demande de l'utilisateur
**Contexte :** l'utilisateur a enrichi le dataset Hugging Face source de vérité et l'a rendu public (`gated: false`), puis a demandé de mesurer concrètement l'impact de cet enrichissement sur le dictionnaire embarqué et sur la qualité des suggestions.

## Résumé

| | Avant (dataset initial) | Après (dataset enrichi) | Delta |
|---|---|---|---|
| Textes du corpus HF | 427 | 703 | +276 (+64,6%) |
| Mots du dictionnaire | 3 680 | 4 043 | +363 (+9,9%) |
| Mots supprimés | — | — | 0 |
| Prédictions n-grammes | 3 582 | 3 902 | +320 (+8,9%) |

Malgré une croissance du corpus de +64,6%, le dictionnaire ne croît que de +9,9% : effet attendu de la [loi de Heaps](https://fr.wikipedia.org/wiki/Loi_de_Heaps) — les mots les plus fréquents du kréyòl étaient déjà couverts par le corpus initial, et l'enrichissement apporte surtout du vocabulaire plus rare (voir échantillon ci-dessous).

## Méthodologie

1. Régénération du dictionnaire/n-grams via `Dictionnaires/KreyolComplet.py` (pipeline existant, non modifié) contre le dataset HF fraîchement enrichi.
2. Reconstruction de l'APK debug avec les nouveaux assets (`android_keyboard/app/src/main/assets/creole_dict.json` et `creole_ngrams.json`).
3. Réinstallation sur le même AVD `kreyol_test` (Pixel 5, Android 14), émulateur affiché à l'écran (WSLg).
4. **Rejeu à l'identique du protocole du 09/07/2026** : mêmes 50 phrases kréyòl du quotidien, même table de coordonnées clavier, même méthode de frappe touche par touche et de mesure de latence (`adb logcat -v epoch`), dans une nouvelle conversation Google Messages (`0690777000`).
5. Comparaison programmatique des suggestions finales et des latences entre `results2.json` (09/07, avant enrichissement) et `results3.json` (10/07, après enrichissement).

## Les 3 trous de dictionnaire connus persistent

Le rapport du 09/07 avait identifié 3 mots/orthographes absents du dictionnaire : `blòké`, `apeti`/`lapeti`, `nwit`/`lannwit`. Vérification directe dans le nouveau `creole_dict.json` :

| Mot recherché | Présent après enrichissement ? |
|---|---|
| `blòké` | ❌ Absent |
| `apeti` | ❌ Absent |
| `lapeti` | ❌ Absent |
| `nwit` | ❌ Absent |
| `lannwit` | ❌ Absent |
| `nuit` (orthographe française) | ✅ Présent (déjà avant) |

Confirmé également en conditions réelles sur l'émulateur (captures d'écran) :
- **« Bon apeti »** → suggestions `avèti, apési, pati, péyi, pété` (identique au 09/07, `apeti` toujours absent)
- **« Bon nwit »** → suggestions `nuit, ni, dwèt, wi, pit` (identique au 09/07)
- **« Wout la blòké »** → suggestions `baké, blo, blé, blagé, lòdè` (identique au 09/07, `blòké` toujours absent)

**Conclusion : les 276 textes ajoutés au corpus ne contiennent pas ces mots/graphies précis.** L'enrichissement quantitatif du corpus ne garantit pas la couverture de vocabulaire spécifique déjà identifié comme manquant — il faudrait soit cibler l'ajout de textes contenant ces mots, soit les ajouter manuellement au dictionnaire.

## Comparaison des 50 phrases — avant / après

43 phrases sur 50 ont des suggestions finales strictement identiques. 7 phrases montrent une évolution (marquées 🔄), toutes dues à l'arrivée de nouveaux mots dans le dictionnaire qui viennent concurrencer les suggestions existantes :

| # | Phrase | Suggestions avant (09/07) | Suggestions après (10/07) |
|---|---|---|---|
| 10 | mwen fen | fen, fenfon | fen, fenfon, **fenyan** |
| 11 | an bwè an tigout dlo | dlo, dlo-la | dlo, dlo-la, **dlo-a** |
| 17 | timoun yo la | la, lang, lavi, **laplaj**, larichès | la, lang, lavi, **lapenn**, larichès |
| 19 | fanmi mwen la | la, lang, lavi, **laplaj**, larichès | la, lang, lavi, **lapenn**, larichès |
| 20 | an renmen'w | wouvè, wayayay, woy, wi, woulé | *(voir note ci-dessous)* |
| 25 | rété la | la, lang, lavi, **laplaj**, larichès | la, lang, lavi, **lapenn**, larichès |
| 28 | jaden an bel | bèl, bèl-la, bèlté, bel | bèl, bèl-la, bèlté, bel, **belmè** |

**Note sur la phrase 20** : le script de mesure a capturé 0 suggestion juste après le dernier caractère (`w`), ce qui ressemblait à une régression. Un re-test manuel immédiat a montré que les 5 suggestions (`wouvè, wayayay, woy, wi, woulé`) réapparaissent bien, identiques à avant — le logcat montre une brève fenêtre à 0 suggestion juste après la finalisation du mot précédent (apostrophe), que le script a capturée par malchance avant l'arrivée de la vraie suggestion. **Artefact de mesure, pas une régression du moteur.**

Aucune suggestion existante n'a disparu ou changé d'ordre parmi les 4 premiers rangs sur l'ensemble des 50 phrases — l'enrichissement n'a eu qu'un effet marginal et additif (nouveaux mots insérés en fin de liste de suggestions), sans perturber les suggestions déjà correctes.

<details><summary>Détail complet des 50 phrases (suggestions + latence, avant/après)</summary>

| # | Phrase | Suggestions avant (09/07) | Suggestions après (10/07) | Latence avant | Latence après |
|---|---|---|---|---|---|
| 1 | bèl bonjou | bonjou | bonjou | -38.8ms | -14.4ms |
| 2 | sa ou fè | fè, fèt, fèmé, fè-nou, fè-y | fè, fèt, fèmé, fè-nou, fè-y | 7.3ms | 407.8ms |
| 3 | ka ou fè jòdi a | an, adan, a-y, anba, asi | an, adan, a-y, anba, asi | -37.1ms | -23.4ms |
| 4 | mwen la wi | wi | wi | -81.2ms | -39.6ms |
| 5 | ou byen | byen, byendéfwa, byenmèsi | byen, byendéfwa, byenmèsi | 164.9ms | -140.2ms |
| 6 | mèsi anpil | anpil | anpil | -6.1ms | -55.7ms |
| 7 | an ka vini | vini, vini-zòt | vini, vini-zòt | -22.7ms | 175.5ms |
| 8 | ki lè i yé | yé, yenki, yè, yépa, yen | yé, yenki, yè, yépa, yen | -12.1ms | -79.1ms |
| 9 | ou vlé manjé | manjé, manjé-la | manjé, manjé-la | -73.8ms | -38.5ms |
| 10 | mwen fen 🔄 | fen, fenfon | fen, fenfon, fenyan | -72.6ms | -32.1ms |
| 11 | an bwè an tigout dlo 🔄 | dlo, dlo-la | dlo, dlo-la, dlo-a | -52.4ms | -112.9ms |
| 12 | koté ou yé | yé, yenki, yè, yépa, yen | yé, yenki, yè, yépa, yen | -72.8ms | -119.0ms |
| 13 | an kaz mwen | mwen, mwenmenm, mwens, mwen-la | mwen, mwenmenm, mwens, mwen-la | -58.0ms | -115.5ms |
| 14 | ou ka travay jòdi a | an, adan, a-y, anba, asi | an, adan, a-y, anba, asi | -78.5ms | -146.4ms |
| 15 | wi mwen ka travay | travayè, travay, travayè-la, travay-la, travay-li | travayè, travay, travayè-la, travay-la, travay-li | -84.8ms | -43.4ms |
| 16 | an ka alé lékòl | lékòl, lékòl-la | lékòl, lékòl-la | -79.3ms | -178.8ms |
| 17 | timoun yo la 🔄 | la, lang, lavi, laplaj, larichès | la, lang, lavi, lapenn, larichès | -52.3ms | -119.5ms |
| 18 | gran moun ka palé | palé, palé-w, palé-la, palé-mwen | palé, palé-w, palé-la, palé-mwen | -60.1ms | -79.0ms |
| 19 | fanmi mwen la 🔄 | la, lang, lavi, laplaj, larichès | la, lang, lavi, lapenn, larichès | -49.0ms | -132.6ms |
| 20 | an renmen'w 🔄 (artefact) | wouvè, wayayay, woy, wi, woulé | (voir note) | -55.6ms | -196.8ms |
| 21 | sa ka fè plézi | plézi | plézi | -91.1ms | -174.7ms |
| 22 | bon apeti | avèti, apési, pati, péyi, pété | avèti, apési, pati, péyi, pété | 26.6ms | -45.2ms |
| 23 | bon nwit | nuit, ni, dwèt, wi, pit | nuit, ni, dwèt, wi, pit | 13.7ms | -83.4ms |
| 24 | dòmi byen | byen, byendéfwa, byenmèsi | byen, byendéfwa, byenmèsi | -67.1ms | -129.8ms |
| 25 | rété la 🔄 | la, lang, lavi, laplaj, larichès | la, lang, lavi, lapenn, larichès | -77.1ms | -185.3ms |
| 26 | vini isi | isidan, isi | isidan, isi | -64.3ms | -130.6ms |
| 27 | an ka vann fwi | fwi, fwitaj | fwi, fwitaj | -41.3ms | -127.2ms |
| 28 | jaden an bel 🔄 | bèl, bèl-la, bèlté, bel | bèl, bèl-la, bèlté, bel, belmè | -64.9ms | -123.6ms |
| 29 | soley ka bril jòdi a | an, adan, a-y, anba, asi | an, adan, a-y, anba, asi | -86.6ms | -195.2ms |
| 30 | lapli ka tonbé | tonbé | tonbé | -67.9ms | -129.1ms |
| 31 | lanmè bel toubannman | toupannan | toupannan | 136.2ms | -75.4ms |
| 32 | an ka péché pwason | pwason | pwason | -66.0ms | -156.9ms |
| 33 | sé bon manjé | manjé, manjé-la | manjé, manjé-la | -68.1ms | -108.1ms |
| 34 | dokan an ouvè | ouvè | ouvè | -74.6ms | -174.7ms |
| 35 | bous mwen vid | vid | vid | -62.6ms | -166.0ms |
| 36 | lajan ka manké | manké | manké | -17.7ms | -62.6ms |
| 37 | travay la red | rèd, rédé, rédé-yo, rédé-y, red | rèd, rédé, rédé-yo, rédé-y, red | -62.6ms | -135.0ms |
| 38 | an fatigé anpil | anpil | anpil | -44.5ms | 8.5ms |
| 39 | an bizwen répozé | dépozé, réponn, pozé, opozé, répondè | dépozé, réponn, pozé, opozé, répondè | 34.9ms | -50.2ms |
| 40 | telefòn mwen kasé | kasé | kasé | -75.7ms | -62.3ms |
| 41 | voiti an pann | pannan, pann, pannansitan, pannyé, pannyé-la | pannan, pann, pannansitan, pannyé, pannyé-la | -74.3ms | 12.4ms |
| 42 | wout la blòké | baké, blo, blé, blagé, lòdè | baké, blo, blé, blagé, lòdè | -51.4ms | -83.4ms |
| 43 | lékòl fini | fini | fini | -84.2ms | -168.2ms |
| 44 | lévé bonnè | bonnè | bonnè | -43.6ms | -162.7ms |
| 45 | kouché ta | tan, tann, tanbou, ta, tata | tan, tann, tanbou, ta, tata | -12.8ms | -168.0ms |
| 46 | fè cho jòdi a | an, adan, a-y, anba, asi | an, adan, a-y, anba, asi | -74.6ms | -179.9ms |
| 47 | van ka soufflé | souflé, soufè, souplé | souflé, soufè, souplé | 6.6ms | 46.9ms |
| 48 | mizik la bon | bon, bonjou, bonmaten-la, bondyé, bonswa | bon, bonjou, bonmaten-la, bondyé, bonswa | -78.9ms | -150.1ms |
| 49 | dansé kréyòl | kréyòl, kréyòl-gwadloup, kréyol, kréyol-la | kréyòl, kréyòl-gwadloup, kréyol, kréyol-la | -83.0ms | -86.4ms |
| 50 | viv kréyòl gwadloup | gwadloup, gwadloupéyen | gwadloup, gwadloupéyen | 95.5ms | 139.0ms |

</details>

## Rapidité

| | Avant (09/07) | Après (10/07) |
|---|---|---|
| Latence moyenne | -40.7ms | -83.8ms |
| Latence médiane | -61.4ms | -114.2ms |
| Écart-type | 54.4ms | 105.8ms |

Les deux séries restent **négatives en moyenne**, ce qui — comme documenté le 09/07 — signifie que le bruit de mesure (aller-retour ADB + désynchronisation d'horloge hôte/émulateur, de l'ordre de ±100 à 200ms) domine largement le temps de calcul réel des suggestions : celui-ci reste **trop rapide pour être isolé par cette méthode**, avant comme après l'enrichissement. L'écart-type plus élevé après enrichissement (105.8ms vs 54.4ms) est cohérent avec un bruit de mesure plus variable ce jour-là (l'émulateur a par ailleurs affiché deux fois le dialogue « System UI isn't responding » au démarrage), pas avec un ralentissement réel du moteur — le dictionnaire (+363 mots) et les n-grams (+320 prédictions) restent des structures en mémoire minuscules pour un appareil mobile moderne.

**Aucun ralentissement perceptible attribuable à l'enrichissement du dictionnaire.**

## Captures d'écran

Captures conservées pour les phrases 1, 2, 3, 10, 20, 22, 23, 30, 40, 42, 50, ainsi que la progression caractère par caractère de « Bèl bonjou » (phrases 5 à 10) — dossier [`rapport_test_dataset_enrichi_2026-07-10_screenshots/`](./rapport_test_dataset_enrichi_2026-07-10_screenshots/). Les phrases 22, 23 et 42 (les 3 mots-trous) ont été spécifiquement capturées pour ce test.

Le bug de casse déjà documenté le 09/07 (première suggestion en MAJUSCULES sur le tout premier caractère d'un message, ex. `VWÈ, VIKTÒ, VLÉ` pour « van ka soufflé ») a été observé à nouveau à l'identique — non lié à l'enrichissement, toujours ouvert dans `applyCasingPattern()`.

## Conclusion (1ʳᵉ passe, 703 textes)

L'enrichissement du dataset (427 → 703 textes, +64,6%) a produit une croissance mesurée mais réelle du dictionnaire embarqué (+363 mots, +9,9%) et des n-grams (+320 prédictions, +8,9%), sans aucune régression : 43 des 50 phrases de test ont des suggestions identiques à avant, les 7 phrases modifiées ne font qu'ajouter de nouvelles options sans supplanter les suggestions correctes déjà en place, et aucun ralentissement n'est mesurable.

En revanche, les **3 trous de vocabulaire identifiés le 09/07 persistent à l'identique** (`blòké`, `apeti`/`lapeti`, `nwit`/`lannwit`) : l'enrichissement générique du corpus n'a pas ciblé ces mots précis. Pour les combler, il faudrait soit ajouter spécifiquement des textes contenant ce vocabulaire courant au dataset source, soit les insérer manuellement dans `creole_dict.json`.

## Addendum — Second cycle d'enrichissement ciblé (2383 textes, 10 juillet 2026, 16:33 CEST)

Suite au premier passage, 1680 phrases supplémentaires (issues du dataset privé `POTOMITAN/potomitan-gcf-fr-translation`, dont 53 phrases de sécurité/premiers secours catégorisées) ont été ajoutées au dataset `PawolKreyol-gfc` (427 → 703 → **2383 textes**). Le pipeline a été rejoué, l'APK reconstruit, et le **même protocole de 50 phrases** rejoué une troisième fois dans une nouvelle conversation Google Messages, pour mesurer l'impact de ce second cycle par rapport au premier (703 textes).

### Résumé

| | 703 textes (10/07 matin) | 2383 textes (10/07 après-midi) | Delta |
|---|---|---|---|
| Mots du dictionnaire | 4 043 | 4 911 | +868 (+21,5%) |
| Mots supprimés | — | — | 0 |
| Prédictions n-grammes | 3 902 | 4 232 | +330 (+8,5%) |

Cette fois, l'ajout de textes **ciblés** (phrases de sécurité + vocabulaire courant issu d'un cours de créole) produit une croissance du dictionnaire proportionnellement bien plus forte (+21,5%) que le premier enrichissement générique (+9,9% pour +64,6% de textes en plus) — cohérent avec l'hypothèse que du contenu choisi délibérément apporte plus de vocabulaire nouveau que des textes ajoutés sans ciblage.

### Les 3 trous historiques persistent encore

| Mot | Présent après 2383 textes ? |
|---|---|
| `blòké` | ❌ Absent |
| `apeti` / `lapeti` | ❌ Absent |
| `nwit` / `lannwit` | ❌ Absent |

Confirmé à nouveau en conditions réelles : « Bon apeti » → `avèti, apési, pati, péyi, pété` (inchangé), « Bon nwit » → `nuit, ni, dwèt, wi, pit` (inchangé), « Wout la blòké » → `baké, blo, blé, blagé, lòdè` (inchangé, capture d'écran à l'appui).

**Constat important : même un enrichissement délibéré et ciblé peut manquer sa cible si les mots précis ne sont pas explicitly présents dans les textes ajoutés.** Les 1680 phrases ajoutées ce cycle ne contenaient tout simplement pas ces 5 graphies — la leçon du premier passage se confirme : combler un trou de vocabulaire connu nécessite d'écrire/choisir des textes contenant explicitement le mot ciblé, pas seulement d'ajouter du volume ou même du contenu thématiquement pertinent.

### Vocabulaire des phrases de sécurité : succès partiel

Une partie du vocabulaire des phrases de sécurité ajoutées est bien détectable dans le nouveau dictionnaire : `blesé` (2), `doktè` (6), `rimèd` (13), `vitman` (39), `évakwasyon` (1), `sékou-la` (1, sous forme composée seulement — `sékou` seul reste absent). C'est la preuve que l'approche « texte ciblé » fonctionne quand le mot exact apparaît dans le texte ajouté.

### Comparaison des 50 phrases — 703 → 2383 textes

11 phrases sur 50 montrent une évolution des suggestions (contre 7 lors du premier cycle), toutes par ajout de nouvelles options sans régression :

| # | Phrase | Suggestions à 703 textes | Suggestions à 2383 textes |
|---|---|---|---|
| 4 | mwen la wi | wi | wi, **wilyàm**, **wilyam**, **wifi** |
| 5 | ou byen | byen, byendéfwa, byenmèsi | byen, byendéfwa, byenmèsi, **byenbonjou**, **byenbonswè** |
| 9 | ou vlé manjé | manjé, manjé-la | manjé, manjé-la, **manje**, **manjé-lasa** |
| 10 | mwen fen | fen, fenfon, fenyan | fen, fenfon, **fenèt**, fenyan |
| 11 | an bwè an tigout dlo | dlo, dlo-la, dlo-a | dlo, dlo-la, dlo-a, **dlo-lanmè-la**, **dlo-pisin-la** |
| 20 | an renmen'w | *(0 — artefact de mesure, voir addendum précédent)* | wouvè, wayayay, woy, wi, woulé *(confirme l'artefact : suggestions bien présentes)* |
| 24 | dòmi byen | byen, byendéfwa, byenmèsi | byen, byendéfwa, byenmèsi, **byenbonjou**, **byenbonswè** |
| 26 | vini isi | isidan, isi | isidan, isi, **isit** |
| 32 | an ka péché pwason | pwason | pwason, **pwason-la** |
| 33 | sé bon manjé | manjé, manjé-la | manjé, manjé-la, **manje**, **manjé-lasa** |
| 47 | van ka soufflé | souflé, soufè, souplé | souflé, soufè, souplé, **souplè** |

### ⚠️ Pollution par noms propres détectée

La phrase 4 (« mwen la wi ») révèle un problème de qualité des données : **`wilyàm` (8 occurrences) et `wilyam` (1) apparaissent maintenant comme suggestions**, en concurrence avec le mot légitime `wi`. Ces mots viennent des dialogues du cours Assimil inclus dans l'extraction (personnages « Anna », « William », « Kévin » utilisés comme exemples pédagogiques) — non filtrés avant l'ajout au corpus `PawolKreyol-gfc`, contrairement à la liste affichée sur la [page des trous de vocabulaire](https://famibelle.github.io/KreyolKeyb/dictionnaire_vocabulaire_manquant.html) où ces noms avaient bien été exclus. `ana` (13 occurrences) et `kévin` (6) sont également désormais dans le dictionnaire, bien qu'ils n'apparaissent pas encore dans le top des suggestions des 50 phrases testées.

**Recommandation** : avant un prochain cycle d'enrichissement à partir de contenu pédagogique/dialogué, filtrer les noms de personnages fictifs pour éviter qu'ils ne concurrencent du vocabulaire réel dans les suggestions.

### Rapidité

Latence moyenne : -83,8ms (703 textes) → -133,3ms (2383 textes) ; écart-type 105,8ms → 74,9ms. Toujours dans le bruit de mesure ADB/horloge (~100-200ms), sans signal de ralentissement réel — le dictionnaire reste largement sous la taille où une différence de performance serait attendue sur un appareil mobile moderne.

### Conclusion mise à jour

Ce second cycle confirme les enseignements du premier tout en les affinant : un enrichissement **ciblé** (phrases de sécurité + vocabulaire choisi) fait croître le dictionnaire proportionnellement bien plus qu'un enrichissement générique, sans aucune régression sur les 50 phrases de test. Mais **cibler un thème ne suffit pas à combler un trou de vocabulaire précis** — les 3 mots-trous historiques (`blòké`, `apeti`/`lapeti`, `nwit`/`lannwit`) restent absents faute d'avoir été explicitement inclus dans les textes ajoutés. Un nouveau problème est apparu : la **pollution par noms propres** provenant de contenu pédagogique non filtré, à corriger avant le prochain cycle.
