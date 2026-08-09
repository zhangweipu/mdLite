package com.example.mviapp.feature.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mviapp.R
import com.example.mviapp.core.history.ReadingRecord
import com.example.mviapp.core.md.MdFile
import com.example.mviapp.core.ui.config.AppDimens
import com.example.mviapp.core.ui.config.LocalAppDimens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 首页 Composable
 *
 * MVI 中的 View 层：
 * - 通过 [collectAsStateWithLifecycle] 观察 State，自动重组
 * - 通过 LaunchedEffect 观察 Effect，消费一次性事件
 * - 用户操作通过 [onIntent] 派发 Intent，不直接修改状态
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onOpenDrawer: () -> Unit,
    onNavigateToReader: (String, String) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    // 读取统一配置，界面尺寸随屏幕分辨率自适应
    val dimens = LocalAppDimens.current
    // 从阅读页返回（或应用回到前台）时自动刷新，保证“最近阅读”记录最新
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.dispatch(HomeIntent.RefreshSilent)
    }
    // 长按重命名目标（source 到 当前文件名），仅本地文件
    var renameTarget by remember { mutableStateOf<Pair<String, String>?>(null) }

    // 消费一次性 Effect
    LaunchedEffect(Unit) {
        viewModel.uiEffect.collect { effect ->
            when (effect) {
                is HomeEffect.ShowToast -> snackbarHostState.showSnackbar(effect.message)
                is HomeEffect.NavigateToReader -> onNavigateToReader(effect.filePath, effect.title)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "打开菜单",
                        )
                    }
                },
                title = { Text(stringResource(R.string.home_title)) },
                actions = {
                    IconButton(onClick = { viewModel.dispatch(HomeIntent.Refresh) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
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
            contentAlignment = Alignment.TopCenter,
        ) {
            when (val s = state) {
                is HomeState.Loading -> LoadingView()
                is HomeState.Success -> SuccessView(
                    state = s,
                    dimens = dimens,
                    onFileClick = { filePath, title ->
                        viewModel.dispatch(HomeIntent.ClickFile(filePath, title))
                    },
                    onRecentClick = { record ->
                        viewModel.dispatch(HomeIntent.ClickFile(record.source, record.title))
                    },
                    onDeleteRecent = { source ->
                        viewModel.dispatch(HomeIntent.DeleteRecent(source))
                    },
                    onTogglePin = { source ->
                        viewModel.dispatch(HomeIntent.TogglePin(source))
                    },
                    onRenameFile = { source, title ->
                        if (source.startsWith("content://")) {
                            renameTarget = source to title
                        } else {
                            // 内置文档不可重命名，直接提示
                            viewModel.dispatch(HomeIntent.RenameTitle(source, title, title))
                        }
                    },
                    onClearRecent = {
                        viewModel.dispatch(HomeIntent.ClearRecent)
                    },
                )
                is HomeState.Error -> ErrorView(
                    message = s.message,
                    dimens = dimens,
                    onRetry = { viewModel.dispatch(HomeIntent.Retry) },
                )
            }

            renameTarget?.let { (source, currentName) ->
                RenameFileDialog(
                    currentName = currentName,
                    onConfirm = { newName ->
                        viewModel.dispatch(HomeIntent.RenameTitle(source, currentName, newName))
                        renameTarget = null
                    },
                    onDismiss = { renameTarget = null },
                )
            }
        }
    }
}

@Composable
private fun LoadingView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun SuccessView(
    state: HomeState.Success,
    dimens: AppDimens,
    onFileClick: (String, String) -> Unit,
    onRecentClick: (ReadingRecord) -> Unit,
    onDeleteRecent: (String) -> Unit,
    onTogglePin: (String) -> Unit,
    onClearRecent: () -> Unit,
    onRenameFile: (String, String) -> Unit,
) {
    LazyColumn(
        // 大屏时限制内容宽度并居中，避免一行排得过长影响阅读
        modifier = Modifier
            .fillMaxHeight()
            .widthIn(max = dimens.maxContentWidth),
        contentPadding = PaddingValues(dimens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(dimens.itemSpacing),
    ) {
        // ====== 最近阅读 ======
        if (state.recentReads.isNotEmpty()) {
            item(key = "recent_header") {
                SectionHeader(
                    title = "最近阅读",
                    onClear = onClearRecent,
                )
            }
            items(
                items = state.recentReads,
                key = { "recent_${it.source}" },
            ) { record ->
                RecentReadRow(
                    record = record,
                    dimens = dimens,
                    onClick = { onRecentClick(record) },
                    onLongClick = { onRenameFile(record.source, record.title) },
                    onTogglePin = { onTogglePin(record.source) },
                    onDelete = { onDeleteRecent(record.source) },
                )
            }
        }

        // ====== 内置文档 ======
        item(key = "docs_header") {
            SectionHeader(
                title = "内置文档",
            )
        }
        items(
            items = state.files,
            key = { it.fileName },
        ) { file ->
            MdFileCard(
                file = file,
                dimens = dimens,
                onClick = { onFileClick(file.filePath, file.title) },
                onLongClick = { onRenameFile(file.filePath, file.title) },
            )
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    onClear: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        if (onClear != null) {
            TextButton(onClick = onClear) {
                Text("清空")
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentReadRow(
    record: ReadingRecord,
    dimens: AppDimens,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = dimens.cardElevation),
        shape = RoundedCornerShape(dimens.cardCornerRadius),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimens.cardPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(dimens.contentGap + 4.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${formatRelativeTime(record.readAt)} · ${sourceLabel(record.source)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onTogglePin) {
                Icon(
                    imageVector = if (record.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                    contentDescription = if (record.pinned) "取消置顶" else "置顶",
                    tint = if (record.pinned) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "删除阅读记录",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 记录来源标签 */
private fun sourceLabel(source: String): String =
    if (source.startsWith("content://")) "本地文件" else "内置文档"

/** 相对时间：刚刚 / x 分钟前 / x 小时前 / 日期 */
private fun formatRelativeTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000 -> "刚刚"
        diff < 3_600_000 -> "${diff / 60_000} 分钟前"
        diff < 86_400_000 -> "${diff / 3_600_000} 小时前"
        else -> SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MdFileCard(
    file: MdFile,
    dimens: AppDimens,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = dimens.cardElevation),
        shape = RoundedCornerShape(dimens.cardCornerRadius),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimens.cardPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 文档图标
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.MenuBook,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(dimens.contentGap + 4.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(dimens.contentGap))
                Text(
                    text = file.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ErrorView(
    message: String,
    dimens: AppDimens,
    onRetry: () -> Unit,
) {
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

/** 重命名显示名称对话框 */
@Composable
private fun RenameFileDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(currentName) { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名显示名称") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("显示名称") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onConfirm(name) },
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}
