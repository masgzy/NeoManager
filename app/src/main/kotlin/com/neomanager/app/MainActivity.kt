/*
 * Copyright (C) 2026 The NeoManager Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.neomanager.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.neomanager.engine.android.EngineAndroid
import com.neomanager.engine.core.vfs.Vfs
import com.neomanager.ui.engine.EngineBridge
import com.neomanager.ui.theme.NeoTheme

/**
 * 单 Activity 入口（蓝图 5 决策）。
 *
 * 全部 UI 由 Jetpack Compose 承载，页面间导航走 Compose Navigation（[NeoApp]）；
 * 平台引擎经 [EngineBridge] 接口注入（UI 不感知 engine-android 实现类）。
 */
public class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NeoTheme {
                val bridge =
                    object : EngineBridge {
                        override fun isRootAvailable(): Boolean = EngineAndroid.isRootAvailable()

                        override fun isShizukuRunning(): Boolean = EngineAndroid.isShizukuRunning()

                        override fun isShizukuGranted(): Boolean = EngineAndroid.isShizukuGranted()

                        override fun requestShizukuPermission() = EngineAndroid.requestShizukuPermission()

                        override suspend fun bindShizukuService(): Result<Unit> =
                            EngineAndroid.bindShizukuService().map { }

                        override fun elevatedVfs(): Vfs? = EngineAndroid.elevatedVfs()
                    }
                NeoApp(
                    versionName = BuildConfig.VERSION_NAME,
                    engineBridge = bridge,
                )
            }
        }
    }
}
