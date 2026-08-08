package com.example.mviapp.core.mvi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * MVI ViewModel 基类
 *
 * 泛型参数:
 * @param I  Intent 类型
 * @param S  State 类型
 * @param E  Effect 类型
 *
 * 核心职责:
 * 1. 暴露不可变的 [uiState]（StateFlow）供 Compose 观察渲染
 * 2. 暴露不可变的 [uiEffect]（Flow）供 Compose 消费一次性事件
 * 3. 提供 [dispatch] 方法接收 Intent，并路由到 [handleIntent] 处理
 *
 * 子类只需实现 [handleIntent]，在其中根据 Intent 更新 State 或发射 Effect。
 *
 * 使用示例:
 *   @HiltViewModel
 *   class HomeViewModel @Inject constructor() : MviViewModel<HomeIntent, HomeState, HomeEffect>(
 *       initialState = HomeState()
 *   ) {
 *       override fun handleIntent(intent: HomeIntent) {
 *           when (intent) {
 *               is HomeIntent.LoadData -> loadData()
 *               is HomeIntent.Increment -> updateState { it.copy(count = it.count + 1) }
 *           }
 *       }
 *   }
 */
abstract class MviViewModel<I : MviIntent, S : MviState, E : MviEffect>(
    initialState: S
) : ViewModel() {

    // ==================== State ====================
    private val _uiState = MutableStateFlow(initialState)
    val uiState: StateFlow<S> = _uiState.asStateFlow()

    // ==================== Effect ====================
    private val _uiEffect = Channel<E>(Channel.BUFFERED)
    val uiEffect: Flow<E> = _uiEffect.receiveAsFlow()

    // ==================== Intent 入口 ====================
    private val _intentFlow = MutableSharedFlow<I>(extraBufferCapacity = 64)

    init {
        // 订阅 Intent 流，路由到 handleIntent
        viewModelScope.launch {
            _intentFlow.collect(::handleIntent)
        }
    }

    /**
     * 从 UI 层派发 Intent，驱动状态变化。
     * 应在 Composable 中通过调用此方法响应用户操作。
     */
    fun dispatch(intent: I) {
        _intentFlow.tryEmit(intent)
    }

    /**
     * 子类实现：处理具体的 Intent，更新 State 或发射 Effect。
     */
    protected abstract suspend fun handleIntent(intent: I)

    // ==================== State 操作工具 ====================

    /**
     * 更新当前 State。使用 `update` 保证原子性。
     */
    protected fun updateState(reducer: (S) -> S) {
        _uiState.update(reducer)
    }

    /**
     * 获取当前 State 的快照值。
     */
    protected val currentState: S
        get() = _uiState.value

    // ==================== Effect 操作工具 ====================

    /**
     * 发射一次性 Effect（如 Toast、导航等）。
     */
    protected fun emitEffect(effect: E) {
        viewModelScope.launch {
            _uiEffect.send(effect)
        }
    }
}
