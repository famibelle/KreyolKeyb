#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
🕳️ FRAZ A TWOU — génération du jeu de phrases à compléter
==========================================================

Produit `android_keyboard/app/src/main/assets/creole_cloze.json`, l'actif du
quatrième jeu de l'application : une phrase kréyòl authentique dont un mot a
été retiré, et quatre propositions dont une seule est celle qu'a écrite
l'auteur.

    cd Dictionnaires
    python generate_cloze.py --strict

Le script est volontairement SÉPARÉ de `KreyolComplet.py` : il ne reconstruit
rien, il **consomme** le dictionnaire et les n-grammes déjà livrés dans les
assets. Il faut donc l'exécuter APRÈS le pipeline, jamais avant — sans quoi les
leurres seraient tirés d'un modèle qui ne correspond plus au corpus.

Ce que le kréyòl change par rapport au portage
----------------------------------------------

Le jeu est repris du Lëtzebuergesch Clavier, qui partage cette base de code.
Là-bas, la casse fait office d'étiquetage grammatical : l'allemand et le
luxembourgeois capitalisent leurs substantifs, donc une majuscule au milieu
d'une phrase désigne un mot porteur de sens. **Le kréyòl ne capitalise rien**,
et ce signal n'existe pas ici. Deux conséquences :

1. **Le mot à masquer est choisi sur la fréquence, faute de grammaire.** Un mot
   trop fréquent est un mot-outil (`an`, `ka`, `sé`, `pou`, `mwen`, `tout`,
   `adan`), et masquer un mot-outil pose une question sans réponse : quatre
   prépositions conviendraient également. Le plafond est un proxy statistique
   assumé, pas un jugement grammatical, et il a un coût : `moun` (267) et
   `pran` (152) sont d'excellentes réponses qu'il écarte avec les autres.

2. **La majuscule, elle, devient un signal de nom propre.** Le kréyòl ne
   capitalise que les noms propres : un mot majuscule ailleurs qu'en tête de
   phrase en est un. C'est plus net que le voisinage de majuscules dont le
   luxembourgeois doit se contenter.

L'accord morphologique entre leurres et réponse a été mesuré et **écarté**.
Exiger la même finale — la règle luxembourgeoise, qui y sépare les verbes en
-t et -en du reste — fait tomber la livraison de 510 à 178 questions sur ce
corpus, pour une langue qui fléchit peu. Le premier leurre venant toujours du
modèle n-grammes, il est plausible à cet emplacement par construction.

Le corpus kréyòl compte 36 500 occurrences là où le luxembourgeois en a 3,1
millions. Tous les seuils de fréquence sont donc lus sur ce corpus-là, jamais
transposés : un mot vu 20 fois y est fréquent, pas rare.

Attribution : les phrases livrées sont des extraits du corpus
POTOMITAN/PawolKreyol-gfc (Apache-2.0), lui-même fait de textes d'auteurs
identifiés — Sylviane Telchid, Sonny Rupaire, Max Rippon et d'autres. Le jeu
affiche la source de chaque phrase, l'écran « À Propos » les crédits complets ;
ne pas retirer l'un ni l'autre.

