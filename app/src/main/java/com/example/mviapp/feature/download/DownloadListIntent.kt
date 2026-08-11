package com.example.mviapp.feature.download

import com.example.mviapp.core.mvi.MviIntent

/** 下载管理 Intent */
sealed interface DownloadListIntent : MviIntent {

    /** 加载下载列表 */
    data object Load : DownloadListIntent

    /** 删除一条记录 */
    data class Remove(val id: String) : DownloadListIntent

    /** 清空所有记录 */
    data object ClearAll : DownloadListIntent
}
