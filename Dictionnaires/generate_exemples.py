#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
🃏 SANBLÉ — les phrases d'exemple au dos des cartes du carnet
=============================================================

Produit `android_keyboard/app/src/main/assets/creole_exemples.json` : pour
chaque mot glosé du dictionnaire, une à trois phrases authentiques du corpus
qui le montrent en situation, avec la source de chacune.

    cd Dictionnaires
    python generate_exemples.py --strict

Le script **consomme** les actifs livrés, il ne reconstruit rien : il lit
`creole_dict.json` et `creole_translations.json`, et doit donc tourner APRÈS
`KreyolComplet.py` et `generate_translations.py`. Il ne touche ni au
dictionnaire ni aux gloses.

Pourquoi le corpus et pas un dictionnaire
-----------------------------------------

Le carnet est repris du Lëtzebuergesch Clavier, où le dos d'une carte porte la
glose et **une phrase du LOD** : le dictionnaire officiel y fournit un exemple
rédigé par lexicographe pour chaque article. Le kréyòl n'a pas d'équivalent —
ni Kreyolopedia ni le Wiktionnaire ne livrent d'exemples exploitables en
volume. Le corpus, lui, en donne : ce sont des phrases écrites par des auteurs
guadeloupéens, donc de l'usage attesté plutôt qu'un exemple fabriqué, ce qui
vaut mieux pour le dos d'une carte.

Le prix de ce choix est l'attribution, et elle n'est pas négociable : chaque
phrase voyage avec sa source, la carte l'affiche, et `À Propos` porte les
crédits complets. C'est déjà la règle de *Fraz a twou*, qui montre des phrases
du même corpus depuis la 11.0.0 ; ce script ne fait que l'étendre au carnet.

Le vivier est celui du carnet : les mots glosés
------------------------------------------------

Seuls les mots qui portent une glose sont traités, parce que ce sont les seuls
qui peuvent devenir des cartes — les jeux ne tirent que là-dedans depuis la
12.0.0 (`TranslationDictionary.filtrerMotsTraduits`). Générer des phrases pour
les 4 674 autres formes gonflerait l'actif de mots qu'aucune carte ne portera
jamais.

Sur les 622 mots de ce vivier, **500 reçoivent au moins une phrase** avec la
bande de longueur retenue. Les 122 restants gardent la glose seule : une carte
muette de sa phrase reste une carte, exactement comme côté luxembourgeois où
0,7 % des formes n'ont pas d'exemple du LOD.

Les seuils, et ce qu'ils ont coûté
-----------------------------------

**La bande de longueur est 4 à 14 mots**, mesurée sur l'instantané local :

    3 à 18 mots  →  3 214 phrases,  524 mots couverts   (illisible sur carte)
    4 à 14 mots  →  2 609 phrases,  500 mots couverts   ← retenu
    5 à 12 mots  →  1 976 phrases,  477 mots couverts
    6 à 16 mots  →  1 887 phrases,  483 mots couverts   (la bande du cloze)

La borne haute est une contrainte de place, pas de langue : le panneau d'une
carte tient trois lignes sous la glose. La borne basse est plus basse que celle
du cloze (6) parce qu'ici la phrase n'a pas à *désigner* une réponse, elle a
seulement à montrer le mot au travail, et un proverbe de quatre mots le fait
très bien.

Le filtre de caractères est celui du cloze, et il coûte 9 mots de couverture
(509 → 500) : chiffres, crochets et guillemets signalent une incise ou une
citation coupée de son contexte, qui se lit mal isolée.

**Une occurrence capitalisée en milieu de phrase est refusée.** Le kréyòl ne
capitalise que les noms propres : `Viktò` dans une réplique n'illustre pas le
mot `viktò` de la carte. C'est le même signal que `generate_cloze.py` exploite,
appliqué ici à l'occurrence plutôt qu'au mot.

Attribution : les phrases livrées sont des extraits du corpus
POTOMITAN/PawolKreyol-gfc (Apache-2.0), fait de textes d'auteurs identifiés —
Sylviane Telchid, Sonny Rupaire, Max Rippon et d'autres. Chaque phrase porte sa
source dans le champ « src » ; ne pas la retirer.

