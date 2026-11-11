package com.hongguotv.nativeapp.touch

import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import com.hongguotv.nativeapp.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26], qualifiers = "w411dp-h891dp-port-xxhdpi")
class TouchWidgetsTest {
    private val context = RuntimeEnvironment.getApplication()

    private fun measure(view: View) {
        view.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }

    private fun texts(view: View): List<TextView> =
        when (view) {
            is ViewGroup -> (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
            is TextView -> listOf(view)
            else -> emptyList()
        }

    @Test
    fun iconButtonKeepsTouchTargetAndDescription() {
        val button = iconButton(context, R.drawable.touch_ic_play, "播放") {}
        measure(button)
        assertTrue(button is ImageButton)
        assertTrue(button.measuredWidth >= TouchTheme.dp(context, 48))
        assertTrue(button.measuredHeight >= TouchTheme.dp(context, 48))
        assertEquals("播放", button.contentDescription.toString())
    }

    @Test
    fun chipKeepsTouchTargetAndHighlightsSelection() {
        val plain = chip(context, "推荐", false) {}
        measure(plain)
        assertTrue(plain.measuredWidth >= TouchTheme.dp(context, 48))
        assertTrue(plain.measuredHeight >= TouchTheme.dp(context, 48))
        assertEquals("推荐", plain.contentDescription.toString())
        assertEquals(TouchTheme.text, plain.currentTextColor)

        val selected = chip(context, "漫剧", true) {}
        assertEquals(TouchTheme.accent, selected.currentTextColor)
    }

    @Test
    fun segmentedControlSelectsOneItemAndReportsOnce() {
        val calls = mutableListOf<Int>()
        val control =
            SegmentedControl(context, listOf("推荐", "漫剧", "真人剧"), 0) { calls += it }
        val items = texts(control)
        assertEquals(3, items.size)
        assertTrue(items[0].isSelected)
        control.select(1)
        assertEquals(listOf(1), calls)
        assertEquals(listOf(false, true, false), items.map { it.isSelected })
    }

    @Test
    fun emptyStateWithActionOffersButton() {
        var clicked = 0
        val state = emptyState(context, "还没有收藏", "去找剧") { clicked++ }
        val action = texts(state).single { it.text.toString() == "去找剧" }
        action.performClick()
        assertEquals(1, clicked)
        assertTrue(texts(state).any { it.text.toString() == "还没有收藏" })
    }

    @Test
    fun emptyStateWithoutActionHasNoButton() {
        val state = emptyState(context, "没有内容")
        assertTrue(texts(state).all { it.contentDescription == null })
        assertTrue(texts(state).size == 1)
    }

    @Test
    fun sectionTitleUsesReadableSize() {
        val view = sectionTitle(context, "选集")
        assertEquals("选集", view.text.toString())
        assertTrue(view.textSize >= TouchTheme.TEXT_BODY)
        assertNull(view.background)
    }
}
