package com.example.totalcheck.ui.main

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.example.totalcheck.data.local.CheckItemEntity
import com.example.totalcheck.data.local.ChecklistEntity
import com.example.totalcheck.ui.theme.LocalReducedMotion
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

private const val ACTIVE_TAG_SCALE = 1.08f

internal class DistantTabTransition(
    val fromPage: Int,
    val toPage: Int,
    val progress: Animatable<Float, AnimationVector1D> = Animatable(0f)
)

private class TagRowBoundsHolder {
    var placeablesBuffer: Array<Placeable?> = arrayOfNulls(16)
    var fractionsBuffer: FloatArray = FloatArray(16)
    var scalesBuffer: FloatArray = FloatArray(16)
    var unscaledLeftsBuffer: FloatArray = FloatArray(16)
    val visualLefts = FloatArray(64)
    val visualRights = FloatArray(64)
    var count: Int = 0

    fun ensureCapacity(size: Int) {
        if (placeablesBuffer.size < size) {
            val nextSize = maxOf(size, placeablesBuffer.size * 2)
            placeablesBuffer = arrayOfNulls(nextSize)
            fractionsBuffer = FloatArray(nextSize)
            scalesBuffer = FloatArray(nextSize)
            unscaledLeftsBuffer = FloatArray(nextSize)
        }
    }
}

