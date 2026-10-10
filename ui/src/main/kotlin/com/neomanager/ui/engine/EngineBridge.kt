/*
 * Copyright (C) 2026 The NeoManager Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.neomanager.ui.engine

import androidx.compose.runtime.staticCompositionLocalOf
import com.neomanager.engine.core.vfs.Vfs

/**
 * 平台引擎桥（UI 层视角）：由 app 壳注入实现（engine-android 的 EngineAndroid）。
 *
 * UI 只面向本接口：探测 Root/Shizuku 状态、请求权限、获取提权浏览后端。
 * 引擎红线：UI 不 import engine-android，经本接口解耦。
 */
public interface EngineBridge {
    /** Root 是否可用（su 授权通过） */
    public fun isRootAvailable(): Boolean

    /** Shizuku 服务端是否在运行 */
    public fun isShizukuRunning(): Boolean

    /** Shizuku 是否已授权本应用 */
    public fun isShizukuGranted(): Boolean

    /** 请求 Shizuku 授权（异步，结果由调用方重新探测） */
    public fun requestShizukuPermission()

    /** 绑定 Shizuku 用户服务（挂起；成功后 elevatedVfs 优先返回 Shizuku 后端） */
    public suspend fun bindShizukuService(): Result<Unit>

    /** 当前可用的提权后端（Shizuku → Root → null） */
    public fun elevatedVfs(): Vfs?
}

/** 默认空实现：未接入平台时全部返回不可用 */
public object NoopEngineBridge : EngineBridge {
    override fun isRootAvailable(): Boolean = false

    override fun isShizukuRunning(): Boolean = false

    override fun isShizukuGranted(): Boolean = false

    override fun requestShizukuPermission() {}

    override suspend fun bindShizukuService(): Result<Unit> = Result.failure(IllegalStateException("未接入平台实现"))

    override fun elevatedVfs(): Vfs? = null
}

/** CompositionLocal 注入口；app 壳在根组合处提供 */
public val LocalEngineBridge: androidx.compose.runtime.ProvidableCompositionLocal<EngineBridge> =
    staticCompositionLocalOf { NoopEngineBridge }
