package com.example.mviapp.core.ui

import kotlin.math.abs

/**
 * 顶部栏滚动方向的纯逻辑判定器，不依赖 Android 或 Compose，便于 JVM 单测。
 *
 * 默认 `direction = 1f`：上滑隐藏 / 下滑显示；`direction = -1f` 时整体反过来。
 * 到顶（overscroll / scrollY 归零）按「下滑手势」处理，目标值随方向一起翻转，
 * 否则会与手势判定互相打架，在页面顶部附近造成标题突然跳一下。
 *
 * 判定结果：
 * - `true`  → 应收起顶部栏
 * - `false` → 应展开顶部栏
 * - `null`  → 保持当前状态
 *
 * 三层防抖，专门解决滑动过程中标题「闪现 / 来回跳」：
 * 1. 同方向累计 + 阈值，小幅抖动不触发；
 * 2. 内部记住当前状态，只有状态真正翻转时才返回非 null。连续同方向滚动、
 *    fling 回弹都不会反复拉出推进；
 * 3. 状态翻转后进入冷却期（[cooldownMs]，略长于 180ms 收起/展开动画），期间吞掉
 *    滚动事件。顶部栏收起会改变 Scaffold 内容高度，WebView 随之重排并回送 scrollY；
 *    若这些事件立刻参与判定，就会形成「隐藏 → 重排 → 误判为下滑 → 显示 → 重排 → …」
 *    的循环，表现正是标题不停闪现。
 *
 * [direction] 是「平台滚动量 → 手势方向」的换算系数，只影响两个入口的符号：
 * `1f` 按标准口径（Compose consumedY 与手势相反、WebView scrollY 增大即上滑）；
 * `-1f` 表示设备上上报方向与标准口径相反，需要整体反转。
 * 方向、阈值、冷却都集中在入口，各页面无需改动。
 */
class AppBarDirectionTracker(
    private val threshold: Float = DEFAULT_THRESHOLD,
    private val maxFrameDelta: Float = DEFAULT_MAX_FRAME_DELTA,
    private val direction: Float = 1f,
    private val cooldownMs: Long = DEFAULT_COOLDOWN_MS,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    private var accumulated = 0f
    private var lastWebViewScrollY = 0
    private var hidden = false
    private var suppressUntil = 0L

    /** 到顶继续下拉属于「下滑手势」，其结果必须与正常下滑一致 */
    private val topValue: Boolean = direction < 0f

    /**
     * Compose 滚动容器的回调参数。
     * [consumedY] 为负表示内容向上滚（手指上滑），[availableY] 大于 0 且未消费表示已到顶部还在下拉。
     */
    fun onComposeScroll(consumedY: Float, availableY: Float): Boolean? {
        if (isCoolingDown()) return null
        // 到顶继续下拉：按「下滑手势」的目标值处理，方向反转时一起翻转
        if (consumedY == 0f && availableY > 0f) {
            accumulated = 0f
            return changeTo(topValue)
        }
        return onDelta(-consumedY * direction)
    }

    /** 原生 WebView 的 scrollY（像素）。 */
    fun onWebViewScroll(scrollY: Int): Boolean? {
        // 冷却期也要同步位置，否则结束后第一帧会出现巨大的 delta
        val delta = (scrollY - lastWebViewScrollY).toFloat() * direction
        lastWebViewScrollY = scrollY
        if (isCoolingDown()) return null
        if (scrollY <= 0) {
            accumulated = 0f
            return changeTo(topValue)
        }
        // 恢复阅读位置 / 页面重载造成的程序化跳转不参与手势判断
        if (abs(delta) > maxFrameDelta) return null
        return onDelta(delta)
    }

    /** 冷却期内吞掉事件并清空累计，避免冷却结束后残留量立刻触发一次反向切换。 */
    private fun isCoolingDown(): Boolean {
        if (nowMs() >= suppressUntil) return false
        accumulated = 0f
        return true
    }

    private fun onDelta(delta: Float): Boolean? {
        if (delta == 0f) return null
        // 方向变化时重新累计
        accumulated = if (accumulated == 0f || (accumulated > 0f) == (delta > 0f)) {
            accumulated + delta
        } else {
            delta
        }
        return when {
            accumulated > threshold -> changeTo(true)
            accumulated < -threshold -> changeTo(false)
            else -> null
        }
    }

    /** 只有状态真正翻转才返回非 null，并启动冷却。 */
    private fun changeTo(value: Boolean): Boolean? {
        accumulated = 0f
        if (hidden == value) return null
        hidden = value
        suppressUntil = nowMs() + cooldownMs
        return value
    }

    private companion object {
        const val DEFAULT_THRESHOLD = 24f
        const val DEFAULT_MAX_FRAME_DELTA = 600f
        // 覆盖 180ms 动画 + 动画结束后 WebView 重新布局的稳定期
        const val DEFAULT_COOLDOWN_MS = 280L
    }
}
