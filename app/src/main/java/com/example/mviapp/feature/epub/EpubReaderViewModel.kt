package com.example.mviapp.feature.epub

import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.example.mviapp.core.epub.EpubOpenResult
import com.example.mviapp.core.epub.EpubReaderRepository
import com.example.mviapp.core.history.ReadingHistoryRepository
import com.example.mviapp.core.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * EPUB 阅读器 ViewModel。
 *
 * 加载分两条互不阻塞的路径：
 * 1. 解析元数据 → 立刻显示目录与第一章文字；
 * 2. 后台解压图片/CSS → 完成后刷新当前章，让 WebView 拿到 file:// baseUrl。
 *
 * 所有 IO 异常都转成可读的错误状态（旧实现会直接让协程崩溃，界面永远转圈）。
 */
@HiltViewModel
class EpubReaderViewModel @Inject constructor(
    private val repository: EpubReaderRepository,
    private val historyRepository: ReadingHistoryRepository,
) : MviViewModel<EpubReaderIntent, EpubReaderState, EpubReaderEffect>(
    initialState = EpubReaderState()
) {
    private var fileUri: Uri? = null
    private var fileTitle: String = ""
    private var loadStarted = false
    private var loadJob: Job? = null
    private var chapterJob: Job? = null

    /** 设置文件信息并开始加载（同一 URI 不重复触发，换书时重新加载） */
    fun setFile(uri: Uri, title: String) {
        if (fileUri == uri && loadStarted) return
        fileUri = uri
        fileTitle = title
        loadStarted = true
        dispatch(EpubReaderIntent.LoadBook)
    }

    override suspend fun handleIntent(intent: EpubReaderIntent) {
        when (intent) {
            is EpubReaderIntent.LoadBook,
            is EpubReaderIntent.Retry -> loadBook()
            is EpubReaderIntent.RetryChapter -> loadChapter(currentState.currentChapterIndex)
            is EpubReaderIntent.JumpToChapter -> jumpToChapter(intent.index)
            is EpubReaderIntent.PrevChapter -> prevChapter()
            is EpubReaderIntent.NextChapter -> nextChapter()
            is EpubReaderIntent.ToggleToc -> toggleToc()
            is EpubReaderIntent.CloseToc -> closeToc()
        }
    }

    private fun loadBook() {
        val uri = fileUri ?: return
        loadJob?.cancel()
        chapterJob?.cancel()
        updateState { EpubReaderState(isLoading = true) }

        loadJob = viewModelScope.launch {
            val result = runCatching { repository.openBook(uri) }
                .getOrElse { EpubOpenResult(null, "读取失败：${it.message ?: "未知错误"}") }

            val book = result.book
            if (book == null) {
                updateState {
                    it.copy(
                        isLoading = false,
                        errorMessage = result.error ?: "无法解析 EPUB 文件",
                    )
                }
                return@launch
            }

            val finalBook = book.copy(title = fileTitle.ifBlank { book.title })
            updateState {
                it.copy(isLoading = false, book = finalBook, errorMessage = null)
            }
            runCatching {
                historyRepository.add(title = finalBook.title, source = uri.toString())
            }

            // 后台补齐图片/CSS，完成后刷新当前章（不影响正文首屏）
            launch {
                repository.prepareResources(uri)
                if (currentState.book != null) {
                    loadChapter(currentState.currentChapterIndex)
                }
            }
            loadChapter(0)
        }
    }

    private fun jumpToChapter(index: Int) {
        val size = currentState.book?.chapters?.size ?: return
        if (index !in 0 until size) return
        updateState { it.copy(showToc = false) }
        loadChapter(index)
    }

    private fun prevChapter() {
        val current = currentState.currentChapterIndex
        if (current > 0) loadChapter(current - 1)
    }

    private fun nextChapter() {
        val size = currentState.book?.chapters?.size ?: return
        if (currentState.currentChapterIndex < size - 1) {
            loadChapter(currentState.currentChapterIndex + 1)
        }
    }

    /** 加载章节；翻章时保留上一章画面直到新内容到达，避免闪白 */
    private fun loadChapter(index: Int) {
        val uri = fileUri ?: return
        val book = currentState.book ?: return
        if (book.chapters.getOrNull(index) == null) return

        chapterJob?.cancel()
        updateState {
            it.copy(
                isChapterLoading = true,
                currentChapterIndex = index,
                chapterError = null,
            )
        }

        chapterJob = viewModelScope.launch {
            val page = runCatching { repository.readChapter(uri, book, index) }
                .getOrNull()
            if (!isActive) return@launch
            updateState {
                it.copy(
                    isChapterLoading = false,
                    page = page ?: it.page,
                    chapterError = if (page == null) {
                        "章节内容读取失败，文件可能已损坏或路径不匹配"
                    } else null,
                )
            }
        }
    }

    private fun toggleToc() {
        updateState { it.copy(showToc = !it.showToc) }
    }

    private fun closeToc() {
        updateState { it.copy(showToc = false) }
    }
}
