package com.example.mviapp.core.mvi

/**
 * MVI - Intent（意图）
 *
 * 表示用户的操作或系统事件，是触发状态变化的唯一来源。
 * 每个功能模块定义自己的 Intent 实现。
 *
 * 用法示例:
 *   sealed interface HomeIntent : MviIntent {
 *       data object LoadData : HomeIntent
 *       data class UpdateTitle(val title: String) : HomeIntent
 *   }
 */
interface MviIntent
