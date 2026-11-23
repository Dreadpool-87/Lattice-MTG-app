package com.example.data.local

object StarterDecks {
    suspend fun seedIfEmpty(cardDao: CardDao, deckDao: DeckDao) {
        if (cardDao.getCardCount() == 0) {
            cardDao.upsertAllPreservingState(StarterCards.cards)
        }
        if (deckDao.getDeckCount() == 0) {
            val now = System.currentTimeMillis()

            // Deck 1: Mycosynth Lattice Engine (Commander)
            val latticeDeckId = deckDao.insertDeck(
                DeckEntity(
                    name = "Mycosynth Lattice Engine",
                    format = "Commander",
                    description = "Mono-Blue artifact synergy & Karn Lattice lock engine built around Urza and Mycosynth Lattice.",
                    commanderCardId = "local-urza-lord-high-artificer",
                    colorIdentity = "U",
                    notes = "Primary line: Ramp with Sol Ring / Urza, tutor Mycosynth Lattice via Whir of Invention or Fabricate, then lock with Karn, the Great Creator or protect permanents with Darksteel Forge.",
                    createdAt = now - 86_400_000L,
                    updatedAt = now - 3_600_000L
                )
            )
            val latticeEntries = listOf(
                DeckEntryEntity(deckId = latticeDeckId, cardId = "local-urza-lord-high-artificer", quantity = 1, zone = DeckZones.COMMANDER),
                DeckEntryEntity(deckId = latticeDeckId, cardId = "local-mycosynth-lattice", quantity = 1, zone = DeckZones.MAINBOARD),
                DeckEntryEntity(deckId = latticeDeckId, cardId = "local-karn-great-creator", quantity = 1, zone = DeckZones.MAINBOARD),
                DeckEntryEntity(deckId = latticeDeckId, cardId = "local-unwinding-clock", quantity = 1, zone = DeckZones.MAINBOARD),
                DeckEntryEntity(deckId = latticeDeckId, cardId = "local-darksteel-forge", quantity = 1, zone = DeckZones.MAINBOARD),
                DeckEntryEntity(deckId = latticeDeckId, cardId = "local-the-one-ring", quantity = 1, zone = DeckZones.MAINBOARD),
                DeckEntryEntity(deckId = latticeDeckId, cardId = "local-sol-ring", quantity = 1, zone = DeckZones.MAINBOARD),
                DeckEntryEntity(deckId = latticeDeckId, cardId = "local-arcane-signet", quantity = 1, zone = DeckZones.MAINBOARD),
                DeckEntryEntity(deckId = latticeDeckId, cardId = "local-foundry-inspector", quantity = 1, zone = DeckZones.MAINBOARD),
                DeckEntryEntity(deckId = latticeDeckId, cardId = "local-etherium-sculptor", quantity = 1, zone = DeckZones.MAINBOARD),
                DeckEntryEntity(deckId = latticeDeckId, cardId = "local-mishras-bauble", quantity = 1, zone = DeckZones.MAINBOARD),
                DeckEntryEntity(deckId = latticeDeckId, cardId = "local-senseis-divining-top", quantity = 1, zone = DeckZones.MAINBOARD),
                DeckEntryEntity(deckId = latticeDeckId, cardId = "local-counterspell", quantity = 1, zone = DeckZones.MAINBOARD),
                DeckEntryEntity(deckId = latticeDeckId, cardId = "local-fierce-guardianship", quantity = 1, zone = DeckZones.MAINBOARD),
                DeckEntryEntity(deckId = latticeDeckId, cardId = "local-cyclonic-rift", quantity = 1, zone = DeckZones.MAINBOARD),
                DeckEntryEntity(deckId = latticeDeckId, cardId = "local-rhystic-study", quantity = 1, zone = DeckZones.MAINBOARD),
                DeckEntryEntity(deckId = latticeDeckId, cardId = "local-fabricate", quantity = 1, zone = DeckZones.MAINBOARD),
                DeckEntryEntity(deckId = latticeDeckId, cardId = "local-whir-of-invention", quantity = 1, zone = DeckZones.MAINBOARD),
                DeckEntryEntity(deckId = latticeDeckId, cardId = "local-thoughtcast", quantity = 1, zone = DeckZones.MAINBOARD),
                DeckEntryEntity(deckId = latticeDeckId, cardId = "local-brainstorm", quantity = 1, zone = DeckZones.MAINBOARD),
                DeckEntryEntity(deckId = latticeDeckId, cardId = "local-ponder", quantity = 1, zone = DeckZones.MAINBOARD),
                DeckEntryEntity(deckId = latticeDeckId, cardId = "local-ancient-tomb", quantity = 1, zone = DeckZones.MAINBOARD),
                DeckEntryEntity(deckId = latticeDeckId, cardId = "local-urzas-saga", quantity = 1, zone = DeckZones.MAINBOARD),
                DeckEntryEntity(deckId = latticeDeckId, cardId = "local-academy-ruins", quantity = 1, zone = DeckZones.MAINBOARD),
                DeckEntryEntity(deckId = latticeDeckId, cardId = "local-inventors-fair", quantity = 1, zone = DeckZones.MAINBOARD),
                DeckEntryEntity(deckId = latticeDeckId, cardId = "local-reliquary-tower", quantity = 1, zone = DeckZones.MAINBOARD),
                DeckEntryEntity(deckId = latticeDeckId, cardId = "local-island", quantity = 14, zone = DeckZones.MAINBOARD)
            )
            deckDao.insertEntries(latticeEntries)

            // Deck 2: Izzet Arcane Tempo (Modern - 60 cards)
            val izzetDeckId = deckDao.insertDeck(
                DeckEntity(
                    name = "Izzet Arcane Tempo",
                    format = "Modern",
                    description = "Low-curve U/R spellslinger tempo built around Dragon's Rage Channeler, Ragavan, and Murktide Regent.",
                    colorIdentity = "U,R",
                    notes = "Fuel Delirium early with Mishra's Bauble and Dragon's Rage Channeler, protect threats with Counterspell, and close with Murktide Regent.",
                    createdAt = now - 172_800_000L,
                    updatedAt = now - 7_200_000L
                )
            )
            val izzetEntries = listOf(
                DeckEntryEntity(deckId = izzetDeckId, cardId = "local-ragavan-nimble-pilferer", quantity = 4, zone = DeckZones.MAINBOARD),
                DeckEntryEntity(deckId = izzetDeckId, cardId = "local-dragons-rage-channeler", quantity = 4, zone = DeckZones.MAINBOARD),
                DeckEntryEntity(deckId = izzetDeckId, cardId = "local-murktide-regent", quantity = 4, zone = DeckZones.MAINBOARD),
                DeckEntryEntity(deckId = izzetDeckId, cardId = "local-monastery-swiftspear", quantity = 2, zone = DeckZones.MAINBOARD),
                DeckEntryEntity(deckId = izzetDeckId, cardId = "local-lightning-bolt", quantity = 4, zone = DeckZones.MAINBOARD),
                DeckEntryEntity(deckId = izzetDeckId, cardId = "local-counterspell", quantity = 4, zone = DeckZones.MAINBOARD),
                DeckEntryEntity(deckId = izzetDeckId, cardId = "local-expressive-iteration", quantity = 4, zone = DeckZones.MAINBOARD),
                DeckEntryEntity(deckId = izzetDeckId, cardId = "local-faithless-looting", quantity = 4, zone = DeckZones.MAINBOARD),
                DeckEntryEntity(deckId = izzetDeckId, cardId = "local-mishras-bauble", quantity = 4, zone = DeckZones.MAINBOARD),
                DeckEntryEntity(deckId = izzetDeckId, cardId = "local-prismari-command", quantity = 2, zone = DeckZones.MAINBOARD),
                DeckEntryEntity(deckId = izzetDeckId, cardId = "local-brainstorm", quantity = 4, zone = DeckZones.MAINBOARD),
                DeckEntryEntity(deckId = izzetDeckId, cardId = "local-steam-vents", quantity = 4, zone = DeckZones.MAINBOARD),
                DeckEntryEntity(deckId = izzetDeckId, cardId = "local-scalding-tarn", quantity = 4, zone = DeckZones.MAINBOARD),
                DeckEntryEntity(deckId = izzetDeckId, cardId = "local-island", quantity = 7, zone = DeckZones.MAINBOARD),
                DeckEntryEntity(deckId = izzetDeckId, cardId = "local-mountain", quantity = 5, zone = DeckZones.MAINBOARD),
                DeckEntryEntity(deckId = izzetDeckId, cardId = "local-blasphemous-act", quantity = 2, zone = DeckZones.SIDEBOARD),
                DeckEntryEntity(deckId = izzetDeckId, cardId = "local-the-one-ring", quantity = 2, zone = DeckZones.SIDEBOARD)
            )
            deckDao.insertEntries(izzetEntries)

            // Deck 3: Boros Sunforge Aggro (Casual / Modern - 60 cards)
            val borosDeckId = deckDao.insertDeck(
                DeckEntity(
                    name = "Boros Sunforge Burn",
                    format = "Casual",
                    description = "Explosive W/R prowess and direct burn shell with Monastery Mentor and Boros Charm.",
                    colorIdentity = "W,R",
                    notes = "Apply early pressure with Swiftspear and Thalia, hold Boros Charm for indestructible or 4-damage reach.",
                    createdAt = now - 259_200_000L,
                    updatedAt = now - 14_400_000L
                )
            )
            val borosEntries = listOf(
                DeckEntryEntity(deckId = borosDeckId, cardId = "local-monastery-swiftspear", quantity = 4, zone = DeckZones.MAINBOARD),
                DeckEntryEntity(deckId = borosDeckId, cardId = "local-esper-sentinel", quantity = 4, zone = DeckZones.MAINBOARD),
                DeckEntryEntity(deckId = borosDeckId, cardId = "local-thalia-guardian-of-thraben", quantity = 3, zone = DeckZones.MAINBOARD),
                DeckEntryEntity(deckId = borosDeckId, cardId = "local-monastery-mentor", quantity = 3, zone = DeckZones.MAINBOARD),
                DeckEntryEntity(deckId = borosDeckId, cardId = "local-lightning-bolt", quantity = 4, zone = DeckZones.MAINBOARD),
                DeckEntryEntity(deckId = borosDeckId, cardId = "local-lightning-helix", quantity = 4, zone = DeckZones.MAINBOARD),
                DeckEntryEntity(deckId = borosDeckId, cardId = "local-boros-charm", quantity = 4, zone = DeckZones.MAINBOARD),
                DeckEntryEntity(deckId = borosDeckId, cardId = "local-path-to-exile", quantity = 4, zone = DeckZones.MAINBOARD),
                DeckEntryEntity(deckId = borosDeckId, cardId = "local-swords-to-plowshares", quantity = 4, zone = DeckZones.MAINBOARD),
                DeckEntryEntity(deckId = borosDeckId, cardId = "local-skullclamp", quantity = 2, zone = DeckZones.MAINBOARD),
                DeckEntryEntity(deckId = borosDeckId, cardId = "local-sacred-foundry", quantity = 4, zone = DeckZones.MAINBOARD),
                DeckEntryEntity(deckId = borosDeckId, cardId = "local-arid-mesa", quantity = 4, zone = DeckZones.MAINBOARD),
                DeckEntryEntity(deckId = borosDeckId, cardId = "local-plains", quantity = 8, zone = DeckZones.MAINBOARD),
                DeckEntryEntity(deckId = borosDeckId, cardId = "local-mountain", quantity = 8, zone = DeckZones.MAINBOARD)
            )
            deckDao.insertEntries(borosEntries)
        }
    }
}
