package com.example.mviapp.feature.github

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.example.mviapp.core.common.isSupportedDocFile
import com.example.mviapp.core.github.GithubRepository
import com.example.mviapp.core.history.ReadingHistoryRepository
import com.example.mviapp.core.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * GitHub 文件页 ViewModel
 *
 * 用户输入 GitHub 仓库地址 → 解析 owner/repo → 浏览目录 → 下载文件。
 * 下载完成后如果是支持的文档格式，会加入"最近阅读"。
 */
@HiltViewModel
class GithubViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: GithubRepository,
    private val historyRepository: ReadingHistoryRepository,
) : MviViewModel<GithubIntent, GithubState, GithubEffect>(
    initialState = GithubState.Idle
) {

    override suspend fun handleIntent(intent: GithubIntent) {
        when (intent) {
            is GithubIntent.Connect -> connect(intent.repoUrl)
            is GithubIntent.Reset -> updateState { GithubState.Idle }
            is GithubIntent.Refresh -> refresh()
            is GithubIntent.EnterDirectory -> enterDirectory(intent.path)
            is GithubIntent.NavigateUp -> navigateUp()
            is GithubIntent.DownloadFile -> downloadFile(intent.name, intent.downloadUrl, intent.destinationUri)
        }
    }

    private fun connect(repoUrl: String) {
        val parsed = repository.parseRepoUrl(repoUrl)
        if (parsed == null) {
            emitEffect(GithubEffect.ShowToast("请输入有效的 GitHub 仓库地址"))
            return
        }
        val (owner, repo) = parsed
        updateState { GithubState.Loading(owner = owner, repo = repo) }
        loadDirectory(owner, repo, "")
    }

    private fun refresh() {
        val s = currentState
        when (s) {
            is GithubState.Connected -> loadDirectory(s.owner, s.repo, s.currentPath)
            else -> Unit
        }
    }

    private fun enterDirectory(path: String) {
        val s = currentState as? GithubState.Connected ?: return
        updateState { GithubState.Loading(owner = s.owner, repo = s.repo, currentPath = path) }
        loadDirectory(s.owner, s.repo, path)
    }

    private fun navigateUp() {
        val s = currentState as? GithubState.Connected ?: return
        if (s.currentPath.isEmpty()) return
        val parentPath = s.currentPath.substringBeforeLast('/')
        updateState { GithubState.Loading(owner = s.owner, repo = s.repo, currentPath = parentPath) }
        loadDirectory(s.owner, s.repo, parentPath)
    }

    private fun loadDirectory(owner: String, repo: String, path: String) {
        viewModelScope.launch {
            runCatching { repository.listFiles(owner, repo, path) }
                .onSuccess { files ->
                    // 目录在前，文件在后，均按名称排序
                    val sorted = files.sortedWith(
                        compareByDescending<com.example.mviapp.core.github.GithubFile> { it.isDir }
                            .thenBy { it.name.lowercase() }
                    )
                    updateState {
                        GithubState.Connected(
                            owner = owner,
                            repo = repo,
                            currentPath = path,
                            files = sorted,
                        )
                    }
                }
                .onFailure { error ->
                    val message = error.message ?: "加载失败"
                    if (currentState is GithubState.Loading) {
                        updateState { GithubState.Error(message) }
                    } else {
                        emitEffect(GithubEffect.ShowToast(message))
                    }
                }
        }
    }

    private fun downloadFile(name: String, downloadUrl: String, destinationUri: Uri) {
        val s = currentState as? GithubState.Connected ?: return
        if (s.isDownloading) {
            emitEffect(GithubEffect.ShowToast("请等待当前下载完成"))
            return
        }
        viewModelScope.launch {
            updateState { state ->
                if (state is GithubState.Connected) {
                    state.copy(downloadingName = name, progress = 0)
                } else state
            }
            repository.downloadFile(downloadUrl, destinationUri) { sent, total ->
                val percent = if (total > 0) (sent * 100 / total).toInt() else 0
                updateState { state ->
                    if (state is GithubState.Connected) state.copy(progress = percent) else state
                }
            }
                .onSuccess {
                    // 持久化保存位置的读写权限，之后可在 App 内直接打开
                    runCatching {
                        context.contentResolver.takePersistableUriPermission(
                            destinationUri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                        )
                    }
                    if (name.isSupportedDocFile()) {
                        historyRepository.add(title = name, source = destinationUri.toString())
                    }
                    updateState { state ->
                        if (state is GithubState.Connected) {
                            state.copy(downloadingName = null, progress = 0)
                        } else state
                    }
                    emitEffect(GithubEffect.ShowToast("下载完成：$name"))
                }
                .onFailure { error ->
                    updateState { state ->
                        if (state is GithubState.Connected) {
                            state.copy(downloadingName = null, progress = 0)
                        } else state
                    }
                    emitEffect(GithubEffect.ShowToast("下载失败：${error.message ?: "未知错误"}"))
                }
        }
    }
}
