package com.hongguotv.nativeapp.touch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TabNavigatorTest {
    @Test
    fun legacyTabIndexesMapToTopLevelTabsAndSegments() {
        assertEquals(TouchTab.Home to 0, TouchTab.fromLegacyIndex(0))
        assertEquals(TouchTab.Discover to 0, TouchTab.fromLegacyIndex(1))
        assertEquals(TouchTab.Discover to 1, TouchTab.fromLegacyIndex(2))
        assertEquals(TouchTab.Library to 0, TouchTab.fromLegacyIndex(3))
        assertEquals(TouchTab.Library to 1, TouchTab.fromLegacyIndex(4))
        assertEquals(TouchTab.Library to 2, TouchTab.fromLegacyIndex(5))
        assertEquals(TouchTab.Me to 0, TouchTab.fromLegacyIndex(6))
    }

    @Test
    fun selectingTheSameTabIsIgnored() {
        val navigator = TabNavigator()
        assertSame(TouchTab.Home, navigator.current)
        assertFalse(navigator.select(TouchTab.Home))
        assertTrue(navigator.select(TouchTab.Discover))
        assertSame(TouchTab.Discover, navigator.current)
    }

    @Test
    fun backFromSecondaryTabReturnsHome() {
        val navigator = TabNavigator(TouchTab.Library)
        assertEquals(BackResult.Handled, navigator.back())
        assertSame(TouchTab.Home, navigator.current)
    }

    @Test
    fun backOnHomeExits() {
        val navigator = TabNavigator()
        assertEquals(BackResult.Exit, navigator.back())
        assertSame(TouchTab.Home, navigator.current)
    }
}
