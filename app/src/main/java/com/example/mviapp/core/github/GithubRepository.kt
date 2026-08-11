package com.example.mviapp.core.github

import android.net.Uri

/**
 * GitHub 文件仓库接口
 *
 * 负责与 GitHub API 交互：浏览仓库目录、下载文件到本地。
 * 接口隔离便于测试和替换实现。
 */
interface GithubRepository {

    /**
     * 解析 GitHub 仓库 URL，提取 owner 和 repo
     *
     * 支持的格式：
     * - https://github.com/owner/repo
     * - https://github.com/owner/repo/tree/main/path/to/dir
     * - github.com/owner/repo
     *
     * @return Pair(owner, repo)，格式不正确时返回 null
     */
    fun parseRepoUrl(url: String): Pair<String, String>?

    /**
     * 列出仓库指定路径下的文件/目录
     *
     * @param owner 仓库所有者
     * @param repo 仓库名
     * @param path 相对路径（空字符串表示根目录）
     * @return 文件/目录列表
     */
    suspend fun listFiles(owner: String, repo: String, path: String = ""): List<GithubFile>

    /**
     * 下载文件到指定 Uri
     *
     * @param downloadUrl 文件的 raw 下载地址
     * @param destinationUri 本地保存位置（content:// Uri）
     * @param onProgress 进度回调（已下载字节数, 总字节数）
     */
    suspend fun downloadFile(
        downloadUrl: String,
        destinationUri: Uri,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): Result<String>
}
