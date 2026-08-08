package com.example.mviapp.feature.reader

import com.example.mviapp.core.mvi.MviIntent

/**
 * Markdown 阅读页 Intent
 */
sealed interface MdReaderIntent : MviIntent {

    /** 进入页面时加载文档 */
    data object LoadContent : MdReaderIntent

    /** 加载失败后重试 */
    data object Retry : MdReaderIntent

    /** 保存阅读位置（页面离开时派发） */
    data class SavePosition(val index: Int, val offset: Int) : MdReaderIntent
}
