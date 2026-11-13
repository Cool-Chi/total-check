package com.example.totalcheck.ui.theme

import android.animation.ValueAnimator
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.launch
import kotlin.math.hypot

val LocalThemeTransitioning = compositionLocalOf { false }
val LocalReducedMotion = compositionLocalOf { false }

@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current

    fun checkReducedMotion(): Boolean {
        val animatorsEnabled = ValueAnimator.areAnimatorsEnabled()
        val scale = runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f
            )
        }.getOrDefault(1.0f)
        return !animatorsEnabled || scale == 0f
    }

    var reducedMotion by remember { mutableStateOf(checkReducedMotion()) }

    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reducedMotion = checkReducedMotion()
            }
        }
        val uri = Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE)
        context.contentResolver.registerContentObserver(uri, false, observer)
        onDispose {
            context.contentResolver.unregisterContentObserver(observer)
        }
    }

    return reducedMotion
}

@Composable
fun RadialThemeTransitionContainer(
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (triggerTransition: (originInRoot: Offset) -> Unit, isTransitioning: Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()
    val contentLayer = rememberGraphicsLayer()
    val snapshotLayer = rememberGraphicsLayer()
    val isReducedMotion = rememberReducedMotion()

    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var showSnapshotOverlay by remember { mutableStateOf(false) }
    var freezeSnapshot by remember { mutableStateOf(false) }
    var revealOrigin by remember { mutableStateOf(Offset.Zero) }
    var isTransitioning by remember { mutableStateOf(false) }
    val revealRadius = remember { Animatable(0f) }
    val clipPath = remember { Path() }

    val triggerTransition: (Offset) -> Unit = { origin ->
        if (!isTransitioning) {
            if (isReducedMotion || containerSize.width == 0 || containerSize.height == 0) {
                onToggleTheme()
            } else {
                // Step 1: 0ms 凍結 GPU snapshotLayer 並顯示覆蓋層
                freezeSnapshot = true
                isTransitioning = true
                showSnapshotOverlay = true
                revealOrigin = origin

                val w = containerSize.width.toFloat()
                val h = containerSize.height.toFloat()
                val maxRadius = maxOf(
                    hypot(origin.x, origin.y),
                    hypot(w - origin.x, origin.y),
                    hypot(origin.x, h - origin.y),
                    hypot(w - origin.x, h - origin.y)
                )

                // Step 2: 同毫秒立即觸發 0ms 樂觀主題切換
                onToggleTheme()

                // Step 3: 立即啟動 560ms 高初速度圓形擴散動畫
                scope.launch {
                    try {
                        revealRadius.snapTo(0f)
                        revealRadius.animateTo(
                            targetValue = maxRadius,
                            animationSpec = tween(
                                durationMillis = 560,
                                easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
                            )
                        )
                    } finally {
                        showSnapshotOverlay = false
                        isTransitioning = false
                        freezeSnapshot = false
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
    ) {
        // 單一活體內容層：持續錄製至 contentLayer，非轉場時零拷貝鏡像至 snapshotLayer
        CompositionLocalProvider(
            LocalThemeTransitioning provides isTransitioning,
            LocalReducedMotion provides isReducedMotion
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        contentLayer.record {
                            this@drawWithContent.drawContent()
                        }
                        if (!isTransitioning && !freezeSnapshot) {
                            snapshotLayer.record {
                                drawLayer(contentLayer)
                            }
                        }
                        drawLayer(contentLayer)
                    }
            ) {
                content(triggerTransition, isTransitioning)
            }
        }

        // 舊主題 GPU 快照覆蓋層：於 Draw 階段直接 drawLayer(snapshotLayer) 並以 ClipOp.Difference 挖空
        if (showSnapshotOverlay) {
            Spacer(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        clipPath.rewind()
                        clipPath.addOval(
                            Rect(center = revealOrigin, radius = revealRadius.value)
                        )
                        clipPath(path = clipPath, clipOp = ClipOp.Difference) {
                            drawLayer(snapshotLayer)
                        }
                    }
            )
        }
    }
}
