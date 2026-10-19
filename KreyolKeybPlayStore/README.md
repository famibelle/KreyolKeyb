# KreyolKeybPlayStore

Tout ce qui part vers la Play Console pour **Klavyé Kréyòl**
(`com.potomitan.kreyolkeyboard`), plus le flyer imprimable. Rien ici n'entre
dans l'APK : ce dossier ne déclenche aucun build.

Les visuels suivent l'organisation du dossier Play Store de LuxKeyb, reprise
le 19 octobre 2026 : chaque fichier porte le nom de l'emplacement de la
Console où il va.

```
texts/
  fichePlayStore.md       nom, descriptions, à coller dans la Console
  aso_pack_*.md           titres, canaux, liens UTM
graphics/
  build_graphics.py       fabrique tout ce qui suit, et le vérifie
  feature-graphic/
    icone-et-presentation/  l'icône et l'image de présentation (+ sa source HTML)
    captures/fr-FR/         les 8 captures téléphone de la Console
    hors-console/fr-FR/     un visuel par jeu, Sonjé et le mot suivant,
                            pour le site et les réseaux, jamais la Console
    captures-tablette/fr-FR/  six captures 1920x1080 en paysage, à envoyer
                            dans « tablette 7 pouces » et « 10 pouces »
    hors-console-tablette/fr-FR/  les visuels hors Console en paysage
  captures-emulateur-pixel9/     les écrans sources du téléphone, noms explicites
  captures-emulateur-tablette/   les écrans sources de la tablette
  app-icon/               l'icône 512 actuelle (logo Potomitan™)
  flyer-triptyque/        flyer A4 3 volets (HTML autonome + PDF)
```

Les fichiers posés directement dans `feature-graphic/` (bannières,
captures du Galaxy S21 Ultra) sont l'ancienne série, gardée pour mémoire.

## Refabriquer les images

```bash
cd graphics
python3 build_graphics.py              # tout, puis la vérification
python3 build_graphics.py shots        # les 8 captures téléphone
python3 build_graphics.py tablette     # les 6 captures tablette
python3 build_graphics.py check        # vérifie sans rien refabriquer
cd ../.. && python3 scripts/tag_assets.py KreyolKeybPlayStore/graphics
```

Demande `google-chrome` et ImageMagick (`convert`). Le docstring de
`build_graphics.py` dit comment les captures sources ont été prises
(émulateurs, carnet de démonstration, mode démo de la barre d'état) : après
un changement d'interface, les recapturer au même endroit sous le même nom,
puis relancer le script.

Les légendes sont en français, dans la palette de communication et en
Fredoka (`docs/charte-graphique.html`). Elles ne rédigent pas de kréyòl : le
kréyòl visible vient des captures ou de la signature produit de la charte.
