# Lattice MTG for Android

Lattice MTG is a native Android companion for Magic: The Gathering. Browse a local card vault, manage decklists, and track a tabletop game on your phone. The app is built with Kotlin, Jetpack Compose, and Room.

**Development preview.** The Android app is still in active development. Features, data handling, and installation steps may change. Keep a separate copy of important decklists while testing preview builds.

[Web app](https://dreadpool-87.github.io/Lattice-MTG/) · [Web source](https://github.com/Dreadpool-87/Lattice-MTG) · [Report an issue](https://github.com/Dreadpool-87/Lattice-MTG-app/issues)

## Features

- **Card Vault.** Open saved collections in **Vault** and search the bundled offline catalog under **Browse catalog**. Bookmark cards, create custom entries, and fetch additional cards from Scryfall. Inspect card faces, locally cached printings, and rulings when available.
- **Delver Lens import.** Open **Vault → Import CSV** and choose **My collection** or **Deck**. Collections are named, expandable lists of owned copies by verified printing. The import review shows conflicts and unresolved rows.
- **Decks.** Create and edit decks, paste a text decklist, copy a decklist to the clipboard, and view mana curve and synergy summaries.
- **Tabletop.** Track life totals, poison counters, and commander damage. Use the probability calculator to explore draws.

## Current limitations

- The catalog includes one representative printing per card plus locally cached printings. Online Scryfall lookups can expand that cache. In offline-only mode, CSV rows that require an online lookup remain unresolved.
- Collection imports require a Scryfall printing ID or a set code and collector number to verify the owned printing. Replacing an existing collection requires confirmation and a fully verified import. Collections imported before this feature can be reconstructed by re-importing the original CSV. Use the [web collection importer](https://dreadpool-87.github.io/Lattice-MTG/collection/) for collection-level exports.
- Deck checks cover selected rules. Review format-specific requirements separately, especially when some cards are unresolved.

## Build and test

The app targets Android API 36 and supports devices on API 24 or newer. For a local build, use Android Studio with Android SDK 36.1, JDK 21, and the included Gradle 9.3.1 wrapper. See the [Android Gradle Plugin compatibility table](https://developer.android.com/build/releases/agp-9-1-0-release-notes#compatibility).

The debug signing configuration expects a local `debug.keystore` at the repository root. For a fresh development install, you can generate it locally:

```sh
keytool -genkeypair -keystore debug.keystore -storepass android \
  -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 \
  -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Keep the signing key used for any installed build you plan to update in place. For instrumentation tests, attach a device or start an emulator, then run `./gradlew :app:connectedDebugAndroidTest`.

The repository includes tests for card refresh, local database migrations, Delver Lens import, and deck checks. When reporting a bug, include the app version or commit, your Android version, and steps to reproduce it.

## In-app updates

**Users on Android app versions 1.0 and 1.2:** those builds used an older signing
certificate. Android cannot install 1.3 or later over them. Before uninstalling,
copy important decklists and keep your original collection CSV. Uninstalling
clears local app data, and recovery from Android Backup across the key change
has not been verified. Keep the old install if you still need data from it.

Versions 1.3 and 1.4 share the current release signing certificate. Its SHA-256
fingerprint is `fb7a676614355ff004ab47de8026d529722874b55131174ad6f1a05fd2da1e73`.
Android verifies the signer during installation. Play Protect evaluates the
download independently.

In **Settings → App updates**, check for a new version and select **Update** when one is offered. Android asks for confirmation and may ask you to allow installs from Lattice MTG the first time.

For each stable GitHub release, attach the signed APK as `lattice-android.apk` and an `android-update.json` file containing `tag` (the exact GitHub release tag), `versionCode` (the integer in the APK), and `sha256` (the lowercase SHA-256 digest of the signed APK). Attach both assets before publishing. Raise the APK version code for each release and use the same application ID and signing key as the installed APK. Android verifies the signature when installing over the existing app.

## Android backup

Android backup is enabled and may include local decks, saved collections and
preferences according to the device's backup settings. Keep separate copies of
important decklists and original collection CSVs.

## Credits and licensing

- [Scryfall](https://scryfall.com/) provides online card data and images.
- [Lattice MTG on the web](https://dreadpool-87.github.io/Lattice-MTG/credits/) lists credits for the web companion. Android dependency and font attributions are available under **Settings → About, Credits & Licensing**.

Lattice MTG is an unofficial Magic: The Gathering fan project. Wizards of the Coast has not approved, endorsed, or sponsored it. Portions of the materials used belong to Wizards of the Coast. © Wizards of the Coast LLC. See the [Fan Content Policy](https://company.wizards.com/en/legal/fancontentpolicy).

This Android repository currently has no project-wide code license. Third-party components retain their respective licenses.

### Offline card data

Open **Vault → Browse catalog** for local catalog search and filters. The Vault itself opens your saved collections. In **Settings → Offline card data**, check for the current Lattice snapshot and download all cards or a single set. The full pack includes one representative printing per card; set packs include one language per collector-number and card combination. A download includes card text, faces and legality. Card images and metadata outside the published snapshot can still be fetched online. Downloaded card data does not change collection quantities or deck contents. After the website publishes a newer snapshot, Vault shows an in-app notice when an installed pack changed.
