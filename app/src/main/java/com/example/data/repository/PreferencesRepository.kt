package com.example.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.ThemeBrightnessMode
import com.example.ui.theme.WubrgTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.latticeDataStore: DataStore<Preferences> by preferencesDataStore(name = "lattice_prefs")

data class Appearance(val theme: WubrgTheme, val brightness: ThemeBrightnessMode)

class PreferencesRepository(private val context: Context) {
    private object Keys {
        val OFFLINE_ONLY_MODE = booleanPreferencesKey("offline_only_mode")
        val DEFAULT_LIFE = intPreferencesKey("default_life")
        val PLAYER_COUNT = intPreferencesKey("player_count")
        val WUBRG_THEME = stringPreferencesKey("wubrg_theme")
        val THEME_BRIGHTNESS = stringPreferencesKey("theme_brightness")
        val SHOW_CARD_ART = booleanPreferencesKey("show_card_art")
        val KEEP_SCREEN_AWAKE = booleanPreferencesKey("keep_screen_awake")
    }

    val offlineOnlyMode: Flow<Boolean> = context.latticeDataStore.data.map { prefs ->
        prefs[Keys.OFFLINE_ONLY_MODE] ?: false
    }

    val defaultLife: Flow<Int> = context.latticeDataStore.data.map { prefs ->
        prefs[Keys.DEFAULT_LIFE] ?: 40
    }

    val playerCount: Flow<Int> = context.latticeDataStore.data.map { prefs ->
        prefs[Keys.PLAYER_COUNT] ?: 2
    }

    val appearance: Flow<Appearance> = context.latticeDataStore.data.map { prefs ->
        Appearance(
            WubrgTheme.fromStorageKey(prefs[Keys.WUBRG_THEME]),
            ThemeBrightnessMode.fromStorageKey(prefs[Keys.THEME_BRIGHTNESS])
        )
    }

    val wubrgTheme: Flow<WubrgTheme> = appearance.map { it.theme }
    val themeBrightnessMode: Flow<ThemeBrightnessMode> =
        appearance.map { it.brightness }

    val showCardArt: Flow<Boolean> = context.latticeDataStore.data.map { prefs ->
        prefs[Keys.SHOW_CARD_ART] ?: true
    }

    val keepScreenAwake: Flow<Boolean> = context.latticeDataStore.data.map { prefs ->
        prefs[Keys.KEEP_SCREEN_AWAKE] ?: true
    }

    suspend fun setOfflineOnlyMode(enabled: Boolean) {
        context.latticeDataStore.edit { it[Keys.OFFLINE_ONLY_MODE] = enabled }
    }

    suspend fun setTabletopDefaults(life: Int, players: Int) {
        context.latticeDataStore.edit {
            it[Keys.DEFAULT_LIFE] = life
            it[Keys.PLAYER_COUNT] = players
        }
    }

    suspend fun setWubrgTheme(theme: WubrgTheme) {
        context.latticeDataStore.edit { it[Keys.WUBRG_THEME] = theme.storageKey }
    }

    suspend fun setThemeBrightnessMode(mode: ThemeBrightnessMode) {
        context.latticeDataStore.edit { it[Keys.THEME_BRIGHTNESS] = mode.storageKey }
    }

    suspend fun setShowCardArt(enabled: Boolean) {
        context.latticeDataStore.edit { it[Keys.SHOW_CARD_ART] = enabled }
    }

    suspend fun setKeepScreenAwake(enabled: Boolean) {
        context.latticeDataStore.edit { it[Keys.KEEP_SCREEN_AWAKE] = enabled }
    }

    private val catalogPackKey = stringSetPreferencesKey("offline_catalog_packs")

    val catalogPacks: Flow<Map<String, String>> = context.latticeDataStore.data.map { prefs ->
        prefs[catalogPackKey].orEmpty().associate { it.substringBefore('=') to it.substringAfter('=') }
    }

    suspend fun recordCatalogPack(id: String, sha256: String) {
        context.latticeDataStore.edit { prefs ->
            prefs[catalogPackKey] = prefs[catalogPackKey].orEmpty()
                .filterNot { it.substringBefore('=') == id }.toSet() + "$id=$sha256"
        }
    }

    suspend fun resetAllPreferences() {
        context.latticeDataStore.edit { prefs ->
            val packs = prefs[catalogPackKey]
            prefs.clear()
            if (packs != null) prefs[catalogPackKey] = packs
        }
    }
}
