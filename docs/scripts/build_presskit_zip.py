#!/usr/bin/env python3
"""Assemble le kit presse téléchargeable en un clic.

Un journaliste ne parcourt pas une arborescence GitHub : il télécharge une
archive, l'ouvre, et prend ce dont il a besoin. Ce script produit
docs/presse/kit-presse.zip à partir des visuels déjà présents dans le dépôt,
sans les dupliquer dans les sources.

L'animation GIF et les extraits vidéo sont volontairement exclus : ils pèsent
à eux seuls plus de cinquante mégaoctets, ce qui transformerait l'archive en
obstacle. Ils restent accessibles par lien direct depuis le dossier de presse.

    python3 docs/scripts/build_presskit_zip.py
"""

import zipfile
from pathlib import Path

DOCS = Path(__file__).resolve().parents[1]
SORTIE = DOCS / "presse" / "kit-presse.zip"

# Chemin dans le dépôt -> nom dans l'archive. Le renommage est délibéré :
# les noms de l'archive doivent se comprendre sans consulter le dossier.
VISUELS = {
    "Screenshots/app_clavier_suggestions.png": "captures/01-suggestions-en-conditions-reelles.png",
    "Screenshots/app_accueil.png":             "captures/02-accueil-configuration.png",
    "Screenshots/app_stats.png":               "captures/03-progression-gamifiee.png",
    "Screenshots/app_guide.png":               "captures/04-guide-et-accents.png",
    "Screenshots/app_jeu_mots_meles.png":      "captures/05-jeu-mots-meles.png",
    "Screenshots/app_jeu_mots_melanges.png":   "captures/06-jeu-mots-melanges.png",
    "Screenshots/app_onglet_apropos.png":      "captures/07-a-propos-mission.png",
    "Screenshots/app_selecteur_clavier.png":   "captures/08-selecteur-de-clavier-systeme.png",
    "Screenshots/app_jeux_menu.png":           "captures/09-jeux-et-carnet.png",
    "Screenshots/app_carnet_carte.png":        "captures/10-carte-du-carnet.png",
    "Screenshots/app_carnet_sanble.png":       "captures/11-carnet-sanble.png",
    "Screenshots/app_carnet_eventail.png":     "captures/12-cartes-a-revoir.png",
    "Screenshots/app_carnet_sonje.png":        "captures/13-revision-sonje.png",
    "Screenshots/app_jeu_mo_an_plas.png":      "captures/14-jeu-mo-an-plas.png",
    "Screenshots/app_jeu_mokware.png":         "captures/15-jeu-mokware.png",
    "assets/potomitan-logo.png":               "logo/potomitan-logo.png",
}

LISEZMOI = """KIT PRESSE - Klavye Kreyol Karukera
Clavier Android pour le creole guadeloupeen.

CONTENU
  captures/   Captures d'ecran de l'application
  logo/       Logo Potomitan(TM)

CONDITIONS D'UTILISATION
  Ces visuels sont mis a disposition des redactions pour illustrer un sujet
  consacre au projet. Merci de crediter Potomitan(TM). Ils ne doivent pas etre
  recadres ou retouches au point d'en alterer le sens.

NON INCLUS DANS L'ARCHIVE, POUR NE PAS L'ALOURDIR
  Animation du clavier en action, extraits des reportages Canal 10 et
  Guadeloupe la 1ere : liens directs dans le dossier de presse en ligne.

DOSSIER DE PRESSE COMPLET, FAITS CLES ET CONTACT
  https://famibelle.github.io/KreyolKeyb/presskit.html

CONTACT
  Medhi Famibelle, Potomitan(TM) - contact@potomitan.io
"""


def main() -> int:
    SORTIE.parent.mkdir(parents=True, exist_ok=True)
    manquants = [s for s in VISUELS if not (DOCS / s).exists()]
    if manquants:
        for s in manquants:
            print(f"introuvable : {s}")
        return 1

    # ZIP_DEFLATED sur des PNG deja compresses ne gagne presque rien, mais
    # coute encore moins : l'archive reste lisible par tous les outils.
    with zipfile.ZipFile(SORTIE, "w", zipfile.ZIP_DEFLATED) as z:
        for source, destination in VISUELS.items():
            z.write(DOCS / source, destination)
        z.writestr("LISEZ-MOI.txt", LISEZMOI)

    poids = SORTIE.stat().st_size / (1024 * 1024)
    print(f"{SORTIE.relative_to(DOCS.parent)} : {len(VISUELS) + 1} entrées, {poids:.1f} Mo")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
