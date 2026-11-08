package com.example.mviapp.core.epub

import android.content.Context
import android.net.Uri
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * EPUB 解析器（轻量级，流式解析）
 *
 * 设计原则（针对低配设备优化）：
 * - 使用 ZipInputStream 流式读取，不把整个 ZIP 加载到内存
 * - 只解析元数据和章节列表，章节正文按需懒加载
 * - 使用 Android 内置 XmlPullParser（比 DOM 解析省内存）
 * - 不引入第三方解析库，减少方法数和 APK 体积
 */
@Singleton
class EpubParser @Inject constructor() {

    /**
     * 从 content URI 解析 EPUB 元数据（书名、作者、章节列表）
     *
     * @return 解析后的书籍信息，失败返回 null
     */
    fun parse(context: Context, uri: Uri): EpubBook? {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                parseFromStream(inputStream)
            }
        }.getOrNull()
    }

    /**
     * 从输入流解析 EPUB 元数据
     *
     * 解析流程（一次扫描，避免多次 reset）：
     * 1. 扫描 ZIP 所有条目，收集 container.xml / OPF / TOC 的内容
     * 2. 解析 container.xml → 找到 OPF 路径
     * 3. 解析 OPF → 元数据 + spine（章节顺序）
     * 4. 解析 TOC（NCX 或 nav.xhtml）→ 章节标题
     */
    private fun parseFromStream(input: InputStream): EpubBook? {
        // 一次扫描，收集所有关键文件的内容
        // key: ZIP 条目路径, value: 文件内容（只收集我们需要的小文件）
        val fileContents = HashMap<String, ByteArray>()
        var opfPathFromContainer: String? = null

        ZipInputStream(input.buffered()).use { zip ->
            var entry: ZipEntry? = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                // 收集我们需要的文件
                when {
                    name == "META-INF/container.xml" -> {
                        val bytes = zip.readBytes()
                        fileContents[name] = bytes
                        opfPathFromContainer = parseContainerXml(bytes.inputStream())
                    }
                    name.endsWith(".opf", ignoreCase = true) -> {
                        fileContents[name] = zip.readBytes()
                    }
                    name.endsWith(".ncx", ignoreCase = true) -> {
                        fileContents[name] = zip.readBytes()
                    }
                    // EPUB 3 nav.xhtml
                    name.endsWith("nav.xhtml", ignoreCase = true) ||
                        name.endsWith("nav.html", ignoreCase = true) -> {
                        fileContents[name] = zip.readBytes()
                    }
                }
                entry = zip.nextEntry
            }
        }

        // 找 OPF 路径：优先用 container.xml 里的，没有就找第一个 .opf 文件
        val opfPath = opfPathFromContainer
            ?: fileContents.keys.firstOrNull { it.endsWith(".opf", ignoreCase = true) }
            ?: return null

        // 计算根目录
        var rootDir = opfPath.substringBeforeLast('/', missingDelimiterValue = "")
        if (rootDir.isNotEmpty()) rootDir += "/"

        // 解析 OPF
        val opfBytes = fileContents[opfPath] ?: return null
        val opfResult = parseOpf(opfBytes.inputStream())

        if (opfResult.spineIds.isEmpty()) return null

        // 找 TOC 文件
        val tocHref = opfResult.tocPath
        val tocFullPath = tocHref?.let { resolvePath(rootDir, it) }
            // 兜底：找第一个 .ncx 或 nav.xhtml
            ?: fileContents.keys.firstOrNull {
                it.endsWith(".ncx", ignoreCase = true)
            }
            ?: fileContents.keys.firstOrNull {
                it.endsWith("nav.xhtml", ignoreCase = true) ||
                    it.endsWith("nav.html", ignoreCase = true)
            }

        // 解析 TOC
        val tocTitles = HashMap<String, String>()
        if (tocFullPath != null) {
            val tocBytes = fileContents[tocFullPath]
            if (tocBytes != null) {
                val map = if (tocFullPath.endsWith(".ncx", ignoreCase = true)) {
                    parseNcxToc(tocBytes.inputStream())
                } else {
                    parseNavToc(tocBytes.inputStream())
                }
                tocTitles.putAll(map)
            }
        }

        // 构建章节列表（按 spine 顺序）
        val chapters = opfResult.spineIds.mapIndexed { index, id ->
            val href = opfResult.manifest[id] ?: ""
            val fullPath = resolvePath(rootDir, href)
            // 优先用 TOC 中的标题，否则用文件名兜底
            val chapterTitle = tocTitles[href]
                ?: href.substringAfterLast('/').substringBeforeLast('.')
            EpubChapter(
                path = fullPath,
                title = chapterTitle.ifBlank { "第${index + 1}章" },
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

    /** 读取指定章节的 HTML 内容（懒加载，读一章解压一章） */
    fun readChapterContent(context: Context, uri: Uri, chapterPath: String): String? {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                var content: String? = null
                ZipInputStream(input.buffered()).use { zip ->
                    var entry: ZipEntry? = zip.nextEntry
                    while (entry != null) {
                        if (entry.name == chapterPath) {
                            content = zip.readBytes().toString(Charsets.UTF_8)
                            break
                        }
                        entry = zip.nextEntry
                    }
                }
                content
            }
        }.getOrNull()
    }

    // ===== 内部解析方法 =====

    /** 解析 container.xml，返回 OPF 文件路径 */
    private fun parseContainerXml(input: InputStream): String? {
        return runCatching {
            val parser = newPullParser(input)
            var opfPath: String? = null
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG &&
                    parser.name == "rootfile"
                ) {
                    opfPath = parser.getAttributeValue(null, "full-path")
                    break
                }
            }
            opfPath
        }.getOrNull()
    }

    /** OPF 解析结果 */
    private data class OpfResult(
        val title: String,
        val author: String?,
        val spineIds: MutableList<String>,
        val manifest: HashMap<String, String>,
        val tocPath: String?,
    )

    /** 解析 OPF 文件 */
    private fun parseOpf(input: InputStream): OpfResult {
        val parser = newPullParser(input)
        var title = ""
        var author: String? = null
        val spineIds = mutableListOf<String>()
        val manifest = HashMap<String, String>()
        var tocPath: String? = null
        var inMetadata = false
        var inTitle = false
        var inCreator = false

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "metadata" -> inMetadata = true
                        "title" -> if (inMetadata) inTitle = true
                        "creator" -> if (inMetadata) inCreator = true
                        "item" -> {
                            val id = parser.getAttributeValue(null, "id")
                            val href = parser.getAttributeValue(null, "href")
                            val mediaType = parser.getAttributeValue(null, "media-type")
                            if (id != null && href != null) {
                                manifest[id] = href
                            }
                            // NCX 目录
                            if (mediaType == "application/x-dtbncx+xml" && id != null) {
                                tocPath = href
                            }
                        }
                        "itemref" -> {
                            val idref = parser.getAttributeValue(null, "idref")
                            if (idref != null) spineIds.add(idref)
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inTitle) {
                        title = parser.text
                        inTitle = false
                    } else if (inCreator) {
                        author = parser.text
                        inCreator = false
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "metadata") inMetadata = false
                }
            }
        }

        return OpfResult(title, author, spineIds, manifest, tocPath)
    }

    /** 解析 NCX 目录，返回 href -> title 映射 */
    private fun parseNcxToc(input: InputStream): Map<String, String> {
        val result = HashMap<String, String>()
        val parser = newPullParser(input)
        var inNavPoint = false
        var inText = false
        var currentSrc: String? = null
        var currentTitle = ""

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
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
                                ?.substringBefore('#') // 去掉锚点
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inText) {
                        currentTitle = parser.text
                        inText = false
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "navPoint" && currentSrc != null) {
                        result[currentSrc!!] = currentTitle
                        inNavPoint = false
                    }
                }
            }
        }
        return result
    }

    /** 解析 nav.xhtml 目录（EPUB 3），返回 href -> title 映射 */
    private fun parseNavToc(input: InputStream): Map<String, String> {
        val result = HashMap<String, String>()
        val parser = newPullParser(input)
        var inNavToc = false
        var inA = false
        var currentHref: String? = null
        var currentTitle = ""

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "nav" -> {
                            val epubType = parser.getAttributeValue(
                                "http://www.idpf.org/2007/ops",
                                "type"
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
                XmlPullParser.TEXT -> {
                    if (inA) currentTitle += parser.text
                }
                XmlPullParser.END_TAG -> {
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
        return result
    }

    // ===== 工具方法 =====

    private fun newPullParser(input: InputStream): XmlPullParser {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false // 简化处理，不关心命名空间
        val parser = factory.newPullParser()
        parser.setInput(input, "UTF-8")
        return parser
    }

    /** 解析相对路径（处理 ../ 等） */
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
