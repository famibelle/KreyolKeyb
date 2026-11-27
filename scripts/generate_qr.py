#!/usr/bin/env python3
"""Regenere les QR codes des supports imprimes.

Sans ce script les QR sont des binaires opaques : personne ne peut lire vers
quoi ils pointent sans les decoder, et une campagne qui change oblige a les
refaire a la main sans trace de ce qui a ete encode. Les cibles sont donc
declarees ici, en clair, et le fichier devient la source.

Chaque support porte son utm_source, et chaque emplacement d'un meme support
son utm_content : sans cela, quatre codes sur un depliant remontent dans la
console Play comme une seule ligne, et on ne sait jamais lequel convertit.

    python3 scripts/generate_qr.py           # regenere tout
    python3 scripts/generate_qr.py --check   # verifie sans rien ecrire

Le referrer de Google Play est un parametre unique dont la valeur est
elle-meme une chaine de parametres : elle doit donc etre encodee une fois de
plus que le reste de l'URL, d'ou le quote() imbrique.
"""
import argparse
import subprocess
import sys
from pathlib import Path
from urllib.parse import quote

try:
    import qrcode
except ImportError:
    sys.exit("Il manque la bibliotheque qrcode : pip install qrcode")

RACINE = Path(__file__).resolve().parent.parent
ASSETS = RACINE / "docs" / "assets"
APP = "com.potomitan.kreyolkeyboard"
CAMPAGNE = "launch10k"

# fichier -> parametres de la cible. Chaque entree decrit exactement ce que le
# fichier existant encode : relancer le script ne doit rien changer a un QR
# deja imprime et distribue.
#   source    : utm_source, le support
#   content   : utm_content, l'emplacement dans ce support
#   campagne  : False pour une cible qui doit rester hors campagne ; toutes
#               en portent une aujourd'hui
#   cote      : cote de l'image en pixels
#
# Un fichier par support : un seul code partage entre plusieurs supports les
# fait remonter sous une source unique dans la console Play, et on ne sait
# plus lequel travaille. qr-affiche.png s'appelait qr-google-play.png et
# servait aussi les tracts et le kit ambassadeur, tous comptes sous "affiche".
CIBLES = {
    "qr-affiche.png": dict(source="affiche", cote=2280),
    "qr-tract.png": dict(source="tract"),
    # Le FlashCode que l'ambassadeur telecharge pour ses propres supports :
    # story, sticker, signature d'email. La source est donc la personne qui
    # le diffuse, pas la page ou il a ete pris. Haute resolution, il sera
    # reemploye a des tailles qu'on ne maitrise pas.
    "qr-ambassadeur.png": dict(source="ambassadeur", cote=2280),
    "qr-triptyque-couverture.png": dict(source="triptyque", content="couverture"),
    "qr-triptyque-dos.png": dict(source="triptyque", content="dos"),
    "qr-triptyque-installation.png": dict(source="triptyque", content="installation"),
    "qr-triptyque-progression.png": dict(source="triptyque", content="progression"),
    # Le code du bloc « Installer en trois étapes » de l'accueil, pour qui lit
    # la page sur un ordinateur. Même source que les boutons du site
    # (landing), son propre emplacement.
    "qr-accueil.png": dict(source="landing", content="qr-code"),
}

# Mesures reprises de qr-triptyque.png, le fichier d'origine : 1 module de
# marge et environ 640 px de cote. La marge est plus fine que les 4 modules de
# la norme, ce que compense la carte blanche qui entoure le code dans la mise
# en page.
BORDURE = 1
COTE_PAR_DEFAUT = 640


def url(source, content=None, campagne=True):
    referrer = f"utm_source={source}"
    if campagne:
        referrer += f"&utm_campaign={CAMPAGNE}"
    if content:
        referrer += f"&utm_content={content}"
    return (f"https://play.google.com/store/apps/details"
            f"?id={APP}&referrer={quote(referrer, safe='')}")


def image(cible, cote=COTE_PAR_DEFAUT):
    qr = qrcode.QRCode(error_correction=qrcode.constants.ERROR_CORRECT_M,
                       border=BORDURE)
    qr.add_data(cible)
    qr.make(fit=True)
    modules = qr.modules_count + 2 * BORDURE
    # box_size entier : un module a une largeur constante, sinon le
    # reechantillonnage en produit d'inegaux et fragilise la lecture.
    qr.box_size = max(1, round(cote / modules))
    return qr.make_image(fill_color="black", back_color="white").convert("RGB")


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--check", action="store_true",
                   help="signale les fichiers manquants sans rien ecrire")
    args = p.parse_args()

    manquants = []
    for nom, params in CIBLES.items():
        chemin = ASSETS / nom
        cote = params.get("cote", COTE_PAR_DEFAUT)
        cible = url(params["source"], params.get("content"),
                    params.get("campagne", True))
        if args.check:
            if not chemin.exists():
                manquants.append(nom)
            print(f"{'   ' if chemin.exists() else '!! '}{nom} -> {cible}")
            continue
        im = image(cible, cote)
        im.save(chemin)
        print(f"{nom} ({im.size[0]} px) -> {cible}")

    if args.check:
        print(f"\n{len(CIBLES) - len(manquants)}/{len(CIBLES)} QR presents.")
        return 1 if manquants else 0

    # Les QR neufs sortent nus : la marque se reinscrit dans leurs metadonnees.
    tag = RACINE / "scripts" / "tag_assets.py"
    if tag.exists():
        subprocess.run([sys.executable, str(tag)], check=False,
                       stdout=subprocess.DEVNULL)
        print("\nMetadonnees de marque reappliquees.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
