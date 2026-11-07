#!/usr/bin/env python3
"""Les meubles des cartes, tirés de silhouettes du domaine public.

Chaque meuble de `scripts/meubles_sources.json` vient d'une silhouette publiée
sous CC0 ou marquée du domaine public : PhyloPic pour le vivant, Openclipart
et les pictogrammes CC0 (Pinhead, Temaki) pour le reste (propriétaire,
2026-10-31). Le fichier garde, pour chacun, la page d'origine, l'auteur et la
licence, vérifiée sur la page même ; rien de tout cela n'est obligatoire sous
CC0, mais c'est ce qui permet de répondre à « d'où vient ce dessin ? ».

Le script ramène chaque silhouette aux règles de `carnet/Meubles.kt` : un
carré de cent unités centré sur l'origine, y vers le bas, tournée vers la
droite, posée au sol (y = 47) ou centrée si elle vole, nage ou flotte, et
**pleine** : les petits trous (un œil, une pastille de couleur) sont comblés,
seuls restent ceux qui dessinent quelque chose (les vitres du bus, le « ! »
du panneau).

Deux chemins mènent au polygone :

- `vecteur` (PhyloPic) : le SVG est lu tel quel, chemin par chemin ;
- `rendu` (le reste) : Chrome sans interface rend le SVG sur fond blanc, tout
  ce qui n'est pas blanc fait encre (ou seulement le sombre, `sombre`), et
  OpenCV reprend les contours. Une illustration en couleurs devient ainsi sa
  silhouette, quelle que soit la façon dont elle est construite.

Quelques meubles sont retouchés (le veau sans pis, les rayures de l'abeille et
de la guêpe, les zigzags de l'œuf de Pâques) ou composés (la forêt de trois sapins, le verger de deux arbres,
la pluie sous son nuage) : voir RETOUCHES et COMPOSES.

Sorties :

- `carnet/MeublesDomaine.kt`, la table nom → chemin lue par Meubles.kt ;
- `scripts/meubles.json`, les mêmes chemins pour les scripts de la règle et
  de la planche de la boîte.

Les téléchargements et les rendus sont gardés dans ~/.cache/luxkeyb/meubles.

    python3 scripts/meubles_domaine.py                 # tout
    python3 scripts/meubles_domaine.py --apercu x.svg  # et une planche de contrôle

Dépendances : google-chrome, opencv-python, numpy, pillow, shapely, svgelements.
"""
import hashlib
import json
import math
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request

import cv2
import numpy as np
from PIL import Image
from shapely import affinity
from shapely.geometry import LineString, MultiPolygon, Polygon, box
from shapely.ops import unary_union
from svgelements import SVG, Close, Move, Path

ICI = os.path.dirname(os.path.abspath(__file__))
SOURCES = os.path.join(ICI, 'meubles_sources.json')
SORTIE_KT = os.path.join(ICI, '..', 'android_keyboard/app/src/main/java/com/example/kreyolkeyboard/carnet/MeublesDomaine.kt')
SORTIE_JSON = os.path.join(ICI, 'meubles.json')
CACHE = os.path.expanduser('~/.cache/luxkeyb/meubles')
UA = {'User-Agent': 'Mozilla/5.0 (LuxKeyb meubles)'}
PX = 1200           # côté du rendu, en pixels
TOLERANCE = 0.1     # simplification du contour, en unités du carré


# ------------------------------------------------------------- sources

def telecharger(url):
    os.makedirs(CACHE, exist_ok=True)
    f = os.path.join(CACHE, hashlib.md5(url.encode()).hexdigest()[:16] + '.svg')
    if not os.path.exists(f) or os.path.getsize(f) < 100:
        # Iconify répond 429 à qui va trop vite : on attend et on recommence.
        for essai in range(6):
            try:
                req = urllib.request.Request(url, headers=UA)
                with urllib.request.urlopen(req, timeout=60) as r:
                    open(f, 'wb').write(r.read())
                break
            except urllib.error.HTTPError as e:
                if e.code != 429 or essai == 5:
                    raise
                time.sleep(5 * (essai + 1))
    return f


