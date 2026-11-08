package com.example.mviapp.core.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mviapp.core.ui.theme.LocalEInkMode

/** Material3 单行 TopAppBar 的标准高度（TopAppBarSmallTokens.ContainerHeight）。 */
private val APP_TOP_BAR_HEIGHT = 64.dp

/**
 * 全应用统一的顶部栏滚动控制器。
 *
 * 交互约定：内容向上滑（手指向上、页面下滚）时展开顶部栏，向下滑时收起；
 * 回到内容顶部时按同一手势方向处理，不与方向判定互相冲突。
 *
 * 与 Material3 自带的 enterAlwaysScrollBehavior 不同，这里用「方向累计 + 阈值」驱动
 * 一次性收起/展开动画：
 * - Compose 的 LazyColumn / verticalScroll 通过 [nestedScrollConnection] 上报滚动量
 * - 原生 WebView 不参与 Compose 嵌套滚动，由 [onWebViewScroll] 上报 scrollY
 * 两者共用同一套状态，因此所有页面的隐藏/显示手感一致。
 *
 * 只做整段收起/展开，不逐帧跟随手指位移：这样 WebView 的可用高度只会在
 * 180ms 动画内变化一次，而不是滚动的每一帧都触发网页重新布局。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Stable
class AppBarScrollController internal constructor(
    internal val state: TopAppBarState,
    private val onHiddenChange: (Boolean) -> Unit,
) {
    private var hidden = false

    // 当前方向：手指向上滑（看后面的内容）展开顶部栏，向下滑收起。
    // 到顶规则由 tracker 按同一方向自动适配，不会互相打架造成闪现。
    // 若想改回「上滑隐藏 / 下滑显示」，把这里的系数改成 1f 即可，其余逻辑无需改动。
    private val tracker = AppBarDirectionTracker(direction = -1f)

    /** 挂到 Scaffold 的 Modifier.nestedScroll 上，接收 Compose 滚动容器的滚动量 */
    val nestedScrollConnection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource,
        ): Offset {
            tracker.onComposeScroll(consumed.y, available.y)?.let(::updateHidden)
            return Offset.Zero
        }
    }

    /** 由 [ObservableWebView] 的 onScrollChanged 调用，scrollY 单位为 px */
    fun onWebViewScroll(scrollY: Int) {
        tracker.onWebViewScroll(scrollY)?.let(::updateHidden)
    }

    private fun updateHidden(value: Boolean) {
        if (hidden == value) return
        hidden = value
        onHiddenChange(value)
    }
}

/**
 * 记住一个顶部栏滚动控制器。页面只需创建一次，同时传给 [AppTopBar] 与 Scaffold 的
 * Modifier.nestedScroll。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberAppBarScrollController(): AppBarScrollController {
    val state = rememberTopAppBarState()
    var hidden by remember { mutableStateOf(false) }

    // heightOffsetLimit 由 TopAppBar 首次布局后写入（初始是 -Float.MAX_VALUE）。
    // 用单行 TopAppBar 的标准高度做兜底，避免首帧拿不到 limit 时动画目标短暂变成 0，
    // 造成第一次滑动时标题「先不动、再突然收起」的闪现。
    val density = LocalDensity.current
    // 墨水屏刷新慢，180ms 过渡会留下明显残影，直接跳到目标位置
    val eInkMode = LocalEInkMode.current
    val limit = state.heightOffsetLimit
    val collapseOffset = if (limit < 0f && limit > -10_000f) {
        limit
    } else {
        with(density) { -APP_TOP_BAR_HEIGHT.toPx() }
    }
    val target = if (hidden) collapseOffset else 0f
    val animationSpec: FiniteAnimationSpec<Float> = if (eInkMode) {
        snap()
    } else {
        tween(durationMillis = 180, easing = FastOutSlowInEasing)
    }
    val offset by animateFloatAsState(
        targetValue = target,
        animationSpec = animationSpec,
        label = "AppTopBarOffset",
    )
    SideEffect {
        if (state.heightOffset != offset) state.heightOffset = offset
    }

    return remember(state) {
        AppBarScrollController(state) { hidden = it }
    }
}

/**
 * 全应用统一的顶部栏。
 *
 * - 标题字号 16sp（原 TopAppBar 默认用主题的 titleLarge = 22sp Bold，偏大偏重）
 * - 左上角固定为菜单按钮，与「全局禁用抽屉滑动手势」配合使用
 * - [subtitle] 用于 EPUB 这类需要显示章节副标题的页面，字号 11sp
 * - 使用 pinnedScrollBehavior 只为拿到 [TopAppBarState] 的高度偏移能力，
 *   自身不接管 nested scroll，避免与 [AppBarScrollController] 重复消费滚动
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    onOpenDrawer: () -> Unit,
    controller: AppBarScrollController,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(state = controller.state),
        navigationIcon = {
            IconButton(onClick = onOpenDrawer) {
                Icon(
                    imageVector = Icons.Filled.Menu,
                    contentDescription = "打开菜单",
                )
            }
        },
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 16.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        actions = actions,
    )
}
