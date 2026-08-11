package com.example.mviapp.feature.github

import android.net.Uri
import com.example.mviapp.core.mvi.MviIntent

/** GitHub 文件页 Intent */
sealed interface GithubIntent : MviIntent {

    /** 提交仓库地址进行连接 */
    data class Connect(val repoUrl: String) : GithubIntent

    /** 返回输入状态（重新输入地址） */
    data object Reset : GithubIntent

    /** 刷新当前目录 */
    data object Refresh : GithubIntent

    /** 进入子目录 */
    data class EnterDirectory(val path: String) : GithubIntent

    /** 返回上一级目录 */
    data object NavigateUp : GithubIntent

    /** 下载文件到用户选择的保存位置 */
    data class DownloadFile(val name: String, val downloadUrl: String, val destinationUri: Uri) : GithubIntent
}
