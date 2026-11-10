package com.example.mviapp.core.epub

/**
 * 把 EPUB 章节 XHTML 整理成「手机竖屏阅读」用的 HTML（纯 Kotlin，可 JVM 单测）。
 *
 * 直接把原始 XHTML 丢给 WebView 会出现两类顽疾：
 *
 * 1. 图片乱：书里常见 `width="800"`、内联 `height:100%`、CSS `float`、
 *    `position` 定宽容器，以及 `/OEBPS/Images/x.jpg` 这类以根开头的绝对路径
 *    （file:// baseUrl 下会解析成 `file:///OEBPS/...` → 破图）。
 * 2. 能横滑：不少 EPUB 自带 `<meta name="viewport" content="width=1200">` 或
 *    固定像素宽度容器，配合 `useWideViewPort` 后内容宽于屏幕。
 *
 * 这里统一做：编码规范化 → 摘掉书籍自带 viewport → 重写资源 URL →
 * 注入高优先级阅读样式（含 `overflow-x: hidden`）。固定版式（漫画/画册）
 * 单独识别并保留其设计宽度，交由 WebView 整体缩放，避免把页面压扁。
 *
 * 主题配色不写死在 HTML 里：样式中留 [THEME_PLACEHOLDER] 占位，
 * 由渲染层按当前明暗主题替换，这样章节缓存不必因换主题而失效。
 */
object EpubHtmlFormatter {

    /** 渲染层把当前主题的背景/文字/链接色 CSS 替换到这个占位符 */
    const val THEME_PLACEHOLDER = "/*MVI_READER_THEME*/"

    /** 章节排版结果 */
    data class Result(
        val html: String,
        /** 固定版式（pre-paginated）：需要 WebView 开启 overview 缩放 */
        val fixedLayout: Boolean,
    )

    fun format(
        rawHtml: String,
        chapterPath: String,
        /** 已解压资源的 ZIP 根相对路径集合，用于把找不到的图片按文件名兜底 */
        assets: Set<String> = emptySet(),
    ): Result {
        var html = normalizeCharset(rawHtml)
        val viewportContents = VIEWPORT_META.findAll(html)
            .mapNotNull { META_CONTENT.find(it.value)?.groupValues?.get(1) }
            .toList()

        val declaredWidth = viewportContents.firstNotNullOfOrNull { content ->
            VIEWPORT_WIDTH.find(content)?.groupValues?.get(1)?.toIntOrNull()
        }
        // 只认 <meta> 标签里的 pre-paginated 声明；全文 contains 会被
        // 正文中讨论该概念的文字（如 EPUB 教程章节）误触发
        val prePaginated = PRE_PAGINATED_META.containsMatchIn(html)
        val imageDriven = isImageDriven(html)
        // 只有真正的固定版式（pre-paginated / 漫画画册类图片页）才保留设计宽度；
        // Markdown 转出的文字书也常带 width=1000 的桌面 viewport，
        // 那种「伪固定」按流式排版阅读体验才正常
        val fixedLayout = prePaginated ||
            (declaredWidth != null && declaredWidth >= FIXED_WIDTH_MIN && imageDriven)
        val fixedWidth = declaredWidth?.takeIf { it >= FIXED_WIDTH_MIN } ?: DEFAULT_FIXED_WIDTH

        // 书籍自带的 viewport 是横滑主因，统一换成我们自己的
        html = VIEWPORT_META.replace(html, "")
        // Markdown 转出来的 EPUB 常把围栏代码块拆成一串 <p>，先还原成 <pre>
        html = normalizeFencedCode(html)
        html = rewriteAssetUrls(html, chapterPath, assets)
        html = injectHead(html, fixedLayout, fixedWidth)
        return Result(html, fixedLayout)
    }

    // ==================== head / 样式 ====================

    private fun injectHead(html: String, fixedLayout: Boolean, fixedWidth: Int): String {
        val viewport = if (fixedLayout) {
            // 不写 initial-scale，交给 WebView 的 overview 模式按屏宽整体缩放；
            // 写死 initial-scale=1.0 会让 overview 失效，设计宽度 1:1 显示被屏幕裁切
            "width=$fixedWidth, user-scalable=no"
        } else {
            "width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no"
        }
        val css = if (fixedLayout) FIXED_LAYOUT_CSS else FLOW_CSS
        val injection = """
            <meta charset="UTF-8">
            <meta name="viewport" content="$viewport">
            <style id="mvi-reader-style">
            $css
            </style>
        """.trimIndent()

        return if (HEAD_CLOSE.containsMatchIn(html)) {
            var injected = false
            HEAD_CLOSE.replace(html) { match ->
                if (injected) {
                    match.value
                } else {
                    injected = true
                    "$injection\n</head>"
                }
            }
        } else {
            "<html><head>$injection</head><body>$html</body></html>"
        }
    }

