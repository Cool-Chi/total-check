package com.example.totalcheck.ui.main

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Hardware-backed tactile haptics helper for differentiated mechanical feel:
 * - List cross-item scrolling uses a distinctly deeper, heavier low-frequency detent
 *   than the crisp gear ticks of the clock drum wheel.
 * - List top/bottom overscroll boundary engagement uses a fuller weighted thud.
 *
 * Pre-allocates [VibrationEffect] instances once so scroll frames allocate 0 objects.
 */
@Stable
internal class TactileHaptics(context: Context) {
    private val appContext: Context = context.applicationContext

    private val vibrator: Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }.getOrNull()

    private val hasVibrator: Boolean = vibrator?.hasVibrator() == true

    private val listScrollDetentEffect: VibrationEffect? = buildListScrollDetentEffect()
    private val listBoundaryThudEffect: VibrationEffect? = buildListBoundaryThudEffect()

    private var lastScrollDetentUptimeMs: Long = 0L
    private var lastBoundaryThudUptimeMs: Long = 0L

    private fun buildListScrollDetentEffect(): VibrationEffect? {
        val vib = vibrator ?: return null
        if (!hasVibrator) return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val supportsLowTick = runCatching {
                vib.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_LOW_TICK)
            }.getOrDefault(false)
            if (supportsLowTick) {
                return VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.85f)
                    .compose()
            }
        }
        return VibrationEffect.createOneShot(18L, 115)
    }

    private fun buildListBoundaryThudEffect(): VibrationEffect? {
        val vib = vibrator ?: return null
        if (!hasVibrator) return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val supportsThud = runCatching {
                vib.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_THUD)
            }.getOrDefault(false)
            if (supportsThud) {
                return VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.90f)
                    .compose()
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val supportsLowTick = runCatching {
                vib.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_LOW_TICK)
            }.getOrDefault(false)
            if (supportsLowTick) {
                return VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 1.0f)
                    .compose()
            }
        }
        return VibrationEffect.createOneShot(26L, 165)
    }

    private fun isSystemHapticEnabled(): Boolean {
        return runCatching {
            @Suppress("DEPRECATION")
            Settings.System.getInt(
                appContext.contentResolver,
                Settings.System.HAPTIC_FEEDBACK_ENABLED,
                1
            ) != 0
        }.getOrDefault(true)
    }

    fun performListScrollDetent() {
        val vib = vibrator ?: return
        val effect = listScrollDetentEffect ?: return
        val nowMs = SystemClock.uptimeMillis()
        if (nowMs - lastScrollDetentUptimeMs < SCROLL_DETENT_DEBOUNCE_MS) return
        if (!isSystemHapticEnabled()) return
        lastScrollDetentUptimeMs = nowMs
        runCatching { vib.vibrate(effect) }
    }

    fun performListBoundaryThud() {
        val vib = vibrator ?: return
        val effect = listBoundaryThudEffect ?: return
        val nowMs = SystemClock.uptimeMillis()
        if (nowMs - lastBoundaryThudUptimeMs < BOUNDARY_THUD_DEBOUNCE_MS) return
        if (!isSystemHapticEnabled()) return
        lastBoundaryThudUptimeMs = nowMs
        runCatching { vib.vibrate(effect) }
    }

    companion object {
        private const val SCROLL_DETENT_DEBOUNCE_MS = 32L
        private const val BOUNDARY_THUD_DEBOUNCE_MS = 140L
    }
}

@Composable
internal fun rememberTactileHaptics(): TactileHaptics {
    val context = LocalContext.current
    return remember(context) { TactileHaptics(context) }
}
