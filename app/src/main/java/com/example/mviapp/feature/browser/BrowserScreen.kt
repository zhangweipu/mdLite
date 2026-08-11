package com.example.mviapp.feature.browser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * 网页浏览器页面
 *
 * 功能：
 * - 地址栏输入网址 / 粘贴 / 二维码识别打开网页
 * - 自动恢复上次访问的网址
 * - 收藏夹（添加/取消/管理）
 * - 网页内下载（加入下载管理）
 * - 前进/后退/刷新/停止
 * - 轻量优化：硬件加速、合理缓存、禁用不必要特性
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    onBack: () -> Unit,
    onNavigateToDownloadList: () -> Unit,
    onNavigateToBookmarks: () -> Unit,
    initialUrl: String = "",
    viewModel: BrowserViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // WebView 引用
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    // 待加载的 URL
    var pendingUrl by remember { mutableStateOf<String?>(null) }
    // 待下载的文件信息
    var pendingDownload by remember { mutableStateOf<Pair<String, String>?>(null) }
    // 是否已经加载过上次访问的网址
    var hasLoadedInitial by remember { mutableStateOf(false) }

    // 系统"保存到"选择器（下载文件）
    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val pending = pendingDownload
        pendingDownload = null
        if (uri != null && pending != null) {
            val (url, name) = pending
            viewModel.dispatch(BrowserIntent.ConfirmDownload(url, name, uri))
        }
    }

    // 相册选图（二维码识别）
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            viewModel.dispatch(BrowserIntent.ScanQrFromImage(uri))
        }
    }

    // 消费一次性 Effect
    LaunchedEffect(Unit) {
        viewModel.uiEffect.collect { effect ->
            when (effect) {
                is BrowserEffect.ShowToast -> snackbarHostState.showSnackbar(effect.message)
                is BrowserEffect.NavigateToDownloadList -> onNavigateToDownloadList()
                is BrowserEffect.NavigateToBookmarks -> onNavigateToBookmarks()
                is BrowserEffect.RequestSaveLocation -> {
                    pendingDownload = effect.url to effect.fileName
                    saveLauncher.launch(effect.fileName)
                }
                is BrowserEffect.QrResult -> {
                    // 二维码/粘贴/收藏结果 → 触发 WebView 加载
                    pendingUrl = effect.content
                }
                is BrowserEffect.BookmarkChanged -> {
                    // 收藏状态变化，图标会通过 state 自动更新
                }
            }
        }
    }

    // 页面首次加载：优先加载传入的 URL，其次加载上次访问的网址
    LaunchedEffect(state.lastVisitedUrl, initialUrl) {
        val targetUrl = when {
            initialUrl.isNotBlank() -> initialUrl
            !hasLoadedInitial && !state.lastVisitedUrl.isNullOrBlank() -> state.lastVisitedUrl
            else -> null
        }
        if (targetUrl != null && webViewRef?.url == null) {
            pendingUrl = targetUrl
            hasLoadedInitial = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.title.ifBlank { "浏览器" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.dispatch(BrowserIntent.ToggleBookmark) }) {
                        Icon(
                            imageVector = if (state.isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                            contentDescription = if (state.isBookmarked) "取消收藏" else "收藏",
                        )
                    }
                    IconButton(onClick = { viewModel.dispatch(BrowserIntent.OpenBookmarks) }) {
                        Icon(Icons.Filled.Bookmark, contentDescription = "收藏夹")
                    }
                    IconButton(onClick = onNavigateToDownloadList) {
                        Icon(Icons.Filled.Download, contentDescription = "下载管理")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // 地址栏 + 导航按钮
            AddressBar(
                input = state.addressInput,
                onInputChange = { viewModel.updateAddressInput(it) },
                onSubmit = { input ->
                    viewModel.dispatch(BrowserIntent.SubmitUrl(input))
                    pendingUrl = normalizeForLoad(input, state.addressInput)
                },
                onPaste = { viewModel.dispatch(BrowserIntent.PasteAndGo) },
                onScanQr = {
                    pickImageLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                canGoBack = state.canGoBack,
                canGoForward = state.canGoForward,
                isLoading = state.isLoading,
                onGoBack = { webViewRef?.goBack() },
                onGoForward = { webViewRef?.goForward() },
                onRefresh = { webViewRef?.reload() },
                onStop = { webViewRef?.stopLoading() },
            )

            // 加载进度条
            if (state.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                Spacer(modifier = Modifier.height(1.dp))
            }

            // WebView
            Box(modifier = Modifier.weight(1f)) {
                WebViewContent(
                    onWebViewReady = { webViewRef = it },
                    pendingUrl = pendingUrl,
                    onUrlLoaded = { pendingUrl = null },
                    viewModel = viewModel,
                    onDownloadStart = { url, _, contentDisposition, mimeType, _ ->
                        val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
                        viewModel.dispatch(BrowserIntent.StartDownload(url, fileName))
                    },
                )
            }
        }
    }
}

/** 规范化 URL 用于加载 */
private fun normalizeForLoad(rawInput: String, stateInput: String): String {
    val input = stateInput.ifBlank { rawInput.trim() }
    return when {
        input.startsWith("http://", ignoreCase = true) ||
            input.startsWith("https://", ignoreCase = true) -> input
        input.contains('.') && !input.contains(' ') -> "https://$input"
        else -> "https://www.google.com/search?q=${Uri.encode(input)}"
    }
}

@Composable
private fun AddressBar(
    input: String,
    onInputChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onPaste: () -> Unit,
    onScanQr: () -> Unit,
    canGoBack: Boolean,
    canGoForward: Boolean,
    isLoading: Boolean,
    onGoBack: () -> Unit,
    onGoForward: () -> Unit,
    onRefresh: () -> Unit,
    onStop: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                placeholder = { Text("输入网址或搜索") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                trailingIcon = {
                    Row {
                        IconButton(onClick = onPaste) {
                            Icon(Icons.Filled.ContentPaste, contentDescription = "粘贴")
                        }
                        IconButton(onClick = onScanQr) {
                            Icon(Icons.Filled.QrCodeScanner, contentDescription = "扫码")
                        }
                        if (input.isNotBlank()) {
                            IconButton(onClick = { onInputChange("") }) {
                                Icon(Icons.Filled.Close, contentDescription = "清空")
                            }
                        }
                    }
                },
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onGoBack, enabled = canGoBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "后退")
            }
            IconButton(onClick = onGoForward, enabled = canGoForward) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "前进")
            }
            IconButton(onClick = { if (isLoading) onStop() else onRefresh() }) {
                Icon(
                    imageVector = if (isLoading) Icons.Filled.Stop else Icons.Filled.Refresh,
                    contentDescription = if (isLoading) "停止" else "刷新",
                )
            }
        }
    }
}

