/*
 * Copyright (C) 2026 The NeoManager Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.neomanager.engine.android

/**
 * engine-android：Android 平台绑定层（Phase 1 ② 已实装）。
 *
 * - [EngineAndroid]：Root(libsu) / Shizuku 可用性探测与提权后端选择
 * - [RootVfs]：root shell 文件后端
 * - [ShizukuVfs] + [NeoFileService]：Shizuku 用户服务文件后端（AIDL）
 *
 * 架构红线：本模块允许依赖 Android SDK 与 Apache-2.0 系三方库；
 * 所有平台能力收敛为接口注入，禁止泄漏进 engine-core（纯 Kotlin）。
 */
public object EngineAndroidBindings {
    /** 已实装的提权后端数量（Root / Shizuku） */
    public const val IMPLEMENTED_BACKENDS: Int = 2
}
