# Traduction française des mots kréyòl

Ce document couvre `creole_translations.json`, la table qui permet à l'onglet
Dictionnaire et aux quatre jeux de dire ce qu'un mot veut dire. Il complète
`generate_translations.py` et `gloses_source.py`, qui portent le détail
technique.

## Les sources et leur licence

| Source | Ce qu'elle apporte | Licence |
|---|---|---|
| [Kreyolopedia](https://kreyolopedia.org) | fiches écrites à la main, sourcées, avec phonétique et exemples | CC BY-SA 4.0 |
| [Wiktionnaire](https://fr.wiktionary.org) | le volume, par deux chemins distincts | CC BY-SA 4.0 |

**Les deux sont en CC BY-SA 4.0**, donc combinables, et cela emporte deux
obligations sur la table produite :

- **Citer les sources.** L'attribution voyage dans l'actif (champ
  `attribution`), s'affiche en pied de l'onglet Dictionnaire, et chaque entrée
  porte le code de la source qui l'a fournie (`K`, `W` ou `T`). Le workflow
  `build-apk.yml` refuse de construire un APK si le champ a disparu.
- **Partager à l'identique.** `creole_translations.json` est un dérivé de
  contenus en CC BY-SA : il se rediffuse sous la même licence. Cela porte sur
  **ce fichier**, pas sur le code de l'application, qui reste une œuvre
  distincte le lisant à l'exécution.

Ce qui n'a **pas** été retenu, et pourquoi :

- les dictionnaires de référence (Ludwig, Montbrand, Poullet, Telchid, chez
  Jasor ; Orphie ; Karthala) sont sous droit d'auteur ;
- [Diko Kréyol](https://www.diko-kreyol.com) n'annonce aucune licence de
  réutilisation ;
- data.gouv.fr ne publie aucun lexique créole ;
- `POTOMITAN/PawolKreyol-gfc`, le corpus du projet, n'a que deux colonnes,
  `Source` et `Texte` : pas une ligne de français en face.

## Les trois chemins d'une glose

Le générateur les essaie dans cet ordre, et s'arrête au premier qui répond.

1. **`K` — Kreyolopedia.** Filtrée sur `variante_code == "gp"` et
   `statut == "valide"`. Vingt et un mots au 5 septembre 2026, dont neuf que le
   Wiktionnaire n'a pas. Ce sont les seules entrées à porter une prononciation
   et des exemples.
2. **`W` — la page guadeloupéenne du Wiktionnaire.** Les lignes `#` de la
   section `== {{langue|gcf}} ==`, le wikitexte nettoyé. 349 entrées.
3. **`T` — l'inversion des sections « Traductions ».** 884 pages *françaises*
   citent une forme guadeloupéenne ; on retourne la paire. C'est le seul chemin
   qui atteint `moun` : le mot n'a pas de page à lui, mais la page « personne »
   le donne en traduction. Ce chemin fournit à lui seul les deux tiers de la
   table.

## Ce que la table couvre, et ce qu'elle ne couvrira pas

1 145 formes glosées, dont 513 des 5 296 mots du dictionnaire du clavier, soit
**36 % des occurrences du corpus** et 86 des 200 mots les plus fréquents. Les
632 autres formes sont des mots que le corpus n'a jamais employés : elles ne
servent qu'à la recherche, les jeux ne tirant que dans le dictionnaire.

À titre de comparaison, le clavier luxembourgeois qui partage cette base de
code glose 55 % de ses formes et 90 % de ses occurrences, à partir d'une source
unique et officielle publiée en CC0. **Ce plafond-là n'est pas atteignable en
kréyòl aujourd'hui**, et l'écart ne se comblera pas en changeant ce script : il
se comblera quand les sources grandiront.

Trois limites qu'il faut connaître avant de conclure à un bug :

- **`sé`, troisième mot le plus fréquent du kréyòl, n'est glosé nulle part.**
  Ni Kreyolopedia ni le Wiktionnaire ne le traitent. Il ne manque rien au
  script.
- **Une glose peut être exacte et hors sujet.** `si` ne remonte que par la page
  française « acide », qui est bien un de ses sens ; ce n'est pas celui que le
  corpus emploie. On ne réécrit pas les sources.
- **Un mot glosé par lui-même est écarté.** La page `pou` du Wiktionnaire ne
  connaît que l'insecte, donc « pou → pou ». C'est exact et sans intérêt, et
  dans un onglet qui s'appelle Dictionnaire cela se lit comme une panne. Sept
  formes sont dans ce cas ; celles qu'une autre source rattrape reviennent par
  elle.

## Lancer la régénération

```bash
cd Dictionnaires
python generate_translations.py --strict
```

Le script **consomme** `creole_dict.json` et n'y ajoute aucun mot : il doit
tourner **après** `KreyolComplet.py`, comme `generate_cloze.py`.

- `--strict` refuse le cache : les deux sources doivent répondre. C'est le mode
  à employer à la main, jamais en CI.
- `--hors-ligne` force le cache, pour mettre au point sans rappeler 900 pages.
- Sans option, le script tente le réseau et se replie sur le cache. C'est ce
  que fait la CI, pour qu'une indisponibilité de source dégrade comme le reste
  de la chaîne au lieu de rendre le build rouge. Deux garde-fous de volume et
  d'attribution arrêtent alors le build si le fichier est tronqué.

Le cache vit dans `Dictionnaires/gloses_data/`, hors du dépôt.

## Élargir la couverture

Par ordre de rendement mesuré, et non d'effort :

1. **Contribuer au Wiktionnaire.** Une page guadeloupéenne créée, c'est une
   glose de plus au prochain build, sans une ligne de code. Ajouter
   `{{trad|gcf|…}}` à une page française coûte encore moins.
2. **Contribuer à Kreyolopedia**, qui donne en plus la prononciation et les
   exemples affichés dans la fiche.
3. **Ouvrir la variante martiniquaise** (`variante_code == "mq"` chez
   Kreyolopedia, `Catégorie:créole martiniquais` au Wiktionnaire, 235 pages).
   Écartée pour l'instant : les deux langues sont proches, mais leurs graphies
   divergent assez pour qu'une glose empruntée se lise comme une faute. Ce
   serait un arbitrage linguistique, pas un réglage de script.
