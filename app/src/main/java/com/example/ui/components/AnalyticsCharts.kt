package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.SynergyCluster
import com.example.domain.SynergyConnection
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ManaCurveChart(
    manaCurve: Map<Int, Int>,
    modifier: Modifier = Modifier
) {
    val maxCount = (manaCurve.values.maxOrNull() ?: 1).coerceAtLeast(1)
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier
                .fillMaxWidth()
                .height(128.dp)
                .padding(horizontal = 4.dp)
        ) {
            (0..6).forEach { cmc ->
                val count = manaCurve[cmc] ?: 0
                val targetFraction = (count.toFloat() / maxCount.toFloat()).coerceIn(0f, 1f)
                val animatedFraction by animateFloatAsState(
                    targetValue = targetFraction,
                    animationSpec = spring(dampingRatio = 0.72f),
                    label = "cmc_$cmc"
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    Text(
                        text = count.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (count > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.68f)
                            .weight(1f, fill = false)
                            .fillMaxHeight(0.75f)
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        if (count > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(animatedFraction.coerceAtLeast(0.08f))
                                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(secondary, primary)
                                        )
                                    )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (cmc == 6) "6+" else cmc.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun ColorPipBreakdownBar(
    colorPips: Map<String, Int>,
    modifier: Modifier = Modifier
) {
    val total = colorPips.values.sum().coerceAtLeast(1)
    val ordered = listOf("W", "U", "B", "R", "G", "C")
    val active = ordered.filter { (colorPips[it] ?: 0) > 0 }

    Column(modifier = modifier.fillMaxWidth()) {
        if (active.isEmpty()) {
            Text(
                text = "No colored mana pips in non-land spells.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            active.forEach { code ->
                val count = colorPips[code] ?: 0
                val (bg, _) = manaColorsForSymbol(code)
                Box(
                    modifier = Modifier
                        .weight(count.toFloat() / total.toFloat())
                        .fillMaxHeight()
                        .background(bg)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            active.forEach { code ->
                val count = colorPips[code] ?: 0
                val pct = (count * 100f / total).toInt()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ManaSymbolPip(symbol = code, size = 16.dp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "$count ($pct%)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SynergyLatticeGraph(
    clusters: List<SynergyCluster>,
    connections: List<SynergyConnection>,
    selectedTag: String?,
    onSelectTag: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val outline = MaterialTheme.colorScheme.outline
    val surfaceHigh = MaterialTheme.colorScheme.surfaceContainerHighest

    Column(modifier = modifier.fillMaxWidth()) {
        if (clusters.isEmpty()) {
            Text(
                text = "Add spells and artifacts to visualize your deck's Synergy Lattice.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .border(1.dp, outline.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                .padding(12.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
            ) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val radius = minOf(size.width, size.height) * 0.36f

                val positions = clusters.mapIndexed { idx, cluster ->
                    val angle = (2.0 * PI * idx / clusters.size) - (PI / 2.0)
                    val x = cx + (radius * cos(angle)).toFloat()
                    val y = cy + (radius * sin(angle)).toFloat()
                    cluster.tag to Offset(x, y)
                }.toMap()

                // Draw outer lattice ring
                drawCircle(
                    color = outline.copy(alpha = 0.25f),
                    radius = radius,
                    center = Offset(cx, cy),
                    style = Stroke(width = 1.5f)
                )

                // Draw hub spokes
                positions.forEach { (tag, pos) ->
                    val highlighted = selectedTag == null || selectedTag == tag
                    drawLine(
                        color = if (highlighted) secondary.copy(alpha = 0.32f) else outline.copy(alpha = 0.12f),
                        start = Offset(cx, cy),
                        end = pos,
                        strokeWidth = if (selectedTag == tag) 4f else 2f,
                        cap = StrokeCap.Round
                    )
                }

                // Draw card-sharing synergy edges
                connections.forEach { conn ->
                    val pA = positions[conn.tagA] ?: return@forEach
                    val pB = positions[conn.tagB] ?: return@forEach
                    val isFocused = selectedTag == null || selectedTag == conn.tagA || selectedTag == conn.tagB
                    drawLine(
                        color = if (isFocused) {
                            primary.copy(alpha = (0.35f + conn.sharedCards * 0.15f).coerceAtMost(0.95f))
                        } else {
                            outline.copy(alpha = 0.10f)
                        },
                        start = pA,
                        end = pB,
                        strokeWidth = (1.5f + conn.sharedCards * 1.3f).coerceAtMost(7f),
                        cap = StrokeCap.Round
                    )
                }

                // Center core node
                drawCircle(
                    color = primary.copy(alpha = 0.25f),
                    radius = 18f,
                    center = Offset(cx, cy)
                )
                drawCircle(
                    color = primary,
                    radius = 9f,
                    center = Offset(cx, cy)
                )

                // Cluster nodes
                clusters.forEach { cluster ->
                    val pos = positions[cluster.tag] ?: return@forEach
                    val isSelected = selectedTag == cluster.tag
                    val nodeRadius = (12f + cluster.cardNames.size * 2.2f).coerceAtMost(26f)
                    drawCircle(
                        color = surfaceHigh,
                        radius = nodeRadius + 3f,
                        center = pos
                    )
                    drawCircle(
                        color = if (isSelected) primary else secondary.copy(alpha = 0.85f),
                        radius = nodeRadius,
                        center = pos
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            clusters.forEach { cluster ->
                val selected = selectedTag == cluster.tag
                FilterChip(
                    selected = selected,
                    onClick = { onSelectTag(cluster.tag) },
                    label = {
                        Text(
                            text = "${cluster.tag} (${cluster.totalCopies})",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag("synergy_chip_${cluster.tag.lowercase().replace(" ", "_")}")
                )
            }
        }

        val activeCluster = clusters.firstOrNull { it.tag == selectedTag } ?: clusters.firstOrNull()
        if (activeCluster != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "${activeCluster.tag} Synergy Nodes (${activeCluster.cardNames.size} cards)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = activeCluster.cardNames.joinToString(" • "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
