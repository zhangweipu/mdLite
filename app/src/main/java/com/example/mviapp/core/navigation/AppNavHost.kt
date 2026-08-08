package com.example.mviapp.core.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.example.mviapp.feature.files.FilesScreen
import com.example.mviapp.feature.home.HomeScreen
import com.example.mviapp.feature.reader.MdReaderScreen

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
            MdReaderScreen(
                filePath = filePath,
                fileTitle = fileTitle,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