    /** 普通流式排版：彻底压掉横向溢出，图片一律等比缩放且不浮动 */
    private val FLOW_CSS = """
        html, body {
            width: 100% !important;
            max-width: 100% !important;
            margin: 0 !important;
            padding: 16px !important;
            box-sizing: border-box !important;
            -webkit-text-size-adjust: 100% !important;
            font-size: 18px !important;
            line-height: 1.8 !important;
            word-wrap: break-word !important;
            overflow-wrap: anywhere !important;
        }
        /* clip 不创建滚动容器，WebView 的垂直滚动与顶部栏手势不受影响；
           老版本 WebView 不认识 clip 时退回 hidden，仍有 View 层锁 x 兜底 */
        html, body { overflow-x: hidden; overflow-x: clip !important; }
        body * { max-width: 100% !important; box-sizing: border-box !important; }
        p { margin: 0.8em 0 !important; }
        h1, h2, h3, h4, h5, h6 { line-height: 1.3 !important; overflow-wrap: anywhere !important; }
        img, svg, image, video, canvas {
            max-width: 100% !important;
            height: auto !important;
            border: 0 !important;
            object-fit: contain;
        }
        /* 窄屏上图文绕排只会让图片压字，统一改为随文流排布；行内小图标仍保持 inline */
        img, svg, video { float: none !important; clear: both; vertical-align: middle; }
        [style*="float"] { float: none !important; }
        table { width: 100% !important; max-width: 100% !important; table-layout: fixed !important; }
        th, td { word-break: break-word !important; overflow-wrap: anywhere !important; }
        /* ---- 代码 ----
           旧规则用 break-all 会把变量名从中间拆开、也没有等宽字体和代码块边界。
           现在：缩进完整保留，只在真正超出屏宽时折行；代码块独立成卡片；
           行内 code 与块内 code 分开处理，避免嵌套出双层底色。 */
        pre, pre code, code, kbd, samp, tt {
            font-family: "DejaVu Sans Mono", "Droid Sans Mono", monospace !important;
        }
        /* 代码一律保留原始换行和缩进；长行只在代码块内部左右滚动，
           不会带动整页横滑（View 层已把 WebView 的 scrollX 锁死为 0）。 */
        pre, pre * { white-space: pre !important; }
        pre {
            overflow-x: auto !important;
            -webkit-overflow-scrolling: touch;
        }
        pre[data-lang]:not([data-lang=""])::before {
            content: attr(data-lang);
            display: block;
            white-space: normal;
            text-align: right;
            font-family: "DejaVu Sans Mono", monospace;
            font-size: 11px;
            line-height: 1.2;
            letter-spacing: 0.04em;
            color: #8A9099;
            margin-bottom: 6px;
        }
        pre.mvi-code-wide::after {
            content: "← 左右滑动查看完整代码 →";
            display: block;
            white-space: normal;
            text-align: center;
            font-size: 11px;
            line-height: 1.4;
            color: #A0A6AE;
            margin-top: 8px;
        }
        pre {
            display: block !important;
            width: 100% !important;
            margin: 1em 0 !important;
            padding: 12px 14px !important;
            background-color: #F5F6F8 !important;
            border: 1px solid #E3E6EB !important;
            border-radius: 8px !important;
            color: #20242B !important;
            font-size: 0.82em !important;
            line-height: 1.55 !important;
            letter-spacing: 0 !important;
            tab-size: 4 !important;
            text-align: left !important;
            word-break: normal !important;
            overflow-wrap: break-word !important;
            hyphens: none !important;
        }
        code, kbd, samp, tt {
            font-size: 0.88em !important;
            background-color: #F1F3F5 !important;
            color: #A3322C !important;
            padding: 1px 4px !important;
            border-radius: 4px !important;
            overflow-wrap: break-word !important;
        }
        pre code, pre kbd, pre samp, pre tt {
            display: inline !important;
            padding: 0 !important;
            border: 0 !important;
            border-radius: 0 !important;
            background: transparent !important;
            color: inherit !important;
            font-size: inherit !important;
        }
        /* DocBook / 技术类 EPUB 常用 div 容器代替 pre */
        div[class*="programlisting"], div[class*="codeblock"], div[class*="code-block"],
        div[class*="literalblock"], div[class*="sourcecode"] {
            font-family: "DejaVu Sans Mono", "Droid Sans Mono", monospace !important;
            background-color: #F5F6F8 !important;
            border: 1px solid #E3E6EB !important;
            border-radius: 8px !important;
            padding: 10px 12px !important;
            margin: 1em 0 !important;
            overflow-wrap: break-word !important;
        }
        /* 语法高亮的 span 自带颜色，保持不动 */
        a { overflow-wrap: anywhere !important; }
        $THEME_PLACEHOLDER
    """.trimIndent()

