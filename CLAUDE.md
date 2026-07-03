# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What This Project Is

**Klavyé Kréyòl Karukera** — an intelligent keyboard for Guadeloupean Creole (kréyòl Guadeloupéen). It is an Android IME (Input Method Editor) with an iOS port in progress. The keyboard provides bilingual suggestions (Kreyòl + French) powered by a curated dictionary and n-gram model built from Creole literary texts.

## Android Build Commands

All commands run from `android_keyboard/`:

```bash
./gradlew assembleDebug          # Build debug APK
./gradlew assembleRelease        # Build signed release APK (requires keystore config)
./gradlew installDebug           # Build + install on connected device
./gradlew test                   # Run all unit tests
./gradlew test --tests "com.example.kreyolkeyboard.LevenshteinDistanceTest"  # Single test class
```

**Local build gotchas:** AGP requires Java 17 (`export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64`), and the checked-in `gradlew` script is corrupted (missing `eval`, passes quoted args to Gradle). Work around it with:
```bash
$JAVA_HOME/bin/java -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain <task>
```
CI is unaffected (it installs Gradle 8.7 directly).

Release signing reads from `android_keyboard/gradle.properties` (local) or environment variables (`KEYSTORE_FILE`, `STORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`). Falls back to debug signing if secrets are missing. See `gradle.properties.example` for the format.

**versionCode** format: `60501` = version `6.5.1` (major × 10000 + minor × 100 + patch). minSdk 21, targetSdk 35.

## Dictionary / Data Pipeline

The JSON assets in `android_keyboard/app/src/main/assets/` are the **source of truth** used by both Android and iOS:
- `creole_dict.json` — `[word, frequency]` list (~1867 words)
- `creole_ngrams.json` — n-gram context model
- `french_simple_dict.json` — French fallback dictionary

To regenerate from the Hugging Face dataset `POTOMITAN/PawolKreyol-gfc` (requires `HF_TOKEN`):
```bash
cd Dictionnaires
pip install datasets huggingface_hub
python KreyolComplet.py          # Fetches HF data, rebuilds dict + n-grams, backs up old files
```

## Android Architecture

### IME Entry Point

`KreyolInputMethodServiceRefactored.kt` is the **active** IME service. `KreyolInputMethodService.kt` is the legacy monolithic version — do not add features there.

The refactored IME coordinates four components via listener interfaces:

| Component | Responsibility |
|-----------|---------------|
| `KeyboardLayoutManager` | Creates and styles key buttons, manages shift/caps/numeric mode |
| `SuggestionEngine` | Loads dictionary + n-grams, produces ranked bilingual suggestions |
| `AccentHandler` | Long-press popup for accented characters |
| `InputProcessor` | Handles key events, backspace, word commit to `InputConnection` |

### Suggestion Pipeline (`SuggestionEngine.kt`)

1. **Prefix match** against `creole_dict.json` (Kreyòl prioritized)
2. **N-gram context** from the last 5 committed words
3. **Levenshtein fuzzy match** (`LevenshteinDistance.kt`) for typo tolerance
4. **Accent-tolerant match** (`AccentTolerantMatcher.kt`) — matches `e` against `é`, etc.
5. **French fallback** — only kicks in at ≥ 3 characters typed
6. **Casing preservation** — `applyCasingPattern()` mirrors the user's casing onto the suggestion

Max 3 suggestions displayed (5 internally scored: 3 Kreyòl + 2 French slots).

### Gamification (`gamification/` package)

- `CreoleDictionaryWithUsage` (Kotlin `actor`) — tracks per-word usage counts, thread-safe
- `WordUsageStats` — per-word stats with 7 mastery levels: Pipirit → Potomitan
- `VocabularyStatsActivity` — displays dashboard with progress per level
- `WordCommitListener` interface — `KreyolInputMethodServiceRefactored` implements this to log each committed word

### Games (`wordscramble/`, `wordsearch/` packages)

Two vocabulary mini-games accessible from `SettingsActivity`. They pull words directly from the loaded dictionary. No separate data source.

## iOS Port (lives on the `ios/port` branch)

The iOS Swift/SwiftUI port is **not on `main`** — its sources, `project.yml`, and `ios-build.yml` workflow exist only on the `ios/port` branch. On `main`, `ios/` contains only signing materials (CSR, distribution key). Check out or merge from `ios/port` before doing iOS work.

The port uses **XcodeGen** (`project.yml`) and requires a Mac with Xcode 15:

```bash
cd ios
xcodegen generate               # Creates KreyolKeyb.xcodeproj from project.yml
```

The iOS project references the shared JSON assets directly from `android_keyboard/app/src/main/assets/` — do not duplicate them. The Xcode project is gitignored; regenerate it with `xcodegen generate` before building.

The structure mirrors Android: `Core/SuggestionEngine.swift`, `Core/LevenshteinDistance.swift`, `Core/AccentTolerantMatcher.swift`, `Gamification/`, `Games/`, `Views/ContentView.swift` (≈ `SettingsActivity`), and `KeyboardExtension/KeyboardViewController.swift` (≈ the IME service).

**Phase 1** (Swift source files) is complete. **Phase 2** (wiring the `KeyboardViewController` with actual key views, accent popups, and App Group sharing) is not yet implemented.

## CI/CD

- **`build-apk.yml`** — triggers on push/PR to `main` when `android_keyboard/**` or `.github/workflows/**` change, or on `v*` tags. Runs the Python dictionary pipeline first (needs `HF_TOKEN` secret), then builds and signs the APK. Creates a GitHub Release on tags. ⚠️ Its paths filter says `Dictionnaries/**` (misspelled) — changes to the real `Dictionnaires/` folder do **not** trigger a build.
- **`ios-build.yml`** (on `ios/port` branch only) — triggers on push to `ios/port` when `ios/` changes. Runs on `macos-14` (Xcode 15, Apple Silicon). Requires secrets: `DIST_CERT_BASE64`, `DIST_CERT_PASSWORD`, `PROVISIONING_PROFILE_BASE64`, `DEVELOPMENT_TEAM`, `APPLE_ID`, `APP_SPECIFIC_PASSWORD`.

## Legacy / Auxiliary Directories

- `clavier_creole/` — abandoned Flutter prototype (`lib/main.dart` + old dict copies). Do not develop here.
- `PawolKreyol/` — raw Creole corpus texts (`Textes_kreyol.json`/`.xlsx`) feeding the HF dataset.
- `docs/` — GitHub Pages site (privacy policy, beta onboarding, feedback form).
- `KreyolKeybPlayStore/`, `Screenshots/`, `Logos/` — store listing and branding assets.
