package com.example.mviapp.core.mvi

/**
 * MVI - Effect（副作用 / 一次性事件）
 *
 * 表示那些不该被 State 持有的一次性操作，例如：
 * - 显示 Toast / Snackbar
 * - 页面导航
 * - 弹出对话框
 *
 * 与 State 的区别：State 是持续的 UI 快照，而 Effect 是只消费一次的事件。
 * 通过 SharedFlow 发射，确保配置旋转后不会重复触发。
 *
 * 用法示例:
 *   sealed interface HomeEffect : MviEffect {
 *       data class ShowToast(val message: String) : HomeEffect
 *       data object NavigateToDetail : HomeEffect
 *   }
 */
interface MviEffect
