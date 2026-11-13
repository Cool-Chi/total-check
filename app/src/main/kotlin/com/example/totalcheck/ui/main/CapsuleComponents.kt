package com.example.totalcheck.ui.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.totalcheck.data.local.CheckItemEntity
import com.example.totalcheck.ui.theme.LocalReducedMotion
import com.example.totalcheck.ui.theme.LocalThemeTransitioning

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
    val isReducedMotion = LocalReducedMotion.current
    val freezeColorTransition = LocalThemeTransitioning.current || isReducedMotion
    val colorSpec: AnimationSpec<Color> = if (freezeColorTransition) {
        snap()
    } else {
        spring(dampingRatio = 0.8f, stiffness = 380f)
    }

    val accentColor by animateColorAsState(
        targetValue = if (uncheckedCount == 0 && totalCount > 0) colorScheme.primary else colorScheme.tertiary,
        animationSpec = colorSpec,
        label = "progressAccent"
    )

    val completedCount = (totalCount - uncheckedCount).coerceAtLeast(0)
    var previousCompletedCount by remember { mutableIntStateOf(completedCount) }
    val badgePulse = remember { Animatable(1f) }
    LaunchedEffect(completedCount, totalCount) {
        if (!isReducedMotion && totalCount > 0 && completedCount != previousCompletedCount) {
            badgePulse.snapTo(if (completedCount > previousCompletedCount) 1.18f else 0.90f)
            badgePulse.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.4f, stiffness = 420f)
            )
        }
        previousCompletedCount = completedCount
    }

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
                        color = accentColor,
                        modifier = Modifier.graphicsLayer {
                            scaleX = badgePulse.value
                            scaleY = badgePulse.value
                            transformOrigin = TransformOrigin(0f, 0.7f)
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "/ 共 $totalCount 項",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                val hasCheckedItems = totalCount > uncheckedCount
                FilledTonalButton(
                    onClick = onReset,
                    enabled = hasCheckedItems,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = colorScheme.surfaceContainerHighest,
                        contentColor = colorScheme.onSurface,
                        disabledContainerColor = colorScheme.surfaceContainerHighest.copy(alpha = 0.45f),
                        disabledContentColor = colorScheme.onSurface.copy(alpha = 0.38f)
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

            val trackColor = colorScheme.surfaceContainerHighest
            val dividerColor = colorScheme.surfaceContainerHigh
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .graphicsLayer {
                        val over = (animatedProgress - 1f).coerceAtLeast(0f)
                        scaleX = 1f + over * 0.08f
                        scaleY = 1f + over * 0.45f
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                    }
            ) {
                val radius = size.height / 2f
                val corner = CornerRadius(radius, radius)
                drawRoundRect(
                    color = trackColor,
                    cornerRadius = corner
                )
                val fillWidth = (size.width * animatedProgress.coerceAtLeast(0.02f))
                    .coerceIn(size.height, size.width)
                drawRoundRect(
                    color = accentColor,
                    size = Size(fillWidth, size.height),
                    cornerRadius = corner
                )
                if (totalCount in 2..20) {
                    val dividerWidth = 2.5.dp.toPx()
                    for (i in 1 until totalCount) {
                        val x = size.width * (i.toFloat() / totalCount) - dividerWidth / 2f
                        drawRect(
                            color = dividerColor,
                            topLeft = Offset(x, 0f),
                            size = Size(dividerWidth, size.height)
                        )
                    }
                }
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
                        selectedContainerColor = colorScheme.tertiaryContainer,
                        selectedLabelColor = colorScheme.onTertiaryContainer,
                        selectedLeadingIconColor = colorScheme.onTertiaryContainer
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CapsuleCheckItemRow(
    item: CheckItemEntity,
    onToggle: () -> Unit,
    isEditing: Boolean = false,
    onStartEdit: () -> Unit = {},
    onConfirmEdit: (String) -> Unit = {},
    onCancelEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val freezeColorTransition = LocalThemeTransitioning.current || LocalReducedMotion.current
    val colorSpec: AnimationSpec<Color> = if (freezeColorTransition) {
        snap()
    } else {
        spring(dampingRatio = 0.8f, stiffness = 380f)
    }

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed && !isEditing) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
        label = "capsulePressScale"
    )
    val containerColor by animateColorAsState(
        targetValue = if (item.isChecked && !isEditing) colorScheme.surfaceContainerLow else colorScheme.surfaceContainerHigh,
        animationSpec = colorSpec,
        label = "capsuleContainerColor"
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            isEditing -> colorScheme.primary
            item.isChecked -> colorScheme.outlineVariant.copy(alpha = 0.4f)
            else -> colorScheme.tertiary.copy(alpha = 0.55f)
        },
        animationSpec = colorSpec,
        label = "capsuleBorderColor"
    )
    val indicatorColor by animateColorAsState(
        targetValue = when {
            isEditing -> colorScheme.primary
            item.isChecked -> colorScheme.outline
            else -> colorScheme.tertiary
        },
        animationSpec = colorSpec,
        label = "capsuleIndicatorColor"
    )
    val badgeBgColor by animateColorAsState(
        targetValue = if (item.isChecked) colorScheme.surfaceContainerHighest else colorScheme.tertiaryContainer,
        animationSpec = colorSpec,
        label = "capsuleBadgeBg"
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (item.isChecked && !isEditing) 0.45f else 1f,
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
            .then(
                if (isEditing) {
                    Modifier
                } else {
                    Modifier.combinedClickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onToggle,
                        onLongClick = onStartEdit
                    )
                }
            ),
        shape = RoundedCornerShape(24.dp),
        color = containerColor,
        border = BorderStroke(if (item.isChecked && !isEditing) 1.dp else 1.5.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp)
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

            if (isEditing) {
                var editValue by remember(item.id, isEditing) {
                    mutableStateOf(
                        TextFieldValue(
                            text = item.label,
                            selection = TextRange(item.label.length)
                        )
                    )
                }
                val focusRequester = remember { FocusRequester() }
                LaunchedEffect(isEditing) {
                    focusRequester.requestFocus()
                }

                BasicTextField(
                    value = editValue,
                    onValueChange = { editValue = it },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .padding(vertical = 4.dp),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (editValue.text.isNotBlank()) {
                                onConfirmEdit(editValue.text)
                            }
                        }
                    )
                )
                IconButton(
                    onClick = { onConfirmEdit(editValue.text) },
                    enabled = editValue.text.isNotBlank(),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "儲存名稱",
                        tint = colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = onCancelEdit,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "取消編輯",
                        tint = colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (item.isChecked) FontWeight.Normal else FontWeight.SemiBold,
                    color = colorScheme.onSurface,
                    textDecoration = if (item.isChecked) TextDecoration.LineThrough else TextDecoration.None,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 4.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = badgeBgColor,
                    modifier = Modifier.width(76.dp)
                ) {
                    AnimatedContent(
                        targetState = item.isChecked,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        contentAlignment = Alignment.Center,
                        label = "capsuleBadgeState"
                    ) { checked ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp),
                            horizontalArrangement = Arrangement.Center,
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
                                    color = colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }
                }
                IconButton(
                    onClick = onStartEdit,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "編輯項目名稱",
                        tint = colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(17.dp)
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "刪除項目",
                        tint = colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
