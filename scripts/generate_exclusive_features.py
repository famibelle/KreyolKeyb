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
    "12.0.1": [
        {
            "emoji": "🏷️",
            "title": "Le jeu de phrases s'appelle « Fraz a twou »",
            "description": (
                "Il était livré sous un nom français, « Mots à Trous », alors "
                "que les trois autres jeux du clavier portent déjà un nom que "
                "la langue reconnaît. Le nom kréyòl vient d'un locuteur, pas "
                "d'une traduction faite au passage."
            ),
        }
    ],
    "12.0.0": [
        {
            "emoji": "📚",
            "title": "Un onglet Dictionnaire, qui cherche dans les deux sens",
            "description": (
                "Tapez « kaz » ou tapez « maison » : la recherche répond dans "
                "les deux cas, sans vous demander de quel côté vous vous "
                "tenez. Touchez un mot et sa fiche s'ouvre, avec le sens, la "
                "prononciation, un exemple et la source. Appui long pour le "
                "copier."
            ),
            "image": "Screenshots/nouveaute_12.0.0_dictionnaire.png",
            "image_alt": (
                "À gauche, la recherche du mot français « maison » dans "
                "l'onglet Dictionnaire, qui remonte kaz, tiwèt et "
                "kalòj-a-poul. À droite, la fiche du mot kaz : prononciation, "
                "les sens « maison » et « case », un exemple d'emploi et la "
                "source Kreyolopedia."
            ),
        },
        {
            "emoji": "🎮",
            "title": "Les jeux disent enfin ce que veut dire leur mot",
            "description": (
                "Retrouver « KAPTÈ » sans savoir qu'il s'agit d'un capteur "
                "faisait travailler l'orthographe et rien d'autre. Les quatre "
                "jeux affichent maintenant la traduction, au moment où elle ne "
                "donne plus la réponse : à la fin de la partie pour Mo an "
                "Karénaj, sous chaque mot de la liste pour Mots Mêlés."
            ),
        },
        {
            "emoji": "💡",
            "title": "Le mot du jour porte sa traduction",
            "description": (
                "« difikilté », et juste en dessous « en français : "
                "difficulté ». Le classement de vos mots les plus employés "
                "fait de même. 1 145 mots kréyòl sont traduits à ce jour, "
                "depuis Kreyolopedia et le Wiktionnaire, deux sources libres "
                "citées dans l'application."
            ),
        },
    ],
    "11.0.0": [
        {
            "emoji": "📝",
            "title": "Un quatrième jeu : une phrase à laquelle il manque un mot",
            "description": (
                "Une vraie phrase kréyòl s'affiche, tirée du corpus littéraire "
                "du clavier, et un mot lui manque. Quatre propositions, une "
                "seule est celle qu'a écrite l'auteur : les trois autres sont "
                "des mots que le corpus atteste au même endroit, elles sonnent "
                "donc juste tant qu'on ne lit pas toute la phrase. 389 phrases, "
                "sur trois niveaux."
            ),
        },
        {
            "emoji": "🧭",
            "title": "Les quatre jeux se rejoignent derrière un onglet « Jé »",
            "description": (
                "À sept onglets, chacun n'avait qu'un septième de la largeur et "
                "les libellés se coupaient en plein milieu d'un mot. Les jeux "
                "tiennent désormais dans une seule destination, le guide et "
                "« À propos » descendent au pied de l'onglet Démarrage, et ce "
                "qui reste respire."
            ),
        },
        {
            "emoji": "⌨️",
            "title": "Un bouton pour revenir au clavier kréyòl",
            "description": (
                "Quand un autre clavier a pris la main, l'application le dit et "
                "propose de rechoisir le kréyòl en un geste, au lieu de vous "
                "renvoyer dans le tunnel d'installation alors que tout est déjà "
                "en place."
            ),
        },
    ],
    "10.14.2": [
        {
            "emoji": "🔤",
            "title": "Les lettres des touches perdent leur gras",
            "description": (
                "Elles étaient écrites en gras depuis les premières versions, "
                "au point que le clavier paraissait surchargé : à la taille où "
                "elles sont affichées, cette graisse noircissait un tiers de "
                "surface en plus et refermait les blancs du g et du m. Les "
                "lettres passent en graisse normale et respirent dans leur "
                "touche, qui reste détachée par son fond et son ombre."
            ),
            "image": "Screenshots/nouveaute_10.14.2_graisse_touches.png",
            "image_alt": (
                "Les trois rangées de lettres du clavier avant et après : "
                "en haut les lettres en gras jusqu'à la 10.14.1, en bas les "
                "mêmes lettres en graisse normale en 10.14.2."
            ),
        }
    ],
    "10.13.0": [
        {
            "emoji": "🌙",
            "title": "Le clavier passe en thème sombre",
            "description": (
                "Il restait blanc quelle que soit l'heure et quel que soit le "
                "réglage du téléphone : dans une conversation affichée en "
                "sombre, il éclairait l'écran à chaque saisie. Il suit "
                "désormais le mode sombre du système, ses touches de lettres "
                "passant du blanc à l'anthracite. Le vert, l'orange et le bleu "
                "de la charte, eux, ne changent pas."
            ),
        },
        {
            "emoji": "🎚️",
            "title": "Trois positions pour l'apparence du clavier",
            "description": (
                "Les réglages du clavier accueillent une carte « Apparence » : "
                "« Comme le téléphone », « Toujours clair » ou « Toujours "
                "sombre ». Les deux dernières existent parce que sur plusieurs "
                "surcouches, le réglage jour/nuit du téléphone ne descend pas "
                "jusqu'aux claviers tiers. Le choix s'applique dès le retour "
                "dans un champ de saisie."
            ),
        },
    ],
    "10.12.5": [
        {
            "emoji": "😀",
            "title": "La touche emoji réapparaît",
            "description": (
                "Elle affichait « … » à la place du visage souriant : agrandi "
                "avec les lettres, l'emoji réclamait plus de place que sa "
                "touche n'en offrait, et Android le remplaçait alors par des "
                "points de suspension. La taille des caractères tient "
                "désormais compte de la largeur des touches autant que de leur "
                "hauteur, y compris sur les téléphones à écran étroit."
            ),
        },
        {
            "emoji": "💬",
            "title": "Les mots proposés bien au milieu de leur puce",
            "description": (
                "Le mot suggéré paraissait posé trop bas dans sa pastille "
                "colorée : la ligne réservait au-dessus de lui la place "
                "d'accents que le français n'écrit jamais. Il retrouve le "
                "milieu de sa puce, et les lettres celui de leurs touches."
            ),
        },
    ],
    "10.12.4": [
        {
            "emoji": "🔎",
            "title": "Les mots proposés se lisent d'un coup d'œil",
            "description": (
                "Le texte des propositions était le plus petit du clavier, "
                "plus petit encore que les lettres des touches, alors que "
                "c'est précisément ce qu'on lit pour décider d'accepter un "
                "mot. Il grandit d'un quart et atteint la taille des lettres, "
                "sans que la pastille change de taille ni que les trois "
                "propositions cessent de tenir côte à côte."
            ),
        }
    ],
    "10.12.3": [
        {
            "emoji": "📐",
            "title": "La touche 123 retrouve sa place",
            "description": (
                "Elle flottait quelques pixels plus bas que ses voisines de la "
                "rangée du bas. Les touches s'alignaient sur la ligne "
                "d'écriture de leur libellé plutôt que sur leur cadre, si bien "
                "qu'un libellé écrit plus petit que les autres se retrouvait "
                "poussé vers le bas. Les neuf touches de la rangée sont de "
                "nouveau à la même hauteur."
            ),
        }
    ],
    "10.12.2": [
        {
            "emoji": "🔠",
            "title": "Des lettres à la taille de leurs touches",
            "description": (
                "Les lettres n'occupaient qu'un peu plus du tiers de la "
                "hauteur de leur touche, nettement moins que sur les autres "
                "claviers du téléphone. Elles gagnent 60 % de hauteur et "
                "remplissent maintenant leur touche, sans que le clavier "
                "prenne plus de place à l'écran et sans rien perdre en "
                "paysage, où les touches sont plus basses."
            ),
        }
    ],
    "10.12.1": [
        {
            "emoji": "⚙️",
            "title": "Les réglages du clavier ont leur propre écran",
            "description": (
                "Ils étaient arrivés dans une carte de l'onglet À Propos, une "
                "page de présentation où personne ne cherche un interrupteur. "
                "Un engrenage en haut à droite de l'application ouvre "
                "maintenant un écran « Réglages du clavier », comme partout "
                "ailleurs sur Android."
            ),
        },
        {
            "emoji": "🏷️",
            "title": "Les noms des onglets sont de nouveau lisibles",
            "description": (
                "La barre du haut n'identifiait ses sept destinations que par "
                "des emojis : les noms existaient, mais ils étaient rognés hors "
                "de la vue. On relit Démarrage, Kréyòl an mwen, Mots Mêlés, "
                "Mots Mélangés, Mo an Karénaj, Guide et À Propos sous chaque "
                "icône."
            ),
        },
    ],
    "10.11.7": [
        {
            "emoji": "🔔",
            "title": "Vibration et son repartent sur Samsung",
            "description": (
                "Sur One UI, le réglage « Vibration au toucher » ne gouverne "
                "que le clavier Samsung : depuis la 10.11.5, le clavier kréyòl "
                "restait donc muet, sans aucun moyen de le rallumer. Il reprend "
                "la main sur son retour de frappe, comme le font Gboard et "
                "SwiftKey."
            ),
        },
        {
            "emoji": "🎚️",
            "title": "Deux interrupteurs pour la vibration et le son",
            "description": (
                "Puisque le clavier ne suit plus le téléphone, il offre "
                "lui-même de quoi le faire taire : « Vibration à la frappe » et "
                "« Son de frappe », actifs par défaut, dans les réglages du "
                "clavier. Le choix s'applique dès le retour dans un champ de "
                "saisie."
            ),
        },
    ],
    "10.11.6": [
        {
            "emoji": "📳",
            "title": "La frappe se sent et s'entend partout",
            "description": (
                "Les touches vibraient, mais ni les propositions ni les emojis, "
                "et la barre d'espace ne faisait aucun bruit. Tout ce qui écrit "
                "du texte donne maintenant les deux retours, avec les vrais "
                "sons de clavier d'Android : un pour les lettres, un pour "
                "l'espace, un pour la suppression, un pour l'entrée."
            ),
        }
    ],
    "10.11.4": [
        {
            "emoji": "✍️",
            "title": "L'apostrophe retrouve une touche",
            "description": (
                "Écrire « l' », « d' » ou « qu' » demandait un appui long sur "
                "la virgule, et rien ne l'annonçait sur le clavier. Elle "
                "redevient une touche visible en troisième rangée, juste après "
                "le n, avec une cible de 5,7 mm. Elle reste aussi accessible "
                "sous la virgule."
            ),
        }
    ],
    "10.11.3": [
        {
            "emoji": "⌨️",
            "title": "Les lettres les plus tapées ont des touches plus larges",
            "description": (
                "La rangée du haut portait onze touches quand les autres en ont "
                "dix : les lettres les plus fréquentes du créole avaient les "
                "cibles les plus étroites. La touche ò la quitte, et a, o, i, "
                "t, u, p gagnent chacune un tiers de millimètre. ò reste en "
                "appui long sur o."
            ),
        }
    ],
    "10.11.2": [
        {
            "emoji": "♿",
            "title": "Les propositions ne se touchent plus",
            "description": (
                "Les deux rangées de suggestions n'étaient séparées que de 0,58 "
                "mm : un appui un demi-millimètre trop bas validait le mot "
                "français à la place du mot kréyòl visé. L'espace vide autour "
                "de chaque puce passe à 1,85 mm, pris sur leur hauteur, donc le "
                "clavier n'occupe pas un pixel de plus."
            ),
        },
        {
            "emoji": "📄",
            "title": "Une fiche pour les ergothérapeutes",
            "description": (
                "Le site accueille une fiche destinée aux professionnels qui "
                "accompagnent des personnes gênées dans le geste de la main : "
                "ce que le clavier fait gagner en nombre d'appuis, ce qu'il ne "
                "fait pas, ses limites connues, et un protocole pour compter "
                "les appuis en séance."
            ),
        },
    ],
    "10.11.1": [
        {
            "emoji": "📐",
            "title": "Le clavier ne prend plus tout l'écran en paysage",
            "description": (
                "Écran couché, il occupait 87 % de la hauteur et ne laissait "
                "voir que la moitié du champ de saisie, et sa rangée du bas "
                "était coupée en deux. Touches plus basses, marges resserrées "
                "et suggestions sur une seule rangée le ramènent à 58 %. Le "
                "portrait ne bouge pas d'un pixel."
            ),
        }
    ],
    "10.9.3": [
        {
            "emoji": "⇧",
            "title": "La touche majuscule montre où elle en est",
            "description": (
                "Ses trois états, minuscules, majuscule pour une lettre et "
                "verrouillage, étaient prévus mais ne se voyaient pas : elle "
                "restait blanche dans les trois cas. Son fond suit désormais "
                "l'état, et ses icônes ont été refaites autour d'une silhouette "
                "unique."
            ),
        }
    ],
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
                feature = {
                    "version": version,
                    "emoji": item["emoji"],
                    "title": item["title"],
                    "description": item["description"],
                    "release_url": RELEASE_URL.format(version=version),
                    "curated": True,
                }
                # Illustration facultative : la page ne l'affiche que si la
                # clé est présente, une nouveauté sans capture reste donc
                # rendue comme avant.
                for cle in ("image", "image_alt"):
                    if item.get(cle):
                        feature[cle] = item[cle]
                features.append(feature)
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
