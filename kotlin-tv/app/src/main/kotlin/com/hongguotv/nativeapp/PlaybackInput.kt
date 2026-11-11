// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp

import android.view.KeyEvent

/** Playback keys: television remotes keep the existing overlay mapping. */
object PlaybackInput {
    enum class Command {
        Toggle,
        SeekBack,
        SeekForward,
        PreviousEpisode,
        NextEpisode,
        ShowPanel,
        ShowEpisodes,
    }

    fun command(keyCode: Int, television: Boolean): Command? =
        when (keyCode) {
            KeyEvent.KEYCODE_SPACE,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER -> Command.Toggle
            KeyEvent.KEYCODE_DPAD_LEFT -> Command.SeekBack
            KeyEvent.KEYCODE_DPAD_RIGHT -> Command.SeekForward
            KeyEvent.KEYCODE_DPAD_UP ->
                if (television) Command.ShowPanel else Command.PreviousEpisode
            KeyEvent.KEYCODE_DPAD_DOWN ->
                if (television) Command.ShowEpisodes else Command.NextEpisode
            KeyEvent.KEYCODE_MENU -> Command.ShowPanel
            else -> null
        }

    /** Positive wheel (up) goes to the previous episode. */
    fun wheelEpisodeDelta(axisVscroll: Float): Int =
        when {
            axisVscroll > 0f -> -1
            axisVscroll < 0f -> 1
            else -> 0
        }
}
