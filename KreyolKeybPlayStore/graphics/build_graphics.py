#!/usr/bin/env python3
"""Fabrique les éléments graphiques de la fiche Play Store.

    python3 build_graphics.py            # tout
    python3 build_graphics.py icon       # icône 512 seule
    python3 build_graphics.py feature    # image de présentation seule
    python3 build_graphics.py shots      # les huit captures téléphone
    python3 build_graphics.py hors       # visuels hors Console (jeux, Sonjé, clavier)
    python3 build_graphics.py tablette   # les six captures tablette
    python3 build_graphics.py hors-tablette  # visuels hors Console en paysage
    python3 build_graphics.py check      # vérifie les contraintes Play Console

Porté de LuxKeyb (`LuxKeybPlayStore/graphics/build_graphics.py`, octobre
2026), sur la même organisation : chaque fichier porte le nom de
l'emplacement du formulaire de la Console où il va, pour qu'il n'y ait rien à
retrouver au moment de l'envoi.

  feature-graphic/
    icone-et-presentation/   Icône de l'application.png, Image de présentation.png
                             (et sa source HTML, feature_graphic_source.html)
    captures/fr-FR/          les huit captures de la Console, légendées
    hors-console/fr-FR/      un visuel par jeu, Sonjé et le mot suivant, pour le
                             site et les réseaux, jamais envoyés à la Console
    captures-tablette/fr-FR/ six captures 1920x1080 en paysage, à envoyer deux
                             fois : « tablette 7 pouces » et « 10 pouces »
    hors-console-tablette/fr-FR/  les visuels hors Console en 1920x1080

Une seule langue, contrairement à LuxKeyb : la fiche est en français. Le
dossier `fr-FR/` garde la place d'une autre langue si la fiche en gagne une.

Ce qui change par rapport à LuxKeyb, et pourquoi (`docs/charte-graphique.html`) :

- la **palette de communication** (`docs/assets/theme.css` : papier, encre,
  mer, hibiscus, soleil) et non celle du clavier, que les captures montrent
  déjà : la charte interdit de mêler les deux dans un même support ;
- pas de drapeau en tête : la bande est le dégradé mer → hibiscus du site
  (le drapeau à fleur de lys est exclu, voir la charte) ;
- **Fredoka** pour les accroches, embarquée depuis `docs/assets/fredoka-embed.css`,
  la pile système pour le reste : aucune troisième police ;
- le pied porte les quatre promesses dans l'ordre de la charte, et le
  clavier n'est jamais présenté comme une méthode d'apprentissage.

Le kréyòl visible vient de l'application elle-même (captures) ou de textes
déjà validés dans le dépôt : « Maké on bèl Kréyòl asi téléfòn a-w. » est la
signature produit de la charte. Aucune légende n'en rédige.

Les captures sources sont réelles et non retouchées (la charte : « c'est la
preuve, elle doit rester vraie »). Téléphone : émulateur `pixel9` (1080x2424),
système en français, barre d'état en mode démo (10:00, batterie pleine).
Tablette : émulateur `tablette10` (2560x1600, paysage). Toutes prises le
19 octobre 2026 sous la 23.1.1 et ses correctifs du jour (cimetière de
Morne-à-l'Eau, étoiles qui scintillent), avec un carnet de démonstration de
55 cartes poussé par `run-as` dans `shared_prefs/kreyol_carnet_prefs.xml`
d'une version de débogage : trente lieux, quatre raretés, carême, hivernage
et trois fêtes, et 480 mots tapés pour le niveau Débrouya. Les captures de
clavier (`-recadre`, 1080x1300) sont prises dans Google Messages puis
recadrées sur la conversation, la barre de suggestions et le clavier ;
l'original plein écran reste à côté. À refaire à chaque changement visible.

Dépendances : google-chrome (rendu HTML) et ImageMagick (`convert`).

Deux pièges de Chrome headless, connus de LuxKeyb :

- le viewport rendu fait 87 px de moins que le `--window-size` demandé, et le
  bas de la page reste vide : on rend plus haut, puis on recadre (`render()`) ;
- Chrome n'ouvre pas un fichier HTML dont le chemin porte une apostrophe, sans
  rien dire : les HTML intermédiaires sont nommés par leur numéro.

La Play Console refuse la transparence sur l'icône et l'image de présentation :
tout est aplati sur blanc en sortie. Chrome écrit des PNG sans métadonnées :
`scripts/tag_assets.py` y inscrit Potomitan™ après chaque fabrication.
"""

