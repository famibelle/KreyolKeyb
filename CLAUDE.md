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
- `creole_exemples.json` — 479 Creole words, each with one to three corpus sentences that show the word at work, plus the credit of the author or the work. Read only by the carnet's cards. Not produced by `KreyolComplet.py`; see `generate_exemples.py` below
- `creole_crossword.json` : ~180 numbered crossword grids for the *Mokwaré* game (60 per difficulty), each grid a word list with positions plus the French clue per word. Not produced by `KreyolComplet.py`; see `generate_crossword.py` below

To regenerate from the Hugging Face dataset `POTOMITAN/PawolKreyol-gfc` (requires `HF_TOKEN`):
```bash
cd Dictionnaires
pip install datasets huggingface_hub
python KreyolComplet.py          # Fetches HF data, rebuilds dict + n-grams, backs up old files
```

**Never run this without a working `HF_TOKEN`.** On download failure the script silently falls back to `PawolKreyol/Textes_kreyol.json`, a local snapshot that may lag far behind the dataset, and rebuilds the dictionary from it.

`python KreyolComplet.py --rapport-seul` replays the same computation but writes **only** `RAPPORT_LINGUISTIQUE.md`: `sauvegarder_donnees()` is skipped, so the dictionaries shipped in the APK are untouched. Unlike the full pipeline this mode *refuses* the local-snapshot fallback (it checks `source_chargement`), because a report regenerated from stale data would still be stamped with today's date.

Outside `--strict`, a source that is unreachable **and** has never been cached is skipped rather than fatal. CI runners start with an empty disk, so there is never a cache there: a Kreyolopedia timeout failed the v16.0.0 tag build on 2026-09-05 over a source contributing 21 of 1 145 forms. What the chain must refuse is a *truncated asset*, not a failed request, and volume decides that better than a `try` does: without Kreyolopedia the table drops to 1 136 forms and the build stays green; without the Wiktionnaire it drops to 21, under the 800 floor of *Verify Generated Assets*, and the build stops.

