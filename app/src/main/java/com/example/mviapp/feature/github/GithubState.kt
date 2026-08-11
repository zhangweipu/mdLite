package com.example.mviapp.feature.github

import com.example.mviapp.core.github.GithubFile
import com.example.mviapp.core.mvi.MviState

/** GitHub 文件页 UI 状态 */
sealed interface GithubState : MviState {

    /** 未连接仓库（输入地址阶段） */
    data object Idle : GithubState

    /** 正在加载文件列表 */
    data class Loading(
        val owner: String,
        val repo: String,
        val currentPath: String = "",
    ) : GithubState

    /** 已连接，展示当前目录下的文件列表 */
    data class Connected(
        val owner: String,
        val repo: String,
        val currentPath: String,
        val files: List<GithubFile>,
        val downloadingName: String? = null,
        val progress: Int = 0,
    ) : GithubState {
        val isDownloading: Boolean get() = downloadingName != null
        val pathSegments: List<String>
            get() = currentPath.split("/").filter { it.isNotEmpty() }
    }

    /** 加载失败 */
    data class Error(val message: String) : GithubState
}
