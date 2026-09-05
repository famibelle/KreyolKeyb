#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
📖 TRADUCTIONS — glose française des mots kréyòl
================================================

Produit `android_keyboard/app/src/main/assets/creole_translations.json`, la
table qui permet à l'onglet Dictionnaire et aux quatre jeux de dire ce que veut
dire un mot. Sans elle, Mots Mêlés et Mots Mélangés font retrouver « KAPTÈ »
sans jamais préciser qu'il s'agit d'un capteur : on y exerce son orthographe,
jamais son vocabulaire.

    cd Dictionnaires
    python generate_translations.py --strict

Comme `generate_cloze.py`, ce script ne reconstruit rien : il **consomme** le
dictionnaire déjà livré dans les actifs et n'y ajoute aucun mot. Il faut donc
l'exécuter APRÈS `KreyolComplet.py`, sinon la table gloserait des formes qui
n'y sont plus et laisserait les nouvelles sans rien.

Ce que le kréyòl change par rapport au portage
----------------------------------------------

Le mécanisme vient du Lëtzebuergesch Clavier, qui partage cette base de code.
Là-bas, une source unique et officielle — le LOD du Zenter fir d'Lëtzebuerger
Sprooch, en CC0 — glose 55 % des formes et 90 % des occurrences. **Le kréyòl
guadeloupéen n'a pas d'équivalent** : les dictionnaires de référence (Ludwig,
Montbrand, Poullet, Telchid ; Orphie) sont sous droit d'auteur, Diko Kréyol
n'annonce aucune licence de réutilisation, et data.gouv.fr ne publie rien.

On assemble donc deux sources libres, toutes deux en CC BY-SA 4.0 (voir
`gloses_source.py`), et on livre 38 % des occurrences là où LuxKeyb en couvre
90 %. C'est le plafond du réutilisable, pas un réglage.

Trois règles décident de la glose, dans cet ordre :

1. **Kreyolopedia d'abord**, parce que ses fiches sont écrites à la main,
   sourcées, et portent phonétique et exemples. Elle n'apporte que neuf mots
   que le Wiktionnaire n'a pas, mais ce sont les neuf mieux documentés.
2. **Sinon la définition de la page guadeloupéenne du Wiktionnaire.**
3. **Sinon l'inversion des sections « Traductions » des pages françaises.**
   C'est le seul chemin qui atteint `moun` : il n'a pas de page à lui, la page
   « personne » le cite. Ce chemin double la couverture à lui seul.

La table glose **toutes** les formes trouvées, y compris celles que le corpus
n'a jamais employées et qui ne sont donc pas dans `creole_dict.json` :
l'onglet Dictionnaire y gagne le double d'entrées, et les jeux, qui ne tirent
que dans le dictionnaire, n'en voient pas la différence.

Attribution : CC BY-SA impose de citer la source et de partager à l'identique.
Elle voyage dans l'actif (`attribution`), s'affiche en pied de l'onglet
Dictionnaire, et chaque entrée porte le code de la source qui l'a fournie. Ne
retirer ni l'un ni l'autre. Voir `Dictionnaires/GLOSES.md`.