Fait avec ❤️ pour préserver le Kréyòl
"""

import json
import os
import random
import re
import sys
from bisect import bisect_left
from collections import Counter
from datetime import datetime
from pathlib import Path

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from KreyolComplet import KreyolPipelineUnique

RACINE = Path(__file__).resolve().parent.parent
RACINE_ASSETS = RACINE / "android_keyboard/app/src/main/assets"
CHEMIN_DICT = RACINE_ASSETS / "creole_dict.json"
CHEMIN_NGRAMS = RACINE_ASSETS / "creole_ngrams.json"
CHEMIN_CLOZE = RACINE_ASSETS / "creole_cloze.json"
DOSSIER_BACKUPS = Path(__file__).resolve().parent / "backups"

# Graine fixe : deux exécutions sur le même corpus doivent produire le même
# fichier, sinon chaque régénération réécrit l'actif pour rien et le diff
# devient illisible.
GRAINE = 20260905

MARQUEUR = "___"
NB_PROPOSITIONS = 4

# Le même motif que le pipeline, à ceci près qu'il accepte les mots d'une
# lettre : `a`, `i` et `é` sont parmi les plus fréquents du kréyòl, et une
# phrase qui en contient n'a aucune raison d'être écartée. Ils ne sont pas
# masquables pour autant (voir LONGUEUR_MIN).
PATTERN_MOT = re.compile(
    r'\b[a-zA-ZàáâäèéêëìíîïòóôöùúûüçñÀÁÂÄÈÉÊËÌÍÎÏÒÓÔÖÙÚÛÜÇÑ\-]{1,}\b'
)

# Découpage en phrases : le corpus livre des entrées, pas des phrases. Une
# entrée va du proverbe de quatre mots au chapitre de plusieurs milliers.
SEPARATEUR_PHRASE = re.compile(r'(?<=[.!?…])\s+|\n+')

# Longueur de phrase, en mots. En deçà de 6 le contexte ne suffit pas à
# désigner une réponse ; au-delà de 16 la phrase ne tient plus à l'écran d'un
# téléphone sans que le joueur perde le fil.
MOTS_MIN, MOTS_MAX = 6, 16

# Caractères qui disqualifient une phrase entière : les chiffres, et les
# incises ou citations coupées de leur contexte.
CARACTERES_INTERDITS = set('0123456789()[]{}«»“”„"/\\|<>=+*#@_;:%§€$')

# Bandes de fréquence de la réponse, lues sur les 36 500 occurrences du corpus
# kréyòl. Un mot vu moins de 5 fois n'est pas devinable, même en contexte : la
# borne basse n'est pas un réglage de difficulté mais un refus de poser une
# question sans réponse. Les deux seuils suivants tombent sur les quartiles
# observés des réponses candidates (11 / 23 / 46).
FREQ_MIN = 5
SEUIL_FACILE = 40
SEUIL_NORMAL = 15

DIFFICULTE_FACILE, DIFFICULTE_NORMALE, DIFFICULTE_DIFFICILE = 1, 2, 3

# Longueur minimale d'une réponse. La moyenne du corpus est de 3,9 lettres :
# quatre lettres est déjà au-dessus du mot médian, et descendre à trois ferait
# entrer tous les mots-outils que le plafond de fréquence n'attrape pas.
LONGUEUR_MIN = 4

# Au-delà, un mot est un mot-outil : `mwen`, `tout`, `adan`, `menm`. Voir
# l'en-tête pour ce que ce plafond coûte.
FREQ_MAX_OUTIL = 150

# Un mot est un nom propre s'il porte la majuscule dans au moins la moitié de
# ses occurrences, **et** au moins une fois ailleurs qu'en tête de phrase. Le
# kréyòl ne capitalise rien d'autre, le signal est donc franc.
#
# Les deux conditions sont nécessaires ensemble. Ne compter que les majuscules
# hors tête de phrase laissait passer `viktò` : c'est un personnage de pièce de
# théâtre, capitalisé 95 fois sur 95, mais 83 de ces occurrences ouvrent une
# réplique, et le rapport tombait à 0,13. Ne regarder que la proportion globale
# ferait à l'inverse un nom propre de tout mot qui commence souvent une phrase.
SEUIL_NOM_PROPRE = 0.50
OCCURRENCES_MIN_NOM_PROPRE = 2

# Écart de longueur toléré entre un leurre et la réponse. Trop large, la
# longueur trahit ; trop étroit, il n'y a plus assez de leurres.
ECART_LONGUEUR = 4

# Combien de questions au maximum partagent la même réponse. Sans plafond, une
# poignée de mots fréquents serait la réponse d'une question sur dix.
MAX_PAR_REPONSE = 3

# Taille de la fenêtre de rang dans laquelle on pioche un leurre de secours,
# quand le contexte n-gramme n'en fournit pas assez.
FENETRE_RANG = 200

# Cible de livraison, calée sur ce que le corpus peut effectivement produire —
# environ 500 questions. Plus de quarante parties de 10 questions sans jamais
# revoir la même phrase.
CIBLE_TOTAL = 450
REPARTITION = {
    DIFFICULTE_FACILE: 0.35,
    DIFFICULTE_NORMALE: 0.40,
    DIFFICULTE_DIFFICILE: 0.25,
}


def charger_corpus(strict):
    """Charge le corpus par le même chemin que le pipeline, et le découpe.

    En mode strict, le repli sur l'instantané local est refusé : un jeu
    fabriqué sur des phrases périmées se joue exactement comme un autre, rien
    ne le signalerait. C'est la règle que `KreyolComplet.py --rapport-seul`
    applique déjà pour le rapport linguistique.
    """
    print("\n📖 CHARGEMENT DU CORPUS")
    print("-" * 45)

    pipeline = KreyolPipelineUnique()
    if not pipeline.charger_textes_kreyol():
        return None
    if strict and getattr(pipeline, "source_chargement", None) != "Hugging Face":
        print("\n❌ Mode strict : corpus chargé depuis l'instantané local.")
        print("   Les phrases livrées seraient peut-être périmées, et rien")
        print("   dans le jeu ne le montrerait. Vérifiez HF_TOKEN.")
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


def charger_modele():
    """Lit le dictionnaire et les n-grammes livrés dans les assets."""
    print("\n📚 LECTURE DU MODÈLE LIVRÉ")
    print("-" * 45)

    with open(CHEMIN_DICT, "r", encoding="utf-8") as f:
        brut = json.load(f)
    if not isinstance(brut, list):
        raise ValueError(f"{CHEMIN_DICT.name} doit être un tableau de paires")
    dico = {paire[0]: paire[1] for paire in brut}

    with open(CHEMIN_NGRAMS, "r", encoding="utf-8") as f:
        ngrams = json.load(f)

    print(f"   ✅ {len(dico)} entrées de dictionnaire")
    print(f"   ✅ {len(ngrams)} contextes n-grammes")
    return dico, ngrams


def indexer_par_frequence(dico):
    """Le dictionnaire trié par fréquence décroissante.

    Sert à trouver des leurres « du même rang » que la réponse. Retourne la
    liste des mots et la liste parallèle des fréquences négatives, pour une
    recherche dichotomique croissante.
    """
    entrees = sorted(((freq, mot) for mot, freq in dico.items()),
                     key=lambda e: (-e[0], e[1]))
    return ([mot for _, mot in entrees], [-freq for freq, _ in entrees])


def detecter_noms_propres(phrases):
    """Repère les mots qui portent la majuscule ailleurs qu'en tête de phrase.

    Le kréyòl ne capitalise que les noms propres. Voir [SEUIL_NOM_PROPRE] pour
    la raison d'être des deux conditions.
    """
    print("\n🏷️  DÉTECTION DES NOMS PROPRES")
    print("-" * 45)

    total = Counter()
    majuscules = Counter()
    hors_tete = Counter()
    for texte, _ in phrases:
        for rang, correspondance in enumerate(PATTERN_MOT.finditer(texte)):
            mot = correspondance.group(0)
            total[mot.lower()] += 1
            if mot[:1].isupper():
                majuscules[mot.lower()] += 1
                if rang > 0:
                    hors_tete[mot.lower()] += 1

    noms = {
        mot for mot, compte in total.items()
        if compte >= OCCURRENCES_MIN_NOM_PROPRE
        and hors_tete[mot] >= 1
        and majuscules[mot] / compte >= SEUIL_NOM_PROPRE
    }
    exemples = sorted(noms, key=lambda m: -total[m])[:12]
    print(f"   ✅ {len(noms)} formes écartées, p. ex. {', '.join(exemples)}")
    return noms


def mot_masquable(mot, dico, noms_propres):
    """Ce mot fait-il une réponse honnête ?

    Faute d'étiquetage grammatical et de majuscule à lire, le critère est
    statistique : assez long, assez vu pour être devinable, pas assez fréquent
    pour être un mot-outil. Voir l'en-tête du fichier.
    """
    freq = dico.get(mot)
    if freq is None or freq < FREQ_MIN or freq > FREQ_MAX_OUTIL:
        return False
    if len(mot) < LONGUEUR_MIN:
        return False
    return mot not in noms_propres


def difficulte_de(freq):
    if freq >= SEUIL_FACILE:
        return DIFFICULTE_FACILE
    if freq >= SEUIL_NORMAL:
        return DIFFICULTE_NORMALE
    return DIFFICULTE_DIFFICILE


def _compatible(candidat, reponse, interdits, dico, noms_propres):
    """Un leurre doit être plausible sans être une variante de la réponse."""
    # Un leurre est soumis aux mêmes exigences qu'une réponse : sans cela, le
    # modèle n-grammes propose `an`, `ka` ou `sé` face à un mot de six lettres,
    # et la bonne case se désigne toute seule.
    if not mot_masquable(candidat, dico, noms_propres):
        return False
    if candidat in interdits:
        return False
    if abs(len(candidat) - len(reponse)) > ECART_LONGUEUR:
        return False
    # Deux formes de la même racine proposées ensemble ne font pas un choix,
    # mais un piège d'orthographe.
    court = min(len(candidat), len(reponse), 4)
    if court >= 4 and candidat[:court] == reponse[:court]:
        return False
    return True


def choisir_leurres(reponse, contextes, mots_phrase, dico, ngrams, index, rng,
                    noms_propres):
    """Trois leurres, dont au moins un attesté dans le même contexte.

    Le premier vivier est le modèle n-grammes : ces mots-là suivent réellement
    les mêmes mots dans le corpus, ils sont donc plausibles à l'emplacement du
    trou. S'il n'en fournit aucun, la question est écartée — quatre mots pris
    au hasard dans la bonne bande de fréquence se départagent à l'œil, sans
    lire la phrase.
    """
    interdits = set(mots_phrase) | {reponse}
    leurres = []

    for cle in contextes:
        for candidat in ngrams.get(cle, []):
            mot = candidat.get("word", "")
            if mot in leurres:
                continue
            if _compatible(mot, reponse, interdits, dico, noms_propres):
                leurres.append(mot)
            if len(leurres) == NB_PROPOSITIONS - 1:
                break
        if len(leurres) == NB_PROPOSITIONS - 1:
            break

    if not leurres:
        return None

    # Complément par voisinage de rang : le jeu ne doit pas se gagner en
    # repérant « le seul mot que je connais » ou « le seul mot rare ».
    mots, freqs = index
    rang = bisect_left(freqs, -dico[reponse])
    voisins = mots[max(0, rang - FENETRE_RANG):rang + FENETRE_RANG]
    rng.shuffle(voisins)
    for mot in voisins:
        if len(leurres) == NB_PROPOSITIONS - 1:
            break
        if mot in leurres:
            continue
        if _compatible(mot, reponse, interdits, dico, noms_propres):
            leurres.append(mot)

    if len(leurres) < NB_PROPOSITIONS - 1:
        return None
    return leurres


def construire_questions(phrases, dico, ngrams, index, rng, noms_propres):
    """Parcourt le corpus et fabrique une question par phrase éligible."""
    print("\n✂️  DÉCOUPAGE DES PHRASES À TROUS")
    print("-" * 45)

    questions = []
    rejets = Counter()

    for texte, source in phrases:
        if any(caractere in CARACTERES_INTERDITS for caractere in texte):
            rejets["chiffres ou incises"] += 1
            continue

        tokens = [(m.group(0), m.start(), m.end())
                  for m in PATTERN_MOT.finditer(texte)]
        if not (MOTS_MIN <= len(tokens) <= MOTS_MAX):
            rejets["longueur"] += 1
            continue

        mots = [token[0].lower() for token in tokens]
        if any(mot not in dico for mot in mots):
            rejets["mot hors dictionnaire"] += 1
            continue

        occurrences = Counter(mots)

        # Le premier mot est écarté : sa majuscule est celle de la phrase, et
        # un trou en tête se devine mal. Le dernier l'est aussi — un trou final
        # se devine sur la ponctuation plutôt que sur le sens.
        emplacements = []
        for i in range(1, len(tokens) - 1):
            forme, debut, fin = tokens[i]
            mot = mots[i]
            # Forme capitalisée au milieu d'une phrase : nom propre, ou mot mis
            # en avant. Dans les deux cas ce n'est plus le mot du dictionnaire.
            if forme != mot:
                continue
            if not mot_masquable(mot, dico, noms_propres):
                continue
            # Le mot est ailleurs dans la phrase : le trou est déjà rempli
            # sous les yeux du joueur.
            if occurrences[mot] > 1:
                continue
            emplacements.append((i, mot, debut, fin))

        if not emplacements:
            rejets["aucun mot masquable"] += 1
            continue

        # Une phrase ne donne qu'une question : dix trous dans la même phrase
        # feraient dix fois la même lecture.
        i, mot, debut, fin = rng.choice(emplacements)

        contextes = []
        if i >= 2:
            contextes.append(f"{mots[i - 2]} {mots[i - 1]}")
        contextes.append(mots[i - 1])

        leurres = choisir_leurres(
            mot, contextes, mots, dico, ngrams, index, rng, noms_propres)
        if leurres is None:
            rejets["leurres insuffisants"] += 1
            continue

        questions.append({
            "s": texte[:debut] + MARQUEUR + texte[fin:],
            "a": mot,
            "d": leurres,
            "l": difficulte_de(dico[mot]),
            "src": source,
        })

    print(f"   ✅ {len(questions)} questions candidates")
    for motif, nombre in rejets.most_common():
        print(f"   ↩️  {nombre:>6} phrases écartées — {motif}")
    return questions


def selectionner(questions, rng):
    """Échantillonne la livraison : difficulté répartie, réponses variées.

    Deux passes. La première respecte le quota de chaque bande de difficulté ;
    la seconde complète jusqu'à la cible sans le regarder, faute de quoi la
    livraison plafonnerait sous sa cible — les réponses faciles ne comptent que
    quelques dizaines de formes distinctes et se heurtent au plafond par
    réponse bien avant leur quota.
    """
    print("\n⚖️  SÉLECTION DE LA LIVRAISON")
    print("-" * 45)

    rng.shuffle(questions)

    quotas = {niveau: round(CIBLE_TOTAL * part)
              for niveau, part in REPARTITION.items()}
    par_reponse = Counter()
    comptes = Counter()
    retenues = []

    def tenter(question, avec_quota):
        if len(retenues) >= CIBLE_TOTAL:
            return False
        niveau = question["l"]
        if avec_quota and comptes[niveau] >= quotas[niveau]:
            return False
        if par_reponse[question["a"]] >= MAX_PAR_REPONSE:
            return False
        par_reponse[question["a"]] += 1
        comptes[niveau] += 1
        retenues.append(question)
        return True

    restantes = [q for q in questions if not tenter(q, avec_quota=True)]
    for question in restantes:
        if len(retenues) >= CIBLE_TOTAL:
            break
        tenter(question, avec_quota=False)

    rng.shuffle(retenues)
    # Ordre stable et lisible dans le fichier : par difficulté croissante.
    retenues.sort(key=lambda q: q["l"])

    libelles = {1: "facile", 2: "normal", 3: "difficile"}
    for niveau in sorted(quotas):
        print(f"   📗 {libelles[niveau]:<9} {comptes[niveau]:>5} / {quotas[niveau]}")
    print(f"   🔤 {len({q['a'] for q in retenues})} réponses distinctes")
    print(f"   📚 {len({q['src'] for q in retenues})} sources représentées")
    return retenues


def valider(questions, dico):
    """Contrôles qui doivent tenir avant d'écrire quoi que ce soit."""
    print("\n🔎 VALIDATION")
    print("-" * 45)

    erreurs = []
    for index, question in enumerate(questions):
        # Comparaison par mot entier, pas par sous-chaîne : `an` est contenu
        # dans `adan` sans y être présent comme mot.
        mots_phrase = {m.group(0).lower()
                       for m in PATTERN_MOT.finditer(question["s"])}
        if question["s"].count(MARQUEUR) != 1:
            erreurs.append(f"#{index} : {MARQUEUR} absent ou répété")
        if len(question["d"]) != NB_PROPOSITIONS - 1:
            erreurs.append(f"#{index} : {len(question['d'])} leurres")
        if len(set(question["d"]) | {question["a"]}) != NB_PROPOSITIONS:
            erreurs.append(f"#{index} : propositions en doublon")
        if question["a"] not in dico:
            erreurs.append(f"#{index} : réponse « {question['a']} » hors dictionnaire")
        if question["a"] in mots_phrase:
            erreurs.append(f"#{index} : réponse « {question['a']} » visible dans la phrase")
        for leurre in question["d"]:
            if leurre not in dico:
                erreurs.append(f"#{index} : leurre « {leurre} » hors dictionnaire")
            if leurre in mots_phrase:
                erreurs.append(f"#{index} : leurre « {leurre} » déjà dans la phrase")

    if erreurs:
        for erreur in erreurs[:20]:
            print(f"   ❌ {erreur}")
        print(f"   ❌ {len(erreurs)} erreurs au total")
        return False

    print(f"   ✅ {len(questions)} questions valides")
    return True


