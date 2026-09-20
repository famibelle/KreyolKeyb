# Douze phrases tapées sans accent : Gboard 18.2.4 et Klavyé Kréyòl 22.2.0

*Réalisé le 20 septembre 2026, les deux claviers sur le même téléphone. Ce rapport consigne des observations, pas un classement.*

Suite de `rapport_test_gboard_kweyol_2026-09-19.md`. Ce jour-là, le clavier de Klavyé Kréyòl n'avait pas de correction automatique et n'avait donc pas été soumis à ce protocole. La 22.1.0 lui a donné une restitution des accents, qui ne remplace jamais un mot par un autre : voici le même essai des deux côtés.

## Conditions

- **Appareil unique** : Samsung Galaxy A21s (SM-A217F), Android 12, écran 720 x 1600, entrée de gamme. Champ de message de Samsung Messages, aucun envoi.
- **Gboard** : 18.2.4.969776716 (release, arm64-v8a). Le Play Store ne proposait aucune mise à jour le 20 septembre (bouton « Ouvrir » seul, dernière version du 2 septembre 2026). Disposition « Créole guadeloupéen » AZERTY, la barre d'espace affiche « Kwéyòl ».
- **Klavyé Kréyòl** : 22.2.0, l'APK de release publié, installé après désinstallation de la 22.0.2 venue du Play Store (les signatures diffèrent, la mise à jour par-dessus était refusée). Réglages par défaut, dont la restitution des accents.
- **Protocole identique** : les douze phrases du simulateur, tapées lettre à lettre par `adb shell input tap`, **sans accent ni ponctuation**, une espace après chaque mot, aucune suggestion touchée. Le texte final est lu par l'arbre d'accessibilité et comparé à la phrase attendue, ponctuation exclue.

## Résultats

| Phrase attendue | Gboard 18.2.4 | Klavyé 22.2.0 |
|---|---|---|
| Sa ou fè | exact | exact |
| Mèsi onpil | Mèsi **anpil** | exact |
| Pa ni pwoblèm | exact | exact |
| An kay òwganizé on diné | An kay **òganizé** on diné | exact |
| Nou kay bengné | exact | exact |
| Kriyé vitman on doktè | Kriyé **vitamin** on doktè | Kriyé vitman on **dokte** |
| Koté ki lè ou ka fèmé | Koté ki lè ou ka **fème** | Koté ki **le** ou ka fèmé |
| Otila nou pou kontré | Otila nou pou **montré** | exact |
| Nou ka vwè pou nou ay sinéma | Nou ka vwè pou nou **au** sinéma | exact |
| Bèf pa ka di savann mèsi | Bèf pa ka di **savan** mèsi | **Bef** pa ka di savann mèsi |
| Yo anonsé on siklòn ka vin si Gwadloup | Yo **anonse** on siklòn ka vin si **gwadloup** | Yo anonsé on **siklon** ka vin si **gwadloup** |
| Manjé tikrazi pli méyè pasé pa manjé hak | Manjé tikrazi pli **mété pase** pa manjé **Jak** | exact |

| Mesure (59 mots) | Gboard 18.2.4 | Klavyé 22.2.0 |
|---|---|---|
| Phrases exactes | 3 sur 12 | 8 sur 12 |
| Mots exacts (casse mise à part) | 48 | 55 |
| Mots avec un accent manquant ou erroné | 3 | 4 |
| Mots **remplacés par un autre mot** | 8 | 0 |
| Mémoire occupée pendant la saisie (PSS) | 132 Mo | 57 Mo |

Gboard reproduit à l'identique le résultat du 19 septembre. Klavyé donne sur ce téléphone exactement le résultat obtenu la veille sur l'émulateur : le protocole est reproductible d'un appareil à l'autre.

## Ce que ces chiffres disent

