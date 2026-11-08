package com.example.mviapp.feature.browser

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.example.mviapp.core.bookmark.BookmarkRepository
import com.example.mviapp.core.download.DownloadRepository
import androidx.lifecycle.viewModelScope
import com.example.mviapp.core.mvi.MviViewModel
import com.example.mviapp.core.qrcode.QrCodeScanner
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 网页浏览器 ViewModel
 *
 * 管理 WebView 的加载状态、地址栏输入、二维码识别、下载、收藏、历史记录等。
 * 轻量设计：不持有 WebView 实例，只管理业务状态。
 */
@HiltViewModel
class BrowserViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadRepository: DownloadRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val qrCodeScanner: QrCodeScanner,
    private val savedStateHandle: SavedStateHandle,
) : MviViewModel<BrowserIntent, BrowserState, BrowserEffect>(
    initialState = BrowserState()
) {

    init {
        // 启动时加载上次访问的网址
        viewModelScope.launch {
            val lastVisited = bookmarkRepository.getLastVisited()
            if (!lastVisited.isNullOrBlank()) {
                updateState { it.copy(lastVisitedUrl = lastVisited) }
            }
        }
    }

    override suspend fun handleIntent(intent: BrowserIntent) {
        when (intent) {
            is BrowserIntent.LoadUrl -> loadUrl(intent.url)
            is BrowserIntent.SubmitUrl -> submitUrl(intent.input)
            is BrowserIntent.PageStarted -> onPageStarted(
                intent.url, currentState.canGoBack, currentState.canGoForward
            )
            is BrowserIntent.PageFinished -> onPageFinished(
                intent.url, currentState.canGoBack, currentState.canGoForward, null
            )
            is BrowserIntent.PageError -> onPageError(intent.message)
            is BrowserIntent.GoBack -> Unit
            is BrowserIntent.GoForward -> Unit
            is BrowserIntent.Refresh -> Unit
            is BrowserIntent.PasteAndGo -> pasteAndGo()
            is BrowserIntent.ScanQrFromImage -> scanQrFromImage(intent.imageUri)
            is BrowserIntent.StartDownload -> requestDownload(intent.url, intent.fileName)
            is BrowserIntent.ConfirmDownload -> confirmDownload(intent.url, intent.fileName, intent.destinationUri)
            is BrowserIntent.ToggleBookmark -> toggleBookmark()
            is BrowserIntent.CheckBookmark -> checkBookmark(intent.url)
            is BrowserIntent.OpenDownloadList -> emitEffect(BrowserEffect.NavigateToDownloadList)
            is BrowserIntent.OpenBookmarks -> emitEffect(BrowserEffect.NavigateToBookmarks)
            is BrowserIntent.OpenBookmark -> openBookmark(intent.url)
        }
    }

    private fun loadUrl(url: String) {
        val normalized = normalizeUrl(url)
        updateState { it.copy(addressInput = normalized, errorMessage = null) }
    }

    private fun submitUrl(input: String) {
        val trimmed = input.trim()
        if (trimmed.isBlank()) {
            emitEffect(BrowserEffect.ShowToast("请输入网址"))
            return
        }
        val normalized = normalizeUrl(trimmed)
        updateState { it.copy(addressInput = normalized, errorMessage = null) }
    }

    private fun onPageError(message: String) {
        updateState { it.copy(isLoading = false, errorMessage = message) }
        emitEffect(BrowserEffect.ShowToast(message))
    }

    private fun pasteAndGo() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip == null || clip.itemCount == 0) {
            emitEffect(BrowserEffect.ShowToast("剪贴板为空"))
            return
        }
        val text = clip.getItemAt(0).text?.toString()?.trim()
        if (text.isNullOrBlank()) {
            emitEffect(BrowserEffect.ShowToast("剪贴板没有文本内容"))
            return
        }
        val normalized = normalizeUrl(text)
        updateState { it.copy(addressInput = normalized, errorMessage = null) }
        emitEffect(BrowserEffect.QrResult(normalized))
    }

    private fun scanQrFromImage(imageUri: Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { qrCodeScanner.scanFromUri(imageUri) }.getOrNull()
            }
            if (result.isNullOrBlank()) {
                emitEffect(BrowserEffect.ShowToast("未识别到二维码"))
            } else {
                val normalized = normalizeUrl(result)
                updateState { it.copy(addressInput = normalized, errorMessage = null) }
                emitEffect(BrowserEffect.QrResult(normalized))
            }
        }
    }

    private fun requestDownload(url: String, fileName: String) {
        emitEffect(BrowserEffect.RequestSaveLocation(url, fileName))
    }

    /**
     * 保存待下载信息到 SavedStateHandle。
     *
     * 系统「保存到」选择器是一个独立 Activity，期间本 Activity 可能被系统回收重建。
     * 以前把待下载信息放在 Composable 的 remember 里，重建后信息丢失，
     * 用户选完位置不会开始下载，只在磁盘上留下 SAF 预创建的 0 字节文件。
     */
    fun rememberPendingDownload(url: String, fileName: String) {
        savedStateHandle[KEY_PENDING_URL] = url
        savedStateHandle[KEY_PENDING_NAME] = fileName
    }

    /** 取出并清除待下载信息（Activity 重建后仍可用） */
    fun consumePendingDownload(): Pair<String, String>? {
        val url = savedStateHandle.get<String>(KEY_PENDING_URL)
        val name = savedStateHandle.get<String>(KEY_PENDING_NAME)
        savedStateHandle.remove<String>(KEY_PENDING_URL)
        savedStateHandle.remove<String>(KEY_PENDING_NAME)
        return if (!url.isNullOrBlank() && !name.isNullOrBlank()) url to name else null
    }

    private fun confirmDownload(url: String, fileName: String, destinationUri: Uri) {
        val referer = currentState.currentUrl.takeIf { it.isNotBlank() }
        viewModelScope.launch {
            // startDownload 立即返回，真正的下载在应用级作用域执行，
            // 离开本页不会中断任务、也不会留下 0 字节文件。
            downloadRepository.startDownload(url, fileName, destinationUri, referer)
            emitEffect(BrowserEffect.ShowToast("已加入下载队列：$fileName"))
        }
    }

    private fun toggleBookmark() {
        val url = currentState.currentUrl
        val title = currentState.title.ifBlank { url }
        if (url.isBlank()) return
        viewModelScope.launch {
            val added = bookmarkRepository.toggle(title, url)
            updateState { it.copy(isBookmarked = added) }
            emitEffect(BrowserEffect.BookmarkChanged(added))
            emitEffect(
                BrowserEffect.ShowToast(if (added) "已添加到收藏夹" else "已取消收藏")
            )
        }
    }

    private fun checkBookmark(url: String) {
        viewModelScope.launch {
            val bookmarked = bookmarkRepository.isBookmarked(url)
            // 值没变就不发射，避免无意义的重组
            if (currentState.isBookmarked != bookmarked) {
                updateState { it.copy(isBookmarked = bookmarked) }
            }
        }
    }

    private fun openBookmark(url: String) {
        val normalized = normalizeUrl(url)
        updateState { it.copy(addressInput = normalized, errorMessage = null) }
        emitEffect(BrowserEffect.QrResult(normalized))
    }

    /**
     * 将用户输入规范化为完整 URL
     *
     * 规则：
     * - 已以 http:// 或 https:// 开头 → 原样返回
     * - 看起来像域名（含 . 且不含空格）→ 添加 https:// 前缀
     * - 其他 → 作为搜索词用 Google 搜索
     */
    private fun normalizeUrl(input: String): String {
        val trimmed = input.trim()
        return when {
            trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.contains('.') && !trimmed.contains(' ') -> "https://$trimmed"
            else -> "https://www.google.com/search?q=${Uri.encode(trimmed)}"
        }
    }

    // ===== WebView 回调入口（由 UI 层调用） =====

    /**
     * 页面开始加载：把 URL、加载态、前进/后退态合并为一次状态更新。
     *
     * 之前每个回调会分别 dispatch 多次 Intent，导致一次页面跳转触发 4~6 次
     * StateFlow 发射，进而让整个 BrowserScreen（含 AndroidView）反复重组，
     * 这是网页滚动时掉帧的主要来源之一。
     *
     * 值未变化时直接返回原 state 实例，StateFlow 不会发射，也不产生重组。
     */
    fun onPageStarted(url: String, canGoBack: Boolean, canGoForward: Boolean) {
        updateState { state ->
            if (state.currentUrl == url &&
                state.isLoading &&
                state.errorMessage == null &&
                state.canGoBack == canGoBack &&
                state.canGoForward == canGoForward
            ) {
                state
            } else {
                state.copy(
                    currentUrl = url,
                    isLoading = true,
                    errorMessage = null,
                    canGoBack = canGoBack,
                    canGoForward = canGoForward,
                )
            }
        }
        checkBookmark(url)
    }

    /**
     * 页面加载完成：同样合并为一次状态更新。
     *
     * 上次访问地址只写入持久化层，不再回写 UI State——页面恢复只依赖
     * ViewModel 初始化时读取的那一次，避免加载完成时多一次重组。
     */
    fun onPageFinished(
        url: String,
        canGoBack: Boolean,
        canGoForward: Boolean,
        title: String?,
    ) {
        updateState { state ->
            val newTitle = title?.takeIf { it.isNotBlank() } ?: state.title
            if (state.currentUrl == url &&
                !state.isLoading &&
                state.canGoBack == canGoBack &&
                state.canGoForward == canGoForward &&
                state.title == newTitle
            ) {
                state
            } else {
                state.copy(
                    currentUrl = url,
                    isLoading = false,
                    canGoBack = canGoBack,
                    canGoForward = canGoForward,
                    title = newTitle,
                )
            }
        }
        viewModelScope.launch { bookmarkRepository.setLastVisited(url) }
    }

    fun updateNavState(canGoBack: Boolean, canGoForward: Boolean) {
        updateState { state ->
            if (state.canGoBack == canGoBack && state.canGoForward == canGoForward) state
            else state.copy(canGoBack = canGoBack, canGoForward = canGoForward)
        }
    }

    fun updateTitle(title: String) {
        if (title.isBlank() || currentState.title == title) return
        updateState { it.copy(title = title) }
    }

    fun updateAddressInput(input: String) {
        if (currentState.addressInput == input) return
        updateState { it.copy(addressInput = input) }
    }

    private companion object {
        const val KEY_PENDING_URL = "pending_download_url"
        const val KEY_PENDING_NAME = "pending_download_name"
    }
}
