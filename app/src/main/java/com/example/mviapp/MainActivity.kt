package com.example.mviapp

import android.content.Intent
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.mviapp.core.common.isSupportedDocFile
import com.example.mviapp.core.navigation.AppNavHost
import com.example.mviapp.core.navigation.Screen
import com.example.mviapp.core.history.ReadingHistoryRepository
import com.example.mviapp.core.ui.config.AppLayoutConfig
import com.example.mviapp.core.ui.display.DisplaySettings
import com.example.mviapp.core.ui.display.DisplayState
import com.example.mviapp.core.ui.display.ReaderTextScale
import com.example.mviapp.core.ui.theme.MviAppTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 应用入口 Activity
 *
 * @AndroidEntryPoint 启用 Hilt 注入，使 Activity 内可使用 @Inject。
 * Compose 内容在此设置，通过 NavHost 管理页面导航。
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** 批量导入时写入“最近阅读”历史 */
    @Inject
    lateinit var historyRepository: ReadingHistoryRepository

    /** 电子墨水屏高对比模式等显示设置 */
    @Inject
    lateinit var displaySettings: DisplaySettings

    /** 其他应用通过“打开方式”传入的待打开文件 URI（ACTION_VIEW） */
    private var pendingOpenUri by mutableStateOf<Uri?>(null)
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 冷启动：浏览器/微信/QQ/文件管理器选择“用本应用打开”时记录文件
        if (savedInstanceState == null) {
            pendingOpenUri = intent?.data
        }
        setContent {
            val display by displaySettings.state.collectAsStateWithLifecycle()
            // 统一 UI 配置：根据屏幕分辨率注入对应的尺寸配置
            AppLayoutConfig {
                MviAppTheme(
                    eInkMode = display.enabled,
                    readerTextScale = display.textScale.scale,
                    monochrome = display.monochrome,
                ) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        val navController = rememberNavController()
                        val drawerState = rememberDrawerState(DrawerValue.Closed)
                        val scope = rememberCoroutineScope()
                        val context = LocalContext.current

                        // 系统文件夹选择器：本机存储 / 内存卡都由此授权并浏览
                        val folderPicker = rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.OpenDocumentTree(),
                        ) { uri ->
                            if (uri != null) {
                                // 持久化读取权限，应用重启后仍可访问所选文件夹
                                runCatching {
                                    context.contentResolver.takePersistableUriPermission(
                                        uri,
                                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                                    )
                                }
                                navController.navigate(Screen.Files.buildRoute(uri.toString()))
                            }
                        }

                        // 单个文件选择器：部分机型的“文件夹选择器”不显示其他软件下载的文件，
                        // 直接选文件可绕过该限制
                        val filePicker = rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.OpenDocument(),
                        ) { uri ->
                            if (uri != null) {
                                val name = uri.displayName(context) ?: uri.fallbackName()
                                if (name.isSupportedDocFile()) {
                                    runCatching {
                                        context.contentResolver.takePersistableUriPermission(
                                            uri,
                                            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                                        )
                                    }
                                    navController.navigate(Screen.Reader.buildRoute(uri.toString(), name))
                                } else {
                                    Toast.makeText(context, "请选择文档文件", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }

                        // 批量导入：一次选择多个 md 文件，直接加入“最近阅读”
                        val batchImportLauncher = rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.OpenMultipleDocuments(),
                        ) { uris ->
                            if (uris.isNotEmpty()) {
                                scope.launch {
                                    var imported = 0
                                    uris.forEach { uri ->
                                        val name = uri.displayName(context) ?: uri.fallbackName()
                                        if (name.isSupportedDocFile()) {
                                            runCatching {
                                                context.contentResolver.takePersistableUriPermission(
                                                    uri,
                                                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                                                )
                                            }
                                            historyRepository.add(title = name, source = uri.toString())
                                            imported++
                                        }
                                    }
                                    Toast.makeText(
                                        context,
                                        if (imported > 0) "已导入 $imported 个文档" else "未选择文档文件",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                                // 重建首页，让新导入的文件立即显示
                                navController.navigate(Screen.Home.route) {
                                    popUpTo(Screen.Home.route) { inclusive = true }
                                    launchSingleTop = true
                                }
                            }
                        }

                        // 处理“打开方式”唤起的文件：授权后直接进入阅读页
                        val openUri = pendingOpenUri
                        LaunchedEffect(openUri) {
                            if (openUri != null) {
                                runCatching {
                                    contentResolver.takePersistableUriPermission(
                                        openUri,
                                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                                    )
                                }
                                val title = openUri.displayName(this@MainActivity) ?: openUri.fallbackName()
                                navController.navigate(Screen.Reader.buildRoute(openUri.toString(), title)) {
                                    launchSingleTop = true
                                }
                                pendingOpenUri = null
                            }
                        }

                        val currentBackStackEntry by navController.currentBackStackEntryAsState()
                        val currentRoute = currentBackStackEntry?.destination?.route

                        // 全局禁用"边缘滑动划出抽屉"：所有页面统一通过左上角的
                        // 菜单按钮打开，避免与页面滚动、翻页、WebView 手势相互干扰。
                        // 抽屉打开时保留手势，确保点击遮罩 / 反向拖动 / 返回键仍能关闭菜单。
                        val drawerGesturesEnabled = drawerState.currentValue == DrawerValue.Open

                        // 切换页面时收起抽屉，避免抽屉动画与新页面首帧同时渲染。
                        LaunchedEffect(currentRoute) {
                            drawerState.close()
                        }

                        ModalNavigationDrawer(
                            drawerState = drawerState,
                            gesturesEnabled = drawerGesturesEnabled,
                            drawerContent = {
                                AppDrawerContent(
                                    currentRoute = currentRoute,
                                    onHomeClick = {
                                        scope.launch { drawerState.close() }
                                        navController.navigate(Screen.Home.route) {
                                            popUpTo(Screen.Home.route) { inclusive = false }
                                            launchSingleTop = true
                                        }
                                    },
                                    onSelectStorage = {
                                        scope.launch { drawerState.close() }
                                        folderPicker.launch(null)
                                    },
                                    onOpenFile = {
                                        scope.launch { drawerState.close() }
                                        filePicker.launch(arrayOf("*/*"))
                                    },
                                    onBatchImport = {
                                        scope.launch { drawerState.close() }
                                        batchImportLauncher.launch(arrayOf("*/*"))
                                    },
                                    onLanDrop = {
                                        scope.launch { drawerState.close() }
                                        navController.navigate(Screen.LanDrop.route)
                                    },
                                    onGithub = {
                                        scope.launch { drawerState.close() }
                                        navController.navigate(Screen.Github.route)
                                    },
                                    onBrowser = {
                                        scope.launch { drawerState.close() }
                                        navController.navigate(Screen.Browser.route)
                                    },
                                    onDownloadList = {
                                        scope.launch { drawerState.close() }
                                        navController.navigate(Screen.DownloadList.route)
                                    },
                                    display = display,
                                    onEInkToggle = displaySettings::setEnabled,
                                    onTextScaleChange = displaySettings::setTextScale,
                                    onMonochromeChange = displaySettings::setMonochrome,
                                )
                            },
                        ) {
                            AppNavHost(
                                navController = navController,
                                onOpenDrawer = { scope.launch { drawerState.open() } },
                            )
                        }
                    }
                }
            }
        }
    }

    /** 应用已在运行时再次收到“打开方式”请求 */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingOpenUri = intent.data
    }
}

