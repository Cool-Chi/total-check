package com.example.totalcheck.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.example.totalcheck.R
import com.example.totalcheck.data.local.CheckItemEntity
import com.example.totalcheck.data.local.ChecklistEntity
import com.example.totalcheck.ui.theme.LocalReducedMotion
import kotlin.math.roundToInt

private val DARK_BLOCK_COLOR = Color.White.copy(alpha = 0.06f)
private val DARK_SHEEN_COLOR = Color.White.copy(alpha = 0.10f)
private val LIGHT_BLOCK_COLOR = Color(9, 11, 16).copy(alpha = 0.09f)
private val LIGHT_SHEEN_COLOR = Color.White.copy(alpha = 0.20f)

private data class SkeletonTabSeed(
    val id: Long,
    val name: String,
    val countTemplate: String
)

@Composable
fun TotalCheckSkeletonShimmer(
    isDarkTheme: Boolean,
    checklists: List<ChecklistEntity>,
    itemsByChecklist: Map<Long, List<CheckItemEntity>>,
    showUncheckedOnly: Boolean,
    modifier: Modifier = Modifier,
    activeChecklistId: Long? = null,
    durationMillis: Int = 1000,
    staggerMillis: Int = 80
) {
    val colorScheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val isReducedMotion = LocalReducedMotion.current
    var elapsedMillis by remember { mutableLongStateOf(0L) }

    if (!isReducedMotion) {
        LaunchedEffect(durationMillis, staggerMillis) {
            var startNanos = 0L
            while (true) {
                withFrameNanos { frameNanos ->
                    if (startNanos == 0L) {
                        startNanos = frameNanos
                    }
                    elapsedMillis = ((frameNanos - startNanos) / 1_000_000L).coerceAtLeast(0L)
                }
            }
        }
    } else {
        elapsedMillis = 0L
    }

    val blockColor = if (isDarkTheme) DARK_BLOCK_COLOR else LIGHT_BLOCK_COLOR
    val sheenColor = if (isDarkTheme) DARK_SHEEN_COLOR else LIGHT_SHEEN_COLOR
    val elapsedProvider = remember { { elapsedMillis } }
    val titleStyle = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
    val countStyle = MaterialTheme.typography.labelMedium.copy(
        fontWeight = FontWeight.ExtraBold,
        fontFeatureSettings = "tnum"
    )
    val borderColor = colorScheme.outlineVariant

    val carryOnName = stringResource(R.string.seed_checklist_carry_on)
    val labName = stringResource(R.string.seed_checklist_lab)
    val travelName = stringResource(R.string.seed_checklist_travel)
    val fallbackTabSeeds = remember(carryOnName, labName, travelName) {
        listOf(
            SkeletonTabSeed(id = -1L, name = carryOnName, countTemplate = "(8/8)"),
            SkeletonTabSeed(id = -2L, name = labName, countTemplate = "(8/8)"),
            SkeletonTabSeed(id = -3L, name = travelName, countTemplate = "(8/8)")
        )
    }
    val fallbackItemLabels = stringArrayResource(R.array.seed_items_carry_on).toList()

    val resolvedTabSeeds = remember(checklists, itemsByChecklist, fallbackTabSeeds) {
        if (checklists.isNotEmpty()) {
            checklists.map { checklist ->
                val count = itemsByChecklist[checklist.id].orEmpty().size
                val template = if (count <= 0) {
                    "(0)"
                } else {
                    val d = count.toString().length.coerceAtLeast(1)
                    val m = "8".repeat(d)
                    "($m/$m)"
                }
                SkeletonTabSeed(
                    id = checklist.id,
                    name = checklist.name,
                    countTemplate = template
                )
            }
        } else {
            fallbackTabSeeds
        }
    }

    val activeChecklistIndex = remember(checklists, activeChecklistId) {
        if (checklists.isEmpty()) {
            0
        } else {
            activeChecklistId
                ?.let { target -> checklists.indexOfFirst { it.id == target }.takeIf { it >= 0 } }
                ?: 0
        }
    }
    val targetChecklist = checklists.getOrNull(activeChecklistIndex) ?: checklists.firstOrNull()
    val hasRealTargetItems = targetChecklist != null && itemsByChecklist.containsKey(targetChecklist.id)
    val resolvedTargetItems = remember(targetChecklist?.id, itemsByChecklist, hasRealTargetItems) {
        if (hasRealTargetItems && targetChecklist != null) {
            itemsByChecklist[targetChecklist.id].orEmpty()
        } else {
            emptyList()
        }
    }
    val resolvedItemLabels = remember(
        resolvedTargetItems,
        showUncheckedOnly,
        hasRealTargetItems,
        fallbackItemLabels
    ) {
        if (hasRealTargetItems) {
            val visible = if (showUncheckedOnly) {
                resolvedTargetItems.filter { !it.isChecked }
            } else {
                resolvedTargetItems
            }
            visible.map { it.label }
        } else {
            fallbackItemLabels
        }
    }

    val targetTotalCount = if (hasRealTargetItems) resolvedTargetItems.size else fallbackItemLabels.size
    val targetUncheckedCount = if (hasRealTargetItems) {
        resolvedTargetItems.count { !it.isChecked }
    } else {
        fallbackItemLabels.size
    }

    val loadingDescription = stringResource(R.string.skeleton_loading_state)
    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("skeleton_shimmer_container")
            .semantics {
                liveRegion = LiveRegionMode.Polite
                stateDescription = loadingDescription
            }
    ) {
        // Blocks 0..1: 頂部膠囊分頁列骨架（1:1 對齊 ExpandingChecklistTabBar 的內距、放大尺度與右側固定 + 按鈕）
        val rowHorizontalEdgePadPx = with(density) { 4.dp.toPx() }
        val baseGapPx = with(density) { 8.dp.toPx() }
        val extraNeighborMarginPx = with(density) { 4.dp.toPx() }
        val verticalScalePadPx = with(density) { 4.dp.roundToPx() }
        val pillConstraints = remember { Constraints() }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp, bottom = 4.dp)
                .clearAndSetSemantics { },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clipToBounds(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.width(12.dp))

                Layout(
                    content = {
                        resolvedTabSeeds.forEachIndexed { index, tabSeed ->
                            val isSelectedTab = index == activeChecklistIndex
                            val tabBlockIndex = if (isSelectedTab) 0 else 1
                            val padH = if (isSelectedTab) 18.dp else 14.dp
                            Box(
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 40.dp)
                                    .clip(RoundedCornerShape(50))
                                    .shimmerBlock(
                                        blockIndex = tabBlockIndex,
                                        blockColor = blockColor,
                                        sheenColor = sheenColor,
                                        isReducedMotion = isReducedMotion,
                                        elapsedMillisProvider = elapsedProvider,
                                        durationMillis = durationMillis,
                                        staggerMillis = staggerMillis,
                                        shape = RoundedCornerShape(50)
                                    )
                                    .drawBehind {
                                        val radius = size.height / 2f
                                        drawRoundRect(
                                            color = borderColor,
                                            cornerRadius = CornerRadius(radius, radius),
                                            style = Stroke(width = if (isSelectedTab) 1.5.dp.toPx() else 1.dp.toPx())
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = padH, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    BasicText(
                                        text = tabSeed.name,
                                        style = titleStyle,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .widthIn(max = 168.dp)
                                            .alpha(0f)
                                    )
                                    if (isSelectedTab) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        BasicText(
                                            text = tabSeed.countTemplate,
                                            style = countStyle,
                                            maxLines = 1,
                                            modifier = Modifier.alpha(0f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                ) { measurables, _ ->
                    val count = measurables.size
                    if (count == 0) {
                        return@Layout layout(0, 0) {}
                    }
                    val placeables = Array(count) { i ->
                        measurables[i].measure(pillConstraints)
                    }
                    val maxTagHeight = placeables.maxOf { it.height }
                    var xCursor = rowHorizontalEdgePadPx
                    val unscaledLefts = FloatArray(count)
                    val scales = FloatArray(count)

                    for (i in 0 until count) {
                        val p = placeables[i]
                        val isSelectedTab = i == activeChecklistIndex
                        val t = if (isSelectedTab) 1f else 0f
                        val scale = if (isSelectedTab && !isReducedMotion) 1.08f else 1f
                        scales[i] = scale
                        val placeableW = p.width.toFloat()
                        val visualW = placeableW * scale
                        val extraMargin = extraNeighborMarginPx * t
                        val visualLeft = xCursor + extraMargin
                        val visualRight = visualLeft + visualW
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
                            val p = placeables[i]
                            val scale = scales[i]
                            val yPos = verticalScalePadPx + (maxTagHeight - p.height) / 2
                            p.placeRelativeWithLayer(
                                x = unscaledLefts[i].roundToInt(),
                                y = yPos,
                                zIndex = if (i == activeChecklistIndex) 2f else 1f
                            ) {
                                scaleX = scale
                                scaleY = scale
                                transformOrigin = TransformOrigin(0.5f, 0.5f)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            Surface(
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(40.dp)
                    .clearAndSetSemantics { },
                shape = RoundedCornerShape(50),
                color = colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, borderColor)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .shimmerBlock(
                            blockIndex = 1,
                            blockColor = blockColor,
                            sheenColor = sheenColor,
                            isReducedMotion = isReducedMotion,
                            elapsedMillisProvider = elapsedProvider,
                            durationMillis = durationMillis,
                            staggerMillis = staggerMillis,
                            shape = CircleShape
                        )
                )
            }
        }

        // Blocks 2..3: 進度模組卡片骨架（1:1 對齊 CapsuleProgressModule 的 24.dp 圓角、TactileFilterChip 48.dp 觸控盒與提醒膠囊按鈕列）
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clearAndSetSemantics { },
            shape = RoundedCornerShape(24.dp),
            color = colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, borderColor)
        ) {
            Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(contentAlignment = Alignment.CenterStart) {
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            modifier = Modifier.alpha(0f)
                        ) {
                            Text(
                                text = if (targetUncheckedCount == 0 && targetTotalCount > 0) {
                                    stringResource(R.string.progress_all_set)
                                } else if (hasRealTargetItems) {
                                    stringResource(R.string.progress_left_count, targetUncheckedCount)
                                } else {
                                    stringResource(R.string.skeleton_sample_left)
                                },
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontFeatureSettings = "tnum"
                                ),
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (hasRealTargetItems) {
                                    stringResource(R.string.progress_total_count, targetTotalCount)
                                } else {
                                    stringResource(R.string.skeleton_sample_total)
                                },
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFeatureSettings = "tnum"
                                ),
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .width(148.dp)
                                .height(30.dp)
                                .shimmerBlock(
                                    blockIndex = 2,
                                    blockColor = blockColor,
                                    sheenColor = sheenColor,
                                    isReducedMotion = isReducedMotion,
                                    elapsedMillisProvider = elapsedProvider,
                                    durationMillis = durationMillis,
                                    staggerMillis = staggerMillis,
                                    shape = RoundedCornerShape(8.dp)
                                )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .defaultMinSize(minWidth = 58.dp, minHeight = 40.dp)
                            .shimmerBlock(
                                blockIndex = 2,
                                blockColor = blockColor,
                                sheenColor = sheenColor,
                                isReducedMotion = isReducedMotion,
                                elapsedMillisProvider = elapsedProvider,
                                durationMillis = durationMillis,
                                staggerMillis = staggerMillis,
                                shape = RoundedCornerShape(50)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.alpha(0f)
                        ) {
                            Spacer(modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.action_reset_all),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .shimmerBlock(
                            blockIndex = 3,
                            blockColor = blockColor,
                            sheenColor = sheenColor,
                            isReducedMotion = isReducedMotion,
                            elapsedMillisProvider = elapsedProvider,
                            durationMillis = durationMillis,
                            staggerMillis = staggerMillis,
                            shape = RoundedCornerShape(50)
                        )
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .shimmerBlock(
                                blockIndex = 3,
                                blockColor = blockColor,
                                sheenColor = sheenColor,
                                isReducedMotion = isReducedMotion,
                                elapsedMillisProvider = elapsedProvider,
                                durationMillis = durationMillis,
                                staggerMillis = staggerMillis,
                                shape = CircleShape
                            )
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                    ) {
                        repeat(4) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .shimmerBlock(
                                        blockIndex = 3,
                                        blockColor = blockColor,
                                        sheenColor = sheenColor,
                                        isReducedMotion = isReducedMotion,
                                        elapsedMillisProvider = elapsedProvider,
                                        durationMillis = durationMillis,
                                        staggerMillis = staggerMillis,
                                        shape = CircleShape
                                    )
                            )
                        }
                    }
                }
            }
        }

        // Blocks 4..7: 清點項目膠囊卡片骨架（1:1 對齊真實清單筆數、LazyColumn 內距與 CapsuleCheckItemRow 三顆右側按鈕尺寸）
        LazyColumn(
            userScrollEnabled = false,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clearAndSetSemantics { },
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(resolvedItemLabels) { rowIndex, itemLabel ->
                val blockIndex = 4 + rowIndex.coerceAtMost(3)
                val titleWidthFraction = remember(itemLabel) {
                    (0.45f + (itemLabel.length.coerceIn(2, 16) / 16f) * 0.42f).coerceIn(0.45f, 0.87f)
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, borderColor)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(6.dp)
                                .height(24.dp)
                                .shimmerBlock(
                                    blockIndex = blockIndex,
                                    blockColor = blockColor,
                                    sheenColor = sheenColor,
                                    isReducedMotion = isReducedMotion,
                                    elapsedMillisProvider = elapsedProvider,
                                    durationMillis = durationMillis,
                                    staggerMillis = staggerMillis,
                                    shape = RoundedCornerShape(50)
                                )
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = itemLabel,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.alpha(0f)
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(titleWidthFraction)
                                    .height(20.dp)
                                    .shimmerBlock(
                                        blockIndex = blockIndex,
                                        blockColor = blockColor,
                                        sheenColor = sheenColor,
                                        isReducedMotion = isReducedMotion,
                                        elapsedMillisProvider = elapsedProvider,
                                        durationMillis = durationMillis,
                                        staggerMillis = staggerMillis,
                                        shape = RoundedCornerShape(6.dp)
                                    )
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .width(74.dp)
                                .height(28.dp)
                                .shimmerBlock(
                                    blockIndex = blockIndex,
                                    blockColor = blockColor,
                                    sheenColor = sheenColor,
                                    isReducedMotion = isReducedMotion,
                                    elapsedMillisProvider = elapsedProvider,
                                    durationMillis = durationMillis,
                                    staggerMillis = staggerMillis,
                                    shape = RoundedCornerShape(50)
                                )
                        )
                        // 單品提醒 + 編輯兩顆 32.dp 按鈕槽位
                        repeat(2) {
                            Box(
                                modifier = Modifier.size(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(17.dp)
                                        .shimmerBlock(
                                            blockIndex = blockIndex,
                                            blockColor = blockColor,
                                            sheenColor = sheenColor,
                                            isReducedMotion = isReducedMotion,
                                            elapsedMillisProvider = elapsedProvider,
                                            durationMillis = durationMillis,
                                            staggerMillis = staggerMillis,
                                            shape = CircleShape
                                        )
                                )
                            }
                        }
                        // 右側 34.dp 六點拖曳手把槽位
                        Box(
                            modifier = Modifier.size(34.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(12.dp)
                                    .height(18.dp)
                                    .shimmerBlock(
                                        blockIndex = blockIndex,
                                        blockColor = blockColor,
                                        sheenColor = sheenColor,
                                        isReducedMotion = isReducedMotion,
                                        elapsedMillisProvider = elapsedProvider,
                                        durationMillis = durationMillis,
                                        staggerMillis = staggerMillis,
                                        shape = RoundedCornerShape(4.dp)
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun Modifier.shimmerBlock(
    blockIndex: Int,
    blockColor: Color,
    sheenColor: Color,
    isReducedMotion: Boolean,
    elapsedMillisProvider: () -> Long,
    durationMillis: Int,
    staggerMillis: Int,
    shape: Shape
): Modifier = this
    .clip(shape)
    .drawBehind {
        drawRect(color = blockColor)
        if (!isReducedMotion && size.width > 0f && size.height > 0f && durationMillis > 0) {
            val delayMs = blockIndex.toLong() * staggerMillis.toLong()
            val localElapsed = elapsedMillisProvider() - delayMs
            if (localElapsed >= 0L) {
                val progress = (localElapsed % durationMillis).toFloat() / durationMillis.toFloat()
                val w = size.width
                val startX = lerp(-2f * w, 2f * w, progress)
                val endX = startX + 2f * w
                drawRect(
                    brush = Brush.linearGradient(
                        colorStops = arrayOf(
                            0f to Color.Transparent,
                            0.5f to sheenColor,
                            1f to Color.Transparent
                        ),
                        start = Offset(startX, 0f),
                        end = Offset(endX, 0f)
                    )
                )
            }
        }
    }
