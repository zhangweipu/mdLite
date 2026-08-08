package com.example.mviapp.feature.files

import com.example.mviapp.core.mvi.MviIntent

/**
 * 文件夹浏览页 Intent
 */
sealed interface FilesIntent : MviIntent {

    /** 进入页面时加载当前文件夹 */
    data object Load : FilesIntent

    /** 打开子文件夹 */
    data class OpenDirectory(val uri: String) : FilesIntent

    /** 打开 md 文件 */
    data class OpenFile(val uri: String, val title: String) : FilesIntent

    /** 加载失败后重试 */
    data object Retry : FilesIntent
}