    /** 固定版式书籍：保留整页设计，只兜住溢出与破图 */
    private val FIXED_LAYOUT_CSS = """
        html, body {
            margin: 0 !important;
            padding: 0 !important;
            width: 100% !important;
            max-width: 100% !important;
            -webkit-text-size-adjust: 100% !important;
        }
        html, body { overflow-x: hidden; overflow-x: clip !important; }
        img, svg, image, video, canvas {
            max-width: 100% !important;
            height: auto !important;
            border: 0 !important;
        }
        /* 固定版式保持整页设计，只统一代码的等宽字体，不强制折行 */
        pre, pre code, code, kbd, samp, tt {
            font-family: "DejaVu Sans Mono", "Droid Sans Mono", monospace !important;
        }
        $THEME_PLACEHOLDER
    """.trimIndent()

    // ==================== 固定版式识别 ====================

    /**
     * 是否「图片主导」页面：漫画/画册页几乎只有图片和少量说明文字。
     * 文本为主的页（即使带 width=1000 的桌面 viewport）必须走流式排版，
     * 否则整页按设计宽度缩放后字号过小，反而不可读。
     */
    private fun isImageDriven(html: String): Boolean {
        val imgCount = IMG_TAG.findAll(html).count()
        if (imgCount == 0) return false
        // style/script 里的内容不是正文，先剔除再去标签
        val textLength = TAG.replace(STYLE_OR_SCRIPT.replace(html, ""), "").trim().length
        return textLength < imgCount * IMAGE_TEXT_BUDGET
    }

    // ==================== Markdown 围栏代码块 ====================

    /**
     * 不少技术类 EPUB 是 Markdown 直接转出来的，围栏代码块没有变成
     * `<pre><code>`，而是 ` ```kotlin `、每一行代码、` ``` ` 各占一个 `<p>`。
     * 直接渲染就会印出围栏符号、每行带大段段落间距、缩进丢失、ASCII 图错位。
     *
     * 这里按出现顺序把成对的围栏段落合并成真正的代码块：中间所有段落拍平为
     * 纯文本（保留缩进与空行、还原 HTML 实体），再重新转义包进 `<pre><code>`。
     * 纯 Kotlin 实现，不涉及 DOM，便于 JVM 单测。
     */
    private fun normalizeFencedCode(html: String): String {
        if (!html.contains("```") && !html.contains("~~~")) return html

        val fences = FENCE_CANDIDATE.findAll(html).mapNotNull { block ->
            val text = plainText(block.groupValues[2]).trim()
            val fence = FENCE_LINE.matchEntire(text) ?: return@mapNotNull null
            FenceMark(block.range, fence.groupValues[1].first(), fence.groupValues[2])
        }.toList()
        if (fences.isEmpty()) return html

        val out = StringBuilder(html.length + 512)
        var cursor = 0
        var i = 0
        while (i + 1 < fences.size) {
            val open = fences[i]
            val close = fences[i + 1]
            // ``` 与 ~~~ 不能互相配对；单个代码块也不该吞掉整章
            if (open.marker != close.marker ||
                close.range.first - open.range.last > MAX_FENCE_BODY
            ) {
                i++
                continue
            }
            val body = html.substring(open.range.last + 1, close.range.first)
            out.append(html, cursor, open.range.first)
            out.append(buildCodeBlock(htmlToCodeText(body), open.language))
            cursor = close.range.last + 1
            i += 2
        }
        out.append(html, cursor, html.length)

        // 落单的围栏段落（例如正文在讲解 Markdown 语法）不再印出 ``` 符号
        return FENCE_CANDIDATE.replace(out.toString()) { block ->
            if (FENCE_LINE.matches(plainText(block.groupValues[2]).trim())) "" else block.value
        }
    }

    private data class FenceMark(val range: IntRange, val marker: Char, val language: String)

