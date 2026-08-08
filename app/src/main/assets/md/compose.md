# Jetpack Compose 入门

Compose 是 Android 的**声明式 UI** 工具包。

## 常用概念

- `Composable`：可组合函数，描述界面
- `State`：状态变化自动触发重组
- `Modifier`：修饰符，控制布局与外观

### 一个简单的示例

```kotlin
@Composable
fun Greeting(name: String) {
    Text(text = "Hello, $name!")
}
```

## 为什么选择 Compose

* 代码更少，UI 即代码
* 与状态驱动架构（MVI）配合良好
* 自动适配不同屏幕尺寸

[了解更多](https://developer.android.com/develop/ui/compose)
