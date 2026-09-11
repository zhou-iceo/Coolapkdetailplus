package com.atxz.coolapkdetailplus

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.type.android.ActivityClass
import com.highcapable.yukihookapi.hook.type.android.DialogClass
import java.lang.ref.WeakReference

object CoolapkHook : YukiBaseHooker() {

    private var currentActivityRef: WeakReference<Activity>? = null

    override fun onHook() {

        LogManager.log(appContext, "CoolapkDetailPlus", "[YukiHookAPI] 框架 Hook 初始化成功")

        // 0. Safety Fix for missing appComponentFactory (androidx.core.app.CoreComponentFactory)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            try {
                val factory = appInfo.appComponentFactory
                if (factory != null && factory.contains("CoreComponentFactory")) {
                    val canLoad = runCatching {
                        appClassLoader?.loadClass(factory)
                    }.isSuccess
                    if (!canLoad) {
                        appInfo.appComponentFactory = "android.app.AppComponentFactory"
                        LogManager.log(appContext, "CoolapkDetailPlus", "[Fix] 宿主缺失 $factory，已重置为 android.app.AppComponentFactory")
                    }
                }
            } catch (t: Throwable) {
                LogManager.log(appContext, "CoolapkDetailPlus", "[Warning] 校验 appComponentFactory 失败: ${t.message}")
            }
        }

        // 1. Global Activity Lifecycle Tracking
        ActivityClass.hook {
            injectMember {
                method {
                    name = "onResume"
                }
                afterHook {
                    try {
                        val act = instance as? Activity ?: return@afterHook
                        currentActivityRef = WeakReference(act)

                        if (isFeedDetailActivity(act)) {
                            LogManager.log(act, "CoolapkDetailPlus", "[Activity] 已进入内容详情页: ${act.javaClass.name}")
                        }
                    } catch (t: Throwable) {
                        LogManager.log(appContext, "CoolapkDetailPlus", "[Error] onResume hook exception: ${t.message}")
                    }
                }
            }

            injectMember {
                method {
                    name = "onDestroy"
                }
                afterHook {
                    try {
                        val act = instance as? Activity ?: return@afterHook
                        if (currentActivityRef?.get() == act) {
                            currentActivityRef = null
                        }
                    } catch (_: Throwable) {
                        // Ignore
                    }
                }
            }
        }

        // 2. Options Menu Hook (for FeedDetailActivityV8)
        "com.coolapk.market.view.feed.FeedDetailActivityV8".toClassOrNull()?.hook {
            injectMember {
                method {
                    name = "onPrepareOptionsMenu"
                    param(android.view.Menu::class.java)
                }
                afterHook {
                    try {
                        val menu = args[0] as? android.view.Menu ?: return@afterHook
                        if (menu.findItem(998877) == null) {
                            menu.add(0, 998877, 3, "查找")
                            LogManager.log(instance as? Activity, "CoolapkDetailPlus", "[OptionsMenu] 顶部菜单插入【查找】选项")
                        }
                    } catch (t: Throwable) {
                        LogManager.log(appContext, "CoolapkDetailPlus", "[Error] onPrepareOptionsMenu hook exception: ${t.message}")
                    }
                }
            }

            injectMember {
                method {
                    name = "onOptionsItemSelected"
                    param(android.view.MenuItem::class.java)
                }
                afterHook {
                    try {
                        val item = args[0] as? android.view.MenuItem ?: return@afterHook
                        if (item.itemId == 998877 || item.title == "查找") {
                            val act = instance as? Activity ?: return@afterHook
                            LogManager.log(act, "CoolapkDetailPlus", "[OptionsMenu] 点击【查找】顶部菜单，显示搜索框")
                            SearchOverlay.show(act)
                            result = true
                        }
                    } catch (t: Throwable) {
                        LogManager.log(appContext, "CoolapkDetailPlus", "[Error] onOptionsItemSelected hook exception: ${t.message}")
                    }
                }
            }
        }

        // 3. Dialog.show() Hook
        DialogClass.hook {
            injectMember {
                method {
                    name = "show"
                }
                afterHook {
                    try {
                        val dialog = instance as? Dialog ?: return@afterHook
                        val activity = getActivityFromContext(dialog.context)
                            ?: dialog.ownerActivity
                            ?: currentActivityRef?.get()
                            ?: return@afterHook

                        if (!isFeedDetailActivity(activity)) return@afterHook
                        if (activity.isFinishing || activity.isDestroyed) return@afterHook

                        val decorView = dialog.window?.decorView as? ViewGroup ?: return@afterHook
                        decorView.postDelayed({
                            try {
                                if (activity.isFinishing || activity.isDestroyed) return@postDelayed
                                tryInjectSearchItem(decorView, activity) {
                                    try { dialog.dismiss() } catch (_: Throwable) {}
                                }
                            } catch (t: Throwable) {
                                LogManager.log(activity, "CoolapkDetailPlus", "[Error] Dialog tryInjectSearchItem exception: ${t.message}")
                            }
                        }, 120)
                    } catch (t: Throwable) {
                        LogManager.log(appContext, "CoolapkDetailPlus", "[Error] Dialog.show hook exception: ${t.message}")
                    }
                }
            }
        }

        // 4. PopupWindow Hook
        PopupWindow::class.java.hook {
            injectMember {
                method {
                    name = "showAtLocation"
                }
                afterHook {
                    try {
                        val popup = instance as? PopupWindow ?: return@afterHook
                        val contentView = popup.contentView as? ViewGroup ?: return@afterHook
                        val activity = getActivityFromContext(contentView.context)
                            ?: currentActivityRef?.get()
                            ?: return@afterHook

                        if (!isFeedDetailActivity(activity)) return@afterHook
                        if (activity.isFinishing || activity.isDestroyed) return@afterHook

                        contentView.postDelayed({
                            try {
                                if (activity.isFinishing || activity.isDestroyed) return@postDelayed
                                tryInjectSearchItem(contentView, activity) {
                                    try { popup.dismiss() } catch (_: Throwable) {}
                                }
                            } catch (t: Throwable) {
                                LogManager.log(activity, "CoolapkDetailPlus", "[Error] Popup tryInjectSearchItem exception: ${t.message}")
                            }
                        }, 120)
                    } catch (_: Throwable) {
                        // Ignore
                    }
                }
            }

            injectMember {
                method {
                    name = "showAsDropDown"
                }
                afterHook {
                    try {
                        val popup = instance as? PopupWindow ?: return@afterHook
                        val contentView = popup.contentView as? ViewGroup ?: return@afterHook
                        val activity = getActivityFromContext(contentView.context)
                            ?: currentActivityRef?.get()
                            ?: return@afterHook

                        if (!isFeedDetailActivity(activity)) return@afterHook
                        if (activity.isFinishing || activity.isDestroyed) return@afterHook

                        contentView.postDelayed({
                            try {
                                if (activity.isFinishing || activity.isDestroyed) return@postDelayed
                                tryInjectSearchItem(contentView, activity) {
                                    try { popup.dismiss() } catch (_: Throwable) {}
                                }
                            } catch (t: Throwable) {
                                LogManager.log(activity, "CoolapkDetailPlus", "[Error] Popup tryInjectSearchItem exception: ${t.message}")
                            }
                        }, 120)
                    } catch (_: Throwable) {
                        // Ignore
                    }
                }
            }
        }
    }

    private fun getActivityFromContext(context: Context?): Activity? {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    private fun isFeedDetailActivity(activity: Activity?): Boolean {
        if (activity == null) return false
        val name = activity.javaClass.name
        return name == "com.coolapk.market.view.feed.FeedDetailActivityV8" ||
               name.contains("FeedDetail") ||
               name.contains("FeedDetailActivity")
    }

    /**
     * 判断指定 ViewGroup 是否属于 RecyclerView 或其派生类
     */
    private fun isRecyclerView(group: ViewGroup): Boolean {
        var clazz: Class<*>? = group.javaClass
        while (clazz != null && clazz != Any::class.java) {
            val name = clazz.name
            if (name.contains("RecyclerView") || name == "androidx.recyclerview.widget.RecyclerView") {
                return true
            }
            clazz = clazz.superclass
        }
        return false
    }

    /**
     * 判断指定 ViewGroup 是否属于传统 AdapterView（如 ListView/GridView）
     */
    private fun isAdapterView(group: ViewGroup): Boolean {
        if (group is android.widget.AdapterView<*>) return true
        var clazz: Class<*>? = group.javaClass
        while (clazz != null && clazz != Any::class.java) {
            val name = clazz.name
            if (name.contains("AdapterView") ||
                name.contains("AbsListView") ||
                name.contains("GridView") ||
                name.contains("ListView")
            ) {
                return true
            }
            clazz = clazz.superclass
        }
        return false
    }

    /**
     * 判断指定 ViewGroup 是否支持直接进行 addView 动态插入操作
     */
    private fun isSupportedViewGroup(group: ViewGroup): Boolean {
        if (isRecyclerView(group) || isAdapterView(group)) {
            return false
        }
        return true
    }

    /**
     * 递归查找指定视图结构中的第一个 ImageView 实例，用于提取宿主既有按钮图标的尺寸、背景和内边距
     */
    private fun findFirstImageView(view: View): ImageView? {
        if (view is ImageView) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val found = findFirstImageView(view.getChildAt(i))
                if (found != null) return found
            }
        }
        return null
    }

    /**
     * 自定义纯代码绘制的矢量放大镜图标 Drawable
     * 自动随当前主题（浅色/深色模式）字体色自适应，避免 emoji 产生的平台色差与视觉突兀感
     */
    private class SearchIconDrawable(private val iconColor: Int) : Drawable() {
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = iconColor
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }

        /**
         * 绘制放大镜圆环镜片及 45 度圆角斜向手柄
         */
        override fun draw(canvas: Canvas) {
            val b = bounds
            val size = minOf(b.width(), b.height()).toFloat()
            if (size <= 0) return

            strokePaint.strokeWidth = size * 0.11f

            val cx = b.exactCenterX() - size * 0.08f
            val cy = b.exactCenterY() - size * 0.08f
            val r = size * 0.28f

            canvas.drawCircle(cx, cy, r, strokePaint)

            val angle = Math.toRadians(45.0)
            val startX = cx + (r * Math.cos(angle)).toFloat()
            val startY = cy + (r * Math.sin(angle)).toFloat()
            val endX = cx + (size * 0.44f * Math.cos(angle)).toFloat()
            val endY = cy + (size * 0.44f * Math.sin(angle)).toFloat()

            canvas.drawLine(startX, startY, endX, endY, strokePaint)
        }

        /**
         * 设置图标透明度
         */
        override fun setAlpha(alpha: Int) {
            strokePaint.alpha = alpha
        }

        /**
         * 设置图标色彩滤镜
         */
        override fun setColorFilter(colorFilter: ColorFilter?) {
            strokePaint.colorFilter = colorFilter
        }

        /**
         * 获取图标不透明度类型
         */
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    private data class ContainerTarget(
        val container: ViewGroup,
        val refChild: View,
        val isHorizontalRow: Boolean
    )

    /**
     * 获取指定叶子视图在目标容器中的直接一级子节点 View
     */
    private fun getDirectChildInContainer(targetContainer: ViewGroup, leafView: View): View {
        var curr: View = leafView
        while (curr.parent is View && curr.parent != targetContainer) {
            curr = curr.parent as View
        }
        return curr
    }

    /**
     * 寻找适宜作为通用插入点的父容器（降级策略时使用）
     */
    private fun findBestContainer(refTv: TextView, rootView: ViewGroup): ContainerTarget? {
        val path = ArrayList<ViewGroup>()
        var currParent = refTv.parent as? ViewGroup
        while (currParent != null) {
            path.add(currParent)
            if (currParent == rootView) break
            currParent = currParent.parent as? ViewGroup
        }

        if (path.isEmpty()) return null

        // Priority 1: 路径中第一个水平且支持 addView 的 LinearLayout
        for (group in path) {
            if (group is LinearLayout && group.orientation == LinearLayout.HORIZONTAL && isSupportedViewGroup(group)) {
                val refChild = getDirectChildInContainer(group, refTv)
                return ContainerTarget(group, refChild, isHorizontalRow = true)
            }
        }

        // Priority 2: 若路径中有 RecyclerView，选取 RecyclerView 上方的一层容器
        for (i in 0 until path.size) {
            val group = path[i]
            if (isRecyclerView(group)) {
                val parentAbove = if (i + 1 < path.size) path[i + 1] else null
                if (parentAbove != null && isSupportedViewGroup(parentAbove)) {
                    val refChild = getDirectChildInContainer(parentAbove, refTv)
                    val isHorizontal = parentAbove is LinearLayout && parentAbove.orientation == LinearLayout.HORIZONTAL
                    return ContainerTarget(parentAbove, refChild, isHorizontalRow = isHorizontal)
                }
            }
        }

        // Priority 3: 路径中垂直方向的 LinearLayout（高于 refTv.parent）
        for (group in path) {
            if (group is LinearLayout && group.orientation == LinearLayout.VERTICAL && isSupportedViewGroup(group)) {
                if (group != refTv.parent) {
                    val refChild = getDirectChildInContainer(group, refTv)
                    return ContainerTarget(group, refChild, isHorizontalRow = false)
                }
            }
        }

        // Priority 4: 其他支持的容器
        for (group in path) {
            if (isSupportedViewGroup(group)) {
                if (group != refTv.parent || group is LinearLayout) {
                    val refChild = getDirectChildInContainer(group, refTv)
                    val isHorizontal = group is LinearLayout && group.orientation == LinearLayout.HORIZONTAL
                    return ContainerTarget(group, refChild, isHorizontalRow = isHorizontal)
                }
            }
        }

        // Priority 5: 兜底到根视图
        if (isSupportedViewGroup(rootView)) {
            val refChild = getDirectChildInContainer(rootView, refTv)
            return ContainerTarget(rootView, refChild, isHorizontalRow = false)
        }

        return null
    }

    /**
     * 核心菜单注入逻辑：检测底栏菜单弹窗并将【查找】按钮按照顺序水平追加在既有功能按钮行末尾
     */
    private fun tryInjectSearchItem(rootView: ViewGroup?, activity: Activity, onDismiss: (() -> Unit)?) {
        if (rootView == null) return
        if (activity.isFinishing || activity.isDestroyed) return

        // 全局去重校验：避免同一弹窗重复注入
        if (rootView.findViewWithTag<View>("COOLAPK_SEARCH_MENU_ITEM") != null ||
            rootView.findViewWithTag<View>("COOLAPK_SEARCH_ROW_WRAPPER") != null ||
            hasSearchText(rootView)
        ) {
            return
        }

        val allTextViews = ArrayList<TextView>()
        findAllTextViews(rootView, allTextViews)

        var copyTv: TextView? = null
        var favorTv: TextView? = null
        var historyTv: TextView? = null
        var reportTv: TextView? = null

        for (tv in allTextViews) {
            val txt = tv.text?.toString()?.trim() ?: continue
            when {
                txt == "复制" || txt.contains("复制内容") || txt.contains("复制链接") -> copyTv = tv
                txt == "收藏" || txt.contains("收藏") -> favorTv = tv
                txt == "编辑历史" || txt.contains("编辑历史") -> historyTv = tv
                txt == "举报" || txt.contains("举报") -> reportTv = tv
            }
        }

        // 选定基准参照项：“举报” > “编辑历史” > “收藏” > “复制”
        val refTv = reportTv ?: historyTv ?: favorTv ?: copyTv ?: return

        // 向上回溯定位参照项所在的 item 根视图及其直接承载容器
        var curr: View = refTv
        var refItemView: View? = null
        var directContainer: ViewGroup? = null

        while (curr.parent is View && curr.parent != rootView) {
            val parent = curr.parent as ViewGroup
            if (isRecyclerView(parent) || (parent is LinearLayout && parent.orientation == LinearLayout.HORIZONTAL)) {
                refItemView = curr
                directContainer = parent
                break
            }
            curr = parent
        }

        val hasHistory = historyTv != null
        val orderDesc = if (hasHistory) "复制 - 收藏 - 编辑历史 - 举报 - 查找" else "复制 - 收藏 - 举报 - 查找"

        // 优先级 1：承载容器为横向 RecyclerView（当前酷安主流 BottomSheet 结构）
        if (directContainer != null && isRecyclerView(directContainer)) {
            val rv = directContainer
            val parentOfRv = rv.parent as? ViewGroup
            val effectiveRefItemView = refItemView ?: getDirectChildInContainer(rv, refTv)

            if (parentOfRv != null) {
                try {
                    val searchItem = buildSearchColumnItemView(refTv, effectiveRefItemView, activity, onDismiss)
                    searchItem.tag = "COOLAPK_SEARCH_MENU_ITEM"

                    val itemWidth = if (effectiveRefItemView.width > 0) effectiveRefItemView.width else dip2px(activity, 70f)
                    val searchLp = LinearLayout.LayoutParams(itemWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
                    searchItem.layoutParams = searchLp

                    val rvIndex = parentOfRv.indexOfChild(rv)
                    if (rvIndex != -1) {
                        val oldRvLp = rv.layoutParams
                        parentOfRv.removeView(rv)

                        // 创建水平包装容器，使 RecyclerView 与【查找】处于同一水平行
                        val rowWrapper = LinearLayout(activity).apply {
                            tag = "COOLAPK_SEARCH_ROW_WRAPPER"
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                if (oldRvLp.height > 0) oldRvLp.height else ViewGroup.LayoutParams.WRAP_CONTENT
                            ).apply {
                                if (oldRvLp is ViewGroup.MarginLayoutParams) {
                                    setMargins(oldRvLp.leftMargin, oldRvLp.topMargin, oldRvLp.rightMargin, oldRvLp.bottomMargin)
                                }
                            }
                        }

                        // 将 RecyclerView 宽度设为自适应内容宽度
                        rv.layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            if (oldRvLp.height > 0) oldRvLp.height else ViewGroup.LayoutParams.WRAP_CONTENT
                        )

                        rowWrapper.addView(rv)
                        rowWrapper.addView(searchItem)

                        parentOfRv.addView(rowWrapper, rvIndex)

                        LogManager.log(activity, "CoolapkDetailPlus", "[Menu] 成功将【查找】按钮水平追加至菜单行末尾 ($orderDesc)")
                        return
                    }
                } catch (t: Throwable) {
                    LogManager.log(activity, "CoolapkDetailPlus", "[Warning] RecyclerView 水平包装注入异常，尝试降级: ${t.message}")
                }
            }
        }

        // 优先级 2：承载容器本身即为水平 LinearLayout
        if (directContainer != null && directContainer is LinearLayout && directContainer.orientation == LinearLayout.HORIZONTAL) {
            try {
                val effectiveRefItemView = refItemView ?: getDirectChildInContainer(directContainer, refTv)
                val searchItem = buildSearchColumnItemView(refTv, effectiveRefItemView, activity, onDismiss)
                searchItem.tag = "COOLAPK_SEARCH_MENU_ITEM"

                val refIndex = directContainer.indexOfChild(effectiveRefItemView)
                val insertIndex = if (refIndex != -1) refIndex + 1 else directContainer.childCount
                directContainer.addView(searchItem, insertIndex)

                LogManager.log(activity, "CoolapkDetailPlus", "[Menu] 成功向水平 LinearLayout 插入【查找】按键 ($orderDesc)")
                return
            } catch (t: Throwable) {
                LogManager.log(activity, "CoolapkDetailPlus", "[Warning] 水平 LinearLayout 插入异常: ${t.message}")
            }
        }

        // 优先级 3：通用查找与降级插入
        val target = findBestContainer(refTv, rootView)
        if (target == null) {
            LogManager.log(activity, "CoolapkDetailPlus", "[Menu] 无法锁定适配的 View 容器，安全跳过")
            return
        }

        val container = target.container
        val refView = target.refChild

        if (container.findViewWithTag<View>("COOLAPK_SEARCH_MENU_ITEM") != null || hasSearchText(container)) {
            return
        }

        val searchItem = buildSearchItem(refTv, refView, container, target.isHorizontalRow, activity, onDismiss) ?: return
        searchItem.tag = "COOLAPK_SEARCH_MENU_ITEM"

        val refIndex = container.indexOfChild(refView)
        val insertIndex = if (refIndex != -1) refIndex + 1 else container.childCount

        try {
            container.addView(searchItem, insertIndex)
            LogManager.log(activity, "CoolapkDetailPlus", "[Menu] 动态适配完成，已通过容器【${container.javaClass.simpleName}】插入【查找】按键 ($orderDesc)")
        } catch (t: Throwable) {
            LogManager.log(activity, "CoolapkDetailPlus", "[Error] 菜单插入兜底失败: ${t.message}")
        }
    }

    /**
     * 根据容器排版方向构建适配的【查找】按键视图（水平列项或垂直全宽横条）
     */
    private fun buildSearchItem(
        refTv: TextView,
        refView: View,
        container: ViewGroup,
        isHorizontalRow: Boolean,
        activity: Activity,
        onDismiss: (() -> Unit)?
    ): View? {
        return try {
            val oldLp = refView.layoutParams
            val newLp = if (isHorizontalRow && container is LinearLayout) {
                val marginLp = oldLp as? ViewGroup.MarginLayoutParams
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (marginLp != null) {
                        leftMargin = marginLp.leftMargin
                        rightMargin = marginLp.rightMargin
                        topMargin = marginLp.topMargin
                        bottomMargin = marginLp.bottomMargin
                    }
                }
            } else if (container is LinearLayout) {
                val marginLp = oldLp as? ViewGroup.MarginLayoutParams
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    if (marginLp != null) {
                        leftMargin = marginLp.leftMargin
                        rightMargin = marginLp.rightMargin
                        topMargin = marginLp.topMargin
                        bottomMargin = marginLp.bottomMargin
                    } else {
                        setMargins(dip2px(activity, 16f), dip2px(activity, 4f), dip2px(activity, 16f), dip2px(activity, 4f))
                    }
                }
            } else {
                createMatchingLayoutParams(refView)
            }

            val itemLayout = if (isHorizontalRow) {
                buildSearchColumnItemView(refTv, refView, activity, onDismiss)
            } else {
                buildSearchRowItemView(refTv, activity)
            }

            itemLayout.layoutParams = newLp

            val clickListener = View.OnClickListener {
                try {
                    LogManager.log(activity, "CoolapkDetailPlus", "[Menu] 点击菜单【查找】按键，显示页内搜索框")
                    onDismiss?.invoke()
                    SearchOverlay.show(activity)
                } catch (t: Throwable) {
                    LogManager.log(activity, "CoolapkDetailPlus", "[Error] Search button click exception: ${t.message}")
                }
            }

            itemLayout.setOnClickListener(clickListener)
            itemLayout
        } catch (t: Throwable) {
            LogManager.log(activity, "CoolapkDetailPlus", "[Error] buildSearchItem exception: ${t.message}")
            null
        }
    }

    /**
     * 根据参考子项的 LayoutParams 类型动态反射生成结构匹配的 LayoutParams
     */
    private fun createMatchingLayoutParams(refView: View): ViewGroup.LayoutParams {
        val oldLp = refView.layoutParams
        if (oldLp != null) {
            try {
                val clazz = oldLp.javaClass
                val constructor = clazz.getConstructor(ViewGroup.LayoutParams::class.java)
                return constructor.newInstance(oldLp) as ViewGroup.LayoutParams
            } catch (_: Throwable) {
                try {
                    val clazz = oldLp.javaClass
                    val constructor = clazz.getConstructor(ViewGroup.MarginLayoutParams::class.java)
                    if (oldLp is ViewGroup.MarginLayoutParams) {
                        return constructor.newInstance(oldLp) as ViewGroup.LayoutParams
                    }
                } catch (_: Throwable) {
                    // Ignore
                }
            }
            if (oldLp is ViewGroup.MarginLayoutParams) {
                return ViewGroup.MarginLayoutParams(oldLp)
            }
        }
        return ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    /**
     * 构建与酷安既有按钮（复制、收藏、举报）风格完全统一的水平列项（圆形图标在上，文字在下，带水波纹与主题自适应）
     */
    private fun buildSearchColumnItemView(
        refTv: TextView,
        refItemView: View,
        activity: Activity,
        onDismiss: (() -> Unit)?
    ): LinearLayout {
        val columnLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            isClickable = true
            isFocusable = true
        }

        // 添加系统级水波纹按压反馈
        val typedValue = TypedValue()
        if (activity.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, typedValue, true)) {
            columnLayout.setBackgroundResource(typedValue.resourceId)
        } else if (activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, typedValue, true)) {
            columnLayout.setBackgroundResource(typedValue.resourceId)
        }

        columnLayout.setPadding(
            refItemView.paddingLeft,
            refItemView.paddingTop,
            refItemView.paddingRight,
            refItemView.paddingBottom
        )

        val refIconView = findFirstImageView(refItemView)
        val circleSize = if (refIconView != null && refIconView.width > 0) {
            refIconView.width
        } else {
            dip2px(activity, 48f)
        }

        // 尝试克隆宿主原生的灰色圆圈背景 Drawable，以完美支持深色模式与原生质感
        val clonedBg = runCatching {
            refIconView?.background?.constantState?.newDrawable(activity.resources)?.mutate()
        }.getOrNull()

        val circleBg = clonedBg ?: GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#F5F5F5"))
        }

        val iconContainer = FrameLayout(activity).apply {
            background = circleBg
        }

        val refIconLp = refIconView?.layoutParams as? ViewGroup.MarginLayoutParams
        val iconLp = LinearLayout.LayoutParams(circleSize, circleSize).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            if (refIconLp != null && refIconLp.topMargin > 0) {
                topMargin = refIconLp.topMargin
            } else {
                topMargin = dip2px(activity, 12f)
            }
        }
        iconContainer.layoutParams = iconLp

        // 放置矢量放大镜图标（跟随当前主题字体颜色自适应）
        val iconColor = refTv.currentTextColor
        val searchDrawable = SearchIconDrawable(iconColor)
        val iconImg = ImageView(activity).apply {
            setImageDrawable(searchDrawable)
            val pad = (circleSize * 0.22f).toInt()
            setPadding(pad, pad, pad, pad)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        iconContainer.addView(iconImg)

        // 文本标签（100% 继承参考 TextView 的字号、字体与颜色）
        val labelTv = TextView(activity).apply {
            text = "查找"
            setTextSize(TypedValue.COMPLEX_UNIT_PX, refTv.textSize)
            setTextColor(refTv.currentTextColor)
            gravity = Gravity.CENTER
            typeface = refTv.typeface
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                val refTvLp = refTv.layoutParams as? ViewGroup.MarginLayoutParams
                if (refTvLp != null && refTvLp.topMargin > 0) {
                    topMargin = refTvLp.topMargin
                } else {
                    topMargin = dip2px(activity, 6f)
                }
                gravity = Gravity.CENTER_HORIZONTAL
            }
        }

        columnLayout.addView(iconContainer)
        columnLayout.addView(labelTv)

        columnLayout.setOnClickListener {
            try {
                LogManager.log(activity, "CoolapkDetailPlus", "[Menu] 点击菜单【查找】按键，关闭弹窗并弹出页内检索浮层")
                onDismiss?.invoke()
                SearchOverlay.show(activity)
            } catch (t: Throwable) {
                LogManager.log(activity, "CoolapkDetailPlus", "[Error] Search button click exception: ${t.message}")
            }
        }

        return columnLayout
    }

    /**
     * 构建全宽横条样式的【查找】按键（仅在无法实现水平排列时的兜底展示）
     */
    private fun buildSearchRowItemView(
        refTv: TextView,
        activity: Activity
    ): LinearLayout {
        val rowLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dip2px(activity, 20f), dip2px(activity, 14f), dip2px(activity, 20f), dip2px(activity, 14f))

            val bgDrawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.parseColor("#FFFFFF"))
            }
            background = bgDrawable
        }

        val iconImg = ImageView(activity).apply {
            val size = dip2px(activity, 24f)
            setImageDrawable(SearchIconDrawable(refTv.currentTextColor))
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                rightMargin = dip2px(activity, 14f)
            }
        }

        val labelTv = TextView(activity).apply {
            text = "查找"
            setTextSize(TypedValue.COMPLEX_UNIT_PX, refTv.textSize)
            setTextColor(refTv.currentTextColor)
            typeface = refTv.typeface
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        rowLayout.addView(iconImg)
        rowLayout.addView(labelTv)

        return rowLayout
    }

    /**
     * 判断容器视图树中是否已存在名为“查找”的 TextView
     */
    private fun hasSearchText(group: ViewGroup): Boolean {
        val list = ArrayList<TextView>()
        findAllTextViews(group, list)
        return list.any { it.text?.toString()?.trim() == "查找" }
    }

    /**
     * 递归遍历视图树，收集所有匹配的 TextView 实例
     */
    private fun findAllTextViews(view: View, list: MutableList<TextView>) {
        try {
            if (view is TextView) {
                list.add(view)
            } else if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    val child = view.getChildAt(i) ?: continue
                    findAllTextViews(child, list)
                }
            }
        } catch (_: Throwable) {
            // 忽略遍历异常
        }
    }

    /**
     * 将 dp 数值转换为对应的系统像素值 px
     */
    private fun dip2px(activity: Activity, dpValue: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dpValue,
            activity.resources.displayMetrics
        ).toInt()
    }
}
