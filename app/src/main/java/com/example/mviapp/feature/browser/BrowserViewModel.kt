package com.example.mviapp.feature.browser

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
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
            is BrowserIntent.PageStarted -> onPageStarted(intent.url)
            is BrowserIntent.PageFinished -> onPageFinished(intent.url)
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

    private fun onPageStarted(url: String) {
        updateState { it.copy(currentUrl = url, isLoading = true, errorMessage = null) }
        // 检查收藏状态
        viewModelScope.launch {
            val bookmarked = bookmarkRepository.isBookmarked(url)
            updateState { it.copy(isBookmarked = bookmarked) }
        }
    }

    private fun onPageFinished(url: String) {
        updateState { it.copy(currentUrl = url, isLoading = false) }
        // 保存上次访问的网址
        viewModelScope.launch {
            bookmarkRepository.setLastVisited(url)
            updateState { it.copy(lastVisitedUrl = url) }
        }
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

    private fun confirmDownload(url: String, fileName: String, destinationUri: Uri) {
        viewModelScope.launch {
            downloadRepository.startDownload(url, fileName, destinationUri)
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
            updateState { it.copy(isBookmarked = bookmarked) }
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

    // ===== WebView 导航状态同步（由 UI 层调用以更新状态） =====

    fun updateNavState(canGoBack: Boolean, canGoForward: Boolean) {
        updateState { it.copy(canGoBack = canGoBack, canGoForward = canGoForward) }
    }

    fun updateTitle(title: String) {
        updateState { it.copy(title = title) }
    }

    fun updateAddressInput(input: String) {
        updateState { it.copy(addressInput = input) }
    }
}
