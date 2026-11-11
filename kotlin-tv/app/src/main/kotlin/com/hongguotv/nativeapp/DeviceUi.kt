// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.View

/** Phone/tablet use touch video UI; television keeps D-pad focus-first. */
object DeviceUi {
    enum class Form {
        Phone,
        Tablet,
        Television,
    }

    fun television(context: Context): Boolean {
        val mode = context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
        return mode == Configuration.UI_MODE_TYPE_TELEVISION
    }

    fun touchFirst(context: Context) = !television(context)

    fun form(context: Context): Form {
        if (television(context)) return Form.Television
        return if (context.resources.configuration.smallestScreenWidthDp >= 600) Form.Tablet
        else Form.Phone
    }

    fun phone(context: Context) = form(context) == Form.Phone

    fun tablet(context: Context) = form(context) == Form.Tablet

    fun preferredOrientation(context: Context): Int =
        when (form(context)) {
            Form.Phone -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            Form.Tablet,
            Form.Television -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }

    fun catalogColumns(context: Context): Int {
        val width = context.resources.configuration.screenWidthDp
        return when {
            width < 600 -> 3
            width < 900 -> 4
            else -> 6
        }
    }
}

fun View.enableRemoteFocus() {
    isFocusable = true
    isFocusableInTouchMode = DeviceUi.television(context)
}
