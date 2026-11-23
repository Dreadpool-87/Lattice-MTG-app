package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CardEntity::class,
        DeckEntity::class,
        DeckEntryEntity::class,
        ChatMessageEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class LatticeDatabase : RoomDatabase() {
    abstract fun cardDao(): CardDao
    abstract fun deckDao(): DeckDao
    abstract fun chatDao(): ChatDao

    companion object {
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cards ADD COLUMN oracleId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE cards ADD COLUMN scryfallId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE cards ADD COLUMN collectorNumber TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE cards ADD COLUMN lang TEXT NOT NULL DEFAULT 'en'")
                db.execSQL("ALTER TABLE cards ADD COLUMN cardFacesJson TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE cards ADD COLUMN rulingsJson TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE cards ADD COLUMN metadataStatus TEXT NOT NULL DEFAULT 'VERIFIED'")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_cards_oracleId ON cards(oracleId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_cards_scryfallId ON cards(scryfallId)")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_cards_setCode_collectorNumber_lang ON cards(setCode, collectorNumber, lang)"
                )
                db.execSQL(
                    """
                    UPDATE cards
                    SET metadataStatus = 'UNRESOLVED', legalFormats = 'Unknown'
                    WHERE id LIKE 'offline-%'
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    UPDATE cards
                    SET metadataStatus = 'CUSTOM'
                    WHERE isCustom = 1 AND id NOT LIKE 'offline-%'
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    UPDATE cards
                    SET scryfallId = SUBSTR(id, 4)
                    WHERE id LIKE 'sf-%' AND scryfallId = ''
                    """.trimIndent()
                )
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN isPartialContext INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN contextScopeNote TEXT")
            }
        }

        @Volatile
        private var INSTANCE: LatticeDatabase? = null

        fun getInstance(context: Context): LatticeDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LatticeDatabase::class.java,
                    "lattice_mtg_offline.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
