package com.example.mviapp.feature.landrop

import com.example.mviapp.core.mvi.MviEffect

/** LanDrop 下载页一次性事件 */
sealed interface LanDropEffect : MviEffect {
    data class ShowToast(val message: String) : LanDropEffect
}
