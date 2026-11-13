package com.example.totalcheck.ui.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.totalcheck.data.local.CheckItemEntity

@Composable
fun CapsuleProgressModule(
    uncheckedCount: Int,
    totalCount: Int,
    animatedProgress: Float,
    showUncheckedOnly: Boolean,
    onToggleUncheckedOnly: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val accentColor by animateColorAsState(
        targetValue = if (uncheckedCount == 0 && totalCount > 0) colorScheme.primary else colorScheme.secondary,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
        label = "progressAccent"
    )

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
                    Text(text = "一鍵重置", style = MaterialTheme.typography.labelLarge)
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

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                FilterChip(
                    selected = showUncheckedOnly,
                    onClick = onToggleUncheckedOnly,
                    shape = RoundedCornerShape(50),
                    label = {
                        Text(
                            text = "只看未帶",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (showUncheckedOnly) Icons.Rounded.Check else Icons.Rounded.FilterList,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colorScheme.secondaryContainer,
                        selectedLabelColor = colorScheme.onSecondaryContainer,
                        selectedLeadingIconColor = colorScheme.onSecondaryContainer
                    )
                )
                Text(
                    text = if (showUncheckedOnly) "已隱藏已備項目" else "顯示全部項目",
                    style = MaterialTheme.typography.labelMedium,
                    color = colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun CapsuleCheckItemRow(
    item: CheckItemEntity,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
        label = "capsulePressScale"
    )
    val containerColor by animateColorAsState(
        targetValue = if (item.isChecked) colorScheme.surfaceContainerLow else colorScheme.surfaceContainerHigh,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
        label = "capsuleContainerColor"
    )
    val borderColor by animateColorAsState(
        targetValue = if (item.isChecked) {
            colorScheme.outlineVariant.copy(alpha = 0.4f)
        } else {
            colorScheme.secondary.copy(alpha = 0.55f)
        },
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
        label = "capsuleBorderColor"
    )
    val indicatorColor by animateColorAsState(
        targetValue = if (item.isChecked) colorScheme.outline else colorScheme.secondary,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
        label = "capsuleIndicatorColor"
    )
    val badgeBgColor by animateColorAsState(
        targetValue = if (item.isChecked) colorScheme.surfaceContainerHighest else colorScheme.secondaryContainer,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
        label = "capsuleBadgeBg"
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (item.isChecked) 0.45f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
        label = "capsuleContentAlpha"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(RoundedCornerShape(24.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onToggle
            ),
        shape = RoundedCornerShape(24.dp),
        color = containerColor,
        border = BorderStroke(if (item.isChecked) 1.dp else 1.5.dp, borderColor)
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
                    .background(indicatorColor)
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
                color = badgeBgColor
            ) {
                AnimatedContent(
                    targetState = item.isChecked,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "capsuleBadgeState"
                ) { checked ->
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (checked) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "已備",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = colorScheme.onSurfaceVariant
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
}
