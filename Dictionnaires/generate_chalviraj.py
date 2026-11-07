#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
📚 CHALVIRAJ — l'export complet du dictionnaire, récupéré au build
====================================================================

Télécharge le dictionnaire Chalviraj Kréyòl Gwadloupéyen
(https://chalviraj.com) par `GET /api/v1/export` et l'écrit dans
`android_keyboard/app/src/main/assets/chalviraj.json`, qui part dans l'APK et
l'AAB. L'export est fourni tel quel sous la clé `export`, enveloppé de sa
licence et de la citation à afficher : ce script ne choisit pas encore ce que
l'application en lit.

Les données sont en **CC BY-NC-SA 4.0** et leur accès est régi par les
conditions d'utilisation de l'API (version 1.2). Cela fixe cinq règles, qu'il
ne faut pas assouplir sans l'accord écrit du projet Chalviraj :

- **Jamais commité.** Le dépôt est public, et l'article 6 interdit de
  rediffuser une copie complète de la base. L'actif et le cache sont dans
  `.gitignore` ; ils n'existent que sur le disque de la CI et dans l'APK.
- **Jamais dans `docs/assets/`.** Le simulateur de GitHub Pages servirait le
  fichier en clair à n'importe qui. Ce script n'écrit que l'actif Android.
- **Jamais appelé par l'application.** Le clavier n'a pas la permission
  INTERNET, et l'API journalise chaque terme cherché (article 9) : interroger
  le serveur depuis le téléphone reviendrait à lui envoyer ce que l'on tape.
  Seule la CI parle à l'API, une fois par build.
- **Jamais fusionné dans `creole_translations.json`.** Celui-ci est en
  CC BY-SA 4.0 (Kreyolopedia, Wiktionnaire), et les deux licences ne se
  combinent pas dans une même œuvre adaptée. Chacune garde son fichier.
- **Citer là où la donnée s'affiche** (article 3) : « Chalviraj Kréyòl
  Gwadloupéyen » avec un lien actif vers https://chalviraj.com, à l'endroit
  même, pas seulement dans une page de crédits.

L'export est limité à deux appels par heure. Un export de moins de 30 minutes
est donc réutilisé depuis `Dictionnaires/chalviraj_data/`, et le workflow ne
l'appelle pas pour les pull requests.

Usage :
    export CHALVIRAJ_API_KEY=...        # ou dans un .env, jamais dans un fichier suivi
    python generate_chalviraj.py        # sans clé ou sans réseau : ignoré, code 0
    python generate_chalviraj.py --strict   # exige un export frais et complet
    python generate_chalviraj.py --forcer   # ignore un export récent en cache

Fait avec ❤️ pour préserver le Kréyòl
"""

import json
import os
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timedelta, timezone
from email.utils import parsedate_to_datetime
from pathlib import Path

ICI = Path(__file__).resolve().parent
DOSSIER_CACHE = ICI / "chalviraj_data"
CACHE_EXPORT = DOSSIER_CACHE / "export.json"
CACHE_META = DOSSIER_CACHE / "export.meta.json"
ACTIF = ICI.parent / "android_keyboard/app/src/main/assets/chalviraj.json"

# Surchargeable pour les essais contre un faux serveur local, jamais en CI.
URL_API = os.getenv("CHALVIRAJ_API_URL", "https://chalviraj.com/api/v1").rstrip("/")

USER_AGENT = ("KlavyeKreyolKarukera "
              "(https://github.com/famibelle/KreyolKeyb) generate_chalviraj")

LICENCE = "CC BY-NC-SA 4.0"
LICENCE_URL = "https://creativecommons.org/licenses/by-nc-sa/4.0/deed.fr"
# La mention exacte que la page de l'API demande d'afficher.
ATTRIBUTION = ("Chalviraj Kréyòl Gwadloupéyen — https://chalviraj.com — "
               "CC BY-NC-SA 4.0")

# Deux exports par heure : à moins de 30 minutes, le précédent est réutilisé.
REUTILISER_SI_MOINS_DE = timedelta(minutes=30)

# Attente maximale sur un 429 avant d'abandonner. Le quota d'export est horaire,
# donc un Retry-After long est probable ; on n'immobilise pas un runner une heure.
ATTENTE_MAX_S = 90

# PROVISOIRE : la taille réelle du dictionnaire n'est pas encore connue. À
# remonter à ~90 % du volume du premier export réel, ici et dans build-apk.yml.
MINIMUM_ENTREES = 1000

CONSEILS = {
    "unauthorized": "clé inconnue, révoquée, ou partenaire suspendu : "
                    "vérifier le secret CHALVIRAJ_API_KEY",
    "terms_not_accepted": "la version en cours des conditions n'est pas "
                          "acceptée : l'accepter dans l'espace partenaire",
    "export_not_allowed": "l'export complet n'est pas ouvert sur cette clé : "
                          "le demander au projet Chalviraj",
    "rate_limited": "quota dépassé (l'export est limité à deux appels par heure)",
}


class ErreurChalviraj(Exception):
    pass


def _signaler(niveau, message):
    """Affiche le message, et l'annote dans l'interface de GitHub Actions."""
    if os.getenv("GITHUB_ACTIONS") == "true":
        print(f"::{niveau}::{message}")
    else:
        print(message)


def _charger_env():
    """Lit un .env local s'il existe, comme KreyolComplet.py pour HF_TOKEN."""
    try:
        from dotenv import load_dotenv
    except ImportError:
        return
    for chemin in (ICI / ".env", ICI.parent / ".env"):
        if chemin.exists():
            load_dotenv(chemin)
            return


def _secondes_retry_after(valeur):
    if not valeur:
        return None
    try:
        return max(0, int(valeur))
    except ValueError:
        pass
    try:
        echeance = parsedate_to_datetime(valeur)
    except (TypeError, ValueError):
        return None
    return max(0, int((echeance - datetime.now(timezone.utc)).total_seconds()))


def _lire_erreur(erreur_http):
    """Code et message du corps d'erreur de l'API, toujours de la forme
    {"error": {"code": ..., "message": ...}}."""
    try:
        corps = json.loads(erreur_http.read().decode("utf-8"))
        return corps["error"]["code"], corps["error"]["message"]
    except Exception:
        return None, erreur_http.reason


def telecharger(cle):
    requete = urllib.request.Request(
        f"{URL_API}/export",
        headers={"X-API-Key": cle, "User-Agent": USER_AGENT,
                 "Accept": "application/json"})
    for essai in (1, 2):
        try:
            with urllib.request.urlopen(requete, timeout=300) as reponse:
                return reponse.read()
        except urllib.error.HTTPError as erreur:
            code, message = _lire_erreur(erreur)
            if erreur.code == 429 and essai == 1:
                attente = _secondes_retry_after(erreur.headers.get("Retry-After"))
                if attente is not None and attente <= ATTENTE_MAX_S:
                    print(f"⏳ Quota atteint, nouvel essai dans {attente} s")
                    time.sleep(attente)
                    continue
            conseil = CONSEILS.get(code)
            raise ErreurChalviraj(
                f"HTTP {erreur.code} {code or ''} — {message}"
                + (f" ({conseil})" if conseil else "")) from None
        except (urllib.error.URLError, TimeoutError, OSError) as erreur:
            raise ErreurChalviraj(f"API injoignable : {erreur}") from None
    raise ErreurChalviraj("quota toujours dépassé après l'attente")


def lire_cache():
    """(octets, date de récupération) du dernier export, ou (None, None)."""
    if not (CACHE_EXPORT.exists() and CACHE_META.exists()):
        return None, None
    with open(CACHE_META, encoding="utf-8") as f:
        recupere_le = datetime.fromisoformat(json.load(f)["recupere_le"])
    return CACHE_EXPORT.read_bytes(), recupere_le


def ecrire_cache(octets, recupere_le):
    DOSSIER_CACHE.mkdir(parents=True, exist_ok=True)
    CACHE_EXPORT.write_bytes(octets)
    with open(CACHE_META, "w", encoding="utf-8") as f:
        json.dump({"recupere_le": recupere_le.isoformat(timespec="seconds")}, f)


def compter_entrees(donnees):
    """Nombre d'entrées de l'export, et où elles ont été comptées.

    La forme exacte de l'export n'est pas documentée sur la page de l'API :
    une liste à la racine, ou la plus longue liste d'un objet racine (les
    réponses de l'API portent aussi leur licence et leur citation)."""
    if isinstance(donnees, list):
        return len(donnees), "racine", donnees
    if isinstance(donnees, dict):
        listes = [(len(v), k, v) for k, v in donnees.items() if isinstance(v, list)]
        if listes:
            return max(listes, key=lambda t: t[0])
        return len(donnees), "clés de la racine", list(donnees.values())
    return 0, "aucune", []


def decrire_structure(donnees, entrees):
    """Les noms de champs seulement, jamais le contenu : de quoi écrire le
    lecteur Android à partir d'un journal de CI."""
    if isinstance(donnees, dict):
        print(f"🔎 Clés de la racine : {sorted(donnees.keys())}")
    premiere = next((e for e in entrees if isinstance(e, dict)), None)
    if premiere is not None:
        print(f"🔎 Champs d'une entrée : {sorted(premiere.keys())}")


def sauvegarder(donnees, nombre, recupere_le):
    actif = {
        "version": 1,
        "generated": recupere_le.date().isoformat(),
        "source": "https://chalviraj.com/api/v1/export",
        "licence": LICENCE,
        "licence_url": LICENCE_URL,
        "attribution": ATTRIBUTION,
        "count": nombre,
        "export": donnees,
    }
    # Écriture atomique : un build interrompu ne laisse pas un JSON tronqué.
    temporaire = ACTIF.with_suffix(".json.tmp")
    with open(temporaire, "w", encoding="utf-8") as f:
        json.dump(actif, f, ensure_ascii=False, separators=(",", ":"))
    temporaire.replace(ACTIF)
    taille = ACTIF.stat().st_size / 1024
    print(f"💾 {ACTIF.relative_to(ICI.parent)} : {nombre} entrées, {taille:.0f} Ko")


def main():
    strict = "--strict" in sys.argv
    forcer = "--forcer" in sys.argv
    echec = 1 if strict else 0

    print("📚 DICTIONNAIRE CHALVIRAJ")
    print("=" * 45)
    _charger_env()
    cle = os.getenv("CHALVIRAJ_API_KEY", "").strip()
    if not cle:
        _signaler("warning" if strict else "notice",
                  "CHALVIRAJ_API_KEY absente : l'APK part sans le dictionnaire Chalviraj.")
        return echec

    maintenant = datetime.now(timezone.utc)
    octets, recupere_le = lire_cache()
    telecharge = False
    if octets is not None and not forcer and maintenant - recupere_le < REUTILISER_SI_MOINS_DE:
        print(f"♻️ Export du {recupere_le:%Y-%m-%d %H:%M} UTC réutilisé "
              f"(moins de 30 minutes, quota de deux exports par heure)")
    else:
        try:
            print("🌐 Téléchargement de l'export complet...")
            octets, recupere_le = telecharger(cle), maintenant
            telecharge = True
        except ErreurChalviraj as erreur:
            # Le mode strict refuse un cache ancien pour la même raison que
            # generate_translations.py : un actif fait de données figées serait
            # quand même daté du jour.
            if strict or octets is None:
                _signaler("error" if strict else "warning",
                          f"Export Chalviraj impossible : {erreur}")
                return echec
            _signaler("warning",
                      f"Export Chalviraj impossible ({erreur}) ; "
                      f"cache du {recupere_le:%Y-%m-%d} utilisé.")

    try:
        donnees = json.loads(octets.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as erreur:
        _signaler("error" if strict else "warning",
                  f"L'export Chalviraj n'est pas du JSON valide : {erreur}")
        return echec

    nombre, ou, entrees = compter_entrees(donnees)
    print(f"📊 {nombre} entrées (comptées dans : {ou})")
    decrire_structure(donnees, entrees)

    # Ce qu'il faut refuser, c'est un actif tronqué, pas une requête ratée :
    # rien n'est écrit, et l'éventuel cache n'est pas écrasé par un export
    # qui aurait perdu la majorité de ses entrées.
    if nombre < MINIMUM_ENTREES:
        _signaler("error" if strict else "warning",
                  f"Export Chalviraj de {nombre} entrées seulement "
                  f"(minimum {MINIMUM_ENTREES}) : rien n'est écrit.")
        return echec

    if telecharge:
        ecrire_cache(octets, recupere_le)
    sauvegarder(donnees, nombre, recupere_le)
    print("🎉 TERMINÉ")
    return 0


if __name__ == "__main__":
    sys.exit(main())
