package com.example.mviapp.feature.landrop

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.example.mviapp.core.common.isSupportedDocFile
import com.example.mviapp.core.history.ReadingHistoryRepository
import com.example.mviapp.core.mvi.MviViewModel
import com.example.mviapp.core.network.LanDropClient
import com.example.mviapp.core.network.LanDropDiscovery
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * LanDrop 下载页 ViewModel
 *
 * NSD 自动发现 LanDrop 服务 → 获取文件列表 → 用户选择保存位置后下载。
 * 下载完成如果是 md/html 文档，会加入“最近阅读”。
 */
@HiltViewModel
class LanDropViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val historyRepository: ReadingHistoryRepository,
) : MviViewModel<LanDropIntent, LanDropState, LanDropEffect>(
    initialState = LanDropState.Discovering
) {

    private var discovery: LanDropDiscovery? = null
    private var client: LanDropClient? = null

    init {
        dispatch(LanDropIntent.StartDiscovery)
    }

    override suspend fun handleIntent(intent: LanDropIntent) {
        when (intent) {
            is LanDropIntent.StartDiscovery -> startDiscovery()
            is LanDropIntent.StopDiscovery -> stopDiscovery()
            is LanDropIntent.ManualConnect -> manualConnect(intent.host, intent.port)
            is LanDropIntent.RefreshFiles -> refreshFiles()
            is LanDropIntent.DownloadFile -> downloadFile(intent.name, intent.destinationUri)
        }
    }

    private fun startDiscovery() {
        stopDiscovery()
        updateState { LanDropState.Discovering }
        discovery = LanDropDiscovery(context).also { d ->
            d.startDiscovery(object : LanDropDiscovery.Callback {
                override fun onServiceFound(host: String, port: Int, name: String) {
                    connect(baseUrl = "http://$host:$port", deviceName = name, host = host, port = port)
                }

                override fun onServiceLost() {
                    updateState { LanDropState.Discovering }
                    emitEffect(LanDropEffect.ShowToast("LanDrop 服务已断开"))
                }

                override fun onError(message: String) {
                    updateState { LanDropState.Error(message) }
                }
            })
        }
    }

    private fun stopDiscovery() {
        discovery?.stopDiscovery()
        discovery = null
    }

    private fun connect(baseUrl: String, deviceName: String, host: String, port: Int) {
        client = LanDropClient(baseUrl)
        updateState {
            LanDropState.Connected(
                device = deviceName,
                host = host,
                port = port,
                files = emptyList(),
            )
        }
        refreshFiles()
    }

    private fun manualConnect(host: String, port: String) {
        val trimmedHost = host.trim()
        val portValue = port.trim().toIntOrNull()
        if (trimmedHost.isBlank() || portValue == null || portValue !in 1..65535) {
            emitEffect(LanDropEffect.ShowToast("请输入正确的 IP 和端口"))
            return
        }
        stopDiscovery()
        connect(
            baseUrl = "http://$trimmedHost:$portValue",
            deviceName = "手动连接",
            host = trimmedHost,
            port = portValue,
        )
    }

    private fun refreshFiles() {
        val current = client ?: return
        viewModelScope.launch {
            current.listFiles()
                .onSuccess { files ->
                    updateState { state ->
                        if (state is LanDropState.Connected) state.copy(files = files) else state
                    }
                }
                .onFailure { error ->
                    emitEffect(LanDropEffect.ShowToast("获取文件列表失败：${error.message}"))
                }
        }
    }

    private fun downloadFile(name: String, destinationUri: Uri) {
        val current = client ?: return
        if ((currentState as? LanDropState.Connected)?.isDownloading == true) {
            emitEffect(LanDropEffect.ShowToast("请等待当前下载完成"))
            return
        }
        viewModelScope.launch {
            updateState { state ->
                if (state is LanDropState.Connected) {
                    state.copy(downloadingName = name, progress = 0)
                } else {
                    state
                }
            }
            runCatching {
                val output = context.contentResolver.openOutputStream(destinationUri, "w")
                    ?: error("无法打开保存位置")
                output.use { stream ->
                    current.download(name, stream) { sent, total ->
                        val percent = if (total > 0) (sent * 100 / total).toInt() else 0
                        updateState { state ->
                            if (state is LanDropState.Connected) state.copy(progress = percent) else state
                        }
                    }.getOrThrow()
                }
            }
                .onSuccess {
                    // 持久化保存位置的读写权限，之后可在 App 内直接打开
                    runCatching {
                        context.contentResolver.takePersistableUriPermission(
                            destinationUri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                        )
                    }
                    if (name.isSupportedDocFile()) {
                        historyRepository.add(title = name, source = destinationUri.toString())
                    }
                    updateState { state ->
                        if (state is LanDropState.Connected) {
                            state.copy(downloadingName = null, progress = 0)
                        } else {
                            state
                        }
                    }
                    emitEffect(LanDropEffect.ShowToast("下载完成：$name"))
                }
                .onFailure { error ->
                    updateState { state ->
                        if (state is LanDropState.Connected) {
                            state.copy(downloadingName = null, progress = 0)
                        } else {
                            state
                        }
                    }
                    emitEffect(LanDropEffect.ShowToast("下载失败：${error.message ?: "未知错误"}"))
                }
        }
    }

    override fun onCleared() {
        stopDiscovery()
        super.onCleared()
    }
}
