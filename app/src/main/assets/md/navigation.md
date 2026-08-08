# Navigation 与路由

项目使用 Navigation Compose 管理页面跳转。

## 路由定义

所有路由集中在 `Screen.kt`：

```kotlin
sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Reader : Screen("reader")
}
```

## 传参

```kotlin
navController.navigate(Screen.Reader.buildRoute(filePath))
```

- 参数通过 **URL 编码**传递，路径中包含 `/` 也没问题
- 阅读页在 `AppNavHost` 中注册并解析参数

---

开始阅读：[README](README.md)
