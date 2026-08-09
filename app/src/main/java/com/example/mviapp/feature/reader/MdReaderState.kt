package com.example.mviapp.feature.reader

import com.example.mviapp.core.marks.Bookmark
import com.example.mviapp.core.marks.ParagraphHighlight
import com.example.mviapp.core.mvi.MviState

/**
 * Markdown 阅读页 UI 状态
 */
sealed interface MdReaderState : MviState {

    /** 加载中 */
    data object Loading : MdReaderState

    /**
     * 加载成功，[content] 为 Markdown 原文；
     * [scrollIndex] / [scrollOffset] 为上次阅读位置，用于自动恢复
     */
    data class Success(
        val content: String,
        val scrollIndex: Int = 0,
        val scrollOffset: Int = 0,
        /** 当前文档是否已置顶（顶栏图钉按钮状态） */
        val pinned: Boolean = false,
        /** 段落标记（高亮）列表 */
        val highlights: List<ParagraphHighlight> = emptyList(),
        /** 书签列表 */
        val bookmarks: List<Bookmark> = emptyList(),
    ) : MdReaderState

    /** 加载失败 */
    data class Error(val message: String) : MdReaderState
}
