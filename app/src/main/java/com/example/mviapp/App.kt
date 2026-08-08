package com.example.mviapp

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application 入口
 *
 * @HiltAndroidApp 触发 Hilt 的代码生成，创建应用的依赖容器。
 * 所有通过 @Inject 注入的依赖都在此容器中管理。
 */
@HiltAndroidApp
class App : Application()
