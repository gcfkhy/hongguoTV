// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp.touch

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.hongguotv.core.DramaCategory
import com.hongguotv.core.DramaFeedState
import com.hongguotv.core.Series
import com.hongguotv.nativeapp.FeedBackend
import com.hongguotv.nativeapp.PlaybackInput
import com.hongguotv.nativeapp.R
import com.hongguotv.nativeapp.TrialClip
import com.hongguotv.nativeapp.TrialPlayback

/** Home tab: a full-screen, vertically swiped trial feed. */
internal class HomePage(
    private val host: TouchHost,
    private val state: DramaFeedState,
    backend: FeedBackend,
    playback: TrialPlayback,
    autoPreview: Boolean = true,
) : TouchPage {
    private val context = host.activity
    private val root = FrameLayout(context)
    internal val stage = FrameLayout(context)

    private val title =
        TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTextColor(TouchTheme.text)
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
    private val meta =
        TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, TouchTheme.TEXT_SECONDARY)
            setTextColor(TouchTheme.accent)
            setPadding(0, TouchTheme.dp(context, 6), 0, TouchTheme.dp(context, 6))
        }
    private val intro =
        TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, TouchTheme.TEXT_BODY)
            setTextColor(TouchTheme.muted)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            contentDescription = "简介"
            setOnClickListener { maxLines = if (maxLines == 2) 6 else 2 }
        }
    private val progress =
        ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progressTintList = android.content.res.ColorStateList.valueOf(TouchTheme.accent)
        }
    private val caption =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                TouchTheme.dp(context, 16),
                TouchTheme.dp(context, 36),
                TouchTheme.dp(context, 84),
                TouchTheme.dp(context, 20),
            )
            background =
                GradientDrawable(
                    GradientDrawable.Orientation.BOTTOM_TOP,
                    intArrayOf(Color.argb(238, 11, 15, 21), Color.TRANSPARENT),
                )
            addView(title)
            addView(meta)
            addView(intro)
            addView(progress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, TouchTheme.dp(context, 3)).apply {
                topMargin = TouchTheme.dp(context, 12)
            })
        }

    private val chips =
        SegmentedControl(context, listOf("推荐", "漫剧", "真人剧"), if (state.recommendation) 0 else if (state.category == DramaCategory.COMIC) 1 else 2) {
            selectCategory(it)
        }

    private val favoriteButton: ImageButton =
        iconButton(context, R.drawable.touch_ic_heart, "收藏") { toggleFavorite() }
    private val episodesButton: ImageButton =
        iconButton(context, R.drawable.touch_ic_list, "选集") { openDetail() }
    private val commentsButton: ImageButton =
        iconButton(context, R.drawable.touch_ic_comment, "评论") { openDetail() }
    private val watchButton =
        TextView(context).apply {
            text = "看全集"
            contentDescription = "看全集"
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, TouchTheme.TEXT_BODY)
            setTextColor(TouchTheme.background)
            typeface = Typeface.DEFAULT_BOLD
            minWidth = TouchTheme.dp(context, 64)
            minHeight = TouchTheme.dp(context, TouchTheme.MIN_TARGET_DP)
            setPadding(TouchTheme.dp(context, 16), 0, TouchTheme.dp(context, 16), 0)
            background = TouchTheme.pressable(context, TouchTheme.accent, 24)
            setOnClickListener { openDetail() }
        }
    private val actions =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(favoriteButton)
            addView(episodesButton)
            addView(commentsButton)
            addView(watchButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, TouchTheme.dp(context, TouchTheme.MIN_TARGET_DP)).apply {
                topMargin = TouchTheme.dp(context, 12)
            })
        }

    private val pauseIcon =
        ImageView(context).apply {
            setImageResource(R.drawable.touch_ic_play)
            setColorFilter(TouchTheme.text)
            contentDescription = "播放"
            visibility = View.GONE
            setPadding(TouchTheme.dp(context, 16), TouchTheme.dp(context, 16), TouchTheme.dp(context, 16), TouchTheme.dp(context, 16))
            background = TouchTheme.pressable(context, Color.argb(140, 0, 0, 0), 40)
            setOnClickListener { controller.togglePause() }
        }

    private val statusText =
        TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, TouchTheme.TEXT_BODY)
            setTextColor(TouchTheme.text)
            gravity = Gravity.CENTER
            setPadding(TouchTheme.dp(context, 16), TouchTheme.dp(context, 12), TouchTheme.dp(context, 16), TouchTheme.dp(context, 12))
        }
    private val retryButton =
        chip(context, "重试", false) { controller.togglePause() }
    private val statusWatchButton =
        chip(context, "看全集", false) { openDetail() }
    private val statusPanel =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            visibility = View.GONE
            setPadding(TouchTheme.dp(context, 20), TouchTheme.dp(context, 16), TouchTheme.dp(context, 20), TouchTheme.dp(context, 16))
            background = TouchTheme.pressable(context, TouchTheme.raised, TouchTheme.SHEET_RADIUS_DP)
            addView(statusText)
            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                    addView(retryButton)
                    addView(statusWatchButton)
                },
            )
        }

    private var statusMessage: String? = null
    private var playing = false
    private var started = false

    internal val controller: FeedController =
        FeedController(state, backend, playback, autoPreview, object : FeedController.Listener {
            override fun onSeries(series: Series?, index: Int) = renderSeries()

            override fun onStatus(message: String?) {
                statusMessage = message
                renderStatus()
            }

            override fun onPlaying(playing: Boolean) {
                this@HomePage.playing = playing
                paint()
            }

            override fun onProgress(positionMs: Long, durationMs: Long) {
                progress.progress =
                    if (durationMs > 0) (positionMs * 1000 / durationMs).toInt().coerceIn(0, 1000) else 0
            }

            override fun onTrial(clip: TrialClip?) = renderSeries()

            override fun onChange() = paint()
        })

    private val gestures =
        GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent) = true

                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    controller.togglePause()
                    return true
                }

                override fun onFling(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    velocityX: Float,
                    velocityY: Float,
                ): Boolean {
                    val delta = PlaybackInput.verticalFlingDelta(velocityY)
                    if (delta == 0) return false
                    if (delta < 0) controller.previous() else controller.next()
                    animateSnap(delta)
                    return true
                }
            },
        )

    init {
        root.setBackgroundColor(TouchTheme.background)
        root.addView(
            stage,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        controller.attachStage(stage)
        stage.isClickable = true
        stage.contentDescription = "推荐视频"
        stage.setOnTouchListener { _, event -> gestures.onTouchEvent(event) }
        root.addView(
            chips,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                topMargin = TouchTheme.dp(context, 12)
            },
        )
        root.addView(
            caption,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM),
        )
        root.addView(
            actions,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END or Gravity.CENTER_VERTICAL).apply {
                marginEnd = TouchTheme.dp(context, 12)
            },
        )
        root.addView(
            pauseIcon,
            FrameLayout.LayoutParams(TouchTheme.dp(context, 88), TouchTheme.dp(context, 88), Gravity.CENTER),
        )
        root.addView(
            statusPanel,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER),
        )
        renderSeries()
        paint()
    }

    override val view: View get() = root

    override fun onShow() {
        if (!started) {
            started = true
            controller.start(true)
        } else controller.setForeground(true)
        paint()
    }

    override fun onHide() {
        controller.setForeground(false)
    }

    override fun onBack() = false

    override fun close() {
        controller.close()
    }

    private fun renderSeries() {
        val series = controller.current
        title.text = series?.title ?: "发现下一部好剧"
        val clip = controller.clip
        meta.text =
            when {
                series == null -> "第一集试播"
                clip != null -> "第一集试播 · 共 ${clip.episodeCount} 集"
                else -> listOf("第一集试播", series.badge, series.tags).filter(String::isNotBlank).joinToString(" · ")
            }
        intro.text =
            series?.description?.ifBlank { "喜欢就看全集，上滑换下一部。" }
                ?: "上滑刷下一部，点画面暂停，点看全集开始追。"
        paint()
    }

    private fun paint() {
        val series = controller.current
        val collected = series != null && host.library.favorite(series.id)
        favoriteButton.setImageResource(
            if (collected) R.drawable.touch_ic_heart_filled else R.drawable.touch_ic_heart,
        )
        favoriteButton.setColorFilter(if (collected) TouchTheme.accent else TouchTheme.text)
        favoriteButton.contentDescription = if (collected) "取消收藏" else "收藏"
        watchButton.alpha = if (series != null) 1f else .4f
        episodesButton.alpha = if (series != null) 1f else .4f
        commentsButton.alpha = if (series != null) 1f else .4f
        chips.select(
            when {
                state.recommendation -> 0
                state.category == DramaCategory.COMIC -> 1
                else -> 2
            },
        )
        pauseIcon.visibility =
            if (!playing && controller.clip != null) View.VISIBLE else View.GONE
        root.keepScreenOn = controller.active && controller.clip != null && playing
        renderStatus()
    }

    private fun renderStatus() {
        val message = statusMessage
        statusPanel.visibility = if (message.isNullOrBlank()) View.GONE else View.VISIBLE
        statusText.text = message ?: ""
        val failed = controller.isFailed
        retryButton.visibility = if (failed) View.VISIBLE else View.GONE
        statusWatchButton.visibility = if (failed) View.VISIBLE else View.GONE
    }

    private fun selectCategory(index: Int) {
        controller.setCategory(
            when (index) {
                1 -> DramaCategory.COMIC
                2 -> DramaCategory.REAL
                else -> null
            },
        )
    }

    private fun toggleFavorite() {
        val series = controller.current ?: return
        host.toggleLocalFavorite(series)
        paint()
    }

    private fun openDetail() {
        val series = controller.current ?: return
        host.openDetail(series)
    }

    private fun animateSnap(direction: Int) {
        val offset = -direction * TouchTheme.dp(context, 28)
        caption.animate().translationY(offset.toFloat()).alpha(0.5f).setDuration(125).withEndAction {
            caption.animate().translationY(0f).alpha(1f).setDuration(125).start()
        }.start()
    }
}
