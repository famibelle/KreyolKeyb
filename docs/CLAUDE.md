# Brand Assets

`docs/charte-graphique.html` is the brand reference page: logo rules, the three palettes and what each is for, typography, reusable components, editorial rules. It *describes* the values that live in `docs/assets/theme.css`, `res/values/colors.xml` and `docs/publicites.html` rather than redefining them, so those files stay the single source and the page follows when they change.

Note the three palettes are deliberately distinct and must not be mixed inside one support: communication (theme.css, muted, made for long text), product (colors.xml, vivid, a keyboard is read in a tenth of a second), advertising (publicites.html, hardcoded gradients that survive network compression).

`scripts/tag_assets.py` writes the Potomitan™ authorship and copyright into every visual's metadata (PNG iTXt + XMP, JPEG Exif/XMP/COM, SVG tags, PDF docinfo + XMP). It works at the chunk/segment level, so **nothing is re-encoded** and repeated runs never degrade a JPEG; it is idempotent, and `--check` reports files that lost their metadata. `docs/assets/ads/generate.py` calls it after every export, because Chrome writes bare PNGs. Third-party visuals (Google Play badge, stock imagery) are skipped by name: stamping our copyright on them would be false. PDF support needs `pikepdf`; without it the PDFs are skipped and the run says so. Note it takes no `--help`: an unknown flag just runs the full stamping pass (and, on the tracked PDFs, rewrites their XMP even with no visible change, so `git checkout` those back if you triggered it by accident).

## Page Nouveautés (`nouveautes.html` + `stats/exclusive_features.json`)

**Every nouveauté in `exclusive_features.json` carries a screenshot of the feature** (`image` + `image_alt`), on the model of the « Un onglet Dictionnaire » entry. The rule holds for any new entry: the capture goes in with the text, not later.

- The file lives in `docs/Screenshots/`, named `nouveaute_<version>_<sujet>.png`.
- Frame on the useful content: no status bar, no navigation bar, no phone chrome. The page's `.feature .shot` rule adds the border and rounded corners itself, and paints a white ground behind the image.
- Run `scripts/tag_assets.py` over the new file (Potomitan™ metadata) before committing.
- A nouveauté that does not photograph (a speed gain, a haptic return, a system setting) stays with no `image`: do not force a capture that shows nothing. A genuine before/after needs the old version rebuilt, as for `nouveaute_10.14.2_graisse_touches.png`; leave it unillustrated rather than faking the "before".
- `image_alt` describes what is on screen in French, factually, and names the kréyòl words visible so the entry still reads without the image.
