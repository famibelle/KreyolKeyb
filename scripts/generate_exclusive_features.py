#!/usr/bin/env python3
"""Régénère docs/stats/exclusive_features.json depuis android_keyboard/CHANGELOG.md.

Liste, pour la page GitHub Pages, les fonctionnalités livrées après la version
actuellement en production sur le Play Store (`production_version`, mise à
jour manuellement via `--set-production-version` le jour où une nouvelle
version est publiée sur le Store).

Deux sources de contenu par fonctionnalité :
- `CURATED` : texte écrit à la main, orienté utilisateur (prioritaire).
- à défaut, extraction automatique du premier point du CHANGELOG dont le
  titre commence par un emoji de la liste `FEATURE_EMOJIS`, en écartant les
  points de diagnostic (Constat/Cause/...) propres aux entrées de bug fix.
  Ces entrées sont marquées `"curated": false` pour rester traçables.
"""
from __future__ import annotations

import argparse
import json
import re
from datetime import datetime, timezone
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
CHANGELOG = REPO_ROOT / "android_keyboard" / "CHANGELOG.md"
OUTPUT = REPO_ROOT / "docs" / "stats" / "exclusive_features.json"
RELEASE_URL = "https://github.com/famibelle/KreyolKeyb/releases/tag/v{version}"

FEATURE_EMOJIS = {"✨", "😀", "🎮", "📤", "🎉"}
DIAGNOSTIC_PREFIXES = (
    "**Constat**",
    "**Cause**",
    "**Premier essai infructueux**",
    "**Effet de bord découvert en vérifiant**",
    "**Piège évité**",
    "**Vérifié",
)

