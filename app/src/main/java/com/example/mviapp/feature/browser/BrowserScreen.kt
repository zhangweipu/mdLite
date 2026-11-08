package com.example.mviapp.feature.browser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
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
import androidx.compose.material.icons.filled.Menu
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.concurrent.atomic.AtomicLong

/**
 * 网页浏览器页面
 *
 * 功能：
 * - 地址栏输入网址 / 粘贴 / 二维码识别打开网页
 * - 自动恢复上次访问的网址
 * - 收藏夹（添加/取消/管理）
 * - 网页内下载（加入下载管理）
 * - 前进/后退/刷新/停止
 *
 * 流畅性优化（见 [WebViewContent] 与 [BrowserWebViewCallbacks]）：
 * - WebView 不再强制使用硬件层，交给系统 WebView 自身的渲染管线
 * - 进度条改为浮层覆盖，加载状态切换不会改变 WebView 高度（避免网页重排）
 * - WebView 相关参数全部 remember 稳定，页面状态变化不会带着 WebView 一起重组
 * - 页面回调合并为一次状态更新，并去掉重复值发射
 * - 离开页面时 destroy WebView，后台时暂停 / 前台时恢复，避免实例堆积
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    onOpenDrawer: () -> Unit,
    onNavigateToDownloadList: () -> Unit,
    onNavigateToBookmarks: () -> Unit,
    initialUrl: String = "",
    viewModel: BrowserViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // WebView 引用与已处理请求 id：用普通持有者而非 mutableStateOf，
    // 避免"拿到 WebView 实例"这种内部事件触发整页重组。
    val webViewHolder = remember { WebViewHolder() }
    // 每次加载请求带一个自增 id，因此重复访问同一网址也能正常触发 loadUrl
    val requestSeq = remember { AtomicLong(0) }
    var loadRequest by remember { mutableStateOf<LoadRequest?>(null) }
    // 页面恢复只做一次，避免 onPageFinished 后重复加载
    val initialLoaded = remember { mutableStateOf(false) }

    // 待下载的文件信息
    var pendingDownload by remember { mutableStateOf<Pair<String, String>?>(null) }
    // 地址输入弹窗是否显示
    var showAddressDialog by remember { mutableStateOf(false) }
    // 弹窗中的地址输入（本地状态，确认后才同步到 ViewModel）
    var dialogInput by remember { mutableStateOf("") }

    fun requestLoad(url: String) {
        loadRequest = LoadRequest(url, requestSeq.incrementAndGet())
    }

    // 所有传给 WebView 的回调合并到一个稳定对象中，
    // 让 WebViewContent 在 state 变化时可被 Compose 跳过重组。
    val callbacks = remember(viewModel) { BrowserWebViewCallbacks(viewModel) }

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
                is BrowserEffect.QrResult -> requestLoad(effect.content)
                is BrowserEffect.BookmarkChanged -> Unit
            }
        }
    }

    // 页面首次加载：优先加载传入的 URL，其次加载上次访问的网址
    LaunchedEffect(state.lastVisitedUrl) {
        if (initialLoaded.value) return@LaunchedEffect
        val target = initialUrl.ifBlank { state.lastVisitedUrl.orEmpty() }
        if (target.isNotBlank()) {
            initialLoaded.value = true
            requestLoad(target)
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
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Filled.Menu, contentDescription = "打开菜单")
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
                IconButton(onClick = { webViewHolder.view?.goBack() }, enabled = state.canGoBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "后退")
                }
                IconButton(onClick = { webViewHolder.view?.goForward() }, enabled = state.canGoForward) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "前进")
                }
                IconButton(onClick = {
                    val view = webViewHolder.view
                    if (state.isLoading) view?.stopLoading() else view?.reload()
                }) {
                    Icon(
                        imageVector = if (state.isLoading) Icons.Filled.Stop else Icons.Filled.Refresh,
                        contentDescription = if (state.isLoading) "停止" else "刷新",
                    )
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // WebView 始终独占整个可用区域，尺寸从不随加载状态变化。
            WebViewContent(
                holder = webViewHolder,
                request = loadRequest,
                callbacks = callbacks,
                modifier = Modifier.fillMaxSize(),
            )

            // 进度条以浮层形式盖在 WebView 顶部，而不是作为 Column 的一项。
            // 之前进度条出现/消失会改变 WebView 高度，触发网页重新布局，
            // 这是加载瞬间和滚动时最明显的卡顿与跳动来源。
            if (state.isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopStart),
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
                requestLoad(normalized)
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

/** 一次 URL 加载请求；id 单调递增，保证同一网址也能被重新加载 */
private data class LoadRequest(val url: String, val id: Long)

/**
 * WebView 实例持有者。
 *
 * 用普通对象而非 Compose State：UI 只需要调用它的方法，不需要观察它，
 * 这样 WebView 创建/销毁不会引发任何重组。
 */
