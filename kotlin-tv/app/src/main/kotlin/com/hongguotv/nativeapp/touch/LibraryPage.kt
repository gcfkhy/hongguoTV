// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp.touch

import android.graphics.Color
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import com.hongguotv.core.CloudReservation
import com.hongguotv.core.Series
import com.hongguotv.nativeapp.ArtworkLoader
import com.hongguotv.nativeapp.WatchProgress

/** Library tab: local/account favorites, watch history and cloud reservations. */
internal class LibraryPage(
    private val host: TouchHost,
    private val segment: Int,
    private val onDiscover: () -> Unit = {},
) : TouchPage {
    private val context = host.activity
    private val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    private val segments =
        SegmentedControl(context, listOf("收藏", "观看历史", "账户预约"), segment) { selectSegment(it) }
    private val sourceRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    private val contentHost = FrameLayout(context)

    internal val favoritesGrid = PosterGridView(context, host)
    internal val historyGrid =
        GridView(context).apply {
            numColumns = WindowClass.gridColumns(context.resources.configuration.screenWidthDp)
            columnWidth = TouchTheme.dp(context, 120)
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            horizontalSpacing = TouchTheme.dp(context, 12)
            verticalSpacing = TouchTheme.dp(context, 12)
            clipToPadding = false
            setPadding(
                TouchTheme.dp(context, 16),
                TouchTheme.dp(context, 8),
                TouchTheme.dp(context, 16),
                TouchTheme.dp(context, 16),
            )
            setSelector(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            visibility = View.GONE
        }
    private val reservations = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val reservationScroll =
        ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            visibility = View.GONE
            addView(reservations)
        }

    private var current = segment.coerceIn(0, 2)
    private var accountSource = false
    private var stateView: View? = null
    private var historyItems: List<WatchProgress> = emptyList()

    init {
        root.setBackgroundColor(TouchTheme.background)
        root.addView(
            segments,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = TouchTheme.dp(context, 16)
                rightMargin = TouchTheme.dp(context, 16)
                topMargin = TouchTheme.dp(context, 8)
            },
        )
        root.addView(
            sourceRow,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, TouchTheme.dp(context, 52)).apply {
                leftMargin = TouchTheme.dp(context, 12)
                rightMargin = TouchTheme.dp(context, 12)
            },
        )
        root.addView(contentHost, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        contentHost.addView(favoritesGrid, FrameLayout.LayoutParams(-1, -1))
        contentHost.addView(historyGrid, FrameLayout.LayoutParams(-1, -1))
        contentHost.addView(reservationScroll, FrameLayout.LayoutParams(-1, -1))

        favoritesGrid.onOpen = { host.openDetail(it) }
        favoritesGrid.onLongPress = { series, _ -> showFavoriteActions(series) }
        favoritesGrid.onRefresh = { render() }
        favoritesGrid.onRetry = { render() }

        historyGrid.adapter = HistoryAdapter()
        historyGrid.onItemClickListener =
            AdapterView.OnItemClickListener { _, _, position, _ ->
                historyItems.getOrNull(position)?.let { host.openDetail(it.series, resume = true) }
            }
        historyGrid.onItemLongClickListener =
            AdapterView.OnItemLongClickListener { _, _, position, _ ->
                val item = historyItems.getOrNull(position) ?: return@OnItemLongClickListener false
                showHistoryActions(item)
                true
            }
        accountSource = host.sources.accountFavorites
        render()
    }

    override val view: View get() = root

    override fun onShow() {
        if (host.account.signedIn) {
            host.account.refreshFavorites()
            host.account.refreshHistory()
            host.account.refreshReservations()
        }
        render()
    }

    override fun onHide() = Unit

    override fun onBack() = false

    override fun close() = Unit

    private fun selectSegment(index: Int) {
        current = index
        render()
    }

    private fun render() {
        stateView?.let {
            contentHost.removeView(it)
            stateView = null
        }
        segments.select(current)
        sourceRow.visibility = if (current == 2) View.GONE else View.VISIBLE
        when (current) {
            0 -> renderFavorites()
            1 -> renderHistory()
            else -> renderReservations()
        }
    }

    private fun renderFavorites() {
        renderSourceRow()
        favoritesGrid.visibility = View.VISIBLE
        historyGrid.visibility = View.GONE
        reservationScroll.visibility = View.GONE
        val local = !accountSource
        val items = if (local) host.library.favorites() else host.account.favoriteState().rows.map { it.series }
        if (items.isEmpty()) {
            favoritesGrid.showEmpty(if (local) "还没有收藏" else "账号里还没有收藏", "去找剧", onDiscover)
        } else {
            favoritesGrid.show(items, hasMore = false)
        }
    }

    private fun renderHistory() {
        renderSourceRow()
        favoritesGrid.visibility = View.GONE
        historyGrid.visibility = View.VISIBLE
        reservationScroll.visibility = View.GONE
        historyItems =
            if (!accountSource) host.library.history()
            else
                host.account.historyRows().map {
                    WatchProgress(
                        it.series,
                        it.episodeId,
                        it.episodeIndex,
                        it.position,
                        it.duration,
                        completed = false,
                        updatedAt = it.updatedAt,
                    )
                }
        (historyGrid.adapter as HistoryAdapter).notifyDataSetChanged()
        if (historyItems.isEmpty()) {
            historyGrid.visibility = View.GONE
            showState(emptyState(context, if (!accountSource) "还没有观看记录" else "账号里还没有观看记录", "去找剧", onDiscover))
        }
    }

    private fun renderReservations() {
        favoritesGrid.visibility = View.GONE
        historyGrid.visibility = View.GONE
        reservationScroll.visibility = View.VISIBLE
        reservations.removeAllViews()
        val online = host.account.reservationState(true).rows
        val pending = host.account.reservationState(false).rows
        val rows = online + pending
        if (rows.isEmpty()) {
            reservations.addView(emptyState(context, "还没有预约的剧集", "去找剧", onDiscover))
            return
        }
        rows.forEach { reservations.addView(reservationRow(it)) }
    }

    private fun renderSourceRow() {
        sourceRow.removeAllViews()
        sourceRow.addView(chip(context, "本机", !accountSource) { switchSource(false) })
        sourceRow.addView(
            chip(context, "账号", accountSource) { switchSource(true) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = TouchTheme.dp(context, 8)
            },
        )
    }

    private fun switchSource(account: Boolean) {
        if (account && !host.account.signedIn) {
            host.account.show()
            return
        }
        accountSource = account
        if (current == 1) host.sources.accountHistory = account else host.sources.accountFavorites = account
        render()
    }

    private fun showState(view: View) {
        stateView = view
        contentHost.addView(view, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
    }

    private fun showFavoriteActions(series: Series) {
        host.sheet.choose("收藏", listOf("取消收藏", "查看完整标题"), 0) { index ->
            when (index) {
                0 -> {
                    host.library.toggle(series)
                    render()
                }
                else ->
                    host.sheet.custom(
                        TextView(context).apply {
                            text = series.title
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, TouchTheme.TEXT_BODY)
                            setTextColor(TouchTheme.text)
                            setPadding(TouchTheme.dp(context, 16), TouchTheme.dp(context, 12), TouchTheme.dp(context, 16), TouchTheme.dp(context, 12))
                        },
                        "完整标题",
                    )
            }
        }
    }

    private fun showHistoryActions(progress: WatchProgress) {
        host.sheet.choose("观看记录", listOf("继续观看", "移出记录", "清空历史"), 0) { index ->
            when (index) {
                0 -> host.openDetail(progress.series, resume = true)
                1 -> {
                    host.library.removeHistory(progress.series.id)
                    renderHistory()
                }
                else ->
                    host.sheet.confirm("清空历史", "清空本机观看历史？收藏不会受影响。", "清空") {
                        host.library.clearHistory()
                        renderHistory()
                    }
            }
        }
    }

    private fun reservationRow(row: CloudReservation): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            contentDescription = row.series.title
            setPadding(TouchTheme.dp(context, 20), TouchTheme.dp(context, 10), TouchTheme.dp(context, 20), TouchTheme.dp(context, 10))
            background = TouchTheme.pressable(context, TouchTheme.surface, TouchTheme.CARD_RADIUS_DP)
            setOnClickListener { host.openDetail(row.series) }
            addView(
                TextView(context).apply {
                    text = row.series.title
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, TouchTheme.TEXT_BODY)
                    setTextColor(TouchTheme.text)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                },
            )
            addView(
                TextView(context).apply {
                    text =
                        if (row.online) "已上线 · 可观看"
                        else "待上线" + if (row.publishText.isNotBlank()) " · ${row.publishText}" else ""
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, TouchTheme.TEXT_SECONDARY)
                    setTextColor(if (row.online) TouchTheme.accent else TouchTheme.muted)
                },
            )
        }.also {
            it.layoutParams =
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    leftMargin = TouchTheme.dp(context, 16)
                    rightMargin = TouchTheme.dp(context, 16)
                    topMargin = TouchTheme.dp(context, 6)
                }
        }

    private inner class HistoryAdapter : BaseAdapter() {
        override fun getCount() = historyItems.size

        override fun getItem(position: Int): Any = historyItems[position]

        override fun getItemId(position: Int) = historyItems[position].series.id.hashCode().toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val card = (convertView as? LinearLayout) ?: newCard()
            val item = historyItems[position]
            val cover = card.getChildAt(0) as ImageView
            cover.contentDescription = item.series.title
            host.artwork.load(cover, item.series.cover, ArtworkLoader.PRIORITY_VISIBLE)
            (card.getChildAt(1) as TextView).text = item.series.title
            (card.getChildAt(2) as TextView).text = "第 ${item.episodeIndex + 1} 集"
            val bar = card.getChildAt(3) as ProgressBar
            bar.max = 1000
            bar.progress =
                if (item.duration > 0) ((item.position.toFloat() / item.duration) * 1000).toInt().coerceIn(0, 1000)
                else 0
            return card
        }

        private fun newCard(): LinearLayout =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams =
                    AbsListView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                addView(
                    ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP },
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, TouchTheme.dp(context, 180)),
                )
                addView(
                    TextView(context).apply {
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                        setTextColor(TouchTheme.text)
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                        setPadding(0, TouchTheme.dp(context, 6), 0, 0)
                    },
                )
                addView(
                    TextView(context).apply {
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, TouchTheme.TEXT_MIN)
                        setTextColor(TouchTheme.muted)
                        maxLines = 1
                    },
                )
                addView(
                    ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
                        max = 1000
                    },
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, TouchTheme.dp(context, 4)).apply {
                        topMargin = TouchTheme.dp(context, 4)
                    },
                )
            }
    }
}
