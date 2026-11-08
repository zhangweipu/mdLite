package com.example.mviapp.core.github

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.example.mviapp.core.download.DownloadValidator
import com.example.mviapp.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.buffer
import okio.sink
import okio.source
import retrofit2.HttpException
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * GitHub 仓库的 Retrofit 实现
 *
 * 通过 [GithubApi] 调用 GitHub REST API，使用 Okio 进行流式下载以支持大文件和进度回调。
 * 下载在 [ApplicationScope] 执行，离开页面不会中断；落盘前校验字节数与文件头，
 * 避免 0 字节 / 错误页被当成成功下载。
 */
@Singleton
class RetrofitGithubRepository @Inject constructor(
    private val api: GithubApi,
    @ApplicationContext private val context: Context,
    @ApplicationScope private val appScope: CoroutineScope,
) : GithubRepository {

    override fun parseRepoUrl(url: String): Pair<String, String>? {
        val trimmed = url.trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("github.com/")
            .removeSuffix("/")

        if (trimmed.isBlank()) return null

        val parts = trimmed.split("/").filter { it.isNotEmpty() }
        if (parts.size < 2) return null

        val owner = parts[0]
        val repo = parts[1].removeSuffix(".git")
        if (owner.isBlank() || repo.isBlank()) return null

        return owner to repo
    }

    override suspend fun listFiles(owner: String, repo: String, path: String): List<GithubFile> {
        return withContext(Dispatchers.IO) {
            try {
                api.listContents(owner, repo, path)
            } catch (e: HttpException) {
                val message = when (e.code()) {
                    404 -> "仓库不存在或路径无效"
                    403 -> "API 请求被限制，请稍后再试"
                    else -> "请求失败：${e.message()}"
                }
                error(message)
            } catch (e: Exception) {
                error("网络错误：${e.message ?: "未知错误"}")
            }
        }
    }

    override suspend fun downloadFile(
        downloadUrl: String,
        destinationUri: Uri,
        onProgress: (Long, Long) -> Unit,
    ): Result<String> = withContext(Dispatchers.IO) {
        // 用应用级作用域承载实际下载：调用方 ViewModel 销毁（离开页面 / 旋转屏幕）
        // 只会取消 await，不会取消写入，也就不会留下 0 字节文件。
        appScope.async { doDownload(downloadUrl, destinationUri, onProgress) }.await()
    }

    private suspend fun doDownload(
        downloadUrl: String,
        destinationUri: Uri,
        onProgress: (Long, Long) -> Unit,
    ): Result<String> {
        val partDir = File(context.cacheDir, "downloads").apply { mkdirs() }
        val part = File(partDir, "github-${System.currentTimeMillis()}.part")
        return runCatching {
            val body = api.downloadFile(downloadUrl)
            var written = 0L
            var total = -1L

            body.use { response ->
                total = response.contentLength()
                response.byteStream().source().buffer().use { source ->
                    part.outputStream().sink().buffer().use { sink ->
                        val buffer = Buffer()
                        while (true) {
                            val read = source.read(buffer, 8192L)
                            if (read == -1L) break
                            sink.write(buffer, read)
                            written += read
                            if (total > 0) onProgress(written, total)
                        }
                    }
                }
            }

            val head = ByteArray(64)
            val headSize = part.inputStream().use { it.read(head) }.coerceAtLeast(0)
            val actualHead = if (headSize == head.size) head else head.copyOf(headSize)
            val name = destinationUri.lastPathSegment?.substringAfterLast('/') ?: "文件"
            DownloadValidator.validate(name, written, total, null, actualHead)?.let { error(it) }

            val target = context.contentResolver.openOutputStream(destinationUri, "w")
                ?: error("无法写入保存位置，请重新选择文件夹")
            val copied = target.use { out ->
                part.inputStream().use { input -> input.copyTo(out, 8192) }
            }
            if (copied != written) error("写入文件不完整：$copied / $written 字节")
            name
        }.onFailure {
            removeEmptyTarget(destinationUri)
        }.also {
            part.delete()
        }
    }

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
}
