// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp.touch

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes

private fun targetSize(context: Context) = TouchTheme.dp(context, TouchTheme.MIN_TARGET_DP)

internal fun dp(context: Context, value: Number) = TouchTheme.dp(context, value)

/** Icon-only button with a 48dp touch target and an accessibility label. */
internal fun iconButton(
    context: Context,
    @DrawableRes icon: Int,
    description: String,
    onClick: () -> Unit,
): ImageButton {
    val size = targetSize(context)
    return ImageButton(context).apply {
        setImageResource(icon)
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        setColorFilter(TouchTheme.text)
        contentDescription = description
        background = TouchTheme.pressable(context, Color.TRANSPARENT, TouchTheme.MIN_TARGET_DP / 2)
        minimumWidth = size
        minimumHeight = size
        setPadding(TouchTheme.dp(context, 12), TouchTheme.dp(context, 12), TouchTheme.dp(context, 12), TouchTheme.dp(context, 12))
        setOnClickListener { onClick() }
    }
}

/** Rounded selection chip; text turns accent colored when selected. */
internal fun chip(context: Context, label: String, selected: Boolean, onClick: () -> Unit): TextView {
    val size = targetSize(context)
    return TextView(context).apply {
        text = label
        contentDescription = label
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, TouchTheme.TEXT_SECONDARY)
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.END
        setTextColor(if (selected) TouchTheme.accent else TouchTheme.text)
        minimumWidth = size
        minimumHeight = size
        setPadding(TouchTheme.dp(context, 16), 0, TouchTheme.dp(context, 16), 0)
        background =
            TouchTheme.pressable(
                context,
                if (selected) Color.argb(50, 255, 120, 68) else TouchTheme.surface,
                TouchTheme.MIN_TARGET_DP / 2,
            )
        setOnClickListener { onClick() }
    }
}

/** Horizontal segmented selector used by discover, library and detail screens. */
internal class SegmentedControl(
    context: Context,
    private val labels: List<String>,
    selected: Int,
    private val onSelect: (Int) -> Unit,
) : LinearLayout(context) {
    private var selectedIndex = selected.coerceIn(0, maxOf(0, labels.lastIndex))
    private val items = mutableListOf<TextView>()

    init {
        orientation = HORIZONTAL
        setPadding(TouchTheme.dp(context, 4), TouchTheme.dp(context, 4), TouchTheme.dp(context, 4), TouchTheme.dp(context, 4))
        background = TouchTheme.pressable(context, TouchTheme.surface, TouchTheme.MIN_TARGET_DP / 2)
        labels.forEachIndexed { index, label ->
            val item =
                TextView(context).apply {
                    text = label
                    contentDescription = label
                    gravity = Gravity.CENTER
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, TouchTheme.TEXT_BODY)
                    isSingleLine = true
                    ellipsize = TextUtils.TruncateAt.END
                    setOnClickListener { select(index) }
                }
            items += item
            addView(item, LayoutParams(0, targetSize(context), 1f))
        }
        render()
    }

    fun select(index: Int) {
        if (index !in labels.indices) return
        val changed = index != selectedIndex
        selectedIndex = index
        render()
        if (changed) onSelect(index)
    }

    private fun render() {
        items.forEachIndexed { index, item ->
            val on = index == selectedIndex
            item.isSelected = on
            item.setTextColor(if (on) TouchTheme.accent else TouchTheme.muted)
            item.background =
                TouchTheme.pressable(
                    context,
                    if (on) Color.argb(50, 255, 120, 68) else Color.TRANSPARENT,
                    TouchTheme.MIN_TARGET_DP / 2 - 4,
                )
        }
    }
}

/** Centered message with an optional call to action. */
internal fun emptyState(
    context: Context,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
): View {
    val column =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(
                TouchTheme.dp(context, 24),
                TouchTheme.dp(context, 32),
                TouchTheme.dp(context, 24),
                TouchTheme.dp(context, 32),
            )
        }
    column.addView(
        TextView(context).apply {
            text = message
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, TouchTheme.TEXT_BODY)
            setTextColor(TouchTheme.muted)
            maxLines = 3
        },
    )
    if (actionLabel != null) {
        column.addView(
            TextView(context).apply {
                text = actionLabel
                contentDescription = actionLabel
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, TouchTheme.TEXT_BODY)
                setTextColor(TouchTheme.accent)
                minimumWidth = targetSize(context)
                minimumHeight = targetSize(context)
                setPadding(TouchTheme.dp(context, 20), 0, TouchTheme.dp(context, 20), 0)
                background = TouchTheme.pressable(context, TouchTheme.raised, TouchTheme.MIN_TARGET_DP / 2)
                setOnClickListener { onAction?.invoke() }
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                targetSize(context),
            ).apply {
                topMargin = TouchTheme.dp(context, 12)
            },
        )
    }
    return column
}

internal fun sectionTitle(context: Context, title: String): TextView =
    TextView(context).apply {
        text = title
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        setTextColor(TouchTheme.text)
        setTypeface(typeface, Typeface.BOLD)
        setPadding(0, TouchTheme.dp(context, 12), 0, TouchTheme.dp(context, 6))
    }
