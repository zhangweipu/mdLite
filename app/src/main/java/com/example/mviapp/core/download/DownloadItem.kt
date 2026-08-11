package com.example.mviapp.core.download

import kotlinx.serialization.Serializable

/**
 * 下载任务状态
 */
enum class DownloadStatus {
    /** 等待中 */
    Pending,
    /** 下载中 */
    Downloading,
    /** 已完成 */
    Completed,
    /** 失败 */
    Failed,
}

/**
 * 下载记录数据模型
 *
 * @property id 唯一标识（用时间戳 + 文件名哈希生成）
 * @property fileName 文件名
 * @property url 下载地址
 * @property localPath 本地保存路径（content:// Uri 字符串）
 * @property status 下载状态
 * @property progress 下载进度百分比（0-100）
 * @property totalSize 总大小（字节），未知时为 0
 * @property downloadedSize 已下载大小（字节）
 * @property createdAt 创建时间戳
 * @property completedAt 完成时间戳，未完成时为 0
 * @property errorMessage 失败时的错误信息
 */
@Serializable
data class DownloadItem(
    val id: String,
    val fileName: String,
    val url: String,
    val localPath: String = "",
    val status: DownloadStatus = DownloadStatus.Pending,
    val progress: Int = 0,
    val totalSize: Long = 0,
    val downloadedSize: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long = 0,
    val errorMessage: String? = null,
) {
    /** 格式化的总大小文本 */
    val totalSizeText: String
        get() = formatSize(totalSize)

    /** 格式化的已下载大小文本 */
    val downloadedSizeText: String
        get() = formatSize(downloadedSize)

    private fun formatSize(bytes: Long): String = when {
        bytes <= 0 -> "-"
        bytes < 1024 -> "${bytes} B"
        bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0)} KB"
        else -> "${"%.1f".format(bytes / 1024.0 / 1024.0)} MB"
    }
}
