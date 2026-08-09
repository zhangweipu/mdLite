package com.example.mviapp.feature.home

import com.example.mviapp.core.mvi.MviIntent

/**
 * 首页 Intent —— 用户意图
 *
 * sealed interface 确保编译器在 when 分支中做穷尽性检查，
 * 新增意图时所有 when 分支会提示补全。
 */
sealed interface HomeIntent : MviIntent {

    /** 进入页面时加载数据 */
    data object LoadItems : HomeIntent

    /** 下拉刷新 */
    data object Refresh : HomeIntent

    /** 页面回到前台时静默刷新（不显示 Loading / Toast） */
    data object RefreshSilent : HomeIntent

    /** 点击某条目 */
    data class ClickFile(val filePath: String, val title: String) : HomeIntent

    /** 删除一条阅读记录 */
    data class DeleteRecent(val source: String) : HomeIntent

    /** 置顶/取消置顶一条阅读记录 */
    data class TogglePin(val source: String) : HomeIntent

    /** 重命名显示名称（只改历史记录里的标题，不修改磁盘文件） */
    data class RenameTitle(
        val source: String,
        val currentTitle: String,
        val newName: String,
    ) : HomeIntent

    /** 清空全部阅读记录 */
    data object ClearRecent : HomeIntent

    /** 点击重试按钮（加载失败后） */
    data object Retry : HomeIntent
}
