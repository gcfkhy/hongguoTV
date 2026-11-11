package com.hongguotv.nativeapp.touch

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import androidx.media3.ui.PlayerView
import com.hongguotv.nativeapp.R
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
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26], qualifiers = "w411dp-h891dp-port-xxhdpi")
@LooperMode(LooperMode.Mode.PAUSED)
class PlayerChromeTest {
    private class FakeActions : PlayerChromeActions {
        var current = PlayerState(10_000, 600_000, playing = true, buffering = false, ready = true, error = false)
        val toggles = mutableListOf<Unit>()
        val seeks = mutableListOf<Long>()
        val skips = mutableListOf<Int>()
        val speeds = mutableListOf<Float?>()
        val episodes = mutableListOf<Unit>()
        val speedSheets = mutableListOf<Unit>()
        val more = mutableListOf<Unit>()
        val comments = mutableListOf<Unit>()
        val backs = mutableListOf<Unit>()

        override fun state() = current

        override fun toggle() {
            toggles += Unit
        }

        override fun seekTo(ms: Long) {
            seeks += ms
        }

        override fun skip(direction: Int) {
            skips += direction
        }

        override fun setSpeedTemporarily(speed: Float?) {
            speeds += speed
        }

        override fun showEpisodes() {
            episodes += Unit
        }

        override fun showSpeed() {
            speedSheets += Unit
        }

        override fun showMore() {
            more += Unit
        }

        override fun showComments() {
            comments += Unit
        }

        override fun back() {
            backs += Unit
        }

        fun calls() = toggles.size + seeks.size + skips.size + speeds.size + episodes.size +
            speedSheets.size + more.size + comments.size + backs.size
    }

    private lateinit var controller: ActivityController<Activity>
    private lateinit var activity: Activity
    private lateinit var actions: FakeActions
    private lateinit var chrome: PlayerChrome
    private lateinit var playerView: PlayerView

    @Before
    fun setUp() {
        controller = Robolectric.buildActivity(Activity::class.java).setup()
        activity = controller.get()
        actions = FakeActions()
        chrome = PlayerChrome(activity, actions)
        playerView = PlayerView(activity)
        val root = FrameLayout(activity)
        root.addView(playerView, FrameLayout.LayoutParams(1080, 1920))
        chrome.install(root, playerView)
        playerView.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
        )
        playerView.layout(0, 0, 1080, 1920)
    }

    private fun views(root: View): List<View> =
        if (root is ViewGroup) (0 until root.childCount).flatMap { views(root.getChildAt(it)) } + root
        else listOf(root)

    private fun icon(description: String): ImageButton =
        views(chrome.views.hud)
            .filterIsInstance<ImageButton>()
            .single { it.contentDescription?.toString() == description }

    private fun motion(action: Int, x: Float, y: Float, downTime: Long, eventTime: Long): MotionEvent =
        MotionEvent.obtain(downTime, eventTime, action, x, y, 0)

    @Test
    fun showHidesItselfWhilePlaying() {
        chrome.show()
        assertEquals(View.VISIBLE, chrome.views.hud.visibility)
        ShadowLooper.idleMainLooper(3000, TimeUnit.MILLISECONDS)
        assertEquals(View.GONE, chrome.views.hud.visibility)
    }

    @Test
    fun pausedPlaybackKeepsControlsVisible() {
        actions.current = actions.current.copy(playing = false)
        chrome.show()
        ShadowLooper.idleMainLooper(3500, TimeUnit.MILLISECONDS)
        assertEquals(View.VISIBLE, chrome.views.hud.visibility)
    }

    @Test
    fun transportButtonsCallTheEngine() {
        icon("播放").performClick()
        assertEquals(1, actions.toggles.size)
        icon("下一集").performClick()
        assertEquals(listOf(1), actions.skips)
        icon("选集").performClick()
        icon("倍速").performClick()
        assertEquals(1, actions.episodes.size)
        assertEquals(1, actions.speedSheets.size)
    }

    @Test
    fun horizontalDragSeeksOnRelease() {
        val down = android.os.SystemClock.uptimeMillis()
        playerView.dispatchTouchEvent(motion(MotionEvent.ACTION_DOWN, 540f, 800f, down, down))
        playerView.dispatchTouchEvent(motion(MotionEvent.ACTION_MOVE, 810f, 800f, down, down + 100))
        assertTrue(chrome.views.status.text.toString().contains("/"))
        playerView.dispatchTouchEvent(motion(MotionEvent.ACTION_UP, 810f, 800f, down, down + 200))
        assertEquals(listOf(40_000L), actions.seeks)
    }

    @Test
    fun verticalFlingSkipsTheEpisode() {
        val down = android.os.SystemClock.uptimeMillis()
        playerView.dispatchTouchEvent(motion(MotionEvent.ACTION_DOWN, 540f, 800f, down, down))
        playerView.dispatchTouchEvent(motion(MotionEvent.ACTION_MOVE, 540f, 650f, down, down + 100))
        playerView.dispatchTouchEvent(motion(MotionEvent.ACTION_UP, 540f, 650f, down, down + 100))
        assertEquals(listOf(1), actions.skips)
    }

    @Test
    fun longPressTemporarilyDoublesSpeed() {
        val down = android.os.SystemClock.uptimeMillis()
        playerView.dispatchTouchEvent(motion(MotionEvent.ACTION_DOWN, 540f, 800f, down, down))
        ShadowLooper.idleMainLooper(600, TimeUnit.MILLISECONDS)
        assertEquals(listOf<Float?>(2f), actions.speeds)
        playerView.dispatchTouchEvent(motion(MotionEvent.ACTION_UP, 540f, 800f, down, down + 600))
        assertEquals(listOf<Float?>(2f, null), actions.speeds)
    }

    @Test
    fun errorStateSwapsControlsForRecovery() {
        actions.current = actions.current.copy(error = true)
        chrome.refresh()
        assertEquals(View.GONE, chrome.views.progress.visibility)
        assertEquals(View.VISIBLE, chrome.views.controls.visibility)
    }

    @Test
    fun relayoutNeverTouchesPlayback() {
        val before = actions.calls()
        chrome.relayout()
        assertEquals(before, actions.calls())
    }

    @Test
    fun fullscreenTogglesOrientation() {
        val fullscreen = FullscreenController(activity)
        fullscreen.toggleLandscape()
        assertTrue(fullscreen.forcedLandscape)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, activity.requestedOrientation)
        fullscreen.toggleLandscape()
        assertFalse(fullscreen.forcedLandscape)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR, activity.requestedOrientation)
    }
}