# Texte curé à la main pour les versions déjà connues : prioritaire sur
# l'extraction automatique. Complétez cette liste au fil des prochaines
# versions pour garder une formulation orientée utilisateur.
CURATED: dict[str, list[dict[str, str]]] = {
    "10.9.2": [
        {
            "emoji": "🔖",
            "title": "Vos partages se retrouvent entre eux",
            "description": (
                "Les messages envoyés depuis l'application partaient chacun de "
                "leur côté. Ils se terminent tous maintenant par "
                "#KlavyéKréyòl : la carte de niveau, la carte d'activation, le "
                "partage de l'application et la puce du clavier. De quoi voir "
                "qui d'autre écrit en kréyòl."
            ),
        }
    ],
    "10.9.1": [
        {
            "emoji": "⚡",
            "title": "La frappe ne marque plus de temps",
            "description": (
                "Chaque mot validé faisait réenregistrer tout le dictionnaire "
                "avant de rendre la main au clavier, jusqu'à une demi-seconde "
                "sur un simple espace. L'enregistrement se fait désormais en "
                "coulisse : rien n'est perdu, et l'écriture reste fluide d'un "
                "bout à l'autre de la phrase."
            ),
        },
        {
            "emoji": "🔔",
            "title": "Le signal de niveau ne se perd plus en route",
            "description": (
                "La pastille de l'onglet « Kréyòl an mwen » n'apparaissait "
                "qu'en rouvrant complètement l'application : un palier franchi "
                "pendant qu'elle attendait en arrière-plan passait inaperçu. "
                "Elle s'affiche maintenant dès le retour, et la pastille de "
                "l'icône s'éteint une fois la progression consultée."
            ),
        },
    ],
    "10.9.0": [
        {
            "emoji": "📤",
            "title": "Votre carte de niveau se partage quand vous voulez",
            "description": (
                "Elle n'était proposée qu'une fois, au moment des "
                "félicitations : répondre « Plus tard » la faisait perdre "
                "pour de bon. Un bouton posé en permanence dans l'onglet "
                "« Kréyòl an mwen » la reconstruit à la demande, autant de "
                "fois que vous le souhaitez."
            ),
        }
    ],
    "10.8.0": [
        {
            "emoji": "🌱",
            "title": "Vos passages de niveau se signalent enfin",
            "description": (
                "Franchir un palier de vocabulaire ne se voyait qu'en "
                "ouvrant vos statistiques. Une pastille se pose désormais "
                "sur l'icône de l'application, sans son ni bandeau : rien ne "
                "vient vous déranger pendant que vous écrivez, et vous la "
                "découvrez en revenant à votre écran d'accueil."
            ),
        }
    ],
    "10.6.0": [
        {
            "emoji": "🔒",
            "title": "Le clavier ne retient aucun de vos mots",
            "description": (
                "La 10.5.0 apprenait les mots absents des textes créoles pour "
                "les proposer ensuite. Cette fonction est retirée : un clavier "
                "qui conserve ce qu'on écrit n'est pas ce qu'on attend d'un "
                "clavier. Ce qui avait été enregistré est effacé au premier "
                "démarrage."
            ),
        }
    ],
    "10.4.1": [
        {
            "emoji": "✅",
            "title": "Vos mots kréyòl ne sont plus soulignés en rouge",
            "description": (
                "Le correcteur orthographique kréyòl existait mais n'était "
                "jamais sollicité par Android : tous vos mots créoles "
                "passaient donc pour des fautes. Il fonctionne désormais, à "
                "sélectionner une fois dans Réglages › Système › Clavier › "
                "Correcteur orthographique."
            ),
        }
    ],
    "10.4.0": [
        {
            "emoji": "🎯",
            "title": "Les suggestions suivent enfin le curseur",
            "description": (
                "Revenir corriger un mot déjà écrit, effacer l'espace qui le "
                "suit ou taper en plein milieu ne donnait plus aucune "
                "suggestion. C'est corrigé : le clavier sait de nouveau où "
                "vous en êtes dans votre texte."
            ),
        },
        {
            "emoji": "📊",
            "title": "Le clavier apprend votre vocabulaire",
            "description": (
                "Les mots que vous employez vraiment remontent dans les "
                "suggestions, même s'ils sont moins courants dans la "
                "littérature créole. Tout reste sur votre téléphone."
            ),
        },
        {
            "emoji": "🧠",
            "title": "Des prédictions plus justes",
            "description": (
                "Le clavier tient compte des deux derniers mots écrits au "
                "lieu d'un seul. Après « an ka », il propose kwè, vwè, "
                "travay, là où « ka » seul donnait fè, di, pran."
            ),
        },
    ],
    "10.1.0": [
        {
            "emoji": "😀",
            "title": "Tous les emojis, rangés par catégories",
            "description": (
                "Le panneau emoji ne se limite plus à une courte liste : "
                "l'ensemble des emojis est là, classé par catégories avec "
                "des onglets, et on passe de l'une à l'autre d'un glissement "
                "du doigt."
            ),
        }
    ],
    "10.3.0": [
        {
            "emoji": "🎮",
            "title": "Mo an Karénaj, le Wordle créole",
            "description": (
                "Devine un mot kréyòl de 5 lettres en 6 essais, avec un retour "
                "en couleur comme le Wordle. Un nouveau jeu de vocabulaire "
                "directement dans l'app."
            ),
        }
    ],
    "10.2.9": [
        {
            "emoji": "✨",
            "title": "Un guide pas à pas pour l'activation",
            "description": (
                "L'onglet Guide explique maintenant, captures d'écran à "
                "l'appui, comment activer et sélectionner le clavier dans "
                "les réglages Android."
            ),
        }
    ],
    "10.2.8": [
        {
            "emoji": "🌐",
            "title": "Un repère pour changer de clavier",
            "description": (
                "Une petite icône apparaît dans la barre d'espace pour "
                "retrouver facilement l'appui long qui ouvre le sélecteur "
                "de clavier."
            ),
        }
    ],
    "10.2.3": [
        {
            "emoji": "🎉",
            "title": "Une carte à partager après l'activation",
            "description": (
                "Une fois le clavier activé, une carte de félicitations "
                "propose de partager la nouvelle avec un message prêt à "
                "l'emploi."
            ),
        }
    ],
    "10.2.1": [
        {
            "emoji": "📤",
            "title": "Envoyer un mot à un ami",
            "description": (
                "Une puce apparaît dans la barre de suggestions dès la "
                "première utilisation réelle du clavier, pour partager "
                "l'app en un tap."
            ),
        }
    ],
}

