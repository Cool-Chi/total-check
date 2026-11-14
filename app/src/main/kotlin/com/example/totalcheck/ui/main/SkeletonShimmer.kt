package com.example.totalcheck.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.example.totalcheck.R
import com.example.totalcheck.data.local.CheckItemEntity
import com.example.totalcheck.data.local.ChecklistEntity
import com.example.totalcheck.ui.theme.LocalReducedMotion

private val DARK_BLOCK_COLOR = Color.White.copy(alpha = 0.06f)
private val DARK_SHEEN_COLOR = Color.White.copy(alpha = 0.10f)
private val LIGHT_BLOCK_COLOR = Color(9, 11, 16).copy(alpha = 0.09f)
private val LIGHT_SHEEN_COLOR = Color.White.copy(alpha = 0.20f)

@Composable
fun TotalCheckSkeletonShimmer(
    isDarkTheme: Boolean,
    checklists: List<ChecklistEntity>,
    itemsByChecklist: Map<Long, List<CheckItemEntity>>,
    showUncheckedOnly: Boolean,
    modifier: Modifier = Modifier,
    durationMillis: Int = 1000,
    staggerMillis: Int = 80
) {
    val colorScheme = MaterialTheme.colorScheme
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
    val tabLabelStyle = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
    val borderColor = colorScheme.outlineVariant

    val carryOnName = stringResource(R.string.seed_checklist_carry_on)
    val labName = stringResource(R.string.seed_checklist_lab)
    val travelName = stringResource(R.string.seed_checklist_travel)
    val fallbackTabLabels = remember(carryOnName, labName, travelName) {
        listOf("$carryOnName (4)", "$labName (4)", "$travelName (4)")
    }
    val fallbackItemLabels = stringArrayResource(R.array.seed_items_carry_on).toList()

    val resolvedTabLabels = remember(checklists, itemsByChecklist, fallbackTabLabels) {
        if (checklists.isNotEmpty()) {
            checklists.map { checklist ->
                val unchecked = itemsByChecklist[checklist.id].orEmpty().count { !it.isChecked }
                if (unchecked > 0) "${checklist.name} ($unchecked)" else checklist.name
            }
        } else {
            fallbackTabLabels
        }
    }

    val firstChecklist = checklists.firstOrNull()
    val hasRealFirstChecklistItems = firstChecklist != null && itemsByChecklist.containsKey(firstChecklist.id)
    val resolvedItemLabels = remember(
        firstChecklist?.id,
        itemsByChecklist,
        showUncheckedOnly,
        hasRealFirstChecklistItems,
        fallbackItemLabels
    ) {
        if (hasRealFirstChecklistItems && firstChecklist != null) {
            val raw = itemsByChecklist[firstChecklist.id].orEmpty()
            val visible = if (showUncheckedOnly) raw.filter { !it.isChecked } else raw
            visible.map { it.label }
        } else {
            fallbackItemLabels
        }
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
        // Blocks 0..1: 頂部膠囊分頁列骨架（對齊 ExpandingChecklistTabBar 與尾端新增按鈕）
        LazyRow(
            userScrollEnabled = false,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 6.dp)
                .clearAndSetSemantics { },
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            itemsIndexed(resolvedTabLabels) { index, labelText ->
                val tabBlockIndex = if (index == 0) 0 else 1
                Box(
                    modifier = Modifier
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
                                style = Stroke(width = 1.dp.toPx())
                            )
                        }
                ) {
                    BasicText(
                        text = labelText,
                        style = tabLabelStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                            .alpha(0f)
                    )
                }
            }

            item {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, borderColor)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
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
        }

        // Blocks 2..3: 進度模組卡片骨架（使用等價佈局撐開，1:1 對齊 CapsuleProgressModule 的 24.dp 圓角與高度）
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
                                text = stringResource(R.string.skeleton_sample_left),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.skeleton_sample_total),
                                style = MaterialTheme.typography.bodyMedium,
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

                    Box(contentAlignment = Alignment.Center) {
                        FilledTonalButton(
                            onClick = {},
                            enabled = false,
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                disabledContainerColor = Color.Transparent,
                                disabledContentColor = Color.Transparent
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.alpha(0f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.RestartAlt,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = stringResource(R.string.action_reset_all), style = MaterialTheme.typography.labelLarge)
                        }
                        Box(
                            modifier = Modifier
                                .heightIn(min = 40.dp)
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
                                Text(text = stringResource(R.string.action_reset_all), style = MaterialTheme.typography.labelLarge)
                            }
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
                    Box(contentAlignment = Alignment.CenterStart) {
                        FilterChip(
                            selected = false,
                            onClick = {},
                            enabled = false,
                            shape = RoundedCornerShape(50),
                            label = {
                                Text(
                                    text = stringResource(R.string.filter_unchecked_only),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.FilterList,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            modifier = Modifier.alpha(0f)
                        )
                        Box(
                            modifier = Modifier
                                .height(32.dp)
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
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.alpha(0f)
                            ) {
                                Spacer(modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.filter_unchecked_only),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        repeat(2) {
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

        // Blocks 4..7: 清點項目膠囊卡片骨架（1:1 對齊真實清單筆數、LazyColumn 內距與 CapsuleCheckItemRow 尺寸）
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
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .width(80.dp)
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
                        Box(
                            modifier = Modifier.size(34.dp),
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
