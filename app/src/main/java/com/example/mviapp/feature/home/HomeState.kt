package com.example.mviapp.feature.home

import com.example.mviapp.core.md.MdFile
import com.example.mviapp.core.history.ReadingRecord
import com.example.mviapp.core.mvi.MviState

/**
 * 首页 UI 状态
 *
 * 使用 sealed interface 表示不同加载阶段的状态，
 * Compose 根据状态类型渲染不同的 UI（加载中 / 成功 / 失败）。
 */
sealed interface HomeState : MviState {

    /** 初始加载中 */
    data object Loading : HomeState

    /** 加载成功（Markdown 文档列表） */
    data class Success(
        val files: List<MdFile> = emptyList(),
        val recentReads: List<ReadingRecord> = emptyList(),
        val isRefreshing: Boolean = false,
    ) : HomeState

    /** 加载失败 */
    data class Error(val message: String) : HomeState
}
