package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.R
import com.example.ui.theme.ThemeBrightnessMode
import com.example.ui.theme.WubrgTheme
import com.example.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showClearOracleDialog by remember { mutableStateOf(false) }
    var showResetPrefsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.statusMessage) {
        val msg = uiState.statusMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.settings_title),
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = stringResource(R.string.settings_subtitle),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp)
            ) {
                item { AppUpdateSettings() }

                // 1. WUBRG 5-Theme Selector & Brightness Mode
                item {
                    SettingsSectionCard(
                        title = "WUBRG Mana Theme",
                        icon = Icons.Filled.Palette
                    ) {
                        Text(
                            text = "Choose one of the 5 Magic mana themes for the entire app:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            WubrgTheme.entries.forEach { theme ->
                                WubrgThemeOptionCard(
                                    theme = theme,
                                    selected = uiState.wubrgTheme == theme,
                                    onClick = { viewModel.selectWubrgTheme(theme) }
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.Brightness6,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Brightness Mode", style = MaterialTheme.typography.titleSmall)
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ThemeBrightnessMode.entries.forEach { mode ->
                                val selected = uiState.brightnessMode == mode
                                FilterChip(
                                    selected = selected,
                                    onClick = { viewModel.selectBrightnessMode(mode) },
                                    label = { Text(mode.label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("brightness_chip_${mode.storageKey.lowercase()}")
                                )
                            }
                        }

                        SettingsSwitchRow(
                            title = "Show Card Art Previews",
                            subtitle = "Render Scryfall card illustrations in vault & inspectors when cached or online.",
                            checked = uiState.showCardArt,
                            onCheckedChange = viewModel::setShowCardArt,
                            testTag = "setting_show_card_art_switch"
                        )
                    }
                }

                // 2. Tabletop & Gameplay Defaults
                item {
                    SettingsSectionCard(
                        title = "Tabletop & Playtest Defaults",
                        icon = Icons.Filled.ScreenLockPortrait
                    ) {
                        Text(
                            text = "Default Starting Match Format",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            FilterChip(
                                selected = uiState.playerCount == 2 && uiState.defaultLife == 20,
                                onClick = { viewModel.setTabletopDefaults(20, 2) },
                                label = { Text("1v1 • 20 Life") },
                                modifier = Modifier.testTag("preset_1v1_20")
                            )
                            FilterChip(
                                selected = uiState.playerCount == 2 && uiState.defaultLife == 40,
                                onClick = { viewModel.setTabletopDefaults(40, 2) },
                                label = { Text("Commander 2P • 40") },
                                modifier = Modifier.testTag("preset_2p_40")
                            )
                            FilterChip(
                                selected = uiState.playerCount == 4 && uiState.defaultLife == 40,
                                onClick = { viewModel.setTabletopDefaults(40, 4) },
                                label = { Text("Pod 4P • 40") },
                                modifier = Modifier.testTag("preset_4p_40")
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        SettingsSwitchRow(
                            title = "Keep Screen Awake on Tabletop",
                            subtitle = "Prevent the display from sleeping while tracking life totals.",
                            checked = uiState.keepScreenAwake,
                            onCheckedChange = viewModel::setKeepScreenAwake,
                            testTag = "setting_keep_awake_switch"
                        )
                    }
                }

                // 3. Offline Vault & Sync Controls
                item {
                    SettingsSectionCard(
                        title = "Offline Vault & Sync",
                        icon = Icons.Filled.Storage
                    ) {
                        SettingsSwitchRow(
                            title = "Offline-Only Mode",
                            subtitle = if (uiState.isOnline) {
                                "Network detected, blocking Scryfall lookups while enabled."
                            } else {
                                "Device offline. All decks and Room card search stay local."
                            },
                            checked = uiState.offlineOnlyMode,
                            onCheckedChange = viewModel::setOfflineOnlyMode,
                            testTag = "setting_offline_only_switch"
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            SynergyMetricTile(
                                label = "Vault Cards",
                                value = uiState.cachedCardCount.toString(),
                                accent = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            SynergyMetricTile(
                                label = "Saved Decks",
                                value = uiState.deckCount.toString(),
                                accent = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.weight(1f)
                            )
                            SynergyMetricTile(
                                label = "Oracle Turns",
                                value = uiState.oracleMessageCount.toString(),
                                accent = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = viewModel::restoreStarterCatalog,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("setting_verify_db_button")
                            ) {
                                Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Verify Base DB", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }

                        }
                    }
                }

                // 4. Oracle AI & Data Management
                item {
                    SettingsSectionCard(
                        title = "Oracle & App Data",
                        icon = Icons.Filled.AutoAwesome
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = { showClearOracleDialog = true },
                                enabled = uiState.oracleMessageCount > 0,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("setting_clear_oracle_button")
                            ) {
                                Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Clear Oracle", maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = { showResetPrefsDialog = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("setting_reset_prefs_button")
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Reset Prefs", maxLines = 1)
                            }
                        }
                    }
                }

                // 5. About, Credits & Licensing Entry
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenAbout)
                            .testTag("open_about_screen_card")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Filled.Info,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "About, Credits & Licensing",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = "Lattice MTG v${BuildConfig.VERSION_NAME} • WotC Fan Policy & Open-Source Notices",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Open About",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    SettingsConfirmationDialogs(
        showClearOracleDialog = showClearOracleDialog,
        onDismissClearOracle = { showClearOracleDialog = false },
        onConfirmClearOracle = {
            showClearOracleDialog = false
            viewModel.clearOracleHistory()
        },
        showResetPrefsDialog = showResetPrefsDialog,
        onDismissResetPrefs = { showResetPrefsDialog = false },
        onConfirmResetPrefs = {
            showResetPrefsDialog = false
            viewModel.resetPreferences()
        }
    )
}
