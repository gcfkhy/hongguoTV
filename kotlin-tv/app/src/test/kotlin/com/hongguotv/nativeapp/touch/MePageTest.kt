package com.hongguotv.nativeapp.touch

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.Switch
import android.widget.TextView
import com.hongguotv.nativeapp.BuildConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26], qualifiers = "w411dp-h891dp-port")
class MePageTest {
    private lateinit var controller: ActivityController<Activity>
    private lateinit var activity: Activity
    private lateinit var host: FakeTouchHost

    @Before
    fun setUp() {
        controller = Robolectric.buildActivity(Activity::class.java).setup()
        activity = controller.get()
        host = FakeTouchHost(activity)
    }

    @After
    fun tearDown() {
        host.destroy()
        controller.pause().stop().destroy()
    }

    private fun texts(view: View): List<TextView> =
        if (view is ViewGroup) (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
        else if (view is TextView) listOf(view) else emptyList()

    private fun switches(view: View): List<Switch> =
        if (view is ViewGroup) (0 until view.childCount).flatMap { switches(view.getChildAt(it)) }
        else if (view is Switch) listOf(view) else emptyList()

    private fun latestTexts(): List<TextView> =
        texts(ShadowDialog.getLatestDialog().window!!.decorView)

    private fun page(): MePage {
        val page = MePage(host)
        activity.setContentView(page.view)
        return page
    }

    /** Labels live inside clickable rows; walk up to the first clickable ancestor. */
    private fun click(view: View) {
        var current: View? = view
        while (current != null) {
            if (current.isClickable) {
                current.performClick()
                return
            }
            current = current.parent as? View
        }
        view.performClick()
    }

    @Test
    fun signedOutCardOpensAccountDialog() {
        val page = page()
        try {
            click(texts(page.view).first { it.text.toString() == "登录并同步" })
            assertTrue(host.account.showing)
        } finally {
            page.close()
        }
    }

    @Test
    fun autoNextSwitchWritesTheLibraryPreference() {
        val page = page()
        try {
            val before = host.library.autoNext
            switches(page.view).first { it.contentDescription?.toString() == "自动连播下一集" }.performClick()
            assertEquals(!before, host.library.autoNext)
        } finally {
            page.close()
        }
    }

    @Test
    fun speedRowUsesTheTouchSheet() {
        val page = page()
        try {
            click(texts(page.view).first { it.text.toString() == "默认倍速" })
            latestTexts().first { it.text.toString() == "1.5×" }.performClick()
            assertEquals(1.5f, host.library.playbackSpeed)
        } finally {
            page.close()
        }
    }

    @Test
    fun copyAvoidsRemoteOnlyWording() {
        val page = page()
        try {
            val copy = texts(page.view).joinToString("\n") { it.text.toString() }
            assertFalse(copy.contains("方向键"))
            assertFalse(copy.contains("遥控"))
        } finally {
            page.close()
        }
    }

    @Test
    fun aboutRowShowsTheVersion() {
        val page = page()
        try {
            assertTrue(texts(page.view).any { it.text.toString() == BuildConfig.VERSION_NAME })
        } finally {
            page.close()
        }
    }
}
