# Rapport de simulation — Mode « suggestions uniquement » sur le dialogue créole de 982 mots

**Date :** 11 juillet 2026
**Heure :** 12:08 – 12:46 (CEST)
**Testeur :** Claude Code (agent, modèle Fable 5), à la demande de l'utilisateur
**Version testée :** 7.0.2 (`versionCode 70002`)
**Environnement :** émulateur Android, AVD `kreyol_test`, **fenêtre affichée à l'écran** (WSLg) pour observation en direct ; conversation SMS dédiée dans Google Messages.

> ⚠️ **Méthodologie et limites du contenu** : le dialogue est le même texte imaginaire que celui du rapport du 10 juillet (écrit à partir de connaissances générales du kréyòl guadeloupéen, non relu par un locuteur natif). Il sert uniquement de matériau de test — **pas une référence linguistique**.

## Pourquoi ce mode ?

En observant la simulation « frappe humaine réaliste » à l'écran, l'utilisateur a constaté que les taps de suggestion étaient quasiment invisibles : dans ce mode, seul ~1 mot sur 7-8 est committé via la barre de suggestions, et le tap est instantané. Ce nouveau passage inverse le principe : **l'utilisateur simulé ne termine jamais un mot à la main**. Pour chaque mot, il tape les lettres une à une et, dès que le mot visé apparaît parmi les 3 suggestions affichées, il tape dessus. La frappe intégrale n'arrive **que** si le clavier ne propose jamais le mot — ce cas est compté à part et constitue une mesure directe de la **couverture du dictionnaire**.

C'est aussi le protocole le plus exigeant pour le moteur de suggestions : il est interrogé **à chaque lettre** (2 123 lectures sur ce passage, contre 1 068 sur le passage réaliste du 10 juillet).

Note de traçabilité : une réplique du passage « réaliste » du 10 juillet (même dialogue, nouvelle graine) avait été lancée en début de séance, puis **interrompue à la demande de l'utilisateur après 43 messages** au profit du présent mode ; aucune statistique n'en a été conservée.

## Méthodologie

- Même dialogue que le 10 juillet : 134 messages, 982 mots.
- **Aucune faute injectée** : le mot est tapé lettre à lettre, correctement.
- Après chaque lettre, la barre de suggestions est lue (logcat `displaySuggestions`) ; si le mot visé (comparaison insensible à la casse, accents compris) figure parmi les 3 chips affichés, le chip est tapé — le clavier committe alors le mot suivi d'un espace automatique.
- Si le mot n'est jamais apparu après sa dernière lettre : il est compté « jamais proposé » (frappe intégrale par épuisement, espace tapé manuellement).
- La ponctuation collée (virgule) est tapée après le commit du mot ; la casse est laissée à l'auto-capitalisation native ; les latences sont mesurées à chaque lettre.

## Résultats

### Vue d'ensemble : l'utilisateur « strictement suggestions »

| Métrique | Valeur |
|---|---|
| Messages envoyés | 134 |
| Mots traités | 982 |
| Mots committés par tap de suggestion | **806 (82,1 %)** |
| … dont avant la fin du mot (vraie économie) | 667 (67,9 % des mots) |
| … dont seulement à la dernière lettre | 139 |
| Mots jamais proposés (frappe intégrale forcée) | 176 (17,9 %) |
| Lettres tapées | 2 142 / 3 196 (67,0 %) |
| **Lettres économisées** | **1 054 (33,0 %)** — plus les espaces, offerts par le commit |

Un utilisateur qui s'appuie exclusivement sur les suggestions économise donc **un tiers des frappes de lettres**, et la quasi-totalité des espaces.

### Rapidité d'apparition du mot visé

Sur les 667 mots attrapés avant la fin de frappe :

| Lettres tapées avant apparition | 1 | 2 | 3 | 4 | 5 | 6 |
|---|---|---|---|---|---|---|
| Mots | 362 | 171 | 88 | 38 | 7 | 1 |

**Médiane : 1 lettre.** Plus de la moitié des mots attrapés le sont dès la première lettre — l'effet combiné des fréquences du dictionnaire et du modèle n-gram contextuel.

### Position du chip choisi

| 1ᵉʳ chip | 2ᵉ chip | 3ᵉ chip |
|---|---|---|
| 468 (58,1 %) | 244 (30,3 %) | 94 (11,7 %) |

Le classement est bon : quand le mot est disponible, il est en tête plus d'une fois sur deux.

### Disponibilité selon la longueur du mot

| Longueur | Proposé / total | Taux |
|---|---|---|
| 1 lettre | 0 / 44 | 0 % (jamais suggérés — voir ci-dessous) |
| 2 lettres | 365 / 389 | 93,8 % |
| 3 lettres | 153 / 173 | 88,4 % |
| 4 lettres | 157 / 174 | 90,2 % |
| 5 lettres | 69 / 97 | 71,1 % |
| 6 lettres | 48 / 78 | 61,5 % |
| 7-8 lettres | 14 / 24 | 58,3 % |
| 9-10 lettres | 0 / 3 | 0 % |

Les mots d'une lettre (`i`, `a`, `é` — 44 occurrences) ne sont jamais proposés : ils sont absents du dictionnaire et, une fois la lettre tapée, il n'y a de toute façon plus rien à compléter. Pour un utilisateur « strictement suggestions », ce sont les seuls mots où l'espace doit être tapé à la main systématiquement.

### Exactitude des messages envoyés

| Métrique | Brute | Insensible à la casse |
|---|---|---|
| Messages identiques au texte visé | 47 / 134 (35,1 %) | **101 / 134 (75,4 %)** |
| Exactitude caractère (Levenshtein) | 95,08 % | **97,44 %** |

