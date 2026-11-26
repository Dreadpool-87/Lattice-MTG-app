package com.example

import android.content.Context
import androidx.compose.runtime.Composable
import com.example.data.local.LatticeDatabase
import com.example.data.repository.DeckRepository
import com.example.data.repository.OfflineCatalogRepository

internal object PreviewTools {
    suspend fun prepare(context: Context, database: LatticeDatabase, decks: DeckRepository) = Unit

    @Composable
    fun Settings(context: Context, database: LatticeDatabase, decks: DeckRepository, catalog: OfflineCatalogRepository) = Unit
}
