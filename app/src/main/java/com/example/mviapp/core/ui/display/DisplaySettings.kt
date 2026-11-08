package com.example.mviapp.core.ui.display

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** 「电子墨水屏模式」用户选择：自动（按设备识别）/ 强制开 / 强制关 */
enum class EInkPreference { AUTO, ON, OFF }

/**
 * 阅读字号档位。用 CSS px 而不是 WebView 的 textZoom 缩放：
 * 整数倍以外的缩放会引入插值，在墨水屏上直接表现为字边缘发虚。
 */
enum class ReaderTextScale(val scale: Float, val label: String) {
    SMALL(0.9f, "小"),
    NORMAL(1.0f, "标准"),
    LARGE(1.18f, "大"),
    XLARGE(1.36f, "特大");

    companion object {
        fun from(name: String?): ReaderTextScale =
            values().firstOrNull { it.name == name } ?: NORMAL
    }
}

/** 当前显示配置的不可变快照 */
data class DisplayState(
    val preference: EInkPreference = EInkPreference.AUTO,
    val textScale: ReaderTextScale = ReaderTextScale.NORMAL,
    /** 黑白屏：把图片也转成灰度。彩色水墨屏保持关闭以保留彩色图片 */
    val monochrome: Boolean = false,
    val isEInkDevice: Boolean = false,
) {
    val enabled: Boolean
        get() = preference == EInkPreference.ON ||
            (preference == EInkPreference.AUTO && isEInkDevice)
}

/**
 * 全应用显示设置（Hilt 单例）。
 *
 * 用 StateFlow 而不是只在 SharedPreferences 里读写，是为了让主题、顶部栏动画、
 * 阅读页 WebView 能在用户切换开关时立即响应，无需重启 Activity。
 */
@Singleton
class DisplaySettings @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 设备是否为电子墨水屏，只在进程启动时检测一次 */
    val isEInkDevice: Boolean = runCatching {
        EInkDeviceDetector.isLikelyEInk(
            Build.BRAND,
            Build.MODEL,
            Build.MANUFACTURER,
            Build.DEVICE,
            Build.PRODUCT,
            Build.HARDWARE,
        )
    }.getOrDefault(false)

    private val _state = MutableStateFlow(read())
    val state: StateFlow<DisplayState> = _state.asStateFlow()

    fun setEnabled(enabled: Boolean) = update {
        it.copy(preference = if (enabled) EInkPreference.ON else EInkPreference.OFF)
    }

    fun setPreference(preference: EInkPreference) = update { it.copy(preference = preference) }

    fun setTextScale(scale: ReaderTextScale) = update { it.copy(textScale = scale) }

    fun setMonochrome(monochrome: Boolean) = update { it.copy(monochrome = monochrome) }

    private fun read(): DisplayState = DisplayState(
        preference = runCatching {
            EInkPreference.valueOf(
                prefs.getString(KEY_PREFERENCE, EInkPreference.AUTO.name)
                    ?: EInkPreference.AUTO.name,
            )
        }.getOrDefault(EInkPreference.AUTO),
        textScale = ReaderTextScale.from(prefs.getString(KEY_TEXT_SCALE, null)),
        monochrome = prefs.getBoolean(KEY_MONOCHROME, false),
        isEInkDevice = isEInkDevice,
    )

    private fun update(block: (DisplayState) -> DisplayState) {
        val next = block(_state.value)
        if (next == _state.value) return
        prefs.edit()
            .putString(KEY_PREFERENCE, next.preference.name)
            .putString(KEY_TEXT_SCALE, next.textScale.name)
            .putBoolean(KEY_MONOCHROME, next.monochrome)
            .apply()
        _state.value = next
    }

    private companion object {
        const val PREFS_NAME = "display_settings"
        const val KEY_PREFERENCE = "eink_preference"
        const val KEY_TEXT_SCALE = "reader_text_scale"
        const val KEY_MONOCHROME = "monochrome"
    }
}
