package com.example.mviapp.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * LanDrop HTTP 客户端
 *
 * 基于 HttpURLConnection 实现，无第三方依赖。Mac 端 LanDrop 服务监听 8080 端口，
 * 提供 ping / 文件列表 / 上传 / 下载 / 删除接口。
 */
class LanDropClient(private val baseUrl: String) {

    data class DeviceInfo(val name: String, val service: String, val port: Int)

    data class FileEntry(val name: String, val size: Long, val sizeText: String, val time: Long)

    /** 健康检查 */
    suspend fun ping(): Result<DeviceInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val body = get("$baseUrl/api/ping")
            val json = JSONObject(body)
            DeviceInfo(
                name = json.optString("name"),
                service = json.optString("service"),
                port = json.optInt("port"),
            )
        }
    }

    /** 获取文件列表 */
    suspend fun listFiles(): Result<List<FileEntry>> = withContext(Dispatchers.IO) {
        runCatching {
            val body = get("$baseUrl/api/files")
            val arr = JSONArray(body)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                FileEntry(
                    name = o.optString("name"),
                    size = o.optLong("size"),
                    sizeText = o.optString("sizeText"),
                    time = o.optLong("time"),
                )
            }
        }
    }

    /** 下载文件到输出流，带进度回调（已下载字节, 总字节；总字节未知时为 -1） */
    suspend fun download(
        name: String,
        output: OutputStream,
        onProgress: (Long, Long) -> Unit,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "$baseUrl/api/download?name=${URLEncoder.encode(name, "UTF-8")}"
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = CONNECT_TIMEOUT
            conn.readTimeout = READ_TIMEOUT
            conn.requestMethod = "GET"
            try {
                if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")
                val total = conn.contentLengthLong
                conn.inputStream.use { input ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var downloaded = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        onProgress(downloaded, total)
                    }
                }
                output.flush()
            } finally {
                conn.disconnect()
            }
        }
    }

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = CONNECT_TIMEOUT
        conn.readTimeout = READ_TIMEOUT
        conn.requestMethod = "GET"
        try {
            if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT = 5_000
        const val READ_TIMEOUT = 30_000
        const val BUFFER_SIZE = 8 * 1024
    }
}
