package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.ArchetypeMatchResult
import com.example.domain.ArchetypeTemplate
import com.example.domain.CardRelationship
import com.example.domain.DeckRoleCounts
import com.example.ui.components.colorForRelationshipType
import com.example.ui.theme.LatticeSparkContainer
import com.example.ui.theme.LatticeSparkGreen
import kotlin.math.roundToInt

@Composable
internal fun SynergyMetricTile(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = accent
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun RelationshipDetailCard(
    relationship: CardRelationship,
    onSelectCard: (String) -> Unit
) {
    val accent = colorForRelationshipType(relationship.type)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.38f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("relationship_card_${relationship.id}")
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    color = accent.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = relationship.type.label.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = accent,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(3) { idx ->
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (idx < relationship.strength) accent
                                    else MaterialTheme.colorScheme.outlineVariant
                                )
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelectCard(relationship.cardA.id) }
                ) {
                    Text(
                        text = relationship.cardA.name,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }

                Icon(
                    imageVector = Icons.Filled.Link,
                    contentDescription = "Synergy link",
                    tint = accent,
                    modifier = Modifier.size(18.dp)
                )

                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelectCard(relationship.cardB.id) }
                ) {
                    Text(
                        text = relationship.cardB.name,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Text(
                text = relationship.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = relationship.explanation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun ArchetypeAffinityCard(
    match: ArchetypeMatchResult,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) LatticeSparkContainer else MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) LatticeSparkGreen else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
            .width(178.dp)
            .clickable(onClick = onClick)
            .testTag("archetype_card_${match.template.id}")
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = match.template.category.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) LatticeSparkGreen else MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = "${match.matchScore}%",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) LatticeSparkGreen else MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = match.template.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            LinearProgressIndicator(
                progress = { (match.matchScore / 100f).coerceIn(0f, 1f) },
                color = if (isSelected) LatticeSparkGreen else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
            )
            Text(
                text = "${match.matchedCards.size} matching cards",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ArchetypeBlueprintCard(
    template: ArchetypeTemplate,
    matchResult: ArchetypeMatchResult,
    roles: DeckRoleCounts,
    deckCardNames: Set<String>
) {
    val scale = (roles.playableTotal.coerceAtLeast(60)) / 60.0
    val targetLands = (roles.playableTotal.coerceAtLeast(60) * template.targetLandRatio).roundToInt()
    val targetThreats = (template.targetThreats60 * scale).roundToInt()
    val targetInteraction = (template.targetInteraction60 * scale).roundToInt()
    val targetDrawRamp = (template.targetDrawRamp60 * scale).roundToInt()

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${template.name} Blueprint",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "${template.category} • Target Avg CMC ${template.idealAvgCmc}",
                        style = MaterialTheme.typography.labelSmall,
                        color = LatticeSparkGreen
                    )
                }
                Surface(
                    color = LatticeSparkContainer,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "${matchResult.matchScore}% Fit",
                        style = MaterialTheme.typography.labelLarge,
                        color = LatticeSparkGreen,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Text(
                text = template.strategyOverview,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            Icons.Filled.Shield,
                            contentDescription = null,
                            tint = LatticeSparkGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Strength: ${template.coreStrength}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Text(
                        text = "Watch Out For: ${template.vulnerableTo}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = "Role Balance vs Archetype Target",
                style = MaterialTheme.typography.titleSmall
            )
            RoleComparisonBar("Lands", roles.lands, targetLands)
            RoleComparisonBar("Threats / Payoffs", roles.threats, targetThreats)
            RoleComparisonBar("Interaction / Burn", roles.interaction, targetInteraction)
            RoleComparisonBar("Draw / Ramp / Tutors", roles.drawAndRamp, targetDrawRamp)

            Text(
                text = "Archetype Pillars (Highlighted = In Your Deck)",
                style = MaterialTheme.typography.titleSmall
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                template.benchmarkCards.forEach { cardName ->
                    val owned = cardName in deckCardNames
                    Surface(
                        color = if (owned) LatticeSparkContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(
                            1.dp,
                            if (owned) LatticeSparkGreen else MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            if (owned) {
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    contentDescription = "In deck",
                                    tint = LatticeSparkGreen,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                text = cardName,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (owned) LatticeSparkGreen else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoleComparisonBar(
    label: String,
    actual: Int,
    target: Int
) {
    val denominator = target.coerceAtLeast(1)
    val fraction = (actual.toFloat() / denominator.toFloat()).coerceIn(0f, 1f)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = label, style = MaterialTheme.typography.bodySmall)
            Text(
                text = "$actual / $target target",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        LinearProgressIndicator(
            progress = { fraction },
            color = if (actual >= target) LatticeSparkGreen else MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(RoundedCornerShape(4.dp))
        )
    }
}