Fait avec ❤️ pour préserver le Kréyòl
"""

import json
import sys
import unicodedata
from datetime import datetime
from pathlib import Path

from gloses_source import (ATTRIBUTION, LONGUEUR_GLOSE, abaisser_initiale,
                           charger_kreyolopedia, charger_wiktionnaire,
                           nettoyer_definition, raccourcir)

RACINE = Path(__file__).resolve().parent.parent
RACINE_ASSETS = RACINE / "android_keyboard/app/src/main/assets"
CHEMIN_DICT = RACINE_ASSETS / "creole_dict.json"
CHEMIN_TRAD = RACINE_ASSETS / "creole_translations.json"
DOSSIER_BACKUPS = Path(__file__).resolve().parent / "backups"

# Codes de source, écrits dans chaque entrée. Un seul caractère : répété
# 1 100 fois, un libellé lisible pèserait plus que les gloses elles-mêmes, et
# c'est le code qui décide du lien de la fiche.
SOURCE_KREYOLOPEDIA = "K"
SOURCE_WIKTIONNAIRE = "W"   # page guadeloupéenne du mot
SOURCE_TRADUCTION = "T"     # cité en traduction par une page française

# Nombre maximal d'acceptions gardées par mot. Une seule ampute « manjé »
# (manger / nourriture) d'un sens que le joueur croira faux ; au-delà de trois,
# la ligne déborde de l'écran d'un téléphone.
MAX_ACCEPTIONS = 3

# Plancher de livraison en mode strict. Mesuré à 1 149 formes le 5 septembre
# 2026, dont 520 dans le dictionnaire du clavier ; en dessous de 800, une des
# deux passes du Wiktionnaire a échoué sans le dire.
MINIMUM_STRICT = 800


def plier(texte):
    """Casse et accents retirés, comme `AccentTolerantMatcher.normalize()`."""
    sans_accent = unicodedata.normalize("NFD", texte.lower())
    return "".join(c for c in sans_accent
                   if unicodedata.category(c) != "Mn")


def charger_dictionnaire():
    """Le dictionnaire livré, mot → fréquence. Sert à ordonner la table et à
    mesurer la couverture, jamais à filtrer les entrées."""
    with open(CHEMIN_DICT, encoding="utf-8") as f:
        return {mot: freq for mot, freq in json.load(f)}


# ═══════════════════════════════════════════════════════════════════════════
# Construction de la table
# ═══════════════════════════════════════════════════════════════════════════

# Longueur au-delà de laquelle la définition entière est elle-même coupée. La
# fiche a de la place, l'écran d'un téléphone non : trois paragraphes de
# botanique sur `chou` ne s'y liraient pas mieux qu'ailleurs.
LONGUEUR_DEFINITION = 300


def definition_entiere(glose, entieres):
    """La définition complète, quand elle dit plus que la glose.

    La glose tient sous le mot, en une ligne : « gwoka → musique ». La fiche,
    elle, a la place de dire « musique, chants et danses traditionnels de
    Guadeloupe joués sur le tambour ka ». Ce champ ne voyage donc que lorsqu'il
    apporte quelque chose, ce qui laisse la table petite pour les mille formes
    dont la glose est déjà toute la définition.
    """
    entiere = " ; ".join(e for e in entieres if e)
    if len(entiere) > LONGUEUR_DEFINITION:
        coupe = entiere.rfind(" ", 0, LONGUEUR_DEFINITION)
        entiere = (entiere[:coupe] if coupe > 0
                   else entiere[:LONGUEUR_DEFINITION]) + "…"
    return {"d": entiere} if entiere and entiere != glose else {}


def joindre_acceptions(acceptions):
    """Trois acceptions au plus, et une ligne qui tienne à l'écran.

    Chaque acception a déjà été raccourcie séparément ; c'est leur mise bout à
    bout qui déborde. On retire par la fin, la première étant toujours le sens
    principal : « pilier central de la case ou du temple, au figuré » se réduit
    ainsi à sa première moitié, qui est la seule lisible.
    """
    gardees = acceptions[:MAX_ACCEPTIONS]
    while len(gardees) > 1 and len(", ".join(gardees)) > LONGUEUR_GLOSE:
        gardees.pop()
    return ", ".join(gardees)


def entree_kreyolopedia(fiche):
    """Une fiche Kreyolopedia rendue au format de l'actif, ou None."""
    definition = (fiche.get("definition") or "").strip()
    if not definition:
        return None

    # Une fiche peut porter plusieurs sens séparés par un point-virgule
    # (« Travail ; travailler »), ce que le Wiktionnaire écrit avec des lignes
    # distinctes. Les deux se ramènent ici à la même liste d'acceptions.
    acceptions, entieres = [], []
    for morceau in definition.split(";"):
        propre, nom_propre = nettoyer_definition(morceau)
        if propre:
            entieres.append(abaisser_initiale(propre, nom_propre))
        court = raccourcir(propre)
        if court:
            acceptions.append(abaisser_initiale(court, nom_propre))
    if not acceptions:
        return None

    entree = {"g": joindre_acceptions(acceptions), "s": SOURCE_KREYOLOPEDIA}
    entree.update(definition_entiere(entree["g"], entieres))
    if fiche.get("url"):
        entree["u"] = fiche["url"]
    if fiche.get("phonetique"):
        entree["p"] = fiche["phonetique"]

    # Les exemples de Kreyolopedia sont une chaîne, une phrase par ligne, le
    # kréyòl et sa traduction séparés par un tiret cadratin. On les livre tels
    # quels : c'est la fiche d'origine qui les a écrits, pas nous.
    exemples = [ligne.strip()
                for ligne in (fiche.get("exemples") or "").split("\n")
                if ligne.strip()]
    if exemples:
        entree["x"] = exemples[:2]
    return entree


def entree_definition(definitions):
    """Les lignes `#` d'une page guadeloupéenne, ramenées à une glose."""
    acceptions, entieres = [], []
    for texte, nom_propre in definitions[:MAX_ACCEPTIONS]:
        entieres.append(abaisser_initiale(texte, nom_propre))
        court = raccourcir(texte)
        if court:
            acceptions.append(abaisser_initiale(court, nom_propre))
    if not acceptions:
        return None

    entree = {"g": joindre_acceptions(acceptions), "s": SOURCE_WIKTIONNAIRE}
    entree.update(definition_entiere(entree["g"], entieres))
    return entree


