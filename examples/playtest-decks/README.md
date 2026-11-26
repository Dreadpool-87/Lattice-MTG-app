# Playtest example decks

Lattice MTG Preview adds these four decks on first launch. They are also used as offline import test fixtures. To try one yourself, copy the deck text into **Decks → Import**, keep the section headers, and select the format below. Open the imported deck and choose **Playtest**.

| Deck | Format | Mainboard | Command zone | Source |
| --- | --- | ---: | ---: | --- |
| [Orzhov Lifegain](../../app/src/debug/assets/playtest-decks/Casual-Orzhov-Lifegain.txt) | Casual | 60 | 0 | [xfile's Star Trek-era Standard mainboard](https://mtgdecks.net/meta/4-new-mtg-standard-decks-featuring-star-trek-mtg-436) |
| [Golgari Midrange](../../app/src/debug/assets/playtest-decks/Casual-Golgari-Midrange.txt) | Casual | 60 | 0 | [oosunq's Reality Fracture-era Standard mainboard](https://mtgdecks.net/meta/4-new-mtg-standard-decks-featuring-reality-fracture-430) |
| [Klingon Fury](../../app/src/debug/assets/playtest-decks/Commander-Klingon-Fury.txt) | Commander | 99 | 1 | [Wizards of the Coast, Star Trek Commander decklists](https://magic.wizards.com/en/news/announcements/star-trek-commander-decklists#klingon-fury) |
| [Multiverse Reforged](../../app/src/debug/assets/playtest-decks/Commander-Multiverse-Reforged.txt) | Commander | 99 | 1 | [Wizards of the Coast, Reality Fracture Commander decklist](https://magic.wizards.com/en/news/announcements/reality-fracture-multiverse-reforged-commander-decklist) |
The Orzhov list replaces Zora, Spider Fancier with one more Starscape Cleric so every card resolves in the included offline catalog.

In Preview, open **Settings → Debug tools** to restore missing example decks, check the local catalog, run a bounded catalog and deck stress test, or copy the local diagnostic log. The official build omits the example assets and debug tools.

Multiverse Reforged includes the FRC and FRA printing numbers of the [stock deck](https://www.deckcipher.com/commander-precons/multiverse-reforged-frc/). Its printing metadata is bundled in the Preview from the Lattice Android card catalog's FRC and FRA set packs. An existing Preview deck is upgraded in place only when its card printings, quantities and zones match the original example.
