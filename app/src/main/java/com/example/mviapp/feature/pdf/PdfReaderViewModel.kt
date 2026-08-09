package com.example.mviapp.feature.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.mviapp.core.history.ReadingHistoryRepository
import com.example.mviapp.core.mvi.MviViewModel
import com.example.mviapp.core.navigation.Screen
import com.example.mviapp.core.reader.ReadingPositionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * PDF 阅读页 ViewModel
 *
 * 基于系统 [PdfRenderer] 打开 content:// 或 file:// 的 PDF 文件，
 * 按需渲染页面 Bitmap（带 LRU 缓存），并复用阅读历史/位置/置顶能力。
 */
@HiltViewModel
class PdfReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val historyRepository: ReadingHistoryRepository,
    private val positionRepository: ReadingPositionRepository,
) : MviViewModel<PdfReaderIntent, PdfReaderState, PdfReaderEffect>(
    initialState = PdfReaderState.Loading
) {

    private val filePath: String =
        savedStateHandle.get<String>(Screen.Reader.ARG_FILE_PATH).orEmpty()

    private val fileTitle: String =
        savedStateHandle.get<String>(Screen.Reader.ARG_TITLE).orEmpty()

    private var renderer: PdfRenderer? = null
    private var descriptor: ParcelFileDescriptor? = null
    private val renderMutex = Mutex()
    private val cache = object : LinkedHashMap<Int, Bitmap>(0, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Bitmap>?): Boolean =
            size > MAX_CACHE_PAGES
    }

    init {
        dispatch(PdfReaderIntent.Load)
    }

    override suspend fun handleIntent(intent: PdfReaderIntent) {
        when (intent) {
            is PdfReaderIntent.Load -> load()
            is PdfReaderIntent.Retry -> load()
            is PdfReaderIntent.TogglePin -> togglePin()
            is PdfReaderIntent.SavePosition -> savePosition(intent.page)
        }
    }

    private fun load() {
        updateState { PdfReaderState.Loading }
        viewModelScope.launch {
            runCatching {
                closeRenderer()
                val descriptor = openDescriptor() ?: error("无法打开文件")
                this@PdfReaderViewModel.descriptor = descriptor
                val renderer = PdfRenderer(descriptor)
                this@PdfReaderViewModel.renderer = renderer
                val pageCount = renderer.pageCount
                historyRepository.add(
                    title = fileTitle.ifBlank { fallbackTitle() },
                    source = filePath,
                )
                val pinned = historyRepository.isPinned(filePath)
                val savedPosition = positionRepository.get(filePath)
                LoadResult(pageCount, pinned, savedPosition?.index ?: 0)
            }
                .onSuccess { result ->
                    updateState {
                        PdfReaderState.Success(
                            pageCount = result.pageCount,
                            pinned = result.pinned,
                            restorePage = result.restorePage,
                        )
                    }
                }
                .onFailure { error ->
                    closeRenderer()
                    updateState { PdfReaderState.Error(message = error.message ?: "打开失败") }
                }
        }
    }

    /** 渲染指定页，带 LRU 缓存；renderer 非线程安全，统一加锁串行执行 */
    suspend fun renderPage(index: Int): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            renderMutex.withLock {
                cache[index]?.let { return@withLock it }
                val current = renderer ?: return@withLock null
                if (index < 0 || index >= current.pageCount) return@withLock null
                current.openPage(index).use { page ->
                    val targetWidth = 1080f
                    val scale = targetWidth / page.width.coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(
                        (page.width * scale).toInt().coerceAtLeast(1),
                        (page.height * scale).toInt().coerceAtLeast(1),
                        Bitmap.Config.ARGB_8888,
                    )
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    cache[index] = bitmap
                    bitmap
                }
            }
        }.getOrNull()
    }

    private fun openDescriptor(): ParcelFileDescriptor? = when {
        filePath.startsWith("content://") ->
            context.contentResolver.openFileDescriptor(Uri.parse(filePath), "r")
        filePath.startsWith("file://") -> {
            val file = Uri.parse(filePath).path?.let(::File)
            if (file?.exists() == true) {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            } else {
                null
            }
        }
        else -> null
    }

    private suspend fun togglePin() {
        if (filePath.isBlank()) return
        val current = currentState as? PdfReaderState.Success ?: return
        val newPinned = !current.pinned
        runCatching { historyRepository.setPinned(filePath, newPinned) }
        updateState { state ->
            if (state is PdfReaderState.Success) state.copy(pinned = newPinned) else state
        }
    }

    private suspend fun savePosition(page: Int) {
        if (filePath.isBlank()) return
        runCatching { positionRepository.save(filePath, page, 0) }
    }

    private fun closeRenderer() {
        renderer?.close()
        renderer = null
        descriptor?.close()
        descriptor = null
        cache.values.forEach { it.recycle() }
        cache.clear()
    }

    private fun fallbackTitle(): String =
        Uri.decode(filePath).substringAfterLast('/').substringAfterLast(':')

    override fun onCleared() {
        closeRenderer()
        super.onCleared()
    }

    private data class LoadResult(
        val pageCount: Int,
        val pinned: Boolean,
        val restorePage: Int,
    )

    private companion object {
        const val MAX_CACHE_PAGES = 8
    }
}
