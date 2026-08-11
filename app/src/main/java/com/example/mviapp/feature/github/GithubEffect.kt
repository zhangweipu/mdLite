package com.example.mviapp.feature.github

import com.example.mviapp.core.mvi.MviEffect

/** GitHub 文件页一次性事件 */
sealed interface GithubEffect : MviEffect {
    data class ShowToast(val message: String) : GithubEffect
}
