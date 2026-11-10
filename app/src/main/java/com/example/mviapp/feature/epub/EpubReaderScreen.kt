package com.example.mviapp.feature.epub

import android.annotation.SuppressLint
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.view.View
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mviapp.core.epub.EpubBook
import com.example.mviapp.core.epub.EpubChapterPage
import com.example.mviapp.core.epub.EpubHtmlFormatter
import com.example.mviapp.core.ui.AppTopBar
import com.example.mviapp.core.ui.EpubWebView
import com.example.mviapp.core.ui.display.DisplaySettingsSheet
import com.example.mviapp.core.ui.display.ReaderDisplayCss
import com.example.mviapp.core.ui.rememberAppBarScrollController
import com.example.mviapp.core.ui.theme.LocalEInkMode
import com.example.mviapp.core.ui.theme.LocalEInkMonochrome
import com.example.mviapp.core.ui.theme.LocalReaderTextScale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/**
 * EPUB 阅读器页面。
 *
 * 渲染要点：
 * - 单 WebView 复用，翻章只换 HTML，且用 data class 作 tag 避免重复 loadData
 * - 去掉强制硬件层（与浏览器页同样的滚动掉帧元凶）
 * - 允许 file:// 访问 + 指向解压缓存的 baseUrl，书内图片/CSS 才能显示
 * - 错误页展示具体原因并提供重试
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpubReaderScreen(
    filePath: String,
    fileTitle: String,
    onOpenDrawer: () -> Unit,
    viewModel: EpubReaderViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val topBarController = rememberAppBarScrollController()
    // 点击底部页码直接打开显示设置，阅读时无需先展开抽屉
    var showDisplaySheet by remember { mutableStateOf(false) }

    // 阅读位置：记录「滚动发生时的章节 + 章内 Y」，滚动停止后节流写盘。
    // 章节索引用 rememberUpdatedState 读取，保证 WebView 里只赋值一次的滚动回调
    // 拿到的是事件发生时（而非回调创建时）的章节。
    val chapterAtScroll = rememberUpdatedState(state.currentChapterIndex)
    val lastPosition = remember { mutableStateOf<Pair<Int, Int>?>(null) }
    LaunchedEffect(Unit) {
        // collectLatest + delay 实现节流：新滚动事件会取消上一次未完成的等待，
        // 只有停下来超过 800ms 才写盘（避开 FlowPreview 的 debounce）。
        snapshotFlow { lastPosition.value }
            .collectLatest { position ->
                delay(POSITION_SAVE_DEBOUNCE_MS)
                position?.let { (chapter, y) ->
                    viewModel.dispatch(EpubReaderIntent.SavePosition(chapter, y))
                }
            }
    }
    // 返回 / 切到别的页面时立即保存，不必等节流窗口
    DisposableEffect(viewModel) {
        onDispose {
            lastPosition.value?.let { (chapter, y) ->
                viewModel.dispatch(EpubReaderIntent.SavePosition(chapter, y))
            }
        }
    }

    // 墨水屏高对比样式由渲染层替换占位符注入，章节 HTML 缓存不因开关/字号变化而失效
    val eInkMode = LocalEInkMode.current
    val textScale = LocalReaderTextScale.current
    val monochrome = LocalEInkMonochrome.current
    val readerCss = remember(eInkMode, textScale, monochrome) {
        READER_THEME_CSS + if (eInkMode) {
            ReaderDisplayCss.highContrast(textScale, monochrome)
        } else {
            ""
        }
    }

    LaunchedEffect(filePath) {
        viewModel.setFile(Uri.parse(filePath), fileTitle)
    }

    Scaffold(
        modifier = Modifier.nestedScroll(topBarController.nestedScrollConnection),
        topBar = {
            AppTopBar(
                title = state.book?.title ?: fileTitle.ifBlank { "EPUB 阅读" },
                subtitle = state.book?.chapters?.getOrNull(state.currentChapterIndex)?.title,
                onOpenDrawer = onOpenDrawer,
                controller = topBarController,
                actions = {
                    IconButton(onClick = { viewModel.dispatch(EpubReaderIntent.ToggleToc) }) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = "目录")
                    }
                },
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { viewModel.dispatch(EpubReaderIntent.PrevChapter) },
                    enabled = state.currentChapterIndex > 0,
                ) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = "上一章")
                }
                Box(
                    modifier = Modifier
                        .heightIn(min = 40.dp)
                        .semantics { contentDescription = "显示设置" }
                        // 墨水屏刷新慢，去掉涟漪动画避免残影
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            showDisplaySheet = true
                        }
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${state.currentChapterIndex + 1}/${state.book?.chapters?.size ?: 0}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
                IconButton(
                    onClick = { viewModel.dispatch(EpubReaderIntent.NextChapter) },
                    enabled = state.book != null &&
                        state.currentChapterIndex < (state.book?.chapters?.size ?: 1) - 1,
                ) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = "下一章")
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.isLoading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        ReaderLoading()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("正在解析书籍…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                state.errorMessage != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = state.errorMessage.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.dispatch(EpubReaderIntent.Retry) }) {
                            Text("重试")
                        }
                    }
                }
                else -> {
                    EpubWebView(
                        page = state.page,
                        isLoading = state.isChapterLoading,
                        onWebViewScroll = { y ->
                            topBarController.onWebViewScroll(y)
                            val chapter = chapterAtScroll.value
                            lastPosition.value = chapter to y
                            // 同步记进 ViewModel，页面销毁时可作为最后一次位置的兜底
                            viewModel.onScroll(chapter, y)
                        },
                        readerCss = readerCss,
                        restoreScrollY = state.restoreScrollY,
                        restoreRequestId = state.restoreRequestId,
                        onRestoreConsumed = {
                            viewModel.dispatch(EpubReaderIntent.RestoreHandled)
                        },
                    )
                    if (state.chapterError != null) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(16.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = state.chapterError.orEmpty(),
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        viewModel.dispatch(EpubReaderIntent.RetryChapter)
                                    },
                                ) { Text("重试本章") }
                            }
                        }
                    }
                }
            }
        }
    }

    // 目录弹窗状态由 ViewModel 单一数据源驱动，避免本地状态与 state 不同步
    if (state.showToc) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.dispatch(EpubReaderIntent.CloseToc) },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            TocSheetContent(
                book = state.book,
                currentIndex = state.currentChapterIndex,
                onChapterClick = { index ->
                    viewModel.dispatch(EpubReaderIntent.JumpToChapter(index))
                },
            )
        }
    }

    // 显示设置面板：由底部页码点击触发，抽屉里的「显示设置」入口打开的是同一个面板
    if (showDisplaySheet) {
        DisplaySettingsSheet(onDismiss = { showDisplaySheet = false })
    }
}

/**
 * EPUB 正文 WebView。
 *
 * - `allowFileAccess = true` + [EpubChapterPage.baseUrl] 是图片/CSS 能显示的关键：
 *   旧实现把 baseUrl 指向 android_asset 且禁用文件访问，书内相对资源全部 404。
 * - `LAYER_TYPE_NONE`：EPUB 是静态内容，强制硬件层反而让每帧多一次离屏合成。
 * - 禁用缩放与 force dark：缩放会带来横向平移；系统深色反色会把书中图片变灰。
 *   阅读区统一纸白底色，让图片保持原色、书内自带的深色文字仍可读。
 */
