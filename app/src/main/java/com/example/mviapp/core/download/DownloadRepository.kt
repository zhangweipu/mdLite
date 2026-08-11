package com.example.mviapp.core.download

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import okio.source
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 下载管理仓库接口
 *
 * 负责下载任务的创建、执行、状态跟踪和持久化。
 * 下载使用 OkHttp 流式写入，支持进度回调。
 */
interface DownloadRepository {

    /** 所有下载记录的 Flow（可观察） */
    val downloads: StateFlow<List<DownloadItem>>

    /** 获取所有下载记录（按创建时间倒序） */
    suspend fun getAll(): List<DownloadItem>

    /**
     * 创建并开始一个下载任务
     *
     * @param url 下载地址
     * @param fileName 文件名
     * @param destinationUri 本地保存位置
     * @return 新创建的下载任务
     */
    suspend fun startDownload(url: String, fileName: String, destinationUri: Uri): DownloadItem

    /** 更新下载进度（内部使用，也暴露给外部自定义下载场景） */
    suspend fun updateProgress(id: String, progress: Int, downloadedSize: Long, totalSize: Long)

    /** 标记下载完成 */
    suspend fun markCompleted(id: String, localPath: String)

    /** 标记下载失败 */
    suspend fun markFailed(id: String, errorMessage: String)

    /** 删除一条下载记录（不删除本地文件） */
    suspend fun remove(id: String)

    /** 清空所有下载记录 */
    suspend fun clear()
}

/**
 * DownloadRepository 的 OkHttp + SharedPreferences 实现
 *
 * 使用 OkHttp 执行下载，SharedPreferences 持久化记录。
 * 内存中维护 StateFlow 供 UI 观察。
 */
@Singleton
class OkHttpDownloadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
) : DownloadRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _downloads = MutableStateFlow<List<DownloadItem>>(emptyList())
    override val downloads: StateFlow<List<DownloadItem>> = _downloads.asStateFlow()

    init {
        // 初始化时加载持久化数据
        _downloads.value = readAll().sortedByDescending { it.createdAt }
    }

    override suspend fun getAll(): List<DownloadItem> = withContext(Dispatchers.IO) {
        readAll().sortedByDescending { it.createdAt }
    }

    override suspend fun startDownload(
        url: String,
        fileName: String,
        destinationUri: Uri,
    ): DownloadItem {
        val id = UUID.randomUUID().toString()
        val item = DownloadItem(
            id = id,
            fileName = fileName,
            url = url,
            status = DownloadStatus.Downloading,
            createdAt = System.currentTimeMillis(),
        )
        addItem(item)

        // 在 IO 线程执行下载
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder().url(url).build()
                val response = okHttpClient.newCall(request).execute()
                val body = response.body ?: error("响应体为空")
                val totalSize = body.contentLength()

                val outputStream = context.contentResolver.openOutputStream(destinationUri, "w")
                    ?: error("无法打开保存位置")

                var downloaded = 0L
                body.source().buffer().use { source ->
                    outputStream.sink().buffer().use { sink ->
                        val buffer = okio.Buffer()
                        while (true) {
                            val read = source.read(buffer, 8192L)
                            if (read == -1L) break
                            sink.write(buffer, read)
                            downloaded += read
                            val progress = if (totalSize > 0) (downloaded * 100 / totalSize).toInt() else 0
                            // 定期更新进度（每 100KB 或完成时）
                            if (progress != item.progress || read == -1L) {
                                updateProgress(id, progress, downloaded, totalSize)
                            }
                        }
                        sink.flush()
                    }
                }
                outputStream.close()
                destinationUri
            }
                .onSuccess { uri ->
                    // 持久化保存位置的读写权限
                    runCatching {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                        )
                    }
                    markCompleted(id, uri.toString())
                }
                .onFailure { error ->
                    markFailed(id, error.message ?: "下载失败")
                }
        }

        return item
    }

    override suspend fun updateProgress(id: String, progress: Int, downloadedSize: Long, totalSize: Long) {
        updateItem(id) { it.copy(progress = progress, downloadedSize = downloadedSize, totalSize = totalSize) }
    }

    override suspend fun markCompleted(id: String, localPath: String) {
        updateItem(id) {
            it.copy(
                status = DownloadStatus.Completed,
                progress = 100,
                localPath = localPath,
                completedAt = System.currentTimeMillis(),
                downloadedSize = it.totalSize.takeIf { s -> s > 0 } ?: it.downloadedSize,
            )
        }
    }

    override suspend fun markFailed(id: String, errorMessage: String) {
        updateItem(id) { it.copy(status = DownloadStatus.Failed, errorMessage = errorMessage) }
    }

    override suspend fun remove(id: String) {
        val updated = _downloads.value.filterNot { it.id == id }
        _downloads.value = updated
        withContext(Dispatchers.IO) { saveAll(updated) }
    }

    override suspend fun clear() {
        _downloads.value = emptyList()
        withContext(Dispatchers.IO) { saveAll(emptyList()) }
    }

    // ==================== 私有方法 ====================

    private suspend fun addItem(item: DownloadItem) {
        val updated = listOf(item) + _downloads.value
        _downloads.value = updated
        withContext(Dispatchers.IO) { saveAll(updated) }
    }

    private suspend fun updateItem(id: String, transform: (DownloadItem) -> DownloadItem) {
        val updated = _downloads.value.map { if (it.id == id) transform(it) else it }
        _downloads.value = updated
        withContext(Dispatchers.IO) { saveAll(updated) }
    }

    private fun readAll(): List<DownloadItem> {
        val raw = prefs.getString(KEY_DOWNLOADS, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(DownloadItem.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    private fun saveAll(items: List<DownloadItem>) {
        prefs.edit()
            .putString(KEY_DOWNLOADS, json.encodeToString(ListSerializer(DownloadItem.serializer()), items))
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "download_manager"
        const val KEY_DOWNLOADS = "downloads"
    }
}
