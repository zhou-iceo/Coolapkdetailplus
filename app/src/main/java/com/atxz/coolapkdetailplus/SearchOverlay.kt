package com.atxz.coolapkdetailplus

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

object SearchOverlay {

    private const val SEARCH_BAR_TAG = "COOLAPK_DETAIL_SEARCH_BAR"

    /**
     * 在当前 Activity 窗口顶部显示页内搜索悬浮交互栏
     *
     * @param activity 当前宿主 Activity
     */
    fun show(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return

        try {
            val decorView = activity.window?.decorView as? ViewGroup ?: return

            // If already showing, clear and focus
            val existingBar = decorView.findViewWithTag<View>(SEARCH_BAR_TAG)
            if (existingBar != null) {
                existingBar.bringToFront()
                val input = existingBar.findViewById<EditText>(android.R.id.input)
                input?.requestFocus()
                return
            }

            val engine = SearchEngine()

            // Container Layout
            val container = FrameLayout(activity).apply {
                tag = SEARCH_BAR_TAG
                elevation = dip2px(activity, 12f).toFloat()
            }

            val containerParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                val margin = dip2px(activity, 12f)
                val topMargin = dip2px(activity, 48f)
                setMargins(margin, topMargin, margin, 0)
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            }

            // Card Panel Background
            val cardBackground = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dip2px(activity, 16f).toFloat()
                setColor(Color.parseColor("#FAF8F5"))
                setStroke(dip2px(activity, 1f), Color.parseColor("#E0E0E0"))
            }

            val mainLayout = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = cardBackground
                setPadding(
                    dip2px(activity, 12f),
                    dip2px(activity, 8f),
                    dip2px(activity, 12f),
                    dip2px(activity, 8f)
                )
            }

            // Search Icon
            val searchIcon = TextView(activity).apply {
                text = "🔍"
                textSize = 16f
                gravity = Gravity.CENTER
                setPadding(0, 0, dip2px(activity, 8f), 0)
            }

            // Input EditText
            val inputEdit = EditText(activity).apply {
                id = android.R.id.input
                hint = "页内查找..."
                setHintTextColor(Color.GRAY)
                setTextColor(Color.parseColor("#212121"))
                background = null
                textSize = 15f
                isSingleLine = true
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            // Match Info Badge
            val infoBadge = TextView(activity).apply {
                text = "0/0"
                textSize = 12f
                setTextColor(Color.parseColor("#757575"))
                typeface = Typeface.MONOSPACE
                setPadding(dip2px(activity, 4f), 0, dip2px(activity, 8f), 0)
            }

            // Helper to perform search
            fun doSearch() {
                try {
                    val query = inputEdit.text.toString()
                    val total = engine.search(decorView, query)
                    if (total > 0) {
                        val current = engine.getCurrentIndex() + 1
                        infoBadge.text = "$current/$total"
                        infoBadge.setTextColor(Color.parseColor("#2E7D32"))
                        LogManager.log(activity, "CoolapkDetailPlus", "[Search] 检索关键字: '$query' -> 找到 $total 处匹配")
                    } else if (query.isNotBlank()) {
                        infoBadge.text = "0/0"
                        infoBadge.setTextColor(Color.parseColor("#D32F2F"))
                        LogManager.log(activity, "CoolapkDetailPlus", "[Search] 检索关键字: '$query' -> 未找到匹配内容")
                    } else {
                        infoBadge.text = "0/0"
                        infoBadge.setTextColor(Color.parseColor("#757575"))
                    }
                } catch (t: Throwable) {
                    LogManager.log(activity, "CoolapkDetailPlus", "[Error] doSearch exception: ${t.message}")
                }
            }

            inputEdit.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    doSearch()
                }
            })

            // Prev Button
            val prevBtn = TextView(activity).apply {
                text = "▲"
                textSize = 14f
                setTextColor(Color.parseColor("#424242"))
                gravity = Gravity.CENTER
                setPadding(dip2px(activity, 6f), dip2px(activity, 4f), dip2px(activity, 6f), dip2px(activity, 4f))
                setOnClickListener {
                    try {
                        val idx = engine.previous()
                        val total = engine.getMatchCount()
                        if (total > 0) {
                            infoBadge.text = "${idx + 1}/$total"
                            infoBadge.setTextColor(Color.parseColor("#2E7D32"))
                        }
                    } catch (t: Throwable) {
                        LogManager.log(activity, "CoolapkDetailPlus", "[Error] prevBtn click exception: ${t.message}")
                    }
                }
            }

            // Next Button
            val nextBtn = TextView(activity).apply {
                text = "▼"
                textSize = 14f
                setTextColor(Color.parseColor("#424242"))
                gravity = Gravity.CENTER
                setPadding(dip2px(activity, 6f), dip2px(activity, 4f), dip2px(activity, 6f), dip2px(activity, 4f))
                setOnClickListener {
                    try {
                        val idx = engine.next()
                        val total = engine.getMatchCount()
                        if (total > 0) {
                            infoBadge.text = "${idx + 1}/$total"
                            infoBadge.setTextColor(Color.parseColor("#2E7D32"))
                        }
                    } catch (t: Throwable) {
                        LogManager.log(activity, "CoolapkDetailPlus", "[Error] nextBtn click exception: ${t.message}")
                    }
                }
            }

            // Close Button
            val closeBtn = TextView(activity).apply {
                text = "✕"
                textSize = 16f
                setTextColor(Color.parseColor("#757575"))
                gravity = Gravity.CENTER
                setPadding(dip2px(activity, 8f), dip2px(activity, 4f), dip2px(activity, 4f), dip2px(activity, 4f))
                setOnClickListener {
                    try {
                        engine.clearHighlights()
                        decorView.removeView(container)
                    } catch (t: Throwable) {
                        LogManager.log(activity, "CoolapkDetailPlus", "[Error] closeBtn click exception: ${t.message}")
                    }
                }
            }

            mainLayout.addView(searchIcon)
            mainLayout.addView(inputEdit)
            mainLayout.addView(infoBadge)
            mainLayout.addView(prevBtn)
            mainLayout.addView(nextBtn)
            mainLayout.addView(closeBtn)

            container.addView(mainLayout)
            decorView.addView(container, containerParams)

            inputEdit.postDelayed({
                try {
                    if (!activity.isFinishing && !activity.isDestroyed) {
                        inputEdit.requestFocus()
                    }
                } catch (t: Throwable) {
                    // Ignore
                }
            }, 100)
        } catch (t: Throwable) {
            LogManager.log(activity, "CoolapkDetailPlus", "[Error] SearchOverlay.show exception: ${t.message}")
        }
    }

    /**
     * 将 dp 数值转换为 px 像素
     *
     * @param activity 当前宿主 Activity
     * @param dpValue dp 数值
     * @return 转换后的像素值
     */
    private fun dip2px(activity: Activity, dpValue: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dpValue,
            activity.resources.displayMetrics
        ).toInt()
    }
}
