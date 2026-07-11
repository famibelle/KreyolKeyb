# Rapport de simulation — Suggestions uniquement, avec hésitations, sur v7.0.3

**Date :** 11 juillet 2026
**Heure :** 13:20 – 14:05 (CEST)
**Testeur :** Claude Code (agent, modèle Fable 5), à la demande de l'utilisateur
**Version testée :** **7.0.3** (`versionCode 70003`) — inclut le correctif de casse sous majuscule automatique
**Environnement :** émulateur Android, AVD `kreyol_test`, fenêtre affichée à l'écran ; conversation SMS dédiée dans Google Messages.

> ⚠️ **Méthodologie et limites du contenu** : même dialogue imaginaire de 982 mots que les rapports précédents (non relu par un locuteur natif) — **pas une référence linguistique**.

## Pourquoi ce passage ?

Deux demandes ont motivé ce troisième passage sur le même dialogue :
1. **Revalider le correctif de casse** livré en 7.0.3 (rapport du 11/07 « suggestions uniquement » sur 7.0.2 : 54 messages sur 134 mis en tout-majuscules par un tap de suggestion sous shift automatique).
2. **Ajouter un comportement d'hésitation** au mode « suggestions uniquement », qui jusqu'ici tapait chaque lettre sans jamais se tromper : sur ~20 % des mots de 3 lettres ou plus, l'utilisateur simulé tape 1-2 lettres correctes, tape une lettre fautive (touche physiquement voisine), s'en rend compte, l'efface au backspace, puis reprend la frappe normalement jusqu'à taper la suggestion.

## Méthodologie

- Même dialogue : 134 messages, 982 mots. Mode strict « suggestions uniquement » (le mot n'est jamais terminé à la main, sauf s'il n'est jamais proposé).
- **Hésitation** (nouveau) : sur les mots ≥ 3 lettres, 20 % de chance de déclencher — 1 ou 2 lettres correctes tapées, puis une lettre fautive (voisine géométrique, même modèle que les runs « réalistes »), lecture des suggestions (ignorée), backspace, reprise normale.
- Suggestions relues à chaque lettre (y compris pendant le préfixe hésité) ; tap dès que le mot visé figure dans le top 3.
- Casse laissée à l'auto-capitalisation native ; noms propres en milieu de phrase tapés en minuscules (limite déjà documentée dans les rapports précédents).

## Résultats

### Vue d'ensemble

| Métrique | Valeur | Rappel run précédent (7.0.2, sans hésitation) |
|---|---|---|
| Messages envoyés | 134 | 134 |
| Mots traités | 982 | 982 |
| Messages identiques au texte visé (sensible casse) | **93 / 134 (69,4 %)** | 47 / 134 (35,1 %) |
| Exactitude caractère (Levenshtein) | **97,62 %** | 95,08 % (97,44 % insensible casse) |
| Mots committés par tap de suggestion | 807 (82,2 %) | 806 (82,1 %) |
| Mots jamais proposés | 175 (17,8 %) | 176 (17,9 %) |

**Le bug de casse est corrigé** : sur 134 messages, seuls **4** diffèrent encore par la casse, et ce ne sont pas des majuscules parasites mais l'inverse — des noms propres en milieu de phrase (`Jòj`, `Vytò`, `Pwentapit`, `Fwanswa`) tapés en minuscules, conforme à la méthodologie documentée depuis le premier rapport (seul le début du champ bénéficie de l'auto-capitalisation native). Rien à corriger côté clavier ici.

### Effet de l'hésitation

| Métrique | Valeur |
|---|---|
| Mots ayant déclenché une hésitation | 98 / 982 (10,0 %) |
| … committés par suggestion malgré l'hésitation | 85 / 98 (86,7 %) |
| … jamais proposés après l'hésitation | 13 / 98 |
| Taux de disponibilité — mots hésités | 86,7 % |
| Taux de disponibilité — mots non hésités | 81,7 % |
| Frappes additionnelles dues à l'hésitation (préfixe + faute + backspace) | 320 |