    /** 块级段落拍平成代码文本：标签变换行，实体还原，保留行首缩进 */
    private fun htmlToCodeText(fragment: String): String {
        // XHTML 源码里每个 <p> 之间通常有换行，那是排版空白而不是代码空行；
        // 只去掉块级标签之间的空白，避免每行代码之间凭空多出空行。
        // 不能全局压缩标签间空白，否则语法高亮 span 之间的空格会丢。
        val compact = INTER_BLOCK_SPACE.replace(fragment) { match ->
            match.groupValues[1] + match.groupValues[2]
        }
        val withBreaks = BREAK_TAG.replace(BLOCK_END.replace(compact, "\n"), "\n")
        // 先去标签再还原实体，否则 &lt; 变回 < 后会被当成标签删掉
        val text = decodeEntities(TAG.replace(withBreaks, ""))
        return text.lines()
            .map { it.trimEnd() }
            .joinToString("\n")
            .trim('\n')
    }

    private fun plainText(fragment: String): String =
        decodeEntities(TAG.replace(fragment, ""))

    private fun buildCodeBlock(codeText: String, language: String): String {
        val longest = codeText.lines().maxOfOrNull { it.length } ?: 0
        // 框线图（模块结构图等）和超长行折行就废了，这类块改为块内横向滚动
        val hasBoxDrawing = codeText.any { it.code in 0x2500..0x257F }
        val wide = hasBoxDrawing || longest > WIDE_LINE_LIMIT
        val cls = if (wide) "mvi-code mvi-code-wide" else "mvi-code"
        val lang = language.takeIf { it.isNotBlank() }
            ?.let { " data-lang=\"$it\"" }.orEmpty()
        return "<pre class=\"$cls\"$lang><code>${escapeHtml(codeText)}</code></pre>"
    }

    private fun decodeEntities(text: String): String = text
        .replace("&lt;", "<", ignoreCase = true)
        .replace("&gt;", ">", ignoreCase = true)
        .replace("&quot;", "\"", ignoreCase = true)
        .replace("&apos;", "'", ignoreCase = true)
        .replace("&#39;", "'", ignoreCase = true)
        .replace("&nbsp;", " ", ignoreCase = true)
        .replace("&amp;", "&", ignoreCase = true)

    private fun escapeHtml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    // ==================== 资源 URL ====================

    /**
     * 把 `/OEBPS/Images/a.jpg` 这类 ZIP 根绝对路径换算成相对章节目录的 URL。
     * 图片/CSS 资源若按解析结果找不到，再按文件名在解压索引里兜底一次；
     * 指向其它章节的 `<a href>` 保持原样，不参与资源改写。
     */
    private fun rewriteAssetUrls(html: String, chapterPath: String, assets: Set<String>): String {
        if (!html.contains('=')) return html
        // 代码示例里经常出现 src="/images/a.png" 这种字面量，绝不能当真实资源改写
        val codeBlocks = ArrayList<String>()
        var working = CODE_BLOCK.replace(html) { match ->
            codeBlocks += match.value
            "<!--MVI_CODE_${codeBlocks.size - 1}-->"
        }
        val fromDir = EpubPaths.parentDir(chapterPath)
        working = ASSET_ATTR.replace(working) { match ->
            val raw = match.groupValues[3].trim()
            if (raw.isEmpty() || raw.startsWith('#') || raw.startsWith("//") ||
                SCHEME.containsMatchIn(raw)
            ) {
                return@replace match.value
            }

            var value = raw.replace("\\", "/")
            if (value.startsWith("/")) {
                value = relativeUrl(fromDir, value.trimStart('/'))
            }
            if (assets.isNotEmpty()) {
                value = resolveExisting(fromDir, value, assets) ?: value
            }
            if (value == raw) match.value else match.value.replaceFirst(raw, value)
        }
        return CODE_HOLDER.replace(working) { match ->
            codeBlocks.getOrNull(match.groupValues[1].toIntOrNull() ?: -1) ?: match.value
        }
    }

    /** 返回「重写后的相对 URL」；资源本来就存在时返回 null 表示无需改动 */
    private fun resolveExisting(fromDir: String, url: String, assets: Set<String>): String? {
        val wanted = EpubPaths.resolve(fromDir, url.substringBefore('#').substringBefore('?'))
        if (wanted.isEmpty()) return null
        // 章节跳转链接不按资源规则改写，否则会把 <a href> 指到同名图片/目录
        if (DOC_EXTENSION.containsMatchIn(wanted)) return null
        if (assets.any { EpubPaths.matches(it, wanted) }) return null

        // ZIP 条目名被规范化、大小写不同、或 HTML 里写的是 OPF 目录相对路径时，
        // 按文件名找一次真实资源，再换算回当前章节目录的相对路径。
        val fileName = wanted.substringAfterLast('/')
        val target = assets.firstOrNull { asset ->
            EpubPaths.matches(asset.substringAfterLast('/'), fileName)
        } ?: return null
        return relativeUrl(fromDir, target)
    }

