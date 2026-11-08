package com.example.mviapp.feature.epub

import com.example.mviapp.core.mvi.MviIntent

/** EPUB 阅读器 Intent */
sealed interface EpubReaderIntent : MviIntent {
    /** 加载书籍 */
    data object LoadBook : EpubReaderIntent

    /** 跳转到指定章节 */
    data class JumpToChapter(val index: Int) : EpubReaderIntent

    /** 上一章 */
    data object PrevChapter : EpubReaderIntent

    /** 下一章 */
    data object NextChapter : EpubReaderIntent

    /** 切换目录显示 */
    data object ToggleToc : EpubReaderIntent

    /** 关闭目录 */
    data object CloseToc : EpubReaderIntent
}
