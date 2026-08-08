package com.example.mviapp.core.md

/**
 * Markdown 文档元数据
 *
 * @param fileName   资源目录下的文件名，如 README.md
 * @param title      文档标题（取文件内第一个一级标题，缺省用文件名）
 * @param description 文档简介（取文件内第一段正文，用于列表展示）
 */
data class MdFile(
    val fileName: String,
    val title: String,
    val description: String = "",
) {
    /** 文档在 assets 下的路径，如 md/README.md */
    val filePath: String get() = "md/$fileName"
}
