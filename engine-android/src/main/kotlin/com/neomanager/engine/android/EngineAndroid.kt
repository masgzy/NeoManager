/*
 * Copyright (C) 2026 The NeoManager Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.neomanager.engine.android

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import com.neomanager.engine.core.vfs.Vfs
import com.topjohnwu.superuser.Shell
import dev.rikka.shizuku.Shizuku
import dev.rikka.shizuku.ShizukuUserServiceArgs
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 平台执行环境桥：探测 Root / Shizuku 可用性并提供提权 [Vfs]。
 *
 * 选择策略：Shizuku 优先（无需 root、权限边界清晰），Root 兜底。
 * 由 app 壳在 Application/Activity 启动时调用 [attach]，UI 经注入的接口使用。
 */
public object EngineAndroid {
    private var appContext: Context? = null

    @Volatile
    private var shizukuService: INeoFileService? = null

    @Volatile
    private var shizukuBound: Boolean = false

    /** 应用启动时挂接 */
    public fun attach(context: Context) {
        appContext = context.applicationContext
        Shell.enableVerboseLogging = false
        Shell.setDefaultBuilder(
            Shell.Builder
                .create()
                .setFlags(Shell.FLAG_REDIRECT_STDERR)
                .setTimeout(10),
        )
    }

    // ------------------------------------------------------------------
    // 探测
    // ------------------------------------------------------------------

    /** Root 可用性：su 存在且能拿到 UID 0 */
    public fun isRootAvailable(): Boolean =
        runCatching {
            Shell.isAppGrantedRoot() == true
        }.getOrElse {
            // 首次调用可能未建会话：同步探测一次
            runCatching { Shell.cmd("id -u").exec().let { it.isSuccess && it.out.firstOrNull()?.trim() == "0" } }
                .getOrDefault(false)
        }

    /** Shizuku 服务端是否在运行 */
    public fun isShizukuRunning(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    /** Shizuku 已授权本应用 */
    public fun isShizukuGranted(): Boolean =
        runCatching {
            Shizuku.pingBinder() &&
                Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)

    /** 是否安装了 Shizuku 应用（含 Sui） */
    public fun isShizukuInstalled(): Boolean =
        runCatching {
            val context = appContext ?: return false
            context.packageManager.getPackageInfo("moe.shizuku.manager", 0)
            true
        }.getOrElse {
            runCatching {
                appContext?.packageManager?.getPackageInfo("org.rikka.sui", 0)
                true
            }.getOrDefault(false)
        }

    // ------------------------------------------------------------------
    // Shizuku 用户服务
    // ------------------------------------------------------------------

    /** 绑定 Shizuku 用户服务（须先授权）；完成回调在主线程之外不限 */
    public suspend fun bindShizukuService(): Result<INeoFileService> =
        suspendCancellableCoroutine { continuation ->
            if (shizukuBound && shizukuService != null) {
                continuation.resume(Result.success(shizukuService!!))
                return@suspendCancellableCoroutine
            }
            val context = appContext
            if (context == null || !isShizukuGranted()) {
                continuation.resume(Result.failure(IllegalStateException("Shizuku 未授权或未初始化")))
                return@suspendCancellableCoroutine
            }
            val args =
                ShizukuUserServiceArgs(ComponentName(context, NeoFileService::class.java))
                    .version(1)
                    .processNameSuffix("file")
                    .debuggable(false)
            val connection =
                object : ServiceConnection {
                    override fun onServiceConnected(
                        name: ComponentName?,
                        binder: IBinder?,
                    ) {
                        if (binder != null && binder.pingBinder()) {
                            shizukuService = INeoFileService.Stub.asInterface(binder)
                            shizukuBound = true
                            continuation.resume(Result.success(shizukuService!!))
                        } else {
                            continuation.resume(Result.failure(IllegalStateException("Shizuku 服务 Binder 无效")))
                        }
                    }

                    override fun onServiceDisconnected(name: ComponentName?) {
                        shizukuService = null
                        shizukuBound = false
                    }
                }
            try {
                Shizuku.bindUserService(args, connection)
            } catch (e: Exception) {
                continuation.resume(Result.failure(e))
            }
        }

    /** 请求 Shizuku 权限（结果经 Shizuku.addRequestPermissionResultListener 通知，UI 侧刷新探测） */
    public fun requestShizukuPermission(requestCode: Int = 0) {
        runCatching { Shizuku.requestPermission(requestCode) }
    }

    // ------------------------------------------------------------------
    // 提权后端选择
    // ------------------------------------------------------------------

    /**
     * 当前可用的提权后端：Shizuku 服务就绪 → [ShizukuVfs]；
     * 否则 Root 可用 → [RootVfs]；都不可用 → null。
     */
    public fun elevatedVfs(): Vfs? {
        val shizuku = shizukuService
        if (shizukuBound && shizuku != null) return ShizukuVfs(shizuku)
        return if (isRootAvailable()) RootVfs else null
    }

    /** 解绑 Shizuku 用户服务（设置页手动关闭时调用） */
    public fun unbindShizuku() {
        shizukuService = null
        shizukuBound = false
    }
}