import base64
import pathlib
import shutil
import struct
import subprocess
import sys
import tempfile

HERE = pathlib.Path(__file__).resolve().parent
REPO = HERE.parents[1]
SHOTS = HERE / "captures-emulateur-pixel9"
SHOTS_TABLETTE = HERE / "captures-emulateur-tablette"
OUT = HERE / "feature-graphic"
COMMUNS = OUT / "icone-et-presentation"
LANGUE = "fr-FR"
CAPTURES_DIR = OUT / "captures" / LANGUE
HORS_CONSOLE = OUT / "hors-console" / LANGUE
TABLETTE_DIR = OUT / "captures-tablette" / LANGUE
HORS_CONSOLE_TABLETTE_DIR = OUT / "hors-console-tablette" / LANGUE
# L'icône de la fiche est celle du lanceur : le logo Potomitan™.
ICON_SOURCE = HERE / "app-icon" / "POTOMITAN-logo-512.png"
ICON = COMMUNS / "Icône de l'application.png"
FREDOKA = REPO / "docs" / "assets" / "fredoka-embed.css"

# marge de rendu qui absorbe la hauteur de fenêtre non peinte par Chrome
CHROME_GUTTER = 200

# La palette de communication, docs/assets/theme.css (thème clair).
PAPIER, ENCRE, ENCRE_DOUCE = "#F5F2EA", "#1C2624", "#4B5750"
MER, HIBISCUS, SOLEIL = "#0E6E76", "#C94A3B", "#C97F1E"

PIED = "Klavyé Kréyòl · gratuit, open source, zéro pub, hors ligne"

# Les huit captures de la Console, dans l'ordre d'envoi :
# (nom, source, kicker, titre, sous-titre). Le nom porte le contenu entre
# parenthèses : la Console lit le numéro, quiconque parcourt le dossier lit
# le contenu.
CAPTURES = [
    ("Captures d'écran pour téléphone 1 (Suggestions)", "08-clavier-suggestions-messages-recadre.png",
     "Suggestions", "Il vous souffle les mots kréyòl",
     "Tirées de textes d'auteurs guadeloupéens, calculées sur le téléphone, sans connexion."),
    ("Captures d'écran pour téléphone 2 (Carte)", "07-carnet-carte-kreyol-recadre.png",
     "Sanblé", "Chaque mot gagné devient une carte",
     "Son sens, la phrase d'un auteur, et un lieu de Guadeloupe : ici la place de la Victoire et Vélo."),
    ("Captures d'écran pour téléphone 3 (Album)", "21-carnet-album-rarete.png",
     "Sanblé", "Trente lieux de Guadeloupe à collectionner",
     "Chaque carte s'ouvre sur un paysage, à l'heure de sa rareté, avec la saison du jour où vous l'avez gagnée."),
    ("Captures d'écran pour téléphone 4 (Jeux)", "02-jeux-onglet-je.png",
     "Jé", "Six jeux avec les mots du clavier",
     "Mots mêlés, Mo an Karénaj, Mokwaré… Tous versent leurs mots dans le même carnet."),
    ("Captures d'écran pour téléphone 5 (Accents)", "09-clavier-accents-appui-long-recadre.png",
     "Accents", "é et è ont leur propre touche",
     "Un appui long donne ò et les autres accents, sans quitter le clavier."),
    ("Captures d'écran pour téléphone 6 (Dictionnaire)", "05-dictionnaire-recherche-lanme.png",
     "Dictionnaire", "Le sens des mots, dans les deux langues",
     "1 145 mots kréyòl traduits en français, et la recherche marche dans les deux sens."),
    ("Captures d'écran pour téléphone 7 (Progression)", "03-progression-onglet-kreyol-an-mwen.png",
     "Kréyòl an mwen", "Chaque mot écrit vous fait monter",
     "De Pipirit à Potomitan, selon la part du dictionnaire que vous employez déjà."),
    ("Captures d'écran pour téléphone 8 (Accueil)", "01-accueil-onglet-dekolaj.png",
     "Dékolaj", "Votre kréyòl du jour",
     "Le mot du jour, les cartes à revoir et votre dernier jeu, dès l'ouverture."),
]

