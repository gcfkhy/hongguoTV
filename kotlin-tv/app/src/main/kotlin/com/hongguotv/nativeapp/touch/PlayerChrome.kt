// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp.touch

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Build
import android.view.View

/** Immersive system bars and the fullscreen orientation toggle. */
internal class FullscreenController(private val activity: Activity) {
    var forcedLandscape = false
        private set

    fun enterImmersive() {
        val flags =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        activity.window.decorView.systemUiVisibility = flags
        if (Build.VERSION.SDK_INT >= 21) {
            activity.window.statusBarColor = android.graphics.Color.TRANSPARENT
            activity.window.navigationBarColor = android.graphics.Color.TRANSPARENT
        }
    }

    fun exitImmersive() {
        activity.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
    }

    fun toggleLandscape() {
        forcedLandscape = !forcedLandscape
        activity.requestedOrientation =
            if (forcedLandscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        if (!forcedLandscape) exitImmersive()
        else enterImmersive()
    }
}

/** Snapshot passed from the activity's player engine to [PlayerChrome]. */
internal data class PlayerState(
    val positionMs: Long,
    val durationMs: Long,
    val playing: Boolean,
    val buffering: Boolean,
    val ready: Boolean,
    val error: Boolean,
)

internal interface PlayerChromeActions {
    fun state(): PlayerState

    fun toggle()

    fun seekTo(ms: Long)

    fun skip(direction: Int)

    fun setSpeedTemporarily(speed: Float?)

    fun showEpisodes()

    fun showSpeed()

    fun showMore()

    fun showComments()

    fun back()
}

/** Touch transport overlay plus tap, drag and long-press gestures. */
internal class PlayerChrome(context: Context, private val actions: PlayerChromeActions) {
    class Views(
        val hud: android.widget.LinearLayout,
        val title: android.widget.TextView,
        val status: android.widget.TextView,
        val progress: android.widget.SeekBar,
        val controls: android.widget.LinearLayout,
    )

    private val context = context
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private val fullscreen = (context as? Activity)?.let(::FullscreenController)

    private val title =
        android.widget.TextView(context).apply {
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(TouchTheme.text)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(TouchTheme.dp(context, 8), 0, TouchTheme.dp(context, 8), 0)
        }
    private val status =
        android.widget.TextView(context).apply {
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, TouchTheme.TEXT_SECONDARY)
            setTextColor(TouchTheme.text)
            gravity = android.view.Gravity.CENTER
            setPadding(TouchTheme.dp(context, 12), TouchTheme.dp(context, 4), TouchTheme.dp(context, 12), TouchTheme.dp(context, 4))
        }
    private val progress = android.widget.SeekBar(context).apply { max = 1000 }
    private val controls = android.widget.LinearLayout(context).apply { orientation = android.widget.LinearLayout.VERTICAL }
    private lateinit var playButton: android.widget.ImageButton
    private lateinit var bottomBar: android.widget.LinearLayout
    private var playerView: View? = null
    private var autoHide: Runnable? = null
    private var longPress: Runnable? = null
    private var pendingTap: Runnable? = null
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var dragging = false
    private var vertical = false
    private var longPressed = false
    private var seekPreview = 0L

    val views: Views