private const val READER_THEME_CSS =
    "html,body{background-color:#FFFFFF !important;color:#202124 !important;}" +
        "a{color:#1A73E8 !important;}"

/** 墨水屏不用无限循环的进度动画：刷新慢，会一直留残影 */
@Composable
private fun ReaderLoading(modifier: Modifier = Modifier) {
    if (LocalEInkMode.current) {
        Text(
            text = "正在加载…",
            modifier = modifier,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    } else {
        LinearProgressIndicator(modifier = modifier)
    }
}

/** WebView 渲染键：切换墨水屏模式或字号时必须重新 loadData */
private data class RenderConfig(val page: EpubChapterPage?, val css: String)

@Suppress("DEPRECATION")
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun EpubWebView(
    page: EpubChapterPage?,
    isLoading: Boolean,
    onWebViewScroll: (Int) -> Unit,
    readerCss: String,
    restoreScrollY: Int,
    restoreRequestId: Int,
    onRestoreConsumed: () -> Unit,
) {
    // 墨水屏设备的 WebView 沙箱渲染进程偶发被系统判为「坏进程」：
    // loadData 静默失败、onPageFinished 不回调，界面只剩一片空白且没有任何提示。
    // 这里同时监听渲染进程崩溃和「加载迟迟不完成」，给用户一个可操作的恢复入口。
    val renderFailed = remember { mutableStateOf(false) }
    val pageReady = remember { mutableStateOf(false) }
    var generation by remember { mutableStateOf(0) }

    // 打开书时由 ViewModel 指定的恢复位置（书内滚动 Y）
    val pendingRestore = remember { mutableStateOf(0) }
    LaunchedEffect(restoreRequestId) {
        if (restoreRequestId > 0) pendingRestore.value = restoreScrollY
    }

    val reload: () -> Unit = {
        renderFailed.value = false
        pageReady.value = false
        generation++
    }
    val onPageReady: (WebView) -> Unit = { view ->
        pageReady.value = true
        renderFailed.value = false
        // 打开书时的持久化位置；同章重灌的位置由 update 直接负责，避免时序竞争
        val target = pendingRestore.value
        if (target > 0) {
            pendingRestore.value = 0
            onRestoreConsumed()
            restoreWebViewScroll(view, target, attempt = 0)
        }
    }
    val onRendererGone: () -> Unit = { renderFailed.value = true }

    // 看门狗：正常章节毫秒级就能 onPageFinished，超时仍未完成说明渲染没跑起来
    LaunchedEffect(generation, page, readerCss) {
        if (page == null) return@LaunchedEffect
        delay(RENDER_WATCHDOG_MS)
        if (!pageReady.value) renderFailed.value = true
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // key 变化会销毁旧 WebView 并在 onRelease 中 destroy，重新创建后自动重灌章节
        key(generation) {
            AndroidView(
                factory = { context ->
                    EpubWebView(context).apply {
                        // 原生 WebView 不参与 Compose 嵌套滚动，手动同步滚动位置
                        onScrollYChanged = onWebViewScroll
                        settings.apply {
                            javaScriptEnabled = false
                            domStorageEnabled = false
                            // 书内图片/样式表位于应用缓存目录，需允许 file:// 读取
                            allowFileAccess = true
                            allowContentAccess = false
                            // JS 已关闭，这里只放开 file:// 子资源（图片/CSS）的读取
                            allowFileAccessFromFileURLs = true
                            // 缩放会让页面宽于屏幕，从而产生横向平移，阅读页不需要
                            setSupportZoom(false)
                            builtInZoomControls = false
                            displayZoomControls = false
                            useWideViewPort = true
                            loadWithOverviewMode = false
                            cacheMode = WebSettings.LOAD_NO_CACHE
                            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            textZoom = 100
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                forceDark = WebSettings.FORCE_DARK_OFF
                            }
                        }
                        setBackgroundColor(Color.WHITE)
                        setLayerType(View.LAYER_TYPE_NONE, null)
                        overScrollMode = View.OVER_SCROLL_NEVER
                        isVerticalScrollBarEnabled = false
                        isHorizontalScrollBarEnabled = false
                        webViewClient = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            EpubWebViewClient(onRendererGone, onPageReady)
                        } else {
                            LegacyWebViewClient(onPageReady)
                        }
                    }
                },
                update = { view ->
                    view.onScrollYChanged = onWebViewScroll
                    // 结构比较：只有章节或显示配置真的变化时才重新 loadData
                    val config = RenderConfig(page, readerCss)
                    if (view.tag != config) {
                        if (page != null) {
                            pageReady.value = false
                            val previous = view.tag as? RenderConfig
                            val sameChapter = previous?.page != null &&
                                previous.page.chapterIndex == page.chapterIndex &&
                                page.chapterIndex >= 0
                            // 同章重灌（资源补齐 / 切字号）保留重灌前的真实滚动位置；
                            // 翻章或首次加载清掉，由 pendingRestore 决定目标位置。
                            val keepY = if (sameChapter) view.scrollY else 0
                            // 固定版式书籍保留整页设计宽度，交给 WebView 缩放到屏宽
                            view.settings.loadWithOverviewMode = page.fixedLayout
                            // 墨水屏高对比 CSS 追加在主题 CSS 之后，压过书籍自带配色
                            val html = page.html.replace(
                                EpubHtmlFormatter.THEME_PLACEHOLDER,
                                readerCss,
                            )
                            view.loadDataWithBaseURL(
                                page.baseUrl ?: "file:///android_asset/",
                                html,
                                "text/html",
                                "UTF-8",
                                null,
                            )
                            // 同章重灌不依赖 onPageFinished：页面把滚动重置回 0 往往晚于
                            // 回调，因此在 loadData 后直接开始分帧校正。
                            // 打开书恢复尚未消费时交给 pendingRestore，避免两个目标打架。
                            if (keepY > 0 && pendingRestore.value <= 0) {
                                restoreWebViewScroll(view, keepY, attempt = 0)
                            }
                        }
                        view.tag = config
                    }
                },
                onRelease = { view ->
                    view.stopLoading()
                    view.destroy()
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (renderFailed.value) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.medium,
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "页面渲染进程异常，正文未能显示",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "可尝试重新加载；若反复出现，请重启设备",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(onClick = reload) { Text("重新加载") }
                }
            }
        }

        if (isLoading) {
            ReaderLoading(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
            )
        }
    }
}

