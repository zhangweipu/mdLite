package com.example.mviapp.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppBarDirectionTrackerTest {

    // 可控时钟：JUnit 每个用例都会新建实例，now 从 0 开始
    private var now = 0L

    private fun tracker(
        direction: Float = 1f,
        cooldownMs: Long = 280L,
    ) = AppBarDirectionTracker(
        direction = direction,
        cooldownMs = cooldownMs,
        nowMs = { now },
    )

    private fun skipCooldown() {
        now += 281
    }

    @Test
    fun `swipe up past threshold hides the top bar`() {
        val tracker = tracker()
        assertNull(tracker.onComposeScroll(consumedY = -10f, availableY = 0f))
        assertEquals(true, tracker.onComposeScroll(consumedY = -20f, availableY = 0f))
    }

    @Test
    fun `swipe down past threshold shows the top bar`() {
        val tracker = tracker()
        assertEquals(true, tracker.onComposeScroll(consumedY = -40f, availableY = 0f))
        skipCooldown()
        assertEquals(false, tracker.onComposeScroll(consumedY = 40f, availableY = 0f))
    }

    @Test
    fun `small jitter does not toggle the top bar`() {
        val tracker = tracker()
        repeat(5) {
            assertNull(tracker.onComposeScroll(consumedY = -3f, availableY = 0f))
            assertNull(tracker.onComposeScroll(consumedY = 3f, availableY = 0f))
        }
    }

    @Test
    fun `reversing direction restarts the accumulation`() {
        val tracker = tracker()
        assertEquals(true, tracker.onComposeScroll(consumedY = -40f, availableY = 0f))
        skipCooldown()
        // 反向 20px 不足以显示，继续同方向累计过阈值才触发
        assertNull(tracker.onComposeScroll(consumedY = 20f, availableY = 0f))
        assertEquals(false, tracker.onComposeScroll(consumedY = 10f, availableY = 0f))
    }

    @Test
    fun `pulling down at the very top always shows the top bar`() {
        val tracker = tracker()
        assertEquals(true, tracker.onComposeScroll(consumedY = -80f, availableY = 0f))
        skipCooldown()
        assertEquals(false, tracker.onComposeScroll(consumedY = 0f, availableY = 30f))
    }

    @Test
    fun `webview scroll follows the same direction rules`() {
        val tracker = tracker()
        assertNull(tracker.onWebViewScroll(10))
        assertEquals(true, tracker.onWebViewScroll(50))
        skipCooldown()
        assertEquals(false, tracker.onWebViewScroll(10))
    }

    @Test
    fun `webview returning to the top shows the top bar`() {
        val tracker = tracker()
        assertEquals(true, tracker.onWebViewScroll(200))
        skipCooldown()
        assertEquals(false, tracker.onWebViewScroll(0))
    }

    @Test
    fun `programmatic webview jump is ignored`() {
        val tracker = tracker()
        // 恢复阅读位置这类一次性大跳转不算手势
        assertNull(tracker.onWebViewScroll(5_000))
        assertEquals(true, tracker.onWebViewScroll(5_040))
    }

    @Test
    fun `state flip starts a cooldown that swallows layout feedback`() {
        val tracker = tracker()
        assertEquals(true, tracker.onComposeScroll(consumedY = -40f, availableY = 0f))
        // 顶部栏收起 → 内容高度变化 → WebView 重排回送的滚动事件必须被吞掉，
        // 否则会误判为下滑并立刻展开，形成闪烁循环
        assertNull(tracker.onComposeScroll(consumedY = 80f, availableY = 0f))
        assertNull(tracker.onWebViewScroll(0))
        assertNull(tracker.onWebViewScroll(120))
        skipCooldown()
        assertEquals(false, tracker.onComposeScroll(consumedY = 40f, availableY = 0f))
    }

    @Test
    fun `repeated same-direction scrolling does not toggle again`() {
        val tracker = tracker()
        assertEquals(true, tracker.onComposeScroll(consumedY = -40f, availableY = 0f))
        skipCooldown()
        assertNull(tracker.onComposeScroll(consumedY = -100f, availableY = 0f))
        skipCooldown()
        assertNull(tracker.onComposeScroll(consumedY = -100f, availableY = 0f))
    }

    @Test
    fun `direction sign -1 inverts compose scroll decisions`() {
        val tracker = tracker(direction = -1f)
        // 反转后：下滑隐藏、上滑显示
        assertEquals(true, tracker.onComposeScroll(consumedY = 40f, availableY = 0f))
        skipCooldown()
        assertEquals(false, tracker.onComposeScroll(consumedY = -40f, availableY = 0f))
    }

    @Test
    fun `direction sign -1 inverts webview scroll decisions`() {
        val tracker = tracker(direction = -1f)
        // 上滑对应「显示」，初始已是显示态，不产生切换
        assertNull(tracker.onWebViewScroll(200))
        skipCooldown()
        assertEquals(true, tracker.onWebViewScroll(160))
        skipCooldown()
        // 到顶按「下滑」处理，仍是隐藏，不会突然展开造成闪现
        assertNull(tracker.onWebViewScroll(0))
    }

    @Test
    fun `direction sign -1 keeps overscroll aligned with swipe down`() {
        val tracker = tracker(direction = -1f)
        assertEquals(true, tracker.onComposeScroll(consumedY = 40f, availableY = 0f))
        skipCooldown()
        // 到顶继续下拉与下滑同为「隐藏」，不产生状态跳变
        assertNull(tracker.onComposeScroll(consumedY = 0f, availableY = 30f))
    }

    @Test
    fun `direction sign -1 keeps the accumulation threshold`() {
        val tracker = tracker(direction = -1f)
        assertEquals(true, tracker.onComposeScroll(consumedY = 40f, availableY = 0f))
        skipCooldown()
        assertNull(tracker.onComposeScroll(consumedY = -10f, availableY = 0f))
        assertEquals(false, tracker.onComposeScroll(consumedY = -20f, availableY = 0f))
    }
}
