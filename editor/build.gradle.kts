/*
 * Copyright (C) 2026 The NeoManager Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ktlint)
}

android {
    namespace = "com.neomanager.editor"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }
}

kotlin {
    jvmToolchain(21)
}

// 依赖协议说明（蓝图 5.2 / 7.2）：
// sora-editor（io.github.Rosemoe.sora-editor）以 LGPL-2.1 动态依赖方式引入——
// 仅作为独立组件运行时不修改其源码，与 GPL-3.0-or-later 主项目协议兼容。
// 接入安排在 Phase 1 第②步：AndroidView(factory = {...}) 互操作封装 + 语法高亮配置。
