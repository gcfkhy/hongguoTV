package com.hongguotv.nativeapp.touch

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.hongguotv.core.ContentRepository
import com.hongguotv.core.Series
import com.hongguotv.nativeapp.AccountController
import com.hongguotv.nativeapp.ArtworkLoader
import com.hongguotv.nativeapp.Library
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26], qualifiers = "w411dp-h891dp-port")
class TouchShellTest {
    private lateinit var controller: ActivityController<Activity>
    private lateinit var host: FakeHost
    private lateinit var root: FrameLayout
    private lateinit var factoryCalls: MutableList<Pair<TouchTab, Int>>
    private lateinit var created: MutableList<FakePage>

    private class FakePage(context: Context) : TouchPage {
        override val view = FrameLayout(context)
        var shown = 0
        var hidden = 0
        var closed = false
        var consumeBack = false

        override fun onShow() {
            shown++
        }

        override fun onHide() {
            hidden++
        }

        override fun onBack() = consumeBack

        override fun close() {
            closed = true
        }
    }

    private class FakeHost(override val activity: Activity) : TouchHost {
        override val library = Library(activity)
        override val account = AccountController(activity) {}
        override val repository = ContentRepository()
        override val artwork = ArtworkLoader(activity)
        override val sheet = TouchSheet(activity)
        override val sources = com.hongguotv.nativeapp.LibrarySources(activity)
        var opened: Pair<Series, Boolean>? = null

        override fun <T> work(action: () -> T, done: (T) -> Unit, failed: (Throwable) -> Unit) = Unit

        override fun openDetail(series: Series, resume: Boolean) {
            opened = series to resume
        }

        override fun toggleLocalFavorite(series: Series) = true

        override fun accountFavorite(series: Series, anchor: View) = Unit
    }

    @Before
    fun setUp() {
        controller = Robolectric.buildActivity(Activity::class.java).setup()
        host = FakeHost(controller.get())
        root = FrameLayout(controller.get())
        factoryCalls = mutableListOf()
        created = mutableListOf()
    }

    @After
    fun tearDown() {
        host.account.destroy()
        host.artwork.close()
        host.library.close()
        controller.destroy()
    }

    private fun shell(): TouchShell =
        TouchShell(host, root) { tab, segment ->
            factoryCalls += tab to segment
            FakePage(host.activity).also { created += it }
        }

    private fun find(view: View, description: String): View? {
        if (view.contentDescription?.toString() == description) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                find(view.getChildAt(index), description)?.let { return it }
            }
        }
        return null
    }

    @Test
    fun compactUsesBottomBarWithFourItems() {
        val shell = shell()
        shell.show(TouchTab.Home)
        val nav = find(root, "底部导航")
        assertNotNull(nav)
        assertEquals(4, (nav as ViewGroup).childCount)
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land")
    fun expandedUsesSideRail() {
        val shell = shell()
        shell.show(TouchTab.Home)
        assertNotNull(find(root, "侧边导航"))
    }

    @Test
    fun tappingATabCreatesItsPageOnce() {
        val shell = shell()
        shell.show(TouchTab.Home)
        find(root, "找剧")!!.performClick()
        assertEquals(DiscoverCall, factoryCalls.filter { it.first == TouchTab.Discover }.single())
        assertSame(TouchTab.Discover, shell.currentTab)
        find(root, "找剧")!!.performClick()
        assertEquals(1, factoryCalls.count { it.first == TouchTab.Discover })
    }

    @Test
    fun backAsksThePageBeforeLeavingTheTab() {
        val shell = shell()
        shell.show(TouchTab.Discover)
        val page = created.single()
        page.consumeBack = true
        assertTrue(shell.back())
        assertSame(TouchTab.Discover, shell.currentTab)

        page.consumeBack = false
        assertTrue(shell.back())
        assertSame(TouchTab.Home, shell.currentTab)
        assertEquals(1, page.hidden)
    }

    @Test
    fun backOnHomeFinishes() {
        val shell = shell()
        shell.show(TouchTab.Home)
        assertFalse(shell.back())
    }

    @Test
    fun configurationChangeSwapsNavigationWithoutRebuildingPages() {
        val shell = shell()
        shell.show(TouchTab.Home)
        val page = created.single()
        RuntimeEnvironment.setQualifiers("w1280dp-h800dp-land")
        shell.onConfigurationChanged()
        assertNotNull(find(root, "侧边导航"))
        assertEquals(1, factoryCalls.size)
        assertSame(page, created.single())
    }

    private companion object {
        val DiscoverCall = TouchTab.Discover to 0
    }
}
