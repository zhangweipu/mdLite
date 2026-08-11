package com.example.mviapp.feature.browser

import com.example.mviapp.core.mvi.MviEffect

/** 网页浏览器一次性事件 */
sealed interface BrowserEffect : MviEffect {
    data class ShowToast(val message: String) : BrowserEffect

    /** 打开下载管理页 */
    data object NavigateToDownloadList : BrowserEffect

    /** 打开收藏夹页 */
    data object NavigateToBookmarks : BrowserEffect

    /** 触发系统保存文件选择器（准备下载） */
    data class RequestSaveLocation(val url: String, val fileName: String) : BrowserEffect

    /** 识别到二维码内容（网址） */
    data class QrResult(val content: String) : BrowserEffect

    /** 收藏状态变化 */
    data class BookmarkChanged(val added: Boolean) : BrowserEffect
}
