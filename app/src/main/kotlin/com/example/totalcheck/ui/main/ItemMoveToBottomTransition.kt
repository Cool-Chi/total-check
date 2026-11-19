package com.example.totalcheck.ui.main

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 項目經由右側拖曳手把左滑超過門檻後「移至列表底部」的視覺動畫狀態容器。
 * 保留 translationX / translationY / scale / alpha，供後續套用專業過渡動畫 Skill 時直接驅動。
 */
@Stable
internal class MoveToBottomVisualState {
    val translationX = Animatable(0f)
    val translationY = Animatable(0f)
    val scale = Animatable(1f)
    val alpha = Animatable(1f)
    var isArmed by mutableStateOf(false)
    var isMovingToBottom by mutableStateOf(false)

    suspend fun snapReset() {
        isArmed = false
        isMovingToBottom = false
        translationX.snapTo(0f)
        translationY.snapTo(0f)
        scale.snapTo(1f)
        alpha.snapTo(1f)
    }
}

/**
 * 可替換的「項目移至底部」過渡動畫接口。
 * 將手勢位移閾值偵測、資料順序提交與視覺過渡解耦，便於後續導入進階物理或編排動效。
 */
internal interface ItemMoveToBottomTransitionSpec {
    suspend fun onHorizontalPull(
        visualState: MoveToBottomVisualState,
        effectivePullDxPx: Float,
        thresholdPx: Float,
        isArmed: Boolean
    )

    suspend fun onCancelPull(
        visualState: MoveToBottomVisualState,
        isReducedMotion: Boolean
    )

    suspend fun onCommitMoveToBottom(
        visualState: MoveToBottomVisualState,
        springTracker: MagneticSpringTracker,
        resolveTargetBottomTopY: () -> Float?,
        isReducedMotion: Boolean,
        onCommitOrder: () -> Unit
    )
}

internal object DefaultItemMoveToBottomTransitionSpec : ItemMoveToBottomTransitionSpec {

    override suspend fun onHorizontalPull(
        visualState: MoveToBottomVisualState,
        effectivePullDxPx: Float,
        thresholdPx: Float,
        isArmed: Boolean
    ) {
        visualState.isArmed = isArmed
        val clampedX = effectivePullDxPx.coerceIn(-thresholdPx * 1.45f, 0f)
        visualState.translationX.snapTo(clampedX)
    }

    override suspend fun onCancelPull(
        visualState: MoveToBottomVisualState,
        isReducedMotion: Boolean
    ) {
        visualState.isArmed = false
        if (isReducedMotion) {
            visualState.translationX.snapTo(0f)
            return
        }
        if (kotlin.math.abs(visualState.translationX.value) > 0.5f) {
            visualState.translationX.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = 0.84f,
                    stiffness = 560f,
                    visibilityThreshold = 0.5f
                )
            )
        } else {
            visualState.translationX.snapTo(0f)
        }
    }

    override suspend fun onCommitMoveToBottom(
        visualState: MoveToBottomVisualState,
        springTracker: MagneticSpringTracker,
        resolveTargetBottomTopY: () -> Float?,
        isReducedMotion: Boolean,
        onCommitOrder: () -> Unit
    ) {
        visualState.isMovingToBottom = true
        visualState.isArmed = false
        onCommitOrder()

        // 等待一幀讓清單佈局完成底部空槽重排，再讀取目標座標
        val firstFrameNanos = withFrameNanos { it }
        val targetY = resolveTargetBottomTopY() ?: springTracker.currentValueState.floatValue

        if (isReducedMotion) {
            visualState.translationX.snapTo(0f)
            springTracker.snapTo(targetY)
            visualState.snapReset()
            return
        }

        coroutineScope {
            val horizJob = launch {
                visualState.translationX.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = 0.85f,
                        stiffness = 520f,
                        visibilityThreshold = 0.5f
                    )
                )
            }
            val vertJob = launch {
                springTracker.setTarget(targetY)
                var settled = springTracker.stepFrame(firstFrameNanos)
                val startNanos = firstFrameNanos
                while (isActive && !settled) {
                    withFrameNanos { frameNanos ->
                        val elapsedNanos = frameNanos - startNanos
                        settled = springTracker.stepFrame(frameNanos) ||
                            elapsedNanos >= 420_000_000L
                    }
                }
                springTracker.snapTo(targetY)
            }
            horizJob.join()
            vertJob.join()
        }
        visualState.snapReset()
    }
}
