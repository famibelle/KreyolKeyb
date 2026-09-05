#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
📖 GLOSES — accès partagé aux deux sources de traduction kréyòl → français
==========================================================================

Ce module ne produit aucun actif. Il porte le téléchargement, le cache et le
nettoyage du wikitexte dont `generate_translations.py` a besoin, pour qu'une
seule copie décide des URL et de la façon de lire les sources.

Deux sources, toutes deux en **CC BY-SA 4.0**, donc combinables :

- **Kreyolopedia** (`kreyolopedia.org`), dictionnaire collaboratif des langues
  créoles. Ses fiches sont écrites à la main, sourcées, avec phonétique et
  exemples : c'est la seule des deux qui a été relue par quelqu'un, elle passe
  donc en premier. Elle est en revanche toute jeune — 148 mots toutes variantes
  confondues au 5 septembre 2026, dont 23 guadeloupéens — et ne peut pas encore
  porter la table à elle seule.

- **Le Wiktionnaire francophone**, qui apporte le volume par deux chemins bien
  distincts :
    1. les 368 pages de la catégorie « créole guadeloupéen », qui portent une
       vraie définition ;
    2. les 884 pages **françaises** dont la section « Traductions » cite une
       forme guadeloupéenne. C'est le seul chemin qui atteint `moun` : le mot
       n'a pas de page à lui, mais la page « personne » le donne en traduction.
       Sans cette seconde passe, la couverture du dictionnaire du clavier tombe
       de 520 mots à 218.

Trois choses à ne pas refaire autrement :

- **L'API de Wikimedia exige un `User-Agent`.** Sans en-tête, elle répond 403,
  et le message ne dit pas pourquoi. Aucune adresse personnelle dedans : leur
  politique demande un moyen de contact, l'adresse du dépôt en est un.
- **Le cache vit hors des actifs**, dans `Dictionnaires/gloses_data/`. Une
  régénération complète demande une vingtaine d'appels et près de 900 pages de
  wikitexte ; les rappeler à chaque essai de mise au point serait impoli
  autant que lent.
- **Ce module ne juge de rien.** Il rend le wikitexte nettoyé et les paires
  telles qu'elles sont écrites ; c'est `generate_translations.py` qui décide
  quelle source l'emporte et ce qui entre dans l'actif.

Fait avec ❤️ pour préserver le Kréyòl
"""

import json
import re
import time
import urllib.parse
import urllib.request
from datetime import datetime
from pathlib import Path

DOSSIER_CACHE = Path(__file__).resolve().parent / "gloses_data"

URL_KREYOLOPEDIA = "https://kreyolopedia.org/api/export/mots.json"
API_WIKTIONNAIRE = "https://fr.wiktionary.org/w/api.php"

# La variante que le clavier écrit. Kreyolopedia range huit créoles dans le même
# export ; le martiniquais et le saint-lucien sont proches, mais leurs graphies
# divergent assez pour qu'une glose empruntée se lise comme une faute.
VARIANTE = "gp"

CATEGORIE_LEMMES = "Catégorie:créole guadeloupéen"
CATEGORIE_TRADUCTIONS = "Catégorie:Traductions en créole guadeloupéen"

# Politique d'accès de Wikimedia : identifier l'outil et donner un moyen de
# contact. L'adresse du dépôt, jamais celle de quelqu'un.
USER_AGENT = ("KlavyeKreyolKarukera/12.0 "
              "(https://github.com/famibelle/KreyolKeyb) generate_translations")

ATTRIBUTION = [
    "Kreyolopedia · https://kreyolopedia.org · licence CC BY-SA 4.0",
    "Wiktionnaire · https://fr.wiktionary.org · licence CC BY-SA 4.0",
]

# Pause entre deux appels à l'API. Les lots font déjà 50 pages ; ce délai n'est
# pas là pour contourner une limite mais pour ne pas en approcher.
PAUSE = 0.1


# ═══════════════════════════════════════════════════════════════════════════
# Accès réseau et cache
# ═══════════════════════════════════════════════════════════════════════════

def _lire_cache(nom):
    """Contenu d'un fichier de cache, ou None s'il n'a jamais été écrit."""
    chemin = DOSSIER_CACHE / nom
    if not chemin.exists():
        return None
    with open(chemin, encoding="utf-8") as f:
        return json.load(f)


def _ecrire_cache(nom, charge):
    DOSSIER_CACHE.mkdir(parents=True, exist_ok=True)
    charge = dict(charge)
    charge["_recupere_le"] = datetime.now().isoformat(timespec="seconds")
    with open(DOSSIER_CACHE / nom, "w", encoding="utf-8") as f:
        json.dump(charge, f, ensure_ascii=False)


