package com.example.totalcheck.ui.main

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.example.totalcheck.R
import com.example.totalcheck.data.reminder.ReminderFormatter
import com.example.totalcheck.ui.theme.LocalReducedMotion
import java.time.LocalTime
import java.util.Locale
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.sin

private val WHEEL_HEIGHT = 172.dp
private val WHEEL_WIDTH = 92.dp
private val WHEEL_ROW_STEP = 36.dp
private val WHEEL_CYLINDER_RADIUS = 104.dp
private val FAST_STEP_VELOCITY_THRESHOLD = 450.dp
private const val HAPTIC_DEBOUNCE_MS = 18L

@Immutable
internal data class TimeSlotState(
    val digit: Char? = null,
    val isAutoZero: Boolean = false,
    val entrySeq: Int = 0
)

@Immutable
internal data class SmartTimeInputState(
    val slots: List<TimeSlotState> = List(4) { TimeSlotState() },
    val activeSlotIndex: Int = 0,
    val nextEntrySeq: Int = 1
) {
    val isComplete: Boolean
        get() = activeSlotIndex >= 4 && slots.all { it.digit != null }

    val hasAnyDigit: Boolean
        get() = slots.any { it.digit != null }
}

internal fun applySmartDigit(
    state: SmartTimeInputState,
    ch: Char
): Pair<SmartTimeInputState, Boolean> {
    if (ch !in '0'..'9' || state.activeSlotIndex >= 4) return state to false
    val seq = state.nextEntrySeq
    val nextSlots = state.slots.toMutableList()
    return when (state.activeSlotIndex) {
        0 -> {
            if (ch in '0'..'2') {
                nextSlots[0] = TimeSlotState(digit = ch, isAutoZero = false, entrySeq = seq)
                state.copy(slots = nextSlots, activeSlotIndex = 1, nextEntrySeq = seq + 1) to false
            } else {
                nextSlots[0] = TimeSlotState(digit = '0', isAutoZero = true, entrySeq = seq)
                nextSlots[1] = TimeSlotState(digit = ch, isAutoZero = false, entrySeq = seq + 1)
                state.copy(slots = nextSlots, activeSlotIndex = 2, nextEntrySeq = seq + 2) to false
            }
        }
        1 -> {
            val h1 = state.slots[0].digit
            if (h1 == '2' && ch !in '0'..'3') {
                state to true
            } else {
                nextSlots[1] = TimeSlotState(digit = ch, isAutoZero = false, entrySeq = seq)
                state.copy(slots = nextSlots, activeSlotIndex = 2, nextEntrySeq = seq + 1) to false
            }
        }
        2 -> {
            if (ch in '0'..'5') {
                nextSlots[2] = TimeSlotState(digit = ch, isAutoZero = false, entrySeq = seq)
                state.copy(slots = nextSlots, activeSlotIndex = 3, nextEntrySeq = seq + 1) to false
            } else {
                nextSlots[2] = TimeSlotState(digit = '0', isAutoZero = true, entrySeq = seq)
                nextSlots[3] = TimeSlotState(digit = ch, isAutoZero = false, entrySeq = seq + 1)
                state.copy(slots = nextSlots, activeSlotIndex = 4, nextEntrySeq = seq + 2) to false
            }
        }
        3 -> {
            nextSlots[3] = TimeSlotState(digit = ch, isAutoZero = false, entrySeq = seq)
            state.copy(slots = nextSlots, activeSlotIndex = 4, nextEntrySeq = seq + 1) to false
        }
        else -> state to false
    }
}

internal fun applySmartBackspace(state: SmartTimeInputState): SmartTimeInputState {
    val nextSlots = state.slots.toMutableList()
    return when (state.activeSlotIndex) {
        4 -> {
            if (nextSlots[2].isAutoZero) {
                nextSlots[2] = TimeSlotState()
                nextSlots[3] = TimeSlotState()
                state.copy(slots = nextSlots, activeSlotIndex = 2)
            } else {
                nextSlots[3] = TimeSlotState()
                state.copy(slots = nextSlots, activeSlotIndex = 3)
            }
        }
        3 -> {
            nextSlots[2] = TimeSlotState()
            state.copy(slots = nextSlots, activeSlotIndex = 2)
        }
        2 -> {
            if (nextSlots[0].isAutoZero) {
                nextSlots[0] = TimeSlotState()
                nextSlots[1] = TimeSlotState()
                state.copy(slots = nextSlots, activeSlotIndex = 0)
            } else {
                nextSlots[1] = TimeSlotState()
                state.copy(slots = nextSlots, activeSlotIndex = 1)
            }
        }
        1 -> {
            nextSlots[0] = TimeSlotState()
            state.copy(slots = nextSlots, activeSlotIndex = 0)
        }
        else -> state
    }
}

internal fun resolveSmartTime(state: SmartTimeInputState): Pair<Int, Int>? {
    if (!state.hasAnyDigit) return null
    val h1 = state.slots[0].digit
    val h2 = state.slots[1].digit
    val m1 = state.slots[2].digit
    val m2 = state.slots[3].digit
    val hour = when {
        h1 != null && h2 != null -> h1.digitToInt() * 10 + h2.digitToInt()
        h1 != null -> h1.digitToInt()
        else -> 0
    }
    val minute = when {
        m1 != null && m2 != null -> m1.digitToInt() * 10 + m2.digitToInt()
        m1 != null -> m1.digitToInt() * 10
        else -> 0
    }
    return if (hour in 0..23 && minute in 0..59) hour to minute else null
}

/**
 * M9 - Part 2 flat, zero-nested-containers Smart Reminder configuration body.
 *
 * Designed to live directly inside [ScopedModalHost]'s single outer Surface:
 * - Block A: 3D Tactile Drum Wheel (00..23 / 00..59) + tap-to-4-digit keyboard input
 * - Block B: Cumulative relative quick-add chips (+15m / +1h / +4h / ↺ Now)
 * - Block C: 3-state LiquidTabBar (Weekdays / Weekends / Daily, tap active to clear) +
 *   staggered bulk-selection weekday circle row
 * - Block D: Borderless single-line smart filter switch with 2.dp vertical Ripple Feedback
 * - Block E: Edit-only Delete Reminder pill button (no test notification button)
 */
