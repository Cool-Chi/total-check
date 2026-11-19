package com.example.totalcheck.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.NotificationsPaused
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Unarchive
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalViewConfiguration
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CapsuleProgressModule(
    checklistId: Long,
    uncheckedCount: Int,
    totalCount: Int,
    progressProvider: () -> Float,
    filterState: ChecklistFilterState,
    globalTagPool: List<TagDefinition> = emptyList(),
    checklistTagPool: List<TagDefinition> = emptyList(),
    onToggleFilter: () -> Unit,
    onLongClickFilter: () -> Unit = {},
    onReset: () -> Unit,
    canDeleteChecklist: Boolean,
    archivedCount: Int = 0,
    archivedItems: List<CheckItemEntity> = emptyList(),
    onOpenArchivedItems: () -> Unit = {},
    onUnarchiveItem: (CheckItemEntity) -> Unit = {},
    onDeleteArchivedItemForever: (CheckItemEntity) -> Unit = {},
    onCreateTagInPool: (name: String, iconKey: String, scope: TagScope) -> Unit = { _, _, _ -> },
    onDeleteTagFromPool: (TagDefinition) -> Unit = {},
    onRenameChecklist: () -> Unit,
    onLongClickRenameChecklist: () -> Unit = {},
    onDeleteChecklist: () -> Unit,
    onSettingsPanelOpenChanged: (Boolean) -> Unit = {},
    activeChecklistReminder: ReminderEntity? = null,
    hasAnyActiveItemReminder: Boolean = false,
    onOpenChecklistReminder: () -> Unit = {},
    onQuickChecklistReminder15m: () -> Unit = {},
    @Suppress("UNUSED_PARAMETER")
    archiveButtonFocusRequester: FocusRequester = remember { FocusRequester() },
    reminderButtonFocusRequester: FocusRequester = remember { FocusRequester() },
    renameButtonFocusRequester: FocusRequester = remember { FocusRequester() },
    deleteButtonFocusRequester: FocusRequester = remember { FocusRequester() },
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val viewConfig = LocalViewConfiguration.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
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

    var isSettingsPanelOpen by remember { mutableStateOf(false) }
    var activeSettingsTab by remember { mutableIntStateOf(0) } // 0 = 全域標籤, 1 = 清單標籤, 2 = 封存項目
    var normalCardHeightDp by remember { mutableStateOf(126.dp) }
    var isChildActionButtonPressed by remember { mutableStateOf(false) }
    val currentIsChildActionButtonPressed by rememberUpdatedState(isChildActionButtonPressed)
    val currentIsSettingsPanelOpen by rememberUpdatedState(isSettingsPanelOpen)
    val currentOnSettingsPanelOpenChanged by rememberUpdatedState(onSettingsPanelOpenChanged)

    val settingsExpandFraction by animateFloatAsState(
        targetValue = if (isSettingsPanelOpen) 1f else 0f,
        animationSpec = if (isReducedMotion) {
            snap()
        } else {
            tween(durationMillis = 260, easing = FastOutSlowInEasing)
        },
        label = "infoCardSettingsExpand"
    )

    LaunchedEffect(isSettingsPanelOpen) {
        currentOnSettingsPanelOpenChanged(isSettingsPanelOpen)
    }
    DisposableEffect(Unit) {
        onDispose {
            currentOnSettingsPanelOpenChanged(false)
        }
    }

    val closeSettingsPanel: () -> Unit = {
        keyboardController?.hide()
        focusManager.clearFocus()
        isSettingsPanelOpen = false
    }

    BackHandler(enabled = isSettingsPanelOpen) {
        closeSettingsPanel()
    }

    val openSettingsLongClickLabel = stringResource(R.string.a11y_open_info_card_settings)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .onSizeChanged { size ->
                if (!currentIsSettingsPanelOpen && settingsExpandFraction <= 0.001f && size.height > 0) {
                    val measuredDp = with(density) { size.height.toDp() }
                    if (measuredDp >= 96.dp) {
                        normalCardHeightDp = measuredDp
                    }
                }
            }
            .then(
                if (isSettingsPanelOpen || settingsExpandFraction > 0.001f) {
                    // 展開／收合時平滑插值（1x ⇄ 2x 高度），同步逐幀將下方項目列表往下推與收回
                    Modifier.height(normalCardHeightDp * (1f + settingsExpandFraction))
                } else {
                    Modifier
                }
            )
            .semantics {
                if (!isSettingsPanelOpen) {
                    customActions = listOf(
                        CustomAccessibilityAction(openSettingsLongClickLabel) {
                            isSettingsPanelOpen = true
                            true
                        }
                    )
                }
            }
            .pointerInput(isSettingsPanelOpen) {
                if (isSettingsPanelOpen) return@pointerInput
                val touchSlop = viewConfig.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var cancelled = false
                    var fired = false
                    val holdJob = scope.launch {
                        delay(350L)
                        if (
                            !cancelled &&
                            !currentIsChildActionButtonPressed &&
                            !currentIsSettingsPanelOpen
                        ) {
                            fired = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            isSettingsPanelOpen = true
                        }
                    }
                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null || change.isConsumed || currentIsChildActionButtonPressed) {
                                cancelled = true
                                holdJob.cancel()
                                break
                            }
                            if (fired) {
                                change.consume()
                            }
                            val dist = hypot(
                                change.position.x - down.position.x,
                                change.position.y - down.position.y
                            )
                            if (dist > touchSlop) {
                                cancelled = true
                                holdJob.cancel()
                                break
                            }
                            if (!change.pressed) {
                                cancelled = true
                                holdJob.cancel()
                                break
                            }
                        }
                    } finally {
                        holdJob.cancel()
                    }
                }
            }
            .testTag("capsule_progress_module"),
        shape = RoundedCornerShape(24.dp),
        color = colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            width = if (isSettingsPanelOpen) 1.5.dp else 1.dp,
            color = if (isSettingsPanelOpen) {
                colorScheme.primary.copy(alpha = 0.65f)
            } else {
                colorScheme.outlineVariant
            }
        )
    ) {
        AnimatedContent(
            targetState = isSettingsPanelOpen,
            transitionSpec = {
                if (isReducedMotion) {
                    fadeIn(animationSpec = snap()) togetherWith fadeOut(animationSpec = snap())
                } else {
                    fadeIn(animationSpec = tween(durationMillis = 200)) togetherWith
                        fadeOut(animationSpec = tween(durationMillis = 160))
                }
            },
            label = "infoCardSettingsTransition"
        ) { expanded ->
            if (!expanded) {
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
                        // 寬度精準對齊下方兩顆 40.dp 圓形按鈕 + 8.dp 間距 = 88.dp
                        TactileButton(
                            onClick = onReset,
                            enabled = hasCheckedItems,
                            modifier = Modifier
                                .width(88.dp)
                                .testTag("reset_checklist_button"),
                            shape = RoundedCornerShape(50),
                            containerColor = colorScheme.surfaceContainerHighest,
                            contentColor = colorScheme.onSurface,
                            disabledContainerColor = colorScheme.surfaceContainerHighest.copy(alpha = 0.45f),
                            disabledContentColor = colorScheme.onSurface.copy(alpha = 0.38f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            onPressChanged = { pressed -> isChildActionButtonPressed = pressed }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.RestartAlt,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.action_reset_all),
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1
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
                        val availableTagsForFilter = remember(globalTagPool, checklistTagPool) {
                            globalTagPool + checklistTagPool
                        }
                        val activeFilterTagToken = filterState
                            .effectiveSelectedTagsForChecklist(checklistId)
                            .firstOrNull()
                        val activeFilterTagDef = remember(activeFilterTagToken, availableTagsForFilter) {
                            activeFilterTagToken?.let { token ->
                                TagCatalog.resolveSingleTagDefinition(token, availableTagsForFilter)
                            }
                        }
                        val isFilterActive = filterState.isActiveForChecklist(checklistId)
                        val filterIcon = if (activeFilterTagDef != null) {
                            TagCatalog.resolveTagIcon(
                                iconKey = activeFilterTagDef.iconKey,
                                scope = activeFilterTagDef.scope
                            )
                        } else {
                            Icons.Rounded.FilterList
                        }
                        val filterLongClickLabel = stringResource(R.string.a11y_filter_by_tag)
                        val filterContentDesc = if (activeFilterTagDef != null) {
                            stringResource(R.string.cd_filter_items_with_tag, activeFilterTagDef.name)
                        } else {
                            stringResource(R.string.cd_filter_items)
                        }
                        TactileIconButton(
                            onClick = onToggleFilter,
                            onLongClick = onLongClickFilter,
                            onLongClickLabel = filterLongClickLabel,
                            longPressTimeoutMs = 350L,
                            onPressChanged = { pressed -> isChildActionButtonPressed = pressed },
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
                                imageVector = filterIcon,
                                contentDescription = filterContentDesc,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                        ) {
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
                                onPressChanged = { pressed -> isChildActionButtonPressed = pressed },
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
                                        isReminderActive -> Icons.Rounded.Notifications
                                        hasAnyActiveItemReminder -> Icons.Rounded.Notifications
                                        else -> Icons.Rounded.NotificationsNone
                                    },
                                    contentDescription = stringResource(R.string.cd_checklist_reminder),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            TactileIconButton(
                                onClick = onRenameChecklist,
                                onLongClick = onLongClickRenameChecklist,
                                onLongClickLabel = stringResource(R.string.a11y_edit_card_layout_reserved),
                                longPressTimeoutMs = 350L,
                                onPressChanged = { pressed -> isChildActionButtonPressed = pressed },
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
                                onPressChanged = { pressed -> isChildActionButtonPressed = pressed },
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
            } else {
                InfoCardSettingsPanelContent(
                    activeTab = activeSettingsTab,
                    onSelectTab = { nextTab ->
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        activeSettingsTab = nextTab
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    },
                    onClose = closeSettingsPanel,
                    globalTagPool = globalTagPool,
                    checklistTagPool = checklistTagPool,
                    onCreateTagInPool = onCreateTagInPool,
                    onDeleteTagFromPool = onDeleteTagFromPool,
                    archivedCount = archivedCount,
                    archivedItems = archivedItems,
                    onUnarchiveItem = onUnarchiveItem,
                    onDeleteArchivedItemForever = onDeleteArchivedItemForever
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InfoCardSettingsPanelContent(
    activeTab: Int,
    onSelectTab: (Int) -> Unit,
    onClose: () -> Unit,
    globalTagPool: List<TagDefinition>,
    checklistTagPool: List<TagDefinition>,
    onCreateTagInPool: (name: String, iconKey: String, scope: TagScope) -> Unit,
    onDeleteTagFromPool: (TagDefinition) -> Unit,
    archivedCount: Int,
    archivedItems: List<CheckItemEntity>,
    onUnarchiveItem: (CheckItemEntity) -> Unit,
    onDeleteArchivedItemForever: (CheckItemEntity) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val currentTagScope = if (activeTab == 1) TagScope.CHECKLIST else TagScope.GLOBAL
    val activeScopeTags = if (activeTab == 1) checklistTagPool else globalTagPool

    var newTagName by remember { mutableStateOf("") }
    var selectedIconKey by remember { mutableStateOf(TagCatalog.DEFAULT_ICON_KEY) }
    val sanitizedNewName = TagCatalog.sanitizeTagName(newTagName)
    val canCreateTag = sanitizedNewName.isNotEmpty()

    val submitNewTag: () -> Unit = {
        if (canCreateTag) {
            onCreateTagInPool(sanitizedNewName, selectedIconKey, currentTagScope)
            newTagName = ""
            keyboardController?.hide()
            focusManager.clearFocus()
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag("info_card_settings_panel")
    ) {
        // 頂欄：並列三頁「全域標籤（實心圖示）／清單標籤（空心圖示）／封存」 + 右上角關閉按鈕
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState())
            ) {
                val isGlobalTab = activeTab == 0
                TactileButton(
                    onClick = { onSelectTab(0) },
                    shape = RoundedCornerShape(50),
                    containerColor = if (isGlobalTab) {
                        colorScheme.primaryContainer
                    } else {
                        colorScheme.surfaceContainerHighest
                    },
                    contentColor = if (isGlobalTab) {
                        colorScheme.onPrimaryContainer
                    } else {
                        colorScheme.onSurfaceVariant
                    },
                    border = BorderStroke(
                        width = if (isGlobalTab) 1.5.dp else 1.dp,
                        color = if (isGlobalTab) colorScheme.primary else colorScheme.outlineVariant
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 5.dp),
                    modifier = Modifier.testTag("settings_tab_global_tags")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LocalOffer,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = stringResource(R.string.info_settings_tab_global_tags, globalTagPool.size),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isGlobalTab) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                val isChecklistTab = activeTab == 1
                TactileButton(
                    onClick = { onSelectTab(1) },
                    shape = RoundedCornerShape(50),
                    containerColor = if (isChecklistTab) {
                        colorScheme.primaryContainer
                    } else {
                        colorScheme.surfaceContainerHighest
                    },
                    contentColor = if (isChecklistTab) {
                        colorScheme.onPrimaryContainer
                    } else {
                        colorScheme.onSurfaceVariant
                    },
                    border = BorderStroke(
                        width = if (isChecklistTab) 1.5.dp else 1.dp,
                        color = if (isChecklistTab) colorScheme.primary else colorScheme.outlineVariant
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 5.dp),
                    modifier = Modifier.testTag("settings_tab_checklist_tags")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.LocalOffer,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = stringResource(R.string.info_settings_tab_checklist_tags, checklistTagPool.size),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isChecklistTab) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                val isArchiveTab = activeTab == 2
                TactileButton(
                    onClick = { onSelectTab(2) },
                    shape = RoundedCornerShape(50),
                    containerColor = if (isArchiveTab) {
                        colorScheme.primaryContainer
                    } else {
                        colorScheme.surfaceContainerHighest
                    },
                    contentColor = if (isArchiveTab) {
                        colorScheme.onPrimaryContainer
                    } else {
                        colorScheme.onSurfaceVariant
                    },
                    border = BorderStroke(
                        width = if (isArchiveTab) 1.5.dp else 1.dp,
                        color = if (isArchiveTab) colorScheme.primary else colorScheme.outlineVariant
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 5.dp),
                    modifier = Modifier.testTag("settings_tab_archived_items")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Archive,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = stringResource(R.string.info_settings_tab_archived_short, archivedCount),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isArchiveTab) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            TactileIconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(32.dp)
                    .testTag("close_info_card_settings_button"),
                shape = CircleShape,
                containerColor = colorScheme.surfaceContainerHighest,
                contentColor = colorScheme.onSurfaceVariant,
                border = BorderStroke(1.dp, colorScheme.outlineVariant)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.action_close),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (activeTab == 0 || activeTab == 1) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 新增標籤列：名稱輸入框 + 新增按鈕
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp),
                            shape = RoundedCornerShape(14.dp),
                            color = colorScheme.surfaceContainerHighest,
                            border = BorderStroke(1.dp, colorScheme.outlineVariant)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 10.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (newTagName.isEmpty()) {
                                    Text(
                                        text = stringResource(
                                            if (currentTagScope == TagScope.GLOBAL) {
                                                R.string.item_tags_new_global_placeholder
                                            } else {
                                                R.string.item_tags_new_checklist_placeholder
                                            }
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                BasicTextField(
                                    value = newTagName,
                                    onValueChange = { newTagName = it },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                                        color = colorScheme.onSurface,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    cursorBrush = SolidColor(colorScheme.primary),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = { submitNewTag() }),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("settings_new_tag_name_input")
                                )
                            }
                        }

                        TactileButton(
                            onClick = submitNewTag,
                            enabled = canCreateTag,
                            shape = RoundedCornerShape(14.dp),
                            containerColor = colorScheme.primary,
                            contentColor = colorScheme.onPrimary,
                            disabledContainerColor = colorScheme.surfaceContainerHighest.copy(alpha = 0.45f),
                            disabledContentColor = colorScheme.onSurface.copy(alpha = 0.38f),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("settings_create_tag_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = stringResource(R.string.action_create),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // 預設圖示挑選列（全域標籤顯示實心圖示；清單標籤顯示空心圖示）
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TagCatalog.PRESET_ICONS.forEach { option ->
                            val isIconSelected = selectedIconKey == option.key
                            TactileIconButton(
                                onClick = {
                                    selectedIconKey = option.key
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("settings_tag_icon_${option.key}"),
                                shape = CircleShape,
                                containerColor = if (isIconSelected) {
                                    colorScheme.primaryContainer
                                } else {
                                    colorScheme.surfaceContainerHighest
                                },
                                contentColor = if (isIconSelected) {
                                    colorScheme.onPrimaryContainer
                                } else {
                                    colorScheme.onSurfaceVariant
                                },
                                border = BorderStroke(
                                    width = if (isIconSelected) 1.5.dp else 1.dp,
                                    color = if (isIconSelected) colorScheme.primary else colorScheme.outlineVariant
                                )
                            ) {
                                Icon(
                                    imageVector = option.iconFor(currentTagScope),
                                    contentDescription = option.key,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    // 當前分頁標籤列表（支援點擊右側刪除按鈕移除並連動清理項目）
                    if (activeScopeTags.isEmpty()) {
                        Text(
                            text = stringResource(
                                if (currentTagScope == TagScope.GLOBAL) {
                                    R.string.settings_global_tags_empty_hint
                                } else {
                                    R.string.settings_checklist_tags_empty_hint
                                }
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            activeScopeTags.forEach { tagDef ->
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = colorScheme.surfaceContainerHighest,
                                    border = BorderStroke(1.dp, colorScheme.outlineVariant),
                                    modifier = Modifier.testTag("settings_pool_tag_${tagDef.storageToken}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = TagCatalog.resolveTagIcon(
                                                iconKey = tagDef.iconKey,
                                                scope = tagDef.scope
                                            ),
                                            contentDescription = null,
                                            tint = colorScheme.primary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            text = tagDef.name,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colorScheme.onSurface,
                                            maxLines = 1
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        TactileIconButton(
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                onDeleteTagFromPool(tagDef)
                                            },
                                            modifier = Modifier
                                                .size(22.dp)
                                                .testTag("delete_pool_tag_${tagDef.storageToken}"),
                                            shape = CircleShape,
                                            containerColor = colorScheme.errorContainer.copy(alpha = 0.65f),
                                            contentColor = colorScheme.onErrorContainer
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Close,
                                                contentDescription = stringResource(
                                                    R.string.cd_delete_tag_from_pool,
                                                    tagDef.name
                                                ),
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // 封存項目內嵌檢視
                if (archivedItems.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Archive,
                                contentDescription = null,
                                tint = colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = stringResource(R.string.archived_items_empty_title),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.archived_items_empty_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        archivedItems.forEach { archivedItem ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = colorScheme.surfaceContainerLow,
                                border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.6f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("inline_archived_item_${archivedItem.id}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = archivedItem.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        TactileIconButton(
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                onUnarchiveItem(archivedItem)
                                            },
                                            modifier = Modifier
                                                .size(30.dp)
                                                .testTag("inline_unarchive_${archivedItem.id}"),
                                            shape = CircleShape,
                                            containerColor = colorScheme.primaryContainer,
                                            contentColor = colorScheme.onPrimaryContainer
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Unarchive,
                                                contentDescription = stringResource(
                                                    R.string.cd_unarchive_item,
                                                    archivedItem.label
                                                ),
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                        TactileIconButton(
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                onDeleteArchivedItemForever(archivedItem)
                                            },
                                            modifier = Modifier
                                                .size(30.dp)
                                                .testTag("inline_delete_archived_${archivedItem.id}"),
                                            shape = CircleShape,
                                            containerColor = colorScheme.errorContainer.copy(alpha = 0.72f),
                                            contentColor = colorScheme.onErrorContainer
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.DeleteOutline,
                                                contentDescription = stringResource(
                                                    R.string.cd_delete_archived_item_forever,
                                                    archivedItem.label
                                                ),
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
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
    tagPool: List<TagDefinition> = emptyList(),
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
    onEditTags: () -> Unit = {},
    onEditDraftChanged: ((String) -> Unit)? = null,
    onConfirmEdit: (String) -> Unit = {},
    onCancelEdit: () -> Unit = {},
    onLongClickItemTags: () -> Unit = {},
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
    val currentOnEditTags by rememberUpdatedState(onEditTags)
    val currentOnLongClickItemTags by rememberUpdatedState(onLongClickItemTags)
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

    val resolvedTags = remember(item.tags, tagPool) {
        TagCatalog.resolveItemTagDefinitions(item.tags, tagPool)
    }
    val hasTags = resolvedTags.isNotEmpty()
    var isShowingTagsFace by remember(item.id) { mutableStateOf(false) }
    if (isEditing && isShowingTagsFace) {
        isShowingTagsFace = false
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

    val currentVisualChecked by rememberUpdatedState(visualChecked)
    val currentItemChecked by rememberUpdatedState(item.isChecked)
    val currentIsEditing by rememberUpdatedState(isEditing)
    val currentIsChildButtonPressed by rememberUpdatedState(isChildButtonPressed)

    val cancelHoldAndRewind: () -> Unit = {
        if (isPressed || holdRingProgress.value > 0.001f || holdAnimJob?.isActive == true) {
            isPressed = false
            startRewindRing()
        }
    }

    LaunchedEffect(item.isChecked, visualChecked, isEditing) {
        cancelHoldAndRewind()
    }

    val triggerToggleOptimistic: () -> Unit = {
        cancelHoldAndRewind()
        isWaveWaitingOrAnimating = false
        visualChecked = !visualChecked
        currentOnToggle()
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
    val toggleTagsFaceActionLabel = stringResource(
        if (isShowingTagsFace) R.string.a11y_hide_item_tags else R.string.a11y_show_item_tags
    )
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
                            customActions = buildList {
                                add(
                                    CustomAccessibilityAction(itemReminderActionLabel) {
                                        onOpenItemReminder()
                                        true
                                    }
                                )
                                add(
                                    CustomAccessibilityAction(
                                        if (hasActiveReminder) cancelOrSkipReminderLabel else quickReminder15mLabel
                                    ) {
                                        currentOnQuickItemReminder15m()
                                        true
                                    }
                                )
                                add(
                                    CustomAccessibilityAction(toggleTagsFaceActionLabel) {
                                        isShowingTagsFace = !isShowingTagsFace
                                        true
                                    }
                                )
                            }
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
                        val downItemChecked = currentItemChecked
                        val downVisualChecked = currentVisualChecked
                        isPressed = true
                        var holdEngaged = false
                        var cancelledOrLeft = false
                        val touchSlop = viewConfiguration.touchSlop

                        holdAnimJob?.cancel()
                        val currentHoldJob = scope.launch {
                            delay(120L)
                            if (
                                cancelledOrLeft ||
                                isDeleting ||
                                currentIsEditing ||
                                currentIsChildButtonPressed ||
                                currentItemChecked != downItemChecked ||
                                currentVisualChecked != downVisualChecked
                            ) {
                                return@launch
                            }
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
                            if (
                                !cancelledOrLeft &&
                                !isDeleting &&
                                !currentIsEditing &&
                                !currentIsChildButtonPressed &&
                                currentItemChecked == downItemChecked &&
                                currentVisualChecked == downVisualChecked &&
                                holdRingProgress.value >= 0.999f
                            ) {
                                triggerHoldDelete()
                            }
                        }
                        holdAnimJob = currentHoldJob

                        while (true) {
                            val event = awaitPointerEvent(pass = PointerEventPass.Final)
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (
                                change == null ||
                                change.isConsumed ||
                                currentIsEditing ||
                                currentIsChildButtonPressed ||
                                currentItemChecked != downItemChecked ||
                                currentVisualChecked != downVisualChecked
                            ) {
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
                                    keyboardController?.hide()
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
                                keyboardController?.hide()
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
                    Spacer(modifier = Modifier.width(6.dp))
                    // 左起：封存 (32.dp) → 確認(✓) (32.dp 精準對齊原鉛筆) → 取消(✗) (34.dp 對齊拖曳手把)；原標籤按鈕已整合至標籤狀態膠囊
                    TactileIconButton(
                        onClick = {
                            keyboardController?.hide()
                            onArchive()
                        },
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("archive_item_button_${item.id}"),
                        contentColor = colorScheme.onSurfaceVariant,
                        onPressChanged = { pressed -> isChildButtonPressed = pressed }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Archive,
                            contentDescription = stringResource(R.string.cd_archive_item),
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    TactileIconButton(
                        onClick = {
                            keyboardController?.hide()
                            val trimmed = editValue.text.trim()
                            if (trimmed.isNotEmpty()) {
                                localOptimisticLabel = trimmed
                                onConfirmEdit(trimmed)
                            }
                        },
                        enabled = editValue.text.isNotBlank(),
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("save_item_button_${item.id}"),
                        contentColor = colorScheme.primary,
                        onPressChanged = { pressed -> isChildButtonPressed = pressed }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = stringResource(R.string.cd_save_name),
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    TactileIconButton(
                        onClick = {
                            keyboardController?.hide()
                            onCancelEdit()
                        },
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("cancel_edit_item_button_${item.id}"),
                        contentColor = colorScheme.onSurfaceVariant,
                        onPressChanged = { pressed -> isChildButtonPressed = pressed }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.cd_cancel_edit),
                            modifier = Modifier.size(17.dp)
                        )
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
                        val reminderTimeColor = if (isSkippedOnce) {
                            colorScheme.onSurfaceVariant.copy(alpha = 0.68f)
                        } else {
                            colorScheme.onSurfaceVariant
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
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
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFeatureSettings = "tnum"
                                ),
                                fontWeight = FontWeight.Medium,
                                color = reminderTimeColor,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    val badgeWidthPx = with(density) { 64.dp.roundToPx() }
                    val badgeHeightPx = with(density) { 28.dp.roundToPx() }
                    val badgeIconGapPx = with(density) { 3.dp.toPx() }
                    val badgeUnboundedConstraints = remember { Constraints() }
                    var isBellPressed by remember(item.id) { mutableStateOf(false) }
                    val bellPressSinkScale by animateFloatAsState(
                        targetValue = if (isBellPressed && !isReducedMotion) 0.92f else 1f,
                        animationSpec = spring(dampingRatio = 0.78f, stiffness = 680f),
                        label = "capsuleBellSinkScale"
                    )
                    val flipDegrees by animateFloatAsState(
                        targetValue = if (isShowingTagsFace) 180f else 0f,
                        animationSpec = if (isReducedMotion) {
                            snap()
                        } else {
                            spring(dampingRatio = 0.78f, stiffness = 420f)
                        },
                        label = "capsuleTagFlipDegrees"
                    )
                    val isBackFaceVisible = flipDegrees > 90f

                    Box(
                        modifier = Modifier.height(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(64.dp)
                                .height(28.dp)
                                .graphicsLayer {
                                    val badgeScale = (1f + (wavePulseScale.value - 1f) * 1.8f) * bellPressSinkScale
                                    scaleX = badgeScale
                                    scaleY = badgeScale
                                    rotationY = flipDegrees
                                    cameraDistance = 12f * density.density
                                    clip = true
                                    shape = RoundedCornerShape(50)
                                }
                        ) {
                            if (!isBackFaceVisible) {
                                Layout(
                                    modifier = Modifier
                                        .width(64.dp)
                                        .height(28.dp)
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
                                                hasActiveReminder -> Icons.Rounded.Notifications
                                                else -> Icons.Rounded.NotificationsNone
                                            },
                                            contentDescription = null,
                                            tint = when {
                                                isSkippedOnce -> colorScheme.onTertiaryContainer.copy(alpha = 0.62f)
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
                            } else {
                                // 翻轉背面（標籤狀態按鈕）：純圖示呈現（0 標籤＝空心標籤輪廓、1 標籤＝置中圖示、2 標籤＝垂直細線分隔雙圖示），色調對齊右側鉛筆圖示
                                val backBgColor = colorScheme.surfaceContainerHighest
                                val backBorderColor = colorScheme.outlineVariant.copy(alpha = 0.72f)
                                val backContentColor = colorScheme.onSurfaceVariant
                                val backDividerColor = colorScheme.onSurfaceVariant.copy(alpha = 0.34f)
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .graphicsLayer { rotationY = 180f }
                                        .drawBehind {
                                            val r = size.height / 2f
                                            drawRoundRect(
                                                color = backBgColor,
                                                cornerRadius = CornerRadius(r, r)
                                            )
                                            drawRoundRect(
                                                color = backBorderColor,
                                                cornerRadius = CornerRadius(r, r),
                                                style = Stroke(width = 1.dp.toPx())
                                            )
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    when (resolvedTags.size) {
                                        0 -> {
                                            Icon(
                                                imageVector = Icons.Outlined.LocalOffer,
                                                contentDescription = null,
                                                tint = backContentColor.copy(alpha = 0.78f),
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                        1 -> {
                                            val tag = resolvedTags[0]
                                            Icon(
                                                imageVector = TagCatalog.resolveTagIcon(
                                                    iconKey = tag.iconKey,
                                                    scope = tag.scope
                                                ),
                                                contentDescription = null,
                                                tint = backContentColor,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                        else -> {
                                            val firstTag = resolvedTags[0]
                                            val secondTag = resolvedTags[1]
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceEvenly
                                            ) {
                                                Icon(
                                                    imageVector = TagCatalog.resolveTagIcon(
                                                        iconKey = firstTag.iconKey,
                                                        scope = firstTag.scope
                                                    ),
                                                    contentDescription = null,
                                                    tint = backContentColor,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .width(1.dp)
                                                        .height(12.dp)
                                                        .background(backDividerColor)
                                                )
                                                Icon(
                                                    imageVector = TagCatalog.resolveTagIcon(
                                                        iconKey = secondTag.iconKey,
                                                        scope = secondTag.scope
                                                    ),
                                                    contentDescription = null,
                                                    tint = backContentColor,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        run {
                            val itemReminderCd = stringResource(R.string.cd_item_reminder, displayedLabel)
                            val emptyTagsCd = stringResource(R.string.a11y_item_tags_empty)
                            val editTagsActionLabel = stringResource(R.string.cd_edit_item_tags)
                            val longPressTagsReservedLabel = stringResource(R.string.a11y_item_tags_quick_toggle)
                            val tagsSummaryCd = if (hasTags) {
                                resolvedTags.joinToString(", ") { it.name }
                            } else {
                                emptyTagsCd
                            }
                            val currentIsShowingTagsFace by rememberUpdatedState(isShowingTagsFace)
                            val currentOnOpenReminder by rememberUpdatedState(onOpenItemReminder)
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .width(64.dp)
                                    .height(32.dp)
                                    .testTag("reminder_item_button_${item.id}")
                                    .semantics {
                                        contentDescription = if (isShowingTagsFace) {
                                            tagsSummaryCd
                                        } else {
                                            itemReminderCd
                                        }
                                        customActions = buildList {
                                            add(
                                                CustomAccessibilityAction(toggleTagsFaceActionLabel) {
                                                    isShowingTagsFace = !isShowingTagsFace
                                                    true
                                                }
                                            )
                                            if (isShowingTagsFace) {
                                                add(
                                                    CustomAccessibilityAction(editTagsActionLabel) {
                                                        currentOnEditTags()
                                                        true
                                                    }
                                                )
                                                add(
                                                    CustomAccessibilityAction(longPressTagsReservedLabel) {
                                                        currentOnLongClickItemTags()
                                                        true
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    .pointerInput(item.id, isEditing, isDeleting, visualChecked) {
                                        if (isEditing || isDeleting) return@pointerInput
                                        val swipeTriggerPx = 14.dp.toPx()
                                        val touchSlop = viewConfiguration.touchSlop
                                        awaitEachGesture {
                                            val down = awaitFirstDown(requireUnconsumed = false)
                                            if (currentIsCompressedProvider()) {
                                                while (true) {
                                                    val ev = awaitPointerEvent(PointerEventPass.Main)
                                                    val ch = ev.changes.firstOrNull { it.id == down.id }
                                                    if (ch == null || ch.isConsumed) break
                                                    if (!ch.pressed) {
                                                        val d = hypot(
                                                            ch.position.x - down.position.x,
                                                            ch.position.y - down.position.y
                                                        )
                                                        if (d <= touchSlop) {
                                                            ch.consume()
                                                            currentOnCompressedClick?.invoke()
                                                        }
                                                        break
                                                    }
                                                }
                                                return@awaitEachGesture
                                            }
                                            isChildButtonPressed = true
                                            val startedOnTagsFace = currentIsShowingTagsFace
                                            val showSink = startedOnTagsFace || !currentVisualChecked
                                            if (showSink) {
                                                isBellPressed = true
                                            }
                                            var didSwipeFlip = false
                                            var didLongPress = false
                                            val longPressJob = if (startedOnTagsFace || !currentVisualChecked) {
                                                scope.launch {
                                                    delay(350L)
                                                    if (!didSwipeFlip && !currentIsEditing && !isDeleting) {
                                                        didLongPress = true
                                                        isBellPressed = false
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        if (startedOnTagsFace) {
                                                            currentOnLongClickItemTags()
                                                        } else {
                                                            currentOnQuickItemReminder15m()
                                                        }
                                                    }
                                                }
                                            } else {
                                                null
                                            }
                                            try {
                                                while (true) {
                                                    val event = awaitPointerEvent(PointerEventPass.Main)
                                                    val change = event.changes.firstOrNull { it.id == down.id }
                                                    if (change == null) {
                                                        longPressJob?.cancel()
                                                        break
                                                    }
                                                    val dx = change.position.x - down.position.x
                                                    val dy = change.position.y - down.position.y
                                                    val dist = hypot(dx, dy)

                                                    // 全狀態（含 0 標籤、已備／待備）皆允許於狀態膠囊水平滑動翻轉，並於 Main pass 優先消費避免被 HorizontalPager 攔截
                                                    if (
                                                        !didLongPress &&
                                                        abs(dx) > touchSlop * 0.45f &&
                                                        abs(dx) > abs(dy)
                                                    ) {
                                                        change.consume()
                                                        if (!didSwipeFlip && abs(dx) >= swipeTriggerPx) {
                                                            didSwipeFlip = true
                                                            longPressJob?.cancel()
                                                            isBellPressed = false
                                                            isShowingTagsFace = !currentIsShowingTagsFace
                                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        }
                                                    } else if (!didSwipeFlip && abs(dy) > touchSlop && abs(dy) > abs(dx)) {
                                                        // 讓垂直捲動順暢接手
                                                        longPressJob?.cancel()
                                                        isBellPressed = false
                                                        break
                                                    } else if (dist > touchSlop) {
                                                        longPressJob?.cancel()
                                                        isBellPressed = false
                                                    }

                                                    if (!change.pressed) {
                                                        longPressJob?.cancel()
                                                        isBellPressed = false
                                                        if (!didSwipeFlip && !didLongPress && dist <= touchSlop) {
                                                            val inBounds = change.position.x in 0f..size.width.toFloat() &&
                                                                change.position.y in 0f..size.height.toFloat()
                                                            if (inBounds) {
                                                                change.consume()
                                                                when {
                                                                    currentIsShowingTagsFace -> {
                                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                                        currentOnEditTags()
                                                                    }
                                                                    !currentVisualChecked -> {
                                                                        currentOnOpenReminder()
                                                                    }
                                                                    else -> {
                                                                        triggerToggleOptimistic()
                                                                    }
                                                                }
                                                            }
                                                        }
                                                        break
                                                    }
                                                }
                                            } finally {
                                                longPressJob?.cancel()
                                                isBellPressed = false
                                                isChildButtonPressed = false
                                            }
                                        }
                                    }
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
