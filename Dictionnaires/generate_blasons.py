#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Le dessin des cartes du carnet : quel mot porte quel meuble.

Produit deux actifs, lus par `carnet/Blason.kt` et `carnet/Series.kt` :

- `android_keyboard/app/src/main/assets/creole_blasons.json`, mot → meuble ;
- `android_keyboard/app/src/main/assets/creole_series.json`, les dessins de la
  série « Enluminures », ceux qu'un jeu peut réellement donner.

    python3 Dictionnaires/generate_blasons.py

Il consomme les actifs livrés (`creole_dict.json`, `creole_translations.json`
et les trois grilles) : à lancer après `generate_translations.py`,
`generate_crossword.py` et `generate_chassecroise.py`. Hors ligne, sans jeton.

## D'où viennent les dessins

De la bibliothèque du carnet luxembourgeois (LuxKeyb 37.10.0, porté en 24.0.0) :
214 silhouettes du domaine public (CC0), source, auteur et licence de chacune
dans `scripts/meubles_sources.json`, et six dessinées à la main. Elles sont
nommées par **concept français** (« maison », « eau », « main »), pas par mot
luxembourgeois : c'est ce qui les rend utilisables ici, à travers la glose.

## Ce que ce fichier ne fait pas

Il ne classe pas les mots en champs sémantiques, comme le fait LuxKeyb : la
table de gloses kréyòl ne porte rien qui le donne, et un classement se relit
mot par mot. Les cartes gardent la teinte de leurs trois premières lettres.

## Le meuble n'est jamais attribué automatiquement

[MEUBLES] est écrit à la main, une ligne à la fois, relu contre la glose
**complète** que la carte affiche. Un dessin n'illustre qu'un sens : sur la
face réponse d'un outil de révision, une image qui tranche une polysémie est
activement fausse. Les mots à plusieurs sens n'en reçoivent donc pas, même
quand le premier se dessinerait très bien. Écartés à la relecture du
2026-11-07, avec leur glose :

