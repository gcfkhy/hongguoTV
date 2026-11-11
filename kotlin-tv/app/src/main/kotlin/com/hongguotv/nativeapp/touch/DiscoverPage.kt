// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp.touch

import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import com.hongguotv.core.CatalogPage
import com.hongguotv.core.DramaCategory
import com.hongguotv.core.RankBoard
import com.hongguotv.core.RankPosition
import com.hongguotv.core.RankSnapshot
import com.hongguotv.core.Series
import com.hongguotv.nativeapp.ArtworkLoader

/** Discover tab: search with suggestions, recommendations and rankings. */
internal class DiscoverPage(
    private val host: TouchHost,
    private val segment: Int,
    private val suggest: (String) -> List<String> = { host.repository.searchSuggestions(it) },
    private val search: (String, Int) -> CatalogPage = { keyword, page ->
        host.repository.searchAll(keyword, page, host.library.contentType)
    },
    private val discover: (DramaCategory) -> CatalogPage = { host.repository.discover(it) },
    private val rank: (RankBoard) -> RankSnapshot = { host.repository.rankSnapshot(it) },
) : TouchPage {
    private enum class Mode { Browse, Suggest, Results }

    private val context = host.activity
    private val main = Handler(Looper.getMainLooper())
    private val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    private val searchBar =
        TextView(context).apply {
            text = "搜索剧名"
            contentDescription = "搜索剧名"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, TouchTheme.TEXT_BODY)
            setTextColor(TouchTheme.muted)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(TouchTheme.dp(context, 16), 0, TouchTheme.dp(context, 16), 0)
            background = TouchTheme.pressable(context, TouchTheme.surface, 24)
            setOnClickListener { enterSearch() }
        }

    private val segments =
        SegmentedControl(context, listOf("推荐", "排行榜"), segment) { selectSegment(it) }

    private val chipsRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    private val chipsScroll =
        HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(chipsRow)
        }

    private val contentHost = FrameLayout(context)
    internal val posterGrid = PosterGridView(context, host)
    private val rankList = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val rankScroll =
        ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            visibility = View.GONE
            addView(rankList)
        }

    private val input =
        EditText(context).apply {
            hint = "输入剧名或关键词"
            inputType = InputType.TYPE_CLASS_TEXT
            isSingleLine = true
            setTextSize(TypedValue.COMPLEX_UNIT_SP, TouchTheme.TEXT_BODY)
            setTextColor(TouchTheme.text)
            setHintTextColor(TouchTheme.muted)
            setPadding(TouchTheme.dp(context, 16), TouchTheme.dp(context, 10), TouchTheme.dp(context, 16), TouchTheme.dp(context, 10))
            background = TouchTheme.pressable(context, TouchTheme.surface, 24)
        }
    private val suggestions = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val searchPanel =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(TouchTheme.dp(context, 16), TouchTheme.dp(context, 8), TouchTheme.dp(context, 16), 0)
            addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, TouchTheme.dp(context, 48)))
            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.END
                    addView(action("搜索") { submit(input.text.toString()) })
                    addView(action("清除") {
                        host.library.clearSearches()
                        renderSearchState()
                    })
                },
            )
            addView(
                ScrollView(context).apply {
                    isVerticalScrollBarEnabled = false
                    addView(suggestions)
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
            )
        }

    private var mode = Mode.Browse
    private var currentSegment = segment.coerceIn(0, 1)
    private var category: DramaCategory? = null
    private var board = RankBoard.ALL
    private var keyword = ""
    private var resultPage = 1
    private var hasMore = false
    private var loaded = false
    private var suggestionJob: Runnable? = null

    init {
        root.setBackgroundColor(TouchTheme.background)
        root.addView(
            searchBar,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, TouchTheme.dp(context, 48)).apply {
                leftMargin = TouchTheme.dp(context, 16)
                rightMargin = TouchTheme.dp(context, 16)
                topMargin = TouchTheme.dp(context, 8)
            },
        )
        root.addView(
            segments,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = TouchTheme.dp(context, 16)
                rightMargin = TouchTheme.dp(context, 16)
                topMargin = TouchTheme.dp(context, 8)
            },
        )
        root.addView(
            chipsScroll,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, TouchTheme.dp(context, 52)).apply {
                leftMargin = TouchTheme.dp(context, 12)
                rightMargin = TouchTheme.dp(context, 12)
            },
        )
        root.addView(contentHost, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        contentHost.addView(posterGrid, FrameLayout.LayoutParams(-1, -1))
        contentHost.addView(rankScroll, FrameLayout.LayoutParams(-1, -1))
        contentHost.addView(searchPanel, FrameLayout.LayoutParams(-1, -1))

        posterGrid.onOpen = { host.openDetail(it) }
        posterGrid.onLongPress = { series, anchor -> showActions(series, anchor) }
        posterGrid.onNearEnd = { if (mode == Mode.Results && hasMore) loadSearchPage(resultPage + 1) }
        posterGrid.onRefresh = {
            if (mode == Mode.Results) submit(keyword) else loadDiscover()
        }
        posterGrid.onRetry = {
            if (mode == Mode.Results) submit(keyword) else loadDiscover()
        }
        input.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

                override fun afterTextChanged(s: Editable?) {
                    scheduleSuggestions(s?.toString().orEmpty())
                }
            },
        )
        if (currentSegment == 0) renderCategoryChips() else renderRankChips()
        applyMode()
    }

    override val view: View get() = root

    override fun onShow() {
        if (loaded) return
        loaded = true
        if (currentSegment == 0) loadDiscover() else loadRank(board)
    }

    override fun onHide() {
        suggestionJob?.let(main::removeCallbacks)
        suggestionJob = null
        hideKeyboard()
    }

    override fun onBack(): Boolean =
        when (mode) {
            Mode.Results -> {
                mode = Mode.Suggest
                applyMode()
                renderSearchState()
                true
            }
            Mode.Suggest -> {
                leaveSearch()
                true
            }
            Mode.Browse -> false
        }

    override fun close() {
        suggestionJob?.let(main::removeCallbacks)
        suggestionJob = null
        hideKeyboard()
    }

    private fun selectSegment(index: Int) {
        currentSegment = index
        mode = Mode.Browse
        if (index == 0) {
            category = null
            renderCategoryChips()
            applyMode()
            loadDiscover()
        } else {
            renderRankChips()
            applyMode()
            loadRank(board)
        }
    }

    private fun renderCategoryChips() {
        chipsRow.removeAllViews()
        chipsRow.addView(chip(context, "全部", category == null) { chooseCategory(null) })
        chipsRow.addView(chip(context, "漫剧", category == DramaCategory.COMIC) { chooseCategory(DramaCategory.COMIC) })
        chipsRow.addView(chip(context, "真人剧", category == DramaCategory.REAL) { chooseCategory(DramaCategory.REAL) })
    }

    private fun renderRankChips() {
        chipsRow.removeAllViews()
        RankBoard.entries.forEach { item ->
            chipsRow.addView(chip(context, item.label, item == board) { chooseBoard(item) })
        }
    }

    private fun chooseCategory(value: DramaCategory?) {
        category = value
        renderCategoryChips()
        loadDiscover()
    }

    private fun chooseBoard(value: RankBoard) {
        board = value
        renderRankChips()
        loadRank(value)
    }

    private fun loadDiscover() {
        posterGrid.visibility = View.VISIBLE
        rankScroll.visibility = View.GONE
        posterGrid.showLoading()
        val target = category ?: if (host.library.contentType == com.hongguotv.core.ContentType.COMIC) DramaCategory.COMIC else DramaCategory.REAL
        host.work(
            { discover(target) },
            { page ->
                if (page.items.isEmpty()) posterGrid.showEmpty("这个分类还没有内容")
                else posterGrid.show(page.items, page.hasMore)
            },
            { posterGrid.showError("内容加载失败，请检查网络") },
        )
    }

    private fun loadRank(target: RankBoard) {
        rankList.removeAllViews()
        rankList.addView(
            ProgressBar(context),
            LinearLayout.LayoutParams(TouchTheme.dp(context, 32), TouchTheme.dp(context, 32)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = TouchTheme.dp(context, 24)
            },
        )
        host.work(
            { rank(target) },
            { snapshot -> renderRank(snapshot) },
            { renderRankError() },
        )
    }

    private fun renderRank(snapshot: RankSnapshot) {
        rankList.removeAllViews()
        val columns = if (WindowClass.of(context) == WindowClass.Expanded) 2 else 1
        var row: LinearLayout? = null
        snapshot.items.forEachIndexed { index, series ->
            if (index % columns == 0) {
                row =
                    LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(TouchTheme.dp(context, 12), TouchTheme.dp(context, 4), TouchTheme.dp(context, 12), TouchTheme.dp(context, 4))
                    }
                rankList.addView(row)
            }
            row?.addView(rankCell(series, snapshot.ranking.positions[series.id]), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    private fun renderRankError() {
        rankList.removeAllViews()
        rankList.addView(
            emptyState(context, "榜单加载失败", "重试") { loadRank(board) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
    }

    private fun rankCell(series: Series, position: RankPosition?): View {
        val cell =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(TouchTheme.dp(context, 6), TouchTheme.dp(context, 8), TouchTheme.dp(context, 6), TouchTheme.dp(context, 8))
                contentDescription = series.title
                background = TouchTheme.pressable(context, TouchTheme.surface)
                isClickable = true
                setOnClickListener { host.openDetail(series) }
                setOnLongClickListener {
                    showActions(series, it)
                    true
                }
            }
        cell.addView(
            TextView(context).apply {
                text = position?.rank?.toString() ?: "-"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                setTextColor(TouchTheme.accent)
                gravity = Gravity.CENTER
                width = TouchTheme.dp(context, 32)
            },
        )
        val cover =
            ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                contentDescription = series.title
            }
        cell.addView(cover, LinearLayout.LayoutParams(TouchTheme.dp(context, 48), TouchTheme.dp(context, 66)))
        host.artwork.load(cover, series.cover, ArtworkLoader.PRIORITY_VISIBLE)
        val labels =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(TouchTheme.dp(context, 10), 0, 0, 0)
            }
        labels.addView(
            TextView(context).apply {
                text = series.title
                setTextSize(TypedValue.COMPLEX_UNIT_SP, TouchTheme.TEXT_BODY)
                setTextColor(TouchTheme.text)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            },
        )
        labels.addView(
            TextView(context).apply {
                text = listOfNotNull(position?.heat?.takeIf { it.isNotBlank() }?.let { "热度 $it" }, series.badge.takeIf { it.isNotBlank() }).joinToString(" · ")
                setTextSize(TypedValue.COMPLEX_UNIT_SP, TouchTheme.TEXT_MIN)
                setTextColor(TouchTheme.muted)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            },
        )
        cell.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return cell
    }

    private fun showActions(series: Series, anchor: View) {
        host.sheet.choose(
            "操作",
            listOf("继续观看", "查看完整标题", "收藏/取消收藏"),
            0,
        ) { index ->
            when (index) {
                0 -> host.openDetail(series, resume = true)
                1 ->
                    host.sheet.custom(
                        TextView(context).apply {
                            text = series.title
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, TouchTheme.TEXT_BODY)
                            setTextColor(TouchTheme.text)
                            setPadding(TouchTheme.dp(context, 16), TouchTheme.dp(context, 12), TouchTheme.dp(context, 16), TouchTheme.dp(context, 12))
                        },
                        "完整标题",
                    )
                else -> host.toggleLocalFavorite(series)
            }
        }
    }

    private fun enterSearch() {
        mode = Mode.Suggest
        applyMode()
        renderSearchState()
        input.requestFocus()
        val manager = context.getSystemService(InputMethodManager::class.java)
        manager?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun leaveSearch() {
        mode = Mode.Browse
        suggestionJob?.let(main::removeCallbacks)
        suggestionJob = null
        hideKeyboard()
        applyMode()
        if (currentSegment == 0) loadDiscover() else loadRank(board)
    }

    private fun applyMode() {
        val search = mode != Mode.Browse
        segments.visibility = if (search) View.GONE else View.VISIBLE
        chipsScroll.visibility = if (search) View.GONE else View.VISIBLE
        searchPanel.visibility = if (mode == Mode.Suggest) View.VISIBLE else View.GONE
        posterGrid.visibility =
            if (!search && currentSegment == 0 || mode == Mode.Results) View.VISIBLE else View.GONE
        rankScroll.visibility = if (!search && currentSegment == 1) View.VISIBLE else View.GONE
        if (!search && currentSegment == 0) posterGrid.visibility = View.VISIBLE
    }

    private fun scheduleSuggestions(text: String) {
        if (mode != Mode.Suggest) return
        suggestionJob?.let(main::removeCallbacks)
        if (text.isBlank()) {
            renderSearchState()
            return
        }
        val job =
            Runnable {
                host.work(
                    { suggest(text) },
                    { words -> renderSuggestions(words) },
                    { renderSuggestions(emptyList()) },
                )
            }
        suggestionJob = job
        main.postDelayed(job, 300)
    }

    private fun renderSearchState() {
        val text = input.text.toString().trim()
        if (text.isBlank()) renderRecents() else scheduleSuggestions(text)
    }

    private fun renderRecents() {
        suggestions.removeAllViews()
        val recents = host.library.searches()
        suggestions.addView(label("最近搜索"))
        if (recents.isEmpty()) {
            suggestions.addView(label("暂无搜索记录"))
            return
        }
        recents.forEach { entry ->
            suggestions.addView(suggestionRow(entry.query) {
                input.setText(entry.query)
                submit(entry.query)
            })
        }
    }

    private fun renderSuggestions(words: List<String>) {
        suggestions.removeAllViews()
        if (words.isEmpty()) {
            suggestions.addView(label("暂无联想词，点「搜索」直接查找"))
            return
        }
        suggestions.addView(label("联想词"))
        words.forEach { word ->
            suggestions.addView(suggestionRow(word) {
                input.setText(word)
                submit(word)
            })
        }
    }

    private fun label(text: String): TextView =
        TextView(context).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, TouchTheme.TEXT_SECONDARY)
            setTextColor(TouchTheme.muted)
            setPadding(TouchTheme.dp(context, 4), TouchTheme.dp(context, 12), 0, TouchTheme.dp(context, 4))
        }

    private fun suggestionRow(word: String, onClick: () -> Unit): TextView =
        TextView(context).apply {
            text = word
            contentDescription = word
            setTextSize(TypedValue.COMPLEX_UNIT_SP, TouchTheme.TEXT_BODY)
            setTextColor(TouchTheme.text)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(TouchTheme.dp(context, 8), 0, TouchTheme.dp(context, 8), 0)
            background = TouchTheme.pressable(context, TouchTheme.surface, 8)
            setOnClickListener { onClick() }
        }.also {
            it.layoutParams =
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    TouchTheme.dp(context, TouchTheme.MIN_TARGET_DP),
                )
        }

    private fun action(label: String, onClick: () -> Unit): TextView =
        TextView(context).apply {
            text = label
            contentDescription = label
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, TouchTheme.TEXT_BODY)
            setTextColor(TouchTheme.accent)
            minimumWidth = TouchTheme.dp(context, TouchTheme.MIN_TARGET_DP)
            minimumHeight = TouchTheme.dp(context, TouchTheme.MIN_TARGET_DP)
            setPadding(TouchTheme.dp(context, 12), 0, TouchTheme.dp(context, 12), 0)
            setOnClickListener { onClick() }
        }

    private fun submit(text: String) {
        val keyword = text.trim()
        if (keyword.isEmpty()) return
        this.keyword = keyword
        resultPage = 1
        mode = Mode.Results
        applyMode()
        host.library.rememberSearch(keyword, host.library.contentType)
        loadSearchPage(1)
    }

    private fun loadSearchPage(page: Int) {
        resultPage = page
        if (page == 1) posterGrid.showLoading()
        host.work(
            { search(keyword, page) },
            { result ->
                hasMore = result.hasMore
                if (page == 1) {
                    if (result.items.isEmpty()) posterGrid.showEmpty("没有找到「$keyword」，换个关键词试试")
                    else posterGrid.show(result.items, result.hasMore)
                } else posterGrid.append(result.items, result.hasMore)
            },
            { posterGrid.showError("搜索失败，请稍后重试") },
        )
    }

    private fun hideKeyboard() {
        val manager = context.getSystemService(InputMethodManager::class.java) ?: return
        manager.hideSoftInputFromWindow(input.windowToken, 0)
    }
}
