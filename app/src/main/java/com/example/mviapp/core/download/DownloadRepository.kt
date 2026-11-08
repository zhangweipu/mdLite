package com.example.mviapp.core.download

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.webkit.CookieManager
import android.webkit.WebSettings
import com.example.mviapp.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import okio.buffer
import okio.sink
import java.io.File
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
     * 创建并开始一个下载任务，**立即返回**，实际下载在应用级作用域继续执行。
     *
     * @param url 下载地址
     * @param fileName 文件名
     * @param destinationUri 本地保存位置
     * @param referer 触发下载的网页地址（防盗链站点需要）
     * @return 新创建的下载任务
     */
    suspend fun startDownload(
        url: String,
        fileName: String,
        destinationUri: Uri,
        referer: String? = null,
    ): DownloadItem

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
 * DownloadRepository 的 OkHttp + SharedPreferences 实现。
 *
 * 修复「下载完是 0 字节」的几处根因：
 * 1. 旧实现整段下载跑在调用方 ViewModel 的 viewModelScope 里。用户点开下载后
 *    一旦离开浏览器页（去看下载进度）或旋转屏幕，ViewModel 被清除、协程被取消，
 *    而 SAF 预创建的空文件已经被 `openOutputStream(uri, "w")` 截断，于是永久留在磁盘上。
 *    现在下载在 [ApplicationScope] 执行，与页面生命周期解耦。
 * 2. 旧实现不检查 HTTP 状态、Content-Length、写入字节数，0 字节 / 403 登录页 /
 *    HTML 错误页都会被标记「已完成」并当成 .epub 打开。
 * 3. 现在先流式写入应用私有临时文件，[DownloadValidator] 校验通过后才写入用户
 *    选定的位置；失败时清理 SAF 预创建的 0 字节文件并给出具体原因。
 */
@Singleton
class OkHttpDownloadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    @ApplicationScope private val appScope: CoroutineScope,
) : DownloadRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _downloads = MutableStateFlow<List<DownloadItem>>(emptyList())
    override val downloads: StateFlow<List<DownloadItem>> = _downloads.asStateFlow()

    init {
        _downloads.value = readAll().sortedByDescending { it.createdAt }
    }

    override suspend fun getAll(): List<DownloadItem> = withContext(Dispatchers.IO) {
        readAll().sortedByDescending { it.createdAt }
    }

    override suspend fun startDownload(
        url: String,
        fileName: String,
        destinationUri: Uri,
        referer: String?,
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

        // 权限要在下载前持久化：下载中途页面退出/进程重启后，仍需要能打开这个文件。
        // 旧实现放在下载成功之后，失败时连读权限都没有拿到。
        persistUriPermission(destinationUri)

        // 不 await：调用方（ViewModel）销毁也不会中断下载。
        appScope.launch { executeDownload(id, url, fileName, destinationUri, referer) }
        return item
    }

    private suspend fun executeDownload(
        id: String,
        url: String,
        fileName: String,
        destinationUri: Uri,
        referer: String?,
    ) {
        val partDir = File(context.cacheDir, "downloads").apply { mkdirs() }
        val part = File(partDir, "$id.part")
        try {
            var written = 0L
            var expected = -1L
            var contentType: String? = null

            okHttpClient.newCall(buildRequest(url, referer)).execute().use { response ->
                if (!response.isSuccessful) {
                    error("HTTP ${response.code}：${response.message}")
                }
                val body = response.body ?: error("服务器返回了空响应（无响应体）")
                expected = body.contentLength()
                contentType = body.contentType()?.toString()

                body.source().use { source ->
                    part.outputStream().sink().buffer().use { sink ->
                        val buffer = Buffer()
                        var lastPercent = -1
                        while (true) {
                            val read = source.read(buffer, BUFFER_SIZE)
                            if (read == -1L) break
                            sink.write(buffer, read)
                            written += read
                            val percent = if (expected > 0) {
                                (written * 100 / expected).toInt().coerceAtMost(99)
                            } else 0
                            if (percent != lastPercent) {
                                lastPercent = percent
                                updateProgress(id, percent, written, expected)
                            }
                        }
                    }
                }
            }

            val head = ByteArray(64)
            val headSize = part.inputStream().use { it.read(head) }.coerceAtLeast(0)
            val actualHead = if (headSize == head.size) head else head.copyOf(headSize)
            DownloadValidator.validate(fileName, written, expected, contentType, actualHead)
                ?.let { error(it) }

            // 只有校验通过才碰用户选定位置，避免把错误页/半个文件写进用户目录。
            val target = context.contentResolver.openOutputStream(destinationUri, "w")
                ?: error("无法写入保存位置，请重新选择文件夹")
            val copied = target.use { out ->
                part.inputStream().use { input -> input.copyTo(out, BUFFER_SIZE.toInt()) }
            }
            if (copied != written) error("写入文件不完整：$copied / $written 字节")

            updateProgress(id, 100, written, written)
            markCompleted(id, destinationUri.toString())
        } catch (error: Throwable) {
            // SAF 在 ACTION_CREATE_DOCUMENT 时会先创建一个 0 字节占位文件，
            // 下载失败必须把它清掉，否则用户看到的就是「下载了 0b 的文件」。
            removeEmptyTarget(destinationUri)
            markFailed(id, error.message?.take(180) ?: "下载失败")
        } finally {
            part.delete()
        }
    }

    /** 带上与 WebView 一致的请求信息，避免被服务器当作爬虫返回空内容或错误页 */
    private fun buildRequest(url: String, referer: String?): Request = Request.Builder()
        .url(url)
        .header("User-Agent", userAgent())
        .header("Accept", "*/*")
        .apply {
            if (!referer.isNullOrBlank()) header("Referer", referer)
            webViewCookies(url)?.let { header("Cookie", it) }
        }
        .build()

    private fun userAgent(): String = runCatching {
        WebSettings.getDefaultUserAgent(context)
    }.getOrDefault(DEFAULT_USER_AGENT)

    /** WebView 的登录 Cookie 默认不与 OkHttp 共享，需要主动透传 */
    private fun webViewCookies(url: String): String? = runCatching {
        CookieManager.getInstance().getCookie(url)
    }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun persistUriPermission(uri: Uri) {
        if (!uri.scheme.equals("content", ignoreCase = true)) return
        val resolver = context.contentResolver
        val both = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { resolver.takePersistableUriPermission(uri, both) }
            .onFailure {
                // 某些 provider 只授予其中一种权限，分开申请避免整体失败
                runCatching { resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                runCatching { resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            }
    }

    /** 仅删除本次下载留下、且仍为空的占位文件，不误删用户已有的非空文件 */
    private fun removeEmptyTarget(uri: Uri) {
        runCatching {
            if (!uri.scheme.equals("content", ignoreCase = true)) return
            val size = context.contentResolver.query(
                uri, arrayOf(OpenableColumns.SIZE), null, null, null,
            )?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else -1L
            } ?: -1L
            if (size == 0L) DocumentsContract.deleteDocument(context.contentResolver, uri)
        }
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
                downloadedSize = it.totalSize.takeIf { size -> size > 0 } ?: it.downloadedSize,
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
        const val BUFFER_SIZE = 8192L
        const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/120.0.0.0 Mobile Safari/537.36"
    }
}
