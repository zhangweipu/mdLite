package com.example.mviapp.feature.epub

import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.example.mviapp.core.epub.EpubOpenResult
import com.example.mviapp.core.epub.EpubReaderRepository
import com.example.mviapp.core.epub.resolveEpubResumePoint
import com.example.mviapp.core.history.ReadingHistoryRepository
import com.example.mviapp.core.mvi.MviViewModel
import com.example.mviapp.core.reader.ReadingPositionRepository
import com.example.mviapp.di.ApplicationScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * EPUB 阅读器 ViewModel。
 *
 * 加载分两条互不阻塞的路径：
 * 1. 解析元数据 → 立刻显示目录与上次读到的那一章；
 * 2. 后台解压图片/CSS → 完成后刷新当前章，让 WebView 拿到 file:// baseUrl。
 *
 * 阅读位置复用 [ReadingPositionRepository]：index 存章节索引，offset 存章内滚动 Y。
 * 打开书时恢复，滚动停止后 800ms / 翻章 / 页面离开时保存。
 *
 * 所有 IO 异常都转成可读的错误状态（旧实现会直接让协程崩溃，界面永远转圈）。
 */
@HiltViewModel
class EpubReaderViewModel @Inject constructor(
    private val repository: EpubReaderRepository,
    private val historyRepository: ReadingHistoryRepository,
    private val positionRepository: ReadingPositionRepository,
    @ApplicationScope private val appScope: CoroutineScope,
) : MviViewModel<EpubReaderIntent, EpubReaderState, EpubReaderEffect>(
    initialState = EpubReaderState()
) {
    private var fileUri: Uri? = null
    private var fileTitle: String = ""
    private var loadStarted = false
    private var loadJob: Job? = null
    private var chapterJob: Job? = null

    /** 上次写盘的章节与 Y，用于去重，避免滚动时节流任务反复写 SharedPreferences */
    private var lastSavedChapter = -1
    private var lastSavedScrollY = -1

    /** UI 每次滚动同步上报的最新位置，供 [onCleared] 兜底写盘 */
    private var latestChapter = -1
    private var latestScrollY = 0

    /** 设置文件信息并开始加载（同一 URI 不重复触发，换书时重新加载） */
    fun setFile(uri: Uri, title: String) {
        if (fileUri == uri && loadStarted) return
        fileUri = uri
        fileTitle = title
        loadStarted = true
        dispatch(EpubReaderIntent.LoadBook)
    }

    /** 滚动回调同步记录最新位置（不走 Intent 流，避免 ViewModel 销毁时丢事件） */
    fun onScroll(chapterIndex: Int, scrollY: Int) {
        latestChapter = chapterIndex
        latestScrollY = scrollY.coerceAtLeast(0)
    }

    override suspend fun handleIntent(intent: EpubReaderIntent) {
        when (intent) {
            is EpubReaderIntent.LoadBook,
            is EpubReaderIntent.Retry -> loadBook()
            is EpubReaderIntent.RetryChapter -> loadChapter(
                currentState.currentChapterIndex,
                persist = false,
            )
            is EpubReaderIntent.JumpToChapter -> jumpToChapter(intent.index)
            is EpubReaderIntent.PrevChapter -> prevChapter()
            is EpubReaderIntent.NextChapter -> nextChapter()
            is EpubReaderIntent.ToggleToc -> toggleToc()
            is EpubReaderIntent.CloseToc -> closeToc()
            is EpubReaderIntent.SavePosition -> savePosition(intent.chapterIndex, intent.scrollY)
            is EpubReaderIntent.RestoreHandled -> updateState { it.copy(restoreScrollY = 0) }
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
            // 章节数可能变化（同名文件被替换），越界记录一律回到第 1 章顶部
            val saved = runCatching { positionRepository.get(uri.toString()) }.getOrNull()
            val resume = resolveEpubResumePoint(
                chapterCount = finalBook.chapters.size,
                savedChapterIndex = saved?.index,
                savedScrollY = saved?.offset,
            )
            lastSavedChapter = resume.chapterIndex
            lastSavedScrollY = resume.scrollY

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
                    // 若首章还没渲染完、恢复位置尚未被 UI 消费，原样带过去；
                    // 已消费则由 WebView 同章重灌的滚动保持逻辑接管。
                    loadChapter(
                        currentState.currentChapterIndex,
                        persist = false,
                        restoreY = currentState.restoreScrollY,
                    )
                }
            }
            loadChapter(
                index = resume.chapterIndex,
                persist = false,
                restoreY = resume.scrollY,
            )
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

    /**
     * 加载章节；翻章时保留上一章画面直到新内容到达，避免闪白。
     *
     * @param persist 用户主动翻章时立即写盘（新章从顶部开始）；资源补齐 / 重试不写盘，
     *                避免把刚恢复的章内位置覆盖掉。
     * @param restoreY 打开书时从持久化记录恢复的章内滚动位置。
     */
    private fun loadChapter(index: Int, persist: Boolean = true, restoreY: Int = 0) {
        val uri = fileUri ?: return
        val book = currentState.book ?: return
        if (book.chapters.getOrNull(index) == null) return

        chapterJob?.cancel()
        updateState {
            it.copy(
                isChapterLoading = true,
                currentChapterIndex = index,
                chapterError = null,
                restoreScrollY = restoreY,
                restoreRequestId = it.restoreRequestId + 1,
            )
        }
        if (persist) savePosition(index, 0)

        chapterJob = viewModelScope.launch {
            val page = runCatching { repository.readChapter(uri, book, index) }
                .getOrNull()
                ?.copy(chapterIndex = index)
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

    /**
     * 写盘保存阅读位置。带章节索引是为了丢弃「用户已翻章、但旧章滚动回调延迟送达」的事件，
     * 否则会把上一章的 Y 写到新章上。
     */
    private fun savePosition(chapterIndex: Int, scrollY: Int) {
        val uri = fileUri ?: return
        if (chapterIndex != currentState.currentChapterIndex) return
        val y = scrollY.coerceAtLeast(0)
        onScroll(chapterIndex, y)
        if (chapterIndex == lastSavedChapter && y == lastSavedScrollY) return
        lastSavedChapter = chapterIndex
        lastSavedScrollY = y
        viewModelScope.launch {
            runCatching { positionRepository.save(uri.toString(), chapterIndex, y) }
        }
    }

    private fun toggleToc() {
        updateState { it.copy(showToc = !it.showToc) }
    }

    private fun closeToc() {
        updateState { it.copy(showToc = false) }
    }

    /**
     * 页面销毁时兜底写盘：ViewModel 作用域此刻已在取消中，
     * 因此改用应用级作用域，避免最后一次滚动恰好落在节流窗口内而丢失。
     */
    override fun onCleared() {
        val uri = fileUri
        val chapter = currentState.currentChapterIndex
        if (uri != null && latestChapter == chapter) {
            val y = latestScrollY
            appScope.launch {
                runCatching { positionRepository.save(uri.toString(), chapter, y) }
            }
        }
        super.onCleared()
    }
}
