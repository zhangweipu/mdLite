package com.example.mviapp.core.md

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mviapp.core.ui.display.MdReaderDisplay
import com.example.mviapp.core.ui.theme.LocalEInkMode
import com.example.mviapp.core.ui.theme.LocalReaderTextScale

/**
 * Markdown 渲染层使用的完整样式集合。
 *
 * 普通模式完全沿用 [MaterialTheme] 的配色与字号；电子墨水屏模式切换到
 * [MdReaderDisplay.Params.highContrast] 的纯黑白结构：所有中间色、半透明、
 * 阴影、圆角被替换成黑色实线边框 / 加粗 / 反白，文字回到彩墨屏的
 * 高分辨率黑白层。
 */
data class MarkdownStyle(
    val params: MdReaderDisplay.Params,
    val colors: Colors,
    /** 段落、列表正文 */
    val bodyLarge: TextStyle,
    /** 表格、引用、代码等次要文字 */
    val bodyMedium: TextStyle,
    /** 标题：index 0 对应 h1，index 5 对应 h6 */
    val heading: List<TextStyle>,
    val code: TextStyle,
    val bullet: TextStyle,
    val borderWidth: Dp,
    val cornerRadius: Dp,
    val cardElevation: Dp,
    val highlightBarWidth: Dp,
) {
    /** 渲染层用到的所有颜色，普通模式取自 MaterialTheme，高对比模式只有黑白与透明 */
    data class Colors(
        val text: Color,
        val heading: Color,
        val subtle: Color,
        val border: Color,
        val surface: Color,
        val highlightBg: Color,
        val highlightMark: Color,
        val inlineCodeBg: Color,
        val inlineCodeText: Color,
        val link: Color,
        val bullet: Color,
        val tableHeaderBg: Color,
        val tableZebraBg: Color,
        val divider: Color,
        val codeText: Color,
        val quoteText: Color,
    )
}

@Composable
fun rememberMarkdownStyle(): MarkdownStyle {
    val eInkMode = LocalEInkMode.current
    val textScale = LocalReaderTextScale.current
    val typography = MaterialTheme.typography
    val scheme = MaterialTheme.colorScheme
    return remember(eInkMode, textScale, typography, scheme) {
        val p = MdReaderDisplay.resolve(eInkMode, textScale)
        val colors = if (p.highContrast) {
            MarkdownStyle.Colors(
                text = Color.Black,
                heading = Color.Black,
                subtle = Color.Black,
                border = Color.Black,
                surface = Color.White,
                highlightBg = Color.White,
                highlightMark = Color.Black,
                // 行内代码反白：小面积黑底白字在墨水屏上对比最强、边界最锐
                inlineCodeBg = Color.Black,
                inlineCodeText = Color.White,
                link = Color.Black,
                bullet = Color.Black,
                tableHeaderBg = Color.White,
                tableZebraBg = Color.Transparent,
                divider = Color.Black,
                codeText = Color.Black,
                quoteText = Color.Black,
            )
        } else {
            MarkdownStyle.Colors(
                text = scheme.onSurface,
                heading = scheme.primary,
                subtle = scheme.onSurfaceVariant,
                border = scheme.outlineVariant,
                surface = scheme.surfaceVariant,
                highlightBg = scheme.primaryContainer,
                highlightMark = Color.Transparent,
                inlineCodeBg = scheme.surfaceVariant,
                inlineCodeText = scheme.primary,
                link = scheme.primary,
                bullet = scheme.primary,
                tableHeaderBg = scheme.surfaceVariant,
                tableZebraBg = scheme.surfaceVariant.copy(alpha = 0.4f),
                divider = scheme.outlineVariant,
                codeText = scheme.onSurfaceVariant,
                quoteText = scheme.onSurfaceVariant,
            )
        }

        val bodyLarge: TextStyle
        val bodyMedium: TextStyle
        val code: TextStyle
        if (p.highContrast) {
            val weight = FontWeight(p.bodyFontWeight)
            bodyLarge = TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = weight,
                fontSize = MdReaderDisplay.bodySp(p.textScale).sp,
                lineHeight = MdReaderDisplay.bodyLineSp(p.textScale).sp,
                color = colors.text,
            )
            bodyMedium = TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = weight,
                fontSize = MdReaderDisplay.smallSp(p.textScale).sp,
                lineHeight = MdReaderDisplay.smallLineSp(p.textScale).sp,
                color = colors.text,
            )
            code = bodyMedium.copy(fontFamily = FontFamily.Monospace, color = colors.codeText)
        } else {
            bodyLarge = typography.bodyLarge
            bodyMedium = typography.bodyMedium
            code = typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                color = colors.codeText,
            )
        }

        val baseHeadings = listOf(
            typography.headlineMedium,
            typography.titleLarge,
            typography.titleMedium,
            typography.titleSmall,
            typography.titleSmall,
            typography.titleSmall,
        )
        val headings = if (p.highContrast) {
            baseHeadings.map { base ->
                base.copy(
                    fontSize = (base.fontSize.value * p.headingScale).sp,
                    lineHeight = (base.lineHeight.value * p.headingScale).sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.heading,
                )
            }
        } else {
            baseHeadings
        }

        MarkdownStyle(
            params = p,
            colors = colors,
            bodyLarge = bodyLarge,
            bodyMedium = bodyMedium,
            heading = headings,
            code = code,
            bullet = bodyLarge.copy(fontWeight = FontWeight.Bold, color = colors.bullet),
            borderWidth = p.borderWidthDp.dp,
            cornerRadius = p.cornerRadiusDp.dp,
            cardElevation = if (p.shadowsEnabled) 1.dp else 0.dp,
            highlightBarWidth = if (p.outlineHighlight) 4.dp else 0.dp,
        )
    }
}

/**
 * 墨水屏下关掉边缘拉伸回弹：Android 12+ 的 stretch 动画在刷新慢的水墨屏上
 * 会拖出一串残影，且发光/拉伸本身是彩色层上的灰阶。
 */
@OptIn(ExperimentalFoundationApi::class)
@Suppress("DEPRECATION")
@Composable
fun WithoutOverscroll(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalOverscrollConfiguration provides null,
        content = content,
    )
}
