package com.example.mviapp.core.ui.display

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mviapp.core.ui.theme.LocalEInkMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * 显示设置的共享状态。
 *
 * 抽屉入口和 EPUB 阅读页底部页码点击共用同一份 [DisplaySettings] 单例，
 * 任意一侧修改都会立刻作用到主题与阅读页，无需层层传递回调。
 */
@HiltViewModel
class DisplaySettingsViewModel @Inject constructor(
    private val settings: DisplaySettings,
) : ViewModel() {
    val state = settings.state

    fun setEnabled(enabled: Boolean) = settings.setEnabled(enabled)

    fun setTextScale(scale: ReaderTextScale) = settings.setTextScale(scale)

    fun setMonochrome(value: Boolean) = settings.setMonochrome(value)
}

/**
 * 显示设置底部弹窗：阅读时点击 EPUB 页底部页码、或抽屉里的「显示设置」入口都会打开。
 *
 * 墨水屏高对比模式下改为纯白直角面板、去掉拖动手柄与色调高度，
 * 避免半透明灰和圆角抗锯齿落到低分辨率彩色层、以及动画残影。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisplaySettingsSheet(
    onDismiss: () -> Unit,
    viewModel: DisplaySettingsViewModel = hiltViewModel(),
) {
    val display by viewModel.state.collectAsStateWithLifecycle()
    val highContrast = LocalEInkMode.current
    val dragHandle: (@Composable () -> Unit)? = if (highContrast) {
        null
    } else {
        { BottomSheetDefaults.DragHandle() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = if (highContrast) {
            RectangleShape
        } else {
            RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        },
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        // 灰阶抖动在彩墨屏上容易留残影，遮罩统一用低不透明度纯黑
        scrimColor = Color.Black.copy(alpha = 0.25f),
        dragHandle = dragHandle,
    ) {
        DisplaySettingsContent(
            display = display,
            onEInkToggle = viewModel::setEnabled,
            onTextScaleChange = viewModel::setTextScale,
            onMonochromeChange = viewModel::setMonochrome,
        )
    }
}

/**
 * 电子墨水屏显示设置内容。
 *
 * 彩色水墨屏的彩色滤光片层分辨率远低于黑白层，文字统一成纯黑后能回到
 * 300ppi 黑白层，是最直接有效的清晰度提升；图片是否转灰度单独可选。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisplaySettingsContent(
    display: DisplayState,
    onEInkToggle: (Boolean) -> Unit,
    onTextScaleChange: (ReaderTextScale) -> Unit,
    onMonochromeChange: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
    ) {
        Text(
            text = "显示设置",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "电子墨水屏模式",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = if (display.isEInkDevice) {
                        "已识别为墨水屏设备"
                    } else {
                        "纯黑文字 · 加实笔画 · 减少残影"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = display.enabled, onCheckedChange = onEInkToggle)
        }

        if (display.enabled) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "阅读字号",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReaderTextScale.values().forEach { scale ->
                    FilterChip(
                        selected = display.textScale == scale,
                        onClick = { onTextScaleChange(scale) },
                        label = { Text(scale.label) },
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "黑白屏（图片转灰度）",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "彩色水墨屏请保持关闭，以保留彩色图片",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = display.monochrome, onCheckedChange = onMonochromeChange)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "阅读 EPUB 时，点击底部页码可直接打开此面板",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