Fait avec ❤️ pour préserver le Kréyòl
"""

import json
import os
import re
import sys
import unicodedata
from datetime import datetime
from pathlib import Path

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from KreyolComplet import KreyolPipelineUnique

RACINE = Path(__file__).resolve().parent.parent
RACINE_ASSETS = RACINE / "android_keyboard/app/src/main/assets"
CHEMIN_DICT = RACINE_ASSETS / "creole_dict.json"
CHEMIN_TRADUCTIONS = RACINE_ASSETS / "creole_translations.json"
CHEMIN_EXEMPLES = RACINE_ASSETS / "creole_exemples.json"
DOSSIER_BACKUPS = Path(__file__).resolve().parent / "backups"

# Découpage en phrases et repérage des mots : repris tels quels de
# `generate_cloze.py`, pour que les deux jeux d'actifs découpent le corpus de
# la même façon. Une divergence ici ferait apparaître dans le carnet des
# phrases que le cloze n'a jamais vues, et inversement.
SEPARATEUR_PHRASE = re.compile(r'(?<=[.!?…])\s+|\n+')
PATTERN_MOT = re.compile(
    r"[a-zA-ZàáâäèéêëìíîïòóôöùúûüçñÀÁÂÄÈÉÊËÌÍÎÏÒÓÔÖÙÚÛÜÇÑ'’\-]+"
)
CARACTERES_INTERDITS = set('0123456789()[]{}«»“”„"/\\|<>=+*#@_;:%§€$')

MOTS_MIN, MOTS_MAX = 4, 14

# Longueur idéale, en mots : la phrase la plus proche gagne. Huit mots tiennent
# sur deux lignes du panneau de carte à la taille de texte retenue.
LONGUEUR_IDEALE = 8

# Combien de phrases par mot. La carte n'en montre qu'une ; les suivantes
# servent à la révision, qui ne doit pas reposer six fois sur la même phrase.
# Au-delà de trois l'actif grossit sans que rien ne les lise.
PHRASES_PAR_MOT = 3

# Longueur du crédit court affiché sous la phrase, sur une carte large de
# 160 dp. Au-delà il passe à la ligne et mange la place de la glose.
CREDIT_MAX = 34

# Plancher du mode strict : en deçà, l'actif est tronqué et le carnet
# livrerait des cartes muettes sans que rien ne le signale.
PLANCHER_STRICT = 400


def plier(mot):
    """Le mot sans ses accents ni sa casse.

    `TranslationDictionary` cherche ses gloses de cette façon, et la table en
    couvre 109 formes de plus qu'une comparaison exacte. Le vivier du carnet
    doit donc se lire avec le même pli, sinon il est plus petit dans le
    générateur que dans l'application.
    """
    return ''.join(c for c in unicodedata.normalize('NFD', mot.lower())
                   if unicodedata.category(c) != 'Mn')


def crediter(source):
    """Le crédit court affiché sous la phrase, sur la carte.

    Les sources du corpus sont des références bibliographiques complètes, de
    quatre-vingts à cent trente caractères : éditeur, année, pagination. Elles
    restent dans l'actif — le champ `src` les garde entières, et l'écran
    « À Propos » porte les crédits complets, comme pour *Fraz a twou* — mais
    une carte ne peut en montrer qu'une ligne.

    La règle est celle que les références suivent déjà : ce qui précède la
    première virgule est l'auteur, ou à défaut le titre. Le corpus Potomitan
    est le seul à ne pas s'y plier, et il est nommé en clair plutôt que par
    son identifiant de jeu de données.
    """
    source = ' '.join(source.split())
    if source.startswith("POTOMITAN/"):
        return "Corpus Potomitan"
    tete = re.split(r'\s*[,(]|\s+https?://', source, maxsplit=1)[0].strip()
    tete = tete or source
    if len(tete) <= CREDIT_MAX:
        return tete
    coupe = tete[:CREDIT_MAX].rsplit(' ', 1)[0]
    return f"{coupe or tete[:CREDIT_MAX]}…"


def charger_vivier():
    """Les mots du dictionnaire qui portent une glose, dans l'ordre du dico.

    L'ordre est celui de la fréquence décroissante, donc l'actif produit est
    lisible de haut en bas comme le dictionnaire lui-même.
    """
    print("\n📚 LECTURE DES ACTIFS LIVRÉS")
    print("-" * 45)

    with open(CHEMIN_DICT, "r", encoding="utf-8") as f:
        brut = json.load(f)
    if not isinstance(brut, list):
        raise ValueError(f"{CHEMIN_DICT.name} doit être un tableau de paires")

    with open(CHEMIN_TRADUCTIONS, "r", encoding="utf-8") as f:
        table = json.load(f)
    gloses = table.get("translations") or {}
    if not gloses:
        raise ValueError(f"{CHEMIN_TRADUCTIONS.name} ne porte aucune glose")

    plies = {plier(forme) for forme in gloses}
    vivier = [paire[0] for paire in brut if plier(paire[0]) in plies]

    print(f"   ✅ {len(brut)} entrées de dictionnaire")
    print(f"   ✅ {len(gloses)} formes glosées")
    print(f"   ✅ {len(vivier)} mots du vivier (glosés ET au dictionnaire)")
    return vivier


def charger_corpus(strict):
    """Charge le corpus par le même chemin que le pipeline, et le découpe.

    En mode strict, le repli sur l'instantané local est refusé : des phrases
    périmées au dos d'une carte se lisent exactement comme les autres, rien ne
    les distinguerait. Même règle que `generate_cloze.py --strict`.
    """
    print("\n📖 CHARGEMENT DU CORPUS")
    print("-" * 45)

    pipeline = KreyolPipelineUnique()
    if not pipeline.charger_textes_kreyol():
        return None
    if strict and getattr(pipeline, "source_chargement", None) != "Hugging Face":
        print("\n❌ Mode strict : corpus chargé depuis l'instantané local.")
        print("   Les phrases livrées seraient peut-être périmées, et rien")
        print("   dans le carnet ne le montrerait. Vérifiez HF_TOKEN.")
        return None

    vues = set()
    phrases = []
    for entree in pipeline.textes_kreyol:
        texte = (entree.get("Texte") or "").strip()
        source = (entree.get("Source") or "").strip() or "Corpus PawolKreyol"
        if not texte:
            continue
        for morceau in SEPARATEUR_PHRASE.split(texte):
            morceau = morceau.strip()
            if not morceau or morceau in vues:
                continue
            vues.add(morceau)
            phrases.append((morceau, source))

    print(f"\n📊 {len(phrases)} phrases uniques issues de "
          f"{len(pipeline.textes_kreyol)} entrées")
    return phrases


def phrase_recevable(phrase):
    """La phrase peut-elle tenir au dos d'une carte."""
    if CARACTERES_INTERDITS & set(phrase):
        return False
    return MOTS_MIN <= len(phrase.split()) <= MOTS_MAX