/** 正常加载最多几十毫秒；给墨水屏留足余量，避免慢刷新时误报 */
private const val RENDER_WATCHDOG_MS = 8_000L

/** 滚动停止后多久写一次阅读位置，避免每帧都写 SharedPreferences */
private const val POSITION_SAVE_DEBOUNCE_MS = 800L

/**
 * 页面刚渲染完时，图片 / CSS / 字号变化仍在异步排版，内容高度可能继续变化并把
 * 滚动位置重置回 0（切换字号尤其明显）。因此在约 700ms 内持续分帧校正：
 * 每帧只在落后目标时补滚，用户已往下读时不抢滚动。
 */
private fun restoreWebViewScroll(view: WebView, target: Int, attempt: Int) {
    if (attempt > 6) return
    view.postDelayed({
        // 只在落后目标时补滚，用户已经往下读时不抢滚动
        if (view.scrollY < target - 4) {
            view.scrollTo(0, target)
        }
        // 无条件继续检查：首次检查时旧页面可能还停在目标位置，
        // 内容真正重置要再过几帧，提前结束会把用户留在章首。
        if (attempt < 6) restoreWebViewScroll(view, target, attempt + 1)
    }, if (attempt == 0) 0L else 120L)
}

/**
 * 渲染进程失效时返回 true，表示由页面自行销毁重建 WebView；
 * 返回 false 会让系统直接结束应用进程，用户只会看到应用闪退。
 */
