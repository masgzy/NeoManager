/*
 * Copyright (C) 2026 The NeoManager Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.neomanager.app

import android.app.Application
import com.neomanager.engine.android.EngineAndroid

/** 应用入口：装配平台引擎（libsu 初始化 / Shizuku 上下文挂接） */
public class NeoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        EngineAndroid.attach(this)
    }
}
