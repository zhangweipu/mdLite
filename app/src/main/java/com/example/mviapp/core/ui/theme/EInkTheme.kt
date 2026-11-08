package com.example.mviapp.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

/**
 * 当前是否处于电子墨水屏高对比模式。
 * 阅读页据此决定要不要注入 [com.example.mviapp.core.ui.display.ReaderDisplayCss]，
 * 顶部栏据此取消收起动画（墨水屏刷新慢，动画会留残影）。
 */
val LocalEInkMode = staticCompositionLocalOf { false }

/** 阅读字号倍率，1.0 = 标准 */
val LocalReaderTextScale = staticCompositionLocalOf { 1f }

/** 黑白墨水屏：图片也转灰度；彩色水墨屏保持 false 以保留彩色图片 */
val LocalEInkMonochrome = staticCompositionLocalOf { false }

/**
 * 墨水屏专用配色：整个应用只用纯黑 / 纯白两极。
 *
 * Material3 默认的 `onSurfaceVariant = #49454F`、`surfaceVariant = #E7E0EC`、
 * 紫色 primary 在彩色水墨屏上会被渲染到低分辨率彩色层，且灰阶抖动后发脏发虚。
 * 统一压成黑白，既提高对比度，也让文字回到 300ppi 黑白层。
 */
internal val EInkColorScheme = lightColorScheme(
    primary = Color.Black,
    onPrimary = Color.White,
    primaryContainer = Color.White,
    onPrimaryContainer = Color.Black,
    secondary = Color.Black,
    onSecondary = Color.White,
    secondaryContainer = Color.White,
    onSecondaryContainer = Color.Black,
    tertiary = Color.Black,
    onTertiary = Color.White,
    tertiaryContainer = Color.White,
    onTertiaryContainer = Color.Black,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    surfaceVariant = Color.White,
    onSurfaceVariant = Color.Black,
    surfaceTint = Color.White,
    inversePrimary = Color.White,
    inverseSurface = Color.Black,
    inverseOnSurface = Color.White,
    error = Color.Black,
    onError = Color.White,
    errorContainer = Color.White,
    onErrorContainer = Color.Black,
    outline = Color.Black,
    outlineVariant = Color.Black,
    scrim = Color.Black,
)

/**
 * 墨水屏字重整体加一档，抵消灰度抗锯齿让细笔画变淡的问题。
 * 不改字号：Compose 列表项高度依赖现有字号，放大容易把布局挤坏；
 * 正文阅读字号由 WebView 里的 CSS 单独控制。
 */
internal val EInkTypography: Typography = AppTypography.copy(
    displayLarge = AppTypography.displayLarge.copy(fontWeight = FontWeight.Medium),
    headlineMedium = AppTypography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = AppTypography.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = AppTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = AppTypography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = AppTypography.bodyLarge.copy(fontWeight = FontWeight.Medium),
    bodyMedium = AppTypography.bodyMedium.copy(fontWeight = FontWeight.Medium),
    bodySmall = AppTypography.bodySmall.copy(fontWeight = FontWeight.Medium),
    labelLarge = AppTypography.labelLarge.copy(fontWeight = FontWeight.Bold),
    labelMedium = AppTypography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
    labelSmall = AppTypography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
)
