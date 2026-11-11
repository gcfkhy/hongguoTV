package com.hongguotv.nativeapp.touch

import android.content.pm.ActivityInfo
import com.hongguotv.nativeapp.DeviceUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26], qualifiers = "w411dp-h891dp-port")
class WindowClassTest {
    @Test
    fun widthThresholdsMatchGlobalConstraints() {
        assertEquals(WindowClass.Compact, WindowClass.of(320))
        assertEquals(WindowClass.Compact, WindowClass.of(599))
        assertEquals(WindowClass.Medium, WindowClass.of(600))
        assertEquals(WindowClass.Medium, WindowClass.of(840))
        assertEquals(WindowClass.Expanded, WindowClass.of(841))
        assertEquals(WindowClass.Expanded, WindowClass.of(1280))
    }

    @Test
    fun gridColumnsFollowWindowWidth() {
        assertEquals(3, WindowClass.gridColumns(411))
        assertEquals(4, WindowClass.gridColumns(700))
        assertEquals(5, WindowClass.gridColumns(900))
        assertEquals(5, WindowClass.gridColumns(1199))
        assertEquals(6, WindowClass.gridColumns(1280))
    }

    @Test
    fun onlyWideWindowsUseNavigationRail() {
        assertFalse(WindowClass.Compact.usesRail)
        assertTrue(WindowClass.Medium.usesRail)
        assertTrue(WindowClass.Expanded.usesRail)
    }

    @Test
    fun phoneContextUsesCompactWindow() {
        assertEquals(WindowClass.Compact, WindowClass.of(RuntimeEnvironment.getApplication()))
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land")
    fun tabletContextUsesExpandedWindow() {
        assertEquals(WindowClass.Expanded, WindowClass.of(RuntimeEnvironment.getApplication()))
    }

    @Test
    fun touchDevicesFollowTheSensor() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR,
            DeviceUi.preferredOrientation(RuntimeEnvironment.getApplication()),
        )
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp-land-television")
    fun televisionStaysLandscape() {
        val context = RuntimeEnvironment.getApplication()
        assertTrue(DeviceUi.television(context))
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
            DeviceUi.preferredOrientation(context),
        )
    }
}
