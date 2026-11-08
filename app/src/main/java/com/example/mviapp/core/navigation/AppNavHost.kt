package com.example.mviapp.core.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
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
                onBack = { navController.popBackStack() },
            )
        }

        // ====== LanDrop 下载页 ======
        composable(Screen.LanDrop.route) {
            LanDropScreen(
                onBack = { navController.popBackStack() },
            )
        }

        // ====== GitHub 文件页 ======
        composable(Screen.Github.route) {
            GithubScreen(
                onBack = { navController.popBackStack() },
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
                onBack = { navController.popBackStack() },
                onNavigateToDownloadList = { navController.navigate(Screen.DownloadList.route) },
                onNavigateToBookmarks = { navController.navigate(Screen.Bookmarks.route) },
                initialUrl = initialUrl,
            )
        }

        // ====== 下载管理 ======
        composable(Screen.DownloadList.route) {
            DownloadListScreen(
                onBack = { navController.popBackStack() },
            )
        }

        // ====== 收藏夹 ======
        composable(Screen.Bookmarks.route) {
            BookmarksScreen(
                onBack = { navController.popBackStack() },
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
                onBack = { navController.popBackStack() },
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
            // EPUB 文件跳转到专用阅读器
            if (filePath.isEpubFile()) {
                EpubReaderScreen(
                    filePath = filePath,
                    fileTitle = fileTitle,
                    onBack = { navController.popBackStack() },
                )
            } else if (filePath.isPdfFile()) {
                PdfReaderScreen(
                    filePath = filePath,
                    fileTitle = fileTitle,
                    onBack = { navController.popBackStack() },
                )
            } else if (filePath.isHtmlFile()) {
                HtmlReaderScreen(
                    filePath = filePath,
                    fileTitle = fileTitle,
                    onBack = { navController.popBackStack() },
                )
            } else {
                MdReaderScreen(
                    filePath = filePath,
                    fileTitle = fileTitle,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