# Hors des huit emplacements de la Console : un visuel par jeu, Sonjé et le
# mot suivant, même gabarit, pour le site, les réseaux et la fiche complète.
HORS_CONSOLE_FICHIERS = [
    ("Jeu 1 (Mots Mêlés)", "11-jeu-mots-meles.png",
     "Jeu", "Retrouvez les mots cachés",
     "Une grille de lettres, des mots kréyòl à faire glisser du doigt, avec leur sens en français."),
    ("Jeu 2 (Mots Mélangés)", "12-jeu-mots-melanges.png",
     "Jeu", "Remettez les lettres dans l'ordre",
     "Un mot kréyòl en désordre, des lettres à replacer, et un indice si besoin."),
    ("Jeu 3 (Mo an Karénaj)", "13-jeu-mo-an-karenaj-trois-essais.png",
     "Jeu", "Devinez le mot en six essais",
     "Cinq lettres, trois couleurs, et un mot qui doit exister dans le dictionnaire kréyòl."),
    ("Jeu 4 (Fraz a twou)", "14-jeu-fraz-a-twou.png",
     "Jeu", "Complétez la vraie phrase",
     "La phrase d'un auteur, un mot manquant, quatre propositions dont une seule est la sienne."),
    ("Jeu 5 (Mokwaré)", "15-jeu-mokware.png",
     "Jeu", "Des mots croisés à écrire soi-même",
     "Le sens en français pour définition, et un clavier de jeu calqué sur celui du téléphone."),
    ("Jeu 6 (Mo an plas)", "16-jeu-mo-an-plas.png",
     "Jeu", "Placez les mots dans la grille",
     "Aucune définition : les longueurs et les croisements suffisent, le sens apparaît une fois le mot placé."),
    ("Sonjé 1 (Boîte de Leitner)", "18-sonje-boite-de-leitner.png",
     "Sonjé", "Les cartes reviennent quand il faut",
     "Une carte retrouvée change de casier et revient de plus en plus tard, d'un jour à trois mois."),
    ("Sonjé 2 (Éventail de cartes)", "19-sonje-eventail-de-cartes.png",
     "Sonjé", "Les cartes d'un casier en éventail",
     "Glissez pour parcourir, touchez une carte pour la lire en grand."),
    ("Clavier (Mot suivant)", "10-clavier-mot-suivant-messages-recadre.png",
     "Mot suivant", "Il devine aussi le mot d'après",
     "Après « Mwen ka », il propose fè, konprann, jété : des suites relevées dans les textes kréyòl."),
]

# Les six captures tablette, dans l'ordre d'envoi : (nom, source, légende),
# la légende étant celle du téléphone ou des visuels hors Console qui montrent
# la même chose, (catalogue, rang), plus deux propres à la tablette.
LEGENDES_TABLETTE = [
    ("Sanblé", "Un album de cartes à collectionner",
     "Chaque mot gagné dans un jeu y reçoit sa carte, rangée page après page."),
    ("Sanblé", "Chaque carte ouvre un paysage vivant",
     "La Traversée, son agouti et son colibri : chaque lieu a sa faune, qui bouge quand la carte est ouverte."),
    ("Dictionnaire", "La liste et la fiche côte à côte",
     "Tapez un mot kréyòl ou français, touchez-le : sa fiche s'ouvre à droite, avec un exemple."),
]
CAPTURES_TABLETTE = [
    ("Captures d'écran pour tablette 1 (Suggestions)", "01-clavier-suggestions-dictionnaire.png", ("tel", 0)),
    ("Captures d'écran pour tablette 2 (Album)", "22-carnet-album.png", ("tab", 0)),
    ("Captures d'écran pour tablette 3 (Carte)", "24-carnet-carte-peyi.png", ("tab", 1)),
    ("Captures d'écran pour tablette 4 (Dictionnaire)", "02-dictionnaire-liste-et-fiche.png", ("tab", 2)),
    ("Captures d'écran pour tablette 5 (Mokwaré)", "16-jeu-mokware.png", ("hors", 4)),
    ("Captures d'écran pour tablette 6 (Mo an plas)", "17-jeu-mo-an-plas.png", ("hors", 5)),
]
HORS_CONSOLE_TABLETTE = [
    ("Jeu 1 (Mots Mêlés)", "11-jeu-mots-meles.png", ("hors", 0)),
    ("Sonjé 1 (Boîte de Leitner)", "18-sonje-boite-de-leitner.png", ("hors", 6)),
    ("Sonjé 2 (Éventail de cartes)", "19-sonje-eventail-de-cartes.png", ("hors", 7)),
    # la carte très rare, la nuit : ses étoiles scintillent sur l'appareil
    ("Carnet (Carte de nuit)", "23-carnet-carte-balkon.png", ("tel", 2)),
]


