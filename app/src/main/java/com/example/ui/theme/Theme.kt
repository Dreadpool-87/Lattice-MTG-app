package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class WubrgTheme(
    val storageKey: String,
    val manaCode: String,
    val displayName: String,
    val subtitle: String,
    val swatchHex: Color
) {
    WHITE(
        storageKey = "WHITE",
        manaCode = "W",
        displayName = "Plains Ivory",
        subtitle = "Radiant Gold & Alabaster",
        swatchHex = Color(0xFFF2D27A)
    ),
    BLUE(
        storageKey = "BLUE",
        manaCode = "U",
        displayName = "Island Arcane",
        subtitle = "Obsidian Azure & Mana Cyan",
        swatchHex = Color(0xFF5FA8FF)
    ),
    BLACK(
        storageKey = "BLACK",
        manaCode = "B",
        displayName = "Swamp Mire",
        subtitle = "Midnight Umber & Amethyst",
        swatchHex = Color(0xFFC084FC)
    ),
    RED(
        storageKey = "RED",
        manaCode = "R",
        displayName = "Mountain Forge",
        subtitle = "Basalt Ember & Molten Copper",
        swatchHex = Color(0xFFFF785A)
    ),
    GREEN(
        storageKey = "GREEN",
        manaCode = "G",
        displayName = "Forest Spark",
        subtitle = "Lattice Obsidian & Mint Spark",
        swatchHex = LatticeSparkGreen
    );

    companion object {
        fun fromStorageKey(key: String?): WubrgTheme {
            return entries.firstOrNull { it.storageKey.equals(key, ignoreCase = true) } ?: GREEN
        }
    }
}

enum class ThemeBrightnessMode(val storageKey: String, val label: String) {
    SYSTEM("SYSTEM", "System"),
    DARK("DARK", "Dark"),
    LIGHT("LIGHT", "Light");

    companion object {
        fun fromStorageKey(key: String?): ThemeBrightnessMode {
            return entries.firstOrNull { it.storageKey.equals(key, ignoreCase = true) } ?: SYSTEM
        }
    }
}

// === 1. WHITE (Plains / Radiant Dawn) ===
private val WhiteDarkColorScheme = darkColorScheme(
    primary = Color(0xFFF2D27A),
    onPrimary = Color(0xFF3E2D05),
    primaryContainer = Color(0xFF473712),
    onPrimaryContainer = Color(0xFFFFE59E),
    secondary = Color(0xFFDCD3BA),
    onSecondary = Color(0xFF2D281E),
    secondaryContainer = Color(0xFF332E22),
    onSecondaryContainer = Color(0xFFEDE4CD),
    tertiary = ManaWhiteBg,
    onTertiary = ManaWhiteFg,
    background = Color(0xFF161208),
    onBackground = Color(0xFFF7F2E4),
    surface = Color(0xFF211C12),
    onSurface = Color(0xFFF7F2E4),
    surfaceVariant = Color(0xFF2D2618),
    onSurfaceVariant = Color(0xFFB5AD99),
    surfaceContainerLow = Color(0xFF211C12),
    surfaceContainer = Color(0xFF292215),
    surfaceContainerHigh = Color(0xFF362C1B),
    surfaceContainerHighest = Color(0xFF453821),
    outline = Color(0xFF655334),
    outlineVariant = Color(0xFF413522),
    error = CrimsonEmber,
    onError = Color.White,
    errorContainer = CrimsonContainer,
    onErrorContainer = CrimsonEmber
)

