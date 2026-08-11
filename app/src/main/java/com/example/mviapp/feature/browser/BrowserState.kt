package com.example.mviapp.feature.browser

import com.example.mviapp.core.mvi.MviState

/** 网页浏览器 UI 状态 */
data class BrowserState(
    val currentUrl: String = "",
    val title: String = "",
    val isLoading: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val addressInput: String = "",
    val errorMessage: String? = null,
    val isBookmarked: Boolean = false,
    val lastVisitedUrl: String? = null,
) : MviState
