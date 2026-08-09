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

    /** 切换当前文档置顶状态 */
    data object TogglePin : MdReaderIntent

    /** 切换某个内容块的段落标记（长按段落触发） */
    data class ToggleHighlight(val blockIndex: Int, val preview: String) : MdReaderIntent

    /** 在当前位置添加书签 */
    data class AddBookmark(val blockIndex: Int, val preview: String) : MdReaderIntent

    /** 删除指定位置的书签 */
    data class RemoveBookmark(val blockIndex: Int) : MdReaderIntent
}
