package com.example.mviapp.feature.epub

import com.example.mviapp.core.epub.EpubBook
import com.example.mviapp.core.epub.EpubChapterPage
import com.example.mviapp.core.mvi.MviState

/** EPUB 阅读器 UI 状态 */
data class EpubReaderState(
    /** 书籍信息（加载完成后才有值） */
    val book: EpubBook? = null,
    /** 当前章节索引 */
    val currentChapterIndex: Int = 0,
    /** 当前章节的渲染数据（HTML + 图片资源 baseUrl） */
    val page: EpubChapterPage? = null,
    /** 是否正在加载（书籍解析中） */
    val isLoading: Boolean = true,
    /** 是否正在加载章节内容 */
    val isChapterLoading: Boolean = false,
    /** 书籍级错误（无法打开） */
    val errorMessage: String? = null,
    /** 章节级错误（某一章读取失败） */
    val chapterError: String? = null,
    /** 是否显示目录弹窗 */
    val showToc: Boolean = false,
    /** 本次章节加载完成后需要恢复到的章内滚动 Y（0 = 章首） */
    val restoreScrollY: Int = 0,
    /** 每次章节加载递增，保证连续两次恢复同一 Y 也能被 UI 感知 */
    val restoreRequestId: Int = 0,
) : MviState
