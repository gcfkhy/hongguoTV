// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp.touch

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

/**
 * Modal sheets used by the touch UI: bottom sheets on compact windows and
 * centered cards (max 480dp) on medium and expanded windows.
 */
internal class TouchSheet(private val activity: Activity) {
    data class ToggleRow(val label: String, val checked: Boolean, val onChange: (Boolean) -> Unit)

    private var dialog: Dialog? = null

    val showing: Boolean get() = dialog?.isShowing == true

    fun choose(title: String, options: List<String>, selected: Int, onPick: (Int) -> Unit) {
        val panel = panel(title)
        val list = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        options.forEachIndexed { index, label ->
            list.addView(
                row(if (index == selected) "✓ $label" else label) {
                    dismiss()
                    onPick(index)
                },
            )
        }
        panel.addView(scroll(list))
        present(panel)
    }

    fun toggles(title: String, rows: List<ToggleRow>) {
        val panel = panel(title)
        val list = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        rows.forEach { item ->
            val switch =
                Switch(activity).apply {
                    isChecked = item.checked
                    contentDescription = item.label
                    setOnCheckedChangeListener { _, checked -> item.onChange(checked) }
                }
            val line =
                LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(TouchTheme.dp(activity, 16), 0, TouchTheme.dp(activity, 8), 0)
                    addView(
                        TextView(activity).apply {
                            text = item.label
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, TouchTheme.TEXT_BODY)
                            setTextColor(TouchTheme.text)
                        },
                        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
                    )
                    addView(switch)
                }
            list.addView(line, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, TouchTheme.dp(activity, TouchTheme.MIN_TARGET_DP)))
        }
        panel.addView(scroll(list))
        present(panel)
    }

    fun grid(title: String, count: Int, current: Int, marks: Map<Int, String>, onPick: (Int) -> Unit) {
        val panel = panel(title)
        val grid = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        var row: LinearLayout? = null
        for (index in 0 until count) {
            if (index % 5 == 0) {
                row =
                    LinearLayout(activity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER
                    }
                grid.addView(row)
            }
            val mark = marks[index]
            val cell =
                TextView(activity).apply {
                    text = if (mark.isNullOrBlank()) "${index + 1}" else "${index + 1}\n$mark"
                    contentDescription = "第 ${index + 1} 集"
                    gravity = Gravity.CENTER
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, TouchTheme.TEXT_BODY)
                    setTextColor(if (index == current) TouchTheme.accent else TouchTheme.text)
                    minHeight = TouchTheme.dp(activity, TouchTheme.MIN_TARGET_DP)
                    background =
                        TouchTheme.pressable(
                            activity,
                            if (index == current) Color.argb(50, 255, 120, 68) else TouchTheme.raised,
                            8,
                        )
                    setOnClickListener {
                        dismiss()
                        onPick(index)
                    }
                }
            row!!.addView(
                cell,
                LinearLayout.LayoutParams(0, TouchTheme.dp(activity, 52), 1f).apply {
                    marginEnd = TouchTheme.dp(activity, 6)
                    bottomMargin = TouchTheme.dp(activity, 6)
                },
            )
        }
        panel.addView(scroll(grid))
        present(panel)
    }

    fun input(title: String, hint: String, initial: String, onDone: (String?) -> Unit) {
        val panel = panel(title)
        val field =
            EditText(activity).apply {
                this.hint = hint
                setText(initial)
                inputType = InputType.TYPE_CLASS_TEXT
                setSingleLine(true)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, TouchTheme.TEXT_BODY)
                setTextColor(TouchTheme.text)
                setHintTextColor(TouchTheme.muted)
                setPadding(
                    TouchTheme.dp(activity, 16),
                    TouchTheme.dp(activity, 12),
                    TouchTheme.dp(activity, 16),
                    TouchTheme.dp(activity, 12),
                )
                background = TouchTheme.pressable(activity, TouchTheme.raised, 8)
            }
        panel.addView(field, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        panel.addView(
            buttonRow(
                "取消" to {
                    dismiss()
                    onDone(null)
                },
                "确定" to {
                    val text = field.text.toString()
                    dismiss()
                    onDone(text)
                },
            ),
        )
        present(panel)
    }

    fun confirm(title: String, message: String, confirmLabel: String, onConfirm: () -> Unit) {
        val panel = panel(title)
        panel.addView(
            TextView(activity).apply {
                text = message
                setTextSize(TypedValue.COMPLEX_UNIT_SP, TouchTheme.TEXT_BODY)
                setTextColor(TouchTheme.muted)
                setPadding(
                    TouchTheme.dp(activity, 16),
                    TouchTheme.dp(activity, 8),
                    TouchTheme.dp(activity, 16),
                    TouchTheme.dp(activity, 8),
                )
            },
        )
        panel.addView(
            buttonRow(
                "取消" to { dismiss() },
                confirmLabel to {
                    dismiss()
                    onConfirm()
                },
            ),
        )
        present(panel)
    }

    fun custom(content: View, title: String? = null) {
        val panel = panel(title)
        panel.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        present(panel)
    }

    fun dismiss() {
        val current = dialog
        dialog = null
        current?.dismiss()
    }

    private fun panel(title: String?): LinearLayout {
        val panel =
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                background =
                    GradientDrawable().apply {
                        setColor(TouchTheme.surface)
                        cornerRadius = TouchTheme.dp(activity, TouchTheme.SHEET_RADIUS_DP).toFloat()
                    }
                setPadding(
                    TouchTheme.dp(activity, 8),
                    TouchTheme.dp(activity, 8),
                    TouchTheme.dp(activity, 8),
                    TouchTheme.dp(activity, 16),
                )
            }
        if (WindowClass.of(activity) == WindowClass.Compact) {
            panel.addView(
                View(activity).apply { setBackgroundColor(TouchTheme.outline) },
                LinearLayout.LayoutParams(TouchTheme.dp(activity, 40), TouchTheme.dp(activity, 4)).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    topMargin = TouchTheme.dp(activity, 6)
                    bottomMargin = TouchTheme.dp(activity, 6)
                },
            )
        }
        if (title != null) {
            panel.addView(
                TextView(activity).apply {
                    text = title
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                    setTextColor(TouchTheme.text)
                    setTypeface(typeface, Typeface.BOLD)
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                    setPadding(
                        TouchTheme.dp(activity, 16),
                        TouchTheme.dp(activity, 10),
                        TouchTheme.dp(activity, 16),
                        TouchTheme.dp(activity, 6),
                    )
                },
            )
        }
        return panel
    }

    private fun row(label: String, onClick: () -> Unit): TextView =
        TextView(activity).apply {
            text = label
            contentDescription = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, TouchTheme.TEXT_BODY)
            setTextColor(TouchTheme.text)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                TouchTheme.dp(activity, 16),
                0,
                TouchTheme.dp(activity, 16),
                0,
            )
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
            background = TouchTheme.pressable(activity, Color.TRANSPARENT, 8)
            setOnClickListener { onClick() }
        }.also {
            it.layoutParams =
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    TouchTheme.dp(activity, TouchTheme.MIN_TARGET_DP),
                )
        }

    private fun buttonRow(vararg buttons: Pair<String, () -> Unit>): LinearLayout =
        LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(
                TouchTheme.dp(activity, 8),
                TouchTheme.dp(activity, 12),
                TouchTheme.dp(activity, 8),
                0,
            )
            buttons.forEach { (label, action) ->
                addView(
                    TextView(activity).apply {
                        text = label
                        contentDescription = label
                        gravity = Gravity.CENTER
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, TouchTheme.TEXT_BODY)
                        setTextColor(TouchTheme.accent)
                        minimumHeight = TouchTheme.dp(activity, TouchTheme.MIN_TARGET_DP)
                        minimumWidth = TouchTheme.dp(activity, TouchTheme.MIN_TARGET_DP)
                        setPadding(
                            TouchTheme.dp(activity, 16),
                            0,
                            TouchTheme.dp(activity, 16),
                            0,
                        )
                        background = TouchTheme.pressable(activity, TouchTheme.raised, 8)
                        setOnClickListener { action() }
                    },
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        TouchTheme.dp(activity, TouchTheme.MIN_TARGET_DP),
                    ).apply { marginStart = TouchTheme.dp(activity, 8) },
                )
            }
        }

    private fun scroll(content: View): ScrollView {
        val maxHeight = (activity.resources.displayMetrics.heightPixels * .7f).toInt()
        return MaxHeightScrollView(activity, maxHeight).apply {
            addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun present(content: View) {
        dismiss()
        val windowClass = WindowClass.of(activity)
        val compact = windowClass == WindowClass.Compact
        val sheet = Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar)
        sheet.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val frame = FrameLayout(activity)
        val screenWidth = activity.resources.displayMetrics.widthPixels
        val width =
            if (compact) ViewGroup.LayoutParams.MATCH_PARENT
            else minOf(TouchTheme.dp(activity, 480), screenWidth)
        frame.addView(
            content,
            FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT, if (compact) Gravity.BOTTOM else Gravity.CENTER),
        )
        sheet.setContentView(frame)
        sheet.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.setDimAmount(.55f)
            window.attributes =
                window.attributes.apply {
                    this.width = width
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                    gravity = if (compact) Gravity.BOTTOM else Gravity.CENTER
                }
        }
        sheet.setCanceledOnTouchOutside(true)
        sheet.setOnDismissListener { if (dialog === sheet) dialog = null }
        dialog = sheet
        sheet.show()
    }

    private class MaxHeightScrollView(context: Context, private val maxHeight: Int) : ScrollView(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(
                widthMeasureSpec,
                MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST),
            )
        }
    }
}
