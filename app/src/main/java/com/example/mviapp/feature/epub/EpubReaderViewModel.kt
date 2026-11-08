package com.example.mviapp.feature.epub

import android.content.Context
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.example.mviapp.core.epub.EpubReaderRepository
import com.example.mviapp.core.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * EPUB 阅读器 ViewModel
 *
 * 性能优化：
 * - 书籍解析在 IO 线程
 * - 章节内容懒加载
 * - 相邻章节不预加载（节省内存，翻章时再读）
 * - 状态更新只在必要时触发
 */
@HiltViewModel
class EpubReaderViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: EpubReaderRepository,
) : MviViewModel<EpubReaderIntent, EpubReaderState, EpubReaderEffect>(
    initialState = EpubReaderState()
) {
    /** 文件 URI（外部传入后设置） */
    private var fileUri: Uri? = null
    /** 书名（外部传入） */
    private var fileTitle: String = ""

    /** 设置文件信息并开始加载 */
    fun setFile(uri: Uri, title: String) {
        if (fileUri != null) return // 防止重复设置
        fileUri = uri
        fileTitle = title
        dispatch(EpubReaderIntent.LoadBook)
    }

    override suspend fun handleIntent(intent: EpubReaderIntent) {
        when (intent) {
            is EpubReaderIntent.LoadBook -> loadBook()
            is EpubReaderIntent.JumpToChapter -> jumpToChapter(intent.index)
            is EpubReaderIntent.PrevChapter -> prevChapter()
            is EpubReaderIntent.NextChapter -> nextChapter()
            is EpubReaderIntent.ToggleToc -> toggleToc()
            is EpubReaderIntent.CloseToc -> closeToc()
        }
    }

    private fun loadBook() {
        val uri = fileUri ?: return
        updateState { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val book = repository.openBook(uri)
            if (book == null) {
                updateState {
                    it.copy(
                        isLoading = false,
                        errorMessage = "无法解析 EPUB 文件"
                    )
                }
                emitEffect(EpubReaderEffect.ShowToast("文件解析失败"))
            } else {
                // 用传入的标题覆盖解析出的标题（优先用文件名）
                val finalTitle = fileTitle.ifBlank { book.title }
                val finalBook = book.copy(title = finalTitle)
                updateState { it.copy(isLoading = false, book = finalBook) }
                // 自动加载第一章
                loadChapter(0)
            }
        }
    }

    private fun jumpToChapter(index: Int) {
        val book = currentState.book ?: return
        if (index < 0 || index >= book.chapters.size) return
        updateState { it.copy(showToc = false) }
        loadChapter(index)
    }

    private fun prevChapter() {
        val current = currentState.currentChapterIndex
        if (current > 0) loadChapter(current - 1)
    }

    private fun nextChapter() {
        val book = currentState.book ?: return
        val current = currentState.currentChapterIndex
        if (current < book.chapters.size - 1) loadChapter(current + 1)
    }

    /** 加载指定章节内容 */
    private fun loadChapter(index: Int) {
        val uri = fileUri ?: return
        val book = currentState.book ?: return
        val chapter = book.chapters.getOrNull(index) ?: return

        updateState {
            it.copy(
                isChapterLoading = true,
                currentChapterIndex = index,
                currentContent = null,
            )
        }

        viewModelScope.launch {
            val content = repository.readChapter(uri, book, index)
            updateState {
                it.copy(
                    isChapterLoading = false,
                    currentContent = content,
                )
            }
            if (content == null) {
                emitEffect(EpubReaderEffect.ShowToast("章节加载失败"))
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
