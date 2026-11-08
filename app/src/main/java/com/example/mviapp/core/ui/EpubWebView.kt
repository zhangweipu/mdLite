package com.example.mviapp.core.ui

import android.content.Context
import android.util.AttributeSet

/**
 * EPUB 正文专用 WebView：强制锁死横向滚动。
 *
 * CSS 已经处理了绝大多数溢出，但书里仍可能出现绝对定位元素、宽 SVG、
 * `white-space: nowrap` 的长公式或表格，让 WebView 认为内容比屏幕宽。
 * 这里在 View 层兜底：所有滚动都钳到 x = 0，用户只能上下翻页。
 *
 * 仍然继承 [ObservableWebView]，顶部栏的上滑隐藏/下滑显示不受影响。
 */
class EpubWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : ObservableWebView(context, attrs) {

    private var locking = false

    override fun scrollTo(x: Int, y: Int) {
        super.scrollTo(0, y)
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        // Chromium 有时绕过 View.scrollTo 直接改内部滚动位置，
        // 因此在回调里再钳一次；locking 防止 scrollTo 触发的回调无限递归。
        if (l != 0 && !locking) {
            locking = true
            try {
                scrollTo(0, t)
            } finally {
                locking = false
            }
        }
    }
}
