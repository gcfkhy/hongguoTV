package com.hongguotv.nativeapp.touch

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.hongguotv.core.Detail
import com.hongguotv.core.Series
import com.hongguotv.nativeapp.WatchProgress
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26], qualifiers = "w411dp-h891dp-port")
class DetailPageTest {
    private class Recorder : DetailPage.Actions {
        val plays = mutableListOf<Pair<Int, Long>>()
        var backs = 0
        val related = mutableListOf<Series>()
        var comments = 0

        override fun play(index: Int, position: Long) {
            plays += index to position
        }

        override fun back() {
            backs++
        }

        override fun openRelated(series: Series) {
            related += series
        }

        override fun comments() {
            comments++
        }
    }

    private lateinit var controller: ActivityController<Activity>
    private lateinit var host: FakeTouchHost
    private lateinit var actions: Recorder
    private var series = Series("1", "测试剧", description = "这是一段剧情简介", tags = "都市 · 逆袭")

    @Before
    fun setUp() {
        controller = Robolectric.buildActivity(Activity::class.java).setup()
        host = FakeTouchHost(controller.get())
        actions = Recorder()
    }

    @After
    fun tearDown() {
        host.destroy()
        controller.destroy()
    }

    private fun detail(count: Int, title: String = "测试剧", description: String = "这是一段剧情简介") =
        Detail(Series("1", title, description = description, tags = "都市 · 逆袭"), (1..count).map { "ep$it" })

    private fun watch(index: Int, position: Long, duration: Long = 100_000) =
        WatchProgress(series, "ep${index + 1}", index, position, duration, completed = false, updatedAt = 1)

    private fun page(
        count: Int = 10,
        progress: WatchProgress? = null,
        watched: Boolean = false,
        current: Int = 0,
        title: String = "测试剧",
        description: String = "这是一段剧情简介",
    ) = DetailPage(
        host,
        detail(count, title, description),
        progress,
        watched,
        current,
        actions,
        related = { emptyList() },
    )

    private fun texts(root: View): List<TextView> =
        when (root) {
            is ViewGroup -> (0 until root.childCount).flatMap { texts(root.getChildAt(it)) }
            is TextView -> listOf(root)
            else -> emptyList()
        }

    @Test
    fun startsFromTheFirstEpisodeWithoutProgress() {
        val page = page()
        clickText(page, "开始观看")
        assertEquals(listOf(0 to 0L), actions.plays)
    }

    @Test
    fun resumesTheRecordedEpisode() {
        val page = page(progress = watch(4, 30_000))
        clickText(page, "继续第 5 集")
        assertEquals(listOf(4 to 30_000L), actions.plays)
    }

    @Test
    fun watchedSeriesStartsOver() {
        val page = page(watched = true)
        clickText(page, "重新观看")
        assertEquals(listOf(0 to 0L), actions.plays)
    }

    @Test
    fun hundredEpisodesSplitIntoFiveGroups() {
        val page = page(count = 100)
        listOf("1–20", "21–40", "41–60", "61–80", "81–100").forEach { label ->
            assertTrue("缺少分组 $label", texts(page.view).any { it.text.toString() == label })
        }
        clickText(page, "21–40")
        val cell = page.episodeGrid.adapter.getView(0, null, page.episodeGrid)
        assertTrue(texts(cell).first().text.toString().startsWith("21"))
    }

    @Test
    fun currentAndLastMarksLandOnTheRightCells() {
        val page = page(progress = watch(4, 30_000), current = 2)
        val currentCell = page.episodeGrid.adapter.getView(2, null, page.episodeGrid)
        assertTrue(texts(currentCell).first().text.toString().contains("当前"))
        val lastCell = page.episodeGrid.adapter.getView(4, null, page.episodeGrid)
        assertTrue(texts(lastCell).first().text.toString().contains("上次"))
        val seenCell = page.episodeGrid.adapter.getView(0, null, page.episodeGrid)
        assertTrue(texts(seenCell).first().text.toString().contains("已看"))
    }

    @Test
    fun pickingAnEpisodePlaysItFromTheStart() {
        val page = page()
        val grid = page.episodeGrid
        grid.onItemClickListener!!.onItemClick(grid, null, 6, 6)
        assertEquals(listOf(6 to 0L), actions.plays)
    }

    @Test
    fun pickingTheUnfinishedEpisodeKeepsItsPosition() {
        val page = page(progress = watch(6, 12_000))
        val grid = page.episodeGrid
        grid.onItemClickListener!!.onItemClick(grid, null, 6, 6)
        assertEquals(listOf(6 to 12_000L), actions.plays)
    }

    @Test
    fun synopsisExpandsOnTap() {
        val text = "这是一段剧情简介"
        val page = page(description = text)
        val synopsis = texts(page.view).single { it.text.toString() == text }
        assertEquals(2, synopsis.maxLines)
        synopsis.performClick()
        assertEquals(20, synopsis.maxLines)
    }

    @Test
    fun longTitleStaysWithinTwoLines() {
        val page = page(title = "超长剧名".repeat(20))
        val title = texts(page.view).first { it.text.toString().startsWith("超长剧名") }
        assertEquals(2, title.maxLines)
        assertNotNull(title.ellipsize)
    }

    private fun clickText(page: DetailPage, label: String) {
        texts(page.view).single { it.text.toString() == label }.performClick()
    }
}