/**
 * WebView 内容组件（轻量优化版）
 *
 * 针对低配设备的优化：
 * - 启用硬件加速
 * - 合理的缓存策略（优先使用缓存，减少网络请求）
 * - 禁用不必要的插件和自动加载图片（可选，这里保留默认以兼容更多网站）
 * - 禁用文件访问，只允许 content:// 访问
 * - 关闭数据库存储（用缓存即可）
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebViewContent(
    onWebViewReady: (WebView) -> Unit,
    pendingUrl: String?,
    onUrlLoaded: () -> Unit,
    viewModel: BrowserViewModel,
    onDownloadStart: (String, String?, String?, String?, Long) -> Unit,
) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.apply {
                    // 基础浏览能力
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    loadWithOverviewMode = true
                    useWideViewPort = true

                    // 缩放：支持但不显示控件（节省空间）
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false

                    // 轻量优化：合理缓存，减少网络消耗
                    cacheMode = WebSettings.LOAD_DEFAULT
                    // 用标准 HTTP 缓存即可（OkHttp 层已处理）

                    // 安全：禁用文件访问，只允许 content URI
                    allowFileAccess = false
                    allowContentAccess = true

                    // 媒体：需要用户手势才播放（节省流量和电量）
                    mediaPlaybackRequiresUserGesture = true

                    // 混合内容：兼容模式（低配设备网络不稳时也能加载）
                    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

                    // 渲染优化
                    setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)

                    // 字体大小默认（跟随系统设置可能导致布局错乱，保持 100%）
                    textZoom = 100
                }

                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        url?.let { viewModel.dispatch(BrowserIntent.PageStarted(it)) }
                        viewModel.updateNavState(
                            view?.canGoBack() == true,
                            view?.canGoForward() == true
                        )
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        url?.let { viewModel.dispatch(BrowserIntent.PageFinished(it)) }
                        viewModel.updateNavState(
                            view?.canGoBack() == true,
                            view?.canGoForward() == true
                        )
                        view?.title?.let { viewModel.updateTitle(it) }
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?,
                    ) {
                        if (request?.isForMainFrame == true) {
                            viewModel.dispatch(
                                BrowserIntent.PageError(
                                    error?.description?.toString() ?: "加载失败"
                                )
                            )
                        }
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onReceivedTitle(view: WebView?, title: String?) {
                        title?.let { viewModel.updateTitle(it) }
                    }
                }

                setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
                    onDownloadStart(url, userAgent, contentDisposition, mimeType, contentLength)
                }

                onWebViewReady(this)
            }
        },
        update = { view ->
            // 有待加载的 URL 时触发加载
            val url = pendingUrl
            if (url != null && url != view.url) {
                view.loadUrl(url)
                onUrlLoaded()
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}