def _requete(url, donnees=None):
    requete = urllib.request.Request(
        url, data=donnees, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(requete, timeout=60) as reponse:
        return json.load(reponse)


def _api_wiktionnaire(**parametres):
    """Appel POST à l'API MediaWiki. POST plutôt que GET : un lot de 50 titres
    dépasse la longueur d'URL que le service accepte en GET."""
    parametres.setdefault("action", "query")
    parametres.setdefault("format", "json")
    donnees = urllib.parse.urlencode(parametres).encode()
    return _requete(API_WIKTIONNAIRE, donnees)


def _membres_categorie(titre, cmtype="page"):
    """Tous les titres d'une catégorie, la pagination suivie jusqu'au bout."""
    titres, suite = [], {}
    while True:
        reponse = _api_wiktionnaire(
            list="categorymembers", cmtitle=titre, cmtype=cmtype,
            cmlimit=500, **suite)
        titres += [m["title"] for m in reponse["query"]["categorymembers"]]
        if "continue" not in reponse:
            return titres
        suite = reponse["continue"]
        time.sleep(PAUSE)


def _wikitexte(titres):
    """Le wikitexte de chaque page, par lots de 50 (le maximum de l'API)."""
    pages = {}
    for debut in range(0, len(titres), 50):
        reponse = _api_wiktionnaire(
            prop="revisions", rvprop="content", rvslots="main",
            titles="|".join(titres[debut:debut + 50]))
        for page in reponse["query"]["pages"].values():
            try:
                pages[page["title"]] = page["revisions"][0]["slots"]["main"]["*"]
            except (KeyError, IndexError):
                # Page supprimée entre le listage et la lecture : elle n'entre
                # simplement pas dans la table.
                continue
        time.sleep(PAUSE)
    return pages


# ═══════════════════════════════════════════════════════════════════════════
# Nettoyage du wikitexte
# ═══════════════════════════════════════════════════════════════════════════

MOTIF_LIEN = re.compile(r'\[\[([^\]|]+)(?:\|([^\]]*))?\]\]')
MOTIF_REF = re.compile(r'<ref[^>]*/>|<ref[^>]*>.*?</ref>', re.S)
MOTIF_COMMENTAIRE = re.compile(r'<!--.*?-->', re.S)
MOTIF_BALISE = re.compile(r'<[^>]+>')
MOTIF_MODELE = re.compile(r'\{\{[^{}]*\}\}')


def nettoyer_definition(ligne):
    """Une ligne `# …` du Wiktionnaire, rendue lisible.

    Renvoie `(texte, commence_par_nom_propre)`.

    Le second membre est ce qui décide de la casse. Le Wiktionnaire capitalise
    la première lettre de **toutes** ses définitions, si bien qu'abaisser
    systématiquement écrirait « frans → france » et garder tout écrirait
    « sangliyé → Sanglier ». La cible du premier lien tranche : `[[France]]`
    pointe sur une page capitalisée, `[[sanglier#fr|Sanglier]]` non. C'est le
    même signal que le pipeline emploie déjà pour les noms propres du corpus,
    lu ici sur la source plutôt que sur les occurrences.
    """
    texte = ligne[1:].strip() if ligne.startswith("#") else ligne.strip()
    texte = MOTIF_REF.sub("", texte)
    texte = MOTIF_COMMENTAIRE.sub("", texte)

    # Les modèles sont retirés, étiquettes de lexique comprises : « musicien »
    # dit déjà ce que « (musique) musicien » dirait. Boucle plutôt que motif
    # unique, pour venir à bout des modèles imbriqués.
    precedent = None
    while precedent != texte:
        precedent = texte
        texte = MOTIF_MODELE.sub(" ", texte)

    # La casse se lit avant de résoudre les liens : après substitution, on ne
    # sait plus si « France » venait d'une cible ou d'un libellé.
    tete = MOTIF_LIEN.search(texte.strip())
    nom_propre = bool(tete
                      and texte.strip().startswith("[[")
                      and tete.group(1)[:1].isupper())

    texte = MOTIF_LIEN.sub(lambda m: (m.group(2) or m.group(1)).split("#")[0],
                           texte)
    texte = texte.replace("'''", "").replace("''", "")
    texte = MOTIF_BALISE.sub("", texte)
    texte = re.sub(r'\s+', " ", texte).strip()
    texte = texte.rstrip(" .;:")
    return texte, nom_propre


# Longueur au-delà de laquelle un segment séparé par une virgule n'est plus un
# synonyme mais une explication. Mesuré sur les 367 définitions guadeloupéennes
# du Wiktionnaire : « archipel, groupe d'îles formant une unité géographique »
# et « chaussure, ce que l'on met au pied pour se chausser » se coupent bien
# ici, tandis que « plus que, davantage que » et « il, elle », qui sont de
# vraies listes de synonymes, passent entiers.
LONGUEUR_SEGMENT = 25

# Longueur totale de la glose. Au-delà, la ligne déborde de l'écran d'un
# téléphone à l'endroit même où elle doit tenir sous le mot.
LONGUEUR_GLOSE = 60


# Un segment qui commence par l'une de ces prépositions et n'a que quelques
# mots n'est pas un sens mais une étiquette de contexte : « au figuré »,
# « par extension », « au football ». Le Wiktionnaire et Kreyolopedia les
# écrivent devant le sens, dont elles se retrouvent séparées dès qu'on coupe
# l'explication qui suit. Gardée seule, l'étiquette pend : la fiche de
# `potomitan` finissait par « pilier central de la case ou du temple, au
# figuré ». On préfère perdre une acception secondaire que l'annoncer sans
# la dire.
PREPOSITIONS = ("au ", "aux ", "à ", "en ", "par ", "dans ", "sur ", "chez ",
                "pour ", "de ", "du ", "des ")


def _etiquette_seule(texte):
    return (texte.lower().startswith(PREPOSITIONS)
            and len(texte.split()) <= 3)


def raccourcir(definition):
    """Garde la tête d'une définition et laisse tomber son explication.

    Le premier segment est toujours conservé, fût-il long : mieux vaut une
    phrase qu'un vide. Les suivants n'entrent que s'ils sont assez courts pour
    être des synonymes.
    """
    # Une définition à plusieurs phrases donne tout dans la première ;
    # `an-nou` ajoutait « . - S'utilise uniquement à la première personne du
    # pluriel », soit 108 caractères sur une ligne qui en tient 60.
    definition = definition.split(". ")[0].strip()

    segments = [s.strip() for s in definition.split(",") if s.strip()]
    if not segments:
        return ""
    gardes = [segments[0]]
    for segment in segments[1:]:
        if len(segment) > LONGUEUR_SEGMENT:
            break
        if len(", ".join(gardes + [segment])) > LONGUEUR_GLOSE:
            break
        gardes.append(segment)

    court = ", ".join(gardes)
    if _etiquette_seule(court):
        return ""

    # Dernier garde-fou : une définition d'un seul tenant peut encore dépasser.
    # On coupe sur un blanc, jamais au milieu d'un mot.
    if len(court) > LONGUEUR_GLOSE:
        coupe = court.rfind(" ", 0, LONGUEUR_GLOSE)
        court = (court[:coupe] if coupe > 0 else court[:LONGUEUR_GLOSE]) + "…"
    return court


def abaisser_initiale(texte, nom_propre):
    """Rend sa minuscule à une définition, sauf nom propre et sigle."""
    if not texte or nom_propre:
        return texte
    if len(texte) > 1 and texte[1].isupper():   # « RTL », « ONU »
        return texte
    return texte[0].lower() + texte[1:]


# ═══════════════════════════════════════════════════════════════════════════
# Source 1 — Kreyolopedia
# ═══════════════════════════════════════════════════════════════════════════

FICHIER_KREYOLOPEDIA = "kreyolopedia_gp.json"


def charger_kreyolopedia(hors_ligne=False, verbeux=True, tolerant=False):
    """Les mots guadeloupéens validés de Kreyolopedia.

    Renvoie `(mots, frais)` : la liste des fiches, et si elles viennent du
    réseau ou du cache.

    `tolerant` rend la source facultative : injoignable et jamais mise en
    cache, elle est ignorée au lieu de lever. C'est ce qu'il faut en CI, où le
    disque est neuf à chaque exécution, donc où il n'y a jamais de cache : une
    indisponibilité passagère de Kreyolopedia y faisait échouer le build
    entier, alors que cette source ne fournit que 21 des 1 145 formes livrées.
    Ce n'est pas une question de fraîcheur mais de volume, et le volume est
    déjà gardé deux fois : `construire()` refuse une table vide, et l'étape
    « Verify Generated Assets » du workflow refuse moins de 800 formes. Perdre
    le Wiktionnaire déclenche donc bien un échec, perdre Kreyolopedia non.
    """
    if not hors_ligne:
        try:
            if verbeux:
                print("   🌐 Kreyolopedia — export des mots")
            export = _requete(URL_KREYOLOPEDIA)
            mots = [m for m in export.get("mots", [])
                    if m.get("variante_code") == VARIANTE
                    and m.get("statut") == "valide"]
            _ecrire_cache(FICHIER_KREYOLOPEDIA,
                          {"licence": export.get("licence"), "mots": mots})
            if verbeux:
                print(f"      {len(mots)} mots guadeloupéens sur "
                      f"{export.get('nombre', '?')} au total")
            return mots, True
        except Exception as erreur:
            if verbeux:
                print(f"      ⚠️  injoignable : {erreur}")

    cache = _lire_cache(FICHIER_KREYOLOPEDIA)
    if cache is None:
        if tolerant:
            if verbeux:
                print("      ⏭️  ni réseau ni cache : source ignorée")
            return [], False
        raise RuntimeError("Kreyolopedia injoignable et jamais mise en cache")
    if verbeux:
        print(f"   📁 cache Kreyolopedia du {cache['_recupere_le'][:10]} — "
              f"{len(cache['mots'])} mots")
    return cache["mots"], False


# ═══════════════════════════════════════════════════════════════════════════
# Source 2 — Wiktionnaire
# ═══════════════════════════════════════════════════════════════════════════

FICHIER_WIKTIONNAIRE = "wiktionnaire_gcf.json"

# La section d'une langue va de son en-tête au suivant. Le motif s'arrête sur
# `== {{langue|` et non sur n'importe quel `==` : les sous-titres d'une entrée
# (« Nom commun », « Étymologie ») en ont deux aussi.
MOTIF_SECTION_GCF = re.compile(
    r'==\s*\{\{langue\|gcf\}\}\s*==(.*?)(?=\n==\s*\{\{langue\||\Z)', re.S)

# Une définition est une ligne `#` suivie d'autre chose que `*`, `:` ou `#` :
# ces trois-là introduisent respectivement un exemple, une note et une
# sous-définition.
MOTIF_LIGNE_DEFINITION = re.compile(r'^#\s*[^*:#\s]')

# La forme guadeloupéenne citée par une page française. Le nom du modèle varie
# (`trad`, `trad-`, `trad--`, `trad+`), le reste ne bouge pas.
MOTIF_TRADUCTION = re.compile(r'\{\{trad[^}|]*\|gcf\|([^}|]+)')


def charger_wiktionnaire(hors_ligne=False, verbeux=True, tolerant=False):
    """Les deux passes du Wiktionnaire.

    Renvoie `(donnees, frais)` où `donnees` porte :
      - `definitions` : mot guadeloupéen → liste de définitions nettoyées,
        chacune sous la forme `[texte, nom_propre]` ;
      - `traductions` : mot guadeloupéen → titres des pages françaises qui le
        citent en traduction.
    """
    if not hors_ligne:
        try:
            if verbeux:
                print("   🌐 Wiktionnaire — pages de la catégorie gcf")
            lemmes = _membres_categorie(CATEGORIE_LEMMES)
            pages = _wikitexte(lemmes)
            definitions = {}
            for titre, texte in pages.items():
                section = MOTIF_SECTION_GCF.search(texte)
                if not section:
                    continue
                lues = []
                for ligne in section.group(1).split("\n"):
                    if not MOTIF_LIGNE_DEFINITION.match(ligne):
                        continue
                    propre, nom_propre = nettoyer_definition(ligne)
                    if propre:
                        lues.append([propre, nom_propre])
                if lues:
                    definitions[titre] = lues
            if verbeux:
                print(f"      {len(definitions)} pages définies sur "
                      f"{len(lemmes)} listées")

            if verbeux:
                print("   🌐 Wiktionnaire — sections « Traductions » "
                      "des pages françaises")
            francaises = _membres_categorie(CATEGORIE_TRADUCTIONS)
            traductions = {}
            for titre, texte in _wikitexte(francaises).items():
                if ":" in titre:        # pages d'annexe ou de discussion
                    continue
                for trouve in MOTIF_TRADUCTION.finditer(texte):
                    mot = trouve.group(1).strip()
                    if mot:
                        traductions.setdefault(mot, [])
                        if titre not in traductions[mot]:
                            traductions[mot].append(titre)
            if verbeux:
                print(f"      {len(traductions)} formes citées par "
                      f"{len(francaises)} pages françaises")

            donnees = {"definitions": definitions, "traductions": traductions}
            _ecrire_cache(FICHIER_WIKTIONNAIRE, donnees)
            return donnees, True
        except Exception as erreur:
            if verbeux:
                print(f"      ⚠️  injoignable : {erreur}")

    cache = _lire_cache(FICHIER_WIKTIONNAIRE)
    if cache is None:
        if tolerant:
            # Voir charger_kreyolopedia : ignorer celle-ci ne masque rien, la
            # table tombe alors sous le plancher de 800 formes et le workflow
            # arrête le build à l'étape de vérification.
            if verbeux:
                print("      ⏭️  ni réseau ni cache : source ignorée")
            return {"definitions": {}, "traductions": {}}, False
        raise RuntimeError("Wiktionnaire injoignable et jamais mis en cache")
    if verbeux:
        print(f"   📁 cache Wiktionnaire du {cache['_recupere_le'][:10]} — "
              f"{len(cache['definitions'])} définitions, "
              f"{len(cache['traductions'])} traductions")
    return cache, False
