# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What This Project Is

**Klavyé Kréyòl Karukera** — an intelligent keyboard for Guadeloupean Creole (kréyòl Guadeloupéen). It is an Android IME (Input Method Editor) with an iOS port in progress. The keyboard provides bilingual suggestions (Kreyòl + French) powered by a curated dictionary and n-gram model built from Creole literary texts.

## Android Build Commands

All Gradle commands run from `android_keyboard/`.

**Local build gotchas:** AGP requires Java 17 (`export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64`), and the checked-in `gradlew` script is corrupted (missing `eval`, passes quoted args to Gradle). Work around it with:
```bash
$JAVA_HOME/bin/java -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain <task>
```
CI is unaffected (it installs Gradle 8.7 directly).

Release signing reads `KEYSTORE_FILE`, `STORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` in that order of precedence: `android_keyboard/keystore.properties` (local, gitignored), then Gradle properties, then environment variables (what CI uses). Falls back to debug signing if any of the four is missing. See `keystore.properties.example` for the format.

**Never put signing credentials in `gradle.properties`** — that file is tracked. Doing so leaked the release passwords publicly between 2025-10-08 and 2025-10-23 (`22001c93` → `563e31aa`); the branches still carrying them were deleted from `origin` on 2026-08-15, with local backups under `refs/backup/2026-08-15-fuite-secret/`. `keystore.properties` exists so there is no tracked file where a credential can plausibly be written.

**versionCode** format: `60501` = version `6.5.1` (major × 10000 + minor × 100 + patch).

## Dictionary / Data Pipeline

The JSON assets in `android_keyboard/app/src/main/assets/` are the **source of truth** used by both Android and iOS:
- `creole_dict.json` — `[word, frequency]` list (~5300 words)
- `creole_ngrams.json` — n-gram context model, ~8850 keys. Two key families in one flat object: one word (`"ka"`, from bigrams) and two words separated by a space (`"an ka"`, from trigrams). See `android_keyboard/NGRAMS.md`
- `french_simple_dict.json` — French fallback dictionary, only ~660 words. This thinness constrains both the bilingual suggestions and the spell checker (see below)
- `creole_cloze.json` — the ~390 fill-in-the-blank questions of the *Fraz a twou* game. Not produced by `KreyolComplet.py`; see `generate_cloze.py` below
- `creole_translations.json` — French gloss of ~1145 Creole forms, covering 622 of the words in `creole_dict.json` and 42% of corpus occurrences. Measure coverage with accent folding, as `TranslationDictionary` looks up: on exact keys it reads 513/36% and misses the 109 forms the fold catches. Not produced by `KreyolComplet.py`; see `generate_translations.py` below and `Dictionnaires/GLOSES.md`

To regenerate from the Hugging Face dataset `POTOMITAN/PawolKreyol-gfc` (requires `HF_TOKEN`):
```bash
cd Dictionnaires
pip install datasets huggingface_hub
python KreyolComplet.py          # Fetches HF data, rebuilds dict + n-grams, backs up old files
```

**Never run this without a working `HF_TOKEN`.** On download failure the script silently falls back to `PawolKreyol/Textes_kreyol.json`, a local snapshot that may lag far behind the dataset, and rebuilds the dictionary from it.

`python KreyolComplet.py --rapport-seul` replays the same computation but writes **only** `RAPPORT_LINGUISTIQUE.md`: `sauvegarder_donnees()` is skipped, so the dictionaries shipped in the APK are untouched. Unlike the full pipeline this mode *refuses* the local-snapshot fallback (it checks `source_chargement`), because a report regenerated from stale data would still be stamped with today's date.

