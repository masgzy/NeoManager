/*
 * Copyright (C) 2026 The NeoManager Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    // 归档/压缩引擎（Apache-2.0 / BSD-2 / Public Domain / unRar，见 THIRD-PARTY-NOTICES.md）
    api(libs.commons.compress)
    implementation(libs.zstd.jni)
    implementation(libs.xz.java)
    implementation(libs.junrar)

    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}
