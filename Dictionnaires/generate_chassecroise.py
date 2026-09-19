#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
🇬🇵 MO AN PLAS — génération des grilles de chassé-croisé
========================================================

Produit `android_keyboard/app/src/main/assets/creole_chassecroise.json`,
l'actif du sixième jeu : une grille vide, la liste des mots à y caser, et
aucune définition. Le joueur déduit des longueurs et des croisements.

    cd Dictionnaires
    python generate_chassecroise.py --strict

Comme `generate_crossword.py`, dont il réutilise le moteur de placement, ce
script ne reconstruit rien : il **consomme** les actifs déjà livrés, le
dictionnaire de fréquences pour les mots et `creole_translations.json` pour les
gloses. Il tourne hors ligne, après `KreyolComplet.py` et
`generate_translations.py`.

Ce que ce jeu apporte et qu'aucun autre n'apporte
--------------------------------------------------

C'est le **seul jeu jouable sans connaître un mot de kréyòl**. Les cinq autres
supposent une compréhension préalable, ne serait-ce que pour lire la
définition : Mokwaré demande de produire l'orthographe à partir du sens, Fraz a
twou et Mo an Karénaj font choisir, Mots Mêlés et Mots Mélangés montrent le mot
mais il faut savoir ce qu'on cherche. Ici les mots sont donnés et la déduction
est géométrique, donc un débutant complet entre par cette porte.

Trois choix qui décident de la qualité des grilles
---------------------------------------------------

1. **La solution doit être unique**, et c'est l'invariant propre à ce jeu.
   Si deux mots de même longueur peuvent s'échanger sans contredire un
   croisement, la grille a deux solutions et le joueur qui trouve la seconde se
   voit refuser une réponse juste. `solution_unique()` énumère les affectations
   possibles et rejette la grille au deuxième succès. Cela ne se voit pas à
   l'œil sur un JSON valide, et le vivier créole rend la contrainte plus chère
   qu'ailleurs : voir le point 3.

2. **La difficulté porte sur la géométrie, pas sur la rareté du vocabulaire.**
   Les mots étant affichés, leur fréquence n'a plus rien à voir avec la
   difficulté du jeu : ce qui la fait, c'est le nombre de mots de même longueur
   (une longueur unique désigne son emplacement toute seule) et la taille de la
   grille. Aucun plancher de fréquence, donc, là où Mokwaré en relève un en
   Facile — et c'est heureux, parce que ce plancher coûterait ici l'essentiel
   du vivier (78 mots au-delà de la fréquence 20, contre 471 en tout).

3. **Le vivier est de 471 mots, treize fois plus petit que le luxembourgeois**
   (6 001), et c'est lui qui commande tous les réglages en dessous. Il ne se
   corrige pas en desserrant un filtre : les 3 574 formes écartées le sont
   faute de glose, et la contrainte d'alphabet, elle, ne coûte que 7 formes
   (six mots composés à trait d'union, `a-y`, `an-nou`, `dòktè-fèy`, plus
   `jandàm`), qui ne se caseraient pas dans une grille de toute façon. Elle est
   donc gardée telle quelle bien que ce jeu, n'ayant pas de pavé de saisie,
   pourrait s'en passer.

Un filtre de `generate_crossword.py` tombe en revanche : la glose est ici la
**récompense** et non l'indice, montrée une fois le mot verrouillé, donc une
acception qui répète le mot serait une confirmation honnête et non un cadeau.
Le mesurer ne rapporte rien — les gloses sont françaises et les mots créoles,
si bien que `definition_de()` ne rejette **aucune** des 471 formes. Le filtre
est retiré parce qu'il n'a plus de raison d'être, pas parce qu'il coûtait.

Attribution : les gloses viennent de Kreyolopedia et du Wiktionnaire français
(CC BY-SA 4.0), les mots et leurs fréquences du corpus POTOMITAN/PawolKreyol-gfc.
Voir `GLOSES.md`. Le jeu affiche ces crédits ; ne pas les retirer.

