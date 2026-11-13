package com.example.totalcheck.ui.shell

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun ProgressSection(
    style: VisualStyle,
    uncheckedCount: Int,
    totalCount: Int,
    animatedProgress: Float,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (style) {
        VisualStyle.StyleA -> StyleAProgressHeader(
            uncheckedCount = uncheckedCount,
            totalCount = totalCount,
            animatedProgress = animatedProgress,
            onReset = onReset,
            modifier = modifier
        )
        VisualStyle.StyleB -> StyleBProgressHeader(
            uncheckedCount = uncheckedCount,
            totalCount = totalCount,
            animatedProgress = animatedProgress,
            onReset = onReset,
            modifier = modifier
        )
        VisualStyle.StyleC -> StyleCProgressHeader(
            uncheckedCount = uncheckedCount,
            totalCount = totalCount,
            animatedProgress = animatedProgress,
            onReset = onReset,
            modifier = modifier
        )
    }
}

@Composable
private fun StyleAProgressHeader(
    uncheckedCount: Int,
    totalCount: Int,
    animatedProgress: Float,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val statusText = if (uncheckedCount == 0 && totalCount > 0) {
        "已全部備齊 ($totalCount/$totalCount)"
    } else {
        "尚缺 $uncheckedCount 項 / 共 $totalCount 項"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (uncheckedCount > 0) colorScheme.secondary else colorScheme.primary
            )
            TextButton(
                onClick = onReset,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.RestartAlt,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "一鍵重置", style = MaterialTheme.typography.labelLarge)
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { animatedProgress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = if (uncheckedCount == 0 && totalCount > 0) colorScheme.primary else colorScheme.secondary,
            trackColor = colorScheme.surfaceContainerHighest,
            strokeCap = StrokeCap.Round
        )
    }
}

@Composable
private fun StyleBProgressHeader(
    uncheckedCount: Int,
    totalCount: Int,
    animatedProgress: Float,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (uncheckedCount == 0 && totalCount > 0) "全數備齊 $totalCount" else "待清點 $uncheckedCount / $totalCount",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = colorScheme.onSurfaceVariant
            )
            TextButton(
                onClick = onReset,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier.height(30.dp)
            ) {
                Text(text = "一鍵重置", style = MaterialTheme.typography.labelMedium)
            }
        }
        LinearProgressIndicator(
            progress = { animatedProgress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp),
            color = colorScheme.primary,
            trackColor = colorScheme.surfaceContainerHighest
        )
    }
}

@Composable
private fun StyleCProgressHeader(
    uncheckedCount: Int,
    totalCount: Int,
    animatedProgress: Float,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val accentColor = if (uncheckedCount == 0 && totalCount > 0) colorScheme.primary else colorScheme.secondary

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        color = colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = if (uncheckedCount == 0 && totalCount > 0) "已備齊" else "尚缺 $uncheckedCount",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = accentColor
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "/ 共 $totalCount 項",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                FilledTonalButton(
                    onClick = onReset,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = colorScheme.surfaceContainerHighest,
                        contentColor = colorScheme.onSurface
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.RestartAlt,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "重置", style = MaterialTheme.typography.labelLarge)
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(RoundedCornerShape(50))
                    .background(colorScheme.surfaceContainerHighest)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress.coerceIn(0.02f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .background(accentColor)
                )
            }
        }
    }
}

@Composable
fun CheckItemRow(
    style: VisualStyle,
    item: MockCheckItem,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (style) {
        VisualStyle.StyleA -> StyleACardItem(item = item, onToggle = onToggle, modifier = modifier)
        VisualStyle.StyleB -> StyleBDenseItem(item = item, onToggle = onToggle, modifier = modifier)
        VisualStyle.StyleC -> StyleCCapsuleItem(item = item, onToggle = onToggle, modifier = modifier)
    }
}

@Composable
private fun StyleACardItem(
    item: MockCheckItem,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val containerColor by animateColorAsState(
        targetValue = if (item.isChecked) colorScheme.surfaceContainerLow else colorScheme.surfaceContainerHigh,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
        label = "styleACardColor"
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (item.isChecked) 0.45f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
        label = "styleAAlpha"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = BorderStroke(
            width = 1.dp,
            color = if (item.isChecked) colorScheme.outlineVariant.copy(alpha = 0.45f) else colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .alpha(contentAlpha),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (item.isChecked) colorScheme.primary else colorScheme.surface)
                    .border(
                        width = 2.dp,
                        color = if (item.isChecked) colorScheme.primary else colorScheme.outline,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (item.isChecked) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "已勾選",
                        tint = colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = item.label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (item.isChecked) FontWeight.Normal else FontWeight.Medium,
                color = colorScheme.onSurface,
                textDecoration = if (item.isChecked) TextDecoration.LineThrough else TextDecoration.None,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun StyleBDenseItem(
    item: MockCheckItem,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val contentAlpha by animateFloatAsState(
        targetValue = if (item.isChecked) 0.45f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
        label = "styleBAlpha"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .alpha(contentAlpha),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(if (item.isChecked) colorScheme.primary else colorScheme.surface)
                    .border(
                        width = 1.5.dp,
                        color = if (item.isChecked) colorScheme.primary else colorScheme.outline,
                        shape = RoundedCornerShape(5.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (item.isChecked) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "已勾選",
                        tint = colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = item.label,
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurface,
                textDecoration = if (item.isChecked) TextDecoration.LineThrough else TextDecoration.None,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        HorizontalDivider(
            thickness = 0.5.dp,
            color = colorScheme.outlineVariant
        )
    }
}

@Composable
private fun StyleCCapsuleItem(
    item: MockCheckItem,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val containerColor by animateColorAsState(
        targetValue = if (item.isChecked) colorScheme.surfaceContainerLow else colorScheme.surfaceContainerHigh,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
        label = "styleCCardColor"
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (item.isChecked) 0.45f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
        label = "styleCAlpha"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(24.dp),
        color = containerColor,
        border = if (item.isChecked) {
            BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.4f))
        } else {
            BorderStroke(1.5.dp, colorScheme.secondary.copy(alpha = 0.55f))
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .alpha(contentAlpha),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(24.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (item.isChecked) colorScheme.outline else colorScheme.secondary)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = item.label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (item.isChecked) FontWeight.Normal else FontWeight.SemiBold,
                color = colorScheme.onSurface,
                textDecoration = if (item.isChecked) TextDecoration.LineThrough else TextDecoration.None,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(12.dp))
            Surface(
                shape = RoundedCornerShape(50),
                color = if (item.isChecked) colorScheme.surfaceContainerHighest else colorScheme.secondaryContainer
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (item.isChecked) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "已勾選",
                            tint = colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Text(
                            text = "待備",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }
}
