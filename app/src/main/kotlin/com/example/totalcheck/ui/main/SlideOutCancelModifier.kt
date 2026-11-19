package com.example.totalcheck.ui.main

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.example.totalcheck.ui.theme.LocalReducedMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.pow

@Stable
internal class KeyboardFocusVisibleController {
    var isKeyboardFocusVisible by mutableStateOf(false)
}

internal val LocalKeyboardFocusVisible = compositionLocalOf {
    KeyboardFocusVisibleController()
}

internal fun Modifier.trackKeyboardFocusVisibility(
    controller: KeyboardFocusVisibleController
): Modifier = this
    .pointerInput(controller) {
        awaitEachGesture {
            awaitFirstDown(
                requireUnconsumed = false,
                pass = PointerEventPass.Initial
            )
            if (controller.isKeyboardFocusVisible) {
                controller.isKeyboardFocusVisible = false
            }
        }
    }
    .onPreviewKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown) {
            when (event.key) {
                Key.Tab,
                Key.DirectionUp,
                Key.DirectionDown,
                Key.DirectionLeft,
                Key.DirectionRight,
                Key.MoveHome,
                Key.MoveEnd,
                Key.PageUp,
                Key.PageDown -> {
                    if (!controller.isKeyboardFocusVisible) {
                        controller.isKeyboardFocusVisible = true
                    }
                }
                else -> {}
            }
        }
        false
    }

internal val SLIDE_OUT_CANCEL_DISTANCE = 44.dp
internal const val PRESS_SINK_SCALE = 0.94f
internal val PRESS_SINK_TRANSLATION_Y = 1.5.dp
internal const val HOLD_LOCK_DELAY_MS = 70L

internal fun computeSlideOutPressStrength(
    posX: Float,
    posY: Float,
    widthPx: Float,
    heightPx: Float,
    cancelRadiusPx: Float
): Float {
    val dx = when {
        posX < 0f -> -posX
        posX > widthPx -> posX - widthPx
        else -> 0f
    }
    val dy = when {
        posY < 0f -> -posY
        posY > heightPx -> posY - heightPx
        else -> 0f
    }
    if (dx == 0f && dy == 0f) return 1f
    val dist = hypot(dx, dy)
    if (cancelRadiusPx <= 0.5f) return 0f
    val linear = (1f - (dist / cancelRadiusPx)).coerceIn(0f, 1f)
    return linear.pow(1.35f)
}

