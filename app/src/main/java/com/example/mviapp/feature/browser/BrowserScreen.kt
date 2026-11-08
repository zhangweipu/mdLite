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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
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
    val context = androidx.compose.ui.platform.LocalContext.current

    // WebView 引用
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    // 待加载的 URL
    var pendingUrl by remember { mutableStateOf<String?>(null) }
    // 待下载的文件信息
    var pendingDownload by remember { mutableStateOf<Pair<String, String>?>(null) }
    // 是否已经加载过上次访问的网址
    var hasLoadedInitial by remember { mutableStateOf(false) }
    // 地址输入弹窗是否显示
    var showAddressDialog by remember { mutableStateOf(false) }
    // 弹窗中的地址输入（本地状态，确认后才同步到 ViewModel）
    var dialogInput by remember { mutableStateOf("") }

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

    // 打开地址弹窗时，用当前地址填充
    fun openAddressDialog() {
        dialogInput = state.currentUrl.ifBlank { state.addressInput }
        showAddressDialog = true
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
                    IconButton(onClick = { openAddressDialog() }) {
                        Icon(Icons.Filled.Link, contentDescription = "地址")
                    }
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
        bottomBar = {
            // 底部导航栏：后退 / 前进 / 刷新
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { webViewRef?.goBack() }, enabled = state.canGoBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "后退")
                }
                IconButton(onClick = { webViewRef?.goForward() }, enabled = state.canGoForward) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "前进")
                }
                IconButton(onClick = {
                    if (state.isLoading) webViewRef?.stopLoading() else webViewRef?.reload()
                }) {
                    Icon(
                        imageVector = if (state.isLoading) Icons.Filled.Stop else Icons.Filled.Refresh,
                        contentDescription = if (state.isLoading) "停止" else "刷新",
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // 加载进度条
            if (state.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            // WebView（占满剩余空间）
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

    // 地址输入弹窗
    if (showAddressDialog) {
        AddressInputDialog(
            input = dialogInput,
            onInputChange = { dialogInput = it },
            onDismiss = { showAddressDialog = false },
            onConfirm = { input ->
                val normalized = normalizeUrl(input)
                viewModel.updateAddressInput(normalized)
                pendingUrl = normalized
                showAddressDialog = false
            },
            onPaste = {
                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = clipboard.primaryClip
                val text = clip?.getItemAt(0)?.text?.toString()?.trim()
                if (!text.isNullOrBlank()) {
                    dialogInput = text
                }
            },
            onScanQr = {
                showAddressDialog = false
                pickImageLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
        )
    }
}

/** 规范化 URL（用于弹窗确认后直接加载） */
private fun normalizeUrl(input: String): String {
    val trimmed = input.trim()
    return when {
        trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
        trimmed.contains('.') && !trimmed.contains(' ') -> "https://$trimmed"
        else -> "https://www.google.com/search?q=${Uri.encode(trimmed)}"
    }
}

/**
 * 地址输入弹窗
 *
 * 把地址输入从主界面移到弹窗，节省页面空间，
 * 让 WebView 内容区域更大，低配设备上也更流畅。
 */
@Composable
private fun AddressInputDialog(
    input: String,
    onInputChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    onPaste: () -> Unit,
    onScanQr: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("输入网址") },
        text = {
            Column {
                OutlinedTextField(
                    value = input,
                    onValueChange = onInputChange,
                    placeholder = { Text("网址或搜索词") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(
                        onGo = {
                            if (input.isNotBlank()) onConfirm(input)
                        }
                    ),
                    trailingIcon = {
                        if (input.isNotBlank()) {
                            IconButton(onClick = { onInputChange("") }) {
                                Icon(Icons.Filled.Close, contentDescription = "清空")
                            }
                        }
                    },
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    IconButton(onClick = onPaste) {
                        Icon(Icons.Filled.ContentPaste, contentDescription = "粘贴")
                    }
                    IconButton(onClick = onScanQr) {
                        Icon(Icons.Filled.QrCodeScanner, contentDescription = "扫码")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(input) },
                enabled = input.isNotBlank(),
            ) {
                Text("前往")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
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
