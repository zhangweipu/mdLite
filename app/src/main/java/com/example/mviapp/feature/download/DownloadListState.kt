package com.example.mviapp.feature.download

import com.example.mviapp.core.download.DownloadItem
import com.example.mviapp.core.mvi.MviState

/** 下载管理 UI 状态 */
data class DownloadListState(
    val items: List<DownloadItem> = emptyList(),
    val isLoading: Boolean = false,
) : MviState {
    val isEmpty: Boolean get() = items.isEmpty()
}
