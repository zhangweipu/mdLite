package com.example.mviapp.feature.home

import androidx.lifecycle.viewModelScope
import com.example.mviapp.core.history.ReadingRecord
import com.example.mviapp.core.history.ReadingHistoryRepository
import com.example.mviapp.core.md.MdFile
import com.example.mviapp.core.md.MdRepository
import com.example.mviapp.core.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 首页 ViewModel
 *
 * MVI 数据流:
 *   Intent → handleIntent → Repository → updateState → StateFlow → Compose 重组
 *                                 ↘ emitEffect → Flow → Compose 消费
 *
 * 单向数据流：UI 只发出 Intent，ViewModel 处理后只产出 State 和 Effect，
 * UI 永远不直接修改状态。
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: MdRepository,
    private val historyRepository: ReadingHistoryRepository,
) : MviViewModel<HomeIntent, HomeState, HomeEffect>(
    initialState = HomeState.Loading
) {

    init {
        // 进入页面自动加载数据
        dispatch(HomeIntent.LoadItems)
    }

    override suspend fun handleIntent(intent: HomeIntent) {
        when (intent) {
            is HomeIntent.LoadItems -> loadItems()
            is HomeIntent.Refresh -> refreshItems()
            is HomeIntent.RefreshSilent -> refreshSilently()
            is HomeIntent.ClickFile -> clickFile(intent.filePath, intent.title)
            is HomeIntent.DeleteRecent -> deleteRecent(intent.source)
            is HomeIntent.TogglePin -> togglePin(intent.source)
            is HomeIntent.RenameTitle -> renameTitle(intent.source, intent.currentTitle, intent.newName)
            is HomeIntent.ClearRecent -> clearRecent()
            is HomeIntent.Retry -> loadItems()
        }
    }

    // ==================== Intent 处理逻辑 ====================

    private fun loadItems() {
        updateState { HomeState.Loading }
        viewModelScope.launch {
            runCatching { loadData() }
                .onSuccess { (files, recentReads) ->
                    updateState { HomeState.Success(files = files, recentReads = recentReads) }
                }
                .onFailure { error ->
                    updateState { HomeState.Error(message = error.message ?: "加载失败") }
                }
        }
    }

    private fun refreshItems() {
        val current = currentState
        if (current is HomeState.Success) {
            updateState { current.copy(isRefreshing = true) }
        }
        viewModelScope.launch {
            runCatching { loadData() }
                .onSuccess { (files, recentReads) ->
                    updateState {
                        HomeState.Success(
                            files = files,
                            recentReads = recentReads,
                            isRefreshing = false,
                        )
                    }
                    emitEffect(HomeEffect.ShowToast("刷新成功"))
                }
                .onFailure { error ->
                    if (current is HomeState.Success) {
                        updateState { current.copy(isRefreshing = false) }
                    }
                    emitEffect(HomeEffect.ShowToast(error.message ?: "刷新失败"))
                }
        }
    }

    /** 静默刷新：从阅读页返回首页时重新读取历史记录，不闪 Loading、不弹提示 */
    private fun refreshSilently() {
        viewModelScope.launch {
            runCatching { loadData() }
                .onSuccess { (files, recentReads) ->
                    updateState {
                        if (it is HomeState.Success) {
                            it.copy(files = files, recentReads = recentReads)
                        } else {
                            HomeState.Success(files = files, recentReads = recentReads)
                        }
                    }
                }
                // 静默失败：保持当前状态，不打扰用户
                .onFailure { }
        }
    }

    private fun clickFile(filePath: String, title: String) {
        emitEffect(HomeEffect.NavigateToReader(filePath, title))
    }

    private suspend fun loadData(): Pair<List<MdFile>, List<ReadingRecord>> {
        val files = repository.getFiles()
        val recentReads = historyRepository.getRecent(MAX_RECENT)
        return files to recentReads
    }

    private suspend fun deleteRecent(source: String) {
        runCatching { historyRepository.remove(source) }
        updateState { state ->
            if (state is HomeState.Success) {
                state.copy(recentReads = state.recentReads.filterNot { it.source == source })
            } else {
                state
            }
        }
    }

    /** 切换置顶状态：置顶记录优先展示 */
    private suspend fun togglePin(source: String) {
        val current = currentState
        if (current !is HomeState.Success) return
        val record = current.recentReads.firstOrNull { it.source == source } ?: return
        val newPinned = !record.pinned
        runCatching { historyRepository.setPinned(source, newPinned) }
        updateState { state ->
            if (state is HomeState.Success) {
                val updated = state.recentReads.map { r ->
                    if (r.source == source) r.copy(pinned = newPinned) else r
                }
                state.copy(
                    recentReads = updated.sortedWith(
                        compareByDescending<ReadingRecord> { it.pinned }
                            .thenByDescending { it.readAt },
                    ),
                )
            } else {
                state
            }
        }
    }

    /** 重命名显示名称：只更新历史记录里的标题映射，磁盘文件名保持不变 */
    private fun renameTitle(source: String, currentTitle: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) {
            emitEffect(HomeEffect.ShowToast("名称不能为空"))
            return
        }
        if (!source.startsWith("content://")) {
            emitEffect(HomeEffect.ShowToast("内置文档不能重命名"))
            return
        }
        viewModelScope.launch {
            runCatching { historyRepository.rename(source, source, trimmed) }
                .onSuccess {
                    updateState { state ->
                        if (state is HomeState.Success) {
                            state.copy(
                                recentReads = state.recentReads.map { record ->
                                    if (record.source == source) record.copy(title = trimmed) else record
                                },
                            )
                        } else {
                            state
                        }
                    }
                    emitEffect(HomeEffect.ShowToast("重命名成功"))
                }
                .onFailure { error ->
                    emitEffect(HomeEffect.ShowToast(error.message ?: "重命名失败"))
                }
        }
    }

    private suspend fun clearRecent() {
        runCatching { historyRepository.clear() }
        updateState { state ->
            if (state is HomeState.Success) {
                state.copy(recentReads = emptyList())
            } else {
                state
            }
        }
    }

    private companion object {
        const val MAX_RECENT = 10
    }
}
