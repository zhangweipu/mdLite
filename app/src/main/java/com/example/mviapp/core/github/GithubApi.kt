package com.example.mviapp.core.github

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Url

/**
 * GitHub REST API 接口
 *
 * 使用 GitHub Contents API 浏览仓库目录和下载文件内容：
 * - GET /repos/{owner}/{repo}/contents/{path} — 列出目录内容或获取单个文件元数据
 * - 文件原始内容通过 download_url 直接 GET 下载
 *
 * 文档：https://docs.github.com/en/rest/repos/contents
 */
interface GithubApi {

    /**
     * 获取仓库指定路径下的文件/目录列表
     *
     * @param owner 仓库所有者（用户名或组织名）
     * @param repo 仓库名
     * @param path 相对路径（空字符串表示根目录）
     */
    @GET("repos/{owner}/{repo}/contents/{path}")
    suspend fun listContents(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path") path: String = "",
    ): List<GithubFile>

    /**
     * 下载文件原始内容
     *
     * @param url 文件的 raw/download URL
     */
    @GET
    suspend fun downloadFile(
        @Url url: String,
    ): ResponseBody
}
