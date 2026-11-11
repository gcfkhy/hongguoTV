// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp.touch

import android.graphics.Color
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.hongguotv.core.Detail
import com.hongguotv.core.RelatedWork
import com.hongguotv.core.ResumePlayback
import com.hongguotv.core.Series
import com.hongguotv.nativeapp.ArtworkLoader
import com.hongguotv.nativeapp.R
import com.hongguotv.nativeapp.WatchProgress

/** Series detail page with grouped episode picker, synopsis and related works. */
internal class DetailPage(
    private val host: TouchHost,
    private val detail: Detail,
    private val progress: WatchProgress?,
    private val watched: Boolean,
    private val currentEpisode: Int,
    private val actions: Actions,
    private val related: (String) -> List<RelatedWork> = { host.repository.related(it) },
) : TouchPage {
    interface Actions {
        fun play(index: Int, position: Long)

        fun back()

        fun openRelated(series: Series)

        fun comments()
    }

    private val context = host.activity
    private val episodes = detail.episodes
    private val target =
        if (progress != null)
            ResumePlayback.target(episodes, progress.episodeId, progress.position, progress.completed)
        else null
    private val groups =
        if (episodes.isEmpty()) emptyList()
        else episodes.indices.step(20).map { start -> start to minOf(start + 19, episodes.lastIndex) }
    private var selectedGroup =
        groups.indexOfFirst { currentEpisode in it.first..it.second }.takeIf { it >= 0 } ?: 0

    private val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val grid =
        GridView(context).apply {
            numColumns = 5
            columnWidth = TouchTheme.dp(context, 56)
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            horizontalSpacing = TouchTheme.dp(context, 6)
            verticalSpacing = TouchTheme.dp(context, 6)
            clipToPadding = false
            setSelector(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            onItemClickListener =
                android.widget.AdapterView.OnItemClickListener { _, _, position, _ ->
                    val episode = groupRange().first + position
                    val positionMs =
                        if (progress != null && progress.episodeIndex == episode && !progress.completed) progress.position
                        else 0L
                    actions.play(episode, positionMs)
                }
        }
    private val groupChips = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    private val synopsis =
        TextView(context).apply {
            text = detail.series.description
            setTextSize(TypedValue.COMPLEX_UNIT_SP, TouchTheme.TEXT_BODY)
            setTextColor(TouchTheme.muted)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setOnClickListener {
                maxLines = if (maxLines == 2) 20 else 2
                ellipsize = if (maxLines == 2) TextUtils.TruncateAt.END else null
            }
        }
    private val relatedRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    private val relatedScroll =
        HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(relatedRow)
        }
    private val relatedSection =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            addView(sectionTitle(context, "相关作品"))
            addView(relatedScroll)
        }
    private val favoriteButton: ImageButton = iconButton(context, R.drawable.touch_ic_heart, "收藏") { toggleFavorite() }

    init {
        root.setBackgroundColor(TouchTheme.background)
        root.addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(TouchTheme.dp(context, 8), TouchTheme.dp(context, 4), TouchTheme.dp(context, 8), TouchTheme.dp(context, 4))
                addView(iconButton(context, R.drawable.touch_ic_back, "返回") { actions.back() })
                addView(
                    TextView(context).apply {
                        text = detail.series.title
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                        setTextColor(TouchTheme.text)
                        maxLines = 2
                        ellipsize = TextUtils.TruncateAt.END
                        setPadding(TouchTheme.dp(context, 8), 0, 0, 0)
                    },
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
                )
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )

        val scroll = ScrollView(context).apply { isVerticalScrollBarEnabled = false }
        val content =
            if (WindowClass.of(context) == WindowClass.Expanded)
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    addView(infoBlock(), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    addView(episodeBlock(), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                }
            else
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(infoBlock())
                    addView(episodeBlock())
                    addView(relatedSection)
                }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(bottomBar(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, TouchTheme.dp(context, 64)))

        buildGroupChips()
        grid.adapter = EpisodeAdapter()
        updateGridHeight()
        host.work(
            { related(detail.series.id) },
            { works -> renderRelated(works) },
            { },
        )
        paintFavorite()
    }

    override val view: View get() = root

    internal val episodeGrid: GridView get() = grid

    override fun onShow() = Unit

    override fun onHide() = Unit

    override fun onBack() = false

    override fun close() = Unit

    private fun infoBlock(): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(TouchTheme.dp(context, 16), TouchTheme.dp(context, 8), TouchTheme.dp(context, 16), TouchTheme.dp(context, 8))
            addView(
                TextView(context).apply {
                    text = "${episodes.size} 集" + if (detail.series.tags.isNotBlank()) " · ${detail.series.tags}" else ""
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, TouchTheme.TEXT_SECONDARY)
                    setTextColor(TouchTheme.muted)
                },
            )
            addView(synopsis)
            addView(
                TextView(context).apply {
                    text = "评论"
                    contentDescription = "评论"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, TouchTheme.TEXT_BODY)
                    setTextColor(TouchTheme.accent)
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, TouchTheme.dp(context, 10), 0, TouchTheme.dp(context, 10))
                    setOnClickListener { actions.comments() }
                },
            )
        }

    private fun episodeBlock(): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(TouchTheme.dp(context, 12), TouchTheme.dp(context, 4), TouchTheme.dp(context, 12), TouchTheme.dp(context, 12))
            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(sectionTitle(context, "选集"), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    if (episodes.size > 40) {
                        addView(
                            TextView(context).apply {
                                text = "跳转"
                                contentDescription = "跳转"
                                setTextSize(TypedValue.COMPLEX_UNIT_SP, TouchTheme.TEXT_SECONDARY)
                                setTextColor(TouchTheme.accent)
                                setPadding(TouchTheme.dp(context, 12), TouchTheme.dp(context, 12), TouchTheme.dp(context, 12), TouchTheme.dp(context, 12))
                                setOnClickListener {
                                    host.sheet.grid("选集", episodes.size, currentEpisode, emptyMap()) { index ->
                                        actions.play(index, 0)
                                    }
                                }
                            },
                        )
                    }
                },
            )
            addView(
                HorizontalScrollView(context).apply {
                    isHorizontalScrollBarEnabled = false
                    addView(groupChips)
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, TouchTheme.dp(context, 52)),
            )
            addView(grid)
            if (WindowClass.of(context) == WindowClass.Expanded) addView(relatedSection)
        }

    private fun bottomBar(): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(TouchTheme.dp(context, 16), TouchTheme.dp(context, 8), TouchTheme.dp(context, 16), TouchTheme.dp(context, 8))
            addView(favoriteButton)
            addView(
                TextView(context).apply {
                    text = mainLabel()
                    contentDescription = mainLabel()
                    gravity = Gravity.CENTER
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    setTextColor(Color.WHITE)
                    background = TouchTheme.pressable(context, TouchTheme.accent, TouchTheme.MIN_TARGET_DP / 2)
                    setOnClickListener { mainAction() }
                },
                LinearLayout.LayoutParams(0, TouchTheme.dp(context, 48), 1f).apply {
                    marginStart = TouchTheme.dp(context, 12)
                },
            )
        }

    private fun mainLabel(): String =
        when {
            watched -> "重新观看"
            target != null -> "继续第 ${target.index + 1} 集"
            else -> "开始观看"
        }

    private fun mainAction() {
        if (watched) actions.play(0, 0)
        else target?.let { actions.play(it.index, it.position) } ?: actions.play(0, 0)
    }

    private fun toggleFavorite() {
        host.toggleLocalFavorite(detail.series)
        paintFavorite()
    }

    private fun paintFavorite() {
        val collected = host.library.favorite(detail.series.id)
        favoriteButton.setImageResource(if (collected) R.drawable.touch_ic_heart_filled else R.drawable.touch_ic_heart)
        favoriteButton.setColorFilter(if (collected) TouchTheme.accent else TouchTheme.text)
        favoriteButton.contentDescription = if (collected) "取消收藏" else "收藏"
    }

    private fun buildGroupChips() {
        groupChips.removeAllViews()
        groups.forEachIndexed { index, (start, end) ->
            groupChips.addView(
                chip(context, "${start + 1}–${end + 1}", index == selectedGroup) {
                    selectedGroup = index
                    buildGroupChips()
                    (grid.adapter as EpisodeAdapter).notifyDataSetChanged()
                    updateGridHeight()
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, TouchTheme.dp(context, 48)).apply {
                    marginEnd = TouchTheme.dp(context, 6)
                },
            )
        }
    }

    private fun groupRange(): IntRange {
        val group = groups.getOrNull(selectedGroup) ?: return IntRange.EMPTY
        return group.first..group.second
    }

    private fun updateGridHeight() {
        val rows = ((groupRange().count() + 4) / 5).coerceAtLeast(1)
        grid.layoutParams =
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                rows * TouchTheme.dp(context, 52),
            )
    }

    private fun renderRelated(works: List<RelatedWork>) {
        relatedRow.removeAllViews()
        if (works.isEmpty()) {
            relatedSection.visibility = View.GONE
            return
        }
        relatedSection.visibility = View.VISIBLE
        works.forEach { work ->
            val card =
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    contentDescription = work.series.title
                    setOnClickListener { actions.openRelated(work.series) }
                }
            val cover =
                ImageView(context).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    contentDescription = work.series.title
                }
            card.addView(cover, LinearLayout.LayoutParams(TouchTheme.dp(context, 96), TouchTheme.dp(context, 130)))
            card.addView(
                TextView(context).apply {
                    text = work.series.title
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, TouchTheme.TEXT_MIN)
                    setTextColor(TouchTheme.text)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                },
            )
            host.artwork.load(cover, work.series.cover, ArtworkLoader.PRIORITY_VISIBLE)
            relatedRow.addView(
                card,
                LinearLayout.LayoutParams(TouchTheme.dp(context, 96), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    marginEnd = TouchTheme.dp(context, 8)
                },
            )
        }
    }

    private inner class EpisodeAdapter : BaseAdapter() {
        override fun getCount() = groupRange().count()

        override fun getItem(position: Int): Any = groupRange().first + position

        override fun getItemId(position: Int) = (groupRange().first + position).toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val episode = groupRange().first + position
            val cell =
                (convertView as? TextView)
                    ?: TextView(context).apply {
                        gravity = Gravity.CENTER
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, TouchTheme.TEXT_BODY)
                        minHeight = TouchTheme.dp(context, TouchTheme.MIN_TARGET_DP)
                    }
            val marks = mutableListOf<String>()
            if (episode == currentEpisode) marks += "当前"
            if (progress != null && episode == progress.episodeIndex && episode != currentEpisode) {
                marks += if (progress.completed) "已看" else "上次"
            } else if (progress != null && episode < progress.episodeIndex) {
                marks += "已看"
            }
            cell.text = "${episode + 1}" + if (marks.isEmpty()) "" else "\n${marks.joinToString(" · ")}"
            cell.contentDescription = "第 ${episode + 1} 集"
            cell.setTextColor(if (episode == currentEpisode) TouchTheme.accent else TouchTheme.text)
            cell.background =
                TouchTheme.pressable(
                    context,
                    if (episode == currentEpisode) Color.argb(50, 255, 120, 68) else TouchTheme.raised,
                    8,
                )
            return cell
        }
    }
}
