// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp

import android.content.Context
import android.content.res.Configuration
import android.view.View

/** TV keeps D-pad focus-first; phones, tablets and emulators click on the first tap. */
object DeviceUi {
    fun television(context: Context): Boolean {
        val mode = context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
        return mode == Configuration.UI_MODE_TYPE_TELEVISION
    }

    fun touchFirst(context: Context) = !television(context)

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
