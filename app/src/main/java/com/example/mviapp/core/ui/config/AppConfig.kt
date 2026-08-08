package com.example.mviapp.core.ui.config

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 全局 UI 统一配置
 *
 * 所有与布局相关的尺寸都集中在此管理，禁止在页面中散落硬编码数值。
 * 尺寸按屏幕宽度尺寸类（[AppWindowSizeClass]）区分，界面自动适配不同分辨率：
 * - Compact  （< 600dp）：手机竖屏
 * - Medium   （600~839dp）：小平板 / 折叠屏展开
 * - Expanded （>= 840dp）：平板 / 大屏
 */

/** 屏幕宽度尺寸类（与 Material Design 3 自适应规范一致） */
enum class AppWindowSizeClass {
    Compact,
    Medium,
    Expanded,
}

/** 统一尺寸配置 */
@Immutable
data class AppDimens(
    /** 页面级外边距 */
    val screenPadding: Dp,
    /** 卡片内部边距 */
    val cardPadding: Dp,
    /** 列表项之间的间距 */
    val itemSpacing: Dp,
    /** 同一卡片内元素之间的间距 */
    val contentGap: Dp,
    /** 卡片圆角 */
    val cardCornerRadius: Dp,
    /** 卡片阴影高度 */
    val cardElevation: Dp,
    /** 内容最大宽度，超宽屏时限制内容宽度以保证可读性 */
    val maxContentWidth: Dp,
)

/** 手机（Compact）：< 600dp */
val CompactDimens = AppDimens(
    screenPadding = 16.dp,
    cardPadding = 16.dp,
    itemSpacing = 12.dp,
    contentGap = 8.dp,
    cardCornerRadius = 12.dp,
    cardElevation = 2.dp,
    maxContentWidth = Dp.Infinity,
)

/** 小平板 / 折叠屏展开（Medium）：600~839dp */
val MediumDimens = AppDimens(
    screenPadding = 24.dp,
    cardPadding = 20.dp,
    itemSpacing = 16.dp,
    contentGap = 10.dp,
    cardCornerRadius = 16.dp,
    cardElevation = 3.dp,
    maxContentWidth = 840.dp,
)

/** 平板 / 大屏（Expanded）：>= 840dp */
val ExpandedDimens = AppDimens(
    screenPadding = 32.dp,
    cardPadding = 24.dp,
    itemSpacing = 20.dp,
    contentGap = 12.dp,
    cardCornerRadius = 20.dp,
    cardElevation = 4.dp,
    maxContentWidth = 840.dp,
)

/** 供 Composable 读取当前尺寸配置 */
val LocalAppDimens = staticCompositionLocalOf { CompactDimens }

/** 根据当前屏幕宽度计算尺寸类 */
@Composable
fun currentWindowSizeClass(): AppWindowSizeClass {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    return when {
        screenWidthDp >= 840 -> AppWindowSizeClass.Expanded
        screenWidthDp >= 600 -> AppWindowSizeClass.Medium
        else -> AppWindowSizeClass.Compact
    }
}

/**
 * 向整棵组合树提供统一的尺寸配置。
 *
 * 用法：在根 Composable 包裹整个界面，子组件通过 [LocalAppDimens] 读取配置。
 */
@Composable
fun AppLayoutConfig(
    windowSizeClass: AppWindowSizeClass = currentWindowSizeClass(),
    content: @Composable () -> Unit,
) {
    val dimens = when (windowSizeClass) {
        AppWindowSizeClass.Compact -> CompactDimens
        AppWindowSizeClass.Medium -> MediumDimens
        AppWindowSizeClass.Expanded -> ExpandedDimens
    }
    CompositionLocalProvider(
        LocalAppDimens provides dimens,
        content = content,
    )
}
