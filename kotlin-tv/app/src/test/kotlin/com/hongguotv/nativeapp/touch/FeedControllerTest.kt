package com.hongguotv.nativeapp.touch

import android.app.Activity
import android.view.View
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26], qualifiers = "w411dp-h891dp-port")
@LooperMode(LooperMode.Mode.PAUSED)
class FeedControllerTest {
    private class Backend(private val count: Int = 3) : FeedBackend {
        var reads = 0
        var fail = false
        val categoryCalls = mutableListOf<DramaCategory>()

        override fun catalog(category: DramaCategory, page: Int) = CatalogPage(emptyList(), false)

        override fun recommendations(page: Int, cursor: RecommendationCursor): CatalogPage {
            reads++
            if (fail) throw java.io.IOException("加载失败")
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
        var released = false
        var duration = 0L

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

        override fun duration() = duration

        override fun clear() {
            loaded = ""
            playing = false
        }

        override fun release() {
            released = true
        }
    }

    private class Listener : FeedController.Listener {
        val series = mutableListOf<Series?>()
        val indexes = mutableListOf<Int>()
        val statuses = mutableListOf<String?>()
        val clips = mutableListOf<TrialClip?>()
        var playing = false

        override fun onSeries(series: Series?, index: Int) {
            this.series += series
            indexes += index
        }

        override fun onStatus(message: String?) {
            statuses += message
        }

        override fun onPlaying(playing: Boolean) {
            this.playing = playing
        }

        override fun onProgress(positionMs: Long, durationMs: Long) = Unit

        override fun onTrial(clip: TrialClip?) {
            clips += clip
        }

        override fun onChange() = Unit
    }

    private fun field(target: Any, name: String): Any? =
        target.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(target)

    private fun pump(controller: FeedController) {
        repeat(4) {
            (field(controller, "job") as? Future<*>)?.get(5, TimeUnit.SECONDS)
            ShadowLooper.idleMainLooper()
        }
    }

    private fun activity(): Activity = Robolectric.buildActivity(Activity::class.java).setup().get()

    @Test
    fun startLoadsTheFirstSeriesAndSwitchesWithNextAndPrevious() {
        val state = DramaFeedState()
        val backend = Backend()
        val playback = Playback(activity())
        val listener = Listener()
        val controller = FeedController(state, backend, playback, true, listener)
        try {
            controller.start(true)
            pump(controller)
            assertEquals("r1", listener.series.last()?.id)
            assertEquals(0, listener.indexes.last())
            controller.next()
            pump(controller)
            assertEquals("r2", listener.series.last()?.id)
            assertEquals(1, listener.indexes.last())
            controller.previous()
            pump(controller)
            assertEquals("r1", listener.series.last()?.id)
            controller.previous()
            pump(controller)
            assertEquals("r1", listener.series.last()?.id)
        } finally {
            controller.close()
        }
    }

    @Test
    fun categorySelectionRequestsTheMatchingCategory() {
        val state = DramaFeedState()
        val backend = Backend()
        val controller = FeedController(state, backend, Playback(activity()), true, Listener())
        try {
            controller.start(true)
            pump(controller)
            controller.setCategory(DramaCategory.COMIC)
            pump(controller)
            assertEquals(listOf(DramaCategory.COMIC), backend.categoryCalls)
            assertFalse(state.recommendation)
            controller.setCategory(null)
            pump(controller)
            assertTrue(state.recommendation)
        } finally {
            controller.close()
        }
    }

    @Test
    fun refreshReReadsTheRecommendationSource() {
        val backend = Backend()
        val controller = FeedController(DramaFeedState(), backend, Playback(activity()), true, Listener())
        try {
            controller.start(true)
            pump(controller)
            val before = backend.reads
            controller.refresh()
            pump(controller)
            assertTrue(backend.reads > before)
        } finally {
            controller.close()
        }
    }

    @Test
    fun backendFailureReportsANonEmptyStatus() {
        val backend = Backend().apply { fail = true }
        val listener = Listener()
        val controller = FeedController(DramaFeedState(), backend, Playback(activity()), true, listener)
        try {
            controller.start(true)
            pump(controller)
            assertTrue(listener.statuses.filterNotNull().any { it.contains("加载失败") })
        } finally {
            controller.close()
        }
    }
}
