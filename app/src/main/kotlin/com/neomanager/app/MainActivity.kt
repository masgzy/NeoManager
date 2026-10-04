/*
 * Copyright (C) 2026 The NeoManager Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.neomanager.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.neomanager.ui.theme.NeoTheme

/**
 * 单 Activity 入口（蓝图 5 决策）。
 *
 * 全部 UI 由 Jetpack Compose 承载，页面间导航走 Compose Navigation（[NeoApp]）；
 * 引擎回调统一经 ViewModel 桥接（Phase 1 ② 引入）。
 */
public class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NeoTheme {
                NeoApp(versionName = BuildConfig.VERSION_NAME)
            }
        }
    }
}
