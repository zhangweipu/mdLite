package com.example.mviapp.feature.html

import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
 * HTML 阅读页
 *
 * 使用系统 WebView 完整渲染 HTML（保留 CSS、布局、图片等），
 * 记录/恢复滚动位置，支持置顶。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HtmlReaderScreen(
    filePath: String,
    fileTitle: String,
    onOpenDrawer: () -> Unit,
    viewModel: HtmlReaderViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var webView by remember { mutableStateOf<WebView?>(null) }
    var textMode by remember { mutableStateOf(false) }

    // 页面离开时保存滚动位置（WebView 的释放交给 AndroidView onRelease）
    DisposableEffect(viewModel) {
        onDispose {
            viewModel.dispatch(HtmlReaderIntent.SavePosition(webView?.scrollY ?: 0))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = fileTitle,
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
                    val success = state as? HtmlReaderState.Success
                    if (success != null) {
                        IconButton(onClick = { textMode = !textMode }) {
                            Icon(
                                imageVector = Icons.Filled.Description,
                                contentDescription = if (textMode) "切换到网页模式" else "切换到文本模式",
                            )
                        }
                        IconButton(onClick = { viewModel.dispatch(HtmlReaderIntent.TogglePin) }) {
                            Icon(
                                imageVector = if (success.pinned) {
                                    Icons.Filled.PushPin
                                } else {
                                    Icons.Outlined.PushPin
                                },
                                contentDescription = if (success.pinned) "取消置顶" else "置顶",
                                tint = if (success.pinned) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (val s = state) {
                is HtmlReaderState.Loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

                is HtmlReaderState.Success -> {
                    if (textMode) {
                        PlainTextContent(content = s.content)
                    } else {
                        AndroidView(
                            factory = { context ->
                                WebView(context).apply {
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.loadWithOverviewMode = true
                                    settings.useWideViewPort = true
                                    webViewClient = object : WebViewClient() {
                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            if (s.restoreY > 0) {
                                                view?.scrollTo(0, s.restoreY)
                                            }
                                        }

                                        override fun onReceivedError(
                                            view: WebView?,
                                            request: WebResourceRequest?,
                                            error: WebResourceError?,
                                        ) {
                                            // 主页面加载失败时自动回退到文本模式，避免白屏
                                            if (request?.isForMainFrame == true) {
                                                textMode = true
                                            }
                                        }
                                    }
                                    webView = this
                                    loadDataWithBaseURL(null, s.content, "text/html", "UTF-8", null)
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                            onRelease = { view -> view.destroy() },
                        )
                    }
                }

                is HtmlReaderState.Error -> Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = s.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = { viewModel.dispatch(HtmlReaderIntent.Retry) }) {
                        Text("重试")
                    }
                }
            }
        }
    }
}

/** 文本模式：直接显示 HTML 原文（去掉标签前先简单处理），保证任何环境都能阅读 */
@Composable
private fun PlainTextContent(content: String) {
    val text = remember(content) {
        content
            .replace(Regex("""(?is)<script[^>]*>.*?</script>"""), "")
            .replace(Regex("""(?is)<style[^>]*>.*?</style>"""), "")
            .replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
