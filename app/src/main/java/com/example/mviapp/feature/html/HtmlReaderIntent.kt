package com.example.mviapp.feature.html

import com.example.mviapp.core.mvi.MviIntent

/** HTML 阅读页 Intent */
sealed interface HtmlReaderIntent : MviIntent {

    /** 加载 HTML 原文 */
    data object Load : HtmlReaderIntent

    /** 加载失败后重试 */
    data object Retry : HtmlReaderIntent

    /** 切换置顶状态 */
    data object TogglePin : HtmlReaderIntent

    /** 保存阅读位置（WebView 滚动 Y） */
    data class SavePosition(val scrollY: Int) : HtmlReaderIntent
}
