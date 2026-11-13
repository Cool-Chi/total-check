package com.example.totalcheck.ui.theme

import android.animation.ValueAnimator
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.roundToInt

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
    val graphicsLayer = rememberGraphicsLayer()
    val isReducedMotion = rememberReducedMotion()

    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var snapshotBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var revealOrigin by remember { mutableStateOf(Offset.Zero) }
    var isTransitioning by remember { mutableStateOf(false) }
    val revealRadius = remember { Animatable(0f) }

    val triggerTransition: (Offset) -> Unit = { origin ->
        if (!isTransitioning) {
            if (isReducedMotion || containerSize.width == 0 || containerSize.height == 0) {
                onToggleTheme()
            } else {
                isTransitioning = true
                scope.launch {
                    try {
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
                        onToggleTheme()

                        revealRadius.animateTo(
                            targetValue = maxRadius,
                            animationSpec = tween(
                                durationMillis = 420,
                                easing = FastOutSlowInEasing
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
        // 單一活體內容層：錄製每幀畫面供切換瞬間擷取離屏快照
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

        // 舊主題快照覆蓋層：以 ClipOp.Difference 從觸控圓心向外挖空揭示底層新主題
        snapshotBitmap?.let { bitmap ->
            Canvas(modifier = Modifier.fillMaxSize()) {
                val circlePath = Path().apply {
                    addOval(Rect(center = revealOrigin, radius = revealRadius.value))
                }
                clipPath(path = circlePath, clipOp = ClipOp.Difference) {
                    drawImage(
                        image = bitmap,
                        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt())
                    )
                }
            }
        }
    }
}
