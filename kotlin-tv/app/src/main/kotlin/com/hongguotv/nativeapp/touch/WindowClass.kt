// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp.touch

import android.content.Context

/** Window width classes for the touch UI; television is decided by [DeviceUi.television] first. */
internal enum class WindowClass {
    Compact,
    Medium,
    Expanded;

    val usesRail: Boolean get() = this != Compact

    companion object {
        fun of(widthDp: Int): WindowClass =
            when {
                widthDp < 600 -> Compact
                widthDp <= 840 -> Medium
                else -> Expanded
            }

        fun of(context: Context): WindowClass =
            of(context.resources.configuration.screenWidthDp)

        fun gridColumns(widthDp: Int): Int =
            when {
                widthDp < 600 -> 3
                widthDp <= 840 -> 4
                widthDp < 1200 -> 5
                else -> 6
            }
    }
}
