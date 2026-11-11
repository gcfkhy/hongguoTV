// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp

// 电视首页试播视图：组装分类栏、视频区和操作栏，并管理遥控器焦点与按键。
// 加载、缓存、预取与恢复逻辑由 touch/FeedController 负责，本类只做渲染与遥控器交互。

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.*
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.hongguotv.core.*
import com.hongguotv.nativeapp.touch.FeedController
import java.io.Closeable

/** 独立首页试播，不写入本机观看记录，也不上传账户进度。 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class HomeFeedScreen(
    private val activity: Activity,
    private val state: DramaFeedState,
    private val nav: List<View>,
    private val watch: (Series) -> Unit,
    private val favorite: (Series, View) -> Unit,
    private val favoriteLabel: (String) -> String,
    backend: FeedBackend,
    playback: TrialPlayback,
    autoPreview: Boolean = true,
) : LinearLayout(activity), Closeable {
    private val stage = FrameLayout(activity)
    private val title = label("发现下一部好剧", 25f)
    private val meta = label("第一集试播", 13f, TvStyle.accent)
    private val intro = label("喜欢这部作品，就选择观看全集。", 15f, TvStyle.muted)
    private val status = label("正在准备热门作品…", 15f)
    private val tag = label("热门发现", 12f, TvStyle.accent)
    private val actions = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
    private val buttons = mutableListOf<TextView>()
    private val pauseButton: TextView
    private val watchButton: TextView
    private lateinit var favoriteButton: TextView
    private val recommendButton: TextView
    private val comicButton: TextView
    private val realButton: TextView
    private var closed = false

    private val controller: FeedController =
        FeedController(state, backend, playback, autoPreview, object : FeedController.Listener {
            override fun onSeries(series: Series?, index: Int) = publishSeries()

            override fun onStatus(message: String?) {
                if (message == null) status.visibility = View.GONE
                else {
                    status.visibility = View.VISIBLE
                    status.text = message
                }
            }

            override fun onPlaying(playing: Boolean) = paintActions()

            override fun onProgress(positionMs: Long, durationMs: Long) = Unit

            override fun onTrial(clip: TrialClip?) = publishSeries()

            override fun onChange() = paintActions()

            override fun onEnded() {
                if (controller.active && stage.hasFocus()) showActions()
            }
        })

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    private fun label(value: String, size: Float, color: Int = TvStyle.text) =
        TextView(activity).apply {
            text = value
            textSize = size
            setTextColor(color)
            includeFontPadding = false
        }

    private fun lp(w: Int = -1, h: Int = -2) = LayoutParams(w, h)

    private fun button(value: String, action: () -> Unit) =
        label(value, 14f).apply {
            id = View.generateViewId()
            enableRemoteFocus()
            isClickable = true
            gravity = Gravity.CENTER
            minHeight = dp(44)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = TvStyle.card(activity, false)
            setOnFocusChangeListener { _, _ -> paintButton(this) }
            setOnClickListener { action() }
        }

    private fun paintButton(button: TextView) {
        button.background = TvStyle.card(activity, button.hasFocus(), button.isSelected)
        button.setTextColor(if (button.isSelected) TvStyle.accent else TvStyle.text)
        button.setTypeface(null, if (button.isSelected) Typeface.BOLD else Typeface.NORMAL)
    }

    // 组装首页布局和点击事件；分类栏、内容区、操作栏按纵向排列。
    init {
        orientation = VERTICAL
        stage.id = View.generateViewId()
        val types = LinearLayout(activity)
        recommendButton = button("推荐") { controller.setCategory(null) }
        comicButton = button("漫剧") { changeType(DramaCategory.COMIC) }
        realButton = button("真人剧") { changeType(DramaCategory.REAL) }
        val refresh =
            button("换一批") {
                controller.refresh()
            }
        val typeChoices = listOf(recommendButton, comicButton, realButton, refresh)
        typeChoices.forEachIndexed { i, b ->
            types.addView(
                b,
                lp(0, dp(40)).apply {
                    weight = 1f
                    rightMargin = dp(8)
                },
            )
            b.nextFocusUpId = nav.first().id
            b.nextFocusDownId = stage.id
            b.nextFocusLeftId = typeChoices.getOrNull(i - 1)?.id ?: b.id
            b.nextFocusRightId = typeChoices.getOrNull(i + 1)?.id ?: b.id
        }
        stage.enableRemoteFocus()
        stage.isClickable = true
        stage.setPadding(dp(3), dp(3), dp(3), dp(3))
        stage.background = TvStyle.shape(activity, Color.BLACK, radius = 10)
        (playback.view as? PlayerView)?.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        controller.attachStage(stage)
        tag.setPadding(dp(12), dp(8), dp(12), dp(8))
        tag.background = TvStyle.shape(activity, Color.argb(210, 11, 15, 21))
        stage.addView(
            tag,
            FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply {
                setMargins(dp(10), dp(10), dp(10), 0)
            },
        )
        status.gravity = Gravity.CENTER
        status.setPadding(dp(14), dp(12), dp(14), dp(12))
        status.background = TvStyle.shape(activity, Color.argb(215, 11, 15, 21))
        stage.addView(
            status,
            FrameLayout.LayoutParams(-1, -2, Gravity.CENTER).apply {
                setMargins(dp(16), dp(16), dp(16), dp(16))
            },
        )
        title.typeface = Typeface.DEFAULT_BOLD
        title.maxLines = 3
        title.ellipsize = TextUtils.TruncateAt.END
        meta.maxLines = 2
        meta.setPadding(0, dp(8), 0, dp(8))
        intro.maxLines = 6
        intro.ellipsize = TextUtils.TruncateAt.END
        pauseButton = button("暂停试播") { controller.togglePause() }
        watchButton =
            button("观看全集") {
                state.current?.let {
                    controller.setForeground(false)
                    watch(it)
                }
            }
        favoriteButton =
            button("账户收藏") {
                state.current?.let {
                    state.userPaused = true
                    controller.pausePlayback()
                    paintActions()
                    favorite(it, favoriteButton)
                }
            }
        buttons += listOf(pauseButton, watchButton, favoriteButton)
        buttons.forEachIndexed { index, b ->
            actions.addView(
                b,
                lp(0, dp(44)).apply {
                    weight = 1f
                    if (index < buttons.lastIndex) rightMargin = dp(10)
                },
            )
            b.nextFocusLeftId = buttons.getOrNull(index - 1)?.id ?: b.id
            b.nextFocusRightId = buttons.getOrNull(index + 1)?.id ?: b.id
            b.nextFocusUpId = stage.id
            b.nextFocusDownId = b.id
        }
        actions.setPadding(0, dp(10), 0, 0)
        assembleFeedLayout(types)
        stage.nextFocusLeftId = stage.id
        stage.nextFocusRightId = stage.id
        stage.nextFocusUpId = stage.id
        stage.nextFocusDownId = stage.id
        nav.forEach {
            it.nextFocusDownId =
                (if (state.recommendation) recommendButton
                    else if (state.category == DramaCategory.COMIC) comicButton else realButton)
                    .id
        }
        stage.setOnFocusChangeListener { _, focused ->
            stage.background =
                TvStyle.shape(
                    activity,
                    Color.BLACK,
                    if (focused) TvVisual.focusedValue else TvStyle.outline,
                )
        }
        bindStageInput()
        describe()
        paintActions()
    }

    private fun assembleFeedLayout(types: LinearLayout) {
        addView(types, lp(-1, dp(48)))
        val content = LinearLayout(activity)
        content.addView(
            stage,
            lp(0, -1).apply {
                weight = 2.2f
                rightMargin = dp(22)
            },
        )
        val info =
            LinearLayout(activity).apply {
                orientation = VERTICAL
                setPadding(0, dp(14), 0, dp(10))
            }
        info.addView(title)
        info.addView(meta)
        info.addView(intro)
        info.addView(Space(activity), lp(1, 0).apply { weight = 1f })
        info.addView(
            label("↑ ↓  换一部\n确定  打开操作\n返回  顶部导航", 13f, TvStyle.muted).apply {
                setLineSpacing(dp(6).toFloat(), 1f)
            },
        )
        content.addView(info, lp(0, -1).apply { weight = 1f })
        addView(content, lp(-1, 0).apply { weight = 1f })
        actions.visibility = View.GONE
        addView(actions)
    }

    private fun bindStageInput() {
        stage.setOnKeyListener { _, key, event ->
            when (key) {
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0)
                        if (key == KeyEvent.KEYCODE_DPAD_UP) controller.previous() else controller.next()
                    true
                }
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER -> {
                    if (event.action == KeyEvent.ACTION_UP) showActions()
                    true
                }
                else -> false
            }
        }
        stage.setOnClickListener { showActions() }
    }

    // 设置进入首页时的初始焦点，并在应用位于前台时启动试播流程。
    fun start(foreground: Boolean, focusNav: Boolean) {
        if (focusNav) nav[0].requestFocus() else stage.requestFocus()
        controller.start(foreground)
    }

    fun resume() = controller.setForeground(true)

    fun pause() = controller.setForeground(false)

    fun back(): Boolean {
        if (actions.visibility != View.VISIBLE) return false
        actions.visibility = View.GONE
        stage.requestFocus()
        return true
    }

    fun refreshAccountLabel() {
        if (!closed) paintActions()
    }

    // 遥控器确定键或点击视频后显示操作栏，并将焦点移到可用操作。
    private fun showActions() {
        actions.visibility = View.VISIBLE
        paintActions()
        (if (watchButton.isEnabled) watchButton else pauseButton).requestFocus()
    }

    private fun paintActions() {
        pauseButton.text =
            when {
                controller.recovering -> "立即重试试播"
                controller.loading -> "正在准备试播…"
                controller.isFailed || state.current == null -> "重试试播"
                state.ended -> "重新试播"
                state.userPaused -> "继续试播"
                else -> "暂停试播"
            }
        watchButton.isEnabled = state.current != null
        favoriteButton.isEnabled = state.current != null
        favoriteButton.text = state.current?.let { favoriteLabel(it.id) } ?: "账户收藏"
        recommendButton.isSelected = state.recommendation
        comicButton.isSelected = !state.recommendation && state.category == DramaCategory.COMIC
        realButton.isSelected = !state.recommendation && state.category == DramaCategory.REAL
        paintButton(recommendButton)
        paintButton(comicButton)
        paintButton(realButton)
        stage.keepScreenOn =
            controller.active && controller.clip != null && !state.userPaused && !state.ended &&
                !controller.isFailed
    }

    private fun publishSeries() {
        describe()
        paintActions()
    }

    private fun describe() {
        val series = state.current
        title.text = series?.title ?: "发现下一部好剧"
        val clip = controller.clip
        val tags = series?.tags?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""
        meta.text =
            when {
                series == null -> "第一集试播"
                clip != null && clip.trialEpisodeIndex > 1 ->
                    "第一集暂不支持电视播放 · 试播第 ${clip.trialEpisodeIndex} 集 · 共 ${clip.episodeCount} 集$tags"
                clip != null -> "第一集试播 · 共 ${clip.episodeCount} 集$tags"
                else ->
                    listOf("第一集试播", series.badge, series.tags)
                        .filter(String::isNotBlank)
                        .joinToString(" · ")
            }
        intro.text =
            series?.description?.ifBlank { "先试试看，喜欢这部作品就选择观看全集。" }
                ?: "选择漫剧或真人剧，上下换一部，找到想追的故事。"
        tag.text = if (state.recommendation) "推荐 · 第一集试播" else "${state.category.label}推荐 · 第一集试播"
        paintActions()
    }

    private fun changeType(category: DramaCategory) {
        if (!state.recommendation && state.category == category) return
        controller.setCategory(category)
        actions.visibility = View.GONE
        stage.requestFocus()
    }

    override fun close() {
        if (closed) return
        closed = true
        stage.keepScreenOn = false
        controller.close()
    }
}
