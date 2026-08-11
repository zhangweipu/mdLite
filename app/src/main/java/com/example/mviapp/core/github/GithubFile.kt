package com.example.mviapp.core.github

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * GitHub 仓库文件/目录条目（对应 GitHub Contents API 返回的列表项）
 *
 * 当 type 为 "file" 时，size 为文件大小；当 type 为 "dir" 时 size 为 0。
 * download_url 仅对文件有效，目录项为 null。
 */
@Serializable
data class GithubFile(
    val name: String,
    val path: String,
    val type: String,        // "file" 或 "dir"
    val size: Long = 0,
    @SerialName("download_url")
    val downloadUrl: String? = null,
    @SerialName("html_url")
    val htmlUrl: String = "",
) {
    val isFile: Boolean get() = type == "file"
    val isDir: Boolean get() = type == "dir"

    /** 格式化后的文件大小文本（仅文件有效） */
    val sizeText: String
        get() = when {
            size <= 0 -> "-"
            size < 1024 -> "${size} B"
            size < 1024 * 1024 -> "${"%.1f".format(size / 1024.0)} KB"
            else -> "${"%.1f".format(size / 1024.0 / 1024.0)} MB"
        }
}
