package com.hongguotv.nativeapp.touch

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.GridView
import android.widget.TextView
import com.hongguotv.core.Series
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26], qualifiers = "w411dp-h891dp-port")
class PosterGridViewTest {
    private lateinit var controller: ActivityController<Activity>
    private lateinit var host: FakeTouchHost
    private lateinit var view: PosterGridView

    @Before
    fun setUp() {
        controller = Robolectric.buildActivity(Activity::class.java).setup()
        host = FakeTouchHost(controller.get())
        view = PosterGridView(controller.get(), host)
    }

    @After
    fun tearDown() {
        host.destroy()
        controller.destroy()
    }

    private fun grid(): GridView =
        (0 until view.childCount)
            .map { view.getChildAt(it) }
            .filterIsInstance<GridView>()
            .single()

    private fun texts(root: View): List<TextView> =
        when (root) {
            is ViewGroup -> (0 until root.childCount).flatMap { texts(root.getChildAt(it)) }
            is TextView -> listOf(root)
            else -> emptyList()
        }

    private fun series(count: Int, title: (Int) -> String = { "标题$it" }) =
        (1..count).map { Series("$it", title(it)) }

    private fun scroll(grid: GridView, first: Int, visible: Int, total: Int) {
        view.scrollListener.onScroll(grid, first, visible, total)
    }

    private fun layout() {
        val metrics = view.context.resources.displayMetrics
        view.measure(
            View.MeasureSpec.makeMeasureSpec(metrics.widthPixels, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(metrics.heightPixels, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, metrics.widthPixels, metrics.heightPixels)
    }

    @Test
    fun compactUsesThreeColumns() {
        view.show(series(10), hasMore = false)
        layout()
        assertEquals(3, grid().numColumns)
        assertEquals(10, grid().adapter.count)
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land")
    fun expandedUsesSixColumns() {
        view.show(series(10), hasMore = false)
        layout()
        assertEquals(6, grid().numColumns)
    }

    @Test
    fun relayoutFollowsWindowClassChanges() {
        view.show(series(4), hasMore = false)
        layout()
        assertEquals(3, grid().numColumns)
        RuntimeEnvironment.setQualifiers("w1280dp-h800dp-land")
        view.relayout()
        layout()
        assertEquals(6, grid().numColumns)
    }

    @Test
    fun tappingAPosterOpensIt() {
        val items = series(5)
        var opened: Series? = null
        view.onOpen = { opened = it }
        view.show(items, hasMore = false)
        val grid = grid()
        grid.onItemClickListener!!.onItemClick(grid, null, 2, 2)
        assertEquals(items[2], opened)
    }

    @Test
    fun longPressReportsTheCard() {
        val items = series(5)
        var pressed: Pair<Series, View>? = null
        view.onLongPress = { series, anchor -> pressed = series to anchor }
        view.show(items, hasMore = false)
        val grid = grid()
        val card = grid.adapter.getView(1, null, grid)
        assertTrue(grid.onItemLongClickListener.onItemLongClick(grid, card, 1, 1))
        assertEquals(items[1], pressed?.first)
    }

    @Test
    fun appendAddsToTheExistingItems() {
        view.show(series(3), hasMore = true)
        view.append(series(2).map { it.copy(id = "x${it.id}") }, hasMore = false)
        assertEquals(5, grid().adapter.count)
    }

    @Test
    fun nearEndFiresOnceUntilMoreIsAppended() {
        var calls = 0
        view.onNearEnd = { calls++ }
        view.show(series(10), hasMore = true)
        val grid = grid()
        scroll(grid, 4, 6, 10)
        assertEquals(1, calls)
        scroll(grid, 5, 5, 10)
        assertEquals(1, calls)
        view.append(series(2).map { it.copy(id = "m${it.id}") }, hasMore = true)
        scroll(grid, 10, 2, 12)
        assertEquals(2, calls)
    }

    @Test
    fun emptyStateOffersAnAction() {
        var clicked = 0
        view.showEmpty("暂无收藏", "去找剧") { clicked++ }
        texts(view).single { it.text.toString() == "去找剧" }.performClick()
        assertEquals(1, clicked)
        assertTrue(texts(view).any { it.text.toString() == "暂无收藏" })
    }

    @Test
    fun errorStateRetries() {
        var retries = 0
        view.onRetry = { retries++ }
        view.showError("加载失败")
        texts(view).single { it.text.toString() == "重试" }.performClick()
        assertEquals(1, retries)
    }

    @Test
    fun longTitlesStayOnOneLine() {
        view.show(series(1) { "超长标题".repeat(20) }, hasMore = false)
        val grid = grid()
        val card = grid.adapter.getView(0, null, grid)
        val title = texts(card).first { it.text.toString().startsWith("超长标题") }
        assertEquals(1, title.maxLines)
        assertNotNull(title.ellipsize)
    }
}
