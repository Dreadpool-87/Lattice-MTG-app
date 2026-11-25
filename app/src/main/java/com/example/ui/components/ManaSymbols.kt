package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.DeckAnalytics
import com.example.ui.theme.ManaBlackBg
import com.example.ui.theme.ManaBlackFg
import com.example.ui.theme.ManaBlueBg
import com.example.ui.theme.ManaBlueFg
import com.example.ui.theme.ManaColorlessBg
import com.example.ui.theme.ManaColorlessFg
import com.example.ui.theme.ManaGreenBg
import com.example.ui.theme.ManaGreenFg
import com.example.ui.theme.ManaRedBg
import com.example.ui.theme.ManaRedFg
import com.example.ui.theme.ManaWhiteBg
import com.example.ui.theme.ManaWhiteFg
import com.example.ui.theme.RarityCommon
import com.example.ui.theme.RarityMythic
import com.example.ui.theme.RarityRare
import com.example.ui.theme.RarityUncommon

fun manaColorsForSymbol(symbol: String): Pair<Color, Color> {
    return when (symbol.uppercase()) {
        "W" -> ManaWhiteBg to ManaWhiteFg
        "U" -> ManaBlueBg to ManaBlueFg
        "B" -> ManaBlackBg to ManaBlackFg
        "R" -> ManaRedBg to ManaRedFg
        "G" -> ManaGreenBg to ManaGreenFg
        else -> ManaColorlessBg to ManaColorlessFg
    }
}

@Composable
fun ManaSymbolPip(
    symbol: String,
    size: Dp = 20.dp,
    modifier: Modifier = Modifier
) {
    val clean = symbol.trim().uppercase()
    val primaryToken = if ("/" in clean) clean.substringBefore("/") else clean
    val (bg, fg) = manaColorsForSymbol(primaryToken)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bg)
            .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
    ) {
        Text(
            text = clean,
            color = fg,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = (size.value * 0.5f).sp,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1
        )
    }
}

@Composable
fun ManaCostRow(
    manaCost: String,
    pipSize: Dp = 19.dp,
    modifier: Modifier = Modifier
) {
    val symbols = DeckAnalytics.parseManaSymbols(manaCost)
    if (symbols.isEmpty()) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        symbols.forEach { sym ->
            ManaSymbolPip(symbol = sym, size = pipSize)
        }
    }
}

@Composable
fun ColorIdentityRow(
    colors: List<String>,
    pipSize: Dp = 18.dp,
    modifier: Modifier = Modifier
) {
    val list = if (colors.isEmpty()) listOf("C") else colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        list.forEach { c ->
            ManaSymbolPip(symbol = c, size = pipSize)
        }
    }
}

@Composable
fun RarityPill(rarity: String, modifier: Modifier = Modifier) {
    val color = when (rarity.lowercase()) {
        "mythic" -> RarityMythic
        "rare" -> RarityRare
        "uncommon" -> RarityUncommon
        else -> RarityCommon
    }
    Surface(
        color = color.copy(alpha = 0.16f),
        shape = RoundedCornerShape(6.dp),
        modifier = modifier
    ) {
        Text(
            text = rarity.uppercase(),
            color = color,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