def lire_vecteur(fichier, pas=1.5):
    """Le dessin d'un SVG en polygones (pair-impair dans chaque chemin)."""
    formes = []
    for el in SVG.parse(fichier).elements():
        if not isinstance(el, Path) or el.fill is None or str(el.fill) in ('none', '#ffffff', 'white'):
            continue
        p = Path(el)
        p.reify()
        anneaux, cour = [], []
        for seg in p:
            if isinstance(seg, Move):
                if len(cour) > 2:
                    anneaux.append(cour)
                cour = [(seg.end.x, seg.end.y)]
            elif isinstance(seg, Close):
                if len(cour) > 2:
                    anneaux.append(cour)
                cour = []
            else:
                n = max(2, int(seg.length(error=1e-2) / pas))
                cour += [(q.x, q.y) for q in (seg.point(i / n) for i in range(1, n + 1))]
        if len(cour) > 2:
            anneaux.append(cour)
        f = None
        for a in anneaux:
            g = Polygon(a).buffer(0)
            f = g if f is None else f.symmetric_difference(g)
        if f is not None:
            formes.append(f)
    return unary_union(formes)


def rendre(fichier):
    """Le SVG rendu par Chrome en PNG carré sur fond blanc (gardé en cache)."""
    cle = hashlib.md5(open(fichier, 'rb').read()).hexdigest()[:16]
    png = os.path.join(CACHE, cle + '.png')
    if os.path.exists(png):
        return png
    html = os.path.join(CACHE, cle + '.html')
    open(html, 'w').write(f'<!doctype html><html><body style="margin:0;background:#fff;width:{PX}px;height:{PX}px;'
                          f'display:flex;align-items:center;justify-content:center">'
                          f'<img src="file://{fichier}" style="width:{PX - 100}px;height:{PX - 100}px;object-fit:contain">'
                          f'</body></html>')
    subprocess.run(['google-chrome', '--headless=new', '--disable-gpu', '--no-sandbox', '--hide-scrollbars',
                    f'--window-size={PX},{PX + 100}', f'--screenshot={png}', f'file://{html}'],
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=120, check=True)
    Image.open(png).crop((0, 0, PX, PX)).save(png)
    return png


def lire_rendu(fichier, seuil=200, sombre=False, recadre=None, dilate=0):
    a = np.asarray(Image.open(rendre(fichier)).convert('RGB')).astype(np.int16)
    m = ((a.mean(axis=2) if sombre else a.min(axis=2)) < seuil).astype(np.uint8) * 255
    if recadre:
        x0, y0, x1, y1 = (int(v * PX) for v in recadre)
        z = np.zeros_like(m)
        z[y0:y1, x0:x1] = m[y0:y1, x0:x1]
        m = z
    if dilate:
        m = cv2.dilate(m, np.ones((dilate, dilate), np.uint8))
    contours, h = cv2.findContours(m, cv2.RETR_CCOMP, cv2.CHAIN_APPROX_NONE)
    morceaux = []
    for i, c in enumerate(contours):
        if h[0][i][3] != -1 or len(c) < 3:
            continue                                  # un trou : il vient avec son parent
        trous, j = [], h[0][i][2]
        while j != -1:
            if len(contours[j]) >= 3:
                trous.append(contours[j][:, 0, :].astype(float))
            j = h[0][j][0]
        p = Polygon(c[:, 0, :].astype(float), trous).buffer(0)
        if p.area > 4:
            morceaux.append(p)
    return unary_union(morceaux).simplify(1.0, preserve_topology=True)


# ----------------------------------------------------------- les règles

def combler(f, aire):
    """Comble les trous plus petits que `aire` (unités carrées)."""
    polys = f.geoms if isinstance(f, MultiPolygon) else [f]
    return unary_union([Polygon(p.exterior, [i for i in p.interiors if Polygon(i).area >= aire]) for p in polys])


