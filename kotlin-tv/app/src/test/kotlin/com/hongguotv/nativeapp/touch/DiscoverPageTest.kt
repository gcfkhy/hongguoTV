package com.hongguotv.nativeapp.touch

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import com.hongguotv.core.CatalogPage
import com.hongguotv.core.ComicRanking
import com.hongguotv.core.DramaCategory
import com.hongguotv.core.RankBoard
import com.hongguotv.core.RankPosition
import com.hongguotv.core.RankSnapshot
import com.hongguotv.core.Series
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper
import java.io.IOException
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26], qualifiers = "w411dp-h891dp-port")
@LooperMode(LooperMode.Mode.PAUSED)
class DiscoverPageTest {
    private lateinit var controller: ActivityController<Activity>
    private lateinit var host: FakeTouchHost
    private lateinit var page: DiscoverPage

    private val suggestionQueries = mutableListOf<String>()
    private val searchCalls = mutableListOf<String>()
    private val rankCalls = mutableListOf<RankBoard>()
    private var searchResult: (String) -> CatalogPage = { CatalogPage(emptyList(), false) }
    private var rankFailure = false
    private var rankSnapshot = snapshot(RankBoard.ALL, listOf(Series("1", "榜单剧一")))

    private fun snapshot(board: RankBoard, items: List<Series>) =
        RankSnapshot(
            board,
            items,
            ComicRanking(
                items.mapIndexed { index, series -> series.id to RankPosition(index + 1, "9$index") }.toMap(),
                totalPages = 1,
                updatedText = "刚刚",
            ),
        )

    private fun series(count: Int, prefix: String = "推荐剧") =
        (1..count).map { Series("$it", "$prefix$it") }

    @Before
    fun setUp() {
        controller = Robolectric.buildActivity(Activity::class.java).setup()
        host = FakeTouchHost(controller.get())
        page =
            DiscoverPage(
                host,
                segment = 0,
                suggest = { query ->
                    suggestionQueries += query
                    listOf("$query 联想")
                },
                search = { query, _ ->
                    searchCalls += query
                    searchResult(query)
                },
                discover = { CatalogPage(series(2), false) },
                rank = { board ->
                    rankCalls += board
                    if (rankFailure) throw IOException("榜单加载失败") else rankSnapshot
                },
            )
        page.onShow()
        ShadowLooper.idleMainLooper()
    }

    @After
    fun tearDown() {
        page.close()
        host.destroy()
        controller.destroy()
    }

    private fun texts(root: View): List<TextView> =
        when (root) {
            is ViewGroup -> (0 until root.childCount).flatMap { texts(root.getChildAt(it)) }
            is TextView -> listOf(root)
            else -> emptyList()
        }

    private fun clickText(label: String) {
        texts(page.view).single { it.text.toString() == label }.performClick()
    }

    private fun searchInput(): EditText = texts(page.view).filterIsInstance<EditText>().single()

    private fun enterSearch() {
        clickText("搜索剧名")
    }

    @Test
    fun backLeavesSearchBeforeLeavingThePage() {
        assertFalse(page.onBack())
        enterSearch()
        assertTrue(page.onBack())
        assertFalse(page.onBack())
    }

    @Test
    fun suggestionsAreDebouncedToTheLatestInput() {
        enterSearch()
        val input = searchInput()
        input.setText("a")
        input.setText("ab")
        input.setText("abc")
        ShadowLooper.idleMainLooper(299, TimeUnit.MILLISECONDS)
        assertTrue(suggestionQueries.isEmpty())
        ShadowLooper.idleMainLooper(1, TimeUnit.MILLISECONDS)
        assertEquals(listOf("abc"), suggestionQueries)
    }

    @Test
    fun submittingSearchRemembersTheKeywordAndShowsResults() {
        searchResult = { CatalogPage(series(2, "结果剧"), false) }
        enterSearch()
        searchInput().setText("abc")
        clickText("搜索")
        assertEquals(listOf("abc"), searchCalls)
        assertEquals("abc", host.library.searches().single().query)
        assertEquals(2, page.posterGrid.itemCount)
    }

    @Test
    fun emptySearchShowsAMessage() {
        searchResult = { CatalogPage(emptyList(), false) }
        enterSearch()
        searchInput().setText("abc")
        clickText("搜索")
        assertTrue(texts(page.view).any { it.text.toString().contains("没有找到") })
    }

    @Test
    fun rankingChipsReloadTheSelectedBoard() {
        clickText("排行榜")
        assertTrue(rankCalls.isNotEmpty())
        clickText("漫剧")
        assertEquals(RankBoard.COMIC, rankCalls.last())
        val labels = listOf("综合", "真人剧", "漫剧", "AI 剧")
        labels.forEach { label ->
            assertTrue("缺少榜单 $label", texts(page.view).any { it.text.toString() == label })
        }
    }

    @Test
    fun rankingFailureShowsRetryAndRequestsAgain() {
        rankFailure = true
        clickText("排行榜")
        assertTrue(texts(page.view).any { it.text.toString() == "重试" })
        val before = rankCalls.size
        rankFailure = false
        clickText("重试")
        assertEquals(before + 1, rankCalls.size)
    }

    @Test
    fun longPressOffersQuickActions() {
        page.onShow()
        val item = series(1).first()
        page.posterGrid.onLongPress(item, page.view)
        val dialogTexts =
            texts(ShadowDialog.getLatestDialog().window!!.decorView).map { it.text.toString() }
        assertTrue(dialogTexts.any { it.contains("查看完整标题") })
        assertNotNull(ShadowDialog.getLatestDialog())
    }

    @Test
    fun categoryChipsReloadDiscover() {
        val before = texts(page.view).count { it.text.toString() == "推荐剧1" }
        assertEquals(0, before)
        assertTrue(texts(page.view).any { it.text.toString() == "全部" })
    }
}
