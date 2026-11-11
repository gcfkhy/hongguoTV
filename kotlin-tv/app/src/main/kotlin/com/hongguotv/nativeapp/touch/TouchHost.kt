// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp.touch

import android.app.Activity
import android.view.View
import com.hongguotv.core.ContentRepository
import com.hongguotv.core.Series
import com.hongguotv.nativeapp.AccountController
import com.hongguotv.nativeapp.ArtworkLoader
import com.hongguotv.nativeapp.Library

/** Narrow seam between the touch screens and the activity that owns state and playback. */
internal interface TouchHost {
    val activity: Activity
    val library: Library
    val account: AccountController
    val repository: ContentRepository
    val artwork: ArtworkLoader
    val sheet: TouchSheet

    fun <T> work(action: () -> T, done: (T) -> Unit, failed: (Throwable) -> Unit)

    fun openDetail(series: Series, resume: Boolean = false)

    /** Toggles the local favorite and reports whether the series is now collected. */
    fun toggleLocalFavorite(series: Series): Boolean

    fun accountFavorite(series: Series, anchor: View)
}
