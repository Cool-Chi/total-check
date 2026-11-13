package com.example.totalcheck.ui.theme

import android.animation.ValueAnimator
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

val LocalThemeTransitioning = compositionLocalOf { false }
val LocalReducedMotion = compositionLocalOf { false }

private const val SNAPSHOT_SCALE = 0.6f

private class RevealWave(
    val id: Int,
    val origin: Offset,
    val maxRadius: Float,
    val radiusAnim: Animatable<Float, AnimationVector1D>,
    var frozenBitmap: ImageBitmap? = null,
    var job: Job? = null
)

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

@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun RadialThemeTransitionContainer(
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier,
    durationMillis: Int = 400,
    fromClick: Boolean = true,
    fade: Boolean = true,
    content: @Composable (
        triggerTransition: (originInRoot: Offset) -> Unit,
        onPreCapture: () -> Unit,
        onCancelPreCapture: () -> Unit,
        isTransitioning: Boolean
    ) -> Unit
) {
    val scope = rememberCoroutineScope()
    val graphicsContext = LocalGraphicsContext.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val isReducedMotion = rememberReducedMotion()

    val currentOnToggleTheme by rememberUpdatedState(onToggleTheme)
    val currentReducedMotion by rememberUpdatedState(isReducedMotion)
    val currentDurationMillis by rememberUpdatedState(durationMillis)
    val currentFromClick by rememberUpdatedState(fromClick)
    val currentFade by rememberUpdatedState(fade)

    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val liveLayer = remember(graphicsContext) {
        graphicsContext.createGraphicsLayer()
    }
    val snapshotLayer = remember(graphicsContext) {
        graphicsContext.createGraphicsLayer()
    }
    var hasRecordedLiveFrame by remember { mutableStateOf(false) }
    var committedThemeEpoch by remember { mutableIntStateOf(0) }
    var drawnThemeEpoch by remember { mutableIntStateOf(0) }

    var baseSnapshotBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    val activeWaves = remember { mutableStateListOf<RevealWave>() }
    var preCaptureJob by remember { mutableStateOf<Deferred<ImageBitmap>?>(null) }
    var preCaptureEpoch by remember { mutableIntStateOf(-1) }
    val triggerMutex = remember { Mutex() }
    var sequenceGuard by remember { mutableIntStateOf(0) }
    val fallbackAlpha = remember { Animatable(1f) }
    val clipPath = remember { Path() }

    val isTransitioning = activeWaves.isNotEmpty() || baseSnapshotBitmap != null

    fun clearAllWavesAndSnapshots() {
        sequenceGuard++
        preCaptureJob?.cancel()
        preCaptureJob = null
        activeWaves.forEach { wave ->
            wave.job?.cancel()
            wave.job = null
            wave.frozenBitmap = null
        }
        activeWaves.clear()
        baseSnapshotBitmap = null
    }

    fun recordScaledSnapshot(): Boolean {
        val w = containerSize.width
        val h = containerSize.height
        if (w <= 0 || h <= 0 || !hasRecordedLiveFrame) return false
        val snapW = (w * SNAPSHOT_SCALE).roundToInt().coerceAtLeast(1)
        val snapH = (h * SNAPSHOT_SCALE).roundToInt().coerceAtLeast(1)
        snapshotLayer.record(
            density = density,
            layoutDirection = layoutDirection,
            size = IntSize(snapW, snapH)
        ) {
            scale(
                scaleX = SNAPSHOT_SCALE,
                scaleY = SNAPSHOT_SCALE,
                pivot = Offset.Zero
            ) {
                drawLayer(liveLayer)
            }
        }
        return true
    }

    DisposableEffect(graphicsContext) {
        onDispose {
            clearAllWavesAndSnapshots()
            runCatching { graphicsContext.releaseGraphicsLayer(snapshotLayer) }
            runCatching { graphicsContext.releaseGraphicsLayer(liveLayer) }
        }
    }

    val onPreCapture: () -> Unit = {
        if (!currentReducedMotion &&
            !triggerMutex.isLocked &&
            containerSize.width > 0 &&
            containerSize.height > 0 &&
            hasRecordedLiveFrame &&
            drawnThemeEpoch == committedThemeEpoch
        ) {
            val existing = preCaptureJob
            if (existing == null || existing.isCancelled || preCaptureEpoch != drawnThemeEpoch) {
                existing?.cancel()
                if (recordScaledSnapshot()) {
                    preCaptureEpoch = drawnThemeEpoch
                    preCaptureJob = scope.async { snapshotLayer.toImageBitmap() }
                }
            }
        }
    }

    val onCancelPreCapture: () -> Unit = {
        preCaptureJob?.cancel()
        preCaptureJob = null
    }

    fun startWaveWithSnapshot(
        capturedBitmap: ImageBitmap,
        clickOrigin: Offset
    ) {
        val w = containerSize.width.toFloat()
        val h = containerSize.height.toFloat()
        if (w <= 0f || h <= 0f) {
            committedThemeEpoch++
            currentOnToggleTheme()
            return
        }

        val resolvedOrigin = if (currentFromClick && clickOrigin.isSpecified &&
            clickOrigin.x in 0f..w && clickOrigin.y in 0f..h
        ) {
            clickOrigin
        } else {
            Offset(w / 2f, h / 2f)
        }

        // R = hypot(max(X, W - X), max(Y, H - Y))
        val maxRadius = hypot(
            max(resolvedOrigin.x, w - resolvedOrigin.x),
            max(resolvedOrigin.y, h - resolvedOrigin.y)
        )
        val resolvedDuration = currentDurationMillis.coerceIn(200, 700)

        val currentTopWave = activeWaves.lastOrNull()
        if (currentTopWave != null && currentTopWave.frozenBitmap == null) {
            currentTopWave.frozenBitmap = capturedBitmap
        } else if (baseSnapshotBitmap == null) {
            baseSnapshotBitmap = capturedBitmap
        }

        val waveId = ++sequenceGuard
        val newWave = RevealWave(
            id = waveId,
            origin = resolvedOrigin,
            maxRadius = maxRadius,
            radiusAnim = Animatable(0f)
        )
        activeWaves.add(newWave)

        // Commit the new theme synchronously right after locking the old snapshot
        committedThemeEpoch++
        currentOnToggleTheme()

        newWave.job = scope.launch {
            try {
                // Yield across Frame 1's animation callback so Frame 1's recomposition/traversal
                // completes BEFORE animateTo locks in startTimeNanos on Frame 2, ensuring the
                // full 400ms duration is visibly rendered from radius 0 -> maxRadius.
                withFrameNanos { }
                newWave.radiusAnim.animateTo(
                    targetValue = maxRadius,
                    animationSpec = tween(
                        durationMillis = resolvedDuration,
                        easing = EaseOut
                    )
                )
                // Once this wave reaches maxRadius, it covers 100% of the viewport
                val idx = activeWaves.indexOf(newWave)
                if (idx >= 0) {
                    for (i in 0 until idx) {
                        val older = activeWaves[i]
                        older.job?.cancel()
                        older.job = null
                        older.frozenBitmap = null
                    }
                    if (idx > 0) {
                        activeWaves.removeRange(0, idx)
                    }
                    activeWaves.remove(newWave)

                    val promotedBitmap = newWave.frozenBitmap
                    newWave.frozenBitmap = null
                    if (activeWaves.isEmpty()) {
                        baseSnapshotBitmap = null
                    } else if (promotedBitmap != null) {
                        baseSnapshotBitmap = promotedBitmap
                    }
                } else {
                    newWave.frozenBitmap = null
                }
            } catch (_: Throwable) {
                newWave.frozenBitmap = null
            }
        }
    }

    val triggerTransition: (Offset) -> Unit = { clickOrigin ->
        val w = containerSize.width.toFloat()
        val h = containerSize.height.toFloat()

        if (currentReducedMotion) {
            clearAllWavesAndSnapshots()
            scope.launch { fallbackAlpha.snapTo(1f) }
            committedThemeEpoch++
            currentOnToggleTheme()
        } else if (w <= 0f || h <= 0f || !hasRecordedLiveFrame) {
            clearAllWavesAndSnapshots()
            committedThemeEpoch++
            currentOnToggleTheme()
            if (currentFade) {
                scope.launch {
                    fallbackAlpha.snapTo(0.55f)
                    fallbackAlpha.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 120, easing = EaseOut)
                    )
                }
            }
        } else {
            val activePreCapture = preCaptureJob
            val capturedEpoch = preCaptureEpoch
            preCaptureJob = null

            val fastBitmap = if (!triggerMutex.isLocked &&
                activePreCapture != null &&
                activePreCapture.isCompleted &&
                !activePreCapture.isCancelled &&
                capturedEpoch == committedThemeEpoch &&
                drawnThemeEpoch == committedThemeEpoch
            ) {
                runCatching { activePreCapture.getCompleted() }.getOrNull()
            } else {
                null
            }

            if (fastBitmap != null) {
                startWaveWithSnapshot(fastBitmap, clickOrigin)
            } else {
                scope.launch {
                    triggerMutex.withLock {
                        val preCaptured = if (activePreCapture != null &&
                            !activePreCapture.isCancelled &&
                            capturedEpoch == committedThemeEpoch
                        ) {
                            runCatching { activePreCapture.await() }.getOrNull()
                        } else {
                            activePreCapture?.cancel()
                            null
                        }
                        val bitmap = preCaptured ?: run {
                            recordScaledSnapshot()
                            snapshotLayer.toImageBitmap()
                        }
                        startWaveWithSnapshot(bitmap, clickOrigin)
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
        CompositionLocalProvider(
            LocalThemeTransitioning provides isTransitioning,
            LocalReducedMotion provides isReducedMotion
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = fallbackAlpha.value
                    }
                    .drawWithContent {
                        liveLayer.record {
                            this@drawWithContent.drawContent()
                        }
                        hasRecordedLiveFrame = true
                        drawnThemeEpoch = committedThemeEpoch
                        if (!isTransitioning) {
                            drawLayer(liveLayer)
                        }
                    }
            ) {
                content(triggerTransition, onPreCapture, onCancelPreCapture, isTransitioning)
            }
        }

        val baseBitmap = baseSnapshotBitmap
        if (baseBitmap != null && activeWaves.isNotEmpty()) {
            Spacer(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt())

                        drawImage(
                            image = baseBitmap,
                            dstSize = dstSize,
                            filterQuality = FilterQuality.Low
                        )

                        for (i in 0 until activeWaves.size) {
                            val wave = activeWaves[i]
                            val radius = wave.radiusAnim.value
                            if (radius > 0f) {
                                clipPath.rewind()
                                clipPath.addOval(
                                    Rect(center = wave.origin, radius = radius)
                                )
                                clipPath(path = clipPath, clipOp = ClipOp.Intersect) {
                                    val frozenBitmap = wave.frozenBitmap
                                    if (frozenBitmap != null) {
                                        drawImage(
                                            image = frozenBitmap,
                                            dstSize = dstSize,
                                            filterQuality = FilterQuality.Low
                                        )
                                    } else {
                                        drawLayer(liveLayer)
                                    }
                                }
                            }
                        }
                    }
            )
        }
    }
}