VERSION_RE = re.compile(r"^## \[(\d+)\.(\d+)\.(\d+)\] - (\d{4}-\d{2}-\d{2})\s*$")
SECTION_RE = re.compile(r"^### (\S+)\s+(.+?)\s*$")
BULLET_RE = re.compile(r"^- (.+)$")


def version_tuple(v: str) -> tuple[int, int, int]:
    parts = v.strip().split(".")
    return tuple(int(p) for p in parts)  # type: ignore[return-value]


def parse_changelog(text: str) -> dict[str, list[dict[str, str]]]:
    """Retourne {version: [{"emoji":..., "title":..., "bullets":[...]}]}."""
    versions: dict[str, list[dict[str, str]]] = {}
    current_version: str | None = None
    current_section: dict[str, str] | None = None

    for line in text.splitlines():
        m = VERSION_RE.match(line)
        if m:
            current_version = f"{m.group(1)}.{m.group(2)}.{m.group(3)}"
            versions[current_version] = []
            current_section = None
            continue
        if current_version is None:
            continue
        m = SECTION_RE.match(line)
        if m:
            current_section = {"emoji": m.group(1), "title": m.group(2), "bullets": []}
            versions[current_version].append(current_section)
            continue
        m = BULLET_RE.match(line)
        if m and current_section is not None:
            current_section["bullets"].append(m.group(1))

    return versions


def pick_description(bullets: list[str], limit: int = 220) -> str:
    for bullet in bullets:
        if bullet.startswith(DIAGNOSTIC_PREFIXES):
            continue
        text = bullet
        break
    else:
        text = bullets[0] if bullets else ""

    text = re.sub(r"\*\*(.+?)\*\*", r"\1", text)  # retire le gras markdown
    if len(text) > limit:
        text = text[:limit].rsplit(" ", 1)[0] + "…"
    return text


def build_features(
    versions: dict[str, list[dict[str, str]]], production_version: str
) -> list[dict[str, object]]:
    cutoff = version_tuple(production_version)
    eligible = sorted(
        (v for v in versions if version_tuple(v) > cutoff),
        key=version_tuple,
        reverse=True,
    )

    features: list[dict[str, object]] = []
    for version in eligible:
        if version in CURATED:
            for item in CURATED[version]:
                features.append(
                    {
                        "version": version,
                        "emoji": item["emoji"],
                        "title": item["title"],
                        "description": item["description"],
                        "release_url": RELEASE_URL.format(version=version),
                        "curated": True,
                    }
                )
            continue

        for section in versions[version]:
            if section["emoji"] not in FEATURE_EMOJIS:
                continue
            features.append(
                {
                    "version": version,
                    "emoji": section["emoji"],
                    "title": section["title"],
                    "description": pick_description(section["bullets"]),
                    "release_url": RELEASE_URL.format(version=version),
                    "curated": False,
                }
            )

    return features


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--set-production-version",
        metavar="X.Y.Z",
        help="Met à jour la version considérée comme en production sur le Play Store.",
    )
    args = parser.parse_args()

    changelog_text = CHANGELOG.read_text(encoding="utf-8")
    versions = parse_changelog(changelog_text)
    all_versions = sorted(versions, key=version_tuple, reverse=True)
    latest_version = all_versions[0] if all_versions else "0.0.0"

    if args.set_production_version:
        production_version = args.set_production_version
    elif OUTPUT.exists():
        production_version = json.loads(OUTPUT.read_text(encoding="utf-8"))["production_version"]
    else:
        production_version = latest_version

    features = build_features(versions, production_version)

    data = {
        "production_version": production_version,
        "latest_version": latest_version,
        "as_of": datetime.now(timezone.utc).strftime("%Y-%m-%d"),
        "features": features,
    }

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"{len(features)} fonctionnalité(s) au-delà de v{production_version} -> {OUTPUT}")


if __name__ == "__main__":
    main()
