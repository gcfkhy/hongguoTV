// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp.touch

import android.content.Context
import android.graphics.Color
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
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
import android.widget.TextView
import com.hongguotv.core.Series
import com.hongguotv.nativeapp.ArtworkLoader

/**
 * Poster grid for touch screens: 2:3 covers, paging, pull-to-refresh and
 * empty/error states. Television keeps using [com.hongguotv.nativeapp.ContinuousCatalogView].
 */
internal class PosterGridView(context: Context, private val host: TouchHost) : FrameLayout(context) {
    var onOpen: (Series) -> Unit = {}
    var onLongPress: (Series, View) -> Unit = { _, _ -> }
    var onNearEnd: () -> Unit = {}
    var onRefresh: () -> Unit = {}
    var onRetry: () -> Unit = {}

    private val items = mutableListOf<Series>()
    private var hasMore = false
    private var badge: (Series) -> String? = { null }
    private var nearEndFired = false
    private var stateView: View? = null

    internal val itemCount: Int get() = items.size

    internal val scrollListener =
        object : AbsListView.OnScrollListener {
            override fun onScroll(
                view: AbsListView?,
                firstVisibleItem: Int,
                visibleItemCount: Int,
                totalItemCount: Int,
            ) {
                if (!hasMore || nearEndFired) return
                if (firstVisibleItem + visibleItemCount >= totalItemCount - 6) {
                    nearEndFired = true
                    onNearEnd()
                }
            }

            override fun onScrollStateChanged(view: AbsListView?, state: Int) = Unit
        }

    private val grid =
        PullGridView(context).apply {
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
            adapter = CardAdapter()
            onItemClickListener =
                AdapterView.OnItemClickListener { _, _, position, _ ->
                    items.getOrNull(position)?.let(onOpen)
                }
            onItemLongClickListener =
                AdapterView.OnItemLongClickListener { _, child, position, _ ->
                    val series = items.getOrNull(position) ?: return@OnItemLongClickListener false
                    onLongPress(series, child)
                    true
                }
            setOnScrollListener(scrollListener)
            onPullRefresh = {
                clearOverlay()
                onRefresh()
            }
        }

    init {
        addView(grid, LayoutParams(-1, -1))
    }

    fun showLoading() {
        clearOverlay()
        grid.visibility = View.INVISIBLE
        val loading = ProgressBar(context)
        stateView = loading
        addView(loading, LayoutParams(TouchTheme.dp(context, 40), TouchTheme.dp(context, 40), Gravity.CENTER))
    }

    fun show(items: List<Series>, hasMore: Boolean, badge: (Series) -> String? = { null }) {
        clearOverlay()
        this.badge = badge
        this.items.clear()
        this.items += items
        this.hasMore = hasMore
        nearEndFired = false
        grid.visibility = View.VISIBLE
        (grid.adapter as CardAdapter).notifyDataSetChanged()
    }

    fun append(items: List<Series>, hasMore: Boolean) {
        this.items += items
        this.hasMore = hasMore
        nearEndFired = false
        (grid.adapter as CardAdapter).notifyDataSetChanged()
    }

    fun showEmpty(message: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
        showState(emptyState(context, message, actionLabel, onAction))
    }

    fun showError(message: String) {
        showState(emptyState(context, message, "重试") { onRetry() })
    }

    fun relayout() {
        grid.numColumns = WindowClass.gridColumns(context.resources.configuration.screenWidthDp)
        grid.requestLayout()
    }

    private fun showState(state: View) {
        clearOverlay()
        grid.visibility = View.GONE
        stateView = state
        addView(state, LayoutParams(-1, -1, Gravity.CENTER))
    }

    private fun clearOverlay() {
        stateView?.let { removeView(it) }
        stateView = null
    }

    private inner class CardAdapter : BaseAdapter() {
        override fun getCount() = items.size

        override fun getItem(position: Int): Any = items[position]

        override fun getItemId(position: Int) = items[position].id.hashCode().toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val card = (convertView as? LinearLayout) ?: newCard()
            val series = items[position]
            card.tag = series
            val image = card.getChildAt(0) as ImageView
            image.contentDescription = series.title
            host.artwork.load(image, series.cover, ArtworkLoader.PRIORITY_VISIBLE)
            (card.getChildAt(1) as TextView).text = series.title
            val subtitle = badge(series) ?: series.badge.ifBlank { series.tags }
            val caption = card.getChildAt(2) as TextView
            caption.text = subtitle
            caption.visibility = if (subtitle.isBlank()) View.GONE else View.VISIBLE
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
                addView(PosterImage(context), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
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
                        ellipsize = TextUtils.TruncateAt.END
                    },
                )
            }
    }

    /** 2:3 portrait cover, matching the short-drama poster ratio. */
    private class PosterImage(context: Context) : ImageView(context) {
        init {
            scaleType = ScaleType.CENTER_CROP
            background = TouchTheme.pressable(context, TouchTheme.raised)
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            super.onMeasure(
                widthMeasureSpec,
                MeasureSpec.makeMeasureSpec(width * 3 / 2, MeasureSpec.EXACTLY),
            )
        }
    }

    /** GridView that reports a downward pull at the top instead of scrolling. */
    private class PullGridView(context: Context) : GridView(context) {
        var onPullRefresh: () -> Unit = {}
        private var startY = 0f
        private var pulling = false

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startY = event.y
                    pulling = false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (firstVisiblePosition == 0 && event.y - startY > TouchTheme.dp(context, 64)) {
                        pulling = true
                        return true
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (pulling) {
                        pulling = false
                        onPullRefresh()
                        return true
                    }
                }
            }
            return super.onTouchEvent(event)
        }
    }
}
