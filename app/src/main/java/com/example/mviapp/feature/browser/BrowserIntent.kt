package com.example.mviapp.feature.browser

import com.example.mviapp.core.mvi.MviIntent

/** 网页浏览器 Intent */
sealed interface BrowserIntent : MviIntent {

    /** 加载指定 URL */
    data class LoadUrl(val url: String) : BrowserIntent

    /** 从地址栏提交访问 */
    data class SubmitUrl(val input: String) : BrowserIntent

    /** 页面开始加载 */
    data class PageStarted(val url: String) : BrowserIntent

    /** 页面加载完成 */
    data class PageFinished(val url: String) : BrowserIntent

    /** 页面加载失败 */
    data class PageError(val message: String) : BrowserIntent

    /** 后退 */
    data object GoBack : BrowserIntent

    /** 前进 */
    data object GoForward : BrowserIntent

    /** 刷新 */
    data object Refresh : BrowserIntent

    /** 粘贴剪贴板内容到地址栏并访问 */
    data object PasteAndGo : BrowserIntent

    /** 从相册选择二维码图片识别并打开 */
    data class ScanQrFromImage(val imageUri: android.net.Uri) : BrowserIntent

    /** 识别到下载链接，请求选择保存位置 */
    data class StartDownload(val url: String, val fileName: String) : BrowserIntent

    /** 用户已选择保存位置，开始下载 */
    data class ConfirmDownload(val url: String, val fileName: String, val destinationUri: android.net.Uri) : BrowserIntent

    /** 切换当前页面的收藏状态 */
    data object ToggleBookmark : BrowserIntent

    /** 检查当前 URL 是否已收藏 */
    data class CheckBookmark(val url: String) : BrowserIntent

    /** 打开下载管理页 */
    data object OpenDownloadList : BrowserIntent

    /** 打开收藏夹页 */
    data object OpenBookmarks : BrowserIntent

    /** 从收藏夹打开网址 */
    data class OpenBookmark(val url: String) : BrowserIntent
}
