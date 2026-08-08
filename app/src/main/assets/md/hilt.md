# Hilt 依赖注入

Hilt 基于 Dagger，是 Android 官方推荐的依赖注入方案。

## 基本用法

1. 在 Application 上添加 `@HiltAndroidApp`
2. 在 Activity 上添加 `@AndroidEntryPoint`
3. 用 `@Inject` 标记构造函数，Hilt 自动提供依赖

## 示例

```kotlin
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: HomeRepository,
) : MviViewModel<HomeIntent, HomeState, HomeEffect>(...) {
}
```

> 接口与实现通过 `@Binds` 绑定，替换实现只需要改一处。
