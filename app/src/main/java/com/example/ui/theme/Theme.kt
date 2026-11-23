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
    background = Color(0xFF13120E),
    onBackground = Color(0xFFF7F2E4),
    surface = Color(0xFF1B1914),
    onSurface = Color(0xFFF7F2E4),
    surfaceVariant = Color(0xFF25221B),
    onSurfaceVariant = Color(0xFFB5AD99),
    surfaceContainerLow = Color(0xFF1B1914),
    surfaceContainer = Color(0xFF232019),
    surfaceContainerHigh = Color(0xFF2D2920),
    surfaceContainerHighest = Color(0xFF363127),
    outline = Color(0xFF534C3D),
    outlineVariant = Color(0xFF353026),
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
    background = Color(0xFFFAF7EE),
    onBackground = Color(0xFF1F1B14),
    surface = Color(0xFFFFFCF5),
    onSurface = Color(0xFF1F1B14),
    surfaceVariant = Color(0xFFF1EBD9),
    onSurfaceVariant = Color(0xFF625B4C),
    surfaceContainerLow = Color(0xFFFFFCF5),
    surfaceContainer = Color(0xFFF2ECE0),
    surfaceContainerHigh = Color(0xFFE8E0CE),
    surfaceContainerHighest = Color(0xFFDDD4C0),
    outline = Color(0xFFD2C7AF),
    outlineVariant = Color(0xFFE4DBC7),
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
    background = ObsidianBg,
    onBackground = ParchmentText,
    surface = ObsidianSurface,
    onSurface = ParchmentText,
    surfaceVariant = ObsidianSurfaceHigh,
    onSurfaceVariant = SlateMuted,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = ObsidianSurfaceHigh,
    surfaceContainerHigh = ObsidianSurfaceHighest,
    surfaceContainerHighest = Color(0xFF2D3C58),
    outline = ObsidianOutline,
    outlineVariant = ObsidianOutlineVariant,
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
    background = Color(0xFFF1F6FC),
    onBackground = Color(0xFF111927),
    surface = Color(0xFFFAFDFE),
    onSurface = Color(0xFF111927),
    surfaceVariant = Color(0xFFE4ECF7),
    onSurfaceVariant = Color(0xFF4B5B73),
    surfaceContainerLow = Color(0xFFFAFDFE),
    surfaceContainer = Color(0xFFE7EFFA),
    surfaceContainerHigh = Color(0xFFDCE7F5),
    surfaceContainerHighest = Color(0xFFD0DDEF),
    outline = Color(0xFFB7C7DF),
    outlineVariant = Color(0xFFD5E1F0),
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
    background = Color(0xFF0F0C14),
    onBackground = Color(0xFFF2ECF8),
    surface = Color(0xFF17131F),
    onSurface = Color(0xFFF2ECF8),
    surfaceVariant = Color(0xFF221C2E),
    onSurfaceVariant = Color(0xFFA79BB8),
    surfaceContainerLow = Color(0xFF17131F),
    surfaceContainer = Color(0xFF201A2B),
    surfaceContainerHigh = Color(0xFF2A2338),
    surfaceContainerHighest = Color(0xFF342C44),
    outline = Color(0xFF493E5E),
    outlineVariant = Color(0xFF312940),
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
    background = Color(0xFFF6F2FA),
    onBackground = Color(0xFF1B1624),
    surface = Color(0xFFFCF9FF),
    onSurface = Color(0xFF1B1624),
    surfaceVariant = Color(0xFFEBE4F3),
    onSurfaceVariant = Color(0xFF5D536E),
    surfaceContainerLow = Color(0xFFFCF9FF),
    surfaceContainer = Color(0xFFEEE8F6),
    surfaceContainerHigh = Color(0xFFE3DCEF),
    surfaceContainerHighest = Color(0xFFD8CFE6),
    outline = Color(0xFFC6BAD9),
    outlineVariant = Color(0xFFE0D7ED),
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
    background = Color(0xFFFBF4F1),
    onBackground = Color(0xFF241614),
    surface = Color(0xFFFFFBF9),
    onSurface = Color(0xFF241614),
    surfaceVariant = Color(0xFFF4E4DD),
    onSurfaceVariant = Color(0xFF6E534C),
    surfaceContainerLow = Color(0xFFFFFBF9),
    surfaceContainer = Color(0xFFF6E8E2),
    surfaceContainerHigh = Color(0xFFECDBD4),
    surfaceContainerHighest = Color(0xFFE3CEC6),
    outline = Color(0xFFD6BCB2),
    outlineVariant = Color(0xFFEAD7CF),
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
    background = LatticeForestBase,
    onBackground = Color(0xFFF1F6F1),
    surface = Color(0xFF151F1A),
    onSurface = Color(0xFFF1F6F1),
    surfaceVariant = Color(0xFF1C2922),
    onSurfaceVariant = Color(0xFF9EB3A2),
    surfaceContainerLow = Color(0xFF151F1A),
    surfaceContainer = Color(0xFF1A2720),
    surfaceContainerHigh = Color(0xFF22332A),
    surfaceContainerHighest = Color(0xFF2B4035),
    outline = Color(0xFF395244),
    outlineVariant = Color(0xFF25372D),
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
    background = Color(0xFFF2F8F3),
    onBackground = Color(0xFF121D15),
    surface = Color(0xFFFAFDFA),
    onSurface = Color(0xFF121D15),
    surfaceVariant = Color(0xFFE1EEE3),
    onSurfaceVariant = Color(0xFF4B6150),
    surfaceContainerLow = Color(0xFFFAFDFA),
    surfaceContainer = Color(0xFFE6F1E8),
    surfaceContainerHigh = Color(0xFFD9E8DC),
    surfaceContainerHighest = Color(0xFFCCE0D0),
    outline = Color(0xFFB3CBB8),
    outlineVariant = Color(0xFFD3E3D6),
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
