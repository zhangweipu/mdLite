package com.example.mviapp.feature.html

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.mviapp.core.history.ReadingHistoryRepository
import com.example.mviapp.core.md.MdRepository
import com.example.mviapp.core.mvi.MviViewModel
import com.example.mviapp.core.navigation.Screen
import com.example.mviapp.core.reader.ReadingPositionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * HTML 阅读页 ViewModel
 *
 * 读取 HTML 原文交给 WebView 完整渲染（保留 CSS/布局），
 * 复用阅读历史、阅读位置（滚动 Y）与置顶能力。
 */
@HiltViewModel
class HtmlReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: MdRepository,
    private val historyRepository: ReadingHistoryRepository,
    private val positionRepository: ReadingPositionRepository,
) : MviViewModel<HtmlReaderIntent, HtmlReaderState, HtmlReaderEffect>(
    initialState = HtmlReaderState.Loading
) {

    private val filePath: String =
        savedStateHandle.get<String>(Screen.Reader.ARG_FILE_PATH).orEmpty()

    private val fileTitle: String =
        savedStateHandle.get<String>(Screen.Reader.ARG_TITLE).orEmpty()

    init {
        dispatch(HtmlReaderIntent.Load)
    }

    override suspend fun handleIntent(intent: HtmlReaderIntent) {
        when (intent) {
            is HtmlReaderIntent.Load -> load()
            is HtmlReaderIntent.Retry -> load()
            is HtmlReaderIntent.TogglePin -> togglePin()
            is HtmlReaderIntent.SavePosition -> savePosition(intent.scrollY)
        }
    }

    private fun load() {
        updateState { HtmlReaderState.Loading }
        viewModelScope.launch {
            runCatching {
                val content = readContent()
                historyRepository.add(
                    title = fileTitle.ifBlank { fallbackTitle() },
                    source = filePath,
                )
                val pinned = historyRepository.isPinned(filePath)
                val savedPosition = positionRepository.get(filePath)
                LoadResult(content, pinned, savedPosition?.index ?: 0)
            }
                .onSuccess { result ->
                    updateState {
                        HtmlReaderState.Success(
                            content = result.content,
                            pinned = result.pinned,
                            restoreY = result.restoreY,
                        )
                    }
                }
                .onFailure { error ->
                    updateState { HtmlReaderState.Error(message = error.message ?: "读取失败") }
                }
        }
    }

    private suspend fun readContent(): String = when {
        filePath.startsWith("content://") -> repository.readUri(Uri.parse(filePath))
        filePath.startsWith("file://") -> {
            val file = Uri.parse(filePath).path?.let(::File)
            file?.readText() ?: error("无法打开文件")
        }
        else -> repository.readFile(filePath)
    }

    private suspend fun togglePin() {
        if (filePath.isBlank()) return
        val current = currentState as? HtmlReaderState.Success ?: return
        val newPinned = !current.pinned
        runCatching { historyRepository.setPinned(filePath, newPinned) }
        updateState { state ->
            if (state is HtmlReaderState.Success) state.copy(pinned = newPinned) else state
        }
    }

    private suspend fun savePosition(scrollY: Int) {
        if (filePath.isBlank()) return
        runCatching { positionRepository.save(filePath, scrollY, 0) }
    }

    private fun fallbackTitle(): String =
        Uri.decode(filePath).substringAfterLast('/').substringAfterLast(':')

    private data class LoadResult(
        val content: String,
        val pinned: Boolean,
        val restoreY: Int,
    )
}
