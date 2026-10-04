/*
 * Copyright (C) 2026 The NeoManager Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ktlint)
}

android {
    namespace = "com.neomanager.engine.android"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }
}

kotlin {
    jvmToolchain(21)
}
