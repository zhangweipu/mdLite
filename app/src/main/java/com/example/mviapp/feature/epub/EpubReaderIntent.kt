package com.example.mviapp.feature.epub

import com.example.mviapp.core.mvi.MviIntent

/** EPUB 阅读器 Intent */
sealed interface EpubReaderIntent : MviIntent {
    /** 加载书籍 */
    data object LoadBook : EpubReaderIntent

    /** 重新加载整本书（错误页重试） */
    data object Retry : EpubReaderIntent

    /** 重新加载当前章节 */
    data object RetryChapter : EpubReaderIntent

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

    /** 保存阅读位置；chapterIndex 用于丢弃翻章后才到达的延迟滚动事件 */
    data class SavePosition(val chapterIndex: Int, val scrollY: Int) : EpubReaderIntent

    /** UI 已完成滚动恢复，清掉待恢复位置 */
    data object RestoreHandled : EpubReaderIntent
}
