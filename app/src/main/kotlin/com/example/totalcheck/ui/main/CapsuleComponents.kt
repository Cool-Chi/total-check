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
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.NotificationsPaused
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.CompositingStrategy
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
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Constraints
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
import com.example.totalcheck.R
import com.example.totalcheck.data.local.CheckItemEntity
import com.example.totalcheck.data.local.ReminderEntity
import com.example.totalcheck.data.reminder.ReminderFormatter
import com.example.totalcheck.data.reminder.ReminderScheduler
import com.example.totalcheck.ui.theme.LocalReducedMotion
import com.example.totalcheck.ui.theme.LocalThemeTransitioning
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

private class CheckedStateTracker(var value: Boolean)

@Composable
fun CapsuleProgressModule(
    uncheckedCount: Int,
    totalCount: Int,
    progressProvider: () -> Float,
    filterState: ChecklistFilterState,
    onToggleFilter: () -> Unit,
    onReset: () -> Unit,
    canDeleteChecklist: Boolean,
    archivedCount: Int = 0,
    onOpenArchivedItems: () -> Unit = {},
    onRenameChecklist: () -> Unit,
    onDeleteChecklist: () -> Unit,
    activeChecklistReminder: ReminderEntity? = null,
    onOpenChecklistReminder: () -> Unit = {},
    onQuickChecklistReminder15m: () -> Unit = {},
    archiveButtonFocusRequester: FocusRequester = remember { FocusRequester() },
    reminderButtonFocusRequester: FocusRequester = remember { FocusRequester() },
    renameButtonFocusRequester: FocusRequester = remember { FocusRequester() },
    deleteButtonFocusRequester: FocusRequester = remember { FocusRequester() },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
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
                        text = if (uncheckedCount == 0 && totalCount > 0) {
                            stringResource(R.string.progress_all_set)
                        } else {
                            stringResource(R.string.progress_left_count, uncheckedCount)
                        },
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFeatureSettings = "tnum"
                        ),
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
                        text = stringResource(R.string.progress_total_count, totalCount),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFeatureSettings = "tnum"
                        ),
                        color = colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                val hasCheckedItems = totalCount > uncheckedCount
                TactileButton(
                    onClick = onReset,
                    enabled = hasCheckedItems,
                    shape = RoundedCornerShape(50),
                    containerColor = colorScheme.surfaceContainerHighest,
                    contentColor = colorScheme.onSurface,
                    disabledContainerColor = colorScheme.surfaceContainerHighest.copy(alpha = 0.45f),
                    disabledContentColor = colorScheme.onSurface.copy(alpha = 0.38f),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.RestartAlt,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.action_reset_all),
                        style = MaterialTheme.typography.labelLarge
                    )
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
                val isFilterActive = filterState.isActive
                TactileIconButton(
                    onClick = onToggleFilter,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("filter_checklist_button"),
                    shape = RoundedCornerShape(50),
                    containerColor = if (isFilterActive) {
                        colorScheme.tertiaryContainer
                    } else {
                        colorScheme.surfaceContainerHighest
                    },
                    contentColor = if (isFilterActive) {
                        colorScheme.onTertiaryContainer
                    } else {
                        colorScheme.onSurfaceVariant
                    },
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isFilterActive) {
                            colorScheme.tertiary.copy(alpha = 0.5f)
                        } else {
                            colorScheme.outlineVariant
                        }
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FilterList,
                        contentDescription = stringResource(R.string.cd_filter_items),
                        modifier = Modifier.size(18.dp)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    val hasArchivedItems = archivedCount > 0
                    TactileIconButton(
                        onClick = onOpenArchivedItems,
                        modifier = Modifier
                            .size(40.dp)
                            .focusRequester(archiveButtonFocusRequester)
                            .testTag("archive_checklist_button"),
                        shape = RoundedCornerShape(50),
                        containerColor = if (hasArchivedItems) {
                            colorScheme.secondaryContainer
                        } else {
                            colorScheme.surfaceContainerHighest
                        },
                        contentColor = if (hasArchivedItems) {
                            colorScheme.onSecondaryContainer
                        } else {
                            colorScheme.onSurfaceVariant
                        },
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (hasArchivedItems) {
                                colorScheme.secondary.copy(alpha = 0.45f)
                            } else {
                                colorScheme.outlineVariant
                            }
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Archive,
                            contentDescription = if (hasArchivedItems) {
                                stringResource(R.string.cd_open_archive_panel_count, archivedCount)
                            } else {
                                stringResource(R.string.cd_open_archive_panel)
                            },
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    val isReminderActive = activeChecklistReminder != null && activeChecklistReminder.isEnabled
                    val isChecklistReminderSkipped = isReminderActive &&
                        ReminderScheduler.isPeriodicReminderSkipped(activeChecklistReminder)
                    TactileIconButton(
                        onClick = onOpenChecklistReminder,
                        onLongClick = onQuickChecklistReminder15m,
                        onLongClickLabel = stringResource(
                            if (isReminderActive) {
                                R.string.a11y_cancel_or_skip_reminder
                            } else {
                                R.string.a11y_quick_reminder_15m
                            }
                        ),
                        longPressTimeoutMs = 350L,
                        sinkScale = 0.92f,
                        modifier = Modifier
                            .size(40.dp)
                            .focusRequester(reminderButtonFocusRequester)
                            .testTag("checklist_reminder_button"),
                        shape = RoundedCornerShape(50),
                        containerColor = if (isReminderActive) {
                            colorScheme.primaryContainer
                        } else {
                            colorScheme.surfaceContainerHighest
                        },
                        contentColor = if (isReminderActive) {
                            colorScheme.onPrimaryContainer
                        } else {
                            colorScheme.onSurfaceVariant
                        },
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isReminderActive) {
                                colorScheme.primary.copy(alpha = 0.5f)
                            } else {
                                colorScheme.outlineVariant
                            }
                        )
                    ) {
                        Icon(
                            imageVector = when {
                                isChecklistReminderSkipped -> Icons.Rounded.NotificationsPaused
                                isReminderActive -> Icons.Rounded.NotificationsActive
                                else -> Icons.Rounded.NotificationsNone
                            },
                            contentDescription = stringResource(R.string.cd_checklist_reminder),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    TactileIconButton(
                        onClick = onRenameChecklist,
                        modifier = Modifier
                            .size(40.dp)
                            .focusRequester(renameButtonFocusRequester)
                            .testTag("rename_checklist_button"),
                        shape = RoundedCornerShape(50),
                        containerColor = colorScheme.surfaceContainerHighest,
                        contentColor = colorScheme.onSurfaceVariant,
                        border = BorderStroke(1.dp, colorScheme.outlineVariant)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = stringResource(R.string.cd_rename_checklist),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    TactileIconButton(
                        onClick = onDeleteChecklist,
                        enabled = canDeleteChecklist,
                        modifier = Modifier
                            .size(40.dp)
                            .focusRequester(deleteButtonFocusRequester)
                            .testTag("delete_checklist_button"),
                        shape = RoundedCornerShape(50),
                        containerColor = colorScheme.errorContainer.copy(alpha = 0.72f),
                        contentColor = colorScheme.onErrorContainer,
                        disabledContainerColor = colorScheme.surfaceContainerHighest.copy(alpha = 0.45f),
                        disabledContentColor = colorScheme.onSurface.copy(alpha = 0.38f),
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (canDeleteChecklist) {
                                colorScheme.error.copy(alpha = 0.35f)
                            } else {
                                colorScheme.outlineVariant.copy(alpha = 0.4f)
                            }
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = stringResource(
                                if (canDeleteChecklist) {
                                    R.string.cd_delete_checklist
                                } else {
                                    R.string.cd_delete_checklist_disabled
                                }
                            ),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DashedCapsuleEmptySlot(
    heightDp: Dp,
    attractionWeightProvider: () -> Float = { 0f },
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val baseFillColor = colorScheme.surfaceContainerLow.copy(alpha = 0.45f)
    val activeFillColor = colorScheme.primaryContainer.copy(alpha = 0.38f)
    val baseDashColor = colorScheme.primary.copy(alpha = 0.40f)
    val activeDashColor = colorScheme.primary.copy(alpha = 0.92f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp)
            .graphicsLayer {
                val w = attractionWeightProvider().coerceIn(0f, 1f)
                val s = lerp(0.985f, 1f, w)
                scaleX = s
                scaleY = s
                transformOrigin = TransformOrigin(0.5f, 0.5f)
            }
            .clip(RoundedCornerShape(24.dp))
            .drawBehind {
                val w = attractionWeightProvider().coerceIn(0f, 1f)
                val r = 24.dp.toPx()
                val corner = CornerRadius(r, r)
                drawRoundRect(
                    color = lerpColor(baseFillColor, activeFillColor, w),
                    cornerRadius = corner
                )
                val strokePx = lerp(1.5f, 2.25f, w).dp.toPx()
                drawRoundRect(
                    color = lerpColor(baseDashColor, activeDashColor, w),
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
    hasAutoRestore: Boolean = false,
    activeItemReminder: ReminderEntity? = null,
    onOpenItemReminder: () -> Unit = {},
    onQuickItemReminder15m: () -> Unit = {},
    holdDeleteFillDurationMsProvider: () -> Int = { 800 },
    stackDepthProvider: () -> Float = { 0f },
    isCompressedProvider: () -> Boolean = { false },
    onCompressedClick: (() -> Unit)? = null,
    resetWaveKey: Int = 0,
    resetDelayMs: Int? = null,
    resetTriggeredAtMillis: Long = 0L,
    onStartEdit: () -> Unit = {},
    onEditDraftChanged: ((String) -> Unit)? = null,
    onConfirmEdit: (String) -> Unit = {},
    onCancelEdit: () -> Unit = {},
    onArchive: () -> Unit = {},
    onDelete: () -> Unit = {},
    canMoveUp: Boolean = false,
    canMoveDown: Boolean = false,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val keyboardFocusController = LocalKeyboardFocusVisible.current
    val scope = rememberCoroutineScope()
    val isReducedMotion = LocalReducedMotion.current
    val currentOnToggle by rememberUpdatedState(onToggle)
    val currentOnQuickItemReminder15m by rememberUpdatedState(onQuickItemReminder15m)
    val currentHoldDeleteFillDurationMsProvider by rememberUpdatedState(holdDeleteFillDurationMsProvider)
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
    val previousCheckedTracker = remember(item.id) { CheckedStateTracker(item.isChecked) }
    val wavePulseScale = remember(item.id) { Animatable(1f) }
    val checkedProgress = remember(item.id) {
        Animatable(if (visualChecked && !isEditing) 1f else 0f)
    }

    val triggerToggleOptimistic: () -> Unit = {
        isWaveWaitingOrAnimating = false
        visualChecked = !visualChecked
        currentOnToggle()
    }

    if (resetWaveKey > 0) {
        LaunchedEffect(resetWaveKey) {
            val delayMs = resetDelayMs
            if (resetWaveKey != lastConsumedWaveKey) {
                if (delayMs != null && !isReducedMotion) {
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
                    } else {
                        lastConsumedWaveKey = resetWaveKey
                    }
                } else {
                    lastConsumedWaveKey = resetWaveKey
                }
            }
        }
    }

    if (item.isChecked != previousCheckedTracker.value || isWaveWaitingOrAnimating || isInActiveWave) {
        LaunchedEffect(item.isChecked) {
            val wasChecked = previousCheckedTracker.value
            previousCheckedTracker.value = item.isChecked
            if (item.isChecked) {
                if (!wasChecked || !isWaveWaitingOrAnimating) {
                    isWaveWaitingOrAnimating = false
                    visualChecked = true
                    wavePulseScale.snapTo(1f)
                }
            } else {
                val elapsedSinceReset = if (resetTriggeredAtMillis > 0L) {
                    (android.os.SystemClock.uptimeMillis() - resetTriggeredAtMillis).coerceAtLeast(0L)
                } else {
                    Long.MAX_VALUE
                }
                val expectingWave = !isReducedMotion &&
                    resetWaveKey > 0 &&
                    resetDelayMs != null &&
                    elapsedSinceReset < (resetDelayMs + 450L) &&
                    (resetWaveKey != lastConsumedWaveKey || isWaveWaitingOrAnimating)
                if (!expectingWave) {
                    isWaveWaitingOrAnimating = false
                    visualChecked = false
                }
            }
        }
    }

    val targetCheckedProgress = if (visualChecked && !isEditing) 1f else 0f
    val needsCheckedProgressSync = abs(checkedProgress.targetValue - targetCheckedProgress) > 0.0001f ||
        abs(checkedProgress.value - targetCheckedProgress) > 0.0005f
    if (needsCheckedProgressSync) {
        LaunchedEffect(targetCheckedProgress, isReducedMotion) {
            if (isReducedMotion) {
                checkedProgress.snapTo(targetCheckedProgress)
            } else if (targetCheckedProgress > checkedProgress.value) {
                checkedProgress.animateTo(
                    targetValue = targetCheckedProgress,
                    animationSpec = spring(
                        dampingRatio = 0.68f,
                        stiffness = 460f,
                        visibilityThreshold = 0.001f
                    )
                )
            } else {
                checkedProgress.animateTo(
                    targetValue = targetCheckedProgress,
                    animationSpec = spring(
                        dampingRatio = 0.88f,
                        stiffness = 480f,
                        visibilityThreshold = 0.001f
                    )
                )
            }
        }
    }

    // Hold-to-Delete Perimeter Ring (按住 150ms 後外圈 24dp 進度環於 1000ms 線性填滿刪除，中途放開/移出/取消於 300ms 內退回)
    var isPressed by remember(item.id) { mutableStateOf(false) }
    var isDeleting by remember(item.id) { mutableStateOf(false) }
    var isCardFocused by remember(item.id) { mutableStateOf(false) }
    var isChildButtonPressed by remember(item.id) { mutableStateOf(false) }
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
        hasAutoRestore -> colorScheme.primary.copy(alpha = 0.72f)
        else -> colorScheme.tertiary.copy(alpha = 0.55f)
    }
    val checkedBorderColor = when {
        isFloatingDragged || isEditing -> colorScheme.primary
        hasAutoRestore -> colorScheme.primary.copy(alpha = 0.45f)
        else -> colorScheme.outlineVariant.copy(alpha = 0.4f)
    }
    val ringTrackBorderColor = colorScheme.outlineVariant.copy(alpha = 0.45f)
    val ringFillColor = colorScheme.errorContainer.copy(alpha = 0.72f)
    val uncheckedIndicatorColor = if (isEditing || hasAutoRestore) colorScheme.primary else colorScheme.tertiary
    val checkedIndicatorColor = if (isEditing || hasAutoRestore) colorScheme.primary else colorScheme.outline
    val uncheckedBadgeBgColor = colorScheme.tertiaryContainer
    val checkedBadgeBgColor = colorScheme.surfaceContainerHighest

    val isDarkSurface = colorScheme.surface.luminance() < 0.5f
    val statusCheckedLabel = stringResource(R.string.status_checked)
    val statusUncheckedLabel = stringResource(R.string.status_unchecked)
    val markUncheckedActionLabel = stringResource(R.string.a11y_mark_unchecked)
    val markCheckedActionLabel = stringResource(R.string.a11y_mark_checked)
    val dragReorderDescription = stringResource(R.string.cd_drag_reorder_item, item.label)
    val moveUpActionLabel = stringResource(R.string.a11y_move_up)
    val moveDownActionLabel = stringResource(R.string.a11y_move_down)
    val quickReminder15mLabel = stringResource(R.string.a11y_quick_reminder_15m)
    val cancelOrSkipReminderLabel = stringResource(R.string.a11y_cancel_or_skip_reminder)
    val itemReminderActionLabel = stringResource(R.string.cd_item_reminder, item.label)
    val hasActiveReminder = activeItemReminder != null && activeItemReminder.isEnabled
    val isSkippedOnce = hasActiveReminder &&
        ReminderScheduler.isPeriodicReminderSkipped(activeItemReminder)

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
                    val showCardFocusRing = isCardFocused &&
                        keyboardFocusController.isKeyboardFocusVisible &&
                        !isEditing &&
                        ringProgress <= 0.001f
                    val baseBorderColor = when {
                        ringProgress > 0.001f -> ringTrackBorderColor
                        showCardFocusRing -> colorScheme.primary
                        else -> lerpColor(uncheckedBorderColor, checkedBorderColor, t)
                    }
                    val effectiveBorderStrokePx = if (showCardFocusRing) {
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
                                    triggerToggleOptimistic()
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
                    stateDescription = if (visualChecked) statusCheckedLabel else statusUncheckedLabel
                    if (!isEditing && !isDeleting) {
                        onClick(label = if (visualChecked) markUncheckedActionLabel else markCheckedActionLabel) {
                            if (currentIsCompressedProvider()) {
                                currentOnCompressedClick?.invoke()
                            } else {
                                triggerToggleOptimistic()
                            }
                            true
                        }
                        if (!currentIsCompressedProvider()) {
                            customActions = listOf(
                                CustomAccessibilityAction(itemReminderActionLabel) {
                                    onOpenItemReminder()
                                    true
                                },
                                CustomAccessibilityAction(
                                    if (hasActiveReminder) cancelOrSkipReminderLabel else quickReminder15mLabel
                                ) {
                                    currentOnQuickItemReminder15m()
                                    true
                                }
                            )
                        }
                    }
                }
                .pointerInput(item.id, isEditing, isDeleting, isReducedMotion) {
                    if (isEditing || isDeleting) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = true)
                        if (isChildButtonPressed) return@awaitEachGesture
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
                            delay(120L)
                            if (cancelledOrLeft || isDeleting) return@launch
                            holdEngaged = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val startVal = holdRingProgress.value.coerceIn(0f, 1f)
                            val baseFillMs = currentHoldDeleteFillDurationMsProvider().coerceIn(450, 1200)
                            val fillDurationMs = ((1f - startVal) * baseFillMs.toFloat())
                                .roundToInt()
                                .coerceAtLeast(1)
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
                                        triggerToggleOptimistic()
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
                    .graphicsLayer {
                        alpha = lerp(1f, 0.45f, checkedProgress.value.coerceIn(0f, 1f))
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                    },
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

                var localOptimisticLabel by remember(item.id) { mutableStateOf<String?>(null) }
                if (localOptimisticLabel != null && item.label == localOptimisticLabel) {
                    localOptimisticLabel = null
                }
                val displayedLabel = localOptimisticLabel ?: item.label

                if (isEditing) {
                    val keyboardController = LocalSoftwareKeyboardController.current
                    var editValue by remember(item.id, isEditing) {
                        mutableStateOf(
                            TextFieldValue(
                                text = displayedLabel,
                                selection = TextRange(displayedLabel.length)
                            )
                        )
                    }
                    val focusRequester = remember { FocusRequester() }
                    LaunchedEffect(isEditing) {
                        runCatching {
                            focusRequester.requestFocus()
                            keyboardController?.show()
                        }
                    }

                    BasicTextField(
                        value = editValue,
                        onValueChange = {
                            editValue = it
                            onEditDraftChanged?.invoke(it.text)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester)
                            .onPreviewKeyEvent { event ->
                                if (
                                    event.type == KeyEventType.KeyDown &&
                                    event.key == Key.Escape
                                ) {
                                    val trimmed = editValue.text.trim()
                                    if (trimmed.isNotEmpty()) {
                                        localOptimisticLabel = trimmed
                                        onConfirmEdit(trimmed)
                                    } else {
                                        onCancelEdit()
                                    }
                                    true
                                } else {
                                    false
                                }
                            }
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
                                val trimmed = editValue.text.trim()
                                if (trimmed.isNotEmpty()) {
                                    localOptimisticLabel = trimmed
                                    onConfirmEdit(trimmed)
                                } else {
                                    onCancelEdit()
                                }
                            }
                        )
                    )
                    TactileIconButton(
                        onClick = {
                            val trimmed = editValue.text.trim()
                            if (trimmed.isNotEmpty()) {
                                localOptimisticLabel = trimmed
                                onConfirmEdit(trimmed)
                            }
                        },
                        enabled = editValue.text.isNotBlank(),
                        modifier = Modifier.size(34.dp),
                        contentColor = colorScheme.primary
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = stringResource(R.string.cd_save_name),
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    TactileIconButton(
                        onClick = onArchive,
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("archive_item_button_${item.id}"),
                        contentColor = colorScheme.onSurfaceVariant
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Archive,
                            contentDescription = stringResource(R.string.cd_archive_item),
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    TactileIconButton(
                        onClick = onCancelEdit,
                        modifier = Modifier.size(34.dp),
                        contentColor = colorScheme.onSurfaceVariant
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.cd_cancel_edit),
                            modifier = Modifier.size(17.dp)
                        )
                    }
                } else {
                    if (hasActiveReminder) {
                        val rawBadgeText = ReminderFormatter.formatCompactBadge(
                            context = context,
                            hour = activeItemReminder.hour,
                            minute = activeItemReminder.minute,
                            repeatDaysBitmask = activeItemReminder.repeatDaysBitmask
                        )
                        val badgeText = if (isSkippedOnce) {
                            stringResource(R.string.reminder_badge_skipped_prefix, rawBadgeText)
                        } else {
                            rawBadgeText
                        }
                        val badgeTint = if (isSkippedOnce) {
                            colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
                        } else {
                            colorScheme.primary
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 2.dp)
                        ) {
                            Text(
                                text = displayedLabel,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (visualChecked) FontWeight.Normal else FontWeight.SemiBold,
                                color = colorScheme.onSurface,
                                textDecoration = if (visualChecked) TextDecoration.LineThrough else TextDecoration.None,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .slideOutCancelClickable(
                                        enabled = !isDeleting,
                                        shape = RoundedCornerShape(6.dp),
                                        sinkScale = 0.96f,
                                        longPressTimeoutMs = 350L,
                                        onLongClickLabel = cancelOrSkipReminderLabel,
                                        onPressChanged = { pressed -> isChildButtonPressed = pressed },
                                        onLongClick = {
                                            if (currentIsCompressedProvider()) {
                                                currentOnCompressedClick?.invoke()
                                            } else {
                                                currentOnQuickItemReminder15m()
                                            }
                                        },
                                        onClick = {
                                            if (currentIsCompressedProvider()) {
                                                currentOnCompressedClick?.invoke()
                                            } else {
                                                onOpenItemReminder()
                                            }
                                        }
                                    )
                                    .padding(horizontal = 2.dp, vertical = 1.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = if (isSkippedOnce) {
                                        Icons.Rounded.NotificationsPaused
                                    } else {
                                        Icons.Rounded.NotificationsActive
                                    },
                                    contentDescription = null,
                                    tint = badgeTint,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = badgeText,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = badgeTint,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    } else {
                        Text(
                            text = displayedLabel,
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
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    val badgeWidthPx = with(density) { 64.dp.roundToPx() }
                    val badgeHeightPx = with(density) { 28.dp.roundToPx() }
                    val badgeIconGapPx = with(density) { 3.dp.toPx() }
                    val badgeUnboundedConstraints = remember { Constraints() }
                    var isBellPressed by remember(item.id) { mutableStateOf(false) }
                    val bellPressSinkScale by animateFloatAsState(
                        targetValue = if (isBellPressed && !visualChecked && !isReducedMotion) 0.92f else 1f,
                        animationSpec = spring(dampingRatio = 0.78f, stiffness = 680f),
                        label = "capsuleBellSinkScale"
                    )

                    Box(
                        modifier = Modifier.height(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Layout(
                            modifier = Modifier
                                .width(64.dp)
                                .height(28.dp)
                                .graphicsLayer {
                                    val badgeScale = (1f + (wavePulseScale.value - 1f) * 1.8f) * bellPressSinkScale
                                    scaleX = badgeScale
                                    scaleY = badgeScale
                                    clip = true
                                    shape = RoundedCornerShape(50)
                                }
                                .drawBehind {
                                    val r = size.height / 2f
                                    drawRoundRect(
                                        color = lerpColor(
                                            uncheckedBadgeBgColor,
                                            checkedBadgeBgColor,
                                            checkedProgress.value.coerceIn(0f, 1f)
                                        ),
                                        cornerRadius = CornerRadius(r, r)
                                    )
                                },
                            content = {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(13.5.dp)
                                )
                                Text(
                                    text = statusUncheckedLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.onTertiaryContainer,
                                    maxLines = 1
                                )
                                Text(
                                    text = statusCheckedLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                                Icon(
                                    imageVector = when {
                                        isSkippedOnce -> Icons.Rounded.NotificationsPaused
                                        hasActiveReminder -> Icons.Rounded.NotificationsActive
                                        else -> Icons.Rounded.NotificationsNone
                                    },
                                    contentDescription = null,
                                    tint = when {
                                        isSkippedOnce -> colorScheme.onTertiaryContainer.copy(alpha = 0.62f)
                                        hasActiveReminder -> colorScheme.primary
                                        else -> colorScheme.onTertiaryContainer
                                    },
                                    modifier = Modifier.size(13.5.dp)
                                )
                            }
                        ) { badgeMeasurables, _ ->
                            val checkPlaceable = badgeMeasurables[0].measure(badgeUnboundedConstraints)
                            val uncheckedTextPlaceable = badgeMeasurables[1].measure(badgeUnboundedConstraints)
                            val checkedTextPlaceable = badgeMeasurables[2].measure(badgeUnboundedConstraints)
                            val bellPlaceable = badgeMeasurables[3].measure(badgeUnboundedConstraints)

                            // 外層膠囊容器尺寸嚴格鎖定為 64.dp x 28.dp，整合待備鈴鐺與已備對勾雙向滑動轉場
                            layout(badgeWidthPx, badgeHeightPx) {
                                val rawT = checkedProgress.value
                                val clampedT = rawT.coerceIn(0f, 1f)
                                val spatialT = rawT.coerceIn(-0.04f, 1.08f)

                                val leftFullSlotPx = checkPlaceable.width.toFloat() + badgeIconGapPx
                                val rightFullSlotPx = bellPlaceable.width.toFloat() + badgeIconGapPx
                                val leftExpandedSlotPx = leftFullSlotPx * spatialT.coerceAtLeast(0f)
                                val rightExpandedSlotPx = rightFullSlotPx * (1f - spatialT).coerceAtLeast(0f)

                                val currentTextWidth = lerp(
                                    uncheckedTextPlaceable.width.toFloat(),
                                    checkedTextPlaceable.width.toFloat(),
                                    clampedT
                                )
                                val totalGroupWidth = leftExpandedSlotPx + currentTextWidth + rightExpandedSlotPx
                                val groupStartX = (badgeWidthPx.toFloat() - totalGroupWidth) * 0.5f
                                val textSlotLeftX = groupStartX + leftExpandedSlotPx

                                if (clampedT > 0.01f) {
                                    // 已備：對勾從文字左側推入展開，切回待備時向文字左側收合淡出
                                    val checkX = (textSlotLeftX - leftFullSlotPx).roundToInt()
                                    val checkY = (badgeHeightPx - checkPlaceable.height) / 2
                                    val checkScale = lerp(0.38f, 1f, spatialT).coerceAtLeast(0.2f)
                                    val checkAlpha = ((clampedT - 0.04f) / 0.78f).coerceIn(0f, 1f)
                                    checkPlaceable.placeRelativeWithLayer(checkX, checkY) {
                                        alpha = checkAlpha
                                        scaleX = checkScale
                                        scaleY = checkScale
                                        transformOrigin = TransformOrigin(0.25f, 0.5f)
                                    }
                                }

                                val uncheckedAlpha = (1f - clampedT * 1.15f).coerceIn(0f, 1f)
                                if (uncheckedAlpha > 0.005f) {
                                    val uncheckedX = (textSlotLeftX + (currentTextWidth - uncheckedTextPlaceable.width) * 0.5f)
                                        .roundToInt()
                                    val uncheckedY = (badgeHeightPx - uncheckedTextPlaceable.height) / 2
                                    uncheckedTextPlaceable.placeRelativeWithLayer(uncheckedX, uncheckedY) {
                                        alpha = uncheckedAlpha
                                    }
                                }

                                val checkedAlpha = ((clampedT - 0.12f) / 0.88f).coerceIn(0f, 1f)
                                if (checkedAlpha > 0.005f) {
                                    val checkedX = (textSlotLeftX + (currentTextWidth - checkedTextPlaceable.width) * 0.5f)
                                        .roundToInt()
                                    val checkedY = (badgeHeightPx - checkedTextPlaceable.height) / 2
                                    checkedTextPlaceable.placeRelativeWithLayer(checkedX, checkedY) {
                                        alpha = checkedAlpha
                                    }
                                }

                                val reverseT = 1f - clampedT
                                if (reverseT > 0.01f) {
                                    // 待備：鈴鐺從文字右側滑入展開，轉為已備時向右側收合滑出
                                    val reverseSpatialT = 1f - spatialT
                                    val bellX = (textSlotLeftX + currentTextWidth + badgeIconGapPx).roundToInt()
                                    val bellY = (badgeHeightPx - bellPlaceable.height) / 2
                                    val bellScale = lerp(0.38f, 1f, reverseSpatialT).coerceAtLeast(0.2f)
                                    val bellAlpha = ((reverseT - 0.04f) / 0.78f).coerceIn(0f, 1f)
                                    bellPlaceable.placeRelativeWithLayer(bellX, bellY) {
                                        alpha = bellAlpha
                                        scaleX = bellScale
                                        scaleY = bellScale
                                        transformOrigin = TransformOrigin(0.75f, 0.5f)
                                    }
                                }
                            }
                        }

                        if (!visualChecked) {
                            val itemReminderCd = stringResource(R.string.cd_item_reminder, displayedLabel)
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .width(64.dp)
                                    .height(32.dp)
                                    .testTag("reminder_item_button_${item.id}")
                                    .semantics {
                                        contentDescription = itemReminderCd
                                    }
                                    .slideOutCancelClickable(
                                        enabled = !isEditing && !isDeleting,
                                        shape = RoundedCornerShape(50),
                                        sinkScale = 1f,
                                        maxDeepenAlpha = 0f,
                                        longPressTimeoutMs = 350L,
                                        onLongClickLabel = if (hasActiveReminder) {
                                            cancelOrSkipReminderLabel
                                        } else {
                                            quickReminder15mLabel
                                        },
                                        onPressChanged = { pressed ->
                                            isBellPressed = pressed
                                            isChildButtonPressed = pressed
                                        },
                                        onLongClick = {
                                            if (currentIsCompressedProvider()) {
                                                currentOnCompressedClick?.invoke()
                                            } else {
                                                currentOnQuickItemReminder15m()
                                            }
                                        },
                                        onClick = {
                                            if (currentIsCompressedProvider()) {
                                                currentOnCompressedClick?.invoke()
                                            } else {
                                                onOpenItemReminder()
                                            }
                                        }
                                    )
                            )
                        }
                    }
                    TactileIconButton(
                        onClick = {
                            if (currentIsCompressedProvider()) {
                                currentOnCompressedClick?.invoke()
                            } else {
                                onStartEdit()
                            }
                        },
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("edit_item_button_${item.id}"),
                        contentColor = colorScheme.onSurfaceVariant,
                        onPressChanged = { pressed -> isChildButtonPressed = pressed }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = stringResource(R.string.cd_edit_item_name),
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
                                if (isHandleFocused && keyboardFocusController.isKeyboardFocusVisible) {
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
                                contentDescription = dragReorderDescription
                                val actions = buildList {
                                    if (canMoveUp && onMoveUp != null) {
                                        add(CustomAccessibilityAction(moveUpActionLabel) { onMoveUp(); true })
                                    }
                                    if (canMoveDown && onMoveDown != null) {
                                        add(CustomAccessibilityAction(moveDownActionLabel) { onMoveDown(); true })
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

            if (!isFloatingDragged) {
                Spacer(
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            val depth = stackDepthProvider().coerceIn(0f, 4f)
                            val maxDim = if (isDarkSurface) 0.42f else 0.22f
                            val stepDim = if (isDarkSurface) 0.11f else 0.055f
                            alpha = if (depth > 0.005f) (depth * stepDim).coerceIn(0f, maxDim) else 0f
                        }
                        .drawBehind {
                            val corner = CornerRadius(cornerRadiusPx, cornerRadiusPx)
                            drawRoundRect(
                                color = Color.Black,
                                cornerRadius = corner
                            )
                        }
                )
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
