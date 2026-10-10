/*
 * Copyright (C) 2026 The NeoManager Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
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

dependencies {
    // sora-editor（LGPL-2.1，动态依赖方式使用，见 THIRD-PARTY-NOTICES.md）
    implementation(libs.sora.editor)

    // Compose 互操作（AndroidView 承载 View 体系编辑器）
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
}

// 依赖协议说明（蓝图 5.2 / 7.2）：
// sora-editor 以 LGPL-2.1 动态依赖方式引入——仅作为独立组件运行时不修改其源码，
// 与 GPL-3.0-or-later 主项目协议兼容。