def entree_traduction(mot, pages):
    """Les têtes françaises qui citent ce mot en traduction.

    Triées de la plus courte à la plus longue : sur `manjé`, cela met
    « manger » devant « culture vivrière ». Une tête plus courte est presque
    toujours le sens de base, la plus longue une spécialisation.
    """
    plie = plier(mot)
    candidates = sorted({p for p in pages if plier(p) != plie},
                        key=lambda p: (len(p), p))
    if not candidates:
        return None

    gardees = []
    for candidate in candidates[:MAX_ACCEPTIONS]:
        if gardees and len(", ".join(gardees + [candidate])) > LONGUEUR_GLOSE:
            break
        gardees.append(candidate)
    return {"g": ", ".join(gardees), "s": SOURCE_TRADUCTION,
            "u": candidates[0]}


def instructive(mot, glose):
    """La glose apprend-elle quelque chose ?

    Le kréyòl emprunte massivement au français, et le Wiktionnaire glose parfois
    un mot par lui-même : sa page `pou` ne connaît que l'insecte, donc
    « pou → pou ». C'est exact et sans le moindre intérêt, et dans un onglet
    qui s'appelle Dictionnaire cela se lit comme une panne. Ces entrées sont
    donc écartées ici plutôt que gardées et filtrées à l'affichage.

    La comparaison plie casse et accents, comme `AccentTolerantMatcher` :
    sinon « manjé → Manjé » passerait au travers.
    """
    plie = plier(mot)
    return any(plier(part.strip()) != plie for part in glose.split(","))


def construire(kreyolopedia, wiktionnaire, dico):
    """La table complète, la source la mieux relue l'emportant."""
    print("\n📖 ASSEMBLAGE DES GLOSES")
    print("-" * 45)

    table, origines = {}, {SOURCE_KREYOLOPEDIA: 0,
                           SOURCE_WIKTIONNAIRE: 0,
                           SOURCE_TRADUCTION: 0}
    muettes = []

    def retenir(mot, entree):
        if not entree or mot in table:
            return
        if not instructive(mot, entree["g"]):
            muettes.append(mot)
            return
        table[mot] = entree

    for fiche in kreyolopedia:
        mot = (fiche.get("mot") or "").strip()
        if mot:
            retenir(mot, entree_kreyolopedia(fiche))

    for mot, definitions in wiktionnaire["definitions"].items():
        retenir(mot, entree_definition(definitions))

    for mot, pages in wiktionnaire["traductions"].items():
        retenir(mot, entree_traduction(mot, pages))

    for entree in table.values():
        origines[entree["s"]] += 1

    # Un mot rejeté sur sa meilleure source peut revenir par la suivante :
    # la page guadeloupéenne de `chou` ne connaît que le légume, donc
    # « chou → chou », mais la page française « ouste » le cite en traduction.
    # Ne sont perdues que les formes qu'aucune des trois n'a su gloser
    # autrement que par elles-mêmes.
    perdues = sorted(set(muettes) - set(table))
    if perdues:
        print(f"   ⊘ glosées par elles-mêmes, écartées : "
              f"{', '.join(perdues[:8])}"
              f"{'…' if len(perdues) > 8 else ''} ({len(perdues)})")

    print(f"   🇰 Kreyolopedia          : {origines[SOURCE_KREYOLOPEDIA]:>5}")
    print(f"   🇼 pages guadeloupéennes : {origines[SOURCE_WIKTIONNAIRE]:>5}")
    print(f"   🇹 par les traductions   : {origines[SOURCE_TRADUCTION]:>5}")
    print(f"   📖 total                 : {len(table):>5} formes glosées")

    # Ordre du fichier : les mots du dictionnaire d'abord, du plus fréquent au
    # plus rare, puis le reste par ordre alphabétique. Ce n'est pas cosmétique.
    # Android lit la table dans l'ordre du fichier pour construire son repli en
    # minuscules : à graphie identique, c'est la forme la plus courante qui
    # doit gagner.
    ordonnee = sorted(table, key=lambda m: (-dico.get(m, 0), m))
    return {mot: table[mot] for mot in ordonnee}


def mesurer(table, dico):
    """Ce que la table couvre du dictionnaire livré."""
    print("\n📊 COUVERTURE DU DICTIONNAIRE")
    print("-" * 45)

    glosees = [mot for mot in dico if mot in table]
    total = sum(dico.values())
    occurrences = sum(dico[mot] for mot in glosees)
    tries = sorted(dico, key=lambda m: -dico[m])
    tete = sum(1 for mot in tries[:200] if mot in table)

    print(f"   {len(glosees)} des {len(dico)} mots du clavier sont glosés "
          f"({100 * len(glosees) / len(dico):.1f} %)")
    print(f"   {100 * occurrences / total:.1f} % des occurrences du corpus")
    print(f"   {tete} des 200 mots les plus fréquents")
    print(f"   {len(table) - len(glosees)} formes glosées hors dictionnaire, "
          f"consultables dans l'onglet Dictionnaire")
    return len(glosees)