def sauvegarder(questions):
    """Écrit l'actif, après copie horodatée de la version précédente."""
    print("\n💾 ÉCRITURE DE L'ACTIF")
    print("-" * 45)

    if CHEMIN_CLOZE.exists():
        DOSSIER_BACKUPS.mkdir(exist_ok=True)
        horodatage = datetime.now().strftime("%Y%m%d_%H%M%S")
        copie = DOSSIER_BACKUPS / f"creole_cloze_{horodatage}.json"
        copie.write_bytes(CHEMIN_CLOZE.read_bytes())
        print(f"   🗄️  sauvegarde : {copie.name}")

    charge = {
        "version": 1,
        "generated": datetime.now().strftime("%Y-%m-%d"),
        # Recopiée dans l'actif pour que l'attribution voyage avec les phrases,
        # y compris si le fichier est lu hors du dépôt.
        "sources": [
            "POTOMITAN/PawolKreyol-gfc — Apache-2.0 — textes d'auteurs "
            "guadeloupéens, crédités phrase par phrase dans le champ « src »",
        ],
        "items": questions,
    }
    with open(CHEMIN_CLOZE, "w", encoding="utf-8") as f:
        json.dump(charge, f, ensure_ascii=False, separators=(",", ":"))

    taille = CHEMIN_CLOZE.stat().st_size
    print(f"   ✅ {CHEMIN_CLOZE.name} — {len(questions)} questions, "
          f"{taille / 1024:.0f} Ko")