private val WhiteLightColorScheme = lightColorScheme(
    primary = Color(0xFF8C6214),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF7E5BC),
    onPrimaryContainer = Color(0xFF543906),
    secondary = Color(0xFF576475),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0E7F0),
    onSecondaryContainer = Color(0xFF303B4A),
    tertiary = ManaWhiteBg,
    onTertiary = ManaWhiteFg,
    background = Color(0xFFFCF3DB),
    onBackground = Color(0xFF1F1B14),
    surface = Color(0xFFFFF9EB),
    onSurface = Color(0xFF1F1B14),
    surfaceVariant = Color(0xFFF5E6C1),
    onSurfaceVariant = Color(0xFF625B4C),
    surfaceContainerLow = Color(0xFFFFF9EB),
    surfaceContainer = Color(0xFFF5E7C6),
    surfaceContainerHigh = Color(0xFFECDDB4),
    surfaceContainerHighest = Color(0xFFE2CFA0),
    outline = Color(0xFFAE9870),
    outlineVariant = Color(0xFFDFCDA5),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B)
)

// === 2. BLUE (Island / Arcane Tide) ===
private val BlueDarkColorScheme = darkColorScheme(
    primary = Color(0xFF5FA8FF),
    onPrimary = Color(0xFF07264B),
    primaryContainer = Color(0xFF13335A),
    onPrimaryContainer = Color(0xFFC2DEFF),
    secondary = ArcaneCyan,
    onSecondary = ArcaneCyanDark,
    secondaryContainer = ArcaneCyanContainer,
    onSecondaryContainer = ArcaneCyan,
    tertiary = BurnishedGold,
    onTertiary = BurnishedGoldDark,
    background = Color(0xFF081524),
    onBackground = ParchmentText,
    surface = Color(0xFF0D2036),
    onSurface = ParchmentText,
    surfaceVariant = Color(0xFF132D47),
    onSurfaceVariant = SlateMuted,
    surfaceContainerLow = Color(0xFF0D2036),
    surfaceContainer = Color(0xFF142B45),
    surfaceContainerHigh = Color(0xFF1A3857),
    surfaceContainerHighest = Color(0xFF224669),
    outline = Color(0xFF3D6182),
    outlineVariant = Color(0xFF243E5A),
    error = CrimsonEmber,
    onError = Color.White,
    errorContainer = CrimsonContainer,
    onErrorContainer = CrimsonEmber
)

private val BlueLightColorScheme = lightColorScheme(
    primary = Color(0xFF1658B8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E6FF),
    onPrimaryContainer = Color(0xFF0A326E),
    secondary = Color(0xFF0E7490),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFF0F7),
    onSecondaryContainer = Color(0xFF084B5E),
    tertiary = ManaBlueBg,
    onTertiary = ManaBlueFg,
    background = Color(0xFFE6F1FF),
    onBackground = Color(0xFF111927),
    surface = Color(0xFFF6FAFF),
    onSurface = Color(0xFF111927),
    surfaceVariant = Color(0xFFD6E7FF),
    onSurfaceVariant = Color(0xFF4B5B73),
    surfaceContainerLow = Color(0xFFF6FAFF),
    surfaceContainer = Color(0xFFDAE9FF),
    surfaceContainerHigh = Color(0xFFCFE1F9),
    surfaceContainerHighest = Color(0xFFC1D8F5),
    outline = Color(0xFF8FAAD0),
    outlineVariant = Color(0xFFBDD2EF),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B)
)

// === 3. BLACK (Swamp / Phyrexian Mire) ===
private val BlackDarkColorScheme = darkColorScheme(
    primary = Color(0xFFC084FC),
    onPrimary = Color(0xFF2F0F4D),
    primaryContainer = Color(0xFF391D54),
    onPrimaryContainer = Color(0xFFE9D5FF),
    secondary = Color(0xFFD4C8E8),
    onSecondary = Color(0xFF272133),
    secondaryContainer = Color(0xFF2D253B),
    onSecondaryContainer = Color(0xFFEAE2F8),
    tertiary = ManaBlackBg,
    onTertiary = ManaBlackFg,
    background = Color(0xFF100B12),
    onBackground = Color(0xFFF2ECF8),
    surface = Color(0xFF1E1222),
    onSurface = Color(0xFFF2ECF8),
    surfaceVariant = Color(0xFF2D1B31),
    onSurfaceVariant = Color(0xFFA79BB8),
    surfaceContainerLow = Color(0xFF1E1222),
    surfaceContainer = Color(0xFF29182E),
    surfaceContainerHigh = Color(0xFF35213C),
    surfaceContainerHighest = Color(0xFF442A4B),
    outline = Color(0xFF634568),
    outlineVariant = Color(0xFF402944),
    error = CrimsonEmber,
    onError = Color.White,
    errorContainer = CrimsonContainer,
    onErrorContainer = CrimsonEmber
)

