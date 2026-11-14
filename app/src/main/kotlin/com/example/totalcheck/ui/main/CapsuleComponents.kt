package com.example.totalcheck.ui.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.material.icons.rounded.DragIndicator
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.util.lerp
import com.example.totalcheck.data.local.CheckItemEntity
import com.example.totalcheck.ui.theme.LocalReducedMotion
import com.example.totalcheck.ui.theme.LocalThemeTransitioning
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.roundToInt

@Composable
fun CapsuleProgressModule(
    uncheckedCount: Int,
    totalCount: Int,
    progressProvider: () -> Float,
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

    val targetAccentColor = if (uncheckedCount == 0 && totalCount > 0) colorScheme.primary else colorScheme.tertiary
    val animatedAccentColor by animateColorAsState(
        targetValue = targetAccentColor,
        animationSpec = colorSpec,
        label = "progressAccent"
    )
    val accentColor = if (freezeColorTransition) targetAccentColor else animatedAccentColor

    val completedCount = (totalCount - uncheckedCount).coerceAtLeast(0)
    var previousCompletedCount by remember { mutableIntStateOf(completedCount) }
    val badgePulse = remember { Animatable(1f) }
    LaunchedEffect(completedCount, totalCount, isReducedMotion) {
        val prev = previousCompletedCount
        previousCompletedCount = completedCount
        if (isReducedMotion || totalCount <= 0) {
            badgePulse.snapTo(1f)
        } else {
            if (completedCount != prev) {
                badgePulse.snapTo(if (completedCount > prev) 1.18f else 0.90f)
            }
            if (badgePulse.value != 1f || badgePulse.targetValue != 1f) {
                badgePulse.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(dampingRatio = 0.4f, stiffness = 420f)
                )
            }
        }
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
            val trackPath = remember { Path() }
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .graphicsLayer {
                        val currentProgress = progressProvider()
                        val over = (currentProgress - 1f).coerceAtLeast(0f)
                        scaleX = 1f + over * 0.08f
                        scaleY = 1f + over * 0.45f
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                    }
            ) {
                val currentProgress = progressProvider()
                val radius = size.height / 2f
                val corner = CornerRadius(radius, radius)
                trackPath.reset()
                trackPath.addRoundRect(
                    RoundRect(
                        left = 0f,
                        top = 0f,
                        right = size.width,
                        bottom = size.height,
                        cornerRadius = corner
                    )
                )
                drawRoundRect(
                    color = trackColor,
                    cornerRadius = corner
                )
                val clampedProgress = currentProgress.coerceIn(0f, 1f)
                if (clampedProgress > 0.002f) {
                    val fillWidth = (size.width * clampedProgress).coerceIn(0f, size.width)
                    clipPath(trackPath) {
                        drawRoundRect(
                            color = accentColor,
                            topLeft = Offset(fillWidth - size.width, 0f),
                            size = Size(size.width, size.height),
                            cornerRadius = corner
                        )
                    }
                }
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

@Composable
fun DashedCapsuleEmptySlot(
    heightDp: Dp,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val fillColor = colorScheme.surfaceContainerLow.copy(alpha = 0.45f)
    val dashColor = colorScheme.primary.copy(alpha = 0.42f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp)
            .clip(RoundedCornerShape(24.dp))
            .drawBehind {
                val r = 24.dp.toPx()
                val corner = CornerRadius(r, r)
                drawRoundRect(
                    color = fillColor,
                    cornerRadius = corner
                )
                val strokePx = 1.5.dp.toPx()
                drawRoundRect(
                    color = dashColor,
                    cornerRadius = corner,
                    style = Stroke(
                        width = strokePx,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f)
                    )
                )
            }
    )
}

