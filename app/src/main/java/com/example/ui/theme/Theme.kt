package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
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
        subtitle = "White and neutral gray",
        swatchHex = Color(0xFFF2F2F2)
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
        subtitle = "Black and charcoal",
        swatchHex = Color(0xFF272727)
    ),
    RED(
        storageKey = "RED",
        manaCode = "R",
        displayName = "Mountain Forge",
        subtitle = "Crimson red",
        swatchHex = Color(0xFFFF5258)
    ),
    GREEN(
        storageKey = "GREEN",
        manaCode = "G",
        displayName = "Forest Spark",
        subtitle = "Lattice Obsidian & Mint Spark",
        swatchHex = LatticeSparkGreen
    ),
    WUBRG(
        storageKey = "WUBRG",
        manaCode = "WUBRG",
        displayName = "WUBRG",
        subtitle = "All five mana colors",
        swatchHex = Color(0xFF63CBFF)
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
    LIGHT("LIGHT", "Light"),
    OLED("OLED", "OLED");

    companion object {
        fun fromStorageKey(key: String?): ThemeBrightnessMode {
            return entries.firstOrNull { it.storageKey.equals(key, ignoreCase = true) } ?: SYSTEM
        }
    }
}

// === 1. WHITE (Plains / Radiant Dawn) ===
private val WhiteDarkColorScheme = darkColorScheme(
    primary = Color(0xFFF2F2F2),
    onPrimary = Color(0xFF202020),
    primaryContainer = Color(0xFF383838),
    onPrimaryContainer = Color(0xFFFAFAFA),
    secondary = Color(0xFFD4D4D4),
    onSecondary = Color(0xFF242424),
    secondaryContainer = Color(0xFF323232),
    onSecondaryContainer = Color(0xFFEEEEEE),
    tertiary = ManaWhiteBg,
    onTertiary = ManaWhiteFg,
    background = Color(0xFF141414),
    onBackground = Color(0xFFF6F6F6),
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFF6F6F6),
    surfaceVariant = Color(0xFF2D2D2D),
    onSurfaceVariant = Color(0xFFC4C4C4),
    surfaceContainerLow = Color(0xFF1A1A1A),
    surfaceContainer = Color(0xFF262626),
    surfaceContainerHigh = Color(0xFF303030),
    surfaceContainerHighest = Color(0xFF3B3B3B),
    outline = Color(0xFF7A7A7A),
    outlineVariant = Color(0xFF404040),
    error = CrimsonEmber,
    onError = Color.White,
    errorContainer = CrimsonContainer,
    onErrorContainer = CrimsonEmber
)

private val WhiteLightColorScheme = lightColorScheme(
    primary = Color(0xFF373737),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE4E4E4),
    onPrimaryContainer = Color(0xFF222222),
    secondary = Color(0xFF555555),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE9E9E9),
    onSecondaryContainer = Color(0xFF333333),
    tertiary = ManaWhiteBg,
    onTertiary = ManaWhiteFg,
    background = Color(0xFFFAFAFA),
    onBackground = Color(0xFF202020),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF202020),
    surfaceVariant = Color(0xFFECECEC),
    onSurfaceVariant = Color(0xFF535353),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF3F3F3),
    surfaceContainerHigh = Color(0xFFEDEDED),
    surfaceContainerHighest = Color(0xFFE5E5E5),
    outline = Color(0xFF747474),
    outlineVariant = Color(0xFFD0D0D0),
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
    primary = Color(0xFFD7D7D7),
    onPrimary = Color(0xFF151515),
    primaryContainer = Color(0xFF383838),
    onPrimaryContainer = Color(0xFFEFEFEF),
    secondary = Color(0xFFBEBEBE),
    onSecondary = Color(0xFF252525),
    secondaryContainer = Color(0xFF303030),
    onSecondaryContainer = Color(0xFFEBEBEB),
    tertiary = ManaBlackBg,
    onTertiary = ManaBlackFg,
    background = Color(0xFF0B0B0B),
    onBackground = Color(0xFFEAEAEA),
    surface = Color(0xFF151515),
    onSurface = Color(0xFFEAEAEA),
    surfaceVariant = Color(0xFF242424),
    onSurfaceVariant = Color(0xFFBCBCBC),
    surfaceContainerLow = Color(0xFF121212),
    surfaceContainer = Color(0xFF1D1D1D),
    surfaceContainerHigh = Color(0xFF262626),
    surfaceContainerHighest = Color(0xFF313131),
    outline = Color(0xFF707070),
    outlineVariant = Color(0xFF3D3D3D),
    error = CrimsonEmber,
    onError = Color.White,
    errorContainer = CrimsonContainer,
    onErrorContainer = CrimsonEmber
)

private val BlackLightColorScheme = lightColorScheme(
    primary = Color(0xFF262626),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDEDEDE),
    onPrimaryContainer = Color(0xFF111111),
    secondary = Color(0xFF505050),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2E2E2),
    onSecondaryContainer = Color(0xFF2A2A2A),
    tertiary = ManaBlackBg,
    onTertiary = ManaBlackFg,
    background = Color(0xFFEEEEEE),
    onBackground = Color(0xFF181818),
    surface = Color(0xFFFAFAFA),
    onSurface = Color(0xFF181818),
    surfaceVariant = Color(0xFFE0E0E0),
    onSurfaceVariant = Color(0xFF4E4E4E),
    surfaceContainerLow = Color(0xFFF6F6F6),
    surfaceContainer = Color(0xFFE9E9E9),
    surfaceContainerHigh = Color(0xFFE1E1E1),
    surfaceContainerHighest = Color(0xFFD8D8D8),
    outline = Color(0xFF737373),
    outlineVariant = Color(0xFFC6C6C6),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B)
)