private val BlackLightColorScheme = lightColorScheme(
    primary = Color(0xFF6D28D9),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE0FF),
    onPrimaryContainer = Color(0xFF3B0D82),
    secondary = Color(0xFF4F4663),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE7E2F2),
    onSecondaryContainer = Color(0xFF2E273D),
    tertiary = ManaBlackBg,
    onTertiary = ManaBlackFg,
    background = Color(0xFFF2E8F9),
    onBackground = Color(0xFF1B1624),
    surface = Color(0xFFFCF6FF),
    onSurface = Color(0xFF1B1624),
    surfaceVariant = Color(0xFFEBDCF2),
    onSurfaceVariant = Color(0xFF5D536E),
    surfaceContainerLow = Color(0xFFFCF6FF),
    surfaceContainer = Color(0xFFEDE0F4),
    surfaceContainerHigh = Color(0xFFE4D3EE),
    surfaceContainerHighest = Color(0xFFD9C4E7),
    outline = Color(0xFFAE94B8),
    outlineVariant = Color(0xFFD4C0E1),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B)
)

// === 4. RED (Mountain / Dragonfire Forge) ===
private val RedDarkColorScheme = darkColorScheme(
    primary = Color(0xFFFF785A),
    onPrimary = Color(0xFF471105),
    primaryContainer = Color(0xFF541B0F),
    onPrimaryContainer = Color(0xFFFFD6CC),
    secondary = Color(0xFFF5B841),
    onSecondary = Color(0xFF3D2903),
    secondaryContainer = Color(0xFF45310B),
    onSecondaryContainer = Color(0xFFFFE5AE),
    tertiary = ManaRedBg,
    onTertiary = ManaRedFg,
    background = Color(0xFF140D0C),
    onBackground = Color(0xFFFAEEEB),
    surface = Color(0xFF1F1412),
    onSurface = Color(0xFFFAEEEB),
    surfaceVariant = Color(0xFF2C1D1A),
    onSurfaceVariant = Color(0xFFBDA19B),
    surfaceContainerLow = Color(0xFF1F1412),
    surfaceContainer = Color(0xFF281B18),
    surfaceContainerHigh = Color(0xFF342420),
    surfaceContainerHighest = Color(0xFF402C27),
    outline = Color(0xFF593D37),
    outlineVariant = Color(0xFF3D2925),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF5C1919),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val RedLightColorScheme = lightColorScheme(
    primary = Color(0xFFB93822),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDDD6),
    onPrimaryContainer = Color(0xFF63170A),
    secondary = Color(0xFFB45309),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFEF0D1),
    onSecondaryContainer = Color(0xFF662E03),
    tertiary = ManaRedBg,
    onTertiary = ManaRedFg,
    background = Color(0xFFFCEBE4),
    onBackground = Color(0xFF241614),
    surface = Color(0xFFFFF7F3),
    onSurface = Color(0xFF241614),
    surfaceVariant = Color(0xFFF8DFD4),
    onSurfaceVariant = Color(0xFF6E534C),
    surfaceContainerLow = Color(0xFFFFF7F3),
    surfaceContainer = Color(0xFFF8E1D8),
    surfaceContainerHigh = Color(0xFFF3D7CA),
    surfaceContainerHighest = Color(0xFFEDCABC),
    outline = Color(0xFFBE9888),
    outlineVariant = Color(0xFFE9CBBD),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B)
)

