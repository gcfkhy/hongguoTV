package com.hongguotv.nativeapp.touch

import android.app.Activity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.hongguotv.core.CatalogPage
import com.hongguotv.core.Detail
import com.hongguotv.core.DramaCategory
import com.hongguotv.core.DramaFeedState
import com.hongguotv.core.RecommendationCursor
import com.hongguotv.core.RemoteVideo
import com.hongguotv.core.Series
import com.hongguotv.core.StreamInfo
import com.hongguotv.nativeapp.FeedBackend
import com.hongguotv.nativeapp.TrialClip
import com.hongguotv.nativeapp.TrialPlayback
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
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
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26], qualifiers = "w411dp-h891dp-port")
@LooperMode(LooperMode.Mode.PAUSED)
class HomePageTest {
    private class Backend(private val count: Int = 3, private val failure: Throwable? = null) : FeedBackend {
        val categoryCalls = mutableListOf<DramaCategory>()

        override fun catalog(category: DramaCategory, page: Int) = CatalogPage(emptyList(), false)

        override fun recommendations(page: Int, cursor: RecommendationCursor): CatalogPage {
            failure?.let { throw it }
            return CatalogPage((1..count).map { Series("r$it", "作品$it") }, false)
        }

        override fun categoryRecommendations(
            category: DramaCategory,
            page: Int,
            cursor: RecommendationCursor,
        ): CatalogPage {
            categoryCalls += category
            return CatalogPage((1..count).map { Series("${category.route}$it", "作品$it") }, false)
        }

        override fun detail(series: Series) = Detail(series, listOf("${series.id}01"))

        override fun prepare(detail: Detail) =
            TrialClip(detail, RemoteVideo(OkHttpClient(), StreamInfo("https://example.com/video", null, "test")))

        override fun close() = Unit
    }

    private class Playback(activity: Activity) : TrialPlayback {
        override val view = View(activity)
        override var status: ((com.hongguotv.nativeapp.TrialStatus) -> Unit)? = null
        var loaded = ""
        var playing = false

        override fun load(clip: TrialClip, position: Long, autoplay: Boolean) {
            loaded = clip.detail.series.id
            playing = autoplay
        }

        override fun pause() {
            playing = false
        }

        override fun resume() {
            playing = true
        }

        override fun restart() {
            playing = true
        }

        override fun position() = 0L

        override fun clear() {
            loaded = ""
            playing = false
        }

        override fun release() = Unit
    }

    private lateinit var controller: ActivityController<Activity>
    private lateinit var activity: Activity
    private lateinit var host: FakeTouchHost

    @Before
    fun setUp() {
        controller = Robolectric.buildActivity(Activity::class.java).setup()
        activity = controller.get()
        host = FakeTouchHost(activity)
    }

    @After
    fun tearDown() {
        host.destroy()
        controller.pause().stop().destroy()
    }

    private fun field(target: Any, name: String): Any? =
        target.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(target)

    private fun pump(page: HomePage) {
        val feed = page.controller
        repeat(4) {
            (field(feed, "job") as? Future<*>)?.get(5, TimeUnit.SECONDS)
            ShadowLooper.idleMainLooper()
        }
    }

    private fun launch(backend: Backend = Backend()): Pair<HomePage, Playback> {
        val playback = Playback(activity)
        val page = HomePage(host, DramaFeedState(), backend, playback)
        activity.setContentView(page.view)
        page.view.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
        )
        page.view.layout(0, 0, 1080, 1920)
        page.onShow()
        pump(page)
        return page to playback
    }

    private fun texts(view: View): List<TextView> =
        if (view is ViewGroup) (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
        else if (view is TextView) listOf(view) else emptyList()

    private fun motion(action: Int, x: Float, y: Float, downTime: Long, eventTime: Long): MotionEvent =
        MotionEvent.obtain(downTime, eventTime, action, x, y, 0)

    @Test
    fun swipingUpMovesToTheNextSeries() {
        val (page, playback) = launch()
        try {
            assertEquals("r1", playback.loaded)
            val stage = page.stage
            val down = android.os.SystemClock.uptimeMillis()
            stage.dispatchTouchEvent(motion(MotionEvent.ACTION_DOWN, 540f, 1600f, down, down))
            stage.dispatchTouchEvent(motion(MotionEvent.ACTION_MOVE, 540f, 1300f, down, down + 100))
            stage.dispatchTouchEvent(motion(MotionEvent.ACTION_UP, 540f, 1300f, down, down + 100))
            pump(page)
            assertEquals("r2", playback.loaded)
        } finally {
            page.close()
        }
    }

    @Test
    fun tappingTheVideoTogglesPlayback() {
        val (page, playback) = launch()
        try {
            assertTrue(playback.playing)
            val down = android.os.SystemClock.uptimeMillis()
            page.stage.dispatchTouchEvent(motion(MotionEvent.ACTION_DOWN, 540f, 900f, down, down))
            page.stage.dispatchTouchEvent(motion(MotionEvent.ACTION_UP, 540f, 900f, down, down + 60))
            assertFalse(playback.playing)
        } finally {
            page.close()
        }
    }

    @Test
    fun watchWholeSeriesOpensTheDetail() {
        val (page, _) = launch()
        try {
            texts(page.view).first { it.text == "看全集" }.performClick()
            assertEquals(listOf("r1"), host.opened.map { it.first.id })
            assertEquals(listOf(false), host.opened.map { it.second })
        } finally {
            page.close()
        }
    }

    @Test
    fun comicChipSwitchesTheCategory() {
        val backend = Backend()
        val (page, _) = launch(backend)
        try {
            texts(page.view).first { it.text == "漫剧" }.performClick()
            pump(page)
            assertEquals(listOf(DramaCategory.COMIC), backend.categoryCalls)
        } finally {
            page.close()
        }
    }

    @Test
    fun failureShowsRetry() {
        val (page, _) = launch(Backend(failure = java.io.IOException("加载失败")))
        try {
            assertTrue(texts(page.view).any { it.text == "重试" && it.isShown })
        } finally {
            page.close()
        }
    }
}
