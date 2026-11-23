# Lattice MTG for Android

Lattice MTG is a native Android companion for Magic: The Gathering. Browse a local card vault, manage decklists, and track a tabletop game on your phone. The app is built with Kotlin, Jetpack Compose, and Room.

**Status:** Development preview. Use test decks while the card refresh issue below is open.

[Web app](https://dreadpool-87.github.io/Lattice-MTG/) · [Web source](https://github.com/Dreadpool-87/Lattice-MTG) · [Report an issue](https://github.com/Dreadpool-87/Lattice-MTG-app/issues)

## Features

- **Card Vault.** Search and filter a bundled starter catalog and locally cached cards. Bookmark cards, create custom entries, and fetch additional cards from Scryfall.
- **Decks.** Create and edit decks, paste a text decklist, copy a decklist to the clipboard, and view mana curve and synergy summaries.
- **Tabletop.** Track life totals, poison counters, and commander damage. Use the probability calculator to explore draws.
- **Lattice Oracle.** Ask Gemini questions through Firebase AI Logic. If you select a deck, the app sends relevant deck information with your question, including up to 35 mainboard entries. Deck statistics and AI replies are aids for reviewing a deck.

## Current limitations

- Refreshing a card already used in a saved deck can remove its deck entries or reset its bookmark. Keep a separate copy of important decklists while this is being fixed.
- The Android app imports plain-text decklists. For Delver Lens collection CSVs and exact printing verification, use [Import collection on the website](https://dreadpool-87.github.io/Lattice-MTG/collection/).
- The Vault searches the local starter catalog and cached results. Scryfall search can add cards to that cache. The website provides the full Lattice catalog, printing details, and official rulings.

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

The app uses [Firebase AI Logic](https://firebase.google.com/docs/ai-logic) and [App Check](https://firebase.google.com/docs/ai-logic/app-check) for Oracle chat. To test chat outside AI Studio, configure Firebase and App Check for your build. Keep local environment files, signing keys, and App Check debug tokens out of commits and issue reports.

## Credits and licensing

- [Scryfall](https://scryfall.com/) provides online card data and images.
- [Lattice MTG on the web](https://dreadpool-87.github.io/Lattice-MTG/credits/) lists credits for the web companion. Android dependency and font attributions are available under **Settings → About, Credits & Licensing**.

Lattice MTG is an unofficial Magic: The Gathering fan project. Wizards of the Coast has not approved, endorsed, or sponsored it. Portions of the materials used belong to Wizards of the Coast. © Wizards of the Coast LLC. See the [Fan Content Policy](https://company.wizards.com/en/legal/fancontentpolicy).

This Android repository currently has no project-wide code license. Third-party components retain their respective licenses.