def valider(table):
    """Contrôles qui doivent tenir avant d'écrire quoi que ce soit."""
    print("\n🔎 VALIDATION")
    print("-" * 45)

    codes = {SOURCE_KREYOLOPEDIA, SOURCE_WIKTIONNAIRE, SOURCE_TRADUCTION}
    erreurs = []
    for mot, entree in table.items():
        glose = entree.get("g", "")
        if not glose.strip():
            erreurs.append(f"« {mot} » : glose vide")
        if entree.get("s") not in codes:
            erreurs.append(f"« {mot} » : source « {entree.get('s')} » inconnue")
        if len(glose) > LONGUEUR_GLOSE * 2:
            erreurs.append(f"« {mot} » : glose de {len(glose)} caractères")
        if glose and not instructive(mot, glose):
            erreurs.append(f"« {mot} » : glosé par lui-même")

    if erreurs:
        for erreur in erreurs[:20]:
            print(f"   ❌ {erreur}")
        print(f"   ❌ {len(erreurs)} erreurs au total")
        return False

    print(f"   ✅ {len(table)} entrées valides")
    return True


def sauvegarder(table):
    """Écrit l'actif, après copie horodatée de la version précédente."""
    print("\n💾 ÉCRITURE DE L'ACTIF")
    print("-" * 45)

    if CHEMIN_TRAD.exists():
        DOSSIER_BACKUPS.mkdir(exist_ok=True)
        horodatage = datetime.now().strftime("%Y%m%d_%H%M%S")
        copie = DOSSIER_BACKUPS / f"creole_translations_{horodatage}.json"
        copie.write_bytes(CHEMIN_TRAD.read_bytes())
        print(f"   🗄️  sauvegarde : {copie.name}")

    charge = {
        "version": 1,
        "generated": datetime.now().strftime("%Y-%m-%d"),
        "licence": "CC BY-SA 4.0",
        "licence_url": "https://creativecommons.org/licenses/by-sa/4.0/deed.fr",
        # Recopiée dans l'actif pour que l'attribution voyage avec les gloses,
        # y compris si le fichier est lu hors du dépôt. CC BY-SA l'exige.
        "attribution": ATTRIBUTION,
        "count": len(table),
        "translations": table,
    }
    with open(CHEMIN_TRAD, "w", encoding="utf-8") as f:
        json.dump(charge, f, ensure_ascii=False, separators=(",", ":"))

    taille = CHEMIN_TRAD.stat().st_size
    print(f"   ✅ {CHEMIN_TRAD.name} — {len(table)} formes, "
          f"{taille / 1024:.0f} Ko")


def main():
    strict = "--strict" in sys.argv
    hors_ligne = "--hors-ligne" in sys.argv

    print("📖 TRADUCTIONS — GLOSE FRANÇAISE DES MOTS KRÉYÒL 📖")
    print("=" * 70)
    print(f"Date: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print("=" * 70)

    try:
        dico = charger_dictionnaire()
    except Exception as erreur:
        print(f"\n❌ Dictionnaire illisible : {erreur}")
        print("   Lancez d'abord `python KreyolComplet.py`.")
        return 1

    print(f"\n🌐 SOURCES ({len(dico)} mots à gloser)")
    print("-" * 45)
    try:
        kreyolopedia, frais_k = charger_kreyolopedia(hors_ligne)
        wiktionnaire, frais_w = charger_wiktionnaire(hors_ligne)
    except Exception as erreur:
        print(f"\n❌ {erreur}")
        return 1

    # Le mode strict refuse le cache pour la même raison que `--rapport-seul`
    # refuse l'instantané local dans `KreyolComplet.py` : une table
    # régénérée sur des données figées serait quand même datée d'aujourd'hui.
    if strict and not (frais_k and frais_w):
        print("\n❌ Mode strict : au moins une source vient du cache.")
        return 1

    table = construire(kreyolopedia, wiktionnaire, dico)
    if not table:
        print("\n❌ Aucune glose produite, rien n'est écrit.")
        return 1

    mesurer(table, dico)
    if not valider(table):
        print("\n❌ Validation échouée, rien n'est écrit.")
        return 1

    if strict and len(table) < MINIMUM_STRICT:
        print(f"\n❌ Mode strict : {len(table)} formes seulement, "
              f"attendu au moins {MINIMUM_STRICT}.")
        return 1

    sauvegarder(table)
    print("\n🎉 TERMINÉ")
    return 0


if __name__ == "__main__":
    sys.exit(main())