@Stable
private class WebViewHolder {
    var view: WebView? = null

    /** 已交给 WebView 处理的请求 id，避免 update 阶段重复 loadUrl */
    var handledRequestId: Long = -1L
}

/**
 * WebView 回调集合。
 *
 * 标记为 [Stable] 并 remember 后，页面状态（标题、进度、收藏…）变化时
 * [WebViewContent] 的参数保持不变，Compose 可跳过其重组。
 */
@Stable
private class BrowserWebViewCallbacks(private val viewModel: BrowserViewModel) {

    fun onPageStarted(url: String?, view: WebView?) {
        if (url.isNullOrBlank()) return
        viewModel.onPageStarted(
            url,
            view?.canGoBack() == true,
            view?.canGoForward() == true,
        )
    }

    fun onPageFinished(url: String?, view: WebView?) {
        if (url.isNullOrBlank()) return
        viewModel.onPageFinished(
            url,
            view?.canGoBack() == true,
            view?.canGoForward() == true,
            view?.title,
        )
    }

    fun onMainFrameError(message: String?) {
        viewModel.dispatch(
            BrowserIntent.PageError(message?.takeIf { it.isNotBlank() } ?: "加载失败")
        )
    }

    fun onTitle(title: String?) {
        if (!title.isNullOrBlank()) viewModel.updateTitle(title)
    }

    fun onDownload(url: String, contentDisposition: String?, mimeType: String?) {
        val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
        viewModel.dispatch(BrowserIntent.StartDownload(url, fileName))
    }
}

/**
 * WebView 内容组件
 *
 * 关键点：
 * - 使用默认渲染层（LAYER_TYPE_NONE）。之前强制 LAYER_TYPE_HARDWARE 会让
 *   WebView 每帧先把整页内容绘制到离屏纹理再合成，滚动时纹理反复上传，
 *   是中低端设备卡顿的最主要原因。系统 WebView 自身已启用硬件加速。
 * - 关闭边缘回弹与滚动条，减少过度绘制，也避免与抽屉手势产生视觉/事件冲突。
 * - [update] 只做一次 id 比较，不写 Compose State，不会引发额外重组。
 * - [onRelease] 销毁 WebView，反复进出浏览器页不会累积实例与 JS 线程。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebViewContent(
    holder: WebViewHolder,
    request: LoadRequest?,
    callbacks: BrowserWebViewCallbacks,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    // 前后台切换时暂停/恢复该 WebView 的 JS 定时器与渲染，
    // 避免应用退到后台后网页动画继续占用主线程与 GPU。
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> holder.view?.onResume()
                Lifecycle.Event.ON_PAUSE -> holder.view?.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AndroidView(
        factory = { context ->
            // WebView 重建后需要重新处理当前请求
            holder.handledRequestId = -1L
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

                    // 标准 HTTP 缓存，减少重复网络请求
                    cacheMode = WebSettings.LOAD_DEFAULT

                    // 安全：禁用文件访问，只允许 content URI
                    allowFileAccess = false
                    allowContentAccess = true

                    // 媒体：需要用户手势才播放（节省流量和电量）
                    mediaPlaybackRequiresUserGesture = true

                    // 混合内容：兼容模式
                    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

                    // 字体大小固定，避免跟随系统设置导致布局错乱
                    textZoom = 100

                    // 避免 WebView 首次进入就抢焦点、拉起额外布局
                    setNeedInitialFocus(false)
                    javaScriptCanOpenWindowsAutomatically = false
                }

                // 渲染层交给 WebView 自己管理；强制硬件层会显著降低滚动帧率
                setLayerType(View.LAYER_TYPE_NONE, null)
                // 关闭边缘回弹与滚动条：减少过度绘制，也不与抽屉手势互相干扰
                overScrollMode = View.OVER_SCROLL_NEVER
                isHorizontalScrollBarEnabled = false
                isVerticalScrollBarEnabled = false

                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        callbacks.onPageStarted(url, view)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        callbacks.onPageFinished(url, view)
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?,
                    ) {
                        if (request?.isForMainFrame == true) {
                            callbacks.onMainFrameError(error?.description?.toString())
                        }
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onReceivedTitle(view: WebView?, title: String?) {
                        callbacks.onTitle(title)
                    }
                }

                setDownloadListener { url, _, contentDisposition, mimeType, _ ->
                    callbacks.onDownload(url, contentDisposition, mimeType)
                }

                holder.view = this
            }
        },
        update = { view ->
            // 只有在出现新的加载请求时才调用 loadUrl；
            // 用 id 而不是"url 是否等于 view.url"判断，重复访问同一网址也能生效。
            if (request != null && request.id != holder.handledRequestId) {
                holder.handledRequestId = request.id
                view.loadUrl(request.url)
            }
        },
        onRelease = { view ->
            holder.view = null
            view.stopLoading()
            view.destroy()
        },
        modifier = modifier,
    )
}
