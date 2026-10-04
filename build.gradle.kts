/*
 * Copyright (C) 2026 The NeoManager Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

// AGP 9 内置 Kotlin 支持：不再（也不得）在模块中 apply org.jetbrains.kotlin.android；
// 通过 buildscript classpath 将内置 KGP 提升到 2.4.20，与 Compose 编译器插件版本对齐。
// 升级路径见 https://developer.android.com/build/built-in-kotlin
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ktlint) apply false
}
