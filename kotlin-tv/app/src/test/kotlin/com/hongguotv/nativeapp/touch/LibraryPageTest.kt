package com.hongguotv.nativeapp.touch

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import com.hongguotv.core.Series
import com.hongguotv.nativeapp.WatchProgress
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26], qualifiers = "w411dp-h891dp-port")
@LooperMode(LooperMode.Mode.PAUSED)
class LibraryPageTest {
    private lateinit var controller: ActivityController<Activity>
    private lateinit var host: FakeTouchHost

    @Before
    fun setUp() {
        controller = Robolectric.buildActivity(Activity::class.java).setup()
        host = FakeTouchHost(controller.get())
    }

    @After
    fun tearDown() {
        host.destroy()
        controller.destroy()
    }

    private fun views(root: View): List<View> =
        when (root) {
            is ViewGroup -> (0 until root.childCount).flatMap { views(root.getChildAt(it)) } + root
            else -> listOf(root)
        }

    private fun texts(root: View): List<TextView> = views(root).filterIsInstance<TextView>()

    private fun progress(series: Series, index: Int, position: Long, duration: Long, at: Long) =
        WatchProgress(series, "ep$index", index, position, duration, completed = false, updatedAt = at)

    @Test
    fun emptyLocalFavoritesOfferDiscover() {
        var discovered = 0
        val page = LibraryPage(host, 0) { discovered++ }
        page.onShow()
        assertTrue(texts(page.view).any { it.text.toString() == "还没有收藏" })
        texts(page.view).single { it.text.toString() == "去找剧" }.performClick()
        assertEquals(1, discovered)
    }

    @Test
    fun historyCardShowsEpisodeAndProgress() {
        host.library.save(progress(Series("1", "历史剧"), 2, 30, 100, 1))
        val page = LibraryPage(host, 1)
        page.onShow()
        val card = page.historyGrid.adapter.getView(0, null, page.historyGrid)
        assertTrue(texts(card).any { it.text.toString() == "第 3 集" })
        val bar = views(card).filterIsInstance<ProgressBar>().single()
        assertEquals(300, bar.progress)
    }

    @Test
    fun historyWithoutDurationDoesNotCrash() {
        host.library.save(progress(Series("1", "历史剧"), 0, 0, 0, 1))
        val page = LibraryPage(host, 1)
        page.onShow()
        val card = page.historyGrid.adapter.getView(0, null, page.historyGrid)
        val bar = views(card).filterIsInstance<ProgressBar>().single()
        assertEquals(0, bar.progress)
    }

    @Test
    fun removingHistoryDropsTheRow() {
        host.library.save(progress(Series("1", "第一部"), 0, 10, 100, 2))
        host.library.save(progress(Series("2", "第二部"), 0, 20, 100, 1))
        val page = LibraryPage(host, 1)
        page.onShow()
        val grid = page.historyGrid
        assertEquals(2, grid.adapter.count)
        val card = grid.adapter.getView(0, null, grid)
        grid.onItemLongClickListener.onItemLongClick(grid, card, 0, 0)
        val sheetTexts =
            texts(ShadowDialog.getLatestDialog().window!!.decorView).map { it.text.toString() }
        assertTrue(sheetTexts.any { it.contains("移出记录") })
        texts(ShadowDialog.getLatestDialog().window!!.decorView)
            .single { it.text.toString() == "移出记录" }
            .performClick()
        assertEquals(1, host.library.history().size)
        assertEquals(1, grid.adapter.count)
    }

    @Test
    fun switchingToAccountWhileSignedOutOpensLogin() {
        val page = LibraryPage(host, 0)
        page.onShow()
        texts(page.view).single { it.text.toString() == "账号" }.performClick()
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertTrue(dialog != null && dialog.isShowing)
    }

    @Test
    fun reservationsShowAnEmptyState() {
        val page = LibraryPage(host, 2)
        page.onShow()
        assertTrue(texts(page.view).any { it.text.toString() == "还没有预约的剧集" })
        assertFalse(texts(page.view).any { it.text.toString() == "本机" })
    }
}