def legende(ref) -> tuple[str, str, str]:
    catalogue, rang = ref
    if catalogue == "tel":
        return CAPTURES[rang][2:]
    if catalogue == "tab":
        return LEGENDES_TABLETTE[rang]
    return HORS_CONSOLE_FICHIERS[rang][2:]


STYLE_COMMUN = """
  *{{ box-sizing:border-box; margin:0; padding:0; }}
  body{{
    background:{papier}; color:{encre};
    font-family:-apple-system,"Segoe UI","Helvetica Neue","Liberation Sans","DejaVu Sans",Arial,sans-serif;
  }}
  .bande{{ background:linear-gradient(90deg,{mer},{hibiscus}); flex:0 0 auto; }}
  .kicker{{
    font-family:ui-monospace,"SF Mono","DejaVu Sans Mono",Consolas,monospace;
    font-weight:700; letter-spacing:.12em; text-transform:uppercase; color:{mer};
  }}
  h1{{ font-family:"Fredoka",sans-serif; font-weight:700; line-height:1.08; letter-spacing:-.01em; }}
  .sub{{ color:{encre_douce}; line-height:1.42; }}
  footer{{ display:flex; align-items:center; font-weight:700; color:#7A857F; }}
  footer img{{ border-radius:50%; background:#fff; }}
  .ecran{{ border:12px solid {encre}; box-shadow:0 26px 60px rgba(28,38,36,.28); }}
"""

SHOT_TEMPLATE = """<meta charset="utf-8">
<style>{fredoka}
""" + STYLE_COMMUN + """
  html,body{{ width:1080px; height:1920px; overflow:hidden; }}
  body{{ display:flex; flex-direction:column; }}
  .bande{{ height:14px; }}
  header{{ padding:72px 84px 44px; flex:0 0 auto; }}
  .kicker{{ font-size:28px; margin-bottom:20px; }}
  h1{{ font-size:72px; }}
  .sub{{ font-size:34px; margin-top:24px; max-width:900px; }}
  .stage{{ flex:1 1 auto; display:flex; align-items:center; justify-content:center;
           padding:0 84px 20px; min-height:0; }}
  .stage img{{ max-width:100%; max-height:100%; width:auto; height:auto; border-radius:34px; }}
  footer{{ flex:0 0 auto; justify-content:center; gap:18px; padding:30px 0 44px; font-size:28px; }}
  footer img{{ width:52px; height:52px; }}
</style>
<div class="bande"></div>
<header>
  <div class="kicker">{kicker}</div>
  <h1>{title}</h1>
  <div class="sub">{sub}</div>
</header>
<div class="stage"><img class="ecran" src="data:image/png;base64,{shot}"></div>
<footer><img src="data:image/png;base64,{icon}">{pied}</footer>
"""

# Paysage : la légende à gauche, l'écran à droite, même papier et même pied.
TABLETTE_TEMPLATE = """<meta charset="utf-8">
<style>{fredoka}
""" + STYLE_COMMUN + """
  html,body{{ width:1920px; height:1080px; overflow:hidden; }}
  body{{ display:flex; flex-direction:column; }}
  .bande{{ height:12px; }}
  main{{ flex:1 1 auto; display:flex; align-items:center; gap:56px; padding:0 64px 0 80px; min-height:0; }}
  .texte{{ flex:0 0 500px; display:flex; flex-direction:column; justify-content:center; height:100%; }}
  .kicker{{ font-size:24px; margin-bottom:22px; }}
  h1{{ font-size:60px; }}
  .sub{{ font-size:30px; margin-top:28px; }}
  footer{{ gap:16px; margin-top:56px; font-size:22px; }}
  footer img{{ width:46px; height:46px; }}
  .stage{{ flex:1 1 auto; display:flex; align-items:center; justify-content:center; min-width:0; }}
  .stage img{{ max-width:100%; max-height:930px; width:auto; height:auto; border-radius:30px; }}
</style>
<div class="bande"></div>
<main>
  <div class="texte">
    <div class="kicker">{kicker}</div>
    <h1>{title}</h1>
    <div class="sub">{sub}</div>
    <footer><img src="data:image/png;base64,{icon}">{pied}</footer>
  </div>
  <div class="stage"><img class="ecran" src="data:image/png;base64,{shot}"></div>
</main>
"""


