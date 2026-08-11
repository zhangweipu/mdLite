package com.example.mviapp.core.github

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.buffer
import okio.sink
import okio.source
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * GitHub 仓库的 Retrofit 实现
 *
 * 通过 [GithubApi] 调用 GitHub REST API，使用 Okio 进行流式下载以支持大文件和进度回调。
 */
@Singleton
class RetrofitGithubRepository @Inject constructor(
    private val api: GithubApi,
    @ApplicationContext private val context: Context,
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
        runCatching {
            val response = api.downloadFile(downloadUrl)
            val total = response.contentLength()
            var bytesRead = 0L

            val inputStream = response.byteStream()
            val outputStream = context.contentResolver.openOutputStream(destinationUri, "w")
                ?: error("无法打开保存位置")

            inputStream.source().buffer().use { source ->
                outputStream.sink().buffer().use { sink ->
                    val buffer = okio.Buffer()
                    while (true) {
                        val read = source.read(buffer, 8192L)
                        if (read == -1L) break
                        sink.write(buffer, read)
                        bytesRead += read
                        if (total > 0) {
                            onProgress(bytesRead, total)
                        }
                    }
                    sink.flush()
                }
            }

            inputStream.close()
            outputStream.close()

            destinationUri.lastPathSegment?.substringAfterLast('/') ?: "文件"
        }
    }
}
