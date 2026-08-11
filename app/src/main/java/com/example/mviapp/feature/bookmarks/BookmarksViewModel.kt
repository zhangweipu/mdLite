package com.example.mviapp.feature.bookmarks

import androidx.lifecycle.viewModelScope
import com.example.mviapp.core.bookmark.BookmarkRepository
import com.example.mviapp.core.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 收藏夹 ViewModel
 *
 * 观察 BookmarkRepository 的 Flow，实时更新列表。
 * 支持打开、删除、清空。
 */
@HiltViewModel
class BookmarksViewModel @Inject constructor(
    private val repository: BookmarkRepository,
) : MviViewModel<BookmarksIntent, BookmarksState, BookmarksEffect>(
    initialState = BookmarksState(isLoading = true)
) {

    init {
        // 订阅收藏列表变化，实时更新 UI
        viewModelScope.launch {
            repository.bookmarks.collectLatest { items ->
                updateState { it.copy(items = items, isLoading = false) }
            }
        }
        dispatch(BookmarksIntent.Load)
    }

    override suspend fun handleIntent(intent: BookmarksIntent) {
        when (intent) {
            is BookmarksIntent.Load -> loadList()
            is BookmarksIntent.Remove -> remove(intent.url)
            is BookmarksIntent.ClearAll -> clearAll()
        }
    }

    private suspend fun loadList() {
        updateState { it.copy(isLoading = true) }
        val items = repository.getAll()
        updateState { it.copy(items = items, isLoading = false) }
    }

    private fun remove(url: String) {
        viewModelScope.launch {
            repository.remove(url)
            emitEffect(BookmarksEffect.ShowToast("已删除"))
        }
    }

    private fun clearAll() {
        viewModelScope.launch {
            // 逐条删除（保持与 Flow 同步）
            val current = repository.getAll()
            current.forEach { repository.remove(it.url) }
            emitEffect(BookmarksEffect.ShowToast("已清空收藏夹"))
        }
    }

    /** 打开某个收藏（由外部处理导航） */
    fun openBookmark(url: String) {
        emitEffect(BookmarksEffect.OpenUrl(url))
    }
}
