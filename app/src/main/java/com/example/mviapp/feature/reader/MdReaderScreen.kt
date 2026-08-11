package com.example.mviapp.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mviapp.core.common.isTxtFile
import com.example.mviapp.core.marks.Bookmark
import com.example.mviapp.core.marks.ParagraphHighlight
import com.example.mviapp.core.md.MarkdownContent
import com.example.mviapp.core.md.MarkdownParser
import com.example.mviapp.core.ui.config.LocalAppDimens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Markdown 阅读页
 *
 * 根据导航参数 [filePath] 读取文档，成功后将原文交给 [MarkdownContent] 渲染。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MdReaderScreen(
    filePath: String,
    fileTitle: String,
    onBack: () -> Unit,
    viewModel: MdReaderViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dimens = LocalAppDimens.current
    // 内容与滚动状态提升到页面级：顶栏的“添加书签”需要读取当前滚动位置
    val content = (state as? MdReaderState.Success)?.content
    // txt 按行拆成段落保留换行；其余按 Markdown 解析渲染
    val blocks = remember(content, filePath) {
        content?.let {
            val source = when {
                filePath.isTxtFile() -> it.lines().joinToString("\n\n") { line -> line.trimEnd() }
                else -> it
            }
            MarkdownParser.parse(source)
        } ?: emptyList()
    }
    val listState = rememberLazyListState()
    var showMarks by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

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
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                actions = {
                    val success = state as? MdReaderState.Success
                    if (success != null) {
                        IconButton(
                            onClick = {
                                val index = listState.firstVisibleItemIndex
                                val preview = blocks.getOrNull(index)
                                    ?.let(MarkdownParser::blockPreview)
                                    .orEmpty()
                                viewModel.dispatch(MdReaderIntent.AddBookmark(index, preview))
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Filled.BookmarkAdd,
                                contentDescription = "添加书签",
                            )
                        }
                        IconButton(onClick = { showMarks = true }) {
                            Icon(
                                imageVector = Icons.Filled.List,
                                contentDescription = "标记列表",
                            )
                        }
                        IconButton(onClick = { viewModel.dispatch(MdReaderIntent.TogglePin) }) {
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
                is MdReaderState.Loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

                is MdReaderState.Success -> {
                    // 阅读进度：当前可见第一个条目在整篇文档中的位置
                    val progress by remember(listState) {
                        derivedStateOf {
                            val info = listState.layoutInfo
                            val total = info.totalItemsCount
                            if (total <= 1) {
                                0f
                            } else {
                                val first = info.visibleItemsInfo.firstOrNull()
                                if (first == null) {
                                    0f
                                } else {
                                    val itemFraction = if (first.size > 0) {
                                        first.offset.toFloat() / first.size
                                    } else {
                                        0f
                                    }
                                    ((first.index + itemFraction) / (total - 1)).coerceIn(0f, 1f)
                                }
                            }
                        }
                    }
                    // 滑动时显示进度条，停止滑动 1.2 秒后自动隐藏
                    var progressVisible by remember { mutableStateOf(false) }
                    LaunchedEffect(listState.isScrollInProgress) {
                        if (listState.isScrollInProgress) {
                            progressVisible = true
                        } else {
                            delay(1200)
                            progressVisible = false
                        }
                    }

                    // 页面离开时保存阅读位置
                    DisposableEffect(viewModel, listState) {
                        onDispose {
                            viewModel.dispatch(
                                MdReaderIntent.SavePosition(
                                    index = listState.firstVisibleItemIndex,
                                    offset = listState.firstVisibleItemScrollOffset,
                                ),
                            )
                        }
                    }
                    MarkdownContent(
                        blocks = blocks,
                        modifier = Modifier
                            .fillMaxHeight()
                            .widthIn(max = dimens.maxContentWidth)
                            .align(Alignment.TopCenter),
                        contentPadding = PaddingValues(
                            start = dimens.screenPadding,
                            top = dimens.screenPadding,
                            // 右边距去掉，内容贴到右边缘
                            end = 0.dp,
                            bottom = dimens.screenPadding,
                        ),
                        listState = listState,
                        restoreIndex = s.scrollIndex,
                        restoreOffset = s.scrollOffset,
                        highlights = s.highlights.map { it.blockIndex }.toSet(),
                        onToggleHighlight = { index, preview ->
                            viewModel.dispatch(MdReaderIntent.ToggleHighlight(index, preview))
                        },
                    )

                    // 右侧阅读进度条：仅滑动时显示
                    if (progressVisible) {
                        ReadingProgressBar(
                            progress = progress,
                            modifier = Modifier.align(Alignment.CenterEnd),
                        )
                    }
                }

                is MdReaderState.Error -> ErrorView(
                    message = s.message,
                    onRetry = { viewModel.dispatch(MdReaderIntent.Retry) },
                )
            }
        }
    }

    val successState = state as? MdReaderState.Success
    if (showMarks && successState != null) {
        MarksDialog(
            highlights = successState.highlights,
            bookmarks = successState.bookmarks,
            onJumpTo = { index ->
                showMarks = false
                scope.launch { listState.scrollToItem(index) }
            },
            onRemoveHighlight = { index ->
                viewModel.dispatch(MdReaderIntent.ToggleHighlight(index, ""))
            },
            onRemoveBookmark = { index ->
                viewModel.dispatch(MdReaderIntent.RemoveBookmark(index))
            },
            onDismiss = { showMarks = false },
        )
    }
}

