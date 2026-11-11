package com.hongguotv.nativeapp.touch

import android.app.Activity
import android.view.View
import com.hongguotv.core.BackupData
import com.hongguotv.core.BackupSettings
import com.hongguotv.core.ContentRepository
import com.hongguotv.core.LibraryIndex
import com.hongguotv.core.Series
import com.hongguotv.nativeapp.AccountController
import com.hongguotv.nativeapp.ArtworkLoader
import com.hongguotv.nativeapp.Library

/** Shared fake for touch page tests: runs work inline and records outward commands. */
internal class FakeTouchHost(override val activity: Activity) : TouchHost {
    override val library = Library(activity)
    override val account = AccountController(activity) {}
    override val repository = ContentRepository()
    override val artwork = ArtworkLoader(activity)
    override val sheet = TouchSheet(activity)

    val opened = mutableListOf<Pair<Series, Boolean>>()
    val accountFavoriteCalls = mutableListOf<Series>()
    var favoriteState = false
    var workDelay: (() -> Unit)? = null
    var workFailure: Throwable? = null

    init {
        Library::class
            .java
            .getDeclaredField("index")
            .apply { isAccessible = true }
            .set(
                library,
                LibraryIndex(
                    BackupData(emptyList(), emptyList(), emptySet(), emptyList(), BackupSettings()),
                ),
            )
    }

    override fun <T> work(action: () -> T, done: (T) -> Unit, failed: (Throwable) -> Unit) {
        workDelay?.invoke()
        val failure = workFailure
        if (failure != null) {
            failed(failure)
            return
        }
        try {
            done(action())
        } catch (problem: Throwable) {
            failed(problem)
        }
    }

    override fun openDetail(series: Series, resume: Boolean) {
        opened += series to resume
    }

    override fun toggleLocalFavorite(series: Series): Boolean {
        favoriteState = !favoriteState
        return favoriteState
    }

    override fun accountFavorite(series: Series, anchor: View) {
        accountFavoriteCalls += series
    }

    fun destroy() {
        sheet.dismiss()
        account.destroy()
        artwork.close()
        library.close()
    }
}
