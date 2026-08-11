package com.example.mviapp.core.bookmark

import kotlinx.serialization.Serializable

/**
 * 书签数据模型
 *
 * @property id 唯一标识（URL 的哈希值）
 * @property title 网页标题
 * @property url 网页地址
 * @property createdAt 添加时间戳
 */
@Serializable
data class Bookmark(
    val id: String,
    val title: String,
    val url: String,
    val createdAt: Long = System.currentTimeMillis(),
)
