package com.example.mviapp.feature.reader

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
    ) : MdReaderState

    /** 加载失败 */
    data class Error(val message: String) : MdReaderState
}
