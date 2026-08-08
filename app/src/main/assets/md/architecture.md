# MVI 架构说明

MVI（Model-View-Intent）是一种**单向数据流**架构。

## 核心概念

1. **Intent（意图）**：对用户操作的描述
2. **Model（模型）**：不可变的 UI 状态
3. **View（视图）**：根据状态渲染的界面

## 数据流

```text
Intent → ViewModel → State → Compose 重组
```

## 代码示例

```kotlin
sealed interface HomeIntent : MviIntent {
    data object LoadItems : HomeIntent
    data class ClickItem(val id: String) : HomeIntent
}
```

> 单向数据流让状态变化可预测、易调试。
