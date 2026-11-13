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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.hypot
import kotlin.math.roundToInt

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
    isDarkMode: Boolean,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (triggerTransition: (originInRoot: Offset) -> Unit, isTransitioning: Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()
    val graphicsLayer = rememberGraphicsLayer()
    val isReducedMotion = rememberReducedMotion()
    val currentDarkMode by rememberUpdatedState(isDarkMode)

    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var snapshotBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var revealOrigin by remember { mutableStateOf(Offset.Zero) }
    var isTransitioning by remember { mutableStateOf(false) }
    val revealRadius = remember { Animatable(0f) }
    val clipPath = remember { Path() }

    val triggerTransition: (Offset) -> Unit = { origin ->
        if (!isTransitioning) {
            if (isReducedMotion || containerSize.width == 0 || containerSize.height == 0) {
                onToggleTheme()
            } else {
                isTransitioning = true
                scope.launch {
                    try {
                        // Step 1: 記錄切換前狀態、擷取離屏快照並初始化遮罩
                        val previousDarkMode = currentDarkMode
                        val captured = graphicsLayer.toImageBitmap()
                        val w = containerSize.width.toFloat()
                        val h = containerSize.height.toFloat()
                        val maxRadius = maxOf(
                            hypot(origin.x, origin.y),
                            hypot(w - origin.x, origin.y),
                            hypot(origin.x, h - origin.y),
                            hypot(w - origin.x, h - origin.y)
                        )

                        revealOrigin = origin
                        revealRadius.snapTo(0f)
                        snapshotBitmap = captured

                        // Step 2: 確保快照覆蓋層已繪製上屏遮蔽全畫面
                        withFrameNanos { }

                        // Step 3: 觸發 DataStore 異步主題切換
                        onToggleTheme()

                        // Step 4: 等待 DataStore 發射新主題狀態至 Compose
                        withTimeoutOrNull(300L) {
                            snapshotFlow { currentDarkMode }.first { it != previousDarkMode }
                        }

                        // Step 5: 連續等待兩幀，完成新主題全樹重組/Layout 與 GPU 首幀合成
                        withFrameNanos { }
                        withFrameNanos { }

                        // Step 6: 啟動 620ms 柔和圓形擴散動畫
                        revealRadius.animateTo(
                            targetValue = maxRadius,
                            animationSpec = tween(
                                durationMillis = 620,
                                easing = CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f)
                            )
                        )
                    } finally {
                        snapshotBitmap = null
                        isTransitioning = false
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
        // 單一活體內容層：錄製每幀畫面供切換瞬間擷取離屏快照，並在轉場期間凍結內部色彩漸變
        CompositionLocalProvider(
            LocalThemeTransitioning provides isTransitioning,
            LocalReducedMotion provides isReducedMotion
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        graphicsLayer.record {
                            this@drawWithContent.drawContent()
                        }
                        drawLayer(graphicsLayer)
                    }
            ) {
                content(triggerTransition, isTransitioning)
            }
        }

        // 舊主題快照覆蓋層：於 Draw 階段讀取 revealRadius 並複用單一 Path，避免每幀 GC 與重組
        snapshotBitmap?.let { bitmap ->
            Spacer(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        clipPath.rewind()
                        clipPath.addOval(
                            Rect(center = revealOrigin, radius = revealRadius.value)
                        )
                        clipPath(path = clipPath, clipOp = ClipOp.Difference) {
                            drawImage(
                                image = bitmap,
                                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt())
                            )
                        }
                    }
            )
        }
    }
}
