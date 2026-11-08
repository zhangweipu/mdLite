package com.example.mviapp.core.ui.display

/**
 * Markdown 阅读器（Compose 原生渲染）的电子墨水屏显示参数，纯 Kotlin 便于 JVM 单测。
 *
 * [ReaderDisplayCss] 只作用于 WebView（EPUB / HTML 阅读页）；Markdown 阅读页是
 * Compose 的 Text / Card / LazyColumn，必须另有一套等价策略：
 *
 * 1. 彩色水墨屏的彩色层分辨率远低于黑白层，任何彩色、灰色、半透明（含阴影、
 *    斑马纹、浅灰卡片、动态取色）都会被画到低分辨率彩色层或产生灰阶抖动噪点；
 * 2. 因此高对比模式只用纯黑 / 纯白，靠**黑色实线边框、加粗、反白**来区分
 *    代码块、表格、引用、行内代码与高亮，而不是靠底色深浅；
 * 3. 墨水屏刷新慢，fling 惯性滚动、overscroll 拉伸、进度条闪现、无限旋转的
 *    加载动画都会留下残影，一律关掉或改成静态。
 *
 * 普通模式返回全默认参数，渲染效果与改造前保持一致。
 */
object MdReaderDisplay {

    /** 高对比模式正文字号基准（sp）。普通模式是 16sp，这里放大抵消细笔画发虚 */
    const val EINK_BODY_SP = 18f
    const val EINK_BODY_LINE_SP = 27f
    const val EINK_SMALL_SP = 15f
    const val EINK_SMALL_LINE_SP = 22f
    private const val MIN_SCALE = 0.8f
    private const val MAX_SCALE = 1.6f

    data class Params(
        val highContrast: Boolean,
        /** 正文/代码等小字号元素的缩放倍率 */
        val textScale: Float,
        /** 标题缩放倍率：比正文缓和，避免大标题被撑出屏幕 */
        val headingScale: Float,
        /** 正文字重数值（400 = Normal，500 = Medium） */
        val bodyFontWeight: Int,
        val borderWidthDp: Float,
        val cornerRadiusDp: Float,
        /** 表格斑马纹：灰阶噪点源，高对比模式关闭 */
        val zebraStripes: Boolean,
        /** 斜体笔画太细容易断，改用加粗表示强调 */
        val italicAsBold: Boolean,
        /** 行内代码用黑底白字反白（灰底在墨水屏上等于看不见） */
        val inlineCodeInverted: Boolean,
        /** Card / Dialog 阴影 */
        val shadowsEnabled: Boolean,
        /** 进度条滑动时淡入淡出 */
        val progressBarBlink: Boolean,
        /** 无限循环的加载动画 */
        val infiniteLoadingAnimation: Boolean,
        /** 边缘拉伸回弹效果 */
        val overscrollEnabled: Boolean,
        /** 松手后的惯性滚动（彩墨屏上会产生大量中间帧 → 拖影） */
        val flingEnabled: Boolean,
        /** 高亮段落用黑边框 + 左侧黑竖条，而不是浅色底 */
        val outlineHighlight: Boolean,
    )

    fun resolve(eInkMode: Boolean, textScale: Float = 1f): Params {
        if (!eInkMode) {
            return Params(
                highContrast = false,
                textScale = 1f,
                headingScale = 1f,
                bodyFontWeight = 400,
                borderWidthDp = 1f,
                cornerRadiusDp = 8f,
                zebraStripes = true,
                italicAsBold = false,
                inlineCodeInverted = false,
                shadowsEnabled = true,
                progressBarBlink = true,
                infiniteLoadingAnimation = true,
                overscrollEnabled = true,
                flingEnabled = true,
                outlineHighlight = false,
            )
        }
        val scale = textScale.coerceIn(MIN_SCALE, MAX_SCALE)
        return Params(
            highContrast = true,
            textScale = scale,
            headingScale = 1f + (scale - 1f) * 0.6f,
            bodyFontWeight = 500,
            borderWidthDp = 1.5f,
            cornerRadiusDp = 0f,
            zebraStripes = false,
            italicAsBold = true,
            inlineCodeInverted = true,
            shadowsEnabled = false,
            progressBarBlink = false,
            infiniteLoadingAnimation = false,
            overscrollEnabled = false,
            flingEnabled = false,
            outlineHighlight = true,
        )
    }

    /** 正文 / 小字号在指定档位下的 sp 值，供单测与渲染层共用 */
    fun bodySp(textScale: Float): Float = EINK_BODY_SP * textScale.coerceIn(MIN_SCALE, MAX_SCALE)
    fun bodyLineSp(textScale: Float): Float = EINK_BODY_LINE_SP * textScale.coerceIn(MIN_SCALE, MAX_SCALE)
    fun smallSp(textScale: Float): Float = EINK_SMALL_SP * textScale.coerceIn(MIN_SCALE, MAX_SCALE)
    fun smallLineSp(textScale: Float): Float = EINK_SMALL_LINE_SP * textScale.coerceIn(MIN_SCALE, MAX_SCALE)
}