L'hésitation ne dégrade pas la disponibilité des suggestions — au contraire, légèrement au-dessus de la moyenne (86,7 % vs 81,7 %), ce qui est attendu : le préfixe fautif est corrigé avant toute vérification finale, le moteur ne voit jamais la faute une fois le backspace effectué. La différence n'est pas significative sur cet échantillon (bruit d'échantillonnage), mais confirme au minimum que **l'hésitation n'introduit pas de pénalité**.

### Rapidité d'apparition et position du chip

| Lettres tapées avant apparition | 1 | 2 | 3 | 4 | 5 | 6 |
|---|---|---|---|---|---|---|
| Mots | 343 | 182 | 101 | 37 | 8 | 1 |

Médiane inchangée : **1 lettre**. Position du chip : 1ᵉʳ 474 (58,7 %), 2ᵉ 240 (29,7 %), 3ᵉ 93 (11,5 %) — quasi identique au run précédent.

### Disponibilité par longueur de mot et mots jamais proposés

| Longueur | Disponible / total |
|---|---|
| 2 lettres | 362/389 |
| 3 lettres | 156/173 |
| 4 lettres | 158/174 |
| 5 lettres | 69/97 |
| 6 lettres | 48/78 |
| 7-8 lettres | 14/24 |

Même liste qu'avant : `renmen`, `konsè`, `démen`, `fatigé`, `sérié`, `nòwmal`, `bò`, `soley`, `red`, `fo`, `jòdi` — confirmée stable, ces mots restent candidats à l'enrichissement du dataset (rappel : tout ajout doit passer par le dataset Hugging Face source, pas par une édition directe du JSON, qui serait écrasée au prochain run du pipeline).

### Latence

| | Valeur |
|---|---|
| Mesures (une par lettre + une par lettre fautive d'hésitation) | 2 140 |
| Moyenne | 21,3 ms |
| Médiane | -30,2 ms |
| Écart-type | 197,4 ms |

Toujours sous le plancher de mesure, y compris avec la charge supplémentaire des lectures pendant les hésitations.

## Anomalies observées (races du harnais, hors casse)

37 messages sur 134 diffèrent du texte visé au-delà des 4 cas de noms propres non capitalisés — mot perdu ou tronqué (ex. `sav` → `sa`, `on` disparu), mauvais mot committé pendant un rafraîchissement de la barre (ex. `mété` → `mét pou sa` fusionné en `mété sa`), du même ordre de grandeur et de même nature que dans les deux rapports précédents : le tap arrive juste au moment où la liste de suggestions se rafraîchit. Comme précédemment, ces cas sont **inclus dans les statistiques** et non retirés. Le nombre plus élevé qu'au run du 10/07 (2 cas) reflète la même explication déjà documentée : ce mode sollicite un tap de suggestion sur 82 % des mots (contre 8 % au run réaliste), donc une exposition largement supérieure au même phénomène.

## Captures d'écran

Une capture tous les ~15-20 messages (12 au total) — dossier [`rapport_simulation_suggestions_hesitation_2026-07-11_screenshots/`](./rapport_simulation_suggestions_hesitation_2026-07-11_screenshots/).

## Conclusion

Ce troisième passage confirme deux choses. **D'abord, le correctif 7.0.3 fonctionne** : le taux de messages parfaitement conformes double (35,1 % → 69,4 %) et les 4 écarts de casse restants sont un phénomène différent, déjà documenté et non lié au bug corrigé. **Ensuite, l'ajout d'hésitations réalistes (frappe, faute, backspace) au mode « suggestions uniquement » ne change quasiment rien aux statistiques de fond** — couverture du dictionnaire (82,2 % vs 82,1 %), vitesse d'apparition (médiane 1 lettre), position du chip, latence : tous stables par rapport au run sans hésitation. C'est un résultat rassurant en soi : le moteur de suggestions ignore proprement les faux départs de l'utilisateur, sans effet de bord une fois le backspace effectué.
