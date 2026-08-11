package com.example.mviapp.feature.html

import com.example.mviapp.core.mvi.MviState

/** HTML 阅读页 UI 状态 */
sealed interface HtmlReaderState : MviState {

    data object Loading : HtmlReaderState

    data class Success(
        val content: String,
        val pinned: Boolean = false,
        val restoreY: Int = 0,
    ) : HtmlReaderState

    data class Error(val message: String) : HtmlReaderState
}