`Dictionnaires/generate_translations.py` builds `creole_translations.json`, the French gloss shown by the Dictionnaire tab and the four games. Like `generate_cloze.py` it **consumes** the shipped dictionary, so it must run **after** `KreyolComplet.py`. It needs no `HF_TOKEN`: its two sources are public and both CC BY-SA 4.0, [Kreyolopedia](https://kreyolopedia.org)'s JSON export and the French Wiktionary's MediaWiki API (which returns **403 without a `User-Agent` header**). `gloses_source.py` holds the fetching, caching under `Dictionnaires/gloses_data/`, and the wikitext cleaning.

Two thirds of the table come from a path that is easy to overlook: 884 *French* Wiktionary pages cite a Guadeloupean form in their translation section, and inverting that pair is the only way to reach `moun`, which has no page of its own. Dropping that pass takes coverage from 622 keyboard words to 218. Conversely the ceiling is real and will not move by editing the script: `sé`, the third most frequent Creole word, is glossed by neither source, and the reference dictionaries (Ludwig, Poullet, Telchid; Orphie) are under copyright. `Dictionnaires/GLOSES.md` carries the licence obligations, the three source codes (`K`/`W`/`T`) and the measured coverage.

`Dictionnaires/generate_cloze.py` builds `creole_cloze.json` for the *Fraz a twou* game. It **consumes** the shipped dictionary and n-grams rather than rebuilding anything, so it must run **after** `KreyolComplet.py` — running it before leaves the distractors drawn from a model that no longer matches the corpus, and `ClozeAssetTest` then fails on answers that are no longer in the dictionary. It reuses `KreyolPipelineUnique.charger_textes_kreyol()` for the corpus, and `--strict` refuses the local-snapshot fallback (CI deliberately runs it *without* `--strict`, so an HF outage degrades the same way the dictionary already does).

Two thresholds in it carry the game's quality, and both are proxies for a grammar the project does not have. Creole capitalizes nothing but proper nouns, so unlike the Luxembourgish fork there is no case signal for "content word": the masked word is picked on frequency instead (5 ≤ freq ≤ 150, ≥ 4 letters), which costs good answers like `moun` (267) and `pran` (152) along with the function words it is aimed at. Conversely a mid-sentence capital *is* a reliable proper-noun signal here, but it must be combined with the overall capitalization ratio — `viktò` is capitalized 95 times out of 95, yet 83 of those open a line of dialogue. Requiring the same final letter for distractors was measured and dropped: it takes the delivery from 510 questions to 178, for a language that barely inflects.

`docs/scripts/generate_corpus_stats.py` computes the figures behind the `docs/corpus.html` page into `docs/assets/corpus_stats.json`. It reads the public parquet export through the HF datasets-server (no `HF_TOKEN`, no `datasets` library) and deliberately mirrors `KreyolComplet.py`'s regex and n-gram thresholds, so its totals stay comparable to the shipped assets. It also stores the dataset's commit SHAs, which is what `rapport-corpus.yml` diffs to decide whether anything needs rebuilding.

Corpus word counts **replace** stored frequencies rather than adding to them, so two consecutive runs produce the same dictionary. Words absent from the corpus (hand-curated additions) are preserved, their frequency rescaled to the current corpus scale.

## Android Architecture

### IME Entry Point

`KreyolInputMethodServiceRefactored.kt` is the **only** IME service. The legacy monolithic `KreyolInputMethodService.kt` and the unused `TestInputMethodService.kt` were deleted in 10.4.2: neither was declared in the manifest, so both were dead code that still shipped in the APK and made features look implemented when they were not (`onUpdateSelection()` lived only there while the active service lacked it). Recover them from git history if ever needed.

The refactored IME coordinates four components (`KeyboardLayoutManager`, `SuggestionEngine`, `AccentHandler`, `InputProcessor`) via listener interfaces.

### Space Bar Gestures (`KeyboardLayoutManager.setupSpaceLongPress()`)

Three gestures share one `OnTouchListener`: tap (space), one-second long press (IME picker), and, since 14.0.0, a horizontal drag that moves the caret one character per `SPACE_CURSOR_STEP_DP` (10 dp). Past `scaledTouchSlop` the long-press timer is cancelled and the release no longer inserts a space.

Three things there are load-bearing and easy to undo:

- **Every branch returns `false`.** That is what makes `View.onTouchEvent` consume the DOWN (the key is clickable), which is what keeps MOVE and UP coming to this listener after the finger has left the key. Returning `true` on DOWN would confine the gesture to the width of the space bar instead of the width of the screen. The same mechanism is why the space key has no `OnClickListener` (two of them insert a double space).
- **The anchor advances by whole steps, not to the finger's position.** Rounding never accumulates, and an out-and-back finger returns the caret exactly where it started.
- **The caret moves via `sendDownUpKeyEvents(DPAD_LEFT/RIGHT)`, not `setSelection()`.** `setSelection()` needs an absolute position, which the IME only learns from `onUpdateSelection()`, i.e. late; during a fast drag it would compute from a stale value and the caret would jump. This is also what AOSP's keyboard does for this gesture.

`onUpdateSelection()` fires per character during the drag, so the service debounces: `glissementCurseur` defers `syncWordWithCursor()` until `DELAI_SYNC_CURSEUR` (120 ms) after the last movement. Without it the suggestion bar recomputes on every character crossed.

### Emoji Recents (`EmojiRecents.kt`)

Since 15.0.0 the emoji panel leads with a « Récents » category built from a 30-entry MRU list in its own `SharedPreferences` file (`kreyol_emoji_prefs`). 30 is `GRID_COLUMNS` × `VISIBLE_ROWS`, i.e. exactly the visible page: a longer list would make the one category that exists to be seen at a glance start scrolling.

This is the one place the project stores something the user typed after 10.6.0 deleted the personal dictionary, so the boundary is deliberate and worth keeping: entries are identifiers drawn from a closed, shipped set (`emoji_data.json`), never free text; there is no timestamp and no counter; and `setEnregistrementAutorise()` is refreshed from `isSensitiveField()` on every `onStartInputView()`, exactly like the gamification word counters. « Vider les emojis récents » in `KeyboardSettingsActivity` clears it.

Two implementation notes:

- **The category list is frozen at construction**, not updated live. `createEmojiLayout()` builds a new `EmojiPickerView` on every switch into emoji mode, so it is fresh at each opening; reordering the grid under the finger of someone picking three emoji in a row would make them miss the third.
- **A skin tone picked by long press does not go through `onEmojiSelected`.** It is committed by the service's `onAccentSelected()`, which is why that method records it too. Without it only the panel's default variant would ever reach the recents.

### Keyboard Theme (`KeyboardTheme.kt`)

One palette object, resolved once per focus, that the four painted surfaces read: the
keys, the suggestion bar, the long-press popup and the emoji panel. The three vivid
product colours (green for Enter/mode keys, orange for punctuation, blue for the
space bar) are **identical in both themes** and defined once; only the white letter
keys and everything derived from them (ink, border, keyboard background, popup) flip
to anthracite. The light theme is pixel-identical to what shipped before the theme.

Two traps, both ported from the same work on the LuxKeyb fork:

- `KeyboardTheme.refresh()` deliberately returns nothing. The settings screen shares
  the IME's process and refreshes the global palette at click time, so a "did it
  change?" boolean read by the service afterwards is always false. The service
  instead compares `palette()` against `paletteDeLaVue`, the palette its cached input
  view was **built** with, and calls `setInputView(onCreateInputView())` when they differ.
- `InputMethodService` caches the input view between fields, and colours are frozen
  into the widgets at construction. Nothing repaints on its own: the rebuild in
  `onStartInputView()` is what makes both the settings choice and the system night
  toggle take effect.

The mode (`systeme` / `clair` / `sombre`) lives in `KeyboardPreferences`, next to the
haptic and sound switches, and for the same reason: on several OEM skins the phone's
day/night setting does not reach third-party keyboards.

Since 16.0.0 the palette also carries `filetSuggestions` and `ombreSuggestions`, the two
colours of the drop shadow the suggestion bar casts onto the keyboard. It is painted by
`OmbreSuggestions`, a hand-written `Drawable` set as the background of the service's
`keyboardContainer`. Three shorter routes were rejected and the reasons still hold:
`View.setElevation()` draws a black shadow that is invisible on the dark theme's
`#131313` and is only tintable from API 28 (minSdk is 21); a dedicated view would cost
its height to the budget `computeAvailableRowsHeight()` counts to the pixel; and
`LayerDrawable` can only confine a layer to the top of its bounds with `setLayerHeight`,
API 23. The 5 dp band (1 dp hairline + 4 dp gradient) sits inside the padding the
keyboard already reserves above its first row, so it costs no vertical space. Measured on
the emulator: light `#FFFFFF` → `#D2D2D2` → `#F5F5F5`, dark `#202020` → `#090909` →
`#131313`, monotone in both, hairline darkest.

### Suggestion Pipeline (`SuggestionEngine.kt`)

The ranking stages live in `SuggestionEngine.kt` and read top to bottom; two thresholds in them are deliberate rather than incidental: the French fallback only kicks in at ≥ 3 characters typed, and 3 suggestions are displayed out of 5 scored internally (3 Kreyòl + 2 French slots).

### Spell Checker (`KreyolSpellCheckerService.kt`)

A system `SpellCheckerService`, separate from the IME: any app's text field can query it, which is what stops Creole words from being underlined as typos. It reuses `SuggestionEngine` (`isKnownWord()` + `getSpellingSuggestions()`) rather than loading its own dictionaries.

Two things are easy to break here, both of which silently disable the service with no error anywhere:

- **Locale subtypes** (`res/xml/kreyol_spellchecker.xml`). Android picks a spell checker by matching a subtype against the *text field's* locale. Declaring only Creole locales means no match and no session is ever created. `fr` must stay declared. Diagnose with `adb shell dumpsys textservices`: empty `Spell Checker Bind Groups` means the service is selected but never instantiated.
- **`setCookieAndSequence()`** on every returned `SuggestionsInfo`. Without it the client cannot map a verdict back to the word it analysed, so nothing is ever underlined even though the service runs and logs correctly.

Because `fr` is declared, this service replaces the system one for **all** French text, on a ~660-word French dictionary. It therefore only flags a word when a plausible correction exists; widening `french_simple_dict.json` is what would let that restriction be lifted.

Android allows a single spell checker system-wide and no app can select itself. The user must pick it in **Settings › System › Keyboard › Spell checker** (under *Keyboard*, not *Languages*), so the app cannot rely on it being active. Three things verified on an emulator by walking the real UI:

- Android shows a deterrent confirmation dialog first, warning that the spell checker "can collect all the text you type, including personal data like passwords and credit card numbers". Any onboarding that guides users here has to prepare them for it.
- A master switch, *Use spell checker*, sits above the picker. When it is off, nothing is checked at all and the chosen service is never called, with no other symptom.
- **Reinstalling the app leaves the system binding stale** (`dumpsys textservices` shows the bind group with `mSpellChecker=null`), and the service stays silent until a reboot. Worth re-testing after a Play Store update before concluding the checker is broken.

### Gamification (`gamification/` package)

- `CreoleDictionaryWithUsage` — plain class over a `JSONObject` persisted to `filesDir`, tracks per-word usage counts. `getWordUsageCount()`/`incrementWordUsage()` are `synchronized`: the suggestion engine reads them from a background thread while the IME writes on the main thread
- `WordUsageStats` — per-word stats with 7 mastery levels: Pipirit → Potomitan
- `VocabularyStatsActivity` — displays dashboard with progress per level
- `WordCommitListener` interface — `KreyolInputMethodServiceRefactored` implements this to log each committed word

### Tabs (`SettingsActivity`)

Four tabs since 12.0.0 — Démarrage, Kréyòl an mwen, Jé, Dictionnaire — down from seven in 11.0.0. `REAL_COUNT` in `SettingsPagerAdapter` is the single source of truth for the count; the tab bar and the adapter must stay in step. Each tab now gets a quarter of the width; verified at 360 dp that all four labels still fit on one line, and that « Kréyòl an mwen » wraps onto the second line `maxLines = 2` allows (never clipped) once the system font scale is raised. The pager is *cyclic*: it repeats the tabs over a huge virtual range, so an absolute `currentItem` is meaningless. Navigate with `allerAOnglet()`, which also refuses to animate a jump of more than one tab (ViewPager2 stops midway, leaving the bar showing one tab while the content is another).

The four games sit behind the `Jé` tab in `GamesFragment`, which swaps the chosen game into its own `FrameLayout` via `childFragmentManager`. **No nested pager**: the word-search grid is dragged with a finger, and a second `ViewPager2` would fight it for every horizontal gesture. Back-to-the-menu is an `OnBackPressedCallback` enabled only while a game is open, so the Back button still quits the app everywhere else.

Guide and À Propos are no longer tabs: `SheetFragment` opens the existing fragments full screen from the foot of the Démarrage tab.

The first-run funnel keys off `funnel_keyboard_enabled`, which is timestamped once and never cleared — the tab bar comes back on first *activation* and never hides again, even if a system update later deselects the keyboard.

### Games (`wordscramble/`, `wordsearch/`, `mokarenaj/`, `cloze/` packages)

Four vocabulary mini-games accessible from the `Jé` tab (`mokarenaj` is a Creole Wordle). Three of them pull words directly from the loaded dictionary, with a hard-coded fallback list.

Since 12.0.0 all four draw **only from words that carry a French gloss**, through `TranslationDictionary.filtrerMotsTraduits()` — a game that cannot say what its word means teaches spelling and nothing else. The filter returns the unfiltered list below 50 words, so a missing translation asset leaves the games playable and merely mute. It applies to the *draw* only: `MoKarenajData.isValidWord()` must keep accepting any dictionary word, glossed or not, or the player's own guesses start getting rejected. `cloze` (*Fraz a twou*) is the exception on both counts: it reads `creole_cloze.json`, and it has **no fallback** — a missing asset shows a screen saying so, because ten hand-written sentences would make a broken delivery perfectly playable.

## iOS Port

The iOS Swift/SwiftUI port lives on the `ios/port` branch, not on `main`. Load the `ios-port` skill before any iOS work.

## CI/CD

- **`build-apk.yml`** — triggers on push/PR to `main` when `android_keyboard/**` or `.github/workflows/**` change, or on `v*` tags. It builds the debug/release APK **and AAB**. Its paths filter negates `!Dictionnaires/RAPPORT_LINGUISTIQUE.md`, so refreshing that report can never start a build: the report is an analysis of the corpus, nothing in it ships in the APK or AAB. Runs the Python dictionary pipeline first (needs `HF_TOKEN` secret), then `generate_cloze.py` and `generate_translations.py` in that order (both consume what the pipeline just wrote), then builds and signs the APK. Each generated asset has a volume guard in the *Verify Generated Assets* step — 250 cloze questions, 800 glossed forms — plus an attribution check, because a truncated asset degrades silently at runtime rather than failing the build. Creates a GitHub Release on tags. Its paths filter also covers `Dictionnaires/**`; note the workflow regenerates the dictionary on every build **without committing it back**, so the shipped APK is built from a freshly regenerated dictionary rather than the committed one.
- **`rapport-corpus.yml`** — triggers on push to `main` touching `docs/**` (plus manual dispatch with a `forcer` input). It first compares the dataset's `main` and `refs/convert/parquet` commit SHAs against the ones stored in `docs/assets/corpus_stats.json`; if neither moved, the job stops there and writes nothing. When the corpus did move it runs `KreyolComplet.py --rapport-seul` and `docs/scripts/generate_corpus_stats.py`, then commits `RAPPORT_LINGUISTIQUE.md` + `corpus_stats.json` with a skip-CI marker.

**This refresh must never produce an APK or AAB build**, and three independent guards enforce that: `build-apk.yml` negates the report file in its paths filter, this workflow negates `docs/assets/corpus_stats.json` in its own (otherwise its commit would retrigger it in a loop), and the commit message carries the skip marker. GitHub Pages deployment is *not* affected by that marker (verified on a real run), so the page still picks up the new numbers.

Never write the literal skip-CI marker into a hand-written commit message, even when describing this mechanism: GitHub scans the whole message and will silently skip every workflow for that push.
- **`ios-build.yml`** (on `ios/port` branch only) — triggers on push to `ios/port` when `ios/` changes. Runs on `macos-14` (Xcode 15, Apple Silicon). Requires secrets: `DIST_CERT_BASE64`, `DIST_CERT_PASSWORD`, `PROVISIONING_PROFILE_BASE64`, `DEVELOPMENT_TEAM`, `APPLE_ID`, `APP_SPECIFIC_PASSWORD`.

## Brand Assets

Brand rules (the three palettes, the charte page, `scripts/tag_assets.py`) live in `docs/CLAUDE.md`, loaded when working under `docs/`.

## Legacy / Auxiliary Directories

- `clavier_creole/` — abandoned Flutter prototype (`lib/main.dart`). Do not develop here, **but do not assume its `assets/` are dead either**: `KreyolComplet.py` reads its previous dictionary from `clavier_creole/assets/` and writes the regenerated files to both there and `android_keyboard/`. The two copies must stay in sync.
- `PawolKreyol/` — raw Creole corpus texts (`Textes_kreyol.json`/`.xlsx`) feeding the HF dataset.
- `docs/` — GitHub Pages site (privacy policy, beta onboarding, feedback form).
- `KreyolKeybPlayStore/`, `Screenshots/`, `Logos/` — store listing and branding assets.