    init {
        val top =
            android.widget.LinearLayout(context).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(TouchTheme.dp(context, 4), TouchTheme.dp(context, 4), TouchTheme.dp(context, 4), 0)
                addView(iconButton(context, com.hongguotv.nativeapp.R.drawable.touch_ic_back, "返回") { actions.back() })
                addView(title, android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(iconButton(context, com.hongguotv.nativeapp.R.drawable.touch_ic_more, "更多") { actions.showMore() })
            }
        bottomBar =
            android.widget.LinearLayout(context).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(TouchTheme.dp(context, 4), TouchTheme.dp(context, 4), TouchTheme.dp(context, 4), TouchTheme.dp(context, 4))
                setBackgroundColor(android.graphics.Color.argb(140, 0, 0, 0))
                playButton = iconButton(context, com.hongguotv.nativeapp.R.drawable.touch_ic_play, "播放") { actions.toggle() }
                addView(playButton)
                addView(progress, android.widget.LinearLayout.LayoutParams(0, TouchTheme.dp(context, 32), 1f))
                addView(iconButton(context, com.hongguotv.nativeapp.R.drawable.touch_ic_previous, "上一集") { actions.skip(-1) })
                addView(iconButton(context, com.hongguotv.nativeapp.R.drawable.touch_ic_next, "下一集") { actions.skip(1) })
                addView(iconButton(context, com.hongguotv.nativeapp.R.drawable.touch_ic_list, "选集") { actions.showEpisodes() })
                addView(iconButton(context, com.hongguotv.nativeapp.R.drawable.touch_ic_speed, "倍速") { actions.showSpeed() })
                addView(iconButton(context, com.hongguotv.nativeapp.R.drawable.touch_ic_comment, "评论") { actions.showComments() })
                addView(iconButton(context, com.hongguotv.nativeapp.R.drawable.touch_ic_fullscreen, "全屏") { fullscreen?.toggleLandscape() })
            }
        val hud =
            android.widget.LinearLayout(context).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                visibility = View.GONE
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                addView(top, android.widget.LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT))
                addView(View(context), android.widget.LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
                addView(status, android.widget.LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT))
                addView(controls, android.widget.LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT))
                addView(bottomBar, android.widget.LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT))
            }
        views = Views(hud, title, status, progress, controls)
        progress.setOnSeekBarChangeListener(
            object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: android.widget.SeekBar?, value: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val duration = actions.state().durationMs
                        status.text = PlayerGestures.bubble(duration * value / 1000, duration)
                    }
                }

                override fun onStartTrackingTouch(bar: android.widget.SeekBar?) {
                    cancelAutoHide()
                }

                override fun onStopTrackingTouch(bar: android.widget.SeekBar?) {
                    val duration = actions.state().durationMs
                    if (duration > 0 && bar != null) actions.seekTo(duration * bar.progress / 1000)
                    scheduleAutoHide()
                }
            },
        )
    }

    fun install(root: android.widget.FrameLayout, playerView: android.view.View) {
        this.playerView = playerView
        root.addView(views.hud, android.widget.FrameLayout.LayoutParams(-1, -1))
        playerView.setOnTouchListener { _, event -> handleTouch(event) }
        playerView.isClickable = true
    }

    fun show() {
        views.hud.visibility = View.VISIBLE
        cancelAutoHide()
        scheduleAutoHide()
    }

    fun hide() {
        views.hud.visibility = View.GONE
        cancelAutoHide()
    }

    val visible: Boolean get() = views.hud.visibility == View.VISIBLE

    fun refresh() {
        val state = actions.state()
        playButton.setImageResource(
            if (state.playing) com.hongguotv.nativeapp.R.drawable.touch_ic_pause
            else com.hongguotv.nativeapp.R.drawable.touch_ic_play,
        )
        playButton.contentDescription = if (state.playing) "暂停" else "播放"
        if (state.error) {
            progress.visibility = View.GONE
            playButton.visibility = View.GONE
            views.controls.visibility = View.VISIBLE
        } else {
            progress.visibility = View.VISIBLE
            playButton.visibility = View.VISIBLE
            if (views.controls.childCount == 0) views.controls.visibility = View.GONE
        }
    }

    /** Window changes only re-layout; playback state is never touched. */
    fun relayout() {
        views.hud.requestLayout()
    }

    private fun scheduleAutoHide() {
        cancelAutoHide()
        if (!actions.state().playing) return
        val runnable =
            Runnable {
                if (actions.state().playing && !actions.state().error) views.hud.visibility = View.GONE
            }
        autoHide = runnable
        main.postDelayed(runnable, PlayerGestures.AUTO_HIDE_MS)
    }

    private fun cancelAutoHide() {
        autoHide?.let(main::removeCallbacks)
        autoHide = null
    }

    private fun cancelLongPress() {
        longPress?.let(main::removeCallbacks)
        longPress = null
    }

    private fun handleTouch(event: android.view.MotionEvent): Boolean {
        val view = playerView ?: return false
        val width = if (view.width > 0) view.width else context.resources.displayMetrics.widthPixels
        when (event.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                downTime = event.eventTime
                dragging = false
                vertical = false
                longPressed = false
                seekPreview = actions.state().positionMs
                cancelAutoHide()
                cancelLongPress()
                val press =
                    Runnable {
                        longPressed = true
                        actions.setSpeedTemporarily(PlayerGestures.LONG_PRESS_SPEED)
                        views.hud.visibility = View.VISIBLE
                    }
                longPress = press
                main.postDelayed(press, 500)
            }
            android.view.MotionEvent.ACTION_MOVE -> {
                val dx = event.x - downX
                val dy = event.y - downY
                if (longPressed) return true
                val slop = android.view.ViewConfiguration.get(context).scaledTouchSlop.toFloat()
                if (!dragging && PlayerGestures.isHorizontalDrag(dx, dy, slop)) dragging = true
                if (dragging) {
                    vertical = false
                    val state = actions.state()
                    seekPreview = PlayerGestures.seekTarget(state.positionMs, state.durationMs, dx, width)
                    status.text = PlayerGestures.bubble(seekPreview, state.durationMs)
                    views.hud.visibility = View.VISIBLE
                    return true
                }
                if (ky(dy) > slop) vertical = true
            }
            android.view.MotionEvent.ACTION_UP -> {
                cancelLongPress()
                if (longPressed) {
                    longPressed = false
                    actions.setSpeedTemporarily(null)
                    scheduleAutoHide()
                    return true
                }
                if (dragging) {
                    dragging = false
                    actions.seekTo(seekPreview)
                    scheduleAutoHide()
                    return true
                }
                val dy = event.y - downY
                val elapsed = (event.eventTime - downTime).coerceAtLeast(1L)
                val direction =
                    if (vertical) com.hongguotv.nativeapp.PlaybackInput.verticalFlingDelta(dy * 1000f / elapsed) else 0
                if (direction != 0) {
                    actions.skip(direction)
                    scheduleAutoHide()
                    return true
                }
                onTap()
            }
            android.view.MotionEvent.ACTION_CANCEL -> {
                cancelLongPress()
                if (longPressed) {
                    longPressed = false
                    actions.setSpeedTemporarily(null)
                }
                dragging = false
                scheduleAutoHide()
            }
        }
        return true
    }

    private fun ky(dy: Float): Float = kotlin.math.abs(dy)

    private fun onTap() {
        val now = android.os.SystemClock.uptimeMillis()
        pendingTap?.let { main.removeCallbacks(it); pendingTap = null; actions.toggle(); return }
        val runnable =
            Runnable {
                pendingTap = null
                if (visible) hide() else show()
            }
        pendingTap = runnable
        main.postDelayed(runnable, 300)
    }
}
