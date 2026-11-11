package com.hongguotv.nativeapp.touch

import android.app.Activity
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26], qualifiers = "w411dp-h891dp-port")
class TouchSheetTest {
    private fun activity(): Activity = Robolectric.buildActivity(Activity::class.java).setup().get()

    private fun texts(view: View): List<TextView> =
        when (view) {
            is ViewGroup -> (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
            is TextView -> listOf(view)
            else -> emptyList()
        }

    private fun latestTexts(): List<TextView> =
        texts(ShadowDialog.getLatestDialog().window!!.decorView)

    @Test
    fun chooseMarksSelectionAndReportsPickedOption() {
        val sheet = TouchSheet(activity())
        var picked = -1
        sheet.choose("选择清晰度", listOf("自动", "1080P", "720P"), 1) { picked = it }
        assertTrue(sheet.showing)
        val labels = latestTexts().map { it.text.toString() }
        assertTrue(labels.any { it.contains("✓") && it.contains("1080P") })
        latestTexts().single { it.text.toString() == "720P" }.performClick()
        assertEquals(2, picked)
        assertFalse(sheet.showing)
    }

    @Test
    fun backPressClosesWithoutCallback() {
        val sheet = TouchSheet(activity())
        var picked = -1
        sheet.choose("选择", listOf("A", "B"), 0) { picked = it }
        ShadowDialog.getLatestDialog().onBackPressed()
        assertFalse(sheet.showing)
        assertEquals(-1, picked)
    }

    @Test
    fun gridRendersEveryCellWithMarks() {
        val sheet = TouchSheet(activity())
        var picked = -1
        sheet.grid("选集", 5, 2, mapOf(2 to "当前", 0 to "已看")) { picked = it }
        val views = latestTexts()
        assertEquals(5, views.count { it.contentDescription?.startsWith("第") == true })
        assertTrue(views.any { it.text.toString().contains("当前") })
        assertTrue(views.any { it.text.toString().contains("已看") })
        views.single { it.contentDescription == "第 4 集" }.performClick()
        assertEquals(3, picked)
        assertFalse(sheet.showing)
    }

    @Test
    fun inputCancelReturnsNull() {
        val sheet = TouchSheet(activity())
        var result = "unchanged"
        sheet.input("跳转", "输入集数", "1") { result = it ?: "null" }
        latestTexts().single { it.text.toString() == "取消" }.performClick()
        assertEquals("null", result)
        assertFalse(sheet.showing)
    }

    @Test
    fun compactSheetIsBottomAnchoredAtFullWidth() {
        val sheet = TouchSheet(activity())
        sheet.choose("选择", listOf("A"), 0) {}
        val window = ShadowDialog.getLatestDialog().window!!
        assertTrue(window.attributes.gravity and Gravity.BOTTOM != 0)
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, window.attributes.width)
        assertTrue(sheet.showing)
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land")
    fun expandedSheetIsCenteredCardNoWiderThan480dp() {
        val activity = activity()
        val sheet = TouchSheet(activity)
        sheet.choose("选择", listOf("A"), 0) {}
        val window = ShadowDialog.getLatestDialog().window!!
        assertTrue(window.attributes.gravity and Gravity.CENTER != 0)
        assertTrue(window.attributes.width <= TouchTheme.dp(activity, 480))
        assertTrue(window.attributes.width > 0)
    }

    @Test
    fun onlyOneSheetIsShowingAtATime() {
        val sheet = TouchSheet(activity())
        sheet.choose("第一层", listOf("A"), 0) {}
        sheet.choose("第二层", listOf("B"), 0) {}
        assertEquals(1, ShadowDialog.getShownDialogs().count { it.isShowing })
        assertTrue(sheet.showing)
    }
}