- `mi` « voici, voilà, quel, mûr » (la recherche automatique y voyait « mur ») ;
- `mwa` « mois, règle », `règ` « règle » (la règle à tracer ou la règle à suivre) ;
- `bato` et `batiman` « bâtiment » (le navire ou l'immeuble) ;
- `kabann` « lit, haillon », `ponm` « pomme, jamalac, pomme malacca » ;
- `soup` « soupe, potage, souple », `kim` « bulle, mousse, écume » ;
- `bway` « garçon, boyau… », `pyès` « champ, pièce », `mitan` « milieu, centre » ;
- `voumtak` « pébroc, parasol, tumulte » ;
- `granbwa` « forêt » : le dessin est une forêt de sapins, et une forêt de
  Guadeloupe se peint de l'intérieur, jamais en cônes (propriétaire) ;
- `potomitan` « pilier central de la case ou du temple » : c'est aussi le nom
  et le logo de l'éditeur, qu'un pilier générique ne doit pas remplacer.

Aucun mot n'est attribué au feu d'artifice de la bibliothèque : le
propriétaire n'en veut nulle part dans l'application (2026-10-19).
"""

import json
import os
import re
import sys
import unicodedata

RACINE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ACTIFS = os.path.join(RACINE, 'android_keyboard', 'app', 'src', 'main', 'assets')
CARNET = os.path.join(RACINE, 'android_keyboard', 'app', 'src', 'main', 'java',
                      'com', 'example', 'kreyolkeyboard', 'carnet')
SORTIE = os.path.join(ACTIFS, 'creole_blasons.json')
SERIES = os.path.join(ACTIFS, 'creole_series.json')

# Les meubles qui ne s'attribuent jamais, quoi qu'en dise une relecture future.
INTERDITS = {'feux_artifice'}

# ---------------------------------------------------------------------------
# La table, mot par mot. La glose affichée par la carte est en commentaire :
# c'est contre elle que chaque ligne a été relue.
# ---------------------------------------------------------------------------
MEUBLES = {
    # Le corps
    'men': 'main',            # main
    'lanmen': 'main',         # main
    'pyé': 'pied',            # pied
    'bouch': 'bouche',        # bouche
    'dan': 'dent',            # dent
    # Les gens
    'fanm': 'femme',          # femme
    'madanm': 'femme',        # madame, femme adulte, épouse, concubine, femme
    'boug': 'homme',          # homme, type, gars (familier)
    'fi': 'fille',            # fille
    'manman': 'mere',         # mère
    'papa': 'pere',           # père
    'fanmi': 'famille',       # famille, parent, membre de la même famille, de la parentèle
    # La nature
    'dlo': 'eau',             # eau
    'solèy': 'soleil',        # soleil
    'lapli': 'pluie',         # pluie
    'loraj': 'orage',         # orage
    'pyébwa': 'arbre',        # arbre
    # Les bêtes
    'pwason': 'poisson',      # poisson
    'chouval': 'cheval',      # cheval
    'myèl': 'abeille',        # abeille
    # La maison, la ville
    'kaz': 'maison',          # maison, case
    'lari': 'rue',            # rue
    'boutik': 'boutique',     # boutique
    'lolo': 'boutique',       # boutique, épicerie
    'rèstoran': 'restaurant', # restaurant
    'fanmasi': 'pharmacie',   # pharmacie
    'lopital': 'hopital',     # hôpital
    'légliz': 'eglise',       # église
    'lajòl': 'prison',        # prison
    'avyon': 'avion',         # avion
    'drapo': 'drapeau',       # drapeau
    # Les choses
    'vè': 'verre',            # verre
    'foto': 'photo',          # photo, photographie
    'liv': 'livre',           # livre
    'téléfòn': 'telephone',   # téléphone
    'kado': 'cadeau',         # cadeau, présent, don
    'wòb': 'robe',            # robe, vêtement long
    'chimiz': 'chemise',      # chemise
    'soulyé': 'chaussure',    # chaussure
    'matématik': 'mathematiques',  # mathématiques
    # À boire, à manger
    'kafé': 'cafe',           # café, graines de caféier
    'diven': 'vin',           # vin
    'moltani': 'soupe',       # soupe
}


def lire(nom):
    with open(os.path.join(ACTIFS, nom), encoding='utf-8') as f:
        return json.load(f)


def plier(s):
    """Comme `TranslationDictionary` : minuscules, sans accents."""
    return ''.join(c for c in unicodedata.normalize('NFD', s.lower())
                   if unicodedata.category(c) != 'Mn')


def meubles_dessines():
    """Les noms que `Meubles.chemin` sait tracer, relus dans le code Kotlin."""
    noms = set()
    for fichier in ('MeublesDomaine.kt', 'Meubles.kt'):
        with open(os.path.join(CARNET, fichier), encoding='utf-8') as f:
            noms |= set(re.findall(r'^\s*"([a-z_]+)" to ', f.read(), re.M))
    return noms


def mots_jouables(dictionnaire, gloses):
    """
    Les formes qu'un jeu peut donner en carte.

    Mots mêlés, Mots mélangés et Mo an Karénaj tirent parmi les formes glosées
    du dictionnaire, de 3 à 10 lettres à eux trois ; Fraz a twou, Mokwaré et
    Mo an plas lisent leur actif.
    """
    glosees = {plier(k) for k in gloses}
    jouables = {m for m in dictionnaire if 3 <= len(m) <= 10 and plier(m) in glosees}
    for item in lire('creole_cloze.json')['items']:
        jouables.add(item['a'])
    for nom in ('creole_crossword.json', 'creole_chassecroise.json'):
        for grille in lire(nom)['grilles']:
            for mot in grille['mots']:
                jouables.add(mot['f'])
    return jouables


def main():
    dictionnaire = [m for m, _ in lire('creole_dict.json')]
    gloses = lire('creole_translations.json')['translations']
    dessines = meubles_dessines()

    erreurs = []
    for mot, meuble in MEUBLES.items():
        if meuble in INTERDITS:
            erreurs.append(f'{mot} : le meuble « {meuble} » est interdit')
        if meuble not in dessines:
            erreurs.append(f'{mot} : le meuble « {meuble} » n’est pas dessiné')
        if mot not in gloses:
            erreurs.append(f'{mot} : pas de glose, la carte ne peut pas exister')
    if erreurs:
        print('\n'.join(erreurs), file=sys.stderr)
        sys.exit(1)

    jouables = mots_jouables(dictionnaire, gloses)
    enluminures = sorted({m for mot, m in MEUBLES.items() if mot in jouables})
    hors_jeu = sorted(mot for mot in MEUBLES if mot not in jouables)

    with open(SORTIE, 'w', encoding='utf-8') as f:
        json.dump({
            'version': 1,
            'note': 'Généré par Dictionnaires/generate_blasons.py : ne pas modifier à la main.',
            'blasons': {mot: {'m': MEUBLES[mot]} for mot in sorted(MEUBLES)},
        }, f, ensure_ascii=False, indent=1)
        f.write('\n')
    with open(SERIES, 'w', encoding='utf-8') as f:
        json.dump({'enluminures': enluminures}, f, ensure_ascii=False, indent=1)
        f.write('\n')

    print(f'{len(MEUBLES)} mots dessinés, {len(set(MEUBLES.values()))} dessins, '
          f'{len(enluminures)} dans la série des enluminures')
    if hors_jeu:
        print('Dessinés mais donnés par aucun jeu : ' + ', '.join(hors_jeu))


if __name__ == '__main__':
    main()