@Composable
internal fun ExpandingChecklistTabBar(
    checklists: List<ChecklistEntity>,
    itemsByChecklist: Map<Long, List<CheckItemEntity>>,
    pagerState: PagerState,
    distantTabTransition: DistantTabTransition?,
    onSelectPage: (Int) -> Unit,
    onLongPressTab: (ChecklistEntity) -> Unit,
    onAddChecklist: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val isReducedMotion = LocalReducedMotion.current
    val currentDistant by rememberUpdatedState(distantTabTransition)

    val scrollState = rememberScrollState()
    var viewportWidthPx by remember { mutableIntStateOf(0) }
    val boundsHolder = remember { TagRowBoundsHolder() }
    val focusRequesters = remember { HashMap<Long, FocusRequester>() }

    val activePageIndex by remember(pagerState, checklists.size) {
        derivedStateOf {
            (currentDistant?.toPage ?: pagerState.targetPage)
                .coerceIn(0, (checklists.size - 1).coerceAtLeast(0))
        }
    }

    val selectionFraction: (Int) -> Float = { index ->
        val distant = currentDistant
        if (distant != null) {
            val p = distant.progress.value.coerceIn(0f, 1f)
            when (index) {
                distant.toPage -> p
                distant.fromPage -> 1f - p
                else -> 0f
            }
        } else {
            val pagePos = pagerState.currentPage + pagerState.currentPageOffsetFraction
            (1f - abs(pagePos - index)).coerceIn(0f, 1f)
        }
    }

    LaunchedEffect(activePageIndex, checklists.size, isReducedMotion) {
        if (activePageIndex !in checklists.indices) return@LaunchedEffect

        suspend fun ensureTagVisible() {
            withFrameNanos { }
            if (viewportWidthPx <= 0 || activePageIndex >= boundsHolder.count) return
            val edgePaddingPx = with(density) { 14.dp.toPx() }
            val desiredLeft = (boundsHolder.visualLefts[activePageIndex] - edgePaddingPx).coerceAtLeast(0f)
            val desiredRight = boundsHolder.visualRights[activePageIndex] + edgePaddingPx
            val currentStart = scrollState.value.toFloat()
            val currentEnd = currentStart + viewportWidthPx
            val targetScroll = when {
                desiredLeft < currentStart -> desiredLeft.roundToInt()
                desiredRight > currentEnd -> (desiredRight - viewportWidthPx).roundToInt()
                else -> null
            }?.coerceIn(0, scrollState.maxValue)

            if (targetScroll != null && targetScroll != scrollState.value) {
                if (isReducedMotion) {
                    scrollState.scrollTo(targetScroll)
                } else {
                    scrollState.animateScrollTo(
                        value = targetScroll,
                        animationSpec = spring(dampingRatio = 0.85f, stiffness = 340f)
                    )
                }
            }
        }

        ensureTagVisible()
        if (!isReducedMotion) {
            delay(180L)
            ensureTagVisible()
        }
    }

    val baseGapPx = with(density) { 8.dp.toPx() }
    val extraNeighborMarginPx = with(density) { 4.dp.toPx() }
    val rowHorizontalEdgePadPx = with(density) { 4.dp.toPx() }
    val verticalScalePadPx = with(density) { 4.dp.roundToPx() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .onSizeChanged { viewportWidthPx = it.width }
                .horizontalScroll(scrollState)
        ) {
            Layout(
                content = {
                    checklists.forEachIndexed { index, checklist ->
                        key(checklist.id) {
                            val items = itemsByChecklist[checklist.id].orEmpty()
                            val totalCount = items.size
                            val uncheckedCount = items.count { !it.isChecked }
                            val requester = focusRequesters.getOrPut(checklist.id) { FocusRequester() }
                            val currentIndex by rememberUpdatedState(index)
                            val totalListSize by rememberUpdatedState(checklists.size)
                            val currentChecklists by rememberUpdatedState(checklists)

                            ExpandingChecklistTag(
                                checklist = checklist,
                                totalCount = totalCount,
                                uncheckedCount = uncheckedCount,
                                isSelected = activePageIndex == index,
                                selectionFractionProvider = { selectionFraction(currentIndex) },
                                focusRequester = requester,
                                onSelect = { onSelectPage(currentIndex) },
                                onLongPress = { onLongPressTab(checklist) },
                                onMoveFocusLeft = {
                                    val prevIdx = currentIndex - 1
                                    if (prevIdx >= 0) {
                                        onSelectPage(prevIdx)
                                        currentChecklists.getOrNull(prevIdx)?.let { prevList ->
                                            focusRequesters[prevList.id]?.requestFocus()
                                        }
                                    }
                                },
                                onMoveFocusRight = {
                                    val nextIdx = currentIndex + 1
                                    if (nextIdx < totalListSize) {
                                        onSelectPage(nextIdx)
                                        currentChecklists.getOrNull(nextIdx)?.let { nextList ->
                                            focusRequesters[nextList.id]?.requestFocus()
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            ) { measurables, _ ->
                val count = measurables.size
                if (count == 0) {
                    boundsHolder.count = 0
                    return@Layout layout(0, 0) {}
                }

                boundsHolder.ensureCapacity(count)
                val placeables = boundsHolder.placeablesBuffer
                val fractions = boundsHolder.fractionsBuffer
                val scales = boundsHolder.scalesBuffer
                val unscaledLefts = boundsHolder.unscaledLeftsBuffer
                val unboundedConstraints = Constraints()

                var maxTagHeight = 0
                for (i in 0 until count) {
                    val p = measurables[i].measure(unboundedConstraints)
                    placeables[i] = p
                    if (p.height > maxTagHeight) {
                        maxTagHeight = p.height
                    }
                }

                var xCursor = rowHorizontalEdgePadPx
                val trackedCount = minOf(count, boundsHolder.visualLefts.size)
                boundsHolder.count = trackedCount

                for (i in 0 until count) {
                    val p = placeables[i]!!
                    val t = selectionFraction(i).coerceIn(0f, 1f)
                    fractions[i] = t
                    val scale = if (isReducedMotion) 1f else lerp(1f, ACTIVE_TAG_SCALE, t)
                    scales[i] = scale

                    val placeableW = p.width.toFloat()
                    val visualW = placeableW * scale
                    val extraMargin = extraNeighborMarginPx * t

                    val visualLeft = xCursor + extraMargin
                    val visualRight = visualLeft + visualW
                    if (i < trackedCount) {
                        boundsHolder.visualLefts[i] = visualLeft
                        boundsHolder.visualRights[i] = visualRight
                    }

                    unscaledLefts[i] = visualLeft + (visualW - placeableW) * 0.5f

                    xCursor = visualRight + extraMargin
                    if (i < count - 1) {
                        xCursor += baseGapPx
                    }
                }

                val totalWidth = (xCursor + rowHorizontalEdgePadPx).roundToInt().coerceAtLeast(0)
                val totalHeight = (maxTagHeight + verticalScalePadPx * 2).coerceAtLeast(0)

                layout(totalWidth, totalHeight) {
                    for (i in 0 until count) {
                        val p = placeables[i] ?: continue
                        val scale = scales[i]
                        val t = fractions[i]
                        val yPos = verticalScalePadPx + (maxTagHeight - p.height) / 2
                        p.placeRelativeWithLayer(
                            x = unscaledLefts[i].roundToInt(),
                            y = yPos,
                            zIndex = 1f + t
                        ) {
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = TransformOrigin(0.5f, 0.5f)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Surface(
            shape = RoundedCornerShape(50),
            color = colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, colorScheme.outlineVariant)
        ) {
            FilledTonalIconButton(
                onClick = onAddChecklist,
                modifier = Modifier
                    .size(42.dp)
                    .testTag("add_checklist_button"),
                shape = RoundedCornerShape(50),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = colorScheme.surfaceContainerHigh,
                    contentColor = colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "新增清單",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExpandingChecklistTag(
    checklist: ChecklistEntity,
    totalCount: Int,
    uncheckedCount: Int,
    isSelected: Boolean,
    selectionFractionProvider: () -> Float,
    focusRequester: FocusRequester,
    onSelect: () -> Unit,
    onLongPress: () -> Unit,
    onMoveFocusLeft: () -> Unit,
    onMoveFocusRight: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val interactionSource = remember(checklist.id) { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    var isFocused by remember(checklist.id) { mutableStateOf(false) }

    val unselectedContainerColor = colorScheme.surfaceContainerHigh
    val hoveredContainerColor = colorScheme.surfaceContainerHighest
    val selectedContainerColor = colorScheme.primaryContainer
    val unselectedBorderColor = colorScheme.outlineVariant
    val selectedBorderColor = colorScheme.primary
    val unselectedLabelColor = colorScheme.onSurfaceVariant
    val selectedLabelColor = colorScheme.onPrimaryContainer
    val focusRingColor = colorScheme.primary

    val titleStyle = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
    val countStyle = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold)
    val countText = if (totalCount == 0) "(0)" else "($uncheckedCount/$totalCount)"
    val a11yDescription = if (totalCount == 0) {
        "${checklist.name}，目前沒有項目"
    } else {
        "${checklist.name}，尚缺 $uncheckedCount 項，共 $totalCount 項"
    }

    val basePadHPx = with(density) { 14.dp.toPx() }
    val expandedPadHPx = with(density) { 18.dp.toPx() }
    val padVPx = with(density) { 9.dp.toPx() }
    val minPillHeightPx = with(density) { 40.dp.roundToPx() }
    val countGapFullPx = with(density) { 6.dp.toPx() }
    val maxTitleWidthPx = with(density) { 168.dp.roundToPx() }
    val titleConstraints = remember(maxTitleWidthPx) { Constraints(maxWidth = maxTitleWidthPx) }
    val countConstraints = remember { Constraints() }

    Layout(
        modifier = Modifier
            .testTag("checklist_tab_${checklist.id}")
            .drawBehind {
                val t = selectionFractionProvider().coerceIn(0f, 1f)
                val radius = size.height / 2f
                val corner = CornerRadius(radius, radius)
                val baseBg = if (isPressed || isHovered) hoveredContainerColor else unselectedContainerColor
                drawRoundRect(
                    color = lerpColor(baseBg, selectedContainerColor, t),
                    cornerRadius = corner
                )
                val borderStrokePx = lerp(1.dp.toPx(), 1.5.dp.toPx(), t)
                drawRoundRect(
                    color = lerpColor(unselectedBorderColor, selectedBorderColor, t),
                    cornerRadius = corner,
                    style = Stroke(width = borderStrokePx)
                )
                if (isFocused) {
                    val focusInset = 2.dp.toPx()
                    drawRoundRect(
                        color = focusRingColor.copy(alpha = 0.85f),
                        topLeft = Offset(focusInset, focusInset),
                        size = Size(
                            width = (size.width - focusInset * 2f).coerceAtLeast(1f),
                            height = (size.height - focusInset * 2f).coerceAtLeast(1f)
                        ),
                        cornerRadius = CornerRadius(
                            (radius - focusInset).coerceAtLeast(0f),
                            (radius - focusInset).coerceAtLeast(0f)
                        ),
                        style = Stroke(width = 1.75.dp.toPx())
                    )
                }
            }
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionLeft -> {
                            onMoveFocusLeft()
                            true
                        }
                        Key.DirectionRight -> {
                            onMoveFocusRight()
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
            .hoverable(interactionSource = interactionSource)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onSelect,
                onLongClick = onLongPress
            )
            .semantics {
                selected = isSelected
                contentDescription = a11yDescription
            },
        content = {
            BasicText(
                text = checklist.name,
                style = titleStyle,
                color = {
                    val t = selectionFractionProvider().coerceIn(0f, 1f)
                    lerpColor(unselectedLabelColor, selectedLabelColor, t)
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            BasicText(
                text = countText,
                style = countStyle,
                color = { selectedLabelColor },
                maxLines = 1
            )
        }
    ) { tagMeasurables, _ ->
        // 兩個 BasicText 皆使用固定 Constraints，Compose LayoutNode 會直接命中測量快取，0 文字重排開銷
        val titlePlaceable = tagMeasurables[0].measure(titleConstraints)
        val countPlaceable = tagMeasurables[1].measure(countConstraints)
        val t = selectionFractionProvider().coerceIn(0f, 1f)

        val padHPx = lerp(basePadHPx, expandedPadHPx, t)
        val countRevealWidth = (countGapFullPx + countPlaceable.width) * t
        val contentWidth = padHPx * 2f + titlePlaceable.width + countRevealWidth
        val contentHeight = padVPx * 2f + maxOf(titlePlaceable.height, countPlaceable.height)

        val pillWidth = contentWidth.roundToInt().coerceAtLeast(0)
        val pillHeight = contentHeight.roundToInt().coerceAtLeast(minPillHeightPx)

        layout(pillWidth, pillHeight) {
            val titleY = (pillHeight - titlePlaceable.height) / 2
            val titleX = padHPx.roundToInt()
            titlePlaceable.placeRelative(titleX, titleY)

            if (t > 0.05f && countPlaceable.width > 0) {
                val countX = (padHPx + titlePlaceable.width + countGapFullPx * t).roundToInt()
                val countY = (pillHeight - countPlaceable.height) / 2
                val revealAlpha = ((t - 0.16f) / 0.72f).coerceIn(0f, 1f)
                val revealScale = lerp(0.84f, 1f, t)
                countPlaceable.placeRelativeWithLayer(countX, countY) {
                    alpha = revealAlpha
                    scaleX = revealScale
                    scaleY = revealScale
                    transformOrigin = TransformOrigin(0f, 0.5f)
                }
            }
        }
    }
}
