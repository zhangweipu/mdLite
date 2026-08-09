package com.example.mviapp.feature.reader

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.mviapp.core.history.ReadingHistoryRepository
import com.example.mviapp.core.marks.Bookmark
import com.example.mviapp.core.marks.ParagraphHighlight
import com.example.mviapp.core.marks.ReadingMarkRepository
import com.example.mviapp.core.md.MdRepository
import com.example.mviapp.core.mvi.MviViewModel
import com.example.mviapp.core.navigation.Screen
import com.example.mviapp.core.reader.ReadingPositionRepository
import com.example.mviapp.core.reader.SavedScrollPosition
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Markdown 阅读页 ViewModel
 *
 * 通过 [SavedStateHandle] 从导航参数中读取文档路径，
 * 调用 [MdRepository] 读取 Markdown 原文并暴露给 UI。
 */
@HiltViewModel
class MdReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: MdRepository,
    private val historyRepository: ReadingHistoryRepository,
    private val positionRepository: ReadingPositionRepository,
    private val markRepository: ReadingMarkRepository,
) : MviViewModel<MdReaderIntent, MdReaderState, MdReaderEffect>(
    initialState = MdReaderState.Loading
) {

    private val filePath: String =
        savedStateHandle.get<String>(Screen.Reader.ARG_FILE_PATH).orEmpty()

    private val fileTitle: String =
        savedStateHandle.get<String>(Screen.Reader.ARG_TITLE).orEmpty()

    init {
        dispatch(MdReaderIntent.LoadContent)
    }

    override suspend fun handleIntent(intent: MdReaderIntent) {
        when (intent) {
            is MdReaderIntent.LoadContent -> loadContent()
            is MdReaderIntent.Retry -> loadContent()
            is MdReaderIntent.SavePosition -> savePosition(intent.index, intent.offset)
            is MdReaderIntent.TogglePin -> togglePin()
            is MdReaderIntent.ToggleHighlight -> toggleHighlight(intent.blockIndex, intent.preview)
            is MdReaderIntent.AddBookmark -> addBookmark(intent.blockIndex, intent.preview)
            is MdReaderIntent.RemoveBookmark -> removeBookmark(intent.blockIndex)
        }
    }

    private fun loadContent() {
        updateState { MdReaderState.Loading }
        viewModelScope.launch {
            runCatching {
                val content = if (filePath.startsWith("content://")) {
                    repository.readUri(Uri.parse(filePath))
                } else {
                    repository.readFile(filePath)
                }
                // 阅读成功后写入阅读记录（首页展示）
                historyRepository.add(
                    title = fileTitle.ifBlank { fallbackTitle() },
                    source = filePath,
                )
                val savedPosition = positionRepository.get(filePath)
                val pinned = historyRepository.isPinned(filePath)
                val highlights = markRepository.getHighlights(filePath)
                val bookmarks = markRepository.getBookmarks(filePath)
                LoadResult(content, savedPosition, pinned, highlights, bookmarks)
            }
                .onSuccess { result ->
                    updateState {
                        MdReaderState.Success(
                            content = result.content,
                            scrollIndex = result.savedPosition?.index ?: 0,
                            scrollOffset = result.savedPosition?.offset ?: 0,
                            pinned = result.pinned,
                            highlights = result.highlights,
                            bookmarks = result.bookmarks,
                        )
                    }
                }
                .onFailure { error ->
                    updateState { MdReaderState.Error(message = error.message ?: "读取失败") }
                }
        }
    }

    private suspend fun savePosition(index: Int, offset: Int) {
        if (filePath.isBlank()) return
        runCatching { positionRepository.save(filePath, index, offset) }
    }

    /** 切换置顶状态（与首页历史记录同步） */
    private suspend fun togglePin() {
        if (filePath.isBlank()) return
        val current = currentState as? MdReaderState.Success ?: return
        val newPinned = !current.pinned
        runCatching { historyRepository.setPinned(filePath, newPinned) }
        updateState { state ->
            if (state is MdReaderState.Success) state.copy(pinned = newPinned) else state
        }
    }

    /** 切换段落标记（高亮） */
    private suspend fun toggleHighlight(blockIndex: Int, preview: String) {
        if (filePath.isBlank()) return
        val highlights = markRepository.toggleHighlight(filePath, blockIndex, preview)
        updateState { state ->
            if (state is MdReaderState.Success) state.copy(highlights = highlights) else state
        }
    }

    /** 当前位置添加书签 */
    private suspend fun addBookmark(blockIndex: Int, preview: String) {
        if (filePath.isBlank()) return
        val bookmarks = markRepository.addBookmark(filePath, blockIndex, preview)
        updateState { state ->
            if (state is MdReaderState.Success) state.copy(bookmarks = bookmarks) else state
        }
    }

    /** 删除书签 */
    private suspend fun removeBookmark(blockIndex: Int) {
        if (filePath.isBlank()) return
        val bookmarks = markRepository.removeBookmark(filePath, blockIndex)
        updateState { state ->
            if (state is MdReaderState.Success) state.copy(bookmarks = bookmarks) else state
        }
    }

    /** 未显式传标题时，从路径推导 */
    private fun fallbackTitle(): String =
        Uri.decode(filePath).substringAfterLast('/').removeSuffix(".md")

    /** 文档加载结果（内容 + 阅读位置 + 置顶 + 标记） */
    private data class LoadResult(
        val content: String,
        val savedPosition: SavedScrollPosition?,
        val pinned: Boolean,
        val highlights: List<ParagraphHighlight>,
        val bookmarks: List<Bookmark>,
    )
}