Fait avec ❤️ pour préserver le Kréyòl
"""

import json
import os
import random
import sys
from collections import Counter, defaultdict
from datetime import datetime
from pathlib import Path

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from generate_crossword import (
    ACCEPTIONS_MAX,
    ALPHABET,
    ESSAIS_PAR_GRILLE,
    LONGUEUR_MAX,
    LONGUEUR_MAX_ACCEPTION,
    LONGUEUR_MIN,
    MOTS_MIN_LIVRABLE,
    NOMS_PROPRES,
    _cases,
    _construire_une_grille,
    _densite,
    _dun_seul_tenant,
    _suites,
    charger_actifs,
    glose_de,
    plier,
)

if sys.platform.startswith('win'):
    import codecs
    sys.stdout = codecs.getwriter('utf-8')(sys.stdout.buffer, 'strict')
    sys.stderr = codecs.getwriter('utf-8')(sys.stderr.buffer, 'strict')

RACINE_ASSETS = Path(__file__).resolve().parent.parent / \
    "android_keyboard/app/src/main/assets"
CHEMIN_GRILLES = RACINE_ASSETS / "creole_chassecroise.json"
DOSSIER_BACKUPS = Path(__file__).resolve().parent / "backups"

# Graine fixe, pour la même raison que dans les autres générateurs : deux
# exécutions sur les mêmes actifs doivent produire le même fichier.
GRAINE = 20260919

# Les trois niveaux, gradués sur la seule géométrie.
#
# `collisions` est la part visée de mots qui partagent leur longueur avec un
# autre mot de la même grille. C'est *la* mesure de difficulté d'un
# chassé-croisé : un mot dont la longueur est unique se pose sans réfléchir, et
# une grille dont toutes les longueurs sont uniques se remplit sans déduction.
# La valeur n'est pas une borne mais une cible — parmi les grilles candidates
# qui passent l'unicité, on garde la plus proche. Une borne dure aurait fait
# échouer des niveaux entiers sans rien apporter.
#
# Les tailles et les nombres de mots sont ceux de Mokwaré (8/9/11, 6/8/10)
# plutôt que ceux du fork (9/10/11, 7/10/13) : le vivier créole ne porte que
# 199 mots d'au moins six lettres et 8 mots d'au moins dix, si bien qu'une
# grille de treize mots se tarit avant d'être remplie.
NIVEAUX = {
    1: {"nom": "Facile", "taille": 8, "mots": 6, "collisions": 0.25},
    2: {"nom": "Normal", "taille": 9, "mots": 8, "collisions": 0.55},
    3: {"nom": "Difficile", "taille": 11, "mots": 10, "collisions": 0.85},
}

# Grilles visées par niveau, comme Mokwaré. Le vivier étant mince, on n'atteint
# pas toujours ce nombre ; le script livre ce qu'il a produit et la CI garde un
# plancher bas.
GRILLES_PAR_NIVEAU = 60

# Nombre de solutions au-delà duquel on cesse de chercher : deux suffisent à
# rejeter la grille, et énumérer les suivantes ne dirait rien de plus.
SOLUTIONS_MAX = 2

# En dessous, il n'y a pas de quoi construire des grilles variées. Le fork pose
# ce plancher à 1 000 ; le vivier créole entier en compte 471.
VIVIER_MIN = 300


def recompense_de(glose):
    """La glose montrée quand le mot est verrouillé, ou None s'il n'y en a pas.

    Une différence avec `definition_de()` de `generate_crossword.py`, et elle
    tient à ce que la glose arrive **après** le placement : rien n'exige plus
    qu'elle « apprenne quelque chose », puisque ce filtre existe pour les jeux
    qui font *chercher* le mot, pas pour celui qui le donne. Voir l'en-tête :
    le retirer ne rend aucune forme au vivier, il retire une règle qui n'avait
    plus de sens ici.

    Ce qui reste : les locutions trop longues débordent de l'écran d'un
    téléphone, donc on les laisse tomber s'il reste autre chose.
    """
    retenues = [a.strip() for a in glose.split(",") if a.strip()]
    if not retenues:
        return None
    courtes = [a for a in retenues if len(a) <= LONGUEUR_MAX_ACCEPTION]
    if courtes:
        retenues = courtes
    return ", ".join(retenues[:ACCEPTIONS_MAX])


def construire_vivier(dico, table):
    """Les mots casables, en un seul vivier.

    Mêmes exigences que Mokwaré — une forme du dictionnaire de fréquences,
    écrite dans l'alphabet de la grille, glosée, et pas un nom propre — moins
    l'exigence que la glose ne contienne pas le mot, et sans plancher de
    fréquence.
    """
    print("\n🔤 CONSTRUCTION DU VIVIER")
    print("-" * 45)

    rejets = Counter()
    vivier = []

    for mot, freq in dico.items():
        if not (LONGUEUR_MIN <= len(mot) <= LONGUEUR_MAX):
            rejets["longueur"] += 1
            continue

        if plier(mot) in NOMS_PROPRES:
            rejets["nom propre"] += 1
            continue

        majuscule = mot.upper()
        if any(c not in ALPHABET for c in majuscule):
            rejets["hors alphabet"] += 1
            continue

        meta = table.get(mot) or table.get(mot.lower())
        if not meta:
            rejets["sans glose"] += 1
            continue

        glose = glose_de(meta)
        if not glose.strip():
            rejets["glose vide"] += 1
            continue

        recompense = recompense_de(glose)
        if recompense is None:
            rejets["glose vide"] += 1
            continue

        vivier.append({
            "m": majuscule,
            "f": mot,
            "g": recompense,
            "freq": freq,
        })

    for motif, nombre in rejets.most_common():
        print(f"   ↩️  {nombre:>6} formes écartées, {motif}")
    print(f"   ✅ {len(vivier)} mots casables")

    longueurs = Counter(len(e["m"]) for e in vivier)
    detail = " · ".join(f"{n}:{longueurs[n]}" for n in sorted(longueurs))
    print(f"   📏 par longueur, {detail}")
    return vivier


def solution_unique(grille):
    """La grille se résout-elle d'une seule façon ?

    L'invariant de ce fichier. Un chassé-croisé ne donne pas de définitions :
    la seule chose qui désigne l'emplacement d'un mot est sa longueur et les
    lettres que ses croisements lui imposent. Si deux mots peuvent s'échanger
    en respectant tout cela, la grille a deux solutions — et comme le jeu ne
    peut en connaître qu'une, il refuse une réponse juste, ce qu'aucun joueur
    ne pardonne.

    L'énumération est un simple retour en arrière sur une bijection mots ↔
    emplacements. Elle tient parce qu'une grille compte au plus dix mots et que
    l'ordre d'essai attaque d'abord les emplacements les plus contraints :
    ceux dont peu de mots partagent la longueur, puis les plus croisés.
    """
    mots = grille["mots"]
    n = len(mots)
    lettres = [m["m"] for m in mots]
    cases = [_cases(m["m"], m["r"], m["c"], m["d"] == "H") for m in mots]

    # Pour chaque emplacement, les croisements : (rang dans ce mot, autre
    # emplacement, rang dans l'autre mot).
    occupants = defaultdict(list)
    for i, suite in enumerate(cases):
        for rang, case in enumerate(suite):
            occupants[case].append((i, rang))
    croisements = defaultdict(list)
    for occupation in occupants.values():
        if len(occupation) == 2:
            (a, i), (b, j) = occupation
            croisements[a].append((i, b, j))
            croisements[b].append((j, a, i))

    par_longueur = Counter(len(mot) for mot in lettres)
    ordre = sorted(
        range(n),
        key=lambda i: (par_longueur[len(lettres[i])], -len(croisements[i]))
    )

    affecte = [None] * n
    pris = [False] * n
    total = 0

    def poser(rang):
        nonlocal total
        if total >= SOLUTIONS_MAX:
            return
        if rang == n:
            total += 1
            return
        emplacement = ordre[rang]
        attendue = len(lettres[emplacement])
        for candidat, texte in enumerate(lettres):
            if pris[candidat] or len(texte) != attendue:
                continue
            if any(
                affecte[autre] is not None and lettres[affecte[autre]][j] != texte[i]
                for i, autre, j in croisements[emplacement]
            ):
                continue
            affecte[emplacement] = candidat
            pris[candidat] = True
            poser(rang + 1)
            affecte[emplacement] = None
            pris[candidat] = False
            if total >= SOLUTIONS_MAX:
                return

    poser(0)
    return total == 1


def taux_de_collision(grille):
    """Part des mots qui partagent leur longueur avec un autre de la grille.

    Zéro veut dire que chaque longueur désigne son emplacement toute seule et
    que la grille se remplit sans déduction ; un veut dire qu'aucun mot ne se
    place sans regarder ses croisements.
    """
    mots = grille["mots"]
    if not mots:
        return 0.0
    longueurs = Counter(len(m["m"]) for m in mots)
    partages = sum(1 for m in mots if longueurs[len(m["m"])] > 1)
    return partages / len(mots)


def construire_grilles(vivier, rng):
    """Construit la livraison, niveau par niveau.

    Pour chaque grille : plusieurs candidates, on écarte celles à solutions
    multiples, et parmi les survivantes on garde celle dont le taux de
    collision approche le mieux la cible du niveau. À taux égal, la plus dense
    l'emporte — une grille très croisée se déduit mieux qu'une étoile.

    Le plafond de réemploi d'un mot est celui de `_construire_une_grille()`,
    c'est-à-dire `MAX_GRILLES_PAR_MOT` de Mokwaré. Le fork en ajoute un second,
    plus serré, parce que son vivier est six fois trop grand pour le premier ;
    ici il est treize fois trop petit, et le resserrer taris­serait les niveaux.
    """
    print("\n🧩 CONSTRUCTION DES GRILLES")
    print("-" * 45)

    grilles = []
    for niveau, base in NIVEAUX.items():
        reglage = dict(base, niveau=niveau)
        usages = Counter()
        produites = 0
        rejets_unicite = 0
        collisions = []

        for _ in range(GRILLES_PAR_NIVEAU):
            meilleure = None
            meilleur_score = None
            for _ in range(ESSAIS_PAR_GRILLE):
                candidate = _construire_une_grille(vivier, usages, rng, reglage)
                if candidate is None:
                    continue
                if not solution_unique(candidate):
                    rejets_unicite += 1
                    continue
                ecart = abs(taux_de_collision(candidate) - reglage["collisions"])
                score = (-ecart, _densite(candidate))
                if meilleur_score is None or score > meilleur_score:
                    meilleure, meilleur_score = candidate, score
            if meilleure is None:
                continue
            for mot in meilleure["mots"]:
                usages[mot["m"]] += 1
            grilles.append(meilleure)
            collisions.append(taux_de_collision(meilleure))
            produites += 1

        moyenne_mots = 0
        moyenne_collision = 0
        if produites:
            recentes = grilles[-produites:]
            moyenne_mots = sum(len(g["mots"]) for g in recentes) / produites
            moyenne_collision = sum(collisions) / produites
        print(f"   ✅ {reglage['nom']:<10} {produites:>4} grilles, "
              f"{moyenne_mots:.1f} mots, collisions {moyenne_collision:.0%} "
              f"(cible {reglage['collisions']:.0%}), "
              f"{len(usages)} formes employées, "
              f"{rejets_unicite} candidates non uniques écartées")

    return grilles


def valider(grilles):
    """Contrôles qui doivent tenir avant d'écrire quoi que ce soit.

    Ceux de `generate_crossword.valider()`, moins celui sur la définition qui
    ne s'applique plus, plus l'unicité de la solution.
    """
    print("\n🔎 VALIDATION")
    print("-" * 45)

    erreurs = []
    for index, grille in enumerate(grilles):
        mots = grille["mots"]
        if len(mots) < MOTS_MIN_LIVRABLE:
            erreurs.append(f"#{index} : {len(mots)} mots seulement")

        cases = {}
        conflit = False
        for mot in mots:
            if len(mot["m"]) < LONGUEUR_MIN:
                erreurs.append(f"#{index} : « {mot['m']} » trop court")
            if any(c not in ALPHABET for c in mot["m"]):
                erreurs.append(f"#{index} : « {mot['m']} » hors alphabet")
            if not mot["g"].strip():
                erreurs.append(f"#{index} : « {mot['m']} » sans récompense")
            if mot["m"] != mot["f"].upper():
                erreurs.append(
                    f"#{index} : « {mot['m']} » ne majuscule pas « {mot['f']} »")
            for (r, c), lettre in zip(
                    _cases(mot["m"], mot["r"], mot["c"], mot["d"] == "H"), mot["m"]):
                if not (0 <= r < grille["h"] and 0 <= c < grille["w"]):
                    erreurs.append(f"#{index} : « {mot['m']} » déborde de la grille")
                    conflit = True
                    break
                if cases.setdefault((r, c), lettre) != lettre:
                    erreurs.append(f"#{index} : croisement contradictoire en ({r},{c})")
                    conflit = True

        if len(set(m["m"] for m in mots)) != len(mots):
            erreurs.append(f"#{index} : un mot est posé deux fois")

        # L'invariant hérité : rien ne se lit dans la grille qui ne soit un mot
        # posé. Il compte double ici — le joueur qui lit une suite non voulue
        # la cherche dans sa liste et ne l'y trouve pas.
        if not conflit:
            posees = Counter(m["m"] for m in mots)
            lues = Counter(_suites(grille))
            if posees != lues:
                intruses = [s for s in lues if s not in posees]
                erreurs.append(
                    f"#{index} : suites de lettres non voulues {intruses[:4]}")

        if mots and not _dun_seul_tenant(mots):
            erreurs.append(f"#{index} : la grille est en plusieurs morceaux")

        # L'invariant propre à ce jeu.
        if not conflit and not solution_unique(grille):
            erreurs.append(f"#{index} : la grille admet plusieurs solutions")

    if erreurs:
        for erreur in erreurs[:20]:
            print(f"   ❌ {erreur}")
        print(f"   ❌ {len(erreurs)} erreurs au total")
        return False

    total_mots = sum(len(g["mots"]) for g in grilles)
    formes = len({m["m"] for g in grilles for m in g["mots"]})
    moyenne = sum(taux_de_collision(g) for g in grilles) / len(grilles)
    print(f"   ✅ {len(grilles)} grilles valides, {total_mots} mots posés, "
          f"{formes} formes distinctes")
    print(f"   ✅ solution unique vérifiée sur les {len(grilles)} grilles, "
          f"collisions moyennes {moyenne:.0%}")
    return True


def sauvegarder(grilles, attribution_gloses):
    """Écrit l'actif, après copie horodatée de la version précédente."""
    print("\n💾 ÉCRITURE DE L'ACTIF")
    print("-" * 45)

    if CHEMIN_GRILLES.exists():
        DOSSIER_BACKUPS.mkdir(exist_ok=True)
        horodatage = datetime.now().strftime("%Y%m%d_%H%M%S")
        copie = DOSSIER_BACKUPS / f"creole_chassecroise_{horodatage}.json"
        copie.write_bytes(CHEMIN_GRILLES.read_bytes())
        print(f"   🗄️  sauvegarde : {copie.name}")

    charge = {
        "version": 1,
        "generated": datetime.now().strftime("%Y-%m-%d"),
        # Les crédits voyagent dans l'actif, comme pour les autres jeux : les
        # gloses viennent de Kreyolopedia et du Wiktionnaire (CC BY-SA, dont la
        # citation est une obligation de licence), les mots et leurs fréquences
        # du corpus PawolKreyol (Apache-2.0).
        "attribution": list(attribution_gloses) + [
            "POTOMITAN/PawolKreyol-gfc, Apache-2.0, textes d'auteurs "
            "guadeloupéens",
        ],
        "grilles": grilles,
    }
    with open(CHEMIN_GRILLES, "w", encoding="utf-8") as f:
        json.dump(charge, f, ensure_ascii=False, separators=(",", ":"))

    taille = CHEMIN_GRILLES.stat().st_size
    print(f"   ✅ {CHEMIN_GRILLES.name} — {len(grilles)} grilles, "
          f"{taille / 1024:.0f} Ko")