/** 标记/书签列表对话框：点击跳转，可删除 */
@Composable
private fun MarksDialog(
    highlights: List<ParagraphHighlight>,
    bookmarks: List<Bookmark>,
    onJumpTo: (Int) -> Unit,
    onRemoveHighlight: (Int) -> Unit,
    onRemoveBookmark: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("标记列表") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (highlights.isEmpty() && bookmarks.isEmpty()) {
                    Text(
                        text = "暂无标记。长按段落可添加高亮标记，点右上角书签按钮可添加书签。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (highlights.isNotEmpty()) {
                    Text(
                        text = "段落标记",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    highlights.forEach { highlight ->
                        MarkRow(
                            label = highlight.preview.ifBlank { "段落 ${highlight.blockIndex}" },
                            onJump = { onJumpTo(highlight.blockIndex) },
                            onRemove = { onRemoveHighlight(highlight.blockIndex) },
                        )
                    }
                }
                if (bookmarks.isNotEmpty()) {
                    Text(
                        text = "书签",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    bookmarks.forEach { bookmark ->
                        MarkRow(
                            label = bookmark.preview.ifBlank { "位置 ${bookmark.blockIndex}" },
                            onJump = { onJumpTo(bookmark.blockIndex) },
                            onRemove = { onRemoveBookmark(bookmark.blockIndex) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
    )
}

/** 标记列表中的一行：预览 + 跳转 + 删除 */
@Composable
private fun MarkRow(
    label: String,
    onJump: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onJump) {
            Text("跳转")
        }
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "删除",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 右侧阅读进度条：滑动时显示，停止滑动后隐藏 */
@Composable
private fun ReadingProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = CircleShape
    Box(
        modifier = modifier
            .padding(top = 32.dp, bottom = 32.dp, end = 2.dp)
            .width(4.dp)
            .fillMaxHeight()
            .clip(shape)
            .background(color = scheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(progress.coerceIn(0f, 1f))
                .align(Alignment.TopCenter)
                .background(color = scheme.primary),
        )
    }
}

@Composable
private fun ErrorView(
    message: String,
    onRetry: () -> Unit,
) {
    val dimens = LocalAppDimens.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(dimens.screenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = Modifier.height(dimens.itemSpacing))
        Button(onClick = onRetry) {
            Text("重试")
        }
    }
}