@RequiresApi(Build.VERSION_CODES.O)
private class EpubWebViewClient(
    private val onRendererGone: () -> Unit,
    private val onPageReady: (WebView) -> Unit,
) : WebViewClient() {
    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        onRendererGone()
        return true
    }

    override fun onPageFinished(view: WebView, url: String) {
        super.onPageFinished(view, url)
        onPageReady(view)
    }
}

/** API 26 以下没有 onRenderProcessGone，只保留加载完成回调 */
private class LegacyWebViewClient(
    private val onPageReady: (WebView) -> Unit,
) : WebViewClient() {
    override fun onPageFinished(view: WebView, url: String) {
        super.onPageFinished(view, url)
        onPageReady(view)
    }
}

/** 目录底部弹窗内容 */
@Composable
private fun TocSheetContent(
    book: EpubBook?,
    currentIndex: Int,
    onChapterClick: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp),
    ) {
        Text(
            text = "目录",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (book == null) {
            Text(
                text = "加载中…",
                modifier = Modifier.padding(horizontal = 24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                items(book.chapters, key = { it.index }) { chapter ->
                    val isCurrent = chapter.index == currentIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onChapterClick(chapter.index) }
                            .padding(horizontal = 24.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (isCurrent) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        } else {
                            Spacer(modifier = Modifier.width(32.dp))
                        }
                        Text(
                            text = chapter.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isCurrent) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}
