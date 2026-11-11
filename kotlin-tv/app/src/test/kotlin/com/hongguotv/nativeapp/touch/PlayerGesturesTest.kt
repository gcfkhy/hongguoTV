package com.hongguotv.nativeapp.touch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerGesturesTest {
    @Test
    fun horizontalDragNeedsMostlyHorizontalMovement() {
        assertTrue(PlayerGestures.isHorizontalDrag(60f, 10f, 24f))
        assertFalse(PlayerGestures.isHorizontalDrag(60f, 40f, 24f))
        assertFalse(PlayerGestures.isHorizontalDrag(20f, 0f, 24f))
    }

    @Test
    fun halfScreenDragSeeksSixtySeconds() {
        assertEquals(70_000L, PlayerGestures.seekTarget(10_000, 600_000, 540f, 1080))
    }

    @Test
    fun seekTargetStaysInsideTheDuration() {
        assertEquals(0L, PlayerGestures.seekTarget(10_000, 600_000, -5_000f, 1080))
        assertEquals(599_500L, PlayerGestures.seekTarget(10_000, 600_000, 6_000f, 1080))
        assertEquals(10_000L, PlayerGestures.seekTarget(10_000, 0, 100f, 1080))
        assertEquals(10_000L, PlayerGestures.seekTarget(10_000, 600_000, 100f, 0))
    }

    @Test
    fun bubbleFormatsMinutesAndHours() {
        assertEquals("01:05 / 02:30", PlayerGestures.bubble(65_000, 150_000))
        assertEquals("1:02:03 / 2:00:00", PlayerGestures.bubble(3_723_000, 7_200_000))
    }

    @Test
    fun touchDefaultsMatchThePlan() {
        assertEquals(3000L, PlayerGestures.AUTO_HIDE_MS)
        assertEquals(2f, PlayerGestures.LONG_PRESS_SPEED)
    }
}