    /** 从 [fromDir] 出发到 ZIP 根相对路径 [target] 的相对 URL */
    private fun relativeUrl(fromDir: String, target: String): String {
        val from = fromDir.split('/').filter { it.isNotEmpty() && it != "." }
        val to = EpubPaths.entry(target).split('/').filter { it.isNotEmpty() }
        var common = 0
        while (common < from.size && common < to.size &&
            from[common].equals(to[common], ignoreCase = true)
        ) {
            common++
        }
        val ups = List(from.size - common) { ".." }
        return (ups + to.subList(common, to.size)).joinToString("/").ifEmpty { "./" }
    }

    // ==================== 编码 ====================

    /** 正文已按原编码解码为 String，声明必须改成 UTF-8，否则 GBK 书二次乱码 */
    private fun normalizeCharset(html: String): String {
        val replaced = CHARSET_DECLARATION.replace(html) { match ->
            val prefix = match.groupValues[1]
            val quote = match.groupValues[2]
            "$prefix${quote}UTF-8$quote"
        }
        return ENCODING_DECLARATION.replace(replaced) { "encoding=\"UTF-8\"" }
    }

    private const val FIXED_WIDTH_MIN = 600
    private const val DEFAULT_FIXED_WIDTH = 1000

    /** 每张图片允许分摊的正文文本量，超过即视为图文混排页而非图片版式页 */
    private const val IMAGE_TEXT_BUDGET = 300

    private val IMG_TAG = Regex("""<img\b""", RegexOption.IGNORE_CASE)
    private val STYLE_OR_SCRIPT = Regex(
        """<(style|script)\b.*?</\1>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    /** EPUB3 固定版式声明：<meta property="rendition:layout" content="pre-paginated">（属性顺序不限） */
    private val PRE_PAGINATED_META = Regex(
        """<meta\s[^>]*pre-paginated[^>]*>""",
        RegexOption.IGNORE_CASE,
    )

    private val HEAD_CLOSE = Regex("</head>", RegexOption.IGNORE_CASE)
    private val VIEWPORT_META = Regex(
        """<meta\s+[^>]*name\s*=\s*["']?viewport["']?[^>]*>""",
        RegexOption.IGNORE_CASE,
    )
    private val META_CONTENT = Regex(
        """content\s*=\s*["']([^"']*)["']""",
        RegexOption.IGNORE_CASE,
    )
    private val VIEWPORT_WIDTH = Regex("""\bwidth\s*=\s*(\d{2,4})\b""", RegexOption.IGNORE_CASE)
    private val SCHEME = Regex("""^[A-Za-z][A-Za-z0-9+.\-]*:""")
    private val DOC_EXTENSION = Regex("""\.(x?html?|htm)$""", RegexOption.IGNORE_CASE)
    private val CODE_BLOCK = Regex(
        """<(pre|code)\b[^>]*>.*?</\1>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val CODE_HOLDER = Regex("""<!--MVI_CODE_(\d+)-->""")

    private const val WIDE_LINE_LIMIT = 100
    private const val MAX_FENCE_BODY = 60_000
    private val FENCE_CANDIDATE = Regex(
        """<(p|div)\b[^>]*>(.*?)</\1>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val FENCE_LINE = Regex("""^(`{3,}|~{3,})([A-Za-z0-9_+#.\-]*)$""")
    private val TAG = Regex("""<[^>]*>""")
    private val BLOCK_END = Regex(
        """</(?:p|div|li|h[1-6]|tr|pre|blockquote)\s*>""",
        RegexOption.IGNORE_CASE,
    )
    private val BREAK_TAG = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)
    private val INTER_BLOCK_SPACE = Regex(
        """(</(?:p|div|li|h[1-6]|tr|pre|blockquote)>)\s+(<(?:p|div|li|h[1-6]|tr|pre|blockquote)\b)""",
        RegexOption.IGNORE_CASE,
    )
    private val ASSET_ATTR = Regex(
        """\b(xlink:href|src|poster|data|background|href)\s*=\s*(["'])(.*?)\2""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    // 反向引用保留原有引号，避免替换后留下孤立引号破坏 HTML
    private val CHARSET_DECLARATION = Regex(
        "(charset\\s*=\\s*)([\"']?)([A-Za-z0-9_\\-]+)\\2",
        RegexOption.IGNORE_CASE,
    )
    private val ENCODING_DECLARATION = Regex(
        "encoding\\s*=\\s*[\"'][^\"']+[\"']",
        RegexOption.IGNORE_CASE,
    )
}
