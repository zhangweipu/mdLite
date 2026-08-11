package com.example.mviapp.feature.download

import androidx.lifecycle.viewModelScope
import com.example.mviapp.core.download.DownloadRepository
import com.example.mviapp.core.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 下载管理 ViewModel
 *
 * 观察 DownloadRepository 的 Flow，实时更新下载列表。
 * 支持删除单条记录、清空全部。
 */
@HiltViewModel
class DownloadListViewModel @Inject constructor(
    private val repository: DownloadRepository,
) : MviViewModel<DownloadListIntent, DownloadListState, DownloadListEffect>(
    initialState = DownloadListState(isLoading = true)
) {

    init {
        // 订阅下载列表变化，实时更新 UI
        viewModelScope.launch {
            repository.downloads.collectLatest { items ->
                updateState { it.copy(items = items, isLoading = false) }
            }
        }
        dispatch(DownloadListIntent.Load)
    }

    override suspend fun handleIntent(intent: DownloadListIntent) {
        when (intent) {
            is DownloadListIntent.Load -> loadList()
            is DownloadListIntent.Remove -> remove(intent.id)
            is DownloadListIntent.ClearAll -> clearAll()
        }
    }

    private suspend fun loadList() {
        updateState { it.copy(isLoading = true) }
        val items = repository.getAll()
        updateState { it.copy(items = items, isLoading = false) }
    }

    private fun remove(id: String) {
        viewModelScope.launch {
            repository.remove(id)
            emitEffect(DownloadListEffect.ShowToast("已删除"))
        }
    }

    private fun clearAll() {
        viewModelScope.launch {
            repository.clear()
            emitEffect(DownloadListEffect.ShowToast("已清空下载记录"))
        }
    }
}
