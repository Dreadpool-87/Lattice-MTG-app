package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ai.configureAppCheck
import com.example.ui.screens.AboutScreen
import com.example.ui.screens.DeckDetailScreen
import com.example.ui.screens.DecksScreen
import com.example.ui.screens.OracleChatScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SynergyAnalyzerScreen
import com.example.ui.screens.TabletopScreen
import com.example.ui.screens.VaultScreen
import com.example.ui.theme.LatticeMtgTheme
import com.example.ui.theme.ThemeBrightnessMode
import com.example.ui.theme.WubrgTheme
import com.example.ui.viewmodel.DeckDetailViewModel
import com.example.ui.viewmodel.DecksViewModel
import com.example.ui.viewmodel.OracleChatViewModel
import com.example.ui.viewmodel.SettingsViewModel
import com.example.ui.viewmodel.SynergyAnalyzerViewModel
import com.example.ui.viewmodel.TabletopViewModel
import com.example.ui.viewmodel.VaultViewModel

object Routes {
    const val DECKS = "decks"
    const val SYNERGY = "synergy"
    const val VAULT = "vault"
    const val ORACLE = "oracle"
    const val TABLETOP = "tabletop"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val DECK_DETAIL = "deck/{deckId}"

    fun deckDetail(deckId: Long) = "deck/$deckId"
}

private data class NavTabItem(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent before any UI or Firebase operations
        configureAppCheck(this, intent)

        // STEP 2: Enable Edge-to-Edge and render Compose UI
        enableEdgeToEdge()
        val container = (application as LatticeApp).container

        setContent {
            val wubrgTheme by container.preferencesRepository.wubrgTheme
                .collectAsStateWithLifecycle(initialValue = WubrgTheme.GREEN)
            val brightnessMode by container.preferencesRepository.themeBrightnessMode
                .collectAsStateWithLifecycle(initialValue = ThemeBrightnessMode.SYSTEM)

            LatticeMtgTheme(
                wubrgTheme = wubrgTheme,
                brightnessMode = brightnessMode
            ) {
                LatticeRootApp(container = container)
            }
        }
    }
}

