package com.example.mviapp.feature.epub

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

/**
 * EPUB 阅读器页面
 *
 * 针对低配设备的性能优化：
 * - 单 WebView 复用，翻章不重建
 * - 预解压到缓存目录，二次打开秒开
 * - 章节懒加载，内存中只缓存最近 3 章
 * - 禁用 JavaScript（EPUB 不需要 JS）
 * - 注入极简阅读样式，加速渲染
 * - 硬件加速 + 合理缓存
 *
 * 交互优化：
 * - 目录用底部弹窗（ModalBottomSheet），避免左边缘滑动误触侧边抽屉
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
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showToc by remember { mutableStateOf(false) }

    // 首次进入时设置文件并加载
    LaunchedEffect(filePath) {
        val uri = Uri.parse(filePath)
        viewModel.setFile(uri, fileTitle)
    }

    // 同步目录显示状态
    LaunchedEffect(state.showToc) {
        if (state.showToc && !showToc) {
            showToc = true
            sheetState.show()
        } else if (!state.showToc && showToc) {
            sheetState.hide()
            showToc = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = state.book?.title ?: fileTitle.ifBlank { "EPUB 阅读" },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        state.book?.chapters?.getOrNull(state.currentChapterIndex)?.let { chapter ->
                            Text(
                                text = chapter.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Filled.Menu, contentDescription = "打开菜单")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            showToc = true
                            viewModel.dispatch(EpubReaderIntent.ToggleToc)
                        }
                    }) {
                        Icon(Icons.Filled.List, contentDescription = "目录")
                    }
                },
            )
        },
        bottomBar = {
            // 底部翻页导航
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
                Text(
                    text = "${state.currentChapterIndex + 1}/${state.book?.chapters?.size ?: 0}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                        LinearProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("正在解析书籍…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                state.errorMessage != null -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = state.errorMessage ?: "加载失败",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                else -> {
                    EpubWebView(
                        content = state.currentContent,
                        isLoading = state.isChapterLoading,
                    )
                }
            }
        }
    }

    // 目录底部弹窗（从底部弹出，避免左滑误触）
    if (showToc) {
        ModalBottomSheet(
            onDismissRequest = {
                showToc = false
                viewModel.dispatch(EpubReaderIntent.CloseToc)
            },
            sheetState = sheetState,
        ) {
            TocSheetContent(
                book = state.book,
                currentIndex = state.currentChapterIndex,
                onChapterClick = { index ->
                    scope.launch { sheetState.hide() }
                    showToc = false
                    viewModel.dispatch(EpubReaderIntent.JumpToChapter(index))
                },
            )
        }
    }
}

/**
 * EPUB WebView 渲染组件（重度优化）
 *
 * 优化点：
 * - 禁用 JS（EPUB 阅读不需要，大幅减少渲染开销）
 * - 启用硬件加速
 * - 禁用不必要的 WebView 特性
 * - 注入极简阅读样式（在 Repository 层已处理）
 * - 单例复用，翻章只 loadData
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun EpubWebView(
    content: String?,
    isLoading: Boolean,
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    settings.apply {
                        // 禁用 JS（EPUB 阅读不需要，大幅减少渲染开销）
                        javaScriptEnabled = false
                        // 启用 DOM 存储（有些 CSS 需要）
                        domStorageEnabled = false
                        // 支持缩放
                        setSupportZoom(true)
                        builtInZoomControls = true
                        displayZoomControls = false
                        // 自适应屏幕
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        // 缓存模式：用缓存，减少 IO
                        cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
                        // 禁用文件访问（用 content URI 替代）
                        allowFileAccess = false
                        allowContentAccess = true
                        // 文字缩放保持 100%
                        textZoom = 100
                        // 硬件加速
                        setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                    }
                    webViewClient = object : WebViewClient() {
                        // 所有链接都在 WebView 内打开（EPUB 内部跳转）
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            url: String?
                        ): Boolean {
                            return false
                        }
                    }
                    webViewRef = this
                }
            },
            update = { view ->
                // 内容变化时加载
                if (content != null && view.tag != content.hashCode()) {
                    view.loadDataWithBaseURL(
                        "file:///android_asset/",
                        content,
                        "text/html",
                        "UTF-8",
                        null,
                    )
                    view.tag = content.hashCode()
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // 章节加载中的进度条
        if (isLoading) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
            )
        }
    }
}

/** 目录底部弹窗内容 */
@Composable
private fun TocSheetContent(
    book: com.example.mviapp.core.epub.EpubBook?,
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
            LazyColumn(
                modifier = Modifier.heightIn(max = 400.dp),
            ) {
                items(book.chapters) { chapter ->
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
                            color = if (isCurrent)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface,
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
