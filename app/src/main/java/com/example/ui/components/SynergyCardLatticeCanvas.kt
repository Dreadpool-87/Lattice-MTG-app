package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.domain.CardRelationship
import com.example.domain.RelationshipType
import com.example.ui.theme.ArcaneCyan
import com.example.ui.theme.BurnishedGold
import com.example.ui.theme.LatticeForestBase
import com.example.ui.theme.LatticeSparkGreen
import com.example.ui.theme.ManaBlueBg
import com.example.ui.theme.SlateMuted
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

fun colorForRelationshipType(type: RelationshipType): Color {
    return when (type) {
        RelationshipType.COMBO_LOCK -> LatticeSparkGreen
        RelationshipType.ENGINE_ENABLER -> BurnishedGold
        RelationshipType.TUTOR_TARGET -> ArcaneCyan
        RelationshipType.PROTECTION_PAYOFF -> ManaBlueBg
        RelationshipType.COMBAT_SYNERGY -> Color(0xFFFF9D82)
        RelationshipType.CURVE_SYNERGY -> SlateMuted
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SynergyCardLatticeCanvas(
    relationships: List<CardRelationship>,
    selectedCardId: String?,
    onSelectCard: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val cards = relationships.flatMap { listOf(it.cardA, it.cardB) }.distinctBy { it.id }
    val connections = relationships
        .flatMap { listOf(it.cardA.id, it.cardB.id) }
        .groupingBy { it }
        .eachCount()

    Column(modifier = modifier.fillMaxWidth()) {
        if (cards.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(16.dp)
            ) {
                Text(
                    text = "No links match the current selection.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(276.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF17271E),
                            LatticeForestBase
                        )
                    )
                )
                .border(1.dp, LatticeSparkGreen.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
        ) {
            val density = LocalDensity.current
            val widthPx = with(density) { maxWidth.toPx() }
            val heightPx = with(density) { maxHeight.toPx() }
            val cx = widthPx / 2f
            val cy = heightPx / 2f
            val rx = widthPx * 0.35f
            val ry = heightPx * 0.34f

            val nodeOffsets = cards.mapIndexed { index, node ->
                val angle = (2.0 * PI * index / cards.size) - (PI / 2.0)
                val x = cx + (rx * cos(angle)).toFloat()
                val y = cy + (ry * sin(angle)).toFloat()
                node.id to Offset(x, y)
            }.toMap()

            Canvas(modifier = Modifier.fillMaxSize()) {
                // Decorative orbital rings
                drawOval(
                    color = LatticeSparkGreen.copy(alpha = 0.14f),
                    topLeft = Offset(cx - rx, cy - ry),
                    size = androidx.compose.ui.geometry.Size(rx * 2f, ry * 2f),
                    style = Stroke(width = 1.5f)
                )
                drawCircle(
                    color = LatticeSparkGreen.copy(alpha = 0.08f),
                    radius = minOf(rx, ry) * 0.48f,
                    center = Offset(cx, cy),
                    style = Stroke(width = 1.2f)
                )

                // Draw card-to-card relationship edges
                relationships.forEach { rel ->
                    val pA = nodeOffsets[rel.cardA.id] ?: return@forEach
                    val pB = nodeOffsets[rel.cardB.id] ?: return@forEach
                    drawLine(
                        color = colorForRelationshipType(rel.type)
                            .copy(alpha = (0.5f + rel.strength * 0.16f).coerceAtMost(0.95f)),
                        start = pA,
                        end = pB,
                        strokeWidth = 2.2f + rel.strength * 1.6f,
                        cap = StrokeCap.Round
                    )
                }

                // Center Lattice core pulse
                drawCircle(
                    color = LatticeSparkGreen.copy(alpha = 0.16f),
                    radius = 22f,
                    center = Offset(cx, cy)
                )
                drawCircle(
                    color = LatticeSparkGreen,
                    radius = 7f,
                    center = Offset(cx, cy)
                )
            }

            // Interactive Card Nodes
            val chipHalfWidthPx = with(density) { 48.dp.toPx() }
            val chipHalfHeightPx = with(density) { 24.dp.toPx() }

            cards.forEach { node ->
                val center = nodeOffsets[node.id] ?: return@forEach
                val isSelected = selectedCardId == node.id
                val shortLabel = node.name
                    .substringBefore(",")
                    .let { if (it.length > 13) it.take(12) + "…" else it }

                Surface(
                    color = when {
                        isSelected -> LatticeSparkGreen
                        else -> Color(0xE6131C18)
                    },
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = when {
                            isSelected -> Color.White
                            else -> LatticeSparkGreen.copy(alpha = 0.45f)
                        }
                    ),
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = (center.x - chipHalfWidthPx).roundToInt(),
                                y = (center.y - chipHalfHeightPx).roundToInt()
                            )
                        }
                        .widthIn(min = 84.dp, max = 104.dp)
                        .minimumInteractiveComponentSize()
                        .clickable { onSelectCard(node.id) }
                        .testTag("lattice_node_${node.id}")
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = shortLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                isSelected -> LatticeForestBase
                                else -> Color(0xFFF3EFE6)
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${connections.getValue(node.id)} links",
                            style = MaterialTheme.typography.labelSmall,
                            color = when {
                                isSelected -> LatticeForestBase.copy(alpha = 0.8f)
                                else -> LatticeSparkGreen
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Edge type legend
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            relationships.map { it.type }.distinct().forEach { relType ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(colorForRelationshipType(relType))
                    )
                    Spacer(modifier = Modifier.padding(horizontal = 2.dp))
                    Text(
                        text = relType.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
