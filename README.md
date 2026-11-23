# Lattice MTG for Android

Lattice MTG is a native Android companion for Magic: The Gathering. Browse a local card vault, manage decklists, and track a tabletop game on your phone. The app is built with Kotlin, Jetpack Compose, and Room.

**Development preview.** The Android app is still in active development. Features, data handling, and installation steps may change. Keep a separate copy of important decklists while testing preview builds.

[Web app](https://dreadpool-87.github.io/Lattice-MTG/) · [Web source](https://github.com/Dreadpool-87/Lattice-MTG) · [Report an issue](https://github.com/Dreadpool-87/Lattice-MTG-app/issues)

## Features

- **Card Vault.** Search and filter a bundled starter catalog and locally cached cards. Bookmark cards, create custom entries, and fetch additional cards from Scryfall. Inspect card faces, locally cached printings, and rulings when available.
- **Delver Lens import.** Open **Vault → CSV** to add verified printings to the Vault. Review verified copies, conflicts, and unresolved rows. You can also create a deck from the verified rows.
- **Decks.** Create and edit decks, paste a text decklist, copy a decklist to the clipboard, and view mana curve and synergy summaries.
- **Tabletop.** Track life totals, poison counters, and commander damage. Use the probability calculator to explore draws.
- **Lattice Oracle.** Ask Gemini questions through Firebase AI Logic. If you select a deck, the app sends relevant deck information with your question, including up to 35 mainboard entries. Deck statistics and AI replies are aids for reviewing a deck.

## Current limitations

- The Vault searches the starter catalog and cards stored on your device. Online Scryfall lookups can expand that cache. In offline-only mode, CSV rows that require an online lookup remain unresolved.
- CSV quantities appear in the import report and carry over when you create a deck. The Vault stores card records. Use the [web collection importer](https://dreadpool-87.github.io/Lattice-MTG/collection/) for collection-level exports.
- Deck checks cover selected rules. Review format-specific requirements separately, especially when some cards are unresolved or the Oracle chat received only part of the decklist.

## Build and test

The app targets Android API 36 and supports devices on API 24 or newer. For a local build, use Android Studio with Android SDK 36.1, JDK 17 or newer compatible with the project, and Gradle 9.3.1. See the [Android Gradle Plugin compatibility table](https://developer.android.com/build/releases/agp-9-1-0-release-notes#compatibility).

The GitHub checkout contains the wrapper version settings. Use an installed Gradle 9.3.1 because the wrapper launchers and JAR are absent. The debug signing configuration expects a local `debug.keystore` at the repository root. For a fresh development install, you can generate it locally:

```sh
keytool -genkeypair -keystore debug.keystore -storepass android \
  -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 \
  -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
gradle :app:assembleDebug :app:testDebugUnitTest
```

Keep the signing key used for any installed build you plan to update in place. For instrumentation tests, attach a device or start an emulator, then run `gradle :app:connectedDebugAndroidTest`.

The repository includes tests for card refresh, upgrading the local database, Delver Lens import, deck checks, and the scope of data sent to Gemini. When reporting a bug, include the app version or commit, your Android version, and steps to reproduce it.

The app uses [Firebase AI Logic](https://firebase.google.com/docs/ai-logic) and [App Check](https://firebase.google.com/docs/ai-logic/app-check) for Oracle chat. To test chat outside AI Studio, configure Firebase and App Check for your build. Keep local environment files, signing keys, and App Check debug tokens out of commits and issue reports.

## In-app updates

In **Settings → App updates**, check for a new version and select **Update** when one is offered. Android asks for confirmation and may ask you to allow installs from Lattice MTG the first time.

For each stable GitHub release, attach the signed APK as `lattice-android.apk` and an `android-update.json` file containing `tag` (the exact GitHub release tag), `versionCode` (the integer in the APK), and `sha256` (the lowercase SHA-256 digest of the signed APK). Attach both assets before publishing. Raise the APK version code for each release and use the same application ID and signing key as the installed APK. Android verifies the signature when installing over the existing app.

## Credits and licensing

- [Scryfall](https://scryfall.com/) provides online card data and images.
- [Lattice MTG on the web](https://dreadpool-87.github.io/Lattice-MTG/credits/) lists credits for the web companion. Android dependency and font attributions are available under **Settings → About, Credits & Licensing**.

Lattice MTG is an unofficial Magic: The Gathering fan project. Wizards of the Coast has not approved, endorsed, or sponsored it. Portions of the materials used belong to Wizards of the Coast. © Wizards of the Coast LLC. See the [Fan Content Policy](https://company.wizards.com/en/legal/fancontentpolicy).

This Android repository currently has no project-wide code license. Third-party components retain their respective licenses.
