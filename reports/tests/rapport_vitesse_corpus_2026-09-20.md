# Vitesse de frappe sur un échantillon neuf du corpus

*Réalisé le 20 septembre 2026, les deux claviers sur le même téléphone. Observations, pas un classement.*

Les mesures du 19 septembre portaient sur une chaîne unique de 21 lettres sans espace, choisie pour ne déclencher aucune correction. Celle-ci porte sur du texte réel : huit phrases tirées du corpus, espaces comprises, donc avec tout ce que chaque clavier fait à la validation d'un mot.

## L'échantillon

Huit phrases de 6 à 10 mots, tirées au sort (graine 20260920) parmi les 1 210 phrases du corpus qui ne contiennent que des lettres tapables, à l'exclusion des douze phrases du simulateur déjà utilisées. **266 frappes** en tout, espaces comprises.

| Phrase | Source |
|---|---|
| ki rann nou solid konsa Solid manman | Matété a Krab, Jomimi |
| Toutmoun ka ba bèf bwè an ma la | Contes marigalantais d'hier et d'avant-hier, kréyòl-Français, Alen Rutil, éditions Nèg Mawon, 2021 |
| Sa k ay é ou menm | POTOMITAN/potomitan-gcf-fr-translation (cours Assimil, traduction FR) |
| Kou koukyanm la Sé w ka pran kou | Soni RUPAIRE SOMANBIL, Pyès téyat, 3 LAK, « gran parad … ti kou baton », Edisyon PARABOLE, 1971 |
| Fò nou pran douvan avan twota baré nou | Soni RUPAIRE SOMANBIL, Pyès téyat, 3 LAK, « gran parad … ti kou baton », Edisyon PARABOLE, 1971 |
| Zòt tini on sendika asi bitasyon la on | Soni RUPAIRE SOMANBIL, Pyès téyat, 3 LAK, « gran parad … ti kou baton », Edisyon PARABOLE, 1971 |
| An sel mouton ka galé tout an troupo | Bel poveb kréyol, potomitan.info (collecte Jid) |
| Mé kou lasa zòt rivé tibwen ta | Soni RUPAIRE SOMANBIL, Pyès téyat, 3 LAK, « gran parad … ti kou baton », Edisyon PARABOLE, 1971 |

Elles sont tapées sans accent ni ponctuation, lettre à lettre, une espace après chaque mot.

## Temps de calcul par frappe

Temps processeur du processus du clavier (`utime + stime` de `/proc/<pid>/stat`), divisé par les 266 frappes. Trois essais par clavier et par cadence.

| | Klavyé Kréyòl 22.2.0 | Gboard 18.2.4 |
|---|---|---|
| Saisie posée (une touche toutes les 0,35 s) | **47.56 ms** (47.41, 47.89, 47.56) | **97.26 ms** (97.26, 97.59, 97.14) |
| Saisie enchaînée (sans pause) | **34.74 ms** (36.09, 34.55, 34.74) | **83.98 ms** (85.23, 83.98, 83.27) |
| Mémoire pendant la saisie (PSS) | 53 Mo | 132 Mo |