/**
 * 抽屉菜单内容
 *
 * - 首页：内置文档与最近阅读
 * - 本机存储 / 内存卡：通过系统选择器授权后浏览文件夹，选择 md 文件
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppDrawerContent(
    currentRoute: String?,
    onHomeClick: () -> Unit,
    onSelectStorage: () -> Unit,
    onOpenFile: () -> Unit,
    onBatchImport: () -> Unit,
    onLanDrop: () -> Unit,
    onGithub: () -> Unit,
    onBrowser: () -> Unit,
    onDownloadList: () -> Unit,
    display: DisplayState,
    onEInkToggle: (Boolean) -> Unit,
    onTextScaleChange: (ReaderTextScale) -> Unit,
    onMonochromeChange: (Boolean) -> Unit,
) {
    ModalDrawerSheet {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            Column(modifier = Modifier.padding(horizontal = 28.dp, vertical = 20.dp)) {
                Text(
                    text = "mdLite",
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Markdown 阅读器",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

        NavigationDrawerItem(
            label = { Text("首页") },
            icon = { Icon(imageVector = Icons.Default.Home, contentDescription = null) },
            selected = currentRoute == Screen.Home.route,
            onClick = onHomeClick,
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
        )
        NavigationDrawerItem(
            label = { Text("本机存储") },
            icon = { Icon(imageVector = Icons.Default.Storage, contentDescription = null) },
            selected = false,
            onClick = onSelectStorage,
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
        )
        NavigationDrawerItem(
            label = { Text("内存卡") },
            icon = { Icon(imageVector = Icons.Default.SdCard, contentDescription = null) },
            selected = false,
            onClick = onSelectStorage,
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
        )
        NavigationDrawerItem(
            label = { Text("打开文档") },
            icon = { Icon(imageVector = Icons.Default.Description, contentDescription = null) },
            selected = false,
            onClick = onOpenFile,
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
        )
        NavigationDrawerItem(
            label = { Text("批量导入文档") },
            icon = { Icon(imageVector = Icons.Default.Add, contentDescription = null) },
            selected = false,
            onClick = onBatchImport,
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
        )
        NavigationDrawerItem(
            label = { Text("LanDrop 下载") },
            icon = { Icon(imageVector = Icons.Default.Download, contentDescription = null) },
            selected = false,
            onClick = onLanDrop,
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
        )
        NavigationDrawerItem(
            label = { Text("GitHub 文件") },
            icon = { Icon(imageVector = Icons.Default.Code, contentDescription = null) },
            selected = currentRoute == Screen.Github.route,
            onClick = onGithub,
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
        )
        NavigationDrawerItem(
            label = { Text("网页浏览器") },
            icon = { Icon(imageVector = Icons.Default.Public, contentDescription = null) },
            selected = currentRoute?.startsWith(Screen.Browser.route) == true,
            onClick = onBrowser,
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
        )
            NavigationDrawerItem(
                label = { Text("下载管理") },
                icon = { Icon(imageVector = Icons.Default.Download, contentDescription = null) },
                selected = currentRoute == Screen.DownloadList.route,
                onClick = onDownloadList,
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            DisplaySettingsSection(
                display = display,
                onEInkToggle = onEInkToggle,
                onTextScaleChange = onTextScaleChange,
                onMonochromeChange = onMonochromeChange,
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * 电子墨水屏显示设置。
 *
 * 彩色水墨屏的彩色滤光片层分辨率远低于黑白层，文字统一成纯黑后能回到
 * 300ppi 黑白层，是最直接有效的清晰度提升；图片是否转灰度单独可选。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DisplaySettingsSection(
    display: DisplayState,
    onEInkToggle: (Boolean) -> Unit,
    onTextScaleChange: (ReaderTextScale) -> Unit,
    onMonochromeChange: (Boolean) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 28.dp)) {
        Text(
            text = "显示",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "电子墨水屏模式",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = if (display.isEInkDevice) {
                        "已识别为墨水屏设备"
                    } else {
                        "纯黑文字 · 加实笔画 · 减少残影"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = display.enabled, onCheckedChange = onEInkToggle)
        }

        if (display.enabled) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "阅读字号",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReaderTextScale.values().forEach { scale ->
                    FilterChip(
                        selected = display.textScale == scale,
                        onClick = { onTextScaleChange(scale) },
                        label = { Text(scale.label) },
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "黑白屏（图片转灰度）",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "彩色水墨屏请保持关闭，以保留彩色图片",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = display.monochrome, onCheckedChange = onMonochromeChange)
            }
        }
    }
}

/** 查询系统提供的文件显示名（适用于 content:// 文件） */
private fun Uri.displayName(context: Context): String? {
    var name: String? = null
    runCatching {
        context.contentResolver.query(
            this,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) name = cursor.getString(index)
            }
        }
    }
    return name
}

/** 显示名查询失败时的兜底解析 */
private fun Uri.fallbackName(): String {
    val decoded = Uri.decode(lastPathSegment.orEmpty())
    return decoded
        .substringAfterLast('/')
        .substringAfterLast(':')
        .ifBlank { "Markdown 文档" }
}