@Composable
fun CapsuleCheckItemRow(
    item: CheckItemEntity,
    onToggle: () -> Unit,
    isEditing: Boolean = false,
    isFloatingDragged: Boolean = false,
    stackDepthProvider: () -> Float = { 0f },
    isCompressedProvider: () -> Boolean = { false },
    onCompressedClick: (() -> Unit)? = null,
    resetWaveKey: Int = 0,
    resetDelayMs: Int? = null,
    resetTriggeredAtMillis: Long = 0L,
    onStartEdit: () -> Unit = {},
    onConfirmEdit: (String) -> Unit = {},
    onCancelEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
    canMoveUp: Boolean = false,
    canMoveDown: Boolean = false,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val isReducedMotion = LocalReducedMotion.current
    val currentOnToggle by rememberUpdatedState(onToggle)
    val currentIsCompressedProvider by rememberUpdatedState(isCompressedProvider)
    val currentOnCompressedClick by rememberUpdatedState(onCompressedClick)
    val currentOnDelete by rememberUpdatedState(onDelete)

    // Staggered Bulk Selection (一鍵重置錯峰波浪)
    val isInActiveWave = !isReducedMotion &&
        resetWaveKey > 0 &&
        resetDelayMs != null &&
        resetTriggeredAtMillis > 0L &&
        (android.os.SystemClock.uptimeMillis() - resetTriggeredAtMillis) < (resetDelayMs + 450L)

    var visualChecked by remember(item.id) {
        mutableStateOf(if (isInActiveWave) true else item.isChecked)
    }
    var lastConsumedWaveKey by remember(item.id) { mutableIntStateOf(0) }
    var isWaveWaitingOrAnimating by remember(item.id) { mutableStateOf(false) }
    var previousIsChecked by remember(item.id) { mutableStateOf(item.isChecked) }
    val wavePulseScale = remember(item.id) { Animatable(1f) }
    val checkedProgress = remember(item.id) {
        Animatable(if (visualChecked && !isEditing) 1f else 0f)
    }

    if (resetWaveKey > 0) {
        LaunchedEffect(resetWaveKey) {
            val delayMs = resetDelayMs
            if (resetWaveKey != lastConsumedWaveKey && delayMs != null && !isReducedMotion) {
                val elapsed = (android.os.SystemClock.uptimeMillis() - resetTriggeredAtMillis).coerceAtLeast(0L)
                if (elapsed < delayMs + 450L) {
                    lastConsumedWaveKey = resetWaveKey
                    isWaveWaitingOrAnimating = true
                    visualChecked = true
                    wavePulseScale.snapTo(1f)
                    val remainingDelay = (delayMs.toLong() - elapsed).coerceAtLeast(0L)
                    if (remainingDelay > 0L) {
                        delay(remainingDelay)
                    }
                    if (!isWaveWaitingOrAnimating) return@LaunchedEffect
                    visualChecked = false
                    isWaveWaitingOrAnimating = false
                    wavePulseScale.animateTo(
                        targetValue = 1.045f,
                        animationSpec = spring(dampingRatio = 0.7f, stiffness = 900f)
                    )
                    wavePulseScale.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(dampingRatio = 0.45f, stiffness = 360f)
                    )
                }
            }
        }
    }

    LaunchedEffect(item.isChecked) {
        val wasChecked = previousIsChecked
        previousIsChecked = item.isChecked
        if (item.isChecked) {
            if (!wasChecked || !isWaveWaitingOrAnimating) {
                isWaveWaitingOrAnimating = false
                visualChecked = true
                wavePulseScale.snapTo(1f)
            }
        } else {
            val expectingWave = !isReducedMotion &&
                resetWaveKey > 0 &&
                resetDelayMs != null &&
                (resetWaveKey != lastConsumedWaveKey || isWaveWaitingOrAnimating)
            if (!expectingWave) {
                visualChecked = false
            }
        }
    }

    val targetCheckedProgress = if (visualChecked && !isEditing) 1f else 0f
    LaunchedEffect(targetCheckedProgress, isReducedMotion) {
        if (isReducedMotion) {
            checkedProgress.snapTo(targetCheckedProgress)
        } else if (
            checkedProgress.targetValue != targetCheckedProgress ||
            checkedProgress.value != targetCheckedProgress
        ) {
            checkedProgress.animateTo(
                targetValue = targetCheckedProgress,
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f)
            )
        }
    }

    // Hold-to-Delete Perimeter Ring (按住 150ms 後外圈 24dp 進度環於 1000ms 線性填滿刪除，中途放開/移出/取消於 300ms 內退回)
    var isPressed by remember(item.id) { mutableStateOf(false) }
    var isDeleting by remember(item.id) { mutableStateOf(false) }
    var isCardFocused by remember(item.id) { mutableStateOf(false) }
    val holdRingProgress = remember(item.id) { Animatable(0f) }
    val deleteExitProgress = remember(item.id) { Animatable(0f) }
    var holdAnimJob by remember(item.id) { mutableStateOf<Job?>(null) }

    DisposableEffect(item.id) {
        onDispose {
            holdAnimJob?.cancel()
            holdAnimJob = null
        }
    }

    val cornerRadiusPx = with(density) { 24.dp.toPx() }
    val ringStrokePx = with(density) { 2.5.dp.toPx() }
    val perimeterPath = remember(item.id) { Path() }
    val progressSegmentPath = remember(item.id) { Path() }
    val pathMeasure = remember(item.id) { PathMeasure() }
    var cachedRingSize by remember(item.id) { mutableStateOf(Size.Zero) }

    val triggerHoldDelete: () -> Unit = {
        if (!isDeleting) {
            isDeleting = true
            isPressed = false
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            if (isReducedMotion) {
                currentOnDelete()
            } else {
                scope.launch {
                    deleteExitProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 160, easing = LinearEasing)
                    )
                    currentOnDelete()
                }
            }
        }
    }

    val startRewindRing: () -> Unit = {
        holdAnimJob?.cancel()
        holdAnimJob = scope.launch {
            val current = holdRingProgress.value
            if (current > 0f) {
                val rewindDuration = (300f * current).roundToInt().coerceIn(80, 300)
                holdRingProgress.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = rewindDuration, easing = LinearEasing)
                )
            }
        }
    }

    val pressScale = animateFloatAsState(
        targetValue = when {
            isFloatingDragged -> 1.03f
            isPressed && !isEditing && !isDeleting -> 0.97f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
        label = "capsulePressScale"
    )
    val uncheckedContainerColor = colorScheme.surfaceContainerHigh
    val checkedContainerColor = colorScheme.surfaceContainerLow
    val uncheckedBorderColor = when {
        isFloatingDragged || isEditing -> colorScheme.primary
        else -> colorScheme.tertiary.copy(alpha = 0.55f)
    }
    val checkedBorderColor = when {
        isFloatingDragged || isEditing -> colorScheme.primary
        else -> colorScheme.outlineVariant.copy(alpha = 0.4f)
    }
    val ringTrackBorderColor = colorScheme.outlineVariant.copy(alpha = 0.45f)
    val ringFillColor = colorScheme.error
    val uncheckedIndicatorColor = if (isEditing) colorScheme.primary else colorScheme.tertiary
    val checkedIndicatorColor = if (isEditing) colorScheme.primary else colorScheme.outline
    val uncheckedBadgeBgColor = colorScheme.tertiaryContainer
    val checkedBadgeBgColor = colorScheme.surfaceContainerHighest

    val isDarkSurface = colorScheme.surface.luminance() < 0.5f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .testTag("check_item_card_${item.id}")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    val exit = deleteExitProgress.value
                    val totalScale = pressScale.value * wavePulseScale.value * lerp(1f, 0.88f, exit)
                    scaleX = totalScale
                    scaleY = totalScale
                    alpha = lerp(1f, 0f, exit)
                    shadowElevation = if (isFloatingDragged) 14.dp.toPx() else 0f
                }
                .then(
                    if (isFloatingDragged) {
                        Modifier.shadow(elevation = 10.dp, shape = RoundedCornerShape(24.dp), clip = false)
                    } else {
                        Modifier
                    }
                )
                .clip(RoundedCornerShape(24.dp))
                .drawBehind {
                    val t = checkedProgress.value.coerceIn(0f, 1f)
                    val ringProgress = holdRingProgress.value.coerceIn(0f, 1f)
                    val corner = CornerRadius(cornerRadiusPx, cornerRadiusPx)
                    drawRoundRect(
                        color = lerpColor(uncheckedContainerColor, checkedContainerColor, t),
                        cornerRadius = corner
                    )
                    // 貼合外框之平常未填充進度環（底環）與長按填滿進度弧（頂環）
                    val baseBorderStrokePx = if (isFloatingDragged) {
                        2.dp.toPx()
                    } else {
                        lerp(1.5f, 1f, t).dp.toPx()
                    }
                    val baseBorderColor = when {
                        ringProgress > 0.001f -> ringTrackBorderColor
                        isCardFocused && !isEditing -> colorScheme.primary
                        else -> lerpColor(uncheckedBorderColor, checkedBorderColor, t)
                    }
                    val effectiveBorderStrokePx = if (isCardFocused && !isEditing && ringProgress <= 0.001f) {
                        2.dp.toPx()
                    } else {
                        baseBorderStrokePx
                    }
                    drawRoundRect(
                        color = baseBorderColor,
                        cornerRadius = corner,
                        style = Stroke(width = effectiveBorderStrokePx)
                    )
                    if (ringProgress > 0.001f && size.width > 0f && size.height > 0f) {
                        if (cachedRingSize != size) {
                            cachedRingSize = size
                            buildClockwisePerimeterPath(
                                width = size.width,
                                height = size.height,
                                cornerRadius = cornerRadiusPx,
                                strokeWidth = ringStrokePx,
                                outPath = perimeterPath
                            )
                            pathMeasure.setPath(perimeterPath, false)
                        }
                        val totalLen = pathMeasure.length
                        if (totalLen > 0f) {
                            progressSegmentPath.reset()
                            pathMeasure.getSegment(
                                startDistance = 0f,
                                stopDistance = totalLen * ringProgress,
                                destination = progressSegmentPath,
                                startWithMoveTo = true
                            )
                            drawPath(
                                path = progressSegmentPath,
                                color = ringFillColor,
                                style = Stroke(
                                    width = ringStrokePx,
                                    cap = StrokeCap.Round
                                )
                            )
                        }
                    }
                }
                .drawWithContent {
                    drawContent()
                    val depth = stackDepthProvider().coerceIn(0f, 4f)
                    if (depth > 0.01f && !isFloatingDragged) {
                        val maxDim = if (isDarkSurface) 0.42f else 0.22f
                        val stepDim = if (isDarkSurface) 0.11f else 0.055f
                        val dimAlpha = (depth * stepDim).coerceIn(0f, maxDim)
                        val corner = CornerRadius(cornerRadiusPx, cornerRadiusPx)
                        drawRoundRect(
                            color = Color.Black.copy(alpha = dimAlpha),
                            cornerRadius = corner
                        )
                    }
                }
                .onFocusChanged { isCardFocused = it.isFocused }
                .focusable(enabled = !isEditing && !isDeleting)
                .onKeyEvent { event ->
                    if (
                        event.type == KeyEventType.KeyDown &&
                        event.nativeKeyEvent.repeatCount == 0 &&
                        !isEditing &&
                        !isDeleting
                    ) {
                        when (event.key) {
                            Key.Spacebar, Key.Enter, Key.NumPadEnter -> {
                                if (currentIsCompressedProvider()) {
                                    currentOnCompressedClick?.invoke()
                                } else {
                                    currentOnToggle()
                                }
                                true
                            }
                            else -> false
                        }
                    } else {
                        false
                    }
                }
                .semantics {
                    stateDescription = if (visualChecked) "已備" else "待備"
                    if (!isEditing && !isDeleting) {
                        onClick(label = if (visualChecked) "標記為待備" else "標記為已備") {
                            if (currentIsCompressedProvider()) {
                                currentOnCompressedClick?.invoke()
                            } else {
                                currentOnToggle()
                            }
                            true
                        }
                    }
                }
                .pointerInput(item.id, isEditing, isDeleting, isReducedMotion) {
                    if (isEditing || isDeleting) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = true)
                        if (currentIsCompressedProvider()) {
                            val touchSlop = viewConfiguration.touchSlop
                            while (true) {
                                val event = awaitPointerEvent(pass = PointerEventPass.Final)
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (change == null || change.isConsumed) break
                                if (!change.pressed) {
                                    val movedDist = hypot(
                                        change.position.x - down.position.x,
                                        change.position.y - down.position.y
                                    )
                                    if (movedDist <= touchSlop) {
                                        currentOnCompressedClick?.invoke()
                                    }
                                    break
                                }
                            }
                            return@awaitEachGesture
                        }
                        isPressed = true
                        var holdEngaged = false
                        var cancelledOrLeft = false
                        val touchSlop = viewConfiguration.touchSlop

                        holdAnimJob?.cancel()
                        val currentHoldJob = scope.launch {
                            delay(150L)
                            if (cancelledOrLeft || isDeleting) return@launch
                            holdEngaged = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val startVal = holdRingProgress.value.coerceIn(0f, 1f)
                            val fillDurationMs = ((1f - startVal) * 1000f).roundToInt().coerceAtLeast(1)
                            holdRingProgress.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(durationMillis = fillDurationMs, easing = LinearEasing)
                            )
                            if (!cancelledOrLeft && !isDeleting && holdRingProgress.value >= 0.999f) {
                                triggerHoldDelete()
                            }
                        }
                        holdAnimJob = currentHoldJob

                        while (true) {
                            val event = awaitPointerEvent(pass = PointerEventPass.Final)
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null || change.isConsumed) {
                                cancelledOrLeft = true
                                isPressed = false
                                startRewindRing()
                                break
                            }

                            val pos = change.position
                            val isInsideBounds = pos.x in 0f..size.width.toFloat() &&
                                pos.y in 0f..size.height.toFloat()

                            if (!isInsideBounds) {
                                cancelledOrLeft = true
                                isPressed = false
                                startRewindRing()
                                break
                            }

                            if (!change.pressed) {
                                isPressed = false
                                if (holdEngaged) {
                                    if (!isDeleting) {
                                        cancelledOrLeft = true
                                        startRewindRing()
                                    }
                                } else {
                                    currentHoldJob.cancel()
                                    val movedDist = hypot(pos.x - down.position.x, pos.y - down.position.y)
                                    if (movedDist <= touchSlop) {
                                        currentOnToggle()
                                    }
                                }
                                break
                            }
                        }
                    }
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp)
                    .graphicsLayer { alpha = lerp(1f, 0.45f, checkedProgress.value.coerceIn(0f, 1f)) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .height(24.dp)
                        .graphicsLayer {
                            val extra = (wavePulseScale.value - 1f) * 2.2f
                            scaleX = 1f + extra * 0.5f
                            scaleY = 1f + extra
                        }
                        .drawBehind {
                            val r = size.width / 2f
                            drawRoundRect(
                                color = lerpColor(uncheckedIndicatorColor, checkedIndicatorColor, checkedProgress.value.coerceIn(0f, 1f)),
                                cornerRadius = CornerRadius(r, r)
                            )
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
                            .height(28.dp)
                            .graphicsLayer {
                                val badgeScale = 1f + (wavePulseScale.value - 1f) * 1.8f
                                scaleX = badgeScale
                                scaleY = badgeScale
                            }
                            .drawBehind {
                                val r = size.height / 2f
                                drawRoundRect(
                                    color = lerpColor(uncheckedBadgeBgColor, checkedBadgeBgColor, checkedProgress.value.coerceIn(0f, 1f)),
                                    cornerRadius = CornerRadius(r, r)
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer { alpha = checkedProgress.value.coerceIn(0f, 1f) },
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
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
                        }
                        Text(
                            text = "待備",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onTertiaryContainer,
                            modifier = Modifier.graphicsLayer {
                                alpha = (1f - checkedProgress.value).coerceIn(0f, 1f)
                            }
                        )
                    }
                    IconButton(
                        onClick = {
                            if (currentIsCompressedProvider()) {
                                currentOnCompressedClick?.invoke()
                            } else {
                                onStartEdit()
                            }
                        },
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("edit_item_button_${item.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = "編輯項目名稱",
                            tint = colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    // 六點拖曳手把（位於最右側 34.dp 槽位，支援即時觸控拖曳、鍵盤上下移動及無障礙自訂操作）
                    var isHandleFocused by remember(item.id) { mutableStateOf(false) }
                    val focusRingColor = colorScheme.primary
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("drag_handle_${item.id}")
                            .drawBehind {
                                if (isHandleFocused) {
                                    val r = 10.dp.toPx()
                                    drawRoundRect(
                                        color = focusRingColor,
                                        cornerRadius = CornerRadius(r, r),
                                        style = Stroke(width = 2.dp.toPx())
                                    )
                                }
                            }
                            .onFocusChanged { isHandleFocused = it.isFocused }
                            .focusable()
                            .onKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown && !currentIsCompressedProvider()) {
                                    when (event.key) {
                                        Key.DirectionUp -> {
                                            if (canMoveUp && onMoveUp != null) {
                                                onMoveUp()
                                                true
                                            } else {
                                                false
                                            }
                                        }
                                        Key.DirectionDown -> {
                                            if (canMoveDown && onMoveDown != null) {
                                                onMoveDown()
                                                true
                                            } else {
                                                false
                                            }
                                        }
                                        else -> false
                                    }
                                } else {
                                    false
                                }
                            }
                            .semantics {
                                contentDescription = "拖曳排序「${item.label}」"
                                val actions = buildList {
                                    if (canMoveUp && onMoveUp != null) {
                                        add(CustomAccessibilityAction("上移一格") { onMoveUp(); true })
                                    }
                                    if (canMoveDown && onMoveDown != null) {
                                        add(CustomAccessibilityAction("下移一格") { onMoveDown(); true })
                                    }
                                }
                                if (actions.isNotEmpty()) {
                                    customActions = actions
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DragIndicator,
                            contentDescription = null,
                            tint = if (isFloatingDragged) colorScheme.primary else colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun buildClockwisePerimeterPath(
    width: Float,
    height: Float,
    cornerRadius: Float,
    strokeWidth: Float,
    outPath: Path
) {
    outPath.reset()
    val halfStroke = strokeWidth / 2f
    val left = halfStroke
    val top = halfStroke
    val right = (width - halfStroke).coerceAtLeast(left + 1f)
    val bottom = (height - halfStroke).coerceAtLeast(top + 1f)
    val r = cornerRadius.coerceIn(0f, minOf((right - left) / 2f, (bottom - top) / 2f))
    val midX = (left + right) / 2f

    outPath.moveTo(midX, top)
    outPath.lineTo(right - r, top)
    if (r > 0f) {
        outPath.arcTo(
            rect = Rect(right - 2 * r, top, right, top + 2 * r),
            startAngleDegrees = -90f,
            sweepAngleDegrees = 90f,
            forceMoveTo = false
        )
    }
    outPath.lineTo(right, bottom - r)
    if (r > 0f) {
        outPath.arcTo(
            rect = Rect(right - 2 * r, bottom - 2 * r, right, bottom),
            startAngleDegrees = 0f,
            sweepAngleDegrees = 90f,
            forceMoveTo = false
        )
    }
    outPath.lineTo(left + r, bottom)
    if (r > 0f) {
        outPath.arcTo(
            rect = Rect(left, bottom - 2 * r, left + 2 * r, bottom),
            startAngleDegrees = 90f,
            sweepAngleDegrees = 90f,
            forceMoveTo = false
        )
    }
    outPath.lineTo(left, top + r)
    if (r > 0f) {
        outPath.arcTo(
            rect = Rect(left, top, left + 2 * r, top + 2 * r),
            startAngleDegrees = 180f,
            sweepAngleDegrees = 90f,
            forceMoveTo = false
        )
    }
    outPath.close()
}