`Dictionnaires/generate_translations.py` builds `creole_translations.json`, the French gloss shown by the Dictionnaire tab and the four games. Like `generate_cloze.py` it **consumes** the shipped dictionary, so it must run **after** `KreyolComplet.py`. It needs no `HF_TOKEN`: its two sources are public and both CC BY-SA 4.0, [Kreyolopedia](https://kreyolopedia.org)'s JSON export and the French Wiktionary's MediaWiki API (which returns **403 without a `User-Agent` header**). `gloses_source.py` holds the fetching, caching under `Dictionnaires/gloses_data/`, and the wikitext cleaning.

Two thirds of the table come from a path that is easy to overlook: 884 *French* Wiktionary pages cite a Guadeloupean form in their translation section, and inverting that pair is the only way to reach `moun`, which has no page of its own. Dropping that pass takes coverage from 622 keyboard words to 218. Conversely the ceiling is real and will not move by editing the script: `sé`, the third most frequent Creole word, is glossed by neither source, and the reference dictionaries (Ludwig, Poullet, Telchid; Orphie) are under copyright. `Dictionnaires/GLOSES.md` carries the licence obligations, the three source codes (`K`/`W`/`T`) and the measured coverage.

`Dictionnaires/generate_cloze.py` builds `creole_cloze.json` for the *Fraz a twou* game. It **consumes** the shipped dictionary and n-grams rather than rebuilding anything, so it must run **after** `KreyolComplet.py` — running it before leaves the distractors drawn from a model that no longer matches the corpus, and `ClozeAssetTest` then fails on answers that are no longer in the dictionary. It reuses `KreyolPipelineUnique.charger_textes_kreyol()` for the corpus, and `--strict` refuses the local-snapshot fallback (CI deliberately runs it *without* `--strict`, so an HF outage degrades the same way the dictionary already does).

Two thresholds in it carry the game's quality, and both are proxies for a grammar the project does not have. Creole capitalizes nothing but proper nouns, so unlike the Luxembourgish fork there is no case signal for "content word": the masked word is picked on frequency instead (5 ≤ freq ≤ 150, ≥ 4 letters), which costs good answers like `moun` (267) and `pran` (152) along with the function words it is aimed at. Conversely a mid-sentence capital *is* a reliable proper-noun signal here, but it must be combined with the overall capitalization ratio — `viktò` is capitalized 95 times out of 95, yet 83 of those open a line of dialogue. Requiring the same final letter for distractors was measured and dropped: it takes the delivery from 510 questions to 178, for a language that barely inflects.

`Dictionnaires/generate_crossword.py` builds `creole_crossword.json` for the *Mokwaré* game. Like `generate_cloze.py` it **consumes** shipped assets rather than rebuilding anything (the frequency dictionary for the words, `creole_translations.json` for the clues), so it must run **after** both `KreyolComplet.py` and `generate_translations.py`. Unlike `generate_cloze.py` it never touches the HF corpus: it runs offline in a few seconds. `--strict` fails the run below 90 grids.

The port from the Luxembourgish fork drops two things Creole does not have. There is no noun-capitalization to preserve (the shipped dictionary is all lowercase, so `KreyolComplet`'s `_classe_de_casse` has no analogue to import) and no *mots écartés* list (not ported, see below). Proper nouns are kept out by the gloss requirement alone (neither Kreyolopedia nor the Wiktionnaire glosses `Gwadloup`), backed by a short hand-kept `NOMS_PROPRES` set. The real constraint is the pool: only ~490 dictionary forms carry a usable gloss and spell with the pad's 29 letters (A-Z plus É È Ò), and above frequency 150 just four remain. So the three difficulties differ by **grid size and word count**, not word rarity, drawing from almost the same pool; only *Facile* raises a frequency floor. The grid-quality invariant is the fork's: every run of two or more letters, in either direction, must be a placed word, checked by `valider()` in the script and re-checked grid by grid by `CrosswordAssetTest`.

`Dictionnaires/generate_chassecroise.py` builds `creole_chassecroise.json` for the *Mo an plas* game, and imports `generate_crossword.py`'s placement engine wholesale rather than copying it, so it must run **after** it. It consumes the same two assets and runs offline. `--strict` fails below half the 180 grids it targets.

Two things separate it from the crossword it is built on. The words are **shown**, so frequency no longer makes difficulty: there is no frequency floor at all (one would cost the pool — 78 words survive frequency 20, against 471 in total), and the three levels are graded on `collisions`, the share of words sharing their length with another in the same grid, targeted at 25/55/85 % and delivered at 34/54/80 %. And the grid must have **exactly one solution**, checked by `solution_unique()`: if two same-length words can swap without contradicting a crossing, the game refuses a correct answer and nothing else would signal it.

The Creole pool is 471 words against the Luxembourgish fork's 6 001, and that ratio is what every setting below it reflects — grids of 8/9/11 holding 6/8/10 words (Mokwaré's geometry, not the fork's 9/10/11 and 7/10/13, which starves on the 8 Creole words of ten letters or more), 60 grids per level rather than 100, and no second reuse cap on top of Mokwaré's. Widening the alphabet is not the lever it looks like: the 29-letter constraint exists for Mokwaré's input pad, which this game does not have, but lifting it recovers only 7 glossed forms (six hyphenated compounds, `jandàm`), none of which would fit a grid. The 3 574 forms rejected for want of a gloss are the whole of the ceiling.

One crossword filter is dropped rather than relaxed: a gloss that repeats the word is no longer a giveaway, since the gloss is the **reward** shown after the word locks. Measured, it rejects **zero** of the 471 forms — the glosses are French and the words Creole — so it goes because it has stopped meaning anything, not because it cost something.

`Dictionnaires/generate_exemples.py` builds `creole_exemples.json`, the sentence on the back of a carnet card. Like the two above it **consumes** shipped assets (the frequency dictionary and the gloss table), so it must run **after** `KreyolComplet.py` and `generate_translations.py`. It reads the HF corpus through `KreyolPipelineUnique.charger_textes_kreyol()` and `--strict` refuses the local-snapshot fallback, exactly like `generate_cloze.py`.

Where the Luxembourgish carnet puts a sentence written by a lexicographer (the LOD gives one per article), Creole has no such source, so the sentence is **an extract of an author's text** from the corpus. That is better in substance and dearer in obligation: each sentence ships with its full bibliographic reference (`src`) and a short display credit (`crd`, capped at 34 characters), and the card shows the credit. Same rule as *Fraz a twou*, which has displayed corpus sentences since 11.0.0.

Two thresholds are measured, not chosen. The sentence band is **4 to 14 words**: 3 to 18 covers 524 words but does not fit the card, 6 to 16 (the cloze band) covers 483, and 4 to 14 covers 500 on the local snapshot. The lower bound is below the cloze's because the sentence does not have to *designate* an answer here, only to show the word at work, and a four-word proverb does that well. The generator also refuses an occurrence capitalized mid-sentence: Creole capitalizes only proper nouns, so `Viktò` in a line of dialogue does not illustrate the card's `viktò`. Delivered: 479 of the 622 glossed words illustrated, 1 149 sentences, 188 KB. The other 143 keep their gloss alone.

`docs/scripts/generate_corpus_stats.py` computes the figures behind the `docs/corpus.html` page into `docs/assets/corpus_stats.json`. It reads the public parquet export through the HF datasets-server (no `HF_TOKEN`, no `datasets` library) and deliberately mirrors `KreyolComplet.py`'s regex and n-gram thresholds, so its totals stay comparable to the shipped assets. It also stores the dataset's commit SHAs, which is what `rapport-corpus.yml` diffs to decide whether anything needs rebuilding.

Corpus word counts **replace** stored frequencies rather than adding to them, so two consecutive runs produce the same dictionary. Words absent from the corpus (hand-curated additions) are preserved, their frequency rescaled to the current corpus scale.

## Android Architecture

### IME Entry Point

`KreyolInputMethodServiceRefactored.kt` is the **only** IME service. The legacy monolithic `KreyolInputMethodService.kt` and the unused `TestInputMethodService.kt` were deleted in 10.4.2: neither was declared in the manifest, so both were dead code that still shipped in the APK and made features look implemented when they were not (`onUpdateSelection()` lived only there while the active service lacked it). Recover them from git history if ever needed.

The refactored IME coordinates four components (`KeyboardLayoutManager`, `SuggestionEngine`, `AccentHandler`, `InputProcessor`) via listener interfaces.

### Keyboard Panels / Mode Switching (`KeyboardLayoutManager`)

Since 18.0.0 `createKeyboardLayout()` builds a `FrameLayout` holding the alphabetic **and** numeric panels at once, and `applyMode()` toggles which is shown. Before, every `123` / `ABC` / emoji press ran `refreshKeyboardLayout()` which threw the whole view tree away and rebuilt ~34 fresh `Button`s on the main thread: measured at 3 to 5 dropped frames per press on a Galaxy A21s (`android_keyboard/PERF_CLAVIER.md`). The service's `refreshKeyboardLayout()` and the settings-screen demo now just call `applyMode()`.

Three things there are load-bearing:

- **The two panels hide as `INVISIBLE`, not `GONE`.** They are the same height (four rows), so nothing reflows, and the hidden panel keeps its display list recorded: the switch is then a redraw, not a re-layout plus a re-record. `GONE` would drop the display list and cost most of what the rebuild cost.
- **The emoji panel is the exception: built on entry, removed on exit.** Its height differs, and `EmojiPickerView` freezes its « Récents » category at construction, so rebuilding it each time is what keeps that category current. Its control-row keys are tracked in `emojiPanelButtons` and pulled out of `keyboardButtons` on teardown, or the leak below comes back one notch at a time.
- **`keyboardButtons` is cleared at the top of `createKeyboardLayout()`.** It used to be cleared only in `cleanup()` (i.e. `onDestroy()`, ~never for an IME), so it grew ~13 to 34 stale `View` refs per mode switch for the life of the process, and `updateKeyboardDisplay()` walked the lot on every shift.

`onStartInputView()` calls `applyMode()` after `forceAlphabeticMode()`: resetting the mode flags no longer changes what is visible on its own.

`applyGuadeloupeStyleToView()` only forces `LAYER_TYPE_SOFTWARE` per key when `forcerRenduLogiciel` is true (Honor / Huawei ROMs, the `caa64aca` GPU bug). Elsewhere it was pure cost, re-rasterising every key on each redraw; the text `setShadowLayer` shadow stays everywhere.

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

### Suggestion Bar Relief (`CuvetteSuggestions.kt`)

Since 17.0.0 the suggestion bar is a tray **carved into** the keyboard. 16.0.0 had it the other way round, casting a drop shadow onto the keys; the two readings are exclusive, so `OmbreSuggestions` was replaced rather than extended, and what was the drop shadow became the tray's lit lower lip.

A recess needs all three signals, and with two of them the eye reads a gradient instead of a volume: the tray floor is **darker** than `fondClavier` (this inverted `fondSuggestions`, which had been lighter than the keys since the theme shipped), an inner shadow runs along the top edge, and a lit hairline along the bottom. The palette carries the last two as `ombreCuvette` and `lisereCuvette`.

Three implementation points are load-bearing:

- **The rim is the side inset.** A recess with no visible ledge has nothing casting the shadow, so the service gives `suggestionsContainer` an 8 dp horizontal margin and `fondClavier` shows through on either side. The margin is horizontal only: a top or bottom margin would add height that `computeAvailableRowsHeight()` does not know about, and the last key row would be clipped by exactly that much.
- **Only the bottom corners are rounded.** The top edge is the edge of the IME window; rounding it there would show the app through the corners. A tray whose upper wall runs off-screen is also what the geometry actually describes.
- **No `clipPath`.** The lip follows the bottom corners' curve, which a clip would give with a jagged edge. Two filled paths do it antialiased: the whole tray in the lip colour, then the same path offset up by the lip's thickness in the floor colour.

Measured on the emulator, monotone in both themes: light floor `#ECECEC` in a `#F5F5F5` ground, shadow from `#CECECE` over 11 px, lip `#FDFDFD`; dark floor `#0C0C0C` in `#131313`, shadow from `#070707`, lip `#363636`.

**Chip colour encodes the language, and only the language.** 17.0.0 moved it onto rank instead, from a correct observation and a wrong conclusion: the bar did state the language three times (row, KR/FR label, chip colour) and the rank nowhere, but rank was already carried by **position**, left to right, as on every suggestion bar. Colour was spent restating what order said, and stopped saying the one thing nothing else said as well. 19.0.0 gave it back to every chip in a row.

Two reasons the language belongs on the fill rather than on the label alone: a row is read as a block, three green pills saying "this is Kreyòl" without being read; and colour works in peripheral vision while the eye stays on the text being typed, where a 10 sp KR/FR label only registers if you look for it. Rank is left to position alone, which is what 17.0.0 should have concluded.

This makes two intermediate states dead ends, worth knowing before reopening the question: 17.0.0 left ranks 2+ with no background at all, which read the rank fine but stopped saying a word was tappable and lost the tap's visual confirmation; 17.0.1 fixed that by giving them a key's material, at the cost of the suggestion bar borrowing the keyboard's vocabulary for something the keyboard does not do. Both are gone, and so are the hairline dividers 17.0.0 added, which existed only because two bare words read as a phrase.

`KREYOL_GREEN` was darkened to `#27864D` in 17.0.0: a suggestion is read at 18 sp, which is WCAG normal text (4.5:1), and the old `#2E9E5B` gave 3.41:1 against white while the French blue gave 4.93:1.

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

The five games sit behind the `Jé` tab in `GamesFragment`, which swaps the chosen game into its own `FrameLayout` via `childFragmentManager`. **No nested pager**: the word-search grid is dragged with a finger, and a second `ViewPager2` would fight it for every horizontal gesture. Back-to-the-menu is an `OnBackPressedCallback` enabled only while a game is open, so the Back button still quits the app everywhere else. The menu lays cards two per row; an odd count (currently five) gets a same-weight spacer on the short row.

Guide and À Propos are no longer tabs: `SheetFragment` opens the existing fragments full screen from the foot of the Démarrage tab.

The first-run funnel keys off `funnel_keyboard_enabled`, which is timestamped once and never cleared — the tab bar comes back on first *activation* and never hides again, even if a system update later deselects the keyboard.

### Games (`wordscramble/`, `wordsearch/`, `mokarenaj/`, `cloze/`, `crossword/`, `chassecroise/` packages)

Six vocabulary mini-games accessible from the `Jé` tab (seven cards since 21.0.0: *Sonjé*, the Leitner revision, sits first — see the carnet section below) (`mokarenaj` is a Creole Wordle, `crossword` is *Mokwaré*, `chassecroise` is *Mo an plas*). Three of them pull words directly from the loaded dictionary, with a hard-coded fallback list.

*Mo an plas* (21.1.0) is the odd one out and the reason it was worth porting: it is the **only game playable without knowing a word of Creole**. The other five require comprehension before anything else happens, if only to read a clue. Here the words are handed to you and the deduction is geometric — lengths and crossings — so a complete beginner has a way in. The gloss is the reward, not the clue: it appears only once a word's crossings have all been placed (`ChasseCroiseSession.verrouille`), because revealing it on drop would let the grid be solved by probing. A won word never comes back out, which is what keeps an already-read gloss from being taken back when a neighbour is moved. It shares `CrosswordGrid`/`CrosswordWord`/`CrosswordDifficulty` with *Mokwaré* rather than duplicating them — same geometry, same asset schema — and reads `creole_chassecroise.json` with no hard-coded fallback, for the same reason as *Mokwaré* and *Fraz a twou*.

Since 12.0.0 the games draw **only from words that carry a French gloss**, through `TranslationDictionary.filtrerMotsTraduits()` (a game that cannot say what its word means teaches spelling and nothing else). The filter returns the unfiltered list below 50 words, so a missing translation asset leaves the games playable and merely mute. It applies to the *draw* only: `MoKarenajData.isValidWord()` must keep accepting any dictionary word, glossed or not, or the player's own guesses start getting rejected. Two games are exceptions, both reading a prebuilt asset with **no fallback** because a hand-written stand-in would make a broken delivery look playable: `cloze` (*Fraz a twou*) reads `creole_cloze.json`, and `crossword` (*Mokwaré*) reads `creole_crossword.json`. *Mokwaré* is also the only game where the player **writes** the word rather than recognising it, so it ships its own 29-key input pad. Since 20.0.1 that pad **mirrors the IME's own AZERTY rows** rather than the alphabet (the player types on that layout daily, so muscle memory beats alphabetical search), with two deliberate departures: É È Ò are direct keys where the keyboard leaves `ò` on a long-press of `o`, and there is no ⇧/apostrophe/space, which makes all three rows ten keys wide. A missing asset shows a screen saying so. Since 20.0.2 that pad is **pinned below the scrolling area rather than inside it**, which is what lets its keys hold 48 dp (the Android touch minimum, and what the IME's own keys measure) without an eleven-row grid pushing them off screen; the grid scrolls in what is left. Beware raw-pixel `setPadding`/`setMargins`, the house style in the other game fragments: on touch targets it silently shrinks them (12 px is 4.4 dp at 440 dpi), which is how the keys shipped at 30 dp in 20.0.0. Both destructive controls (*Solution*, *Nouvelle grille*) sit at the far end of the scroll, under the clue lists, and confirm before discarding a grid; *Solution* previously sat 7 dp under the backspace key, which is the key people hit without aiming. Grid models and the play session live in `crossword/CrosswordModels.kt` (session logic is Android-free and covered by `CrosswordSessionTest`); the fragment is `CrosswordFragment` in `SettingsActivity.kt`.

### The carnet and its spaced repetition (`carnet/` package, 21.0.0)

A word won in any of the five games becomes a **card**: one per form, carrying its gloss, a corpus sentence with its credit, its rarity and the games that gave it. The collection is called **Sanblé** (« collectionner, rassembler », attested in the shipped gloss table), the revision **Sonjé** (« se souvenir »). Both names come from `creole_translations.json`; nothing here is invented Creole. Ported from the Luxembourgish fork (its 21.0.0 and 22.0.0, `carnet/`), 11 400 lines of which the drawing is language-free and ports verbatim.

The entry point is a full-width banner at the top of the `Jé` tab, above the game cards, plus a sixth card (*Sonjé*) that opens the Leitner boxes. The banner reads **only what the preferences hold** (total, games represented, cards due): rarities would need the `creole_dict.json` scan, which has no business on the main thread when a tab opens.

**Storage and the privacy boundary.** The collection is a `SharedPreferences` blob (`kreyol_carnet_prefs`), a domain both backup rule files include, so it is backed up and transfers between phones. That is deliberate and it holds, because what enters it is drawn from a closed shipped set — the same boundary as emoji recents, never free text. The counterpart is `PreuveDeFrappe`, "the keyboard is the exam": a card whose usage counter rose since the last session goes up a box with no question asked. Those counters (`creole_dict_with_usage.json`) and the reference it compares against (`carnet_vu.json`) live in `filesDir`, and 21.0.0 made both backup rule files **exclude them explicitly** rather than rely on include-only semantics. Only a due date reaches the preferences, where it is indistinguishable from one pushed by a successful review. Do not "simplify" that reference into the preferences: it would ship a typing history to the cloud.

**A word with no gloss does not become a card**, and the refusal lives in `Carnet.ajouter`, not in the five games. It is not theoretical: four games have drawn only from glossed words since 12.0.0, but *Fraz a twou* does not draw — its answers come from the corpus, and **156 of its 240 answers, 65 %, have no gloss at all**. Without the filter, two cards in three from that game would read « sens non répertorié ». The price is that *Fraz a twou* gives three times fewer cards than it makes you find; widening the gloss table is what lifts it, not softening the rule.

**Rarity is the frequency rank**, never an invented statistic, and the thresholds are measured on the Creole pool rather than transposed: the Luxembourgish dictionary has 38 442 forms and puts them at 3 000 / 6 500 / 9 000, where the Creole one has 5 296 and every card would be common. On the 622 glossed forms that can become cards, **800 / 2 000 / 3 500** give 42 / 28 / 20 / 10 %, against the fork's 37 / 34 / 20 / 8 %. Two consequences are frozen in `CarnetRareteTest`: *Mokwaré* is the supplier of rare cards (49 / 27 / 15 / 9 %), and *Fraz a twou* can never give one, because its answers are picked in a frequency band (5 ≤ freq ≤ 150) and none passes rank 1 800.

Three things the port drops, all for reasons already settled elsewhere in this file:

- **The word's nature** (the shield's partition, and the second channel that makes eight hues readable). The fork reads it off the form: a capital is a noun, `-en` a verb. Creole capitalizes only proper nouns and barely inflects. Reading it off the French gloss was measured and dropped: « first gloss word ends in -er/-ir/-re » catches 144 of the 622 words and is **wrong on a third of them** (`té` « terre », `manman` « mère », `pyébwa` « arbre », `kat` « quatre »). `Armorial.natureDe` therefore returns `AUTRE` always, and says so.
- **The flexion family** and **the grammatical category**: no source gives either. The panel space they held goes to the sentence's credit, which is an obligation rather than a content. The type pill under the name now carries the **gloss's source** (« Kreyolopedia » / « Wiktionnaire ») — true information the card holds, and the CC BY-SA attribution travelling with what it covers.
- **`creole_blasons.json`**, the semantic-field classification and the hand-attributed charges. Shipping without it is a valid delivery, not a failure: every card keeps the hue of its first three letters and the trace of its word, which is exactly the state the fork lived in until its 22.6.0. No intermediate version of that classification is ugly, which is what makes it addable in waves. `Meubles.kt` (111 silhouettes) and `Blason.kt` are ported and wait for it.

**The revision is a flashcard in every box** (six Leitner boxes, fixed intervals 1/3/7/16/35/90 days, then the card is acquired and leaves the queue). The fork made boxes 2+ type the word and removed it entirely in its 23.0.0; do not bring typing back. The queue is capped at 12, existing carnets are spread rather than dumped (a 200-card wall never opens), and a due date further out than the longest interval is treated as aberrant so a backward clock cannot lock the deck. `Sonje.kt` is the calendar and has no `Context`; `SessionSonje.kt` the rules; `VueSonje.kt` the screen. `SonjePlanTest`, `SonjeSessionTest`, `CarnetRareteTest` and `ExemplesAssetTest` lock the above.

`KeyFeedback` gained the fork's haptic-texture layer whole (composed primitives, per-device probing) so a thumb crossing a card feels its relief. It is the only reason `VIBRATE` is now declared in the manifest: without it `sonder()` falls back to `NiveauTactile.CANNED` and the card feels like a key, so the permission can be dropped if it is not worth a line on the Play listing.

## iOS Port

The iOS Swift/SwiftUI port lives on the `ios/port` branch, not on `main`. Load the `ios-port` skill before any iOS work.

## CI/CD

- **`build-apk.yml`** — triggers on push/PR to `main` when `android_keyboard/**` or `.github/workflows/**` change, or on `v*` tags. It builds the debug/release APK **and AAB**. Its paths filter negates `!Dictionnaires/RAPPORT_LINGUISTIQUE.md`, so refreshing that report can never start a build: the report is an analysis of the corpus, nothing in it ships in the APK or AAB. Runs the Python dictionary pipeline first (needs `HF_TOKEN` secret), then `generate_cloze.py`, `generate_translations.py`, `generate_crossword.py`, `generate_chassecroise.py` and `generate_exemples.py` in that order (each consumes what the steps before it wrote; the crossword needs both the dictionary and the gloss table, the chassé-croisé imports the crossword's placement engine, the examples need the gloss table to know which words can become cards). Then builds and signs the APK. Each generated asset has a volume guard in the *Verify Generated Assets* step (250 cloze questions, 800 glossed forms, 90 crossword grids with 25 per difficulty, 150 chassé-croisé grids with 50 per difficulty, 400 illustrated words), plus an attribution check, because a truncated asset degrades silently at runtime rather than failing the build. Creates a GitHub Release on tags. Its paths filter also covers `Dictionnaires/**`; note the workflow regenerates the dictionary on every build **without committing it back**, so the shipped APK is built from a freshly regenerated dictionary rather than the committed one.
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
