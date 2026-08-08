package com.example.mviapp.feature.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mviapp.core.md.MarkdownContent
import com.example.mviapp.core.md.MarkdownParser
import com.example.mviapp.core.ui.config.LocalAppDimens
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
                    val listState = rememberLazyListState()
                    val blocks = remember(s.content) { MarkdownParser.parse(s.content) }
                    val chapters = remember(blocks) { MarkdownParser.chapterBlockIndexes(blocks) }
                    val showChapterDots = chapters.size >= 2
                    // 当前章节：滚动位置对应的最近一个已开始章节
                    val currentChapter by remember(chapters, listState) {
                        derivedStateOf {
                            val index = chapters.indexOfLast { it <= listState.firstVisibleItemIndex }
                            if (index >= 0) index else 0
                        }
                    }
                    val scope = rememberCoroutineScope()

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
                            // 右边距只保留进度点的宽度；无进度点时贴到右边缘
                            end = if (showChapterDots) CHAPTER_DOTS_AREA_WIDTH else 0.dp,
                            bottom = dimens.screenPadding,
                        ),
                        listState = listState,
                        restoreIndex = s.scrollIndex,
                        restoreOffset = s.scrollOffset,
                    )

                    // 右侧章节进度点：每章一个点，当前章节加粗高亮，点击可跳转
                    if (showChapterDots) {
                        ChapterProgressDots(
                            chapterCount = chapters.size,
                            currentChapter = currentChapter,
                            onSelect = { index ->
                                scope.launch { listState.animateScrollToItem(chapters[index]) }
                            },
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
}

/** 章节进度点占用的右侧宽度 */
private val CHAPTER_DOTS_AREA_WIDTH = 36.dp

/** 右侧章节进度点 */
@Composable
private fun ChapterProgressDots(
    chapterCount: Int,
    currentChapter: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        repeat(chapterCount) { index ->
            val active = index == currentChapter
            Text(
                text = "•",
                color = if (active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                fontSize = if (active) 22.sp else 12.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onSelect(index) }
                    .padding(6.dp),
            )
        }
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