def normaliser(f, miroir=False, vole=False, angle=0, trou=2.5):
    """Tournée vers la droite, dans le carré, posée sur y = 47 ou centrée."""
    if miroir:
        f = affinity.scale(f, -1, 1, origin=(0, 0))
    if angle:
        f = affinity.rotate(f, angle, origin='centroid')
    x0, y0, x1, y1 = f.bounds
    s = min(96 / (x1 - x0), (96 if vole else 94) / (y1 - y0))
    f = affinity.scale(f, s, s, origin=(0, 0))
    x0, y0, x1, y1 = f.bounds
    f = affinity.translate(f, -(x0 + x1) / 2, -(y0 + y1) / 2 if vole else 47 - y1)
    return combler(f.buffer(0), trou)


def brut(nom, S, **force):
    d = dict(S[nom])
    d.update(force)
    f = telecharger(d['url'])
    if d['mode'] == 'vecteur':
        g = lire_vecteur(f)
    else:
        g = lire_rendu(f, d.get('seuil', 200), d.get('sombre', False), d.get('recadre'), d.get('dilate', 0))
    return normaliser(g, d.get('miroir', False), d.get('vole', False), d.get('angle', 0), d.get('trou', 2.5))


def trait(pts, demi):
    """Un trait d'épaisseur 2 × demi le long d'une ligne brisée."""
    return LineString(pts).buffer(demi, cap_style=2, join_style=2)


def _sans_pis(f):
    """La seule silhouette de bovin libre est une vache : on retire le pis."""
    return f - box(-30.7, 25.2, -18, 31)


def _rayures_abeille(f):
    """Trois jours qui montent du ventre jusque sous les ailes repliées."""
    for x in (-17, -25, -33):
        f = f - LineString([(x + 1.4, 14), (x - 1.2, 2), (x + .6, -10)]).buffer(1.05)
    return f


def _rayures_guepe(f):
    """Trois rayures en travers de l'abdomen qui pend, sans toucher la patte voisine."""
    abdomen = Polygon([(-48, -2), (-21, -3), (-21.5, 8), (-25, 22), (-28.5, 34), (-34, 46), (-48, 46)])
    for t in (.36, .56, .76):
        px, py = -20 - 18 * t, 44 * t
        bande = trait([(px - 16 * .926, py - 16 * .379), (px + 16 * .926, py + 16 * .379)], 1.1)
        f = f - (bande & abdomen)
    return f


def _oeuf_decore(f):
    """L'œuf de Pâques : l'œuf, et deux bandes en zigzag découpées, pour
    qu'il ne se confonde pas avec l'œuf tout court."""
    x0, y0, x1, y1 = f.bounds
    for t in (.38, .62):
        y = y0 + (y1 - y0) * t
        pas = (x1 - x0) / 8
        pts = [(x0 - 2 + i * pas, y + (2.2 if i % 2 else -2.2)) for i in range(10)]
        f = f - LineString(pts).buffer(1.3, join_style=2)
    return f


RETOUCHES = {'veau': _sans_pis, 'abeille': _rayures_abeille, 'guepe': _rayures_guepe,
             'oeuf_paques': _oeuf_decore}


def _foret(S):
    """Trois sapins : un grand devant, deux plus petits de part et d'autre."""
    s = brut('foret', S)
    return normaliser(unary_union([affinity.translate(affinity.scale(s, .44, .44, origin=(0, 47)), -24, 0),
                                   affinity.translate(affinity.scale(s, .5, .5, origin=(0, 47)), 23, 0),
                                   affinity.scale(s, .62, .62, origin=(0, 47))]))


def _verger(S):
    """Deux pommiers côte à côte."""
    s = brut('verger', S)
    return normaliser(unary_union([affinity.translate(affinity.scale(s, .55, .55, origin=(0, 47)), -22, 0),
                                   affinity.translate(affinity.scale(s, .62, .62, origin=(0, 47)), 20, 0)]))


def _pluie(S):
    """Le nuage, et trois gouttes dessous : celles du meuble « eau »."""
    nuage = affinity.translate(affinity.scale(brut('pluie', S), .9, .9, origin=(0, 0)), 0, -14)
    goutte = affinity.scale(brut('eau', S), .16, .16, origin=(0, 0))
    return normaliser(unary_union([nuage] + [affinity.translate(goutte, x, y) for x, y in ((-22, 30), (0, 36), (22, 30))]),
                      vole=True)


