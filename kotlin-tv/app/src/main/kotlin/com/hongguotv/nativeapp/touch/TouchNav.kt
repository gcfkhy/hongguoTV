// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp.touch

import android.view.View

/** Top-level destinations of the touch UI. */
internal enum class TouchTab {
    Home,
    Discover,
    Library,
    Me;

    companion object {
        /** Maps a legacy television tab index to a touch tab and its segment. */
        fun fromLegacyIndex(index: Int): Pair<TouchTab, Int> =
            when (index) {
                0 -> Home to 0
                1 -> Discover to 0
                2 -> Discover to 1
                3 -> Library to 0
                4 -> Library to 1
                5 -> Library to 2
                6 -> Me to 0
                else -> Home to 0
            }
    }
}

internal enum class BackResult {
    Handled,
    Exit,
}

/** Remembers the selected top-level tab and implements the back rules. */
internal class TabNavigator(initial: TouchTab = TouchTab.Home) {
    var current: TouchTab = initial
        private set

    fun select(tab: TouchTab): Boolean {
        if (tab == current) return false
        current = tab
        return true
    }

    fun back(): BackResult {
        if (current == TouchTab.Home) return BackResult.Exit
        current = TouchTab.Home
        return BackResult.Handled
    }
}

/** A screen hosted by [TouchShell]. */
internal interface TouchPage {
    val view: View

    fun onShow()

    fun onHide()

    /** Consumes the back press inside the page; true means handled. */
    fun onBack(): Boolean

    fun close()
}
