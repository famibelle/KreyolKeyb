# Douze phrases tapées sans accent : Gboard 18.2.4 et Klavyé Kréyòl 22.2.0

*Réalisé le 20 septembre 2026. Ce rapport consigne des observations, pas un classement.*

Suite de `rapport_test_gboard_kweyol_2026-09-19.md`. Ce jour-là, le clavier de Klavyé Kréyòl n'avait pas de correction automatique et n'avait donc pas été soumis à ce protocole. La 22.1.0 lui a donné une restitution des accents (elle ne remplace jamais un mot par un autre) : voici le même essai des deux côtés.

## Conditions

- **Gboard** : 18.2.4.969776716 (release, arm64-v8a) sur le Samsung Galaxy A21s (SM-A217F, Android 12, 720 x 1600). Le Play Store n'a proposé aucune mise à jour (bouton « Ouvrir » seul, dernière version du 2 septembre 2026), donc pas de changement depuis le 19 septembre. Disposition « Créole guadeloupéen » AZERTY (barre d'espace « Kwéyòl »). Champ de message de Samsung Messages.
- **Klavyé Kréyòl** : 22.2.0 (APK de release publié), sur l'**émulateur** Android 14 (1080 x 2340), champ de message de Google Messages. Le téléphone porte la 22.0.2 installée par le Play Store, signée par Google : l'APK publié ne s'y installe pas par-dessus (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`), et je n'ai pas désinstallé l'application du téléphone. Les deux séries n'ont donc pas tourné sur le même appareil. Cela ne change pas ce qui est mesuré ici (le texte obtenu), mais cela empêche toute comparaison de vitesse.
- **Protocole identique** : les douze phrases du simulateur, tapées lettre à lettre par `adb shell input tap`, **sans accent ni ponctuation**, une espace après chaque mot, aucune suggestion touchée. Le texte final est lu par l'arbre d'accessibilité et comparé à la phrase attendue, ponctuation exclue.
- Réglages de Klavyé : ceux par défaut (restitution des accents activée).

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

Gboard reproduit à l'identique le résultat du 19 septembre.

## Ce que ces chiffres disent

- **Les deux claviers rétablissent la plupart des accents.** L'écart tient à ce qu'ils s'autorisent : Gboard remplace aussi des mots (`onpil` par `anpil`, `vitman` par `vitamin`, `kontré` par `montré`, `ay` par `au`, `savann` par `savan`, `méyè` par `mété`, `hak` par `Jak`, `òwganizé` par `òganizé`). Klavyé n'en remplace aucun, par construction : le mot d'arrivée a les mêmes lettres que le mot tapé, aux accents près.
- **Le prix de cette prudence** : Klavyé laisse quatre mots sans leur accent, là où Gboard en corrige plusieurs. `doktè` reste nu parce que le dictionnaire porte deux graphies accentuées rivales (`doktè` 4 occurrences, `dòktè` 2), et la règle ne tranche pas entre elles. `bèf` (13) et `siklòn` (8) restent nus parce que leur forme sans accent est elle-même attestée (`bef` 7, `siklon` 7) et n'est pas nettement dominée. `lè` reste `le` parce que `le` est aussi un mot français, que la règle ne touche pas. Les quatre restent une saisie à reprendre à la main.
- **Gwadloup** : les deux claviers laissent `gwadloup` en minuscules (Gboard l'écrit aussi `Jak` pour `hak`). Aucun n'a de règle de majuscule pour un nom propre.

## Limites

- **Les phrases viennent du corpus de Klavyé Kréyòl**, dont le dictionnaire est tiré : ce test est favorable à Klavyé et défavorable à Gboard, qui a appris une autre graphie. Un texte hors corpus donnerait probablement un écart plus faible. Le rapport du 19 septembre disait déjà que la graphie de ces phrases est celle du corpus.
- Deux appareils différents, une seule version de chaque clavier, une seule passe, douze phrases : ce n'est pas une mesure de qualité générale.
- Le test mesure la correction automatique seule. Un utilisateur réel touche les suggestions et corrige au fil de l'eau, ce que ni l'un ni l'autre des protocoles ne simule.
- Klavyé : la restitution repose sur des seuils fixés à la main (5 et 8 fois, voir `AccentRestoration.kt`), pas réglés sur ce test.

## Effet sur les textes publics

Les pages qui comparent Gboard et Klavyé ne disent pas encore que Klavyé restitue les accents sans remplacer de mot, et le rapport du 19 septembre les décrivait comme dépourvues de correction automatique. À reprendre avec la même prudence : fait vérifié sur douze phrases du corpus, pas un classement.