fun Modifier.slideOutCancelClickable(
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(50),
    role: Role = Role.Button,
    sinkScale: Float = PRESS_SINK_SCALE,
    sinkTranslationY: Dp = PRESS_SINK_TRANSLATION_Y,
    maxDeepenAlpha: Float? = null,
    longPressTimeoutMs: Long? = null,
    onLongClickLabel: String? = null,
    onPressChanged: ((Boolean) -> Unit)? = null,
    onPressDown: (() -> Unit)? = null,
    onPressCancelled: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onClickWithLocalOffset: ((Offset) -> Unit)? = null,
    onClick: () -> Unit
): Modifier = composed {
    val colorScheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val keyboardFocusController = LocalKeyboardFocusVisible.current
    val viewConfig = LocalViewConfiguration.current
    val isReducedMotion = LocalReducedMotion.current
    val scope = rememberCoroutineScope()

    val currentEnabled by rememberUpdatedState(enabled)
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnClickWithLocalOffset by rememberUpdatedState(onClickWithLocalOffset)
    val currentOnLongClick by rememberUpdatedState(onLongClick)
    val currentLongPressTimeoutMs by rememberUpdatedState(longPressTimeoutMs)
    val currentOnLongClickLabel by rememberUpdatedState(onLongClickLabel)
    val currentOnPressDown by rememberUpdatedState(onPressDown)
    val currentOnPressCancelled by rememberUpdatedState(onPressCancelled)
    val currentOnPressChanged by rememberUpdatedState(onPressChanged)

    val cancelRadiusPx = with(density) { SLIDE_OUT_CANCEL_DISTANCE.toPx() }
    val sinkTranslationPx = with(density) { sinkTranslationY.toPx() }
    val isDarkSurface = colorScheme.surface.luminance() < 0.5f
    val effectiveMaxAlpha = maxDeepenAlpha ?: if (isDarkSurface) 0.24f else 0.14f

    val pressStrength = remember { Animatable(0f) }
    var isFocused by remember { mutableStateOf(false) }
    var animJob by remember { mutableStateOf<Job?>(null) }
    var longPressJob by remember { mutableStateOf<Job?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            animJob?.cancel()
            longPressJob?.cancel()
            currentOnPressChanged?.invoke(false)
        }
    }

    val animateToRest: () -> Unit = {
        animJob?.cancel()
        if (isReducedMotion) {
            animJob = scope.launch { pressStrength.snapTo(0f) }
        } else {
            animJob = scope.launch {
                pressStrength.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = 0.82f,
                        stiffness = 620f,
                        visibilityThreshold = 0.002f
                    )
                )
            }
        }
    }

    val focusRingColor = colorScheme.primary

    this
        .graphicsLayer {
            val p = pressStrength.value.coerceIn(0f, 1f)
            if (!isReducedMotion && p > 0.001f) {
                val sc = lerp(1f, sinkScale, p)
                scaleX = sc
                scaleY = sc
                translationY = lerp(0f, sinkTranslationPx, p)
                transformOrigin = TransformOrigin(0.5f, 0.5f)
            }
        }
        .clip(shape)
        .drawWithContent {
            drawContent()
            val p = pressStrength.value.coerceIn(0f, 1f)
            if (p > 0.001f) {
                drawRect(color = Color.Black.copy(alpha = effectiveMaxAlpha * p))
            }
            if (isFocused && keyboardFocusController.isKeyboardFocusVisible) {
                drawRect(color = focusRingColor.copy(alpha = 0.12f))
                drawOutline(
                    outline = shape.createOutline(size, layoutDirection, this),
                    color = focusRingColor.copy(alpha = 0.88f),
                    style = Stroke(width = 3.dp.toPx())
                )
            }
        }
        .onFocusChanged { isFocused = it.isFocused }
        .focusable(enabled = enabled)
        .onKeyEvent { event ->
            if (
                currentEnabled &&
                event.type == KeyEventType.KeyDown &&
                event.nativeKeyEvent.repeatCount == 0
            ) {
                when (event.key) {
                    Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                        currentOnClick()
                        true
                    }
                    else -> false
                }
            } else {
                false
            }
        }
        .semantics {
            this.role = role
            if (!currentEnabled) {
                disabled()
            } else {
                onClick {
                    currentOnClick()
                    true
                }
                if (currentOnLongClick != null) {
                    onLongClick(label = currentOnLongClickLabel) {
                        currentOnLongClick?.invoke()
                        true
                    }
                }
            }
        }
        .pointerInput(enabled, isReducedMotion) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = true)
                val downTimeMs = down.uptimeMillis
                var longPressFired = false
                var isInsideBounds = true
                var gestureCancelled = false
                var isGestureLocked = false
                var exceededHorizontalSlopEarly = false
                var lastPos = down.position

                currentOnPressChanged?.invoke(true)
                currentOnPressDown?.invoke()

                animJob?.cancel()
                if (isReducedMotion) {
                    animJob = scope.launch { pressStrength.snapTo(1f) }
                } else {
                    animJob = scope.launch {
                        pressStrength.animateTo(
                            targetValue = 1f,
                            animationSpec = spring(
                                dampingRatio = 0.85f,
                                stiffness = 920f,
                                visibilityThreshold = 0.002f
                            )
                        )
                    }
                }

                longPressJob?.cancel()
                if (currentOnLongClick != null) {
                    longPressJob = scope.launch {
                        delay(currentLongPressTimeoutMs ?: viewConfig.longPressTimeoutMillis)
                        if (isInsideBounds && !gestureCancelled) {
                            longPressFired = true
                            animateToRest()
                            currentOnPressChanged?.invoke(false)
                            currentOnLongClick?.invoke()
                        }
                    }
                }

                try {
                    while (true) {
                        val mainEvent = awaitPointerEvent(pass = PointerEventPass.Main)
                        val mainChange = mainEvent.changes.firstOrNull { it.id == down.id }
                        if (mainChange == null || mainChange.isConsumed) {
                            gestureCancelled = true
                            longPressJob?.cancel()
                            currentOnPressCancelled?.invoke()
                            animateToRest()
                            break
                        }

                        var consumedBySelf = false
                        if (!isGestureLocked && !exceededHorizontalSlopEarly) {
                            val heldMs = mainChange.uptimeMillis - downTimeMs
                            val movedX = kotlin.math.abs(mainChange.position.x - down.position.x)
                            if (heldMs >= HOLD_LOCK_DELAY_MS) {
                                isGestureLocked = true
                            } else if (movedX > viewConfig.touchSlop) {
                                exceededHorizontalSlopEarly = true
                            }
                        }
                        if (isGestureLocked) {
                            mainChange.consume()
                            consumedBySelf = true
                        }

                        val event = awaitPointerEvent(pass = PointerEventPass.Final)
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null || (!consumedBySelf && change.isConsumed)) {
                            gestureCancelled = true
                            longPressJob?.cancel()
                            currentOnPressCancelled?.invoke()
                            animateToRest()
                            break
                        }

                        lastPos = change.position
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        val h = size.height.toFloat().coerceAtLeast(1f)
                        isInsideBounds = lastPos.x in 0f..w && lastPos.y in 0f..h

                        if (!change.pressed) {
                            longPressJob?.cancel()
                            if (!longPressFired) {
                                if (isInsideBounds) {
                                    animateToRest()
                                    val withOffset = currentOnClickWithLocalOffset
                                    if (withOffset != null) {
                                        withOffset(lastPos)
                                    } else {
                                        currentOnClick()
                                    }
                                } else {
                                    currentOnPressCancelled?.invoke()
                                    animateToRest()
                                }
                            }
                            break
                        }

                        val targetStrength = computeSlideOutPressStrength(
                            posX = lastPos.x,
                            posY = lastPos.y,
                            widthPx = w,
                            heightPx = h,
                            cancelRadiusPx = cancelRadiusPx
                        )
                        if (isReducedMotion) {
                            val snapTarget = if (isInsideBounds) 1f else 0f
                            if (pressStrength.value != snapTarget) {
                                animJob?.cancel()
                                animJob = scope.launch { pressStrength.snapTo(snapTarget) }
                            }
                        } else if (!isInsideBounds) {
                            // 滑出邊界後 1:1 跟隨手指距離連續衰減
                            animJob?.cancel()
                            animJob = scope.launch { pressStrength.snapTo(targetStrength) }
                        } else if (pressStrength.targetValue < 0.999f) {
                            // 從外部重新滑回按鈕內部時，平滑且迅速地恢復完整按壓狀態
                            animJob?.cancel()
                            animJob = scope.launch {
                                pressStrength.animateTo(
                                    targetValue = 1f,
                                    animationSpec = spring(
                                        dampingRatio = 0.85f,
                                        stiffness = 880f,
                                        visibilityThreshold = 0.002f
                                    )
                                )
                            }
                        }
                    }
                } finally {
                    longPressJob?.cancel()
                    currentOnPressChanged?.invoke(false)
                }
            }
        }
}

