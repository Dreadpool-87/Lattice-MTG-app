package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.R
import com.example.ui.theme.LatticeForestBase
import com.example.ui.theme.LatticeSparkGreen
import kotlinx.coroutines.launch

private data class LicenseEntry(
    val id: String,
    val library: String,
    val author: String,
    val licenseName: String,
    val summary: String
)

private val openSourceLicenses = listOf(
    LicenseEntry(
        id = "androidx_jetpack",
        library = "AndroidX, Jetpack Compose & Material 3",
        author = "Google LLC / Android Open Source Project",
        licenseName = "Apache License 2.0",
        summary = "UI toolkit, Navigation Compose, Room SQLite persistence, DataStore Preferences, Lifecycle ViewModels, and Material Icons."
    ),
    LicenseEntry(
        id = "kotlin_jetbrains",
        library = "Kotlin Standard Library & Coroutines",
        author = "JetBrains s.r.o.",
        licenseName = "Apache License 2.0",
        summary = "Kotlin language runtime, structured concurrency, StateFlow reactive streams, and Kotlin Symbol Processing (KSP)."
    ),
    LicenseEntry(
        id = "square_net",
        library = "Retrofit, OkHttp & Moshi",
        author = "Square, Inc.",
        licenseName = "Apache License 2.0",
        summary = "Type-safe Scryfall HTTP client, connection pooling, and JSON adapter serialization for offline caching."
    ),
    LicenseEntry(
        id = "coil",
        library = "Coil Image Loading",
        author = "Coil Contributors",
        licenseName = "Apache License 2.0",
        summary = "Coroutine-backed asynchronous card artwork loading and disk/memory image caching."
    ),
    LicenseEntry(
        id = "firebase_sdk",
        library = "Firebase AI Logic & App Check Android SDK",
        author = "Google LLC",
        licenseName = "Apache License 2.0",
        summary = "Provides secure Gemini API inference and reCAPTCHA / debug App Check attestation for Lattice Oracle."
    ),
    LicenseEntry(
        id = "sil_fonts",
        library = "Cinzel, Plus Jakarta Sans & JetBrains Mono",
        author = "Natanael Gama • Tokotype • JetBrains",
        licenseName = "SIL Open Font License 1.1",
        summary = "Bundled typography families used for arcane display titles, readable UI body copy, and monospace mana/probability badges."
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var expandedLicenseId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.about_title),
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = stringResource(R.string.about_subtitle),
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
                            .testTag("about_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to settings"
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
                // 1. App Identity Hero Card
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, LatticeSparkGreen.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                        ) {
                            Surface(
                                color = LatticeForestBase,
                                shape = RoundedCornerShape(18.dp),
                                border = BorderStroke(1.5.dp, LatticeSparkGreen),
                                modifier = Modifier.size(68.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Image(
                                        painter = painterResource(id = R.drawable.lattice_logo),
                                        contentDescription = "Lattice MTG Logo",
                                        modifier = Modifier.size(44.dp)
                                    )
                                }
                            }

                            Text(
                                text = "LATTICE MTG",
                                style = MaterialTheme.typography.headlineMedium
                            )
                            Text(
                                text = "Version ${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "An offline-first Magic: The Gathering deckbuilding studio, card vault, synergy analyzer, tabletop life tracker, and rules Oracle.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            OutlinedButton(
                                onClick = {
                                    val info = "Lattice MTG v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\nPackage: ${BuildConfig.APPLICATION_ID}"
                                    clipboard.setText(AnnotatedString(info))
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Copied app version details.")
                                    }
                                },
                                modifier = Modifier.testTag("copy_version_info_button")
                            ) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Copy Version Info")
                            }
                        }
                    }
                }

                // 2. Credits Section
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.Groups,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Credits & Attributions", style = MaterialTheme.typography.titleMedium)
                            }

                            CreditRow(
                                role = "Project & Lattice Spark Brand",
                                detail = "Dreadpool-87 • Lattice MTG (dreadpool-87.github.io/Lattice-MTG)"
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            CreditRow(
                                role = "Card Metadata & Oracle Archive",
                                detail = "Scryfall API (scryfall.com) — optional online sync & card artwork"
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            CreditRow(
                                role = "MTG Rules & Deck Strategist",
                                detail = "Lattice Oracle powered by Firebase AI Logic & Google App Check"
                            )
                        }
                    }
                }

                // 3. Wizards of the Coast Fan Content Policy Notice
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.Gavel,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Wizards of the Coast Fan Content Policy",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            Text(
                                text = "Lattice MTG is unofficial Fan Content permitted under the Wizards of the Coast Fan Content Policy. Not approved or endorsed by Wizards. Portions of the materials used are property of Wizards of the Coast LLC. © Wizards of the Coast LLC.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Magic: The Gathering, mana symbols, and card names are trademarks of Wizards of the Coast LLC in the USA and other countries.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 4. Open-Source & Font Licenses
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.MenuBook,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Open-Source & Font Licenses",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }

                items(openSourceLicenses, key = { it.id }) { entry ->
                    val expanded = expandedLicenseId == entry.id
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                expandedLicenseId = if (expanded) null else entry.id
                            }
                            .testTag("license_card_${entry.id}")
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = entry.library,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "${entry.author} • ${entry.licenseName}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Icon(
                                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                    contentDescription = if (expanded) "Collapse license" else "Expand license",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            AnimatedVisibility(visible = expanded) {
                                Column {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = entry.summary,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreditRow(role: String, detail: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = role,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.secondary
        )
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