Klavyé calcule environ **deux fois moins par frappe** et occupe environ deux fois et demie moins de mémoire. Les trois essais sont serrés des deux côtés (moins de 1 % d'écart), et les valeurs rejoignent celles du 19 septembre sur un tout autre texte (Klavyé 51 à 54 ms posée et 39 ms enchaînée en 22.0.2 ; Gboard 94 à 95 et 85). Le résultat tient donc sur deux échantillons indépendants.

Gboard fait à chaque frappe des choses que Klavyé ne fait pas du tout : reconnaissance d'un éventuel glissement, correction automatique complète, apprentissage. Un clavier plus simple coûte moins cher à faire tourner ; cela ne dit rien de la qualité de son code.

## Ce que la qualité de restitution n'a pas pu dire

Le même essai devait comparer le texte obtenu. Il n'est pas exploitable pour Gboard, et la cause est dans le protocole.

**La série enchaînée a dégradé Gboard.** Envoyées sans pause, les 266 frappes ont été lues en partie comme des gestes de glissement. Gboard apprend de ce qu'il reçoit : après cette série, il rend « Ra » pour `pale`, « Razé » pour `mesi`, « Érè » pour `nou`, et ne corrige plus `kreyol` en `kréyòl`, alors qu'il le faisait le matin même sur ce téléphone. Les relevés de qualité postérieurs à cette série mesurent donc l'état abîmé, pas le comportement de Gboard.

À refaire après remise à zéro des mots appris de Gboard, et **sans série enchaînée** : la cadence posée suffit pour le temps de calcul.

Le comptage côté Klavyé, lui, reste cohérent avec le reste : 60 mots rendus sur 60, 55 exacts, 5 avec un accent manquant, **aucun mot remplacé par un autre**. Il ne dépend pas de l'état de Gboard.

## Un second échantillon, Sonny Rupaire

À la demande, un échantillon tiré des seuls textes de Sonny Rupaire présents au corpus (461 phrases candidates, 8 tirées au sort avec la graine 920, 215 frappes).

| Phrase attendue | Klavyé Kréyòl 22.2.0 |
|---|---|
| Yo maré tou jouké an menm pak la | Yo maré tou jouke an menm pak la |
| Ou ka fè mwen lapenn wi | Ou ka fè mwen lapenn wi |
| Timo Mé ka ou ka fouté la alò | Timo mé ka ou ka fouté la alò |
| Kou vòlè la Sé w ka pran kou | Kou volé la se w ka pran kou |
| I k ay ouvè pòt la | I k ay ouvè pòt la |
| É nou nou ka travay tè | E nou nou ka travay té |
| Ou ka santi sa an vyann a w wi | Ou ka santi sa an vyann a w wi |
| Krab é kribich ka fè dèyè | Krab e kribich ka fè dèyè |

Sources : *Gran parad ti kou baton* (1970), *Pyès téyat, 3 LAK* (Edisyon PARABOLE, 1971), *Cette igname brisée qu'est ma terre natale* (Editions Caribéennes, 1982), *Kè ou lèstonmak*.

**Klavyé, sur 57 mots** : 4 phrases exactes sur 8, 51 mots exacts, 6 mots dont l'accent manque, **aucun mot remplacé par un autre**. Les manques sont de la même famille que sur l'autre échantillon : `jouké`, `volé`, `sé`, `té`, `é` et `mé` restent nus ou gardent une graphie rivale, parce que le dictionnaire hésite ou que la forme nue y est attestée.

**Gboard n'est pas mesurable sur cet échantillon.** Voir ci-dessous.

## Pourquoi Gboard n'a pas pu être mesuré sur ces deux échantillons

Au fil de la journée, Gboard s'est mis à rendre `Ra`, `Érè`, `Frè`, `Razé` à la place des mots tapés, y compris sur les douze phrases du simulateur qu'il traite correctement (3 phrases exactes sur 12, résultat reproduit trois fois à l'identique). Quatre remises en état ont été essayées :

1. **Supprimer les mots et les données appris** (Réglages Gboard, Confidentialité) : sans effet.
2. **Retirer puis remettre la langue « Créole guadeloupéen »** : a fonctionné une fois, Gboard reproduisant ensuite sa référence mot pour mot. La dégradation est revenue après une nouvelle série de frappes.
3. **La même opération, refaite** : sans effet, même après trois minutes d'attente.
4. **Désinstallation puis réinstallation complète** (mêmes APK, données vierges), langue créole rajoutée, **« Personnaliser pour vous » coupé** pour empêcher tout apprentissage, quatre minutes d'attente. Gboard a alors réussi son contrôle de santé : les douze phrases, mot pour mot comme les 19 et 20 septembre.

C'est dans cet état propre qu'a été lancée la passe unique sur les phrases de Sonny Rupaire. Le **contrôle d'après** a échoué : les douze phrases de référence, rejouées aussitôt, donnent de nouveau `Ra`, `Érè`, `Frè`. Gboard s'est donc dégradé **pendant** les 215 frappes de l'échantillon Rupaire, alors même que son apprentissage était coupé et qu'il venait d'être réinstallé.

La mesure n'est pas encadrée : sain avant, abîmé après, on ne peut pas dire à partir de quelle phrase le résultat cesse de valoir. Elle n'est donc pas publiable. À noter que les douze phrases du simulateur, elles, ne le dégradent pas, bien qu'elles comptent 262 frappes contre 215 : ce n'est donc pas le volume qui est en cause, mais quelque chose dans ce texte, que ce protocole ne permet pas d'isoler.

**Conséquence** : les seuls chiffres de qualité de Gboard qui tiennent restent ceux des douze phrases du simulateur (3 phrases exactes sur 12, 8 mots remplacés sur 59), mesurés le 19 septembre et reproduits trois fois le 20, dont une sur une installation neuve. Aucun chiffre de qualité de Gboard sur les échantillons du corpus n'est publiable. Les mesures de **vitesse** restent valables : elles ne dépendent pas de la justesse du texte rendu, et elles concordent avec celles du 19 septembre.

**État du téléphone après ces essais** : Gboard réinstallé, « Personnaliser pour vous » remis en marche, clavier par défaut rendu à Klavyé Kréyòl.


## Limites

- Un seul appareil (Samsung Galaxy A21s, Android 12), une seule version de chaque clavier, trois essais par cadence.
- La cadence est imposée par `adb shell input tap`, dont le lancement coûte environ 90 ms : la « saisie enchaînée » n'est pas une frappe humaine rapide.
- Réseau actif des deux côtés, aucun service de Gboard coupé.
- Le temps processeur ne compte que le processus du clavier : le travail fait par l'application de messages et par le système n'y figure pas.
- La mesure des images par seconde a été abandonnée : `dumpsys gfxinfo reset` a provoqué un plantage natif d'Android (`libhwui`, `JankTracker`, « Impossible totalDuration 0 »), qui a tué le processus du clavier. C'est un défaut de la plateforme déclenché par l'outil de mesure, pas du clavier.

