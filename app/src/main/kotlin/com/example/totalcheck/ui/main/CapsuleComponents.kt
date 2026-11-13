package com.example.totalcheck.ui.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.example.totalcheck.data.local.CheckItemEntity
import com.example.totalcheck.ui.theme.LocalReducedMotion
import com.example.totalcheck.ui.theme.LocalThemeTransitioning
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

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
    resetWaveKey: Int = 0,
    resetDelayMs: Int? = null,
    onStartEdit: () -> Unit = {},
    onConfirmEdit: (String) -> Unit = {},
    onCancelEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isReducedMotion = LocalReducedMotion.current
    val freezeColorTransition = LocalThemeTransitioning.current || isReducedMotion
    val colorSpec: AnimationSpec<Color> = if (freezeColorTransition) {
        snap()
    } else {
        spring(dampingRatio = 0.8f, stiffness = 380f)
    }

    // Staggered Bulk Selection (一鍵重置錯峰波浪)
    var visualChecked by remember(item.id) { mutableStateOf(item.isChecked) }
    var lastConsumedWaveKey by remember(item.id) { mutableIntStateOf(resetWaveKey) }
    val wavePulseScale = remember(item.id) { Animatable(1f) }

    LaunchedEffect(item.isChecked, resetWaveKey) {
        if (item.isChecked) {
            visualChecked = true
            lastConsumedWaveKey = resetWaveKey
        } else if (visualChecked) {
            val isStaggeredWave = resetWaveKey != lastConsumedWaveKey && resetDelayMs != null
            lastConsumedWaveKey = resetWaveKey
            if (isStaggeredWave && !isReducedMotion) {
                val delayDuration = resetDelayMs ?: 0
                if (delayDuration > 0) delay(delayDuration.toLong())
                visualChecked = false
                wavePulseScale.animateTo(
                    targetValue = 1.045f,
                    animationSpec = spring(dampingRatio = 0.8f, stiffness = 900f)
                )
                wavePulseScale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(dampingRatio = 0.55f, stiffness = 400f)
                )
            } else {
                visualChecked = false
            }
        }
    }

    // Curved Card Deletion (左滑或垃圾桶曲線刪除)
    var cardWidthPx by remember(item.id) { mutableFloatStateOf(0f) }
    var isDeleting by remember(item.id) { mutableStateOf(false) }
    var exitStartX by remember(item.id) { mutableFloatStateOf(0f) }
    val dragOffsetX = remember(item.id) { Animatable(0f) }
    val exitProgress = remember(item.id) { Animatable(0f) }
    val curveDropPx = with(density) { 72.dp.toPx() }
    val cornerRadiusPx = with(density) { 24.dp.toPx() }

    val triggerCurvedDelete: () -> Unit = {
        if (!isDeleting) {
            isDeleting = true
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            if (isReducedMotion) {
                onDelete()
            } else {
                scope.launch {
                    exitStartX = dragOffsetX.value
                    exitProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 280, easing = FastOutLinearInEasing)
                    )
                    onDelete()
                }
            }
        }
    }

    val pressScale = animateFloatAsState(
        targetValue = if (isPressed && !isEditing && !isDeleting) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
        label = "capsulePressScale"
    )
    val containerColor = animateColorAsState(
        targetValue = if (visualChecked && !isEditing) colorScheme.surfaceContainerLow else colorScheme.surfaceContainerHigh,
        animationSpec = colorSpec,
        label = "capsuleContainerColor"
    )
    val borderColor = animateColorAsState(
        targetValue = when {
            isEditing -> colorScheme.primary
            visualChecked -> colorScheme.outlineVariant.copy(alpha = 0.4f)
            else -> colorScheme.tertiary.copy(alpha = 0.55f)
        },
        animationSpec = colorSpec,
        label = "capsuleBorderColor"
    )
    val indicatorColor = animateColorAsState(
        targetValue = when {
            isEditing -> colorScheme.primary
            visualChecked -> colorScheme.outline
            else -> colorScheme.tertiary
        },
        animationSpec = colorSpec,
        label = "capsuleIndicatorColor"
    )
    val badgeBgColor = animateColorAsState(
        targetValue = if (visualChecked) colorScheme.surfaceContainerHighest else colorScheme.tertiaryContainer,
        animationSpec = colorSpec,
        label = "capsuleBadgeBg"
    )
    val contentAlpha = animateFloatAsState(
        targetValue = if (visualChecked && !isEditing) 0.45f else 1f,
        animationSpec = if (isReducedMotion) snap() else spring(dampingRatio = 0.8f, stiffness = 380f),
        label = "capsuleContentAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { cardWidthPx = it.width.toFloat() }
    ) {
        // 背景刪除垃圾桶指示膠囊（僅於左滑或曲線退出時於 Layer/Draw 階段顯現）
        val trashRevealErrorBg = colorScheme.errorContainer
        val trashThresholdErrorBg = colorScheme.error
        val trashRevealTint = colorScheme.onErrorContainer
        val trashThresholdTint = colorScheme.onError
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(vertical = 2.dp, horizontal = 4.dp)
                .graphicsLayer {
                    val dragFraction = if (cardWidthPx > 0f) {
                        (-dragOffsetX.value / (cardWidthPx * 0.35f)).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    val reveal = maxOf(dragFraction, exitProgress.value)
                    alpha = (reveal * 1.15f).coerceIn(0f, 1f) * (1f - exitProgress.value * 0.4f)
                }
                .clip(RoundedCornerShape(24.dp))
                .drawBehind {
                    val thresholdReached = cardWidthPx > 0f && -dragOffsetX.value >= cardWidthPx * 0.35f
                    drawRect(if (thresholdReached || exitProgress.value > 0f) trashThresholdErrorBg else trashRevealErrorBg)
                }
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.graphicsLayer {
                    val thresholdReached = cardWidthPx > 0f && -dragOffsetX.value >= cardWidthPx * 0.35f
                    val targetScale = if (thresholdReached || exitProgress.value > 0f) 1.08f else 0.92f
                    scaleX = targetScale
                    scaleY = targetScale
                }
            ) {
                val thresholdReached = cardWidthPx > 0f && -dragOffsetX.value >= cardWidthPx * 0.35f
                val tintColor = if (thresholdReached || exitProgress.value > 0f) trashThresholdTint else trashRevealTint
                Text(
                    text = "刪除",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = tintColor
                )
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = null,
                    tint = tintColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // 前景項目膠囊卡片：所有縮放、位移、旋轉、淡出與底色/邊框重繪全面下沉至 graphicsLayer & drawBehind
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    val progress = exitProgress.value
                    val width = if (cardWidthPx > 0f) cardWidthPx else 1000f
                    val tx = if (progress > 0f) {
                        lerp(exitStartX, -width * 1.1f, progress)
                    } else {
                        dragOffsetX.value
                    }
                    val ty = (progress * progress) * curveDropPx
                    val totalScale = pressScale.value * wavePulseScale.value * lerp(1f, 0.78f, progress)
                    translationX = tx
                    translationY = ty
                    scaleX = totalScale
                    scaleY = totalScale
                    rotationZ = lerp(0f, -7f, progress)
                    alpha = lerp(1f, 0f, progress)
                }
                .clip(RoundedCornerShape(24.dp))
                .drawBehind {
                    val corner = CornerRadius(cornerRadiusPx, cornerRadiusPx)
                    drawRoundRect(
                        color = containerColor.value,
                        cornerRadius = corner
                    )
                    val strokePx = (if (visualChecked && !isEditing) 1.dp else 1.5.dp).toPx()
                    drawRoundRect(
                        color = borderColor.value,
                        cornerRadius = corner,
                        style = Stroke(width = strokePx)
                    )
                }
                .pointerInput(isEditing, isDeleting, isReducedMotion) {
                    if (isEditing || isDeleting) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var totalDx = 0f
                        var totalDy = 0f
                        val touchSlop = viewConfiguration.touchSlop
                        var draggingLeft = false

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.changedToUpIgnoreConsumed()) {
                                if (draggingLeft) {
                                    val thresholdPx = cardWidthPx * 0.35f
                                    if (cardWidthPx > 0f && -dragOffsetX.value >= thresholdPx) {
                                        triggerCurvedDelete()
                                    } else {
                                        scope.launch {
                                            dragOffsetX.animateTo(
                                                targetValue = 0f,
                                                animationSpec = spring(dampingRatio = 0.78f, stiffness = 380f)
                                            )
                                        }
                                    }
                                }
                                break
                            }
                            if (change.isConsumed) {
                                if (draggingLeft) {
                                    scope.launch {
                                        dragOffsetX.animateTo(
                                            targetValue = 0f,
                                            animationSpec = spring(dampingRatio = 0.78f, stiffness = 380f)
                                        )
                                    }
                                }
                                break
                            }
                            val delta = change.positionChange()
                            if (!draggingLeft) {
                                totalDx += delta.x
                                totalDy += delta.y
                                if (abs(totalDy) > touchSlop && abs(totalDy) > abs(totalDx)) {
                                    break
                                }
                                if (totalDx > touchSlop && abs(totalDx) > abs(totalDy)) {
                                    break
                                }
                                if (totalDx < -touchSlop && abs(totalDx) > abs(totalDy)) {
                                    draggingLeft = true
                                    change.consume()
                                    val clamped = totalDx.coerceAtMost(0f)
                                    scope.launch { dragOffsetX.snapTo(clamped) }
                                }
                            } else {
                                change.consume()
                                val nextX = (dragOffsetX.value + delta.x).coerceAtMost(0f)
                                scope.launch { dragOffsetX.snapTo(nextX) }
                            }
                        }
                    }
                }
                .then(
                    if (isEditing || isDeleting) {
                        Modifier
                    } else {
                        Modifier.combinedClickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = onToggle,
                            onLongClick = onStartEdit
                        )
                    }
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp)
                    .graphicsLayer { alpha = contentAlpha.value },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .height(24.dp)
                        .clip(RoundedCornerShape(50))
                        .drawBehind {
                            drawRect(indicatorColor.value)
                        }
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
                        fontWeight = if (visualChecked) FontWeight.Normal else FontWeight.SemiBold,
                        color = colorScheme.onSurface,
                        textDecoration = if (visualChecked) TextDecoration.LineThrough else TextDecoration.None,
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 4.dp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .width(76.dp)
                            .clip(RoundedCornerShape(50))
                            .drawBehind { drawRect(badgeBgColor.value) }
                    ) {
                        AnimatedContent(
                            targetState = visualChecked,
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
                        onClick = triggerCurvedDelete,
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
}
