package com.example.mviapp.core.ui.display

/**
 * 电子墨水屏（含彩色水墨屏）阅读样式，纯 Kotlin 生成，便于 JVM 单测。
 *
 * 彩色水墨屏有两个物理层：黑白层约 300ppi，彩色滤光片层往往只有 100~150ppi。
 * 因此**任何带颜色的文字（蓝色链接、红色行内代码、语法高亮、灰色注释）
 * 都会被渲染在低分辨率彩色层上**，看起来比黑字明显发虚、发脏。
 *
 * 这里的核心策略不是简单「加深颜色」，而是：
 * 1. 所有文字强制纯黑 `#000`，让它走高分辨率黑白层；背景强制纯白；
 * 2. 去掉中间灰、半透明、阴影、圆角浅底，这些在灰阶抖动下会变成噪点；
 * 3. 用 `-webkit-text-stroke` 把笔画描实，抵消灰度抗锯齿造成的细笔画变淡；
 * 4. 字号放大（用 CSS px，不用 WebView textZoom，避免非整数缩放插值）；
 * 5. 禁用动画/过渡/平滑滚动，减少彩墨屏刷新慢带来的残影。
 *
 * 彩色水墨屏默认**保留图片彩色**（不加 filter）；只有黑白屏用户才需要
 * [monochrome] = true 把图片转灰度。
 *
 * 样式必须注入在书籍自带 CSS 之后，所以由渲染层替换
 * [com.example.mviapp.core.epub.EpubHtmlFormatter.THEME_PLACEHOLDER] 时追加，
 * 而不是改 formatter 本身——这样章节缓存不会因切换主题/开关而失效。
 */
object ReaderDisplayCss {

    /** E-ink 模式下的正文基准字号（px），普通模式是 18px */
    private const val BASE_FONT_PX = 20f

    fun highContrast(textScale: Float = 1f, monochrome: Boolean = false): String {
        val scale = textScale.coerceIn(0.8f, 1.6f)
        val fontPx = formatPx(BASE_FONT_PX * scale)
        val imageFilter = if (monochrome) {
            "img, svg, image, video, canvas { filter: grayscale(1) contrast(1.15) !important; }"
        } else {
            // 彩色水墨屏：保持原图，不额外栅格化，避免 filter 让图片变糊
            ""
        }
        return """
            /* ===== 电子墨水屏高对比模式 ===== */
            *, *::before, *::after {
                text-shadow: none !important;
                box-shadow: none !important;
                background-image: none !important;
                animation: none !important;
                transition: none !important;
                -webkit-font-smoothing: none !important;
            }
            html, body {
                background: #FFFFFF !important;
                background-color: #FFFFFF !important;
                color: #000000 !important;
                font-family: "Noto Sans CJK SC", "Noto Sans SC", "Source Han Sans SC",
                             Roboto, "Droid Sans", sans-serif !important;
                font-size: ${fontPx}px !important;
                line-height: 1.85 !important;
                font-weight: 500 !important;
                letter-spacing: 0 !important;
                -webkit-text-size-adjust: 100% !important;
                scroll-behavior: auto !important;
            }
            /* 放在最后、用 body * 提高特异性，压过书籍自带的高优先级彩色规则 */
            html body, html body * {
                color: #000000 !important;
                background-color: transparent !important;
                border-color: #000000 !important;
                -webkit-text-stroke: 0.3px #000000 !important;
            }
            /* 同特异性、写在后面，把 body 从上面的 transparent 改回纯白 */
            html, html body { background-color: #FFFFFF !important; }
            /* 下列规则统一带 html body 前缀，特异性高于书籍自带的 !important 样式 */
            html body h1, html body h2, html body h3, html body h4,
            html body h5, html body h6, html body strong, html body b, html body th {
                font-weight: 700 !important;
                -webkit-text-stroke: 0.35px #000000 !important;
            }
            /* 斜体笔画更细，在墨水屏上容易断；改用加粗表示强调 */
            html body em, html body i, html body cite, html body dfn {
                font-style: normal !important;
                font-weight: 600 !important;
            }
            html body a, html body a * {
                text-decoration: underline !important;
                font-weight: 600 !important;
            }
            /* 彩色语法高亮 / 红色行内代码统一压成纯黑，回到高分辨率黑白层 */
            html body pre, html body pre *, html body code, html body code *,
            html body kbd, html body samp, html body tt, html body font {
                -webkit-text-stroke: 0.2px #000000 !important;
            }
            html body pre {
                background-color: #FFFFFF !important;
                border: 2px solid #000000 !important;
                border-radius: 0 !important;
                font-size: 0.86em !important;
            }
            html body code, html body kbd, html body samp, html body tt {
                background-color: #FFFFFF !important;
                border: 1px solid #000000 !important;
                border-radius: 0 !important;
                font-weight: 600 !important;
            }
            html body pre code, html body pre kbd, html body pre samp, html body pre tt {
                border: 0 !important;
                background-color: transparent !important;
                font-weight: 500 !important;
            }
            html body pre[data-lang]::before, html body pre.mvi-code-wide::after {
                color: #000000 !important;
                font-weight: 700 !important;
            }
            html body table, html body th, html body td {
                border: 1px solid #000000 !important;
                border-collapse: collapse !important;
            }
            html body blockquote { border-left: 3px solid #000000 !important; }
            html body hr { border-top: 1px solid #000000 !important; }
            html body mark, html body ins {
                font-weight: 700 !important;
                text-decoration: underline !important;
            }
            html body img, html body svg, html body image,
            html body video, html body canvas {
                -webkit-text-stroke: 0 !important;
            }
            $imageFilter
            /* ===== 电子墨水屏高对比模式结束 ===== */
        """.trimIndent()
    }

    /**
     * 把样式注入到一段 HTML（供 HTML 阅读页使用）。
     * EPUB 阅读页不走这里，而是把 CSS 追加到 formatter 预留的主题占位符后面。
     */
    fun injectInto(html: String, css: String): String {
        if (css.isBlank()) return html
        // 书籍/网页自带的 width=1200 之类视口会让 WebView 做非整数缩放，
        // 在墨水屏上直接表现为字边缘发虚，统一换成 1:1 的设备宽度。
        val viewport = "<meta name=\"viewport\" " +
            "content=\"width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no\">"
        val cleaned = VIEWPORT_META.replace(html, "")
        val head = "$viewport\n<style id=\"mvi-eink-style\">\n$css\n</style>"
        if (HEAD_CLOSE.containsMatchIn(cleaned)) {
            var injected = false
            return HEAD_CLOSE.replace(cleaned) { match ->
                if (injected) {
                    match.value
                } else {
                    injected = true
                    "$head\n</head>"
                }
            }
        }
        return "<html><head><meta charset=\"UTF-8\">$head</head><body>$cleaned</body></html>"
    }

    private fun formatPx(value: Float): String {
        val tenths = (value * 10f).let { kotlin.math.round(it) }.toInt()
        return if (tenths % 10 == 0) (tenths / 10).toString() else "${tenths / 10}.${tenths % 10}"
    }

    private val HEAD_CLOSE = Regex("</head>", RegexOption.IGNORE_CASE)
    private val VIEWPORT_META = Regex(
        """<meta\s+[^>]*name\s*=\s*["']?viewport["']?[^>]*>""",
        RegexOption.IGNORE_CASE,
    )
}
