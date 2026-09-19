# Test de Gboard avec sa disposition « Créole guadeloupéen »

*Réalisé le 19 septembre 2026. Ce rapport consigne des observations, pas un classement.*

## Conditions

- **Appareil** : Samsung Galaxy A21s (SM-A217F), Android 12, écran 720 x 1600.
- **Gboard** : 18.2.4.969776716 (release, arm64-v8a), installé le jour même depuis le Play Store (le téléphone n'en avait pas). Une première série sur l'émulateur Android 14 avec Gboard 12.4.05 (préinstallé) donne les mêmes constats de fond, sauf sur `maké` (voir plus bas).
- **Disposition** : « Créole guadeloupéen » AZERTY, ajoutée dans Gboard > Langues. La barre d'espace affiche « Kwéyòl ».
- **Saisie** : touches tapées à l'écran par `adb shell input tap`, champ de message de Samsung Messages, aucun envoi. Texte lu par l'arbre d'accessibilité ; barre de suggestions lue par OCR (les accents y sont mal reconnus, vérifiés à l'œil quand ils comptaient).
- **Micro** : accès refusé à Gboard (pas d'autorisation donnée). La dictée n'a donc pas été testée.

## Ce que Gboard fait avec cette disposition

| Point | Constat |
|---|---|
| Lettre ò | Appui long sur `o` : `Ò` est le premier choix, puis `Ô`, `Œ`, `°`, `9`. Pas de touche dédiée. |
| é et è | Appui long sur `e` : `É` puis `È`, `Ê`, `Ë`. Pas de touches dédiées. |
| Suggestions de mots | Oui, en créole : `pyeb` → Pyébwa ; `lanm` → Lanmou ; `mate` → Matematik ; `bonj` → Bonjou ; `maman` → Manman ; `kab` → Kabrit. Pas de `maké` proposé pendant la frappe de `mak` (Man, Mal). |
| Prédiction du mot suivant | Oui. Après « nou » : té, kay, tini. Après « nou ka » : fè, di, twouvé. Après « an kay » : enme, vle, kapab (sans accents). Après « sa ou » : té, lé, ka. |
| Correction automatique | Oui, et elle rétablit les accents : `kreyol` → kréyòl, `pale` → palé, `make` → maké. Elle remplace aussi des mots (voir ci-dessous). Le bouton d'annulation de la barre défait la correction. |
| Saisie glissée | Oui avec la disposition créole : `nou` → Nou, `kreyol` → Kréyòl, `pale` → Palè (Palé proposé en alternative). |
| Soulignement des fautes | Aucun trait observé, même sous un mot inventé (`xyzqk`). Le correcteur système de ce téléphone est celui de Samsung. Gboard n'installe pas de correcteur système. |
| Dictée vocale | Non testée (micro non autorisé). |

## Douze phrases tapées sans accent

Les douze phrases du simulateur (corpus du clavier), tapées lettre à lettre **sans accent ni ponctuation**, une espace après chaque mot, disposition Kwéyòl, sans toucher aucune suggestion. Comparaison du texte final à la phrase attendue, sans la ponctuation.

| Phrase attendue | Obtenu | Exact |
|---|---|---|
| Sa ou fè | Sa ou fè | oui |
| Mèsi onpil | Mèsi anpil | non |
| Pa ni pwoblèm | Pa ni pwoblèm | oui |
| An kay òwganizé on diné | An kay òganizé on diné | non |
| Nou kay bengné | Nou kay bengné | oui |
| Kriyé vitman on doktè | Kriyé vitamin on doktè | non |
| Koté ki lè ou ka fèmé | Koté ki lè ou ka fème | non |
| Otila nou pou kontré | Otila nou pou montré | non |
| Nou ka vwè pou nou ay sinéma | Nou ka vwè pou nou au sinéma | non |
| Bèf pa ka di savann mèsi | Bèf pa ka di savan mèsi | non |
| Yo anonsé on siklòn ka vin si Gwadloup | Yo anonse on siklòn ka vin si gwadloup | non |
| Manjé tikrazi pli méyè pasé pa manjé hak | Manjé tikrazi pli mété pase pa manjé Jak | non |

**Bilan** : 3 phrases exactes sur 12. Sur 59 mots, 48 sont restitués exactement (la casse mise à part), 3 gardent un accent manquant ou erroné, et 8 sont **remplacés par un autre mot** : `onpil` → anpil, `òwganizé` → òganizé, `vitman` → vitamin, `kontré` → montré, `ay` → au, `savann` → savan, `méyè` → mété, `hak` → Jak.

Autres remplacements relevés lors d'un essai à part : `toupiti` → « Tou piti », `matete` → « latérè ».

## Limites

- Un seul appareil, une seule version de Gboard, quelques dizaines de mots. Ce n'est pas une mesure de qualité générale.
- Les phrases viennent du corpus de Klavyé Kréyòl ; leur graphie est celle de ce corpus, pas nécessairement celle que Gboard a apprise.
- Un utilisateur réel touche les suggestions et corrige au fil de l'eau : ce test mesure la correction automatique seule.
- Le clavier de Klavyé Kréyòl n'a pas de correction automatique : il n'a ni restitué d'accents ni remplacé de mot dans ce protocole, puisqu'il n'a pas été soumis à cette frappe sans accent.

## Conséquence pour les textes publics

Gboard propose des suggestions et une prédiction du mot suivant en créole guadeloupéen. La formule « premier clavier … intelligent » n'est donc plus tenable, et les cases « non vérifié » de `docs/comparatif.html` peuvent être renseignées avec ce qui précède.
