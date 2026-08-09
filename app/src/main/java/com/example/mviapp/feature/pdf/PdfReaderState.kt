package com.example.mviapp.feature.pdf

import com.example.mviapp.core.mvi.MviState

/** PDF 阅读页 UI 状态 */
sealed interface PdfReaderState : MviState {

    data object Loading : PdfReaderState

    data class Success(
        val pageCount: Int,
        val pinned: Boolean = false,
        val restorePage: Int = 0,
    ) : PdfReaderState

    data class Error(val message: String) : PdfReaderState
}
