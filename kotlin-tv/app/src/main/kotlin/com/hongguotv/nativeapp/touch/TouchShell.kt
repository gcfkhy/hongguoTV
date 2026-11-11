// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp.touch

import android.graphics.Color
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.hongguotv.nativeapp.R

/**
 * Root of the touch UI: hosts one page per top-level tab, switches between a
 * bottom bar (compact) and a side rail (medium/expanded), and routes back presses.
 */
internal class TouchShell(
    private val host: TouchHost,
    private val root: FrameLayout,
    private val pageFactory: (TouchTab, Int) -> TouchPage,
) {
    private val navigator = TabNavigator()
    private val pages = LinkedHashMap<TouchTab, TouchPage>()
    private val content = FrameLayout(host.activity)
    private var navigation: LinearLayout? = null
    private val navItems = mutableListOf<NavItem>()
    private var visiblePage: TouchPage? = null
    private var fullscreen: View? = null
    private var attached = false

    val currentTab: TouchTab get() = navigator.current

    fun show(tab: TouchTab, segment: Int = 0) {
        ensureAttached()
        restoreNavigation()
        val target = pages[tab] ?: pageFactory(tab, segment).also { page ->
            pages[tab] = page
            content.addView(page.view, FrameLayout.LayoutParams(-1, -1))
            page.view.visibility = View.GONE
        }
        navigator.select(tab)
        if (visiblePage !== target) {
            visiblePage?.let {
                it.onHide()
                it.view.visibility = View.GONE
            }
            target.view.visibility = View.VISIBLE
            visiblePage = target
            target.onShow()
        }
        paintNavigation()
    }

    /** Detail and playback mount themselves above the tabs and hide the navigation. */
    fun showFullscreenPage(view: View) {
        ensureAttached()
        visiblePage?.let {
            it.onHide()
            it.view.visibility = View.GONE
        }
        visiblePage = null
        navigation?.visibility = View.GONE
        fullscreen?.let { content.removeView(it) }
        fullscreen = view
        content.addView(view, FrameLayout.LayoutParams(-1, -1))
    }

    fun back(): Boolean {
        if (fullscreen != null) return false
        val page = pages[navigator.current]
        if (page?.onBack() == true) return true
        return when (navigator.back()) {
            BackResult.Handled -> {
                show(TouchTab.Home)
                true
            }
            BackResult.Exit -> false
        }
    }

    fun onConfigurationChanged() {
        if (!attached) return
        buildNavigation()
        applyLayout()
        paintNavigation()
    }

    fun close() {
        pages.values.forEach { it.close() }
        pages.clear()
        visiblePage = null
        fullscreen = null
        content.removeAllViews()
        navigation?.let { root.removeView(it) }
        navigation = null
        navItems.clear()
        if (attached) root.removeView(content)
        attached = false
    }

    private fun ensureAttached() {
        // The player clears the activity root, so restore the shell when coming back.
        if (attached && content.parent !== root) {
            attached = false
            navigation = null
            navItems.clear()
        }
        if (attached) return
        attached = true
        root.removeAllViews()
        root.addView(content, FrameLayout.LayoutParams(-1, -1))
        buildNavigation()
        applyLayout()
    }

    private fun restoreNavigation() {
        fullscreen?.let {
            content.removeView(it)
            fullscreen = null
        }
        navigation?.visibility = View.VISIBLE
    }

    private fun buildNavigation() {
        navigation?.let { root.removeView(it) }
        navItems.clear()
        val compact = WindowClass.of(host.activity) == WindowClass.Compact
        val bar =
            LinearLayout(host.activity).apply {
                orientation = if (compact) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                contentDescription = if (compact) "底部导航" else "侧边导航"
                setBackgroundColor(TouchTheme.background)
                if (compact) setPadding(0, TouchTheme.dp(host.activity, 4), 0, TouchTheme.dp(host.activity, 4))
                else setPadding(0, TouchTheme.dp(host.activity, 16), 0, 0)
            }
        val items =
            listOf(
                Triple(TouchTab.Home, "首页", R.drawable.touch_ic_home),
                Triple(TouchTab.Discover, "找剧", R.drawable.touch_ic_search),
                Triple(TouchTab.Library, "追剧", R.drawable.touch_ic_library),
                Triple(TouchTab.Me, "我的", R.drawable.touch_ic_me),
            )
        items.forEach { (tab, label, icon) ->
            val item =
                LinearLayout(host.activity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    contentDescription = label
                    isClickable = true
                    setOnClickListener { show(tab) }
                }
            val image =
                ImageView(host.activity).apply {
                    setImageResource(icon)
                    setColorFilter(TouchTheme.muted)
                }
            item.addView(image, LinearLayout.LayoutParams(TouchTheme.dp(host.activity, 24), TouchTheme.dp(host.activity, 24)))
            val text =
                TextView(host.activity).apply {
                    this.text = label
                    gravity = Gravity.CENTER
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    setTextColor(TouchTheme.muted)
                    isSingleLine = true
                }
            item.addView(text)
            bar.addView(
                item,
                if (compact) LinearLayout.LayoutParams(0, TouchTheme.dp(host.activity, 52), 1f)
                else LinearLayout.LayoutParams(TouchTheme.dp(host.activity, 72), TouchTheme.dp(host.activity, 72)).apply {
                    topMargin = TouchTheme.dp(host.activity, 8)
                },
            )
            navItems += NavItem(tab, item, image, text)
        }
        navigation = bar
        root.addView(bar, FrameLayout.LayoutParams(-1, -1))
    }

    private fun applyLayout() {
        val compact = WindowClass.of(host.activity) == WindowClass.Compact
        val size = TouchTheme.dp(host.activity, if (compact) 56 else 80)
        navigation?.layoutParams =
            if (compact) FrameLayout.LayoutParams(-1, size, Gravity.BOTTOM)
            else FrameLayout.LayoutParams(size, -1, Gravity.START)
        content.layoutParams =
            FrameLayout.LayoutParams(-1, -1).apply {
                if (compact) bottomMargin = size else marginStart = size
            }
        navItems.forEach { item ->
            item.container.layoutParams =
                if (compact) LinearLayout.LayoutParams(0, TouchTheme.dp(host.activity, 52), 1f)
                else LinearLayout.LayoutParams(TouchTheme.dp(host.activity, 72), TouchTheme.dp(host.activity, 72)).apply {
                    topMargin = TouchTheme.dp(host.activity, 8)
                }
        }
    }

    private fun paintNavigation() {
        navItems.forEach { item ->
            val selected = item.tab == navigator.current
            item.icon.setColorFilter(if (selected) TouchTheme.accent else TouchTheme.muted)
            item.label.setTextColor(if (selected) TouchTheme.accent else TouchTheme.muted)
            item.container.isSelected = selected
            item.container.setBackgroundColor(Color.TRANSPARENT)
        }
    }

    private data class NavItem(
        val tab: TouchTab,
        val container: LinearLayout,
        val icon: ImageView,
        val label: TextView,
    )
}
