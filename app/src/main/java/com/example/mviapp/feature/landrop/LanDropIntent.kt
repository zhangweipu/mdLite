package com.example.mviapp.feature.landrop

import android.net.Uri
import com.example.mviapp.core.mvi.MviIntent

/** LanDrop 下载页 Intent */
sealed interface LanDropIntent : MviIntent {

    /** 开始 NSD 自动发现 */
    data object StartDiscovery : LanDropIntent

    /** 停止发现（页面销毁时） */
    data object StopDiscovery : LanDropIntent

    /** 手动输入 IP:端口 连接 */
    data class ManualConnect(val host: String, val port: String) : LanDropIntent

    /** 刷新文件列表 */
    data object RefreshFiles : LanDropIntent

    /** 下载文件到用户选择的保存位置 */
    data class DownloadFile(val name: String, val destinationUri: Uri) : LanDropIntent
}
