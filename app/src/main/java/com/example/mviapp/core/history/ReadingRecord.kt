package com.example.mviapp.core.history

import kotlinx.serialization.Serializable

/**
 * 一条阅读记录
 *
 * @param title  文档标题
 * @param source 文档来源：assets 相对路径（md/xxx.md）或 content:// URI
 * @param readAt 阅读时间（毫秒时间戳）
 */
@Serializable
data class ReadingRecord(
    val title: String,
    val source: String,
    val readAt: Long,
)