// === 4. RED (Mountain / Dragonfire Forge) ===
private val RedDarkColorScheme = darkColorScheme(
    primary = Color(0xFFFF696F),
    onPrimary = Color(0xFF410008),
    primaryContainer = Color(0xFF731521),
    onPrimaryContainer = Color(0xFFFFD9DC),
    secondary = Color(0xFFFF9AA0),
    onSecondary = Color(0xFF480910),
    secondaryContainer = Color(0xFF532026),
    onSecondaryContainer = Color(0xFFFFD9DC),
    tertiary = ManaRedBg,
    onTertiary = ManaRedFg,
    background = Color(0xFF1B0D10),
    onBackground = Color(0xFFFFF0F1),
    surface = Color(0xFF281217),
    onSurface = Color(0xFFFFF0F1),
    surfaceVariant = Color(0xFF3A1D23),
    onSurfaceVariant = Color(0xFFE0B5BA),
    surfaceContainerLow = Color(0xFF241015),
    surfaceContainer = Color(0xFF34171D),
    surfaceContainerHigh = Color(0xFF431C25),
    surfaceContainerHighest = Color(0xFF52232D),
    outline = Color(0xFF97616A),
    outlineVariant = Color(0xFF56313A),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF5C1919),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val RedLightColorScheme = lightColorScheme(
    primary = Color(0xFFAA1830),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDADF),
    onPrimaryContainer = Color(0xFF55000F),
    secondary = Color(0xFF9E3345),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDFE3),
    onSecondaryContainer = Color(0xFF521D29),
    tertiary = ManaRedBg,
    onTertiary = ManaRedFg,
    background = Color(0xFFFFF3F4),
    onBackground = Color(0xFF2B161B),
    surface = Color(0xFFFFFAFA),
    onSurface = Color(0xFF2B161B),
    surfaceVariant = Color(0xFFF8E3E6),
    onSurfaceVariant = Color(0xFF6F4F56),
    surfaceContainerLow = Color(0xFFFFFAFA),
    surfaceContainer = Color(0xFFFCE8EB),
    surfaceContainerHigh = Color(0xFFF6DCE0),
    surfaceContainerHighest = Color(0xFFF1D1D7),
    outline = Color(0xFF9F737C),
    outlineVariant = Color(0xFFE4BFC7),
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

val LocalWubrgTheme = staticCompositionLocalOf { WubrgTheme.GREEN }

val WubrgSwatchColors = listOf(
    Color(0xFFF7F3E8), Color(0xFF63CBFF), Color(0xFF242424),
    Color(0xFFFF696F), Color(0xFF57EFAA)
)

private val WubrgDarkColorScheme = BlackDarkColorScheme.copy(
    primary = WubrgSwatchColors[1],
    onPrimary = Color(0xFF082630),
    primaryContainer = Color(0xFF123B52),
    onPrimaryContainer = Color(0xFFC1EBFF),
    secondary = WubrgSwatchColors[4],
    onSecondary = Color(0xFF092D20),
    secondaryContainer = Color(0xFF174533),
    onSecondaryContainer = Color(0xFFC2F9DC),
    tertiary = WubrgSwatchColors[3],
    onTertiary = Color(0xFF450B13),
    tertiaryContainer = Color(0xFF592127),
    onTertiaryContainer = Color(0xFFFFDADD)
)

private val WubrgLightColorScheme = WhiteLightColorScheme.copy(
    primary = Color(0xFF126186),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7F0FF),
    onPrimaryContainer = Color(0xFF123748),
    secondary = Color(0xFF136747),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCEF4E1),
    onSecondaryContainer = Color(0xFF103B2A),
    tertiary = Color(0xFFAA273D),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDBDF),
    onTertiaryContainer = Color(0xFF5B1322)
)

fun ColorScheme.withOledSurfaces() = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceBright = Color(0xFF131313),
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0B0B0B),
    surfaceContainer = Color(0xFF101010),
    surfaceContainerHigh = Color(0xFF181818),
    surfaceContainerHighest = Color(0xFF202020)
)

fun colorSchemeForWubrg(wubrgTheme: WubrgTheme, isDark: Boolean): ColorScheme {
    return when (wubrgTheme) {
        WubrgTheme.WHITE -> if (isDark) WhiteDarkColorScheme else WhiteLightColorScheme
        WubrgTheme.BLUE -> if (isDark) BlueDarkColorScheme else BlueLightColorScheme
        WubrgTheme.BLACK -> if (isDark) BlackDarkColorScheme else BlackLightColorScheme
        WubrgTheme.RED -> if (isDark) RedDarkColorScheme else RedLightColorScheme
        WubrgTheme.GREEN -> if (isDark) GreenDarkColorScheme else GreenLightColorScheme
        WubrgTheme.WUBRG -> if (isDark) WubrgDarkColorScheme else WubrgLightColorScheme
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
        ThemeBrightnessMode.DARK, ThemeBrightnessMode.OLED -> true
        ThemeBrightnessMode.LIGHT -> false
    }
    val palette = colorSchemeForWubrg(wubrgTheme = wubrgTheme, isDark = useDark)
    val colorScheme = if (brightnessMode == ThemeBrightnessMode.OLED) palette.withOledSurfaces() else palette
    CompositionLocalProvider(LocalWubrgTheme provides wubrgTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = LatticeTypography,
            shapes = LatticeShapes,
            content = content
        )
    }
}