def main():
    strict = "--strict" in sys.argv

    print("🇬🇵 MO AN PLAS — GÉNÉRATION DES GRILLES DE CHASSÉ-CROISÉ 🇬🇵")
    print("=" * 70)
    print(f"Date: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print("=" * 70)

    rng = random.Random(GRAINE)

    try:
        dico, table, attribution = charger_actifs()
    except Exception as e:
        print(f"\n❌ Actifs illisibles : {e}")
        print("   Lancez d'abord `python KreyolComplet.py` "
              "puis `python generate_translations.py`.")
        return 1

    vivier = construire_vivier(dico, table)
    if len(vivier) < VIVIER_MIN:
        print(f"\n❌ Vivier trop maigre ({len(vivier)} mots, plancher "
              f"{VIVIER_MIN}) pour construire des grilles.")
        return 1

    grilles = construire_grilles(vivier, rng)
    if not grilles:
        print("\n❌ Aucune grille produite, rien n'est écrit.")
        return 1

    if not valider(grilles):
        print("\n❌ Validation échouée, rien n'est écrit.")
        return 1

    attendu = GRILLES_PAR_NIVEAU * len(NIVEAUX)
    if strict and len(grilles) < attendu * 0.5:
        print(f"\n❌ Mode strict : {len(grilles)} grilles seulement, "
              f"attendu ~{attendu}.")
        return 1

    sauvegarder(grilles, attribution)
    print("\n🎉 TERMINÉ")
    return 0


if __name__ == "__main__":
    sys.exit(main())
