package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        CardEntity::class,
        DeckEntity::class,
        DeckEntryEntity::class,
        ChatMessageEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class LatticeDatabase : RoomDatabase() {
    abstract fun cardDao(): CardDao
    abstract fun deckDao(): DeckDao
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var INSTANCE: LatticeDatabase? = null

        fun getInstance(context: Context): LatticeDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LatticeDatabase::class.java,
                    "lattice_mtg_offline.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
