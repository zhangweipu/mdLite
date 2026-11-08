package com.example.mviapp.core.epub

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * EPUB 阅读器仓库（带缓存，针对低配设备优化）
 *
 * 性能优化策略：
 * 1. **预解压缓存**：首次打开时将 EPUB 解压到应用缓存目录，后续直接读文件
 * 2. **章节内存缓存**：最近 3 章内容缓存在内存中，来回翻章不用重新读
 * 3. **懒加载**：只在需要时才加载章节内容
 * 4. **IO 调度**：所有解压/读取操作都在 IO 线程
 */
@Singleton
class EpubReaderRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val parser: EpubParser,
) {

    /** 内存缓存：最多保存最近 3 章内容（LRU 策略） */
    private val contentCache = LinkedHashMap<String, String>(3, 0.75f, true)

    /**
     * 打开 EPUB 书籍：解析元数据 + 预解压到缓存
     *
     * @return 书籍信息，失败返回 null
     */
    suspend fun openBook(uri: Uri): EpubBook? = withContext(Dispatchers.IO) {
        val cacheKey = uri.hashCode().toString()
        val cacheDir = File(context.cacheDir, "epub/$cacheKey")

        // 如果缓存目录已存在且有内容，直接从缓存解析
        if (cacheDir.exists() && cacheDir.listFiles()?.isNotEmpty() == true) {
            parseFromCacheDir(cacheDir)
        } else {
            // 首次打开：解压 + 解析
            extractToCache(uri, cacheDir)
            parseFromCacheDir(cacheDir)
        }
    }

    /**
     * 读取指定章节的 HTML 内容
     *
     * 优先从内存缓存取，其次从文件缓存读，最后回退到 ZIP 解压。
     */
    suspend fun readChapter(uri: Uri, book: EpubBook, chapterIndex: Int): String? {
        val chapter = book.chapters.getOrNull(chapterIndex) ?: return null
        val cacheKey = "${uri.hashCode()}/${chapter.path}"

        // 1. 内存缓存命中
        contentCache[cacheKey]?.let { return it }

        return withContext(Dispatchers.IO) {
            val cacheDir = File(context.cacheDir, "epub/${uri.hashCode()}")
            var content: String? = null

            // 2. 文件缓存命中
            val cachedFile = File(cacheDir, chapter.path)
            if (cachedFile.exists()) {
                content = cachedFile.readText(Charsets.UTF_8)
            } else {
                // 3. 回退：从 ZIP 读取
                content = parser.readChapterContent(context, uri, chapter.path)
            }

            // 注入阅读优化样式 + 处理
            content?.let {
                val optimized = injectReaderStyle(it)
                // 写入内存缓存
                putToCache(cacheKey, optimized)
                optimized
            }
        }
    }

    // ===== 内部方法 =====

    /** 把 EPUB 解压到缓存目录 */
    private fun extractToCache(uri: Uri, cacheDir: File) {
        cacheDir.mkdirs()
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input.buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val targetFile = File(cacheDir, entry.name)
                    if (entry.isDirectory) {
                        targetFile.mkdirs()
                    } else {
                        targetFile.parentFile?.mkdirs()
                        targetFile.outputStream().use { output ->
                            zip.copyTo(output)
                        }
                    }
                    entry = zip.nextEntry
                }
            }
        }
    }

    /** 从缓存目录解析书籍信息 */
    private fun parseFromCacheDir(cacheDir: File): EpubBook? {
        // 找 container.xml
        val containerFile = File(cacheDir, "META-INF/container.xml")
        if (!containerFile.exists()) return null

        // 解析 container.xml 找 OPF 路径
        val opfPath = parseContainerPath(containerFile) ?: return null
        val opfFile = File(cacheDir, opfPath)
        if (!opfFile.exists()) return null

        val rootDir = opfPath.substringBeforeLast('/', missingDelimiterValue = "")
            .let { if (it.isEmpty()) "" else "$it/" }

        // 解析 OPF
        val opfResult = parseOpfFile(opfFile)

        // 找 TOC
        val tocPath = opfResult.tocPath?.let { resolvePath(rootDir, it) }
        val tocTitles = tocPath?.let { File(cacheDir, it) }
            ?.takeIf { it.exists() }
            ?.let { tocFile ->
                if (tocFile.name.endsWith(".ncx", ignoreCase = true)) {
                    parseNcxFile(tocFile)
                } else {
                    parseNavFile(tocFile)
                }
            } ?: emptyMap()

        // 构建章节
        val chapters = opfResult.spineIds.mapIndexed { index, id ->
            val href = opfResult.manifest[id] ?: ""
            val fullPath = resolvePath(rootDir, href)
            val title = tocTitles[href]
                ?: href.substringAfterLast('/').substringBeforeLast('.')
            EpubChapter(
                path = fullPath,
                title = title.ifBlank { "第${index + 1}章" },
                index = index,
            )
        }

        return EpubBook(
            title = opfResult.title.ifBlank { "未知书名" },
            author = opfResult.author,
            chapters = chapters,
            rootDir = rootDir,
        )
    }

    /** 注入极简阅读样式，加速渲染 */
    private fun injectReaderStyle(html: String): String {
        // 在 </head> 前注入阅读样式
        val style = """
            <style>
                /* 极简阅读样式：覆盖 EPUB 复杂样式，加速渲染 */
                body {
                    font-size: 18px !important;
                    line-height: 1.8 !important;
                    padding: 16px !important;
                    max-width: 100% !important;
                    margin: 0 !important;
                    word-wrap: break-word;
                }
                p { margin: 0.8em 0 !important; }
                h1, h2, h3, h4 { margin: 1em 0 0.5em !important; line-height: 1.3 !important; }
                img { max-width: 100% !important; height: auto !important; }
                /* 隐藏可能导致重排的元素 */
                nav, aside, header, footer { display: none !important; }
            </style>
        """.trimIndent()

        return html.replace("</head>", "$style\n</head>", ignoreCase = true)
            // 如果没有 head 标签，直接在开头加
            .takeIf { it.contains("</head>", ignoreCase = true) }
            ?: "<html><head>$style</head><body>$html</body></html>"
    }

    /** 写入内存缓存（LRU，最多 3 条） */
    @Synchronized
    private fun putToCache(key: String, value: String) {
        if (contentCache.size >= 3 && !contentCache.containsKey(key)) {
            // 移除最久未使用的（LinkedHashMap 按访问顺序，第一个就是最久的）
            val eldest = contentCache.entries.firstOrNull()
            eldest?.let { contentCache.remove(it.key) }
        }
        contentCache[key] = value
    }

    // ===== 简化版文件解析（复用 parser 逻辑，但直接读文件） =====

    private fun parseContainerPath(file: File): String? {
        return file.inputStream().use { stream ->
            val factory = org.xmlpull.v1.XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val parser = factory.newPullParser()
            parser.setInput(stream, "UTF-8")
            var path: String? = null
            while (parser.next() != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == org.xmlpull.v1.XmlPullParser.START_TAG &&
                    parser.name == "rootfile"
                ) {
                    path = parser.getAttributeValue(null, "full-path")
                    break
                }
            }
            path
        }
    }

    private data class OpfResult(
        val title: String,
        val author: String?,
        val spineIds: MutableList<String>,
        val manifest: HashMap<String, String>,
        val tocPath: String?,
    )

    private fun parseOpfFile(file: File): OpfResult {
        return file.inputStream().use { stream ->
            val factory = org.xmlpull.v1.XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val parser = factory.newPullParser()
            parser.setInput(stream, "UTF-8")

            var title = ""
            var author: String? = null
            val spineIds = mutableListOf<String>()
            val manifest = HashMap<String, String>()
            var tocPath: String? = null
            var inMetadata = false
            var inTitle = false
            var inCreator = false

            while (parser.next() != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                when (parser.eventType) {
                    org.xmlpull.v1.XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "metadata" -> inMetadata = true
                            "title" -> if (inMetadata) inTitle = true
                            "creator" -> if (inMetadata) inCreator = true
                            "item" -> {
                                val id = parser.getAttributeValue(null, "id")
                                val href = parser.getAttributeValue(null, "href")
                                val mediaType = parser.getAttributeValue(null, "media-type")
                                if (id != null && href != null) manifest[id] = href
                                if (mediaType == "application/x-dtbncx+xml") tocPath = href
                            }
                            "itemref" -> {
                                parser.getAttributeValue(null, "idref")?.let { spineIds.add(it) }
                            }
                        }
                    }
                    org.xmlpull.v1.XmlPullParser.TEXT -> {
                        if (inTitle) { title = parser.text; inTitle = false }
                        else if (inCreator) { author = parser.text; inCreator = false }
                    }
                    org.xmlpull.v1.XmlPullParser.END_TAG -> {
                        if (parser.name == "metadata") inMetadata = false
                    }
                }
            }
            OpfResult(title, author, spineIds, manifest, tocPath)
        }
    }

    private fun parseNcxFile(file: File): Map<String, String> {
        return file.inputStream().use { stream ->
            val factory = org.xmlpull.v1.XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val parser = factory.newPullParser()
            parser.setInput(stream, "UTF-8")

            val result = HashMap<String, String>()
            var inNavPoint = false
            var inText = false
            var currentSrc: String? = null
            var currentTitle = ""

            while (parser.next() != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                when (parser.eventType) {
                    org.xmlpull.v1.XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "navPoint" -> {
                                if (inNavPoint && currentSrc != null) {
                                    result[currentSrc!!] = currentTitle
                                }
                                inNavPoint = true
                                currentSrc = null
                                currentTitle = ""
                            }
                            "text" -> inText = true
                            "content" -> {
                                currentSrc = parser.getAttributeValue(null, "src")
                                    ?.substringBefore('#')
                            }
                        }
                    }
                    org.xmlpull.v1.XmlPullParser.TEXT -> {
                        if (inText) { currentTitle = parser.text; inText = false }
                    }
                    org.xmlpull.v1.XmlPullParser.END_TAG -> {
                        if (parser.name == "navPoint" && currentSrc != null) {
                            result[currentSrc!!] = currentTitle
                            inNavPoint = false
                        }
                    }
                }
            }
            result
        }
    }

    private fun parseNavFile(file: File): Map<String, String> {
        return file.inputStream().use { stream ->
            val factory = org.xmlpull.v1.XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val parser = factory.newPullParser()
            parser.setInput(stream, "UTF-8")

            val result = HashMap<String, String>()
            var inNavToc = false
            var inA = false
            var currentHref: String? = null
            var currentTitle = ""

            while (parser.next() != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                when (parser.eventType) {
                    org.xmlpull.v1.XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "nav" -> {
                                val epubType = parser.getAttributeValue(
                                    "http://www.idpf.org/2007/ops", "type"
                                )
                                if (epubType == "toc") inNavToc = true
                            }
                            "a" -> if (inNavToc) {
                                inA = true
                                currentHref = parser.getAttributeValue(null, "href")
                                    ?.substringBefore('#')
                                currentTitle = ""
                            }
                        }
                    }
                    org.xmlpull.v1.XmlPullParser.TEXT -> {
                        if (inA) currentTitle += parser.text
                    }
                    org.xmlpull.v1.XmlPullParser.END_TAG -> {
                        when (parser.name) {
                            "a" -> {
                                if (inA && currentHref != null) {
                                    result[currentHref!!] = currentTitle.trim()
                                }
                                inA = false
                            }
                            "nav" -> inNavToc = false
                        }
                    }
                }
            }
            result
        }
    }

    private fun resolvePath(baseDir: String, relativePath: String): String {
        if (relativePath.startsWith('/')) return relativePath.drop(1)
        val baseParts = baseDir.split('/').filter { it.isNotEmpty() }.toMutableList()
        val relParts = relativePath.split('/')
        for (part in relParts) {
            when (part) {
                ".." -> if (baseParts.isNotEmpty()) baseParts.removeLast()
                "." -> Unit
                else -> baseParts.add(part)
            }
        }
        return baseParts.joinToString("/")
    }
}
