// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp.touch

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import com.hongguotv.nativeapp.TvStyle

/** Size, color and surface tokens shared by the touch screens. */
internal object TouchTheme {
    const val MIN_TARGET_DP = 48
    const val CARD_RADIUS_DP = 12
    const val SHEET_RADIUS_DP = 20
    const val TEXT_BODY = 15f
    const val TEXT_SECONDARY = 13f
    const val TEXT_MIN = 12f

    val background = TvStyle.background
    val surface = TvStyle.surface
    val raised = TvStyle.raised
    val accent = TvStyle.accent
    val text = TvStyle.text
    val muted = TvStyle.muted
    val outline = TvStyle.outline
    val scrim = Color.argb(170, 0, 0, 0)

    fun dp(context: Context, value: Number): Int =
        (value.toFloat() * context.resources.displayMetrics.density + .5f).toInt()

    /** Horizontal page margin in pixels: 16dp on compact windows, 24dp otherwise. */
    fun pageMargin(windowClass: WindowClass): Int {
        val density = Resources.getSystem().displayMetrics.density
        return ((if (windowClass == WindowClass.Compact) 16f else 24f) * density + .5f).toInt()
    }

    /** Ripple surface used by every touch target instead of focus highlighting. */
    fun pressable(context: Context, fill: Int, radiusDp: Int = CARD_RADIUS_DP): Drawable {
        val content =
            GradientDrawable().apply {
                setColor(fill)
                cornerRadius = dp(context, radiusDp).toFloat()
            }
        return RippleDrawable(ColorStateList.valueOf(Color.argb(70, 255, 255, 255)), content, null)
    }
}
