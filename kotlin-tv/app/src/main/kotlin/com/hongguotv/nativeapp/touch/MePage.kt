// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp.touch

import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import com.hongguotv.core.PlaybackQuality
import com.hongguotv.core.PlaybackSpeed
import com.hongguotv.core.VideoFrameMode
import com.hongguotv.core.VideoRotation
import com.hongguotv.nativeapp.BuildConfig

/** Me tab: account card and grouped settings backed by the same Library preferences. */
internal class MePage(
    private val host: TouchHost,
    private val version: String = BuildConfig.VERSION_NAME,
    private val onCheckUpdates: ((View) -> Unit)? = null,
) : TouchPage {
    private val context = host.activity
    private val root =
        ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            setBackgroundColor(TouchTheme.background)
        }
    private val content =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                TouchTheme.dp(context, 16),
                TouchTheme.dp(context, 16),
                TouchTheme.dp(context, 16),
                TouchTheme.dp(context, 24),
            )
        }

    init {
        root.addView(content)
        rebuild()
    }

    override val view: View get() = root

    override fun onShow() = rebuild()

    override fun onHide() = Unit

    override fun onBack() = false

    override fun close() = Unit

    private fun rebuild() {
        content.removeAllViews()
        content.addView(
            accountCard(),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = TouchTheme.dp(context, 8)
            },
        )
        val library = host.library
        group(
            "播放",
            listOf(
                optionRow("默认倍速", PlaybackSpeed.label(library.playbackSpeed)) {
                    host.sheet.choose(
                        "默认倍速",
                        PlaybackSpeed.options.map(PlaybackSpeed::label),
                        PlaybackSpeed.options.indexOf(library.playbackSpeed),
                    ) { index ->
                        library.playbackSpeed = PlaybackSpeed.options[index]
                        rebuild()
                    }
                },
                optionRow("最高清晰度", "${library.maxQuality}P") {
                    host.sheet.choose(
                        "最高清晰度",
                        PlaybackQuality.options.map { "${it}P" },
                        PlaybackQuality.options.indexOf(library.maxQuality),
                    ) { index ->
                        library.maxQuality = PlaybackQuality.options[index]
                        rebuild()
                    }
                },
                optionRow("画面比例", library.frameMode.label) {
                    host.sheet.choose(
                        "画面比例",
                        VideoFrameMode.entries.map { it.label },
                        library.frameMode.ordinal,
                    ) { index ->
                        library.frameMode = VideoFrameMode.entries[index]
                        rebuild()
                    }
                },
                optionRow("画面旋转", "${library.videoRotation}°") {
                    host.sheet.choose(
                        "画面旋转",
                        VideoRotation.options.map { "${it}°" },
                        VideoRotation.options.indexOf(library.videoRotation),
                    ) { index ->
                        library.videoRotation = VideoRotation.options[index]
                        rebuild()
                    }
                },
                toggleRow("自动连播下一集", library.autoNext) {
                    library.autoNext = it
                    rebuild()
                },
                toggleRow("跨季连播", library.seasonAutoplay) {
                    library.seasonAutoplay = it
                    rebuild()
                },
                toggleRow("常驻播放进度", library.alwaysShowProgress) {
                    library.alwaysShowProgress = it
                    rebuild()
                },
                toggleRow("首页自动试播", library.homeAutoplay) {
                    library.homeAutoplay = it
                    rebuild()
                },
            ),
        )
        val clockLabels = listOf("关闭", "操作时显示", "始终显示")
        group(
            "显示",
            listOf(
                optionRow("播放时钟", clockLabels.getOrElse(library.clockMode) { clockLabels[1] }) {
                    host.sheet.choose("播放时钟", clockLabels, library.clockMode) { index ->
                        library.clockMode = index
                        rebuild()
                    }
                },
                optionRow("启动页面", startupLabels.getOrElse(library.startupPage) { startupLabels[0] }) {
                    host.sheet.choose("启动页面", startupLabels, library.startupPage) { index ->
                        library.startupPage = index
                        rebuild()
                    }
                },
            ),
        )
        val accountStatus =
            TextView(context).apply {
                text = host.account.historyStatus
                setTextSize(TypedValue.COMPLEX_UNIT_SP, TouchTheme.TEXT_SECONDARY)
                setTextColor(TouchTheme.muted)
                setPadding(0, 0, 0, TouchTheme.dp(context, 8))
            }
        group(
            "账户",
            listOf(
                accountStatus,
                optionRow("登录与账户管理", host.account.accountLabel) { host.account.show() },
            ),
        )
        val updateRow = optionRow("版本与更新", version)
        updateRow.setOnClickListener {
            onCheckUpdates?.invoke(updateRow)
                ?: host.sheet.confirm("版本与更新", "当前版本 $version", "知道了") {}
        }
        group(
            "更新与关于",
            listOf(
                updateRow,
                optionRow("关于红果 TV", version) {
                    host.sheet.confirm(
                        "关于红果 TV",
                        "hongguoTV · 非官方客户端\n基于 N3urda/hongguoTV\n" +
                            "功能同步：tsukiyo-zero/hongguoTV\n版本：$version\n" +
                            "项目：https://github.com/N3urda/hongguoTV\n\n本应用与红果官方无隶属关系。",
                        "关闭",
                    ) {}
                },
            ),
        )
    }

    private fun accountCard(): View {
        val signed = host.account.signedIn
        val card =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(
                    TouchTheme.dp(context, 16),
                    TouchTheme.dp(context, 16),
                    TouchTheme.dp(context, 16),
                    TouchTheme.dp(context, 16),
                )
                background = TouchTheme.pressable(context, TouchTheme.surface)
                isClickable = true
                contentDescription = "账号"
                setOnClickListener { host.account.show() }
            }
        card.addView(
            TextView(context).apply {
                text = if (signed) host.account.accountLabel else "登录并同步"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                setTextColor(TouchTheme.text)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            },
        )
        card.addView(
            TextView(context).apply {
                text = if (signed) host.account.historyStatus else "登录后同步收藏、历史与预约"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, TouchTheme.TEXT_SECONDARY)
                setTextColor(TouchTheme.muted)
                setPadding(0, TouchTheme.dp(context, 6), 0, 0)
            },
        )
        return card
    }

    private fun group(title: String, rows: List<View>) {
        content.addView(
            sectionTitle(context, title).apply {
                setPadding(0, TouchTheme.dp(context, 16), 0, TouchTheme.dp(context, 6))
            },
        )
        rows.forEach { content.addView(it) }
    }

    private fun optionRow(label: String, value: String, onClick: (() -> Unit)? = null): LinearLayout {
        val row =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = TouchTheme.dp(context, TouchTheme.MIN_TARGET_DP)
                setPadding(
                    TouchTheme.dp(context, 12),
                    TouchTheme.dp(context, 10),
                    TouchTheme.dp(context, 12),
                    TouchTheme.dp(context, 10),
                )
                background = TouchTheme.pressable(context, TouchTheme.surface)
            }
        row.addView(
            TextView(context).apply {
                text = label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, TouchTheme.TEXT_BODY)
                setTextColor(TouchTheme.text)
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        row.addView(
            TextView(context).apply {
                text = value
                contentDescription = "$label，$value"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, TouchTheme.TEXT_SECONDARY)
                setTextColor(TouchTheme.muted)
            },
        )
        if (onClick != null) row.setOnClickListener { onClick() }
        return row
    }

    private fun toggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit): LinearLayout {
        val row =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = TouchTheme.dp(context, TouchTheme.MIN_TARGET_DP)
                setPadding(
                    TouchTheme.dp(context, 12),
                    TouchTheme.dp(context, 4),
                    TouchTheme.dp(context, 8),
                    TouchTheme.dp(context, 4),
                )
                background = TouchTheme.pressable(context, TouchTheme.surface)
            }
        row.addView(
            TextView(context).apply {
                text = label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, TouchTheme.TEXT_BODY)
                setTextColor(TouchTheme.text)
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        row.addView(
            Switch(context).apply {
                isChecked = checked
                contentDescription = label
                setOnCheckedChangeListener { _, value -> onChange(value) }
            },
        )
        return row
    }

    private companion object {
        val startupLabels = listOf("首页", "找剧", "排行榜", "收藏", "观看历史", "账户预约", "设置")
    }
}
