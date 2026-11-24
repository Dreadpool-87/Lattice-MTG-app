package com.example.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun WubrgColorStrip(modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(3.dp)
    Row(
        modifier.clip(shape).border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .testTag("wubrg_color_strip")
    ) {
        WubrgSwatchColors.forEachIndexed { index, color ->
            Box(Modifier.weight(1f).fillMaxHeight().background(color).testTag("wubrg_band_$index"))
        }
    }
}