@Composable
internal fun ReminderConfigBody(
    sessionKey: Int,
    selectedHour: Int,
    selectedMinute: Int,
    repeatMask: Int,
    onlyIfUnchecked: Boolean,
    hasExistingReminder: Boolean,
    onlyIfUncheckedTitle: String,
    isDirectInputMode: Boolean,
    smartTimeState: SmartTimeInputState,
    invalidShakeSignal: Int,
    primaryFocus: FocusRequester,
    activeCancelSignal: Int,
    systemImeHideSignal: Int,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
    onSmartTimeStateChange: (SmartTimeInputState) -> Unit,
    onRepeatMaskChange: (Int) -> Unit,
    onOnlyIfUncheckedChange: (Boolean) -> Unit,
    onEnterDirectInput: () -> Unit,
    onExitDirectInput: (imeWasFocusLost: Boolean) -> Unit,
    onDirectInputFocusChanged: (Boolean) -> Unit,
    onDeleteExisting: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val isReducedMotion = LocalReducedMotion.current
    val scope = rememberCoroutineScope()

    var directInputErrorHint by remember(sessionKey) { mutableStateOf(false) }
    var preInputHour by remember(sessionKey) { mutableIntStateOf(selectedHour) }
    var preInputMinute by remember(sessionKey) { mutableIntStateOf(selectedMinute) }
    val shakeOffsetPx = remember(sessionKey) { Animatable(0f) }
    var shakeJob by remember(sessionKey) { mutableStateOf<Job?>(null) }

    val currentHour by rememberUpdatedState(selectedHour)
    val currentMinute by rememberUpdatedState(selectedMinute)
    val currentIsDirectInput by rememberUpdatedState(isDirectInputMode)
    val currentSmartTimeState by rememberUpdatedState(smartTimeState)

    fun triggerInvalidSlotShake() {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        directInputErrorHint = true
        if (!isReducedMotion) {
            shakeJob?.cancel()
            shakeJob = scope.launch {
                val amp = with(density) { 4.dp.toPx() }
                shakeOffsetPx.snapTo(0f)
                val keyOffsets = listOf(-amp, amp, -amp * 0.65f, amp * 0.65f, -amp * 0.25f, 0f)
                keyOffsets.forEach { target ->
                    shakeOffsetPx.animateTo(
                        targetValue = target,
                        animationSpec = tween(durationMillis = 36, easing = FastOutSlowInEasing)
                    )
                }
            }
        }
    }

    LaunchedEffect(invalidShakeSignal) {
        if (invalidShakeSignal > 0 && currentIsDirectInput) {
            triggerInvalidSlotShake()
        }
    }

    fun tryCommitSmartState(candidate: SmartTimeInputState, allowEmptyFallback: Boolean) {
        if (!candidate.hasAnyDigit) {
            if (allowEmptyFallback) {
                directInputErrorHint = false
                onExitDirectInput(false)
            }
            return
        }
        val resolved = resolveSmartTime(candidate)
        if (resolved != null) {
            directInputErrorHint = false
            onSmartTimeStateChange(SmartTimeInputState())
            onHourChange(resolved.first)
            onMinuteChange(resolved.second)
            onExitDirectInput(false)
        } else {
            triggerInvalidSlotShake()
        }
    }

    // Auto-apply 120ms after all 4 slots are filled so the user briefly sees the final complete time
    LaunchedEffect(smartTimeState.isComplete, isDirectInputMode) {
        if (isDirectInputMode && smartTimeState.isComplete) {
            delay(120L)
            val latest = currentSmartTimeState
            if (currentIsDirectInput && latest.isComplete) {
                tryCommitSmartState(latest, allowEmptyFallback = false)
            }
        }
    }

    // Active cancel from scrim tap or Back button: revert to pre-edit time and return to 3D wheel.
    LaunchedEffect(activeCancelSignal) {
        if (activeCancelSignal > 0 && currentIsDirectInput) {
            onSmartTimeStateChange(SmartTimeInputState())
            directInputErrorHint = false
            onHourChange(preInputHour)
            onMinuteChange(preInputMinute)
            onExitDirectInput(true)
        }
    }

    // System IME dismissed (e.g. keyboard minimize arrow): auto left-pad valid partial input or revert.
    LaunchedEffect(systemImeHideSignal) {
        if (systemImeHideSignal > 0 && currentIsDirectInput) {
            val resolved = resolveSmartTime(currentSmartTimeState)
            if (resolved != null) {
                onHourChange(resolved.first)
                onMinuteChange(resolved.second)
            } else {
                onHourChange(preInputHour)
                onMinuteChange(preInputMinute)
            }
            onSmartTimeStateChange(SmartTimeInputState())
            directInputErrorHint = false
            onExitDirectInput(true)
        }
    }

    val openDirectInput: () -> Unit = {
        preInputHour = currentHour
        preInputMinute = currentMinute
        onSmartTimeStateChange(SmartTimeInputState())
        directInputErrorHint = false
        onEnterDirectInput()
    }

    // Block C staggered wave trigger (only on preset tab tap, not individual weekday circle clicks)
    var bulkPresetWaveKey by remember(sessionKey) { mutableIntStateOf(0) }
    var bulkPresetTriggeredAt by remember(sessionKey) { mutableLongStateOf(0L) }

    // Block D Ripple Feedback resonance offset shared with label and weekday circle row
    val resonanceOffsetPx = remember(sessionKey) { Animatable(0f) }

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // ── Block A: 3D Tactile Drum Wheel <-> 4-Digit Direct Keyboard Input ──────────────
        AnimatedContent(
            targetState = isDirectInputMode,
            transitionSpec = {
                if (isReducedMotion) {
                    fadeIn(tween(1)) togetherWith fadeOut(tween(1))
                } else {
                    (fadeIn(tween(180)) + scaleIn(initialScale = 0.94f, animationSpec = tween(200)))
                        .togetherWith(
                            fadeOut(tween(140)) + scaleOut(targetScale = 0.94f, animationSpec = tween(140))
                        )
                }
            },
            label = "reminder_wheel_input_transition"
        ) { directMode ->
            if (!directMode) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(WHEEL_HEIGHT),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TactileDrumWheel(
                            sessionKey = sessionKey,
                            value = selectedHour,
                            rangeSize = 24,
                            contentDescription = stringResource(R.string.cd_reminder_hour_wheel),
                            onValueSettled = onHourChange,
                            onCenterTap = openDirectInput,
                            modifier = Modifier.testTag("reminder_hour_wheel")
                        )
                        Text(
                            text = ":",
                            style = MaterialTheme.typography.displaySmall.copy(
                                fontSize = 38.sp,
                                fontWeight = FontWeight.Bold,
                                fontFeatureSettings = "tnum"
                            ),
                            color = colorScheme.onSurface,
                            modifier = Modifier
                                .padding(horizontal = 8.dp)
                                .graphicsLayer { translationY = -2.dp.toPx() }
                        )
                        TactileDrumWheel(
                            sessionKey = sessionKey,
                            value = selectedMinute,
                            rangeSize = 60,
                            enableDualSpeed = true,
                            contentDescription = stringResource(R.string.cd_reminder_minute_wheel),
                            onValueSettled = onMinuteChange,
                            onCenterTap = openDirectInput,
                            modifier = Modifier.testTag("reminder_minute_wheel")
                        )
                    }
                }
            } else {
                SmartFourSlotTimeInput(
                    sessionKey = sessionKey,
                    state = smartTimeState,
                    placeholderTime = ReminderFormatter.formatTime(selectedHour, selectedMinute),
                    hasError = directInputErrorHint,
                    shakeOffsetPxProvider = { shakeOffsetPx.value },
                    focusRequester = primaryFocus,
                    onFocusChanged = onDirectInputFocusChanged,
                    onDigitTyped = { ch ->
                        val (nextState, rejected) = applySmartDigit(currentSmartTimeState, ch)
                        if (rejected) {
                            triggerInvalidSlotShake()
                        } else {
                            directInputErrorHint = false
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSmartTimeStateChange(nextState)
                        }
                    },
                    onBackspace = {
                        directInputErrorHint = false
                        val nextState = applySmartBackspace(currentSmartTimeState)
                        if (nextState != currentSmartTimeState) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSmartTimeStateChange(nextState)
                        }
                    },
                    onDone = {
                        tryCommitSmartState(currentSmartTimeState, allowEmptyFallback = true)
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Block B: Cumulative Relative Time Chips Row ───────────────────────────────────
        val commitBeforeQuickAction: () -> Unit = {
            if (isDirectInputMode) {
                val resolved = resolveSmartTime(currentSmartTimeState)
                if (resolved != null) {
                    onHourChange(resolved.first)
                    onMinuteChange(resolved.second)
                }
                onSmartTimeStateChange(SmartTimeInputState())
                directInputErrorHint = false
                onExitDirectInput(false)
            }
        }

        val addRelativeMinutes: (Int) -> Unit = { deltaMinutes ->
            commitBeforeQuickAction()
            val total = ((currentHour * 60 + currentMinute) + deltaMinutes) % 1440
            val normalizedTotal = if (total < 0) total + 1440 else total
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onHourChange(normalizedTotal / 60)
            onMinuteChange(normalizedTotal % 60)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            QuickTimeCapsuleChip(
                label = stringResource(R.string.reminder_quick_plus_15m),
                testTag = "reminder_quick_15m",
                onClick = { addRelativeMinutes(15) },
                modifier = Modifier.weight(1f)
            )
            QuickTimeCapsuleChip(
                label = stringResource(R.string.reminder_quick_plus_1h),
                testTag = "reminder_quick_1h",
                onClick = { addRelativeMinutes(60) },
                modifier = Modifier.weight(1f)
            )
            QuickTimeCapsuleChip(
                label = stringResource(R.string.reminder_quick_plus_4h),
                testTag = "reminder_quick_4h",
                onClick = { addRelativeMinutes(240) },
                modifier = Modifier.weight(1f)
            )
            QuickTimeCapsuleChip(
                label = stringResource(R.string.reminder_quick_now),
                testTag = "reminder_quick_now",
                onClick = {
                    commitBeforeQuickAction()
                    val now = LocalTime.now()
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onHourChange(now.hour)
                    onMinuteChange(now.minute)
                },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── Block C: Three-State LiquidTabBar + Staggered Weekday Circles ─────────────────
        val presetMasks = remember {
            listOf(
                ReminderFormatter.WEEKDAYS_MASK,
                ReminderFormatter.WEEKENDS_MASK,
                ReminderFormatter.DAILY_MASK
            )
        }
        val presetLabels = listOf(
            stringResource(R.string.reminder_tab_weekdays),
            stringResource(R.string.reminder_tab_weekends),
            stringResource(R.string.reminder_tab_daily),
        )
        val normalizedMask = repeatMask and ReminderFormatter.DAILY_MASK
        val selectedPresetIndex = when (normalizedMask) {
            ReminderFormatter.WEEKDAYS_MASK -> 0
            ReminderFormatter.WEEKENDS_MASK -> 1
            ReminderFormatter.DAILY_MASK -> 2
            else -> null
        }

        LiquidTabBar(
            labels = presetLabels,
            selectedIndex = selectedPresetIndex,
            onSelect = { index ->
                val tappedMask = presetMasks[index]
                if (selectedPresetIndex == index) {
                    onRepeatMaskChange(0)
                } else {
                    onRepeatMaskChange(tappedMask)
                    bulkPresetTriggeredAt = SystemClock.uptimeMillis()
                    bulkPresetWaveKey++
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("reminder_repeat_liquid_tabs")
        )

        Spacer(modifier = Modifier.height(10.dp))

        val dayLabels = listOf(
            stringResource(R.string.reminder_day_mon),
            stringResource(R.string.reminder_day_tue),
            stringResource(R.string.reminder_day_wed),
            stringResource(R.string.reminder_day_thu),
            stringResource(R.string.reminder_day_fri),
            stringResource(R.string.reminder_day_sat),
            stringResource(R.string.reminder_day_sun),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationY = resonanceOffsetPx.value
                },
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            dayLabels.forEachIndexed { bitIdx, dayLabel ->
                val bit = 1 shl bitIdx
                val isDaySelected = (normalizedMask and bit) != 0
                StaggeredWeekdayCircle(
                    label = dayLabel,
                    bitIndex = bitIdx,
                    selected = isDaySelected,
                    waveKey = bulkPresetWaveKey,
                    waveTriggeredAtMs = bulkPresetTriggeredAt,
                    onClick = {
                        val nextMask = if (isDaySelected) {
                            normalizedMask and bit.inv()
                        } else {
                            normalizedMask or bit
                        }
                        onRepeatMaskChange(nextMask)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("reminder_weekday_$bitIdx")
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // ── Block D: Borderless Single-Line Smart Filter Switch + Ripple Feedback ─────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = onlyIfUncheckedTitle,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer {
                        translationY = resonanceOffsetPx.value
                    }
            )
            Switch(
                checked = onlyIfUnchecked,
                onCheckedChange = { checked ->
                    onOnlyIfUncheckedChange(checked)
                    if (!isReducedMotion) {
                        scope.launch {
                            val amplitudePx = with(density) { 2.dp.toPx() }
                            resonanceOffsetPx.animateTo(
                                targetValue = amplitudePx,
                                animationSpec = spring(dampingRatio = 0.72f, stiffness = 880f)
                            )
                            resonanceOffsetPx.animateTo(
                                targetValue = 0f,
                                animationSpec = spring(dampingRatio = 0.45f, stiffness = 360f)
                            )
                        }
                    }
                },
                modifier = Modifier.testTag("reminder_only_unchecked_switch")
            )
        }

        // ── Block E: Edit-Only Delete Button (Test Notification Button Hidden) ────────────
        if (hasExistingReminder) {
            Spacer(modifier = Modifier.height(14.dp))
            TactileButton(
                onClick = onDeleteExisting,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("reminder_delete_button"),
                shape = RoundedCornerShape(50),
                containerColor = colorScheme.errorContainer.copy(alpha = 0.72f),
                contentColor = colorScheme.onErrorContainer,
                border = BorderStroke(1.dp, colorScheme.error.copy(alpha = 0.35f)),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.action_delete_reminder),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * Zero-recomposition 3D cylindrical drum wheel.
 *
 * Pre-measures all 2-digit numbers once via [TextMeasurer] and draws the 7 pooled slots
 * strictly inside [graphicsLayer] (rotationX, scaleX/Y, translationY, alpha) and [drawBehind],
 * ensuring 0 recompositions during drag, velocity fling, overshoot, and spring snap.
 */
@Composable
private fun TactileDrumWheel(
    sessionKey: Int,
    value: Int,
    rangeSize: Int,
    contentDescription: String,
    onValueSettled: (Int) -> Unit,
    onCenterTap: () -> Unit,
    modifier: Modifier = Modifier,
    enableDualSpeed: Boolean = false
) {
    val colorScheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val viewConfig = LocalViewConfiguration.current
    val isReducedMotion = LocalReducedMotion.current
    val scope = rememberCoroutineScope()

    val selectedTextColor = colorScheme.primary
    val unselectedTextColor = colorScheme.onSurfaceVariant

    val textMeasurer = rememberTextMeasurer()
    val boldDigitStyle = MaterialTheme.typography.displaySmall.copy(
        fontSize = 42.sp,
        fontWeight = FontWeight.Bold,
        fontFeatureSettings = "tnum",
        lineHeight = 46.sp
    )
    val subDigitStyle = MaterialTheme.typography.displaySmall.copy(
        fontSize = 42.sp,
        fontWeight = FontWeight.Medium,
        fontFeatureSettings = "tnum",
        lineHeight = 46.sp
    )
    val measuredBoldDigits = remember(textMeasurer, boldDigitStyle, rangeSize) {
        List(rangeSize) { idx ->
            textMeasurer.measure(
                text = String.format(Locale.US, "%02d", idx),
                style = boldDigitStyle
            )
        }
    }
    val measuredSubDigits = remember(textMeasurer, subDigitStyle, rangeSize, enableDualSpeed) {
        if (!enableDualSpeed) {
            measuredBoldDigits
        } else {
            List(rangeSize) { idx ->
                textMeasurer.measure(
                    text = String.format(Locale.US, "%02d", idx),
                    style = subDigitStyle
                )
            }
        }
    }

    val rowStepPx = with(density) { WHEEL_ROW_STEP.toPx() }
    val cylinderRadiusPx = with(density) { WHEEL_CYLINDER_RADIUS.toPx() }
    val fastThresholdPxPerSec = with(density) { FAST_STEP_VELOCITY_THRESHOLD.toPx() }
    val fastTicksPerSecThreshold = fastThresholdPxPerSec / rowStepPx
    val continuousTick = remember(sessionKey) { Animatable(value.toFloat()) }
    var isDraggingOrFlinging by remember(sessionKey) { mutableStateOf(false) }
    var isFastStepMode by remember(sessionKey) { mutableStateOf(false) }
    var lastSyncedValue by remember(sessionKey) { mutableIntStateOf(value) }
    var logicalTargetTick by remember(sessionKey) { mutableFloatStateOf(value.toFloat()) }
    var suppressHaptics by remember(sessionKey) { mutableStateOf(false) }
    var animJob by remember(sessionKey) { mutableStateOf<Job?>(null) }

    val currentOnValueSettled by rememberUpdatedState(onValueSettled)
    val currentOnCenterTap by rememberUpdatedState(onCenterTap)

    val fadeBrush = remember {
        Brush.verticalGradient(
            0.0f to Color.Transparent,
            0.22f to Color.Black,
            0.78f to Color.Black,
            1.0f to Color.Transparent
        )
    }

    DisposableEffect(Unit) {
        onDispose { animJob?.cancel() }
    }

    // Debounced graded gear haptic feedback:
    // - Fast 5x mode on minute wheel: clicks every 5 ticks moved (preserving starting remainder)
    // - Slow 1x mode / hour wheel: clicks on every integer tick
    LaunchedEffect(continuousTick, enableDualSpeed, fastTicksPerSecThreshold) {
        var lastVibratedTick = round(continuousTick.value).toInt()
        var lastHapticMs = 0L
        snapshotFlow { round(continuousTick.value).toInt() }.collect { currentTickInt ->
            if (suppressHaptics) {
                lastVibratedTick = currentTickInt
                return@collect
            }
            if (currentTickInt != lastVibratedTick) {
                val currentSpeedTicks = abs(continuousTick.velocity)
                val isFastStride = enableDualSpeed && isFastStepMode &&
                    (currentSpeedTicks == 0f || currentSpeedTicks >= fastTicksPerSecThreshold * 2.0f)
                val requiredStep = if (isFastStride) 5 else 1
                if (abs(currentTickInt - lastVibratedTick) >= requiredStep) {
                    val nowMs = SystemClock.uptimeMillis()
                    if (nowMs - lastHapticMs >= HAPTIC_DEBOUNCE_MS) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        lastHapticMs = nowMs
                    }
                    lastVibratedTick = currentTickInt
                }
            }
        }
    }

    // Sync external target updates (e.g. +15m, +1h, +4h, Now, or 4-digit direct commit).
    // Computes shortest modular delta from lastSyncedValue so rapid multi-taps accumulate monotonically.
    LaunchedEffect(value, isReducedMotion) {
        if (isDraggingOrFlinging) return@LaunchedEffect
        val normalizedTarget = Math.floorMod(value, rangeSize)
        if (normalizedTarget == lastSyncedValue &&
            abs(continuousTick.value - round(continuousTick.value)) < 0.01f
        ) {
            return@LaunchedEffect
        }
        var delta = normalizedTarget - lastSyncedValue
        val half = rangeSize / 2
        if (delta > half) delta -= rangeSize
        if (delta < -half) delta += rangeSize

        // If external change jumped straight without intermediate frames, anchor logicalTargetTick safely
        if (abs(logicalTargetTick - continuousTick.value) > rangeSize * 1.5f) {
            logicalTargetTick = round(continuousTick.value)
        }
        logicalTargetTick += delta
        lastSyncedValue = normalizedTarget
        isFastStepMode = false

        animJob?.cancel()
        if (isReducedMotion) {
            suppressHaptics = true
            continuousTick.snapTo(normalizedTarget.toFloat())
            logicalTargetTick = normalizedTarget.toFloat()
            suppressHaptics = false
        } else {
            val targetSnapshot = logicalTargetTick
            animJob = scope.launch {
                continuousTick.animateTo(
                    targetValue = targetSnapshot,
                    animationSpec = spring(
                        dampingRatio = 0.78f,
                        stiffness = 360f,
                        visibilityThreshold = 0.002f
                    )
                )
                val settledMod = Math.floorMod(round(continuousTick.value).toInt(), rangeSize)
                if (abs(continuousTick.value - settledMod) > 0.5f) {
                    suppressHaptics = true
                    continuousTick.snapTo(settledMod.toFloat())
                    logicalTargetTick = settledMod.toFloat()
                    suppressHaptics = false
                }
            }
        }
    }

    val stateDesc = String.format(Locale.US, "%02d", Math.floorMod(value, rangeSize))

    Box(
        modifier = modifier
            .width(WHEEL_WIDTH)
            .height(WHEEL_HEIGHT)
            .semantics {
                this.contentDescription = contentDescription
                this.stateDescription = stateDesc
            }
            .pointerInput(rangeSize, isReducedMotion, enableDualSpeed) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    animJob?.cancel()
                    isDraggingOrFlinging = true
                    isFastStepMode = false

                    val velocityTracker = VelocityTracker()
                    velocityTracker.addPosition(down.uptimeMillis, down.position)
                    var totalDragPx = 0f
                    var exceededSlop = false
                    var lastY = down.position.y
                    var lastUptimeMs = down.uptimeMillis
                    var emaSpeedPxPerSec = 0f
                    var runningTick = continuousTick.value
                    val slop = viewConfig.touchSlop

                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            change.consume()
                            break
                        }
                        val dy = change.position.y - lastY
                        val dtMs = (change.uptimeMillis - lastUptimeMs).coerceAtLeast(1L)
                        lastY = change.position.y
                        lastUptimeMs = change.uptimeMillis
                        totalDragPx += abs(dy)
                        if (totalDragPx > slop) {
                            exceededSlop = true
                        }
                        change.consume()
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        if (exceededSlop) {
                            val instSpeed = abs(dy) * 1000f / dtMs
                            emaSpeedPxPerSec = emaSpeedPxPerSec * 0.55f + instSpeed * 0.45f
                            val isFastDrag = enableDualSpeed &&
                                emaSpeedPxPerSec >= fastThresholdPxPerSec
                            isFastStepMode = isFastDrag
                            val stepMultiplier = if (isFastDrag) 5f else 1f
                            runningTick += (-dy / rowStepPx) * stepMultiplier
                            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                                continuousTick.snapTo(runningTick)
                            }
                        }
                    }

                    if (!exceededSlop) {
                        isFastStepMode = false
                        val relY = down.position.y - (size.height * 0.5f)
                        if (abs(relY) <= rowStepPx * 0.72f) {
                            isDraggingOrFlinging = false
                            currentOnCenterTap()
                        } else {
                            val rowDelta = (relY / rowStepPx).roundToInt().coerceIn(-2, 2)
                            val targetTick = round(continuousTick.value) + rowDelta
                            animJob = scope.launch {
                                try {
                                    if (isReducedMotion) {
                                        continuousTick.snapTo(targetTick)
                                    } else {
                                        continuousTick.animateTo(
                                            targetValue = targetTick,
                                            animationSpec = spring(
                                                dampingRatio = 0.78f,
                                                stiffness = 360f,
                                                visibilityThreshold = 0.002f
                                            )
                                        )
                                    }
                                    val settledMod = Math.floorMod(
                                        round(continuousTick.value).toInt(),
                                        rangeSize
                                    )
                                    suppressHaptics = true
                                    continuousTick.snapTo(settledMod.toFloat())
                                    suppressHaptics = false
                                    lastSyncedValue = settledMod
                                    logicalTargetTick = settledMod.toFloat()
                                    currentOnValueSettled(settledMod)
                                } finally {
                                    isDraggingOrFlinging = false
                                }
                            }
                        }
                    } else {
                        val releaseVelocityYPx = -velocityTracker.calculateVelocity().y
                        val isFastFling = enableDualSpeed &&
                            abs(releaseVelocityYPx) >= fastThresholdPxPerSec
                        isFastStepMode = isFastFling
                        val speedMultiplier = if (isFastFling) 5f else 1f
                        val maxVelocityTicks = if (isFastFling) 120f else 55f
                        val velocityTicksPerSec = ((releaseVelocityYPx / rowStepPx) * speedMultiplier)
                            .coerceIn(-maxVelocityTicks, maxVelocityTicks)
                        animJob = scope.launch {
                            try {
                                if (isReducedMotion) {
                                    continuousTick.snapTo(round(continuousTick.value))
                                } else {
                                    if (abs(velocityTicksPerSec) > 1.8f) {
                                        val decay = exponentialDecay<Float>(
                                            frictionMultiplier = if (isFastFling) 2.0f else 2.6f
                                        )
                                        continuousTick.animateDecay(velocityTicksPerSec, decay)
                                    }
                                    isFastStepMode = false
                                    val nearestTick = round(continuousTick.value)
                                    continuousTick.animateTo(
                                        targetValue = nearestTick,
                                        initialVelocity = if (abs(velocityTicksPerSec) <= 1.8f) {
                                            velocityTicksPerSec
                                        } else {
                                            0f
                                        },
                                        animationSpec = spring(
                                            dampingRatio = 0.78f,
                                            stiffness = 360f,
                                            visibilityThreshold = 0.002f
                                        )
                                    )
                                }
                                val settledMod = Math.floorMod(
                                    round(continuousTick.value).toInt(),
                                    rangeSize
                                )
                                suppressHaptics = true
                                continuousTick.snapTo(settledMod.toFloat())
                                suppressHaptics = false
                                lastSyncedValue = settledMod
                                logicalTargetTick = settledMod.toFloat()
                                currentOnValueSettled(settledMod)
                            } finally {
                                isFastStepMode = false
                                isDraggingOrFlinging = false
                            }
                        }
                    }
                }
            }
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithContent {
                drawContent()
                drawRect(brush = fadeBrush, blendMode = BlendMode.DstIn)
            }
    ) {
        for (slotIdx in 0 until 7) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val tick = continuousTick.value
                        val centerInt = floor(tick + 0.5f).toInt()
                        val rem = Math.floorMod(centerInt, 7)
                        val deltaFromCenter = Math.floorMod(slotIdx - rem + 3, 7) - 3
                        val slotTick = centerInt + deltaFromCenter
                        val offsetRows = slotTick - tick
                        val absOffset = abs(offsetRows)
                        val angleDeg = (offsetRows * 21f).coerceIn(-68f, 68f)
                        val angleRad = Math.toRadians(angleDeg.toDouble()).toFloat()
                        val scale = lerp(1.0f, 0.72f, (absOffset / 2f).coerceIn(0f, 1f))
                        rotationX = -angleDeg
                        scaleX = scale
                        scaleY = scale
                        translationY = sin(angleRad) * cylinderRadiusPx
                        cameraDistance = 12f * density.density
                        alpha = 1f - ((absOffset - 2.0f) / 0.85f).coerceIn(0f, 1f)
                    }
                    .drawBehind {
                        val tick = continuousTick.value
                        val centerInt = floor(tick + 0.5f).toInt()
                        val rem = Math.floorMod(centerInt, 7)
                        val deltaFromCenter = Math.floorMod(slotIdx - rem + 3, 7) - 3
                        val slotTick = centerInt + deltaFromCenter
                        val offsetRows = slotTick - tick
                        val modValue = Math.floorMod(slotTick, rangeSize)
                        val isMajorTick = !enableDualSpeed || (modValue % 5 == 0)
                        val centerProximity = (1f - (abs(offsetRows) / 0.7f)).coerceIn(0f, 1f)
                        val layout = if (isMajorTick || centerProximity >= 0.5f) {
                            measuredBoldDigits[modValue]
                        } else {
                            measuredSubDigits[modValue]
                        }
                        val baseColor = lerpColor(unselectedTextColor, selectedTextColor, centerProximity)
                        val subAlphaFactor = if (isMajorTick) {
                            1f
                        } else {
                            lerp(0.78f, 1f, centerProximity)
                        }
                        val color = baseColor.copy(alpha = baseColor.alpha * subAlphaFactor)
                        drawText(
                            textLayoutResult = layout,
                            color = color,
                            topLeft = Offset(
                                x = (size.width - layout.size.width) * 0.5f,
                                y = (size.height - layout.size.height) * 0.5f
                            )
                        )
                    }
            )
        }
    }
}