// === 5. GREEN (Forest / Official Lattice Spark #101714 & #ADECAD) ===
private val GreenDarkColorScheme = darkColorScheme(
    primary = LatticeSparkGreen,
    onPrimary = LatticeForestBase,
    primaryContainer = LatticeSparkContainer,
    onPrimaryContainer = LatticeSparkGreen,
    secondary = BurnishedGold,
    onSecondary = BurnishedGoldDark,
    secondaryContainer = BurnishedGoldContainer,
    onSecondaryContainer = BurnishedGold,
    tertiary = ArcaneCyan,
    onTertiary = ArcaneCyanDark,
    background = Color(0xFF091810),
    onBackground = Color(0xFFF1F6F1),
    surface = Color(0xFF102419),
    onSurface = Color(0xFFF1F6F1),
    surfaceVariant = Color(0xFF183324),
    onSurfaceVariant = Color(0xFF9EB3A2),
    surfaceContainerLow = Color(0xFF102419),
    surfaceContainer = Color(0xFF163021),
    surfaceContainerHigh = Color(0xFF1C3C28),
    surfaceContainerHighest = Color(0xFF244C34),
    outline = Color(0xFF3E6A4B),
    outlineVariant = Color(0xFF264632),
    error = CrimsonEmber,
    onError = Color.White,
    errorContainer = CrimsonContainer,
    onErrorContainer = CrimsonEmber
)

private val GreenLightColorScheme = lightColorScheme(
    primary = Color(0xFF1E6B3A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1F2D8),
    onPrimaryContainer = Color(0xFF0E3E20),
    secondary = DeepTealSecondary,
    onSecondary = Color.White,
    secondaryContainer = DeepTealContainer,
    onSecondaryContainer = DeepTealSecondary,
    tertiary = BronzePrimary,
    onTertiary = Color.White,
    background = Color(0xFFE4F3E4),
    onBackground = Color(0xFF121D15),
    surface = Color(0xFFF5FCF4),
    onSurface = Color(0xFF121D15),
    surfaceVariant = Color(0xFFD7EED9),
    onSurfaceVariant = Color(0xFF4B6150),
    surfaceContainerLow = Color(0xFFF5FCF4),
    surfaceContainer = Color(0xFFDCEEDC),
    surfaceContainerHigh = Color(0xFFCEE8D0),
    surfaceContainerHighest = Color(0xFFC1DFC4),
    outline = Color(0xFF90B296),
    outlineVariant = Color(0xFFBFDCC2),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B)
)

fun colorSchemeForWubrg(wubrgTheme: WubrgTheme, isDark: Boolean): ColorScheme {
    return when (wubrgTheme) {
        WubrgTheme.WHITE -> if (isDark) WhiteDarkColorScheme else WhiteLightColorScheme
        WubrgTheme.BLUE -> if (isDark) BlueDarkColorScheme else BlueLightColorScheme
        WubrgTheme.BLACK -> if (isDark) BlackDarkColorScheme else BlackLightColorScheme
        WubrgTheme.RED -> if (isDark) RedDarkColorScheme else RedLightColorScheme
        WubrgTheme.GREEN -> if (isDark) GreenDarkColorScheme else GreenLightColorScheme
    }
}

val LatticeShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun LatticeMtgTheme(
    wubrgTheme: WubrgTheme = WubrgTheme.GREEN,
    brightnessMode: ThemeBrightnessMode = ThemeBrightnessMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (brightnessMode) {
        ThemeBrightnessMode.SYSTEM -> systemDark
        ThemeBrightnessMode.DARK -> true
        ThemeBrightnessMode.LIGHT -> false
    }
    val colorScheme = colorSchemeForWubrg(wubrgTheme = wubrgTheme, isDark = useDark)
    MaterialTheme(
        colorScheme = colorScheme,
        typography = LatticeTypography,
        shapes = LatticeShapes,
        content = content
    )
}