def b64(path: pathlib.Path) -> str:
    return base64.b64encode(path.read_bytes()).decode()


def magick(*args: str) -> None:
    subprocess.run(["convert", *args], check=True)


def fredoka() -> str:
    return FREDOKA.read_text(encoding="utf-8")


def render(html: pathlib.Path, out: pathlib.Path, width: int, height: int) -> None:
    """Rend `html` en PNG opaque de width x height, sans le bas tronqué de Chrome."""
    subprocess.run([
        "google-chrome", "--headless", "--disable-gpu", "--no-sandbox",
        "--hide-scrollbars", "--force-device-scale-factor=1",
        f"--window-size={width},{height + CHROME_GUTTER}",
        f"--screenshot={out}", str(html),
    ], check=True, capture_output=True)
    magick(str(out), "-crop", f"{width}x{height}+0+0", "+repage",
           "-background", "white", "-alpha", "remove", "-alpha", "off", f"PNG24:{out}")


def build_icon() -> None:
    ICON.parent.mkdir(parents=True, exist_ok=True)
    magick(str(ICON_SOURCE), "-background", "white", "-alpha", "remove", "-alpha", "off",
           "-resize", "512x512", "-depth", "8", f"PNG24:{ICON}")
    print(f"{ICON.relative_to(HERE)}  ok")


def build_feature() -> None:
    src = COMMUNS / "feature_graphic_source.html"
    out = COMMUNS / "Image de présentation.png"
    render(src, out, 1024, 500)
    print(f"{out.relative_to(HERE)}  ok")


def _rendre(specs, template, taille, largeur_capture, icone_px, out_dir, sources) -> None:
    out_dir.mkdir(parents=True, exist_ok=True)
    police = fredoka()
    with tempfile.TemporaryDirectory() as tmpdir:
        tmp = pathlib.Path(tmpdir)
        small_icon = tmp / "icon.png"
        magick(str(ICON), "-resize", f"{icone_px}x{icone_px}", str(small_icon))
        icon = b64(small_icon)
        for index, (name, src, (kicker, title, sub)) in enumerate(specs, 1):
            source = sources / src
            if not source.exists():
                sys.exit(f"source manquante : {source}")
            shot = tmp / f"{index:02d}.png"
            magick(str(source), "-resize", f"{largeur_capture}x", str(shot))
            # nom neutre : Chrome n'ouvre pas un chemin qui porte une apostrophe
            html = tmp / f"{index:02d}.html"
            html.write_text(template.format(
                fredoka=police, papier=PAPIER, encre=ENCRE, encre_douce=ENCRE_DOUCE,
                mer=MER, hibiscus=HIBISCUS, kicker=kicker, title=title, sub=sub,
                pied=PIED, shot=b64(shot), icon=icon), encoding="utf-8")
            out = out_dir / f"{name}.png"
            render(html, out, *taille)
            print(f"{out.relative_to(HERE)}  ok")


def build_shots() -> None:
    specs = [(n, s, (k, t, u)) for n, s, k, t, u in CAPTURES]
    _rendre(specs, SHOT_TEMPLATE, (1080, 1920), 1600, 104, CAPTURES_DIR, SHOTS)


def build_hors_console() -> None:
    specs = [(n, s, (k, t, u)) for n, s, k, t, u in HORS_CONSOLE_FICHIERS]
    _rendre(specs, SHOT_TEMPLATE, (1080, 1920), 1600, 104, HORS_CONSOLE, SHOTS)


def build_tablette(specs=CAPTURES_TABLETTE, dossier=TABLETTE_DIR) -> None:
    specs = [(n, s, legende(ref)) for n, s, ref in specs]
    _rendre(specs, TABLETTE_TEMPLATE, (1920, 1080), 1500, 92, dossier, SHOTS_TABLETTE)


def build_hors_tablette() -> None:
    build_tablette(HORS_CONSOLE_TABLETTE, HORS_CONSOLE_TABLETTE_DIR)


