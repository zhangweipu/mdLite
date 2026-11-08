package com.example.mviapp.core.navigation

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.example.mviapp.core.common.isEpubFile
import com.example.mviapp.core.common.isHtmlFile
import com.example.mviapp.core.common.isPdfFile
import com.example.mviapp.feature.files.FilesScreen
import com.example.mviapp.feature.github.GithubScreen
import com.example.mviapp.feature.home.HomeScreen
import com.example.mviapp.feature.html.HtmlReaderScreen
import com.example.mviapp.feature.landrop.LanDropScreen
import com.example.mviapp.feature.pdf.PdfReaderScreen
import com.example.mviapp.feature.reader.MdReaderScreen
import com.example.mviapp.feature.bookmarks.BookmarksScreen
import com.example.mviapp.feature.browser.BrowserScreen
import com.example.mviapp.feature.download.DownloadListScreen
import com.example.mviapp.feature.epub.EpubReaderScreen

/**
 * 应用导航图
 *
 * 集中管理所有页面的路由注册。
 * 新增功能页面时：
 * 1. 在 [Screen] 中定义路由
 * 2. 在此处的 NavHost 中添加对应的 composable
 */
@Composable
fun AppNavHost(
    navController: NavHostController,
    onOpenDrawer: () -> Unit,
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
    ) {
        // ====== 首页 ======
        composable(Screen.Home.route) {
            HomeScreen(
                onOpenDrawer = onOpenDrawer,
                onNavigateToReader = { filePath, title ->
                    navController.navigate(Screen.Reader.buildRoute(filePath, title))
                },
            )
        }

        // ====== 本地文件夹浏览页 ======
        composable(
            route = Screen.Files.routeWithArgs,
            arguments = listOf(
                navArgument(Screen.Files.ARG_TREE_URI) {
                    type = NavType.StringType
                }
            ),
        ) {
            FilesScreen(
                onOpenFile = { uri, title ->
                    navController.navigate(Screen.Reader.buildRoute(uri, title))
                },
                onOpenDirectory = { uri ->
                    navController.navigate(Screen.Files.buildRoute(uri))
                },
                onOpenDrawer = onOpenDrawer,
            )
        }

        // ====== LanDrop 下载页 ======
        composable(Screen.LanDrop.route) {
            LanDropScreen(
                onOpenDrawer = onOpenDrawer,
            )
        }

        // ====== GitHub 文件页 ======
        composable(Screen.Github.route) {
            GithubScreen(
                onOpenDrawer = onOpenDrawer,
            )
        }

        // ====== 网页浏览器 ======
        composable(
            route = Screen.Browser.routeWithArgs,
            arguments = listOf(
                navArgument(Screen.Browser.ARG_URL) {
                    type = NavType.StringType
                    defaultValue = ""
                }
            ),
        ) { backStackEntry ->
            val initialUrl = backStackEntry.arguments
                ?.getString(Screen.Browser.ARG_URL)
                .orEmpty()
            BrowserScreen(
                onOpenDrawer = onOpenDrawer,
                onNavigateToDownloadList = { navController.navigate(Screen.DownloadList.route) },
                onNavigateToBookmarks = { navController.navigate(Screen.Bookmarks.route) },
                initialUrl = initialUrl,
            )
        }

        // ====== 下载管理 ======
        composable(Screen.DownloadList.route) {
            DownloadListScreen(
                onOpenDrawer = onOpenDrawer,
                onOpenFile = { path, title ->
                    navController.navigate(Screen.Reader.buildRoute(path, title))
                },
            )
        }

        // ====== 收藏夹 ======
        composable(Screen.Bookmarks.route) {
            BookmarksScreen(
                onOpenDrawer = onOpenDrawer,
                onOpenUrl = { url ->
                    // 从收藏夹打开 → 跳转到浏览器页面并加载该 URL
                    navController.navigate(Screen.Browser.buildRoute(url)) {
                        popUpTo(Screen.Browser.route) { inclusive = true }
                    }
                },
            )
        }

        // ====== EPUB 阅读器 ======
        composable(
            route = Screen.EpubReader.routeWithArgs,
            arguments = listOf(
                navArgument(Screen.EpubReader.ARG_FILE_PATH) {
                    type = NavType.StringType
                },
                navArgument(Screen.EpubReader.ARG_TITLE) {
                    type = NavType.StringType
                    defaultValue = ""
                }
            ),
        ) { backStackEntry ->
            val filePath = backStackEntry.arguments
                ?.getString(Screen.EpubReader.ARG_FILE_PATH)
                .orEmpty()
            val title = backStackEntry.arguments
                ?.getString(Screen.EpubReader.ARG_TITLE)
                .orEmpty()
            EpubReaderScreen(
                filePath = filePath,
                fileTitle = title,
                onOpenDrawer = onOpenDrawer,
            )
        }

        // ====== Markdown 阅读页 ======
        composable(
            route = Screen.Reader.routeWithArgs,
            arguments = listOf(
                navArgument(Screen.Reader.ARG_FILE_PATH) {
                    type = NavType.StringType
                },
                navArgument(Screen.Reader.ARG_TITLE) {
                    type = NavType.StringType
                    defaultValue = ""
                }
            ),
        ) { backStackEntry ->
            val filePath = backStackEntry.arguments
                ?.getString(Screen.Reader.ARG_FILE_PATH)
                .orEmpty()
            val title = backStackEntry.arguments
                ?.getString(Screen.Reader.ARG_TITLE)
                .orEmpty()
            val fileTitle = title.ifBlank {
                Uri.decode(filePath).substringAfterLast('/').removeSuffix(".md")
            }
            // 文档类型分流：路径 + 文件名 + MIME 三重判断。
            // SAF 返回的 content URI 可能以数字 ID 结尾（不含扩展名），只看路径会把
            // EPUB/PDF 误当成 Markdown，用二进制当文本读，表现为「文件打不开」。
            val context = LocalContext.current
            val docKind = remember(filePath, fileTitle) {
                resolveDocKind(context, filePath, fileTitle)
            }
            when (docKind) {
                DocKind.EPUB -> EpubReaderScreen(
                    filePath = filePath,
                    fileTitle = fileTitle,
                    onOpenDrawer = onOpenDrawer,
                )
                DocKind.PDF -> PdfReaderScreen(
                    filePath = filePath,
                    fileTitle = fileTitle,
                    onOpenDrawer = onOpenDrawer,
                )
                DocKind.HTML -> HtmlReaderScreen(
                    filePath = filePath,
                    fileTitle = fileTitle,
                    onOpenDrawer = onOpenDrawer,
                )
                else -> MdReaderScreen(
                    filePath = filePath,
                    fileTitle = fileTitle,
                    onOpenDrawer = onOpenDrawer,
                )
            }
        }
    }
}

/** 阅读器页面类型 */
private enum class DocKind { EPUB, PDF, HTML, TEXT }

/** 文档类型解析：先按路径和文件名，仍无法确定时再查 content provider 的 MIME */
private fun resolveDocKind(context: Context, path: String, title: String): DocKind = when {
    path.isEpubFile() || title.isEpubFile() ||
        isMime(context, path, "application/epub+zip") -> DocKind.EPUB
    path.isPdfFile() || title.isPdfFile() -> DocKind.PDF
    path.isHtmlFile() || title.isHtmlFile() -> DocKind.HTML
    else -> DocKind.TEXT
}

private fun isMime(context: Context, path: String, expected: String): Boolean =
    path.startsWith("content://", ignoreCase = true) && runCatching {
        context.contentResolver.getType(Uri.parse(path))
            ?.equals(expected, ignoreCase = true) == true
    }.getOrDefault(false)
