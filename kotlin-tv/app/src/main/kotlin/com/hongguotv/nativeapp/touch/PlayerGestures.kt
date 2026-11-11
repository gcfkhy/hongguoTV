// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp.touch

import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Pure math behind the touch player gestures. */
internal object PlayerGestures {
    const val AUTO_HIDE_MS = 3000L
    const val LONG_PRESS_SPEED = 2f

    fun isHorizontalDrag(dx: Float, dy: Float, slopPx: Float): Boolean =
        abs(dx) > slopPx && abs(dx) > 2 * abs(dy)

    /** A full-width drag maps to at most two minutes of playback. */
    fun seekTarget(startMs: Long, durationMs: Long, dxPx: Float, widthPx: Int): Long {
        if (durationMs <= 0 || widthPx <= 0) return startMs
        val span = min(durationMs, 120_000L)
        val delta = (dxPx / widthPx) * span
        val upper = max(0L, durationMs - 500L)
        return (startMs + delta.toLong()).coerceIn(0L, upper)
    }

    fun bubble(targetMs: Long, durationMs: Long): String =
        "${stamp(targetMs, durationMs)} / ${stamp(durationMs, durationMs)}"

    private fun stamp(valueMs: Long, durationMs: Long): String {
        val total = max(0L, valueMs) / 1000
        val hours = total / 3600
        val minutes = (total % 3600) / 60
        val seconds = total % 60
        return if (durationMs >= 3_600_000L) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }
}