def main():
    strict = "--strict" in sys.argv

    print("🕳️ FRAZ A TWOU — GÉNÉRATION DU JEU DE PHRASES À COMPLÉTER 🕳️")
    print("=" * 70)
    print(f"Date: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print("=" * 70)

    rng = random.Random(GRAINE)

    try:
        dico, ngrams = charger_modele()
    except Exception as erreur:
        print(f"\n❌ Modèle illisible : {erreur}")
        print("   Lancez d'abord `python KreyolComplet.py`.")
        return 1

    phrases = charger_corpus(strict)
    if phrases is None:
        return 1

    noms_propres = detecter_noms_propres(phrases)
    index = indexer_par_frequence(dico)
    questions = construire_questions(
        phrases, dico, ngrams, index, rng, noms_propres)
    if not questions:
        print("\n❌ Aucune question produite, rien n'est écrit.")
        return 1

    livraison = selectionner(questions, rng)
    if not valider(livraison, dico):
        print("\n❌ Validation échouée, rien n'est écrit.")
        return 1

    if strict and len(livraison) < CIBLE_TOTAL * 0.6:
        print(f"\n❌ Mode strict : {len(livraison)} questions seulement, "
              f"attendu au moins {int(CIBLE_TOTAL * 0.6)}.")
        return 1

    sauvegarder(livraison)
    print("\n🎉 TERMINÉ")
    return 0


if __name__ == "__main__":
    sys.exit(main())
