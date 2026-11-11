package com.hongguotv.nativeapp

import android.view.KeyEvent
import android.widget.LinearLayout
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.hongguotv.core.Detail
import com.hongguotv.core.Series
import java.lang.reflect.Proxy
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
@LooperMode(LooperMode.Mode.PAUSED)
class PlaybackInputTest {
    @Test
    fun televisionRemoteKeepsOverlayMapping() {
        assertEquals(
            PlaybackInput.Command.Toggle,
            PlaybackInput.command(KeyEvent.KEYCODE_DPAD_CENTER, true),
        )
        assertEquals(
            PlaybackInput.Command.SeekBack,
            PlaybackInput.command(KeyEvent.KEYCODE_DPAD_LEFT, true),
        )
        assertEquals(
            PlaybackInput.Command.ShowPanel,
            PlaybackInput.command(KeyEvent.KEYCODE_DPAD_UP, true),
        )
        assertEquals(
            PlaybackInput.Command.ShowEpisodes,
            PlaybackInput.command(KeyEvent.KEYCODE_DPAD_DOWN, true),
        )
    }

    @Test
    fun touchAndEmulatorUseSpaceSeekAndEpisodeSkip() {
        assertEquals(
            PlaybackInput.Command.Toggle,
            PlaybackInput.command(KeyEvent.KEYCODE_SPACE, false),
        )
        assertEquals(
            PlaybackInput.Command.SeekForward,
            PlaybackInput.command(KeyEvent.KEYCODE_DPAD_RIGHT, false),
        )
        assertEquals(
            PlaybackInput.Command.PreviousEpisode,
            PlaybackInput.command(KeyEvent.KEYCODE_DPAD_UP, false),
        )
        assertEquals(
            PlaybackInput.Command.NextEpisode,
            PlaybackInput.command(KeyEvent.KEYCODE_DPAD_DOWN, false),
        )
        assertEquals(-1, PlaybackInput.wheelEpisodeDelta(1f))
        assertEquals(1, PlaybackInput.wheelEpisodeDelta(-1f))
        assertEquals(0, PlaybackInput.wheelEpisodeDelta(0f))
        assertEquals(1, PlaybackInput.verticalFlingDelta(-1200f))
        assertEquals(-1, PlaybackInput.verticalFlingDelta(1200f))
        assertEquals(0, PlaybackInput.verticalFlingDelta(100f))
    }

    @Test
    @Config(sdk = [26], qualifiers = "w360dp-h800dp-port-mdpi")
    fun phoneFollowsTheSensor() {
        val context = RuntimeEnvironment.getApplication()
        assertEquals(DeviceUi.Form.Phone, DeviceUi.form(context))
        assertEquals(
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR,
            DeviceUi.preferredOrientation(context),
        )
    }

    @Test
    @Config(sdk = [26], qualifiers = "sw720dp-w1280dp-h800dp-land-mdpi")
    fun tabletAlsoFollowsTheSensor() {
        val context = RuntimeEnvironment.getApplication()
        assertEquals(DeviceUi.Form.Tablet, DeviceUi.form(context))
        assertEquals(
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR,
            DeviceUi.preferredOrientation(context),
        )
    }

    @Test
    @Config(sdk = [26], qualifiers = "w960dp-h540dp-land-mdpi")
    fun phoneDoesNotStealFirstTapForFocus() {
        val context = RuntimeEnvironment.getApplication()
        assertFalse(DeviceUi.television(context))
        assertTrue(DeviceUi.touchFirst(context))
        val view = TextView(context)
        view.enableRemoteFocus()
        assertTrue(view.isFocusable)
        assertFalse(view.isFocusableInTouchMode)
        assertEquals(6, DeviceUi.catalogColumns(context))
    }

    @Test
    @Config(sdk = [26], qualifiers = "w360dp-h640dp-port-mdpi")
    fun phonePortraitUsesThreeCatalogColumns() {
        assertEquals(3, DeviceUi.catalogColumns(RuntimeEnvironment.getApplication()))
    }

    @Test
    @Config(sdk = [26], qualifiers = "w960dp-h540dp-land-television-mdpi")
    fun televisionKeepsFocusInTouchMode() {
        val context = RuntimeEnvironment.getApplication()
        assertTrue(DeviceUi.television(context))
        val view = TextView(context)
        view.enableRemoteFocus()
        assertTrue(view.isFocusableInTouchMode)
        assertEquals(6, DeviceUi.catalogColumns(context))
    }

    @Test
    @Config(sdk = [26], qualifiers = "w960dp-h540dp-land-mdpi")
    fun spacePausesAndArrowsSeekOnPhonePlayer() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create()
        val activity = controller.get()
        val calls = mutableListOf<String>()
        try {
            val library = field(activity, "library") as Library
            (field(library, "loader") as ExecutorService).submit {}.get(5, TimeUnit.SECONDS)
            (field(field(activity, "cloudAccount")!!, "worker") as ExecutorService)
                .submit {}
                .get(5, TimeUnit.SECONDS)
            ShadowLooper.idleMainLooper()
            fieldSet(activity, "detail", Detail(Series("1", "触控播放"), listOf("101", "102")))
            fieldSet(activity, "screen", "player")
            fieldSet(activity, "foreground", true)
            fieldSet(activity, "player", fakePlayer(calls))
            fieldSet(activity, "episodeIndex", 0)
            fieldSet(activity, "playbackReady", true)
            fieldSet(activity, "playbackTitle", TextView(activity))
            fieldSet(activity, "controls", LinearLayout(activity))
            fieldSet(activity, "hud", LinearLayout(activity))
            fieldSet(activity, "playbackText", TextView(activity))
            fieldSet(activity, "progressBar", android.widget.ProgressBar(activity))
            assertTrue(
                activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SPACE))
            )
            assertTrue(calls.contains("pause"))
            assertTrue(
                activity.dispatchKeyEvent(
                    KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT)
                )
            )
            assertEquals(10000L, field(activity, "pendingSeek"))
        } finally {
            fieldSet(activity, "foreground", false)
            controller.destroy()
        }
    }

    private fun field(target: Any, name: String) =
        target.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(target)

    private fun fieldSet(target: Any, name: String, value: Any?) {
        target.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(target, value)
    }

    private fun fakePlayer(calls: MutableList<String>): ExoPlayer {
        val item = MediaItem.Builder().setMediaId("101").build()
        return Proxy.newProxyInstance(
            ExoPlayer::class.java.classLoader,
            arrayOf(ExoPlayer::class.java),
        ) { _, method, _ ->
            calls += method.name
            when (method.name) {
                "getCurrentMediaItem" -> item
                "getMediaItemCount" -> 1
                "getCurrentMediaItemIndex" -> 0
                "getCurrentPosition" -> 0L
                "getDuration" -> 90000L
                "getPlaybackState" -> Player.STATE_READY
                "getPlaybackParameters" -> PlaybackParameters(1f)
                "getPlayWhenReady",
                "isPlaying" -> true
                else ->
                    when (method.returnType) {
                        java.lang.Boolean.TYPE -> false
                        java.lang.Integer.TYPE -> 0
                        java.lang.Long.TYPE -> 0L
                        else -> null
                    }
            }
        } as ExoPlayer
    }
}
