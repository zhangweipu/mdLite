package com.example.mviapp.feature.reader

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
                content to savedPosition
            }
                .onSuccess { (content, savedPosition) ->
                    updateState {
                        MdReaderState.Success(
                            content = content,
                            scrollIndex = savedPosition?.index ?: 0,
                            scrollOffset = savedPosition?.offset ?: 0,
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

    /** 未显式传标题时，从路径推导 */
    private fun fallbackTitle(): String =
        Uri.decode(filePath).substringAfterLast('/').removeSuffix(".md")
}
