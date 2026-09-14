package com.atxz.coolapkdetailplus

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import java.lang.ref.WeakReference

/**
 * 文本检索匹配结果数据模型
 *
 * @property textViewRef 目标 TextView 的弱引用，防止内存泄漏
 * @property start 匹配文本在 TextView 中的起始索引
 * @property end 匹配文本在 TextView 中的结束索引
 * @property globalIndex 全局匹配项序号（0-based）
 * @property matchedKeyword 匹配的关键字文本
 * @property rvRef 所属的 RecyclerView 弱引用（若位于列表中）
 * @property adapterPosition 所属列表项在 RecyclerView 中的 Adapter 索引（若位于列表中）
 */
data class TextMatch(
    var textViewRef: WeakReference<TextView>,
    val start: Int,
    val end: Int,
    val globalIndex: Int,
    val matchedKeyword: String = "",
    val rvRef: WeakReference<ViewGroup>? = null,
    val adapterPosition: Int = -1
) {
    val textView: TextView? get() = textViewRef.get()
    val recyclerView: ViewGroup? get() = rvRef?.get()
}

/**
 * 页内全文检索与精准滚动引擎
 */
class SearchEngine {

    private val originalTexts = HashMap<TextView, CharSequence>()
    private val matches = ArrayList<TextMatch>()
    private var currentIndex = -1

    /**
     * 在指定根视图中检索包含指定关键字的 TextView 并初始化匹配项列表
     *
     * @param rootView 检索入口根视图
     * @param keyword 检索关键字
     * @return 匹配到的结果总数
     */
    fun search(rootView: View, keyword: String): Int {
        clearHighlights()
        matches.clear()
        currentIndex = -1

        if (keyword.isBlank()) {
            return 0
        }

        val textViews = ArrayList<TextView>()
        collectTextViews(rootView, textViews)

        val lowerKeyword = keyword.lowercase()
        var matchCounter = 0

        for (tv in textViews) {
            val text = tv.text?.toString() ?: continue
            if (text.isBlank()) continue

            val lowerText = text.lowercase()
            var start = 0
            val (rv, itemView) = findRecyclerViewAndItemView(tv) ?: Pair(null, null)
            val adapterPos = if (rv != null && itemView != null) getAdapterPosition(rv, itemView) else -1

            while (start < lowerText.length) {
                val foundIndex = lowerText.indexOf(lowerKeyword, start)
                if (foundIndex == -1) break

                if (!originalTexts.containsKey(tv)) {
                    originalTexts[tv] = tv.text
                }

                val matchEnd = foundIndex + lowerKeyword.length
                matches.add(
                    TextMatch(
                        textViewRef = WeakReference(tv),
                        start = foundIndex,
                        end = matchEnd,
                        globalIndex = matchCounter,
                        matchedKeyword = keyword,
                        rvRef = rv?.let { WeakReference(it) },
                        adapterPosition = adapterPos
                    )
                )
                matchCounter++
                start = matchEnd
            }
        }

        if (matches.isNotEmpty()) {
            currentIndex = 0
            applyHighlights()
        }

        return matches.size
    }

    /**
     * 切换到下一个匹配项并触发定位滚动
     *
     * @return 当前选中的匹配项索引（0-based）
     */
    fun next(): Int {
        if (matches.isEmpty()) return -1
        currentIndex = (currentIndex + 1) % matches.size
        applyHighlights()
        return currentIndex
    }

    /**
     * 切换到上一个匹配项并触发定位滚动
     *
     * @return 当前选中的匹配项索引（0-based）
     */
    fun previous(): Int {
        if (matches.isEmpty()) return -1
        currentIndex = if (currentIndex - 1 < 0) matches.size - 1 else currentIndex - 1
        applyHighlights()
        return currentIndex
    }

    /**
     * 获取当前选中的匹配项索引
     *
     * @return 当前索引（0-based）
     */
    fun getCurrentIndex(): Int = currentIndex

    /**
     * 获取检索到的匹配项总数
     *
     * @return 匹配项总数
     */
    fun getMatchCount(): Int = matches.size