@Composable
fun LatticeRootApp(container: AppContainer) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val decksViewModel: DecksViewModel = viewModel(
        factory = DecksViewModel.Factory(
            deckRepository = container.deckRepository,
            cardRepository = container.cardRepository,
            networkMonitor = container.networkMonitor,
            preferencesRepository = container.preferencesRepository
        )
    )
    val vaultViewModel: VaultViewModel = viewModel(
        factory = VaultViewModel.Factory(
            cardRepository = container.cardRepository,
            deckRepository = container.deckRepository,
            networkMonitor = container.networkMonitor,
            preferencesRepository = container.preferencesRepository
        )
    )
    val oracleViewModel: OracleChatViewModel = viewModel(
        factory = OracleChatViewModel.Factory(
            chatDao = container.chatDao,
            deckRepository = container.deckRepository,
            cardRepository = container.cardRepository,
            networkMonitor = container.networkMonitor,
            preferencesRepository = container.preferencesRepository
        )
    )
    val tabletopViewModel: TabletopViewModel = viewModel(
        factory = TabletopViewModel.Factory(
            cardRepository = container.cardRepository,
            deckRepository = container.deckRepository,
            networkMonitor = container.networkMonitor,
            preferencesRepository = container.preferencesRepository
        )
    )
    val synergyViewModel: SynergyAnalyzerViewModel = viewModel(
        factory = SynergyAnalyzerViewModel.Factory(
            deckRepository = container.deckRepository
        )
    )
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.Factory(
            preferencesRepository = container.preferencesRepository,
            cardRepository = container.cardRepository,
            deckRepository = container.deckRepository,
            chatDao = container.chatDao,
            networkMonitor = container.networkMonitor
        )
    )

    val tabs = remember {
        listOf(
            NavTabItem(
                route = Routes.DECKS,
                labelRes = R.string.nav_decks,
                selectedIcon = Icons.Filled.Style,
                unselectedIcon = Icons.Outlined.Style,
                testTag = "nav_tab_decks"
            ),
            NavTabItem(
                route = Routes.SYNERGY,
                labelRes = R.string.nav_synergy,
                selectedIcon = Icons.Filled.Hub,
                unselectedIcon = Icons.Outlined.Hub,
                testTag = "nav_tab_synergy"
            ),
            NavTabItem(
                route = Routes.VAULT,
                labelRes = R.string.nav_vault,
                selectedIcon = Icons.Filled.LibraryBooks,
                unselectedIcon = Icons.Outlined.LibraryBooks,
                testTag = "nav_tab_vault"
            ),
            NavTabItem(
                route = Routes.ORACLE,
                labelRes = R.string.nav_oracle,
                selectedIcon = Icons.Filled.AutoAwesome,
                unselectedIcon = Icons.Outlined.AutoAwesome,
                testTag = "nav_tab_oracle"
            ),
            NavTabItem(
                route = Routes.TABLETOP,
                labelRes = R.string.nav_tabletop,
                selectedIcon = Icons.Filled.Casino,
                unselectedIcon = Icons.Outlined.Casino,
                testTag = "nav_tab_tabletop"
            )
        )
    }

    val isTopLevelRoute = currentRoute in setOf(
        Routes.DECKS,
        Routes.SYNERGY,
        Routes.VAULT,
        Routes.ORACLE,
        Routes.TABLETOP
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useNavRail && isTopLevelRoute) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        tabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(Routes.DECKS) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = {
                                    val labelRes = if (tab.route == Routes.VAULT) R.string.nav_vault_compact else tab.labelRes
                                    Text(
                                        text = stringResource(labelRes),
                                        style = MaterialTheme.typography.labelSmall,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail && isTopLevelRoute) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        tabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(Routes.DECKS) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = Routes.DECKS,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(Routes.DECKS) {
                        DecksScreen(
                            viewModel = decksViewModel,
                            snackbarHostState = snackbarHostState,
                            onOpenDeck = { deckId ->
                                navController.navigate(Routes.deckDetail(deckId))
                            },
                            onOpenSettings = { navController.navigate(Routes.SETTINGS) }
                        )
                    }

                    composable(Routes.SYNERGY) {
                        SynergyAnalyzerScreen(
                            viewModel = synergyViewModel,
                            onAskOracleForDeck = { deckId ->
                                oracleViewModel.selectContextDeck(deckId)
                                navController.navigate(Routes.ORACLE) {
                                    popUpTo(Routes.DECKS) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            onOpenDeck = { deckId ->
                                navController.navigate(Routes.deckDetail(deckId))
                            },
                            onOpenSettings = { navController.navigate(Routes.SETTINGS) }
                        )
                    }

                    composable(Routes.VAULT) {
                        VaultScreen(
                            viewModel = vaultViewModel,
                            snackbarHostState = snackbarHostState,
                            onOpenSettings = { navController.navigate(Routes.SETTINGS) }
                        )
                    }

                    composable(Routes.ORACLE) {
                        OracleChatScreen(
                            viewModel = oracleViewModel,
                            onOpenSettings = { navController.navigate(Routes.SETTINGS) }
                        )
                    }

                    composable(Routes.TABLETOP) {
                        TabletopScreen(
                            viewModel = tabletopViewModel,
                            snackbarHostState = snackbarHostState,
                            onOpenSettings = { navController.navigate(Routes.SETTINGS) }
                        )
                    }

                    composable(Routes.SETTINGS) {
                        SettingsScreen(
                            viewModel = settingsViewModel,
                            snackbarHostState = snackbarHostState,
                            onBack = { navController.popBackStack() },
                            onOpenAbout = { navController.navigate(Routes.ABOUT) }
                        )
                    }

                    composable(Routes.ABOUT) {
                        AboutScreen(
                            snackbarHostState = snackbarHostState,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(
                        route = Routes.DECK_DETAIL,
                        arguments = listOf(navArgument("deckId") { type = NavType.LongType })
                    ) { entry ->
                        val deckId = entry.arguments?.getLong("deckId") ?: 0L
                        val detailVm: DeckDetailViewModel = viewModel(
                            key = "deck_detail_$deckId",
                            factory = DeckDetailViewModel.Factory(
                                deckId = deckId,
                                deckRepository = container.deckRepository,
                                cardRepository = container.cardRepository
                            )
                        )
                        DeckDetailScreen(
                            viewModel = detailVm,
                            snackbarHostState = snackbarHostState,
                            onBack = { navController.popBackStack() },
                            onAskOracleAboutDeck = { id ->
                                oracleViewModel.selectContextDeck(id)
                                navController.navigate(Routes.ORACLE) {
                                    popUpTo(Routes.DECKS) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            onOpenSynergyAnalyzer = { id ->
                                synergyViewModel.selectDeck(id)
                                navController.navigate(Routes.SYNERGY) {
                                    popUpTo(Routes.DECKS) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
