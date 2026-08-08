package com.example.mviapp.feature.home

import com.example.mviapp.core.mvi.MviEffect

/**
 * 首页 Effect —— 一次性副作用
 *
 * 用于不属于 State 的操作，如 Toast、导航等。
 */
sealed interface HomeEffect : MviEffect {

    /** 显示 Toast */
    data class ShowToast(val message: String) : HomeEffect

    /** 导航到 Markdown 阅读页 */
    data class NavigateToReader(val filePath: String, val title: String) : HomeEffect
}
