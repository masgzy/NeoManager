/*
 * Copyright (C) 2026 The NeoManager Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.neomanager.engine.android

/**
 * engine-android：Android 平台绑定层占位。
 *
 * 本模块负责引擎层与 Android 平台能力的桥接，规划接入：
 * - Root 执行环境（libsu / Shell 委托）
 * - Shizuku 服务绑定
 * - 系统安装器（PackageInstaller）与存储后端
 *
 * 架构红线：本模块允许依赖 Android SDK 与 Apache-2.0 系三方库；
 * 所有平台能力必须收敛为接口注入，禁止泄漏进 [com.neomanager.engine.core]（纯 Kotlin）。
 *
 * 实际接入安排在 Phase 1 第②步（引擎接入：双窗口浏览 + 压缩解压 + root/shizuku 读写）。
 */
public object EngineAndroidBindings {
    /**
     * 模块就绪状态标记：true 表示已接入至少一种平台执行环境。
     * Phase 1 ② 完成后由真实探测逻辑替换。
     */
    public const val PLACEHOLDER: Boolean = false
}
