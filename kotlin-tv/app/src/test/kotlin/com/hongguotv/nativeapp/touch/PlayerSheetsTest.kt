package com.hongguotv.nativeapp.touch

import com.hongguotv.core.PlaybackSpeed
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerSheetsTest {
    @Test
    fun moreOptionsMatchesTheTvPlaybackSettingsOrderAndWording() {
        val options =
            PlayerSheets.moreOptions(
                speedLabel = PlaybackSpeed.label(1.25f),
                quality = 720,
                frameLabel = "完整画面",
                sleepLabel = "关闭",
                relayEnabled = true,
                rotation = 90,
                alwaysShowProgress = true,
            )
        assertEquals(
            listOf(
                "倍速 1.25×",
                "清晰度 720P",
                "画面 完整画面",
                "定时 关闭",
                "伴机代解码 开",
                "从头播放",
                "重置设备能力记录",
                "画面旋转 90°",
                "常驻播放进度 开启",
            ),
            options,
        )
    }

    @Test
    fun moreOptionsReflectsCurrentSettings() {
        val options =
            PlayerSheets.moreOptions(
                speedLabel = PlaybackSpeed.label(1f),
                quality = 1080,
                frameLabel = "拉伸铺满",
                sleepLabel = "15 分钟后",
                relayEnabled = false,
                rotation = 0,
                alwaysShowProgress = false,
            )
        assertEquals("倍速 1×", options[0])
        assertEquals("清晰度 1080P", options[1])
        assertEquals("画面 拉伸铺满", options[2])
        assertEquals("定时 15 分钟后", options[3])
        assertEquals("伴机代解码 关", options[4])
        assertEquals("画面旋转 0°", options[7])
        assertEquals("常驻播放进度 关闭", options[8])
    }
}
