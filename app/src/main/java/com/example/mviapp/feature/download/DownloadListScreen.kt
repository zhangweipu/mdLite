package com.example.mviapp.feature.download

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mviapp.core.download.DownloadItem
import com.example.mviapp.core.download.DownloadStatus
import com.example.mviapp.core.ui.AppTopBar
import com.example.mviapp.core.ui.rememberAppBarScrollController
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 下载管理列表页
 *
 * 展示所有下载任务（进行中、已完成、失败），
 * 支持删除单条记录和清空全部。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadListScreen(
    onOpenDrawer: () -> Unit,
    onOpenFile: (path: String, title: String) -> Unit,
    viewModel: DownloadListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val topBarController = rememberAppBarScrollController()
    var showClearConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.uiEffect.collect { effect ->
            if (effect is DownloadListEffect.ShowToast) {
                snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(topBarController.nestedScrollConnection),
        topBar = {
            AppTopBar(
                title = "下载管理",
                onOpenDrawer = onOpenDrawer,
                controller = topBarController,
                actions = {
                    if (state.items.isNotEmpty()) {
                        IconButton(onClick = { showClearConfirm = true }) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = "清空全部")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.isLoading && state.items.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

                state.isEmpty -> EmptyView()

                else -> DownloadListContent(
                    items = state.items,
                    onRemove = { id -> viewModel.dispatch(DownloadListIntent.Remove(id)) },
                    onOpen = { item -> onOpenFile(item.localPath, item.fileName) },
                )
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清空下载记录") },
            text = { Text("确定要清空所有下载记录吗？此操作不会删除本地文件。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.dispatch(DownloadListIntent.ClearAll)
                    showClearConfirm = false
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun EmptyView() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Download,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "暂无下载记录",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "在浏览器中下载的文件会显示在这里",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DownloadListContent(
    items: List<DownloadItem>,
    onRemove: (String) -> Unit,
    onOpen: (DownloadItem) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            items = items,
            key = { it.id },
        ) { item ->
            DownloadItemRow(
                item = item,
                onRemove = { onRemove(item.id) },
                onOpen = { onOpen(item) },
            )
        }
    }
}

@Composable
private fun DownloadItemRow(
    item: DownloadItem,
    onRemove: () -> Unit,
    onOpen: () -> Unit,
) {
    val canOpen = item.status == DownloadStatus.Completed && item.localPath.isNotBlank()
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.clickable(enabled = canOpen, onClick = onOpen),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 状态图标
                Icon(
                    imageVector = statusIcon(item.status),
                    contentDescription = null,
                    tint = statusColor(item.status),
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                // 文件名 + 状态
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.fileName,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = buildString {
                            // 下载完成且文件可用时，状态直接呈现为「打开」
                            append(
                                if (item.status == DownloadStatus.Completed && canOpen) {
                                    "打开"
                                } else {
                                    statusText(item.status)
                                }
                            )
                            append(" · ")
                            append(formatDate(item.createdAt))
                            if (item.status == DownloadStatus.Completed || item.status == DownloadStatus.Downloading) {
                                append(" · ")
                                append(item.totalSizeText)
                            }
                            if (item.status == DownloadStatus.Failed && item.errorMessage != null) {
                                append(" · ")
                                append(item.errorMessage)
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // 下载完成后提供显式的「打开」入口，不必靠猜整行可点
                if (canOpen) {
                    FilledTonalButton(
                        onClick = onOpen,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier.height(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "打开",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
                // 删除按钮
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // 进度条（仅下载中显示）
            if (item.status == DownloadStatus.Downloading) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { item.progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${item.downloadedSizeText} / ${item.totalSizeText}（${item.progress}%）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun statusIcon(status: DownloadStatus) = when (status) {
    DownloadStatus.Pending -> Icons.Filled.Schedule
    DownloadStatus.Downloading -> Icons.Filled.Download
    DownloadStatus.Completed -> Icons.Filled.DownloadDone
    DownloadStatus.Failed -> Icons.Filled.Error
}

@Composable
private fun statusColor(status: DownloadStatus) = when (status) {
    DownloadStatus.Pending -> MaterialTheme.colorScheme.onSurfaceVariant
    DownloadStatus.Downloading -> MaterialTheme.colorScheme.primary
    DownloadStatus.Completed -> MaterialTheme.colorScheme.primary
    DownloadStatus.Failed -> MaterialTheme.colorScheme.error
}

private fun statusText(status: DownloadStatus) = when (status) {
    DownloadStatus.Pending -> "等待中"
    DownloadStatus.Downloading -> "下载中"
    DownloadStatus.Completed -> "已完成"
    DownloadStatus.Failed -> "失败"
}

private fun formatDate(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < 60_000 -> "刚刚"
        diff < 3_600_000 -> "${diff / 60_000} 分钟前"
        diff < 86_400_000 -> "${diff / 3_600_000} 小时前"
        diff < 86_400_000 * 7 -> "${diff / 86_400_000} 天前"
        else -> SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestamp))
    }
}
