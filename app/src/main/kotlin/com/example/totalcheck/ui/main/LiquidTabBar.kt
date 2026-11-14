package com.example.totalcheck.ui.main

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.totalcheck.ui.theme.LocalReducedMotion
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

@Immutable
private data class LiquidTabBounds(
    val left: Float,
    val right: Float
)

/**
 * Reusable Liquid Tab Indicator bar.
 *
 * Features asymmetric leading/trailing edge springs ("stretch then settle"),
 * mid-flight interruption continuity on rapid taps, actual tab bounds tracking
 * for equal-width or variable-width tabs, pixel-accurate dual-layer text contrast
 * clipping, and configurable Tab vs. Navigation-Button semantics.
 */
@Composable
fun LiquidTabBar(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    equalWidth: Boolean = true,
    scrollable: Boolean = false,
    useTabSemantics: Boolean = true,
    containerShape: Shape = RoundedCornerShape(50),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    indicatorColor: Color = MaterialTheme.colorScheme.primary,
    selectedContentColor: Color = MaterialTheme.colorScheme.onPrimary,
    unselectedContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    containerPadding: Dp = 4.dp,
    itemSpacing: Dp = 4.dp,
    itemContentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    textStyle: TextStyle = MaterialTheme.typography.labelLarge
) {
    if (labels.isEmpty()) return

    val safeSelectedIndex = selectedIndex.coerceIn(0, labels.lastIndex)
    val isReducedMotion = LocalReducedMotion.current

    val tabBounds = remember(labels.size) { mutableStateMapOf<Int, LiquidTabBounds>() }
    var trackWidthPx by remember { mutableIntStateOf(0) }

    val leadingEdge = remember { Animatable(0f) }
    val trailingEdge = remember { Animatable(0f) }
    var hasInitialized by remember { mutableStateOf(false) }
    var lastSelectedIndex by remember { mutableIntStateOf(safeSelectedIndex) }

    val targetBounds = tabBounds[safeSelectedIndex]

    LaunchedEffect(safeSelectedIndex, targetBounds, isReducedMotion) {
        val bounds = targetBounds ?: return@LaunchedEffect
        if (!hasInitialized || isReducedMotion || safeSelectedIndex == lastSelectedIndex) {
            leadingEdge.snapTo(bounds.left)
            trailingEdge.snapTo(bounds.right)
            hasInitialized = true
            lastSelectedIndex = safeSelectedIndex
            return@LaunchedEffect
        }

        val currentCenter = (leadingEdge.value + trailingEdge.value) * 0.5f
        val targetCenter = (bounds.left + bounds.right) * 0.5f
        val movingRight = targetCenter >= currentCenter
        lastSelectedIndex = safeSelectedIndex

        val fastSpring = spring<Float>(
            dampingRatio = 0.82f,
            stiffness = 550f,
            visibilityThreshold = 0.5f
        )
        val slowSpring = spring<Float>(
            dampingRatio = 0.88f,
            stiffness = 380f,
            visibilityThreshold = 0.5f
        )

        coroutineScope {
            launch {
                leadingEdge.animateTo(
                    targetValue = bounds.left,
                    animationSpec = if (movingRight) slowSpring else fastSpring
                )
            }
            launch {
                trailingEdge.animateTo(
                    targetValue = bounds.right,
                    animationSpec = if (movingRight) fastSpring else slowSpring
                )
            }
        }
    }

    val scrollModifier = if (scrollable && !equalWidth) {
        Modifier.horizontalScroll(rememberScrollState())
    } else {
        Modifier
    }

    val containerSemanticsModifier = if (useTabSemantics) {
        Modifier.selectableGroup()
    } else {
        Modifier
    }

    val indicatorPath = remember { Path() }

    Box(
        modifier = modifier
            .then(containerSemanticsModifier)
            .clip(containerShape)
            .background(containerColor)
            .then(scrollModifier)
            .padding(containerPadding)
    ) {
        Box(
            modifier = Modifier
                .then(if (equalWidth) Modifier.fillMaxWidth() else Modifier)
                .clipToBounds()
                .onSizeChanged { trackWidthPx = it.width }
                .drawBehind {
                    if (!hasInitialized || size.width <= 0f || size.height <= 0f) return@drawBehind
                    val maxW = if (trackWidthPx > 0) trackWidthPx.toFloat() else size.width
                    val left = min(leadingEdge.value, trailingEdge.value).coerceIn(0f, maxW)
                    val right = max(leadingEdge.value, trailingEdge.value).coerceIn(left, maxW)
                    val width = right - left
                    if (width <= 0.5f) return@drawBehind
                    val radius = size.height * 0.5f
                    drawRoundRect(
                        color = indicatorColor,
                        topLeft = Offset(left, 0f),
                        size = Size(width, size.height),
                        cornerRadius = CornerRadius(radius, radius)
                    )
                }
        ) {
            // Base interactive layer with unselected text color
            LiquidTabContentRow(
                labels = labels,
                selectedIndex = safeSelectedIndex,
                equalWidth = equalWidth,
                useTabSemantics = useTabSemantics,
                textColor = unselectedContentColor,
                itemSpacing = itemSpacing,
                itemContentPadding = itemContentPadding,
                textStyle = textStyle,
                interactive = true,
                onSelect = onSelect,
                onTabBoundsMeasured = { index, left, right ->
                    val next = LiquidTabBounds(left, right)
                    if (tabBounds[index] != next) {
                        tabBounds[index] = next
                    }
                }
            )

            // Non-interactive selected text overlay clipped to the liquid indicator pill
            LiquidTabContentRow(
                labels = labels,
                selectedIndex = safeSelectedIndex,
                equalWidth = equalWidth,
                useTabSemantics = useTabSemantics,
                textColor = selectedContentColor,
                itemSpacing = itemSpacing,
                itemContentPadding = itemContentPadding,
                textStyle = textStyle,
                interactive = false,
                onSelect = {},
                onTabBoundsMeasured = { _, _, _ -> },
                modifier = Modifier
                    .clearAndSetSemantics { }
                    .drawWithContent {
                        if (!hasInitialized || size.width <= 0f || size.height <= 0f) return@drawWithContent
                        val maxW = if (trackWidthPx > 0) trackWidthPx.toFloat() else size.width
                        val left = min(leadingEdge.value, trailingEdge.value).coerceIn(0f, maxW)
                        val right = max(leadingEdge.value, trailingEdge.value).coerceIn(left, maxW)
                        if (right - left <= 0.5f) return@drawWithContent
                        val radius = size.height * 0.5f
                        indicatorPath.reset()
                        indicatorPath.addRoundRect(
                            RoundRect(
                                left = left,
                                top = 0f,
                                right = right,
                                bottom = size.height,
                                cornerRadius = CornerRadius(radius, radius)
                            )
                        )
                        clipPath(indicatorPath) {
                            this@drawWithContent.drawContent()
                        }
                    }
            )
        }
    }
}