def png_header(path: pathlib.Path) -> tuple[int, int, bool]:
    """(largeur, hauteur, transparence) lus dans l'en-tête IHDR."""
    head = path.open("rb").read(26)
    if head[:8] != b"\x89PNG\r\n\x1a\n":
        sys.exit(f"{path.name} : ce n'est pas un PNG")
    width, height = struct.unpack(">II", head[16:24])
    return width, height, head[25] in (4, 6)


def build_check() -> None:
    """Confronte les fichiers produits aux contraintes de la Play Console."""
    problems = []
    for path, want_w, want_h, max_mo in [(ICON, 512, 512, 1),
                                         (COMMUNS / "Image de présentation.png", 1024, 500, 15)]:
        if not path.exists():
            problems.append(f"{path.name} : absent")
            continue
        w, h, alpha = png_header(path)
        if (w, h) != (want_w, want_h):
            problems.append(f"{path.name} : {w}x{h}, attendu {want_w}x{want_h}")
        if alpha:
            problems.append(f"{path.name} : transparence, la Console la refuse")
        if path.stat().st_size > max_mo * 1024 * 1024:
            problems.append(f"{path.name} : plus de {max_mo} Mo")

    if not 2 <= len(CAPTURES) <= 8:
        problems.append(f"{len(CAPTURES)} captures, la Console en veut 2 à 8")
    promouvables = 0
    for name, *_ in CAPTURES:
        path = CAPTURES_DIR / f"{name}.png"
        if not path.exists():
            problems.append(f"{path.relative_to(OUT)} : absent")
            continue
        w, h, _ = png_header(path)
        if (w, h) not in ((1080, 1920), (1920, 1080)):
            problems.append(f"{path.name} : {w}x{h}, attendu du 9:16 ou du 16:9")
        if path.stat().st_size > 8 * 1024 * 1024:
            problems.append(f"{path.name} : plus de 8 Mo")
        if w >= 1080 and h >= 1080:
            promouvables += 1
    if promouvables < 4:
        problems.append(f"{promouvables} captures au moins 1080x1080, il en faut 4 "
                        "pour que l'application soit promouvable")

    for name, *_ in HORS_CONSOLE_FICHIERS:
        path = HORS_CONSOLE / f"{name}.png"
        if not path.exists():
            problems.append(f"{path.relative_to(OUT)} : absent")
        elif png_header(path)[:2] != (1080, 1920):
            problems.append(f"{path.name} : pas en 1080x1920")

    tablette = [TABLETTE_DIR / f"{n}.png" for n, _, _ in CAPTURES_TABLETTE]
    tablette += [HORS_CONSOLE_TABLETTE_DIR / f"{n}.png" for n, _, _ in HORS_CONSOLE_TABLETTE]
    for path in tablette:
        if not path.exists():
            problems.append(f"{path.relative_to(OUT)} : absent")
            continue
        w, h, alpha = png_header(path)
        # 7 pouces : 320 à 3840 px ; 10 pouces : 1080 à 7680 px ; 16:9
        if (w, h) != (1920, 1080):
            problems.append(f"{path.name} : {w}x{h}, attendu 1920x1080")
        if alpha:
            problems.append(f"{path.name} : transparence")
        if path.stat().st_size > 8 * 1024 * 1024:
            problems.append(f"{path.name} : plus de 8 Mo")

    for problem in problems:
        print(f"  ✗ {problem}")
    if problems:
        sys.exit(f"{len(problems)} problème(s)")
    print(f"check  ok : icône, image de présentation, {len(CAPTURES)} captures téléphone, "
          f"{len(HORS_CONSOLE_FICHIERS)} visuels hors Console, {len(CAPTURES_TABLETTE)} captures "
          f"tablette et {len(HORS_CONSOLE_TABLETTE)} visuels hors Console tablette, conformes")


def main(argv: list[str]) -> int:
    for tool in ("google-chrome", "convert"):
        if not shutil.which(tool):
            sys.exit(f"{tool} introuvable")
    targets = argv[1:] or ["icon", "feature", "shots", "hors", "tablette", "hors-tablette", "check"]
    known = {"icon": build_icon, "feature": build_feature,
             "shots": build_shots, "hors": build_hors_console,
             "tablette": build_tablette, "hors-tablette": build_hors_tablette,
             "check": build_check}
    for target in targets:
        if target not in known:
            sys.exit(f"cible inconnue : {target} ({' | '.join(known)})")
        known[target]()
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
