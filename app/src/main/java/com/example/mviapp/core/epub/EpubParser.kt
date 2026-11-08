package com.example.mviapp.core.epub

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.mviapp.core.common.FileSignatures
import com.example.mviapp.core.common.percentDecode
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.PushbackInputStream
import java.io.StringReader
import java.nio.charset.Charset
import java.util.ArrayDeque
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/** EPUB 解析结果：失败时带上可读原因，便于界面提示与排障 */
sealed interface EpubParseResult {
    data class Success(val book: EpubBook) : EpubParseResult
    data class Failure(val reason: String) : EpubParseResult
}

/**
 * EPUB 解析器（流式、无第三方依赖）。
 *
 * 与旧实现的差别（都是「打不开 EPUB」的常见原因）：
 * - ZIP 流式解析与解压目录解析**共用同一套 XML 解析逻辑**，不再有两份易漂移的代码
 * - 容忍缺少 container.xml / spine 为空 / OPF 带命名空间前缀 / 中文文件名百分号编码
 * - 同时支持 EPUB 2 的 NCX 与 EPUB 3 的 nav.xhtml 目录
 * - 章节 HTML 按文件声明的字符集解码（GBK 中文书不再乱码）
 * - 路径统一走 [EpubPaths]，正确处理 `../`、锚点、大小写差异
 */
@Singleton
class EpubParser @Inject constructor() {

    /**
     * 统一打开 EPUB 输入流。
     *
     * `ContentResolver.openInputStream` 只能处理 content://（及部分 file://）；
     * 首页历史记录与外部「打开方式」还可能传入无 scheme 的绝对路径或 file:// URI，
     * 旧实现在这些场景直接抛异常，界面便一直停在「正在解析书籍…」。
     */
    fun openStream(context: Context, uri: Uri): InputStream {
        val scheme = uri.scheme?.lowercase()
        return when {
            scheme == null -> FileInputStream(uri.path?.takeIf { it.isNotBlank() } ?: uri.toString())
            scheme == "file" -> FileInputStream(uri.path ?: error("无效的文件路径"))
            else -> context.contentResolver.openInputStream(uri)
                ?: error("无法打开文件，可能是访问权限已失效，请重新选择文件")
        }
    }

    /** 直接从 EPUB（ZIP）流解析元数据，一次扫描，不需要先解压整本书 */
    fun parse(context: Context, uri: Uri): EpubParseResult = runCatching {
        val size = sourceSize(context, uri)
        openStream(context, uri).use { parseFromZip(it, size) }
    }.getOrElse { failure(it) }

    /** 从已解压的缓存目录解析元数据（内容与 ZIP 中一致时使用） */
    fun parseFromDirectory(root: File): EpubParseResult = runCatching {
        parseWith(DirectorySource(root))
    }.getOrElse { failure(it) }

