package com.example.mviapp.core.navigation

import android.net.Uri

/**
 * 应用路由定义
 *
 * 每个页面定义为一个 sealed class 子项，包含：
 * - [route]: 路由字符串，用于 NavHost 注册和导航
 * - 可选的参数
 *
 * 新增页面时只需在此添加一个新的对象，并在 AppNavHost 中注册对应 composable。
 */
sealed class Screen(val route: String) {

    /** 首页 */
    data object Home : Screen("home")

    /** 本地文件夹浏览页（本机存储 / 内存卡） */
    data object Files : Screen("files") {
        const val ARG_TREE_URI = "treeUri"
        val routeWithArgs = "$route/{$ARG_TREE_URI}"

        fun buildRoute(treeUri: String): String = "$route/${Uri.encode(treeUri)}"
    }

    /** LanDrop 局域网文件下载页 */
    data object LanDrop : Screen("landrop")

    /** GitHub 文件浏览下载页 */
    data object Github : Screen("github")

    /** 网页浏览器 */
    data object Browser : Screen("browser") {
        const val ARG_URL = "url"
        val routeWithArgs = "$route?$ARG_URL={$ARG_URL}"

        fun buildRoute(url: String = ""): String =
            if (url.isBlank()) route else "$route?$ARG_URL=${Uri.encode(url)}"
    }

    /** 下载管理 */
    data object DownloadList : Screen("download-list")

    /** 收藏夹 */
    data object Bookmarks : Screen("bookmarks")

    /** Markdown 阅读页（带参数） */
    data object Reader : Screen("reader") {
        const val ARG_FILE_PATH = "filePath"
        const val ARG_TITLE = "title"
        val routeWithArgs = "$route/{$ARG_FILE_PATH}?$ARG_TITLE={$ARG_TITLE}"

        /** 文档路径可能包含 /，需要 URL 编码后作为单个路由参数传递 */
        fun buildRoute(filePath: String, title: String = ""): String =
            "$route/${Uri.encode(filePath)}?$ARG_TITLE=${Uri.encode(title)}"
    }
}
