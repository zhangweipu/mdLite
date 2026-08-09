package com.example.mviapp.feature.landrop

import com.example.mviapp.core.mvi.MviState
import com.example.mviapp.core.network.LanDropClient

/** LanDrop 下载页 UI 状态 */
sealed interface LanDropState : MviState {

    /** 正在自动发现服务 */
    data object Discovering : LanDropState

    /** 已连接，[files] 为 Mac 端文件列表 */
    data class Connected(
        val device: String,
        val host: String,
        val port: Int,
        val files: List<LanDropClient.FileEntry>,
        val downloadingName: String? = null,
        val progress: Int = 0,
    ) : LanDropState {
        val isDownloading: Boolean get() = downloadingName != null
    }

    /** 连接/发现失败 */
    data class Error(val message: String) : LanDropState
}