def indexer(phrases):
    """Pour chaque forme pliée, les phrases recevables qui la contiennent.

    Une seule passe sur le corpus : le vivier fait 622 mots et les phrases
    2 600, un test mot par phrase coûterait 1,6 million de comparaisons pour
    le même résultat.

    L'occurrence capitalisée en milieu de phrase est écartée ici, et pas au
    moment du choix : c'est une propriété de la rencontre entre ce mot et
    cette phrase, pas de l'une ou de l'autre.
    """
    print("\n🔎 INDEXATION")
    print("-" * 45)

    index = {}
    retenues = 0
    for phrase, source in phrases:
        if not phrase_recevable(phrase):
            continue
        retenues += 1
        jetons = list(PATTERN_MOT.finditer(phrase))
        for rang, jeton in enumerate(jetons):
            brut = jeton.group(0)
            # Une majuscule ailleurs qu'au premier mot désigne un nom propre :
            # le kréyòl ne capitalise rien d'autre.
            if rang > 0 and brut[:1].isupper():
                continue
            index.setdefault(plier(brut), []).append((phrase, source, brut))

    print(f"   ✅ {retenues} phrases recevables sur {len(phrases)}")
    print(f"   ✅ {len(index)} formes indexées")
    return index


def score(candidat, forme):
    """Ce qui fait une bonne phrase de carte, du meilleur au moins bon.

    1. La forme telle que le dictionnaire l'écrit, accents compris. Le pli
       rattrape `ka`/`kà`, mais la carte montre l'orthographe du dictionnaire
       et la phrase doit la confirmer, pas la contredire.
    2. Une longueur proche de huit mots.
    3. La phrase elle-même, pour départager : deux exécutions sur le même
       corpus doivent produire le même fichier, sinon le diff est illisible.
    """
    phrase, _, brut = candidat
    return (0 if brut == forme else 1,
            abs(len(phrase.split()) - LONGUEUR_IDEALE),
            phrase)