- **Les deux claviers rétablissent la plupart des accents.** L'écart tient à ce qu'ils s'autorisent : Gboard remplace aussi des mots (`onpil` par `anpil`, `vitman` par `vitamin`, `kontré` par `montré`, `ay` par `au`, `savann` par `savan`, `méyè` par `mété`, `hak` par `Jak`, `òwganizé` par `òganizé`). Klavyé n'en remplace aucun, par construction : le mot d'arrivée a les mêmes lettres que le mot tapé, aux accents près.
- **Le prix de cette prudence** : Klavyé laisse quatre mots sans leur accent. `doktè` reste nu parce que le dictionnaire porte deux graphies accentuées rivales (`doktè` 4 occurrences, `dòktè` 2), et la règle ne tranche pas entre elles. `bèf` (13) et `siklòn` (8) restent nus parce que leur forme sans accent est elle-même attestée (`bef` 7, `siklon` 7) et n'est pas nettement dominée. `lè` reste `le` parce que `le` est aussi un mot français, que la règle ne touche pas. Les quatre restent une saisie à reprendre à la main.
- **Gwadloup** : les deux claviers laissent `gwadloup` en minuscules. Aucun n'a de règle de majuscule pour un nom propre.
- **La mémoire** : 57 Mo contre 132 Mo pendant la même saisie de 21 lettres, mesure refaite ce jour sur ce téléphone. L'écart va dans le même sens que celui du 19 septembre (55 contre 134 Mo). Gboard fait à chaque frappe des choses que Klavyé ne fait pas du tout (saisie glissée, dictionnaire personnel, services en ligne).

## Ce que le clavier porte au-delà de la saisie

Vérifié le 20 septembre sur ce téléphone, application 22.2.0 fraîchement installée.

- **Six jeux de vocabulaire** dans l'onglet *Jé* : Mots Mêlés, Mots Mélangés, Mo an Karénaj, Fraz a twou, Mokwaré, Mo an plas. Ils tirent leurs mots du dictionnaire du clavier, et seulement parmi ceux qui portent une traduction française.
- **Un carnet de cartes** (*Sanblé*) : chaque mot gagné devient une carte avec son sens, une phrase d'auteur guadeloupéen et son crédit, plus une révision espacée (*Sonjé*) qui les ramène à intervalles croissants.
- **Un dictionnaire consultable** dans l'onglet *Dictionnaire* : **1 145 mots traduits**, recherche dans les deux sens (kréyòl vers français et l'inverse), avec la mention des sources (Kreyolopedia et Wiktionnaire, CC BY-SA 4.0).
- **Une progression** dans l'onglet *Kréyòl an mwen* : sept niveaux de Pipirit à Potomitan, le compte des mots découverts sur les 5 296 du dictionnaire, un mot du jour et une liste de mots à découvrir.

Gboard n'a rien de cet ordre : c'est un clavier, pas un outil de vocabulaire. Le comparer sur ce terrain ne dit donc pas qu'il fait moins bien, mais que les deux applications ne couvrent pas le même besoin.

## Limites

- **Les phrases viennent du corpus de Klavyé Kréyòl**, dont le dictionnaire est tiré : ce test est favorable à Klavyé et défavorable à Gboard, qui a appris une autre graphie. Un texte hors corpus donnerait probablement un écart plus faible.
- Une seule version de chaque clavier, une seule passe, douze phrases, un seul appareil : ce n'est pas une mesure de qualité générale.
- Le test mesure la correction automatique seule. Un utilisateur réel touche les suggestions et corrige au fil de l'eau, ce que le protocole ne simule pas.
- Klavyé : la restitution repose sur des seuils fixés à la main (5 et 8 fois, voir `AccentRestoration.kt`), pas réglés sur ce test.
- La mémoire est relevée par `dumpsys meminfo` juste après la frappe, une fois par clavier. Réseau actif des deux côtés.

## Effet sur les textes publics

Les pages qui comparent Gboard et Klavyé décrivaient ce dernier comme dépourvu de correction automatique. Repris le 20 septembre avec la même prudence : fait vérifié sur douze phrases du corpus, pas un classement.
