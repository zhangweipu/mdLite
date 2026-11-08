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

/** 打开书籍的结果：失败时带可读原因 */
data class EpubOpenResult(
    val book: EpubBook?,
    val error: String?,
)

/**
 * EPUB 阅读器仓库。
 *
 * 旧实现把「解析元数据」建立在「先把整本书解压到缓存」之上：解压一旦抛异常，
 * 协程直接崩溃，界面永远停在加载中；缓存目录残留半成品后还会永远不再重试。
 * 现在改为两段式：
 * 1. [openBook] 只流式解析 ZIP 元数据，秒返回，正文可直接从 ZIP 读；
 * 2. [prepareResources] 在后台把图片/CSS 解压到缓存目录，目录写好后打上
 *    `.ready` 标记，WebView 才有可用的 file:// baseUrl（原子替换，不会读到半成品）。
 *
 * 任一环节失败都不会阻断「打开书看正文」这条主路径。
 */
@Singleton
class EpubReaderRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val parser: EpubParser,
) {
    /** 内存缓存：最近 4 章（access-order LRU），所有访问都加锁 */
    private val contentCache = LinkedHashMap<String, EpubChapterPage>(4, 0.75f, true)

    /** 已解压资源在 ZIP 内的相对路径集合，用于修正书里写错/被规范化的图片路径 */
    private val assetsIndex = HashMap<String, Set<String>>()
    private val cacheLock = Any()

    /** 解析书籍元数据（IO 线程，不解压整本书） */
    suspend fun openBook(uri: Uri): EpubOpenResult = withContext(Dispatchers.IO) {
        when (val result = parser.parse(context, uri)) {
            is EpubParseResult.Success -> EpubOpenResult(result.book, null)
            is EpubParseResult.Failure -> EpubOpenResult(null, result.reason)
        }
    }

    /**
     * 后台解压书内资源，让 WebView 能加载图片与样式表。
     * 返回是否可用；失败时章节仍可从 ZIP 读取文字内容。
     */
    suspend fun prepareResources(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val dir = cacheDir(uri)
        if (File(dir, READY).isFile()) return@withContext true

        val tmp = File(dir.parentFile, "${dir.name}.tmp")
        runCatching {
            tmp.deleteRecursively()
            if (!tmp.mkdirs() && !tmp.isDirectory) error("无法创建缓存目录")
            var total = 0L
            parser.openStream(context, uri).use { input ->
                ZipInputStream(input.buffered()).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        val name = EpubPaths.entry(entry.name)
                        if (name.isEmpty() || entry.isDirectory || shouldSkip(name)) continue

                        val target = File(tmp, name)
                        // Zip Slip 防护：条目不得越出临时目录
                        if (!target.canonicalPath.startsWith(tmp.canonicalPath + File.separator)) {
                            continue
                        }
                        target.parentFile?.mkdirs()
                        total += target.outputStream().use { output -> zip.copyTo(output) }
                        if (total > MAX_EXTRACT_BYTES) {
                            error("书籍资源过大，已跳过图片预解压")
                        }
                    }
                }
            }
            File(tmp, READY).writeText("1")
            dir.deleteRecursively()
            if (!tmp.renameTo(dir)) error("缓存目录写入失败")
            synchronized(cacheLock) { assetsIndex.remove(dir.absolutePath) }
            // 之前缓存的章节没有 baseUrl（图片不可用），需要重新生成
            invalidateCache(uri)
            true
        }.getOrElse {
            tmp.deleteRecursively()
            false
        }
    }

    /** 读取一章的渲染数据：内存缓存 → 解压缓存 → 原始 ZIP，三级回退 */
    suspend fun readChapter(
        uri: Uri,
        book: EpubBook,
        chapterIndex: Int,
    ): EpubChapterPage? {
        val chapter = book.chapters.getOrNull(chapterIndex) ?: return null
        val key = "$uri|${chapter.path}"
        synchronized(cacheLock) {
            contentCache[key]?.let { return it }
        }

        return withContext(Dispatchers.IO) {
            val dir = cacheDir(uri)
            val ready = File(dir, READY).isFile()
            var bytes = if (ready) readCachedBytes(dir, chapter.path) else null
            if (bytes == null) bytes = parser.readChapterBytes(context, uri, chapter.path)
            if (bytes == null) return@withContext null

            val formatted = EpubHtmlFormatter.format(
                rawHtml = parser.decodeHtml(bytes),
                chapterPath = chapter.path,
                assets = if (ready) assetsFor(dir) else emptySet(),
            )
            val page = EpubChapterPage(
                html = formatted.html,
                baseUrl = if (ready) baseUrlFor(dir, chapter.path) else null,
                fixedLayout = formatted.fixedLayout,
            )
            synchronized(cacheLock) { putToCache(key, page) }
            page
        }
    }

    // ==================== 内部方法 ====================

    private fun cacheDir(uri: Uri): File =
        File(context.cacheDir, "epub/${uri.toString().hashCode()}")

    private fun shouldSkip(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".mp4") || lower.endsWith(".mp3") ||
            lower.endsWith(".m4a") || lower.endsWith(".avi") ||
            lower.endsWith(".zip") || lower.endsWith(".epub")
    }

    /** 精确路径 → 忽略大小写/编码 → 按文件名，逐级容错 */
    private fun readCachedBytes(dir: File, path: String): ByteArray? {
        val direct = File(dir, path)
        if (direct.isFile) return direct.readBytes()

        val target = EpubPaths.entry(path)
        val fileName = target.substringAfterLast('/')
        var byName: File? = null
        for (file in dir.walkTopDown()) {
            if (!file.isFile || file.name == READY) continue
            val relative = EpubPaths.entry(file.relativeTo(dir).path)
            if (EpubPaths.matches(relative, target)) return file.readBytes()
            if (byName == null && file.name.equals(fileName, ignoreCase = true)) byName = file
        }
        return byName?.readBytes()
    }

    /** 章节内相对资源（图片/CSS）的 baseUrl，需指向章节所在目录 */
    private fun baseUrlFor(dir: File, chapterPath: String): String {
        val subDir = EpubPaths.parentDir(chapterPath)
        val base = if (subDir.isEmpty()) dir else File(dir, subDir)
        val target = if (base.isDirectory) base else dir
        return Uri.fromFile(target).toString().let {
            if (it.endsWith("/")) it else "$it/"
        }
    }

    /**
     * 列出解压目录里的真实资源路径（ZIP 根相对）。
     * 章节 XHTML 里的图片路径常与 ZIP 条目名有大小写/编码/`..` 差异，
     * 交给 [EpubHtmlFormatter] 按文件名兜底换算，避免破图。
     */
    private fun assetsFor(dir: File): Set<String> = synchronized(cacheLock) {
        assetsIndex.getOrPut(dir.absolutePath) {
            dir.walkTopDown()
                .filter { it.isFile && it.name != READY }
                .map { EpubPaths.entry(it.relativeTo(dir).path) }
                .filter { it.isNotEmpty() }
                .toSet()
        }
    }

    /** 资源解压完成后丢弃旧章节缓存，让下一帧带上 file:// baseUrl 重新渲染 */
    private fun invalidateCache(uri: Uri) {
        val prefix = "$uri|"
        synchronized(cacheLock) {
            val iterator = contentCache.entries.iterator()
            while (iterator.hasNext()) {
                if (iterator.next().key.startsWith(prefix)) iterator.remove()
            }
        }
    }

    private fun putToCache(key: String, value: EpubChapterPage) {
        if (contentCache.size >= MAX_CACHED_CHAPTERS && !contentCache.containsKey(key)) {
            contentCache.entries.firstOrNull()?.let { contentCache.remove(it.key) }
        }
        contentCache[key] = value
    }

    private companion object {
        const val READY = ".ready"
        const val MAX_CACHED_CHAPTERS = 4
        const val MAX_EXTRACT_BYTES = 256L * 1024 * 1024
    }
}