def construire(vivier, index):
    """Les phrases retenues, mot par mot."""
    print("\n🃏 CHOIX DES PHRASES")
    print("-" * 45)

    exemples = {}
    for forme in vivier:
        candidats = index.get(plier(forme))
        if not candidats:
            continue
        retenues = []
        vues = set()
        for phrase, source, _ in sorted(candidats,
                                        key=lambda c: score(c, forme)):
            if phrase in vues:
                continue
            vues.add(phrase)
            retenues.append({"p": phrase,
                             "crd": crediter(source),
                             "src": ' '.join(source.split())})
            if len(retenues) == PHRASES_PAR_MOT:
                break
        exemples[forme] = retenues

    couverts = len(exemples)
    total = sum(len(v) for v in exemples.values())
    print(f"   ✅ {couverts} mots du vivier sur {len(vivier)} "
          f"({couverts / len(vivier):.0%})")
    print(f"   ✅ {total} phrases retenues")
    return exemples


def valider(exemples, vivier):
    """Refuse d'écrire un actif dont une carte ne pourrait rien faire."""
    print("\n🔬 VALIDATION")
    print("-" * 45)

    connus = set(vivier)
    for forme, phrases in exemples.items():
        if forme not in connus:
            print(f"   ❌ {forme} n'est pas au vivier")
            return False
        if not phrases:
            print(f"   ❌ {forme} a une liste vide")
            return False
        for entree in phrases:
            if not entree.get("p") or not entree.get("src"):
                print(f"   ❌ {forme} : phrase sans texte ou sans source")
                return False
            if not entree.get("crd"):
                print(f"   ❌ {forme} : phrase sans crédit affichable")
                return False
            if plier(forme) not in plier(entree["p"]):
                print(f"   ❌ {forme} absent de sa propre phrase")
                return False

    print("   ✅ toutes les phrases portent leur mot et leur source")
    return True


def sauvegarder(exemples):
    """Écrit l'actif, après copie horodatée de la version précédente."""
    print("\n💾 ÉCRITURE DE L'ACTIF")
    print("-" * 45)

    if CHEMIN_EXEMPLES.exists():
        DOSSIER_BACKUPS.mkdir(exist_ok=True)
        horodatage = datetime.now().strftime("%Y%m%d_%H%M%S")
        copie = DOSSIER_BACKUPS / f"creole_exemples_{horodatage}.json"
        copie.write_bytes(CHEMIN_EXEMPLES.read_bytes())
        print(f"   🗄️  sauvegarde : {copie.name}")

    charge = {
        "version": 1,
        "generated": datetime.now().strftime("%Y-%m-%d"),
        # Recopiée dans l'actif pour que l'attribution voyage avec les
        # phrases, y compris si le fichier est lu hors du dépôt.
        "sources": [
            "POTOMITAN/PawolKreyol-gfc — Apache-2.0 — textes d'auteurs "
            "guadeloupéens, crédités phrase par phrase dans le champ « src »",
        ],
        "count": len(exemples),
        "exemples": exemples,
    }
    with open(CHEMIN_EXEMPLES, "w", encoding="utf-8") as f:
        json.dump(charge, f, ensure_ascii=False, separators=(",", ":"))

    taille = CHEMIN_EXEMPLES.stat().st_size
    print(f"   ✅ {CHEMIN_EXEMPLES.name} — {len(exemples)} mots, "
          f"{taille / 1024:.0f} Ko")


def main():
    strict = "--strict" in sys.argv

    print("🃏 SANBLÉ — GÉNÉRATION DES PHRASES DE CARTE 🃏")
    print("=" * 70)
    print(f"Date: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print("=" * 70)

    try:
        vivier = charger_vivier()
    except Exception as erreur:
        print(f"\n❌ Actifs illisibles : {erreur}")
        print("   Lancez d'abord `python KreyolComplet.py`, puis")
        print("   `python generate_translations.py`.")
        return 1

    phrases = charger_corpus(strict)
    if phrases is None:
        return 1

    exemples = construire(vivier, indexer(phrases))
    if not exemples:
        print("\n❌ Aucune phrase retenue, rien n'est écrit.")
        return 1

    if not valider(exemples, vivier):
        print("\n❌ Validation échouée, rien n'est écrit.")
        return 1

    if strict and len(exemples) < PLANCHER_STRICT:
        print(f"\n❌ Mode strict : {len(exemples)} mots couverts seulement, "
              f"attendu au moins {PLANCHER_STRICT}.")
        return 1

    sauvegarder(exemples)
    print("\n🎉 TERMINÉ")
    return 0


if __name__ == "__main__":
    sys.exit(main())