L'écart spectaculaire entre les deux colonnes vient d'un seul phénomène, détaillé ci-dessous : **54 messages (40,3 %) ne diffèrent du texte visé que par la casse du premier mot**.

## Deux découvertes sur le moteur (actionnables)

### 1. Tap de suggestion sous majuscule automatique → mot entier en MAJUSCULES

Quand le premier mot d'un message est committé par tap alors que le shift automatique est encore actif (une seule lettre tapée, en majuscule), `applyCasingPattern()` généralise la casse du préfixe « 100 % majuscule » à toute la suggestion : `Bèl` → **`BÈL`**, `An` → **`AN`**, `Nou` → **`NOU`**, `Mwen` → **`MWEN`**… Le phénomène a touché 54 messages sur 134. Un préfixe d'**une seule** lettre majuscule devrait être traité comme une casse de type titre (première lettre seulement), pas comme du tout-majuscules.

### 2. La correspondance exacte peut être battue par les variantes accentuées

176 mots n'ont jamais été proposés. La plupart sont réellement absents du dictionnaire, mais **5 mots présents dans `creole_dict.json` ne sont jamais apparus dans le top 3**, éjectés par des concurrents plus fréquents via la correspondance tolérante aux accents :

| Mot tapé (fréq. dict) | Top 3 affiché après frappe complète |
|---|---|
| `fo` (60) | `fò`, `fòs`, `fon` |
| `bò` (72) | `bon`, `bout`, `bouch` |
| `red` (15) | `rèd`, `rédé`, `rédé-yo` (`red` classé 5ᵉ en interne) |
| `jòdi` (3), `soley` (2) | variantes plus fréquentes |

Le cas `bò` est le plus parlant : l'utilisateur a **explicitement tapé le `ò` accentué**, mais les mots en `bo-`/`bon-` non accentués, plus fréquents, remplissent les 3 chips. Une **priorité de rang à la correspondance exacte du préfixe (accents compris)** corrigerait les cinq cas d'un coup.

### Mots réellement absents du dictionnaire (candidats à l'enrichissement)

`renmen` (6 occ.), `konsè` (5), `démen` (5), `fatigé` (4), `sérié` (3), `nòwmal` (3), `aswè` (3), ainsi que les mots-outils d'une lettre `i`, `é`, `a`. Rappel : le dictionnaire étant régénéré depuis le dataset Hugging Face par la CI, l'enrichissement durable passe par le dataset (les ajouts manuels dans le JSON seraient écrasés).

## Latence

| | Valeur |
|---|---|
| Mesures (une par lettre tapée) | 2 123 |
| Moyenne | 27,6 ms |
| Médiane | -38,0 ms |
| Écart-type | 223,8 ms |

Toujours sous le plancher de mesure (bruit ADB/horloge hôte-émulateur), alors que ce mode sollicite le moteur **à chaque lettre** — soit le double de lectures du passage du 10 juillet. Aucun ralentissement observé sur les ~40 minutes.

## Anomalies observées (essentiellement des races du harnais)

33 messages diffèrent du texte visé au-delà de la casse. Trois familles, tirées de la comparaison texte envoyé / texte visé :

1. **Mot entièrement perdu** (~14 cas — `ka`, `mwen` ×3, `ni`, `gwadloup`…) : le tap sur le chip est parti pendant un rafraîchissement de la barre et n'a rien committé, le harnais croyant le mot inséré.
2. **Mauvais mot committé** (~13 cas — `pou ou` → `pou ouvriyé`, `té` → `tout`, `lwen` → `lè`…) : la liste s'est rafraîchie entre la lecture du logcat et le tap, un autre mot occupait la position visée.
3. **Espace fusionné** (~11 cas — `an pi` → `anpi`, `ka alé` → `kalé`…) : espace automatique ou manuel perdu dans les mêmes conditions de vitesse.

Ces races sont un artefact de l'automatisation (taps à ~100 ms du rafraîchissement, 806 taps de chips contre 67 au passage réaliste, d'où une exposition ~12× supérieure — 2 anomalies le 10 juillet, ~38 ici, ordres de grandeur cohérents). À noter tout de même : un humain rapide peut rencontrer la variante « mauvais mot committé » dans la vraie vie (tap au moment où la barre se rafraîchit) — un délai minimal de stabilité de la barre avant prise en compte du tap pourrait la neutraliser. Tous ces cas sont **inclus dans les statistiques ci-dessus**, non retirés.

## Captures d'écran

Une capture tous les ~15-20 messages (12 au total, `human3_msg_*.png`) — dossier [`rapport_simulation_suggestions_uniquement_2026-07-11_screenshots/`](./rapport_simulation_suggestions_uniquement_2026-07-11_screenshots/). Contrairement au passage réaliste, chaque mot de ce passage est visible à l'écran en train d'être committé depuis la barre de suggestions.

## Conclusion

En usage « strictement suggestions », le Klavyé Kréyòl committe **82 % des mots par tap** et fait économiser **un tiers des frappes de lettres** (plus tous les espaces), avec un mot visé disponible dès la **première lettre** dans plus de la moitié des cas attrapés en cours de frappe et classé en **1ᵉʳ chip 58 % du temps**. La latence reste indétectable malgré une sollicitation du moteur à chaque lettre.

Le protocole a surtout révélé **deux défauts actionnables du moteur** — la casse tout-majuscules appliquée aux suggestions committées sous shift automatique (40 % des messages touchés dans ce mode), et l'absence de priorité de la correspondance exacte face aux variantes accentuées plus fréquentes — ainsi qu'une **liste concrète de mots à ajouter au dataset** (`renmen`, `konsè`, `démen`, `fatigé`, `sérié`, `nòwmal`, `aswè`…). C'est exactement le type de retour que le passage « frappe parfaite » et le passage « réaliste » ne pouvaient pas produire.