@Composable
fun TactileButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(50),
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    disabledContainerColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
    disabledContentColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 10.dp),
    sinkScale: Float = PRESS_SINK_SCALE,
    longPressTimeoutMs: Long? = null,
    onLongClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit
) {
    val resolvedContainer = if (enabled) containerColor else disabledContainerColor
    val resolvedContent = if (enabled) contentColor else disabledContentColor
    Surface(
        modifier = modifier
            .defaultMinSize(minWidth = 58.dp, minHeight = 40.dp)
            .slideOutCancelClickable(
                enabled = enabled,
                shape = shape,
                role = Role.Button,
                sinkScale = sinkScale,
                longPressTimeoutMs = longPressTimeoutMs,
                onLongClickLabel = onLongClickLabel,
                onLongClick = onLongClick,
                onClick = onClick
            ),
        shape = shape,
        color = resolvedContainer,
        contentColor = resolvedContent,
        border = border
    ) {
        ProvideTextStyle(value = MaterialTheme.typography.labelLarge) {
            Row(
                modifier = Modifier.padding(contentPadding),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }
    }
}

@Composable
fun TactileTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(50),
    contentColor: Color = MaterialTheme.colorScheme.primary,
    disabledContentColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    content: @Composable RowScope.() -> Unit
) {
    val resolvedContent = if (enabled) contentColor else disabledContentColor
    CompositionLocalProvider(LocalContentColor provides resolvedContent) {
        ProvideTextStyle(value = MaterialTheme.typography.labelLarge) {
            Row(
                modifier = modifier
                    .defaultMinSize(minWidth = 48.dp, minHeight = 40.dp)
                    .slideOutCancelClickable(
                        enabled = enabled,
                        shape = shape,
                        role = Role.Button,
                        onClick = onClick
                    )
                    .padding(contentPadding),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }
    }
}

@Composable
fun TactileIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CircleShape,
    containerColor: Color = Color.Transparent,
    contentColor: Color = LocalContentColor.current,
    disabledContainerColor: Color = Color.Transparent,
    disabledContentColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
    border: BorderStroke? = null,
    sinkScale: Float = PRESS_SINK_SCALE,
    longPressTimeoutMs: Long? = null,
    onLongClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    onPressChanged: ((Boolean) -> Unit)? = null,
    onPressDown: (() -> Unit)? = null,
    onPressCancelled: (() -> Unit)? = null,
    onClickWithLocalOffset: ((Offset) -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val resolvedContainer = if (enabled) containerColor else disabledContainerColor
    val resolvedContent = if (enabled) contentColor else disabledContentColor
    Surface(
        modifier = modifier
            .slideOutCancelClickable(
                enabled = enabled,
                shape = shape,
                role = Role.Button,
                sinkScale = sinkScale,
                longPressTimeoutMs = longPressTimeoutMs,
                onLongClickLabel = onLongClickLabel,
                onLongClick = onLongClick,
                onPressChanged = onPressChanged,
                onPressDown = onPressDown,
                onPressCancelled = onPressCancelled,
                onClickWithLocalOffset = onClickWithLocalOffset,
                onClick = onClick
            ),
        shape = shape,
        color = resolvedContainer,
        contentColor = resolvedContent,
        border = border
    ) {
        Box(
            contentAlignment = Alignment.Center,
            content = content
        )
    }
}

@Composable
fun TactileFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    shape: Shape = RoundedCornerShape(50),
    selectedContainerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    unselectedContainerColor: Color = Color.Transparent,
    selectedLabelColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    unselectedLabelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    selectedLeadingIconColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    unselectedLeadingIconColor: Color = MaterialTheme.colorScheme.primary
) {
    val colorScheme = MaterialTheme.colorScheme
    val containerColor = if (selected) selectedContainerColor else unselectedContainerColor
    val labelColor = if (selected) selectedLabelColor else unselectedLabelColor
    val iconColor = if (selected) selectedLeadingIconColor else unselectedLeadingIconColor
    val border = if (selected) {
        null
    } else {
        BorderStroke(1.dp, colorScheme.outline)
    }

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .semantics { },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .height(32.dp)
                .slideOutCancelClickable(
                    enabled = enabled,
                    shape = shape,
                    role = Role.Checkbox,
                    onClick = onClick
                ),
            shape = shape,
            color = containerColor,
            contentColor = labelColor,
            border = border
        ) {
            Row(
                modifier = Modifier.padding(
                    start = if (leadingIcon != null) 8.dp else 16.dp,
                    end = 16.dp
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (leadingIcon != null) {
                    CompositionLocalProvider(LocalContentColor provides iconColor) {
                        leadingIcon()
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                ProvideTextStyle(value = MaterialTheme.typography.labelLarge) {
                    label()
                }
            }
        }
    }
}

@Composable
internal fun Modifier.expandingTabSlideOutCancel(
    onSelect: () -> Unit,
    onLongPress: () -> Unit,
    pressFractionState: androidx.compose.runtime.MutableFloatState,
    isHoveredState: androidx.compose.runtime.MutableState<Boolean>,
    longPressEnabled: Boolean = true
): Modifier {
    val density = LocalDensity.current
    val viewConfig = LocalViewConfiguration.current
    val isReducedMotion = LocalReducedMotion.current
    val scope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    isHoveredState.value = isHovered

    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val currentLongPressEnabled by rememberUpdatedState(longPressEnabled)
    val cancelRadiusPx = with(density) { SLIDE_OUT_CANCEL_DISTANCE.toPx() }
    val sinkTranslationPx = with(density) { PRESS_SINK_TRANSLATION_Y.toPx() }
    val pressAnim = remember { Animatable(0f) }
    var animJob by remember { mutableStateOf<Job?>(null) }
    var longPressJob by remember { mutableStateOf<Job?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            animJob?.cancel()
            longPressJob?.cancel()
        }
    }

    val animateToRest: () -> Unit = {
        animJob?.cancel()
        if (isReducedMotion) {
            pressFractionState.floatValue = 0f
            animJob = scope.launch { pressAnim.snapTo(0f) }
        } else {
            animJob = scope.launch {
                pressAnim.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = 0.82f,
                        stiffness = 620f,
                        visibilityThreshold = 0.002f
                    )
                ) {
                    pressFractionState.floatValue = value
                }
            }
        }
    }

    return this
        .graphicsLayer {
            val p = pressAnim.value.coerceIn(0f, 1f)
            if (!isReducedMotion && p > 0.001f) {
                val sc = lerp(1f, PRESS_SINK_SCALE, p)
                scaleX = sc
                scaleY = sc
                translationY = lerp(0f, sinkTranslationPx, p)
                transformOrigin = TransformOrigin(0.5f, 0.5f)
            }
        }
        .hoverable(interactionSource = interactionSource)
        .pointerInput(isReducedMotion) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = true)
                val downTimeMs = down.uptimeMillis
                var longPressFired = false
                var isInsideBounds = true
                var gestureCancelled = false
                var isGestureLocked = false
                var exceededHorizontalSlopEarly = false

                animJob?.cancel()
                if (isReducedMotion) {
                    pressFractionState.floatValue = 1f
                    animJob = scope.launch { pressAnim.snapTo(1f) }
                } else {
                    animJob = scope.launch {
                        pressAnim.animateTo(
                            targetValue = 1f,
                            animationSpec = spring(
                                dampingRatio = 0.85f,
                                stiffness = 920f,
                                visibilityThreshold = 0.002f
                            )
                        ) {
                            pressFractionState.floatValue = value
                        }
                    }
                }

                longPressJob?.cancel()
                if (currentLongPressEnabled) {
                    longPressJob = scope.launch {
                        delay(viewConfig.longPressTimeoutMillis)
                        if (isInsideBounds && !gestureCancelled) {
                            longPressFired = true
                            animateToRest()
                            currentOnLongPress()
                        }
                    }
                }

                try {
                    while (true) {
                        val mainEvent = awaitPointerEvent(pass = PointerEventPass.Main)
                        val mainChange = mainEvent.changes.firstOrNull { it.id == down.id }
                        if (mainChange == null || mainChange.isConsumed) {
                            gestureCancelled = true
                            longPressJob?.cancel()
                            animateToRest()
                            break
                        }

                        var consumedBySelf = false
                        if (!isGestureLocked && !exceededHorizontalSlopEarly) {
                            val heldMs = mainChange.uptimeMillis - downTimeMs
                            val movedX = kotlin.math.abs(mainChange.position.x - down.position.x)
                            if (heldMs >= HOLD_LOCK_DELAY_MS) {
                                isGestureLocked = true
                            } else if (movedX > viewConfig.touchSlop) {
                                exceededHorizontalSlopEarly = true
                            }
                        }
                        if (isGestureLocked) {
                            mainChange.consume()
                            consumedBySelf = true
                        }

                        val event = awaitPointerEvent(pass = PointerEventPass.Final)
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null || (!consumedBySelf && change.isConsumed)) {
                            gestureCancelled = true
                            longPressJob?.cancel()
                            animateToRest()
                            break
                        }

                        val pos = change.position
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        val h = size.height.toFloat().coerceAtLeast(1f)
                        isInsideBounds = pos.x in 0f..w && pos.y in 0f..h

                        if (!change.pressed) {
                            longPressJob?.cancel()
                            if (!longPressFired) {
                                if (isInsideBounds) {
                                    animateToRest()
                                    currentOnSelect()
                                } else {
                                    animateToRest()
                                }
                            }
                            break
                        }

                        val targetStrength = computeSlideOutPressStrength(
                            posX = pos.x,
                            posY = pos.y,
                            widthPx = w,
                            heightPx = h,
                            cancelRadiusPx = cancelRadiusPx
                        )
                        if (isReducedMotion) {
                            val snapTarget = if (isInsideBounds) 1f else 0f
                            if (pressFractionState.floatValue != snapTarget) {
                                pressFractionState.floatValue = snapTarget
                                animJob?.cancel()
                                animJob = scope.launch { pressAnim.snapTo(snapTarget) }
                            }
                        } else if (!isInsideBounds) {
                            animJob?.cancel()
                            pressFractionState.floatValue = targetStrength
                            animJob = scope.launch { pressAnim.snapTo(targetStrength) }
                        } else if (pressAnim.targetValue < 0.999f) {
                            animJob?.cancel()
                            animJob = scope.launch {
                                pressAnim.animateTo(
                                    targetValue = 1f,
                                    animationSpec = spring(
                                        dampingRatio = 0.85f,
                                        stiffness = 880f,
                                        visibilityThreshold = 0.002f
                                    )
                                ) {
                                    pressFractionState.floatValue = value
                                }
                            }
                        }
                    }
                } finally {
                    longPressJob?.cancel()
                }
            }
        }
}
