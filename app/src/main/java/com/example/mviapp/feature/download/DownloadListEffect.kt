package com.example.mviapp.feature.download

import com.example.mviapp.core.mvi.MviEffect

/** 下载管理一次性事件 */
sealed interface DownloadListEffect : MviEffect {
    data class ShowToast(val message: String) : DownloadListEffect
}