    /**
     * 应用匹配文本的高亮着色效果
     */
    private fun applyHighlights() {
        // 按 TextView 分组处理高亮
        val grouped = matches.groupBy { it.textView }
        for ((tv, matchGroup) in grouped) {
            if (tv == null || !tv.isAttachedToWindow) continue
            try {
                val orig = originalTexts[tv] ?: tv.text
                val builder = SpannableStringBuilder(orig)

                for (m in matchGroup) {
                    if (m.start >= 0 && m.end <= builder.length) {
                        if (m.globalIndex == currentIndex) {
                            // 当前活动匹配项：醒目橙底白字
                            builder.setSpan(
                                BackgroundColorSpan(Color.parseColor("#FF9800")),
                                m.start,
                                m.end,
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                            builder.setSpan(
                                ForegroundColorSpan(Color.WHITE),
                                m.start,
                                m.end,
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                        } else {
                            // 其他匹配项：淡黄底黑字
                            builder.setSpan(
                                BackgroundColorSpan(Color.parseColor("#FFEE58")),
                                m.start,
                                m.end,
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                            builder.setSpan(
                                ForegroundColorSpan(Color.BLACK),
                                m.start,
                                m.end,
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                        }
                    }
                }
                tv.text = builder
            } catch (_: Throwable) {
                // 忽略高亮着色异常
            }
        }

        scrollToActiveMatch()
    }

    /**
     * 精准滚动定位到当前活动匹配项所在的行及屏幕位置
     */
    private fun scrollToActiveMatch() {
        if (currentIndex !in matches.indices) return

        try {
            val activeMatch = matches[currentIndex]
            val tv = activeMatch.textView

            // 1. 如果 tv 存在且仍附加在 Window 上，执行精准行级滚动
            if (tv != null && tv.isAttachedToWindow) {
                tv.post {
                    performScrollToMatch(tv, activeMatch)
                }
                return
            }

            // 2. 如果 tv 已被 RecyclerView 离屏分离，通过 LayoutManager 重新带回视野
            val rv = activeMatch.recyclerView
            val pos = activeMatch.adapterPosition
            if (rv != null && pos >= 0 && rv.isAttachedToWindow) {
                val ctx = rv.context
                val desiredY = calculateDesiredScreenY(rv)
                val rvLoc = IntArray(2)
                rv.getLocationOnScreen(rvLoc)
                val offsetInRv = (desiredY - rvLoc[1]).coerceAtLeast(0)

                LogManager.log(
                    ctx,
                    "CoolapkDetailPlus",
                    "[Scroll] 匹配项[${currentIndex + 1}/${matches.size}]处于离屏状态，通过 RecyclerView 滚动至 Position=$pos, Offset=$offsetInRv"
                )

                scrollToPositionWithOffset(rv, pos, offsetInRv)

                // 等待 RecyclerView 完成布局更新后，重新获取对应 TextView 并精确定位
                rv.postDelayed({
                    try {
                        val recoveredTv = findTvInPosition(rv, pos, activeMatch.matchedKeyword)
                        if (recoveredTv != null) {
                            activeMatch.textViewRef = WeakReference(recoveredTv)
                            if (!originalTexts.containsKey(recoveredTv)) {
                                originalTexts[recoveredTv] = recoveredTv.text
                            }
                            applyHighlights()
                            performScrollToMatch(recoveredTv, activeMatch)
                        }
                    } catch (t: Throwable) {
                        LogManager.log(ctx, "CoolapkDetailPlus", "[Error] 恢复离屏 View 异常: ${t.message}")
                    }
                }, 120)
            }
        } catch (t: Throwable) {
            // 忽略外部滚动异常
        }
    }

    /**
     * 执行具体的行级精准滚动计算与容器滑动
     *
     * @param tv 目标 TextView
     * @param match 匹配信息数据
     */
    private fun performScrollToMatch(tv: TextView, match: TextMatch) {
        if (!tv.isAttachedToWindow) return
        val context = tv.context ?: return

        try {
            val layout = tv.layout
            val textLen = tv.text?.length ?: 0
            val start = match.start.coerceIn(0, (textLen - 1).coerceAtLeast(0))

            // 计算匹配行在 TextView 内容中的像素偏移
            var lineTop = 0
            var lineBottom = tv.height
            var targetLine = 0
            if (layout != null && start in 0..layout.text.length) {
                targetLine = layout.getLineForOffset(start)
                lineTop = layout.getLineTop(targetLine)
                lineBottom = layout.getLineBottom(targetLine)
            }

            // 计算匹配项在屏幕上的精确绝对 Y 坐标
            val tvLocation = IntArray(2)
            tv.getLocationOnScreen(tvLocation)
            val matchScreenY = tvLocation[1] + tv.totalPaddingTop - tv.scrollY + lineTop

            // 计算期望的目标停靠 Y 坐标（动态避开顶部悬浮栏）
            val desiredY = calculateDesiredScreenY(tv)

            // 计算垂直滑动距离：dy > 0 表示在目标下方需向上滚；dy < 0 表示在上方需向下滚
            val dy = matchScreenY - desiredY

            val container = findScrollingContainer(tv)
            if (container != null) {
                scrollContainerBy(container, dy)
                LogManager.log(
                    context,
                    "CoolapkDetailPlus",
                    "[Scroll] 精准滚动至[${match.globalIndex + 1}/${matches.size}] (行:$targetLine): 目标Y=$desiredY, 当前Y=$matchScreenY, dy=$dy, 容器=${container.javaClass.simpleName}"
                )
            } else {
                // 备选方案：使用合法的正数局部矩形区域发起滚动请求
                val clearance = (lineBottom - lineTop).coerceAtLeast(dip2px(context, 40f))
                val safeTop = (lineTop - clearance).coerceAtLeast(0)
                val rect = Rect(0, safeTop, tv.width, lineBottom)
                tv.requestRectangleOnScreen(rect, false)
                LogManager.log(
                    context,
                    "CoolapkDetailPlus",
                    "[Scroll] 备用方案请求区域: $rect"
                )
            }
        } catch (t: Throwable) {
            LogManager.log(context, "CoolapkDetailPlus", "[Error] performScrollToMatch 异常: ${t.message}")
        }
    }

    /**
     * 计算匹配项期望在屏幕上停靠的绝对 Y 坐标，动态避开顶部悬浮检索栏
     *
     * @param anchorView 参考视图
     * @return 期望的屏幕 Y 像素坐标
     */
    private fun calculateDesiredScreenY(anchorView: View): Int {
        val context = anchorView.context ?: return 350
        val defaultDesiredY = dip2px(context, 130f)

        return try {
            val decorView = anchorView.rootView as? ViewGroup
            val searchBar = decorView?.findViewWithTag<View>("COOLAPK_DETAIL_SEARCH_BAR")
            if (searchBar != null && searchBar.isAttachedToWindow) {
                val barLoc = IntArray(2)
                searchBar.getLocationOnScreen(barLoc)
                // 停靠在搜索栏底部并保留 16dp 安全呼吸间距
                barLoc[1] + searchBar.height + dip2px(context, 16f)
            } else {
                defaultDesiredY
            }
        } catch (_: Throwable) {
            defaultDesiredY
        }
    }

    /**
     * 跨 ClassLoader 安全判断视图是否属于 RecyclerView 体系
     *
     * @param view 待检测视图
     * @return 是否为 RecyclerView
     */
    private fun isRecyclerView(view: View?): Boolean {
        if (view == null) return false
        var clazz: Class<*>? = view.javaClass
        while (clazz != null && clazz != Any::class.java) {
            val name = clazz.name
            if (name.contains("RecyclerView") || name.endsWith(".RecyclerView")) {
                return true
            }
            clazz = clazz.superclass
        }
        return false
    }

    /**
     * 跨 ClassLoader 安全判断视图是否属于 ScrollView 或 NestedScrollView
     *
     * @param view 待检测视图
     * @return 是否为 ScrollView/NestedScrollView
     */
    private fun isScrollView(view: View?): Boolean {
        if (view == null) return false
        var clazz: Class<*>? = view.javaClass
        while (clazz != null && clazz != Any::class.java) {
            val name = clazz.name
            if (name.contains("ScrollView") ||
                name.contains("NestedScrollView") ||
                clazz == android.widget.ScrollView::class.java
            ) {
                return true
            }
            clazz = clazz.superclass
        }
        return false
    }

    /**
     * 沿 View 层级向上寻找最近的可滚动容器
     *
     * @param view 初始子视图
     * @return 最近的可滚动容器 View，未找到返回 null
     */
    private fun findScrollingContainer(view: View): View? {
        var curr: View? = view.parent as? View
        while (curr != null) {
            if (isRecyclerView(curr) || isScrollView(curr) || curr is android.widget.AbsListView) {
                return curr
            }
            curr = curr.parent as? View
        }
        return null
    }

    /**
     * 向上查找所属的 RecyclerView 以及直接 itemView 子视图
     *
     * @param view 目标视图
     * @return Pair(RecyclerView 容器, 对应的 itemView 直接子视图)，若不在 RecyclerView 中返回 null
     */
    private fun findRecyclerViewAndItemView(view: View): Pair<ViewGroup, View>? {
        var curr: View? = view
        while (curr != null) {
            val parent = curr.parent as? ViewGroup
            if (parent != null && isRecyclerView(parent)) {
                return Pair(parent, curr)
            }
            curr = parent
        }
        return null
    }

    /**
     * 获取直接子项在 RecyclerView 适配器中的位置
     *
     * @param recyclerView RecyclerView 容器
     * @param itemView 直接子项视图
     * @return 适配器位置索引，若获取失败返回 -1
     */
    private fun getAdapterPosition(recyclerView: ViewGroup, itemView: View): Int {
        return try {
            val method = recyclerView.javaClass.getMethod("getChildAdapterPosition", View::class.java)
            val pos = method.invoke(recyclerView, itemView) as? Int ?: -1
            if (pos != -1) return pos
            val layoutMethod = recyclerView.javaClass.getMethod("getChildLayoutPosition", View::class.java)
            (layoutMethod.invoke(recyclerView, itemView) as? Int) ?: -1
        } catch (_: Throwable) {
            -1
        }
    }

    /**
     * 滚动指定的滑动容器
     *
     * @param container 滑动容器（RecyclerView, ScrollView, NestedScrollView 等）
     * @param dy 需要滚动的垂直像素数（正数向上滚，负数向下滚）
     */
    private fun scrollContainerBy(container: View, dy: Int) {
        if (dy == 0) return
        try {
            if (isRecyclerView(container)) {
                val method = container.javaClass.getMethod(
                    "smoothScrollBy",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                )
                method.invoke(container, 0, dy)
            } else if (isScrollView(container)) {
                try {
                    val method = container.javaClass.getMethod(
                        "smoothScrollBy",
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType
                    )
                    method.invoke(container, 0, dy)
                } catch (_: Throwable) {
                    container.scrollBy(0, dy)
                }
            } else {
                container.scrollBy(0, dy)
            }
        } catch (_: Throwable) {
            try {
                container.scrollBy(0, dy)
            } catch (_: Throwable) {
                // 忽略二次异常
            }
        }
    }

    /**
     * 反射调用 RecyclerView 内部 LayoutManager 的 scrollToPositionWithOffset
     *
     * @param recyclerView 目标 RecyclerView
     * @param position 列表项下标
     * @param offset 目标顶部偏移像素
     */
    private fun scrollToPositionWithOffset(recyclerView: ViewGroup, position: Int, offset: Int) {
        try {
            val getLmMethod = recyclerView.javaClass.getMethod("getLayoutManager")
            val lm = getLmMethod.invoke(recyclerView)
            if (lm != null) {
                val scrollToPosOffset = lm.javaClass.getMethod(
                    "scrollToPositionWithOffset",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                )
                scrollToPosOffset.invoke(lm, position, offset)
                return
            }
        } catch (_: Throwable) {
            // 忽略 LayoutManager 异常
        }
        try {
            val scrollToPos = recyclerView.javaClass.getMethod(
                "scrollToPosition",
                Int::class.javaPrimitiveType
            )
            scrollToPos.invoke(recyclerView, position)
        } catch (_: Throwable) {
            // 忽略异常
        }
    }

    /**
     * 在 RecyclerView 指定位置的 item 重新进入视野后，查找包含关键字的 TextView
     *
     * @param recyclerView RecyclerView 容器
     * @param position 列表项下标
     * @param keyword 检索关键字
     * @return 重新匹配到的 TextView，若未找到返回 null
     */
    private fun findTvInPosition(recyclerView: ViewGroup, position: Int, keyword: String): TextView? {
        return try {
            val getLmMethod = recyclerView.javaClass.getMethod("getLayoutManager")
            val lm = getLmMethod.invoke(recyclerView) ?: return null
            val findViewByPosMethod = lm.javaClass.getMethod("findViewByPosition", Int::class.javaPrimitiveType)
            val itemView = findViewByPosMethod.invoke(lm, position) as? View ?: return null

            val tvList = ArrayList<TextView>()
            collectTextViews(itemView, tvList)
            val lowerKeyword = keyword.lowercase()
            tvList.firstOrNull { it.text?.toString()?.lowercase()?.contains(lowerKeyword) == true }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * 清理所有匹配项的高亮样式，还原原始文本
     */
    fun clearHighlights() {
        for ((tv, orig) in originalTexts) {
            try {
                if (tv.isAttachedToWindow) {
                    tv.text = orig
                }
            } catch (_: Throwable) {
                // 忽略清理异常
            }
        }
        originalTexts.clear()
        matches.clear()
        currentIndex = -1
    }

    /**
     * 深度优先遍历视图树收集所有可见且非空的 TextView
     *
     * @param view 当前遍历节点
     * @param result 收集结果列表
     */
    private fun collectTextViews(view: View, result: MutableList<TextView>) {
        try {
            if (view.visibility != View.VISIBLE) return

            // 跳过检索悬浮栏自身及其子组件
            if (view.tag == "COOLAPK_DETAIL_SEARCH_BAR") return

            if (view is TextView) {
                if (!view.text.isNullOrBlank()) {
                    result.add(view)
                }
            } else if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    val child = view.getChildAt(i) ?: continue
                    collectTextViews(child, result)
                }
            }
        } catch (_: Throwable) {
            // 忽略遍历异常
        }
    }

    /**
     * 将 dp 数值转换为当前屏幕的 px 像素值
     *
     * @param context 上下文
     * @param dpValue dp 数值
     * @return 像素值
     */
    private fun dip2px(context: Context, dpValue: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dpValue,
            context.resources.displayMetrics
        ).toInt()
    }
}
