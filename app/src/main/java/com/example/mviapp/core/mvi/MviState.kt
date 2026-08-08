package com.example.mviapp.core.mvi

/**
 * MVI - State（状态）
 *
 * 表示 UI 在某一时刻的完整快照，是不可变的。
 * Compose 直接根据 State 渲染界面，State 变化时 UI 自动重组。
 *
 * 用法示例:
 *   data class HomeState(
 *       val isLoading: Boolean = false,
 *       val title: String = "",
 *       val items: List<String> = emptyList(),
 *   ) : MviState
 */
interface MviState
