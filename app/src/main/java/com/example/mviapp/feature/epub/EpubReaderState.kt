package com.example.mviapp.feature.epub

import com.example.mviapp.core.epub.EpubBook
import com.example.mviapp.core.mvi.MviState

/** EPUB 阅读器 UI 状态 */
data class EpubReaderState(
    /** 书籍信息（加载完成后才有值） */
    val book: EpubBook? = null,
    /** 当前章节索引 */
    val currentChapterIndex: Int = 0,
    /** 当前章节 HTML 内容 */
    val currentContent: String? = null,
    /** 是否正在加载（书籍解析中） */
    val isLoading: Boolean = true,
    /** 是否正在加载章节内容 */
    val isChapterLoading: Boolean = false,
    /** 错误信息 */
    val errorMessage: String? = null,
    /** 是否显示目录抽屉 */
    val showToc: Boolean = false,
) : MviState