@Composable
private fun LiquidTabContentRow(
    labels: List<String>,
    selectedIndex: Int,
    equalWidth: Boolean,
    useTabSemantics: Boolean,
    textColor: Color,
    itemSpacing: Dp,
    itemContentPadding: PaddingValues,
    textStyle: TextStyle,
    interactive: Boolean,
    onSelect: (Int) -> Unit,
    onTabBoundsMeasured: (index: Int, left: Float, right: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.then(if (equalWidth) Modifier.fillMaxWidth() else Modifier),
        horizontalArrangement = Arrangement.spacedBy(itemSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        labels.forEachIndexed { index, label ->
            val isSelected = index == selectedIndex
            val itemModifier = Modifier
                .then(if (equalWidth) Modifier.weight(1f) else Modifier)
                .then(
                    if (interactive) {
                        Modifier
                            .onGloballyPositioned { coords ->
                                val left = coords.positionInParent().x
                                val right = left + coords.size.width.toFloat()
                                onTabBoundsMeasured(index, left, right)
                            }
                            .then(
                                if (useTabSemantics) {
                                    Modifier.semantics { selected = isSelected }
                                } else {
                                    Modifier
                                }
                            )
                            .slideOutCancelClickable(
                                shape = RoundedCornerShape(50),
                                role = if (useTabSemantics) Role.Tab else Role.Button,
                                sinkScale = 1f,
                                sinkTranslationY = 0.dp,
                                maxDeepenAlpha = 0.08f,
                                onClick = { onSelect(index) }
                            )
                    } else {
                        Modifier
                    }
                )

            Box(
                modifier = itemModifier.padding(itemContentPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = textStyle,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