    /** 从 ZIP 读取指定章节原始字节（缓存未就绪时的兜底） */
    fun readChapterBytes(context: Context, uri: Uri, chapterPath: String): ByteArray? =
        runCatching {
            openStream(context, uri).use { input ->
                ZipInputStream(input.buffered()).use { zip ->
                    var result: ByteArray? = null
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        val name = EpubPaths.entry(entry.name)
                        if (!entry.isDirectory && EpubPaths.matches(name, chapterPath)) {
                            result = zip.readBytes()
                            break
                        }
                    }
                    result
                }
            }
        }.getOrNull()

    /** 兼容旧调用的便捷方法 */
    fun readChapterContent(context: Context, uri: Uri, chapterPath: String): String? =
        readChapterBytes(context, uri, chapterPath)?.let(::decodeHtml)

    // ==================== ZIP / 目录统一入口 ====================

    private fun parseFromZip(input: InputStream, sourceSize: Long): EpubParseResult {
        // 先探一下文件头。以前只要 ZIP 里一个条目都读不到就统一报
        // 「文件为空或不是有效的 EPUB」，用户无法区分「下载没完成」、
        // 「下到的是网页」和「EPUB 真的损坏」，也就无从排查。
        val pushback = PushbackInputStream(input.buffered(), HEAD_SIZE)
        val headBuffer = ByteArray(HEAD_SIZE)
        val headSize = pushback.read(headBuffer).coerceAtLeast(0)
        val head = headBuffer.copyOf(headSize)
        pushback.unread(head)
        val sizeText = if (sourceSize >= 0) "（文件大小 $sourceSize 字节）" else ""

        if (headSize == 0) {
            return EpubParseResult.Failure(
                "文件是空的，0 字节$sizeText：下载没有完成，请重新下载该文件"
            )
        }
        if (!FileSignatures.isZip(head)) {
            val kind = FileSignatures.describe(head)
            val sample = FileSignatures.preview(head)
            return EpubParseResult.Failure(
                "文件内容是$kind，不是 EPUB 压缩包$sizeText；开头是「$sample」。\n" +
                    "如果这是刚下载的文件，说明下载到的是网页/错误页或下载未完成，请重新下载"
            )
        }

        val files = HashMap<String, ByteArray>()
        val names = ArrayList<String>()
        ZipInputStream(pushback).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = EpubPaths.entry(entry.name)
                if (name.isNotEmpty()) {
                    names += name
                    if (!entry.isDirectory && isMetadataFile(name)) {
                        files[name] = zip.readBytes()
                    }
                }
            }
        }
        if (names.isEmpty()) {
            return EpubParseResult.Failure(
                "ZIP 内没有任何条目，文件可能下载不完整或已损坏$sizeText，请重新下载"
            )
        }
        return parseWith(ZipSource(names, files))
    }

    /** 读取文件大小用于错误提示；provider 不支持时返回 -1 */
    private fun sourceSize(context: Context, uri: Uri): Long {
        val scheme = uri.scheme?.lowercase()
        if (scheme == null || scheme == "file") {
            val path = if (scheme == null) uri.toString() else uri.path
            return path?.let { name -> File(name).takeIf { file -> file.isFile }?.length() } ?: -1L
        }
        return runCatching {
            context.contentResolver.query(
                uri, arrayOf(OpenableColumns.SIZE), null, null, null,
            )?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else -1L
            } ?: -1L
        }.getOrDefault(-1L)
    }

    private fun isMetadataFile(name: String): Boolean {
        val lower = name.lowercase()
        return lower == "meta-inf/container.xml" ||
            lower.endsWith(".opf") || lower.endsWith(".ncx") ||
            lower.endsWith("nav.xhtml") || lower.endsWith("nav.html")
    }

    private fun parseWith(source: EpubSource): EpubParseResult {
        // 1. 定位 OPF：container.xml → 第一个 .opf（容忍不规范的 EPUB）
        val opfPath = source.find("META-INF/container.xml")
            ?.let { source.bytes(it) }
            ?.let(::parseContainerPath)
            ?.let { EpubPaths.resolve("", it) }
            ?.let { source.find(it) }
            ?: source.names.firstOrNull { it.endsWith(".opf", ignoreCase = true) }
            ?: return EpubParseResult.Failure("未找到 OPF 书架文件，这可能不是标准 EPUB")

        val opfBytes = source.bytes(opfPath)
            ?: return EpubParseResult.Failure("无法读取 OPF 文件：$opfPath")

        val opf = parseOpf(opfBytes)
        val rootDir = EpubPaths.parentDir(opfPath)

        // 2. 目录：优先 OPF 声明的 nav / ncx，其次按文件名兜底
        val declaredToc = (opf.navPath ?: opf.tocPath)
            ?.let { EpubPaths.resolve(rootDir, it) }
            ?.let { source.find(it) }
            ?: source.names.firstOrNull { it.endsWith(".ncx", ignoreCase = true) }
            ?: source.names.firstOrNull {
                it.endsWith("nav.xhtml", ignoreCase = true) ||
                    it.endsWith("nav.html", ignoreCase = true)
            }

        val rawToc = declaredToc?.let { path ->
            val bytes = source.bytes(path) ?: return@let emptyMap()
            if (path.endsWith(".ncx", ignoreCase = true)) parseNcx(bytes) else parseNav(bytes)
        }.orEmpty()

        val tocByPath = HashMap<String, String>()
        val tocByName = HashMap<String, String>()
        rawToc.forEach { (href, title) ->
            val resolved = EpubPaths.resolve(rootDir, href)
            if (resolved.isNotBlank() && title.isNotBlank()) {
                tocByPath.putIfAbsentCompat(resolved, title)
                tocByName.putIfAbsentCompat(resolved.substringAfterLast('/').lowercase(), title)
            }
        }

        // 3. 阅读顺序：spine → manifest；spine 缺失/失效时用文档条目兜底
        val spineHrefs = opf.spineIds.mapNotNull { opf.manifest[it] }
        val chapterHrefs = spineHrefs.ifEmpty { source.chapterCandidates() }
        if (chapterHrefs.isEmpty()) {
            return EpubParseResult.Failure("OPF 中没有可阅读的章节内容")
        }

        val chapters = chapterHrefs.mapIndexed { index, href ->
            val path = EpubPaths.resolve(rootDir, href)
            val fileName = path.substringAfterLast('/')
            val title = tocByPath[path]
                ?: tocByName[fileName.lowercase()]
                ?: fileName.substringBeforeLast('.').percentDecode()
            EpubChapter(
                path = path,
                title = title.ifBlank { "第 ${index + 1} 章" },
                index = index,
            )
        }

        return EpubParseResult.Success(
            EpubBook(
                title = opf.title.ifBlank { "未知书名" },
                author = opf.author,
                chapters = chapters,
                rootDir = rootDir,
            )
        )
    }

    // ==================== XML 解析 ====================

    private fun parseContainerPath(bytes: ByteArray): String? = runCatching {
        val parser = newPullParser(bytes)
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && local(parser.name) == "rootfile") {
                return parser.attr("full-path")
            }
        }
        null
    }.getOrNull()

    private data class OpfInfo(
        val title: String,
        val author: String?,
        val spineIds: List<String>,
        val manifest: Map<String, String>,
        val tocPath: String?,
        val navPath: String?,
    )

    private fun parseOpf(bytes: ByteArray): OpfInfo {
        val parser = newPullParser(bytes)
        val title = StringBuilder()
        val creator = StringBuilder()
        val spineIds = ArrayList<String>()
        val manifest = HashMap<String, String>()
        var inMetadata = false
        var textTarget: String? = null
        var tocPath: String? = null
        var navPath: String? = null

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (local(parser.name)) {
                    "metadata" -> inMetadata = true
                    "title" -> if (inMetadata && title.isEmpty()) textTarget = "title"
                    "creator" -> if (inMetadata && creator.isEmpty()) textTarget = "creator"
                    "item" -> {
                        val id = parser.attr("id")
                        val href = parser.attr("href")
                        val mediaType = parser.attr("media-type")
                        val properties = parser.attr("properties")
                        if (id != null && href != null) manifest[id] = href
                        if (mediaType?.equals("application/x-dtbncx+xml", true) == true) {
                            tocPath = href
                        }
                        // EPUB 3：<item properties="nav" href="nav.xhtml" …/>
                        if (properties?.split(Regex("\\s+"))
                                ?.any { it.equals("nav", true) } == true
                        ) {
                            navPath = href
                        }
                    }
                    "itemref" -> parser.attr("idref")?.let { spineIds += it }
                }
                XmlPullParser.TEXT -> when (textTarget) {
                    "title" -> title.append(parser.text)
                    "creator" -> creator.append(parser.text)
                }
                XmlPullParser.END_TAG -> when (local(parser.name)) {
                    "title", "creator" -> textTarget = null
                    "metadata" -> inMetadata = false
                }
            }
        }
        return OpfInfo(
            title = title.toString().trim(),
            author = creator.toString().trim().takeIf { it.isNotEmpty() },
            spineIds = spineIds,
            manifest = manifest,
            tocPath = tocPath,
            navPath = navPath,
        )
    }

    /** EPUB 2 的 NCX 目录：用栈处理嵌套 navPoint */
    private fun parseNcx(bytes: ByteArray): Map<String, String> = runCatching {
        val result = LinkedHashMap<String, String>()
        val parser = newPullParser(bytes)
        val stack = ArrayDeque<NavPoint>()
        var inText = false

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (local(parser.name)) {
                    "navpoint" -> stack.addLast(NavPoint())
                    "text" -> inText = true
                    "content" -> stack.peekLast()?.let { it.src = parser.attr("src") }
                }
                XmlPullParser.TEXT -> if (inText) stack.peekLast()?.title?.append(parser.text)
                XmlPullParser.END_TAG -> when (local(parser.name)) {
                    "text" -> inText = false
                    "navpoint" -> stack.pollLast()?.let { nav ->
                        nav.src?.takeIf { it.isNotBlank() }?.let { src ->
                            result.putIfAbsentCompat(src, nav.title.toString().trim())
                        }
                    }
                }
            }
        }
        result
    }.getOrElse { emptyMap() }

    private data class NavPoint(
        var src: String? = null,
        val title: StringBuilder = StringBuilder(),
    )

    /** EPUB 3 的 nav.xhtml 目录 */
    private fun parseNav(bytes: ByteArray): Map<String, String> = runCatching {
        val result = LinkedHashMap<String, String>()
        val parser = newPullParser(bytes)
        var navSeen = false
        var inTocNav = false
        var navDepth = 0
        var inAnchor = false
        var anchorHref: String? = null
        val anchorText = StringBuilder()

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (local(parser.name)) {
                    "nav" -> {
                        navSeen = true
                        val type = parser.attr("type")?.lowercase()
                        val id = parser.attr("id")?.lowercase()
                        if (!inTocNav && (type == "toc" || id == "toc" ||
                                (type == null && id == null))
                        ) {
                            inTocNav = true
                            navDepth = 1
                        } else if (inTocNav) {
                            navDepth++
                        }
                    }
                    "a" -> if (inTocNav || !navSeen) {
                        inAnchor = true
                        anchorHref = parser.attr("href")
                        anchorText.setLength(0)
                    }
                }
                XmlPullParser.TEXT -> if (inAnchor) anchorText.append(parser.text)
                XmlPullParser.END_TAG -> when (local(parser.name)) {
                    "a" -> {
                        if (inAnchor) {
                            anchorHref?.takeIf { it.isNotBlank() }?.let { href ->
                                result.putIfAbsentCompat(href, anchorText.toString().trim())
                            }
                            inAnchor = false
                            anchorHref = null
                        }
                    }
                    "nav" -> if (inTocNav) {
                        navDepth--
                        if (navDepth <= 0) inTocNav = false
                    }
                }
            }
        }
        result
    }.getOrElse { emptyMap() }

    // ==================== 数据源抽象 ====================

    private interface EpubSource {
        val names: List<String>
        fun readRaw(name: String): ByteArray?

        /** 精确 → 忽略大小写 → 忽略百分号编码 → 按文件名，逐级容错 */
        fun find(path: String): String? {
            val wanted = EpubPaths.entry(path)
            if (wanted.isEmpty()) return null
            names.firstOrNull { it == wanted }?.let { return it }
            names.firstOrNull { it.equals(wanted, ignoreCase = true) }?.let { return it }
            names.firstOrNull { EpubPaths.matches(it, wanted) }?.let { return it }
            val last = wanted.substringAfterLast('/')
            return names.firstOrNull { it.substringAfterLast('/').equals(last, ignoreCase = true) }
        }

        fun bytes(path: String): ByteArray? = find(path)?.let(::readRaw)

        fun chapterCandidates(): List<String> = names.filter { name ->
            val lower = name.lowercase()
            (lower.endsWith(".xhtml") || lower.endsWith(".html") || lower.endsWith(".htm")) &&
                !lower.endsWith("nav.xhtml") && !lower.endsWith("nav.html") &&
                !lower.endsWith("toc.html") && !lower.endsWith("toc.xhtml")
        }.sorted()
    }

    private class ZipSource(
        override val names: List<String>,
        private val files: Map<String, ByteArray>,
    ) : EpubSource {
        override fun readRaw(name: String): ByteArray? = files[name]
    }

    private class DirectorySource(private val root: File) : EpubSource {
        override val names: List<String> = root.walkTopDown()
            .filter { it.isFile && it.name != ".ready" }
            .map { EpubPaths.entry(it.relativeTo(root).path) }
            .filter { it.isNotEmpty() }
            .toList()

        override fun readRaw(name: String): ByteArray? =
            File(root, name).takeIf { it.isFile }?.readBytes()
    }

    // ==================== 工具 ====================

    /** 按文件声明的字符集解码（EPUB 常见 UTF-8 / GBK / GB18030） */
    fun decodeHtml(bytes: ByteArray): String {
        val probeLength = minOf(bytes.size, 2048)
        val probe = String(bytes, 0, probeLength, Charsets.ISO_8859_1)
        // HTML 用 charset=，XHTML/OPF/NCX 常用 XML 声明里的 encoding=
        val declared = (CHARSET_REGEX.find(probe) ?: ENCODING_REGEX.find(probe))
            ?.groupValues?.get(1)?.trim()
        if (declared.isNullOrEmpty()) return String(bytes, Charsets.UTF_8).removePrefix("\uFEFF")
        if (declared.equals("utf-8", true) || declared.equals("utf8", true)) {
            return String(bytes, Charsets.UTF_8).removePrefix("\uFEFF")
        }
        return runCatching { String(bytes, Charset.forName(declared)) }
            .getOrElse { String(bytes, Charsets.UTF_8) }
            .removePrefix("\uFEFF")
    }

    private fun failure(error: Throwable): EpubParseResult.Failure {
        val detail = when (error) {
            is SecurityException -> "文件访问权限已失效，请重新选择文件"
            is java.io.FileNotFoundException -> "找不到文件，可能已被移动或删除"
            else -> error.message?.take(120)
        }
        return EpubParseResult.Failure(
            if (detail.isNullOrBlank()) "无法读取 EPUB 文件" else "无法读取 EPUB 文件：$detail"
        )
    }

    private fun newPullParser(bytes: ByteArray): XmlPullParser {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        val parser = factory.newPullParser()
        // 先自行按声明的编码解码成字符串，避免 GBK 的 OPF/NCX 被硬按 UTF-8 解析
        parser.setInput(StringReader(decodeHtml(bytes)))
        return parser
    }

    /** 标签名去命名空间前缀：dc:title → title，opf:item → item */
    private fun local(name: String?): String = name.orEmpty().substringAfterLast(':').lowercase()

    /** 属性查找：兼容带前缀的属性名（如 epub:type，旧实现因 namespaceAware=false 永远取不到） */
    private fun XmlPullParser.attr(name: String): String? {
        getAttributeValue(null, name)?.let { return it }
        for (i in 0 until attributeCount) {
            if (getAttributeName(i).substringAfterLast(':').equals(name, ignoreCase = true)) {
                return getAttributeValue(i)
            }
        }
        return null
    }

    private fun MutableMap<String, String>.putIfAbsentCompat(key: String, value: String) {
        if (!containsKey(key)) put(key, value)
    }

    private companion object {
        const val HEAD_SIZE = 64

        val CHARSET_REGEX = Regex(
            "charset\\s*=\\s*[\"']?\\s*([A-Za-z0-9_\\-]+)",
            RegexOption.IGNORE_CASE,
        )
        val ENCODING_REGEX = Regex(
            "encoding\\s*=\\s*[\"']([A-Za-z0-9_\\-]+)[\"']",
            RegexOption.IGNORE_CASE,
        )
    }
}
