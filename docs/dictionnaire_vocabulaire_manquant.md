# Dictionnaire des trous de vocabulaire — kréyòl Gwadloupéyen

*Page générée le 10 juillet 2026 à 12:35 (heure locale du dépôt).*

> ⚠️ **Méthodologie et limites** : cette liste n'est **pas issue d'un corpus ni d'une analyse statistique**. Elle a été établie à partir de connaissances générales sur le lexique du kréyòl guadeloupéen (verbes, vocabulaire du quotidien, thèmes courants), puis **vérifiée mot par mot par script** contre le dictionnaire embarqué actuel (`creole_dict.json`, 4 043 mots au 10 juillet 2026) pour ne garder que les mots réellement absents. Ce n'est donc **ni une liste académique validée, ni exhaustive** — c'est un point de départ actionnable, **à faire valider par des locuteurs natifs** avant toute intégration dans le corpus. Les exemples de phrases sont fournis à titre indicatif et devraient également être relus par un locuteur natif avant d'être utilisés comme matériau d'entraînement.

## Pourquoi cette liste ?

Le [rapport du 10 juillet 2026](./notes_techniques.html#rapport-de-test-impact-de-lenrichissement-du-dataset-potomitanpawolkreyol-gfc) a montré qu'enrichir le dataset `POTOMITAN/PawolKreyol-gfc` avec des textes ajoutés génériquement (427→703 textes) fait grossir le dictionnaire embarqué (+363 mots) **sans combler des trous de vocabulaire déjà identifiés** (`blòké`, `apeti`/`lapeti`, `nwit`/`lannwit`) : les nouveaux textes ne contenaient tout simplement pas ces mots précis.

Cette page propose une approche **ciblée** : une liste de mots courants du kréyòl guadeloupéen, vérifiés comme absents du dictionnaire actuel, organisée par thème, pour orienter la prochaine phase d'enrichissement du corpus vers du vocabulaire concret et utile au quotidien plutôt que vers un enrichissement générique.

## Bilan de la vérification

Sur 136 mots candidats passés en revue (verbes courants, famille, nourriture, météo, temps, émotions, adjectifs, objets du foyer, santé, école, animaux), **88 étaient déjà présents** dans le dictionnaire actuel — signe que la couverture lexicale de base est déjà solide. **48 mots restent confirmés absents**, listés ci-dessous par catégorie.

## Mots confirmés absents, par catégorie

### Déjà confirmés par test réel en conditions d'usage (09-10 juillet 2026)

Ces 5 mots/graphies ont été identifiés non pas par revue lexicale mais par un test empirique direct (frappe réelle sur le clavier, observation des suggestions) — la priorité la plus solide pour un premier enrichissement ciblé.

| Kréyòl | Français | Exemple |
|---|---|---|
| `blòké` | bloqué | Wout la blòké. |
| `apeti` | appétit | Bon apeti. |
| `lapeti` | appétit (variante avec article) | I pèd lapeti-y. |
| `nwit` | nuit | Bon nwit. |
| `lannwit` | la nuit (variante avec article) | Yo maché lannwit. |

### Verbes du quotidien (6 mots)

| Kréyòl | Français | Exemple |
|---|---|---|
| `doné` | donner | Doné mwen liv-la souplé. |
| `édé` | aider | Ou pé édé mwen chayé sak-la ? |
| `bouyi` | faire bouillir | Bouyi dlo-a avan ou mété diri-a. |
| `kòmansé` | commencer | Lékòl kòmansé a wit-è. |
| `chwazi` | choisir | Chwazi kilès ou vlé. |
| `génié` | gagner | Ekip-nou génié match-la. |

### Famille et corps (4 mots)

| Kréyòl | Français | Exemple |
|---|---|---|
| `tonton` | oncle | Tonton mwen ka rété Pwentapit. |
| `matant` | tante | Matant mwen vini wè nou dimanch. |
| `kouzen` | cousin | Kouzen mwen ka jwé foutbol. |
| `nen` | nez | Nen mwen bouché. |

### Nourriture et cuisine (8 mots)

| Kréyòl | Français | Exemple |
|---|---|---|
| `zaboka` | avocat | Zaboka-a mi, nou pé manjé-y. |
| `zoranj` | orange | Presé zoranj pou fè ji. |
| `tomat` | tomate | Mété tomat adan sòs-la. |
| `zonyon` | oignon | Koupé zonyon pou fè kolonbo-a. |
| `lay` | ail | Mété on tibren lay adan manjé-a. |
| `sik` | sucre | Ba mwen tibren sik pou kafé-a. |
| `lwil` | huile | Mété lwil adan lapoèl-la. |
| `bwason` | boisson | Ou vlé an bwason fret ? |

### Météo et nature (5 mots)

| Kréyòl | Français | Exemple |
|---|---|---|
| `loraj` | orage | Loraj ka gronmé o lwen. |
| `fredi` | froid | I ka fè fredi lè o soley kouché. |
| `chalè` | chaleur | Chalè-a two fò jòdi-a. |
| `montany` | montagne | La Soufyè sé on gran montany. |
| `lékim` | écume | Lékim lanmè-a blan kon koton. |

### Temps et calendrier (3 mots)

| Kréyòl | Français | Exemple |
|---|---|---|
| `démen` | demain | Démen nou ka alé lanmè. |
| `aprémidi` | après-midi | Aprémidi-a nou ka rété kaz. |
| `aswè` | ce soir | Aswè nou ka gadé match-la. |

### Émotions et sentiments (2 mots)

| Kréyòl | Français | Exemple |
|---|---|---|
| `faché` | fâché | Manman mwen faché paskè an an réta. |
| `onté` | honte | I ni onté di sa i fè-a. |

### Adjectifs courants (5 mots)

| Kréyòl | Français | Exemple |
|---|---|---|
| `lonng` | long | Chimen-an lonng anpil. |
| `lèjè` | léger | Valiz-la pa lou, i lèjè. |
| `fèb` | faible | I fèb apré maladi-a. |
| `rich` | riche | Fanmi-tala rich anlo. |
| `led` | laid | Kaz-la led men i sòlid. |

### Objets du foyer (4 mots)

| Kréyòl | Français | Exemple |
|---|---|---|
| `fenèt` | fenêtre | Louvri fenèt-la, i two cho. |
| `asyèt` | assiette | Ranjé asyèt-la asou tab-la. |
| `kouto` | couteau | Fè atansyon épi kouto-a. |
| `kiyè` | cuillère | Ba mwen on kiyè pou manjé soup-la. |

### Santé (2 mots)

| Kréyòl | Français | Exemple |
|---|---|---|
| `médikaman` | médicament | Pran médikaman-ou chak maten. |
| `fyèv` | fièvre | Bébé-a ni fyèv depi yè. |

### Travail et école (3 mots)

| Kréyòl | Français | Exemple |
|---|---|---|
| `pwofésè` | professeur | Pwofésè-a two sévè. |
| `kaye` | cahier | Pran kaye-w é kréyon-ou. |
| `kréyon` | crayon | An pèd kréyon mwen. |

### Animaux (1 mot)

| Kréyòl | Français | Exemple |
|---|---|---|
| `zwazo` | oiseau | Zwazo-a ka chanté a bonnè. |

## Comment utiliser cette liste

1. **Valider avec des locuteurs natifs** : orthographe, sens, et pertinence des exemples de phrases doivent être relus avant toute intégration.
2. **Écrire ou collecter des textes** contenant naturellement ces mots (courts dialogues, phrases de la vie quotidienne, proverbes) — plutôt que d'insérer les mots isolément, ce qui priverait le modèle de n-grams du contexte d'usage.
3. **Ajouter ces textes au dataset** `POTOMITAN/PawolKreyol-gfc` sur Hugging Face.
4. **Relancer le pipeline** `Dictionnaires/KreyolComplet.py` pour régénérer `creole_dict.json` et `creole_ngrams.json` à partir du corpus mis à jour.
5. **Revérifier** que les mots ciblés sont bien présents après régénération (par exemple avec le même script de vérification que celui utilisé pour produire cette page), puis idéalement rejouer un test en conditions réelles sur les phrases concernées.

Cette approche ciblée devrait être plus efficace que l'ajout générique de textes pour combler des trous de vocabulaire précis et déjà identifiés.
