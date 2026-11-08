package com.example.mviapp.core.ui

import android.content.Context
import android.util.AttributeSet
import android.webkit.WebView

/**
 * 把原生 WebView 的垂直滚动位置变化暴露给 Compose。
 *
 * WebView 是 Android View，不参与 Compose 的嵌套滚动体系，因此顶部栏无法感知
 * 网页滚动。这里重写 [onScrollChanged] 上报 scrollY，交给
 * [AppBarScrollController.onWebViewScroll] 统一处理隐藏/显示。
 */
open class ObservableWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : WebView(context, attrs) {

    var onScrollYChanged: ((Int) -> Unit)? = null

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        if (t != oldt) onScrollYChanged?.invoke(t)
    }
}