@Composable
private fun SmartFourSlotTimeInput(
    sessionKey: Int,
    state: SmartTimeInputState,
    placeholderTime: String,
    hasError: Boolean,
    shakeOffsetPxProvider: () -> Float,
    focusRequester: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    onDigitTyped: (Char) -> Unit,
    onBackspace: () -> Unit,
    onDone: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val keyboardController = LocalSoftwareKeyboardController.current
    val isReducedMotion = LocalReducedMotion.current
    val displayDigitStyle = MaterialTheme.typography.displaySmall.copy(
        fontSize = 42.sp,
        fontWeight = FontWeight.Bold,
        fontFeatureSettings = "tnum"
    )
    val hintText = stringResource(R.string.reminder_direct_input_label)
    val errorText = stringResource(R.string.reminder_direct_input_error)
    val cdInput = stringResource(R.string.cd_reminder_direct_input_hint)

    var imeAnchorValue by remember(sessionKey) {
        mutableStateOf(TextFieldValue(text = "0000", selection = TextRange(4)))
    }

    val slotCentersX = remember(sessionKey) { mutableStateListOf(0f, 0f, 0f, 0f) }
    val clampedActiveSlot = state.activeSlotIndex.coerceIn(0, 3)
    val activeCenterX = slotCentersX[clampedActiveSlot]
    val hasMeasuredSlots = activeCenterX > 0f
    var snapFirstIndicator by remember(sessionKey) { mutableStateOf(true) }

    LaunchedEffect(hasMeasuredSlots) {
        if (hasMeasuredSlots && snapFirstIndicator) {
            withFrameNanos { }
            snapFirstIndicator = false
        }
    }

    val animatedIndicatorOffset by animateOffsetAsState(
        targetValue = Offset(x = activeCenterX, y = 0f),
        animationSpec = if (snapFirstIndicator || isReducedMotion) {
            snap()
        } else {
            spring(dampingRatio = 0.75f, stiffness = 420f)
        },
        label = "smart_slot_focus_indicator"
    )
    val indicatorAlpha = remember(sessionKey) { Animatable(0f) }
    LaunchedEffect(hasMeasuredSlots, state.isComplete, isReducedMotion) {
        val targetAlpha = if (hasMeasuredSlots && !state.isComplete) 1f else 0f
        if (isReducedMotion || snapFirstIndicator) {
            indicatorAlpha.snapTo(targetAlpha)
        } else {
            indicatorAlpha.animateTo(
                targetValue = targetAlpha,
                animationSpec = tween(durationMillis = 120)
            )
        }
    }

    LaunchedEffect(focusRequester) {
        runCatching {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(WHEEL_HEIGHT)
            .graphicsLayer {
                translationX = shakeOffsetPxProvider()
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = cdInput }
        ) {
            val primaryColor = colorScheme.primary
            val errorColor = colorScheme.error
            val placeholders = listOf(
                placeholderTime.getOrNull(0)?.toString() ?: "0",
                placeholderTime.getOrNull(1)?.toString() ?: "0",
                placeholderTime.getOrNull(3)?.toString() ?: "0",
                placeholderTime.getOrNull(4)?.toString() ?: "0",
            )

            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .drawBehind {
                        val alphaVal = indicatorAlpha.value.coerceIn(0f, 1f)
                        if (hasMeasuredSlots && alphaVal > 0.005f) {
                            val cx = animatedIndicatorOffset.x
                            val pillW = 28.dp.toPx()
                            val pillH = 3.dp.toPx()
                            val pillR = pillH / 2f
                            val pillTop = size.height - pillH
                            val barColor = if (hasError) errorColor else primaryColor
                            drawRoundRect(
                                color = barColor.copy(alpha = alphaVal),
                                topLeft = Offset(cx - pillW * 0.5f, pillTop),
                                size = Size(pillW, pillH),
                                cornerRadius = CornerRadius(pillR, pillR)
                            )
                        }
                    }
            ) {
                SmartDigitSlot(
                    slotIndex = 0,
                    slotState = state.slots[0],
                    placeholderDigit = placeholders[0],
                    isActive = !state.isComplete && state.activeSlotIndex == 0,
                    hasError = hasError && state.activeSlotIndex == 0,
                    displayDigitStyle = displayDigitStyle,
                    isReducedMotion = isReducedMotion,
                    onPositionedCenterX = { cx ->
                        if (abs(slotCentersX[0] - cx) > 0.5f) slotCentersX[0] = cx
                    }
                )
                Spacer(modifier = Modifier.width(4.dp))
                SmartDigitSlot(
                    slotIndex = 1,
                    slotState = state.slots[1],
                    placeholderDigit = placeholders[1],
                    isActive = !state.isComplete && state.activeSlotIndex == 1,
                    hasError = hasError && state.activeSlotIndex == 1,
                    displayDigitStyle = displayDigitStyle,
                    isReducedMotion = isReducedMotion,
                    onPositionedCenterX = { cx ->
                        if (abs(slotCentersX[1] - cx) > 0.5f) slotCentersX[1] = cx
                    }
                )
                Text(
                    text = ":",
                    style = displayDigitStyle,
                    color = if (hasError) colorScheme.error else colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 10.dp)
                )
                SmartDigitSlot(
                    slotIndex = 2,
                    slotState = state.slots[2],
                    placeholderDigit = placeholders[2],
                    isActive = !state.isComplete && state.activeSlotIndex == 2,
                    hasError = hasError && state.activeSlotIndex == 2,
                    displayDigitStyle = displayDigitStyle,
                    isReducedMotion = isReducedMotion,
                    onPositionedCenterX = { cx ->
                        if (abs(slotCentersX[2] - cx) > 0.5f) slotCentersX[2] = cx
                    }
                )
                Spacer(modifier = Modifier.width(4.dp))
                SmartDigitSlot(
                    slotIndex = 3,
                    slotState = state.slots[3],
                    placeholderDigit = placeholders[3],
                    isActive = !state.isComplete && state.activeSlotIndex == 3,
                    hasError = hasError && state.activeSlotIndex == 3,
                    displayDigitStyle = displayDigitStyle,
                    isReducedMotion = isReducedMotion,
                    onPositionedCenterX = { cx ->
                        if (abs(slotCentersX[3] - cx) > 0.5f) slotCentersX[3] = cx
                    }
                )
            }

            // Invisible interactive BasicTextField handling IME digit & backspace events cleanly
            BasicTextField(
                value = imeAnchorValue,
                onValueChange = { nextValue ->
                    val oldText = imeAnchorValue.text
                    val newText = nextValue.text
                    when {
                        newText.length < oldText.length -> {
                            onBackspace()
                        }
                        newText.length > oldText.length -> {
                            val extraCount = newText.length - oldText.length
                            val inserted = if (newText.startsWith(oldText)) {
                                newText.substring(oldText.length)
                            } else {
                                var prefix = 0
                                while (prefix < oldText.length && newText[prefix] == oldText[prefix]) {
                                    prefix++
                                }
                                newText.substring(prefix, (prefix + extraCount).coerceAtMost(newText.length))
                            }
                            inserted.filter { it.isDigit() }.forEach { ch ->
                                onDigitTyped(ch)
                            }
                        }
                        newText != oldText -> {
                            newText.filter { it.isDigit() }.lastOrNull()?.let { ch ->
                                onDigitTyped(ch)
                            }
                        }
                    }
                    val nextAnchor = if (oldText == "0000") "00000" else "0000"
                    imeAnchorValue = TextFieldValue(
                        text = nextAnchor,
                        selection = TextRange(nextAnchor.length)
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { onDone() }
                ),
                cursorBrush = SolidColor(Color.Transparent),
                textStyle = displayDigitStyle.copy(color = Color.Transparent),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp)
                    .focusRequester(focusRequester)
                    .onFocusChanged { onFocusChanged(it.isFocused) }
                    .testTag("reminder_direct_time_input")
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = if (hasError) errorText else hintText,
            style = MaterialTheme.typography.labelMedium,
            color = if (hasError) colorScheme.error else colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SmartDigitSlot(
    slotIndex: Int,
    slotState: TimeSlotState,
    placeholderDigit: String,
    isActive: Boolean,
    hasError: Boolean,
    displayDigitStyle: androidx.compose.ui.text.TextStyle,
    isReducedMotion: Boolean,
    onPositionedCenterX: (Float) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val entryScale = remember(slotIndex) { Animatable(1f) }

    LaunchedEffect(slotState.entrySeq, isReducedMotion) {
        if (slotState.digit != null && slotState.entrySeq > 0) {
            if (isReducedMotion) {
                entryScale.snapTo(1f)
            } else {
                entryScale.snapTo(0.75f)
                entryScale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = 0.65f,
                        stiffness = 620f,
                        visibilityThreshold = 0.002f
                    )
                )
            }
        } else {
            entryScale.snapTo(1f)
        }
    }

    val targetAlpha = when {
        slotState.digit == null -> 0.28f
        slotState.isAutoZero -> 0.55f
        else -> 1.0f
    }
    val textColor = when {
        hasError -> colorScheme.error
        slotState.digit != null -> colorScheme.primary
        isActive -> colorScheme.primary
        else -> colorScheme.onSurfaceVariant
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .width(34.dp)
            .onGloballyPositioned { coords ->
                onPositionedCenterX(coords.positionInParent().x + coords.size.width * 0.5f)
            }
            .testTag("reminder_slot_$slotIndex")
    ) {
        Text(
            text = slotState.digit?.toString() ?: placeholderDigit,
            style = displayDigitStyle,
            color = textColor,
            modifier = Modifier.graphicsLayer {
                val sc = entryScale.value
                scaleX = sc
                scaleY = sc
                alpha = targetAlpha
                transformOrigin = TransformOrigin(0.5f, 0.5f)
            }
        )
    }
}