COMPOSES = {'foret': _foret, 'verger': _verger, 'pluie': _pluie}


def meuble(nom, S):
    f = COMPOSES[nom](S) if nom in COMPOSES else brut(nom, S)
    return RETOUCHES.get(nom, lambda g: g)(f).buffer(0)


# ------------------------------------------------------------- sorties

def chemin(f, tolerance=TOLERANCE, miettes=0):
    """Le polygone en chemin SVG réduit : M, paires implicites, Z. Les pièces
    de moins de `miettes` unités carrées (les îlots d'une côte) tombent."""
    f = f.simplify(tolerance, preserve_topology=True)
    polys = [p for p in (f.geoms if isinstance(f, MultiPolygon) else [f]) if p.area >= miettes]
    morceaux = []
    for p in polys:
        for anneau in [p.exterior, *p.interiors]:
            pts, prec = [], None
            for x, y in list(anneau.coords)[:-1]:
                q = (round(x, 1) + 0.0, round(y, 1) + 0.0)
                if q != prec:
                    pts.append(q)
                    prec = q
            if len(pts) >= 3:
                morceaux.append('M' + ' '.join(f'{x:g} {y:g}' for x, y in pts) + 'Z')
    return ''.join(morceaux)


def kotlin(chemins, S):
    lignes = []
    for nom, d in chemins.items():
        s = S[nom]
        lic = 'CC0' if 'zero' in s['licence'] else 'domaine public'
        lignes.append(f'        // {s["source"]}, {s["auteur"]}, {lic} : {s["page"]}')
        lignes.append(f'        "{nom}" to "{d}",')
    return f'''package com.example.kreyolkeyboard.carnet

// Généré par scripts/meubles_domaine.py : ne pas modifier à la main.

/**
 * Les meubles tirés du domaine public, en chemins SVG réduits (`M`, paires
 * implicites, `Z`) dans le carré de cent unités de [Meubles]. Chaque entrée
 * dit d'où vient sa silhouette ; la table complète, avec les retouches, est
 * `scripts/meubles_sources.json`.
 */
internal object MeublesDomaine {{

    val TRACES: Map<String, String> = mapOf(
{chr(10).join(lignes)}
    )
}}
'''


def apercu(chemins, fichier):
    cases = []
    for i, (nom, d) in enumerate(chemins.items()):
        x, y = (i % 8) * 130 + 65, (i // 8) * 150 + 70
        cases.append(f'<g transform="translate({x} {y})"><rect x="-55" y="-55" width="110" height="110" rx="8" '
                     f'fill="#22356f"/><path d="{d}" fill-rule="evenodd" fill="#FFFDF6"/>'
                     f'<text y="72" text-anchor="middle" font-family="sans-serif" font-size="13">{nom}</text></g>')
    h = ((len(chemins) - 1) // 8 + 1) * 150 + 10
    open(fichier, 'w').write(f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1040 {h}" width="1040" height="{h}">'
                             f'<rect width="1040" height="{h}" fill="#f3f1ea"/>{"".join(cases)}</svg>')


def main():
    S = json.load(open(SOURCES))
    chemins = {}
    for nom in S:
        chemins[nom] = chemin(meuble(nom, S), S[nom].get('tolerance', TOLERANCE), S[nom].get('miettes', 0))
        print(f'{nom:15s} {len(chemins[nom]):6d} car.', flush=True)
    json.dump(chemins, open(SORTIE_JSON, 'w'), indent=0, ensure_ascii=False)
    open(SORTIE_KT, 'w').write(kotlin(chemins, S))
    print(len(chemins), 'meubles,', sum(len(d) for d in chemins.values()), 'caractères')
    if '--apercu' in sys.argv:
        apercu(chemins, sys.argv[sys.argv.index('--apercu') + 1])


if __name__ == '__main__':
    main()