@Composable
private fun QuickTimeCapsuleChip(
    label: String,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .height(36.dp)
            .testTag(testTag)
            .slideOutCancelClickable(
                shape = RoundedCornerShape(50),
                role = Role.Button,
                onClick = onClick
            )
            .background(
                color = colorScheme.surfaceContainerHighest.copy(alpha = 0.72f),
                shape = RoundedCornerShape(50)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun StaggeredWeekdayCircle(
    label: String,
    bitIndex: Int,
    selected: Boolean,
    waveKey: Int,
    waveTriggeredAtMs: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val isReducedMotion = LocalReducedMotion.current

    var visualSelected by remember { mutableStateOf(selected) }
    var lastConsumedWaveKey by remember { mutableIntStateOf(0) }
    val pulseScale = remember { Animatable(1f) }
    val fillProgress = remember { Animatable(if (selected) 1f else 0f) }

    // Single deterministic coroutine handling both preset staggered wave and individual taps
    LaunchedEffect(selected, waveKey) {
        if (waveKey > 0 && waveKey != lastConsumedWaveKey) {
            lastConsumedWaveKey = waveKey
            if (!isReducedMotion) {
                val delayMs = bitIndex * 25L
                val elapsed = (SystemClock.uptimeMillis() - waveTriggeredAtMs).coerceAtLeast(0L)
                val remaining = (delayMs - elapsed).coerceAtLeast(0L)
                if (remaining > 0L) delay(remaining)
            }
            visualSelected = selected
            if (selected && !isReducedMotion) {
                pulseScale.animateTo(
                    targetValue = 1.05f,
                    animationSpec = spring(dampingRatio = 0.65f, stiffness = 780f)
                )
                pulseScale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(dampingRatio = 0.55f, stiffness = 380f)
                )
            }
        } else {
            visualSelected = selected
            if (isReducedMotion) pulseScale.snapTo(1f)
        }
    }

    LaunchedEffect(visualSelected, isReducedMotion) {
        val target = if (visualSelected) 1f else 0f
        if (isReducedMotion) {
            fillProgress.snapTo(target)
        } else {
            fillProgress.animateTo(
                targetValue = target,
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 460f)
            )
        }
    }

    val selectedBg = colorScheme.primary
    val unselectedBg = colorScheme.surfaceContainerHighest.copy(alpha = 0.68f)
    val selectedText = colorScheme.onPrimary
    val unselectedText = colorScheme.onSurfaceVariant

    Box(
        modifier = modifier
            .height(36.dp)
            .graphicsLayer {
                val sc = pulseScale.value
                scaleX = sc
                scaleY = sc
            }
            .slideOutCancelClickable(
                shape = CircleShape,
                role = Role.Checkbox,
                onClick = onClick
            )
            .drawBehind {
                val p = fillProgress.value.coerceIn(0f, 1f)
                drawCircle(color = lerpColor(unselectedBg, selectedBg, p))
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (visualSelected) selectedText else unselectedText,
            maxLines = 1
        )
    }
}
