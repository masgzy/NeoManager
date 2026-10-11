/*
 * Copyright (C) 2026 The NeoManager Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.neomanager.ui.engine

import com.neomanager.engine.core.vfs.Vfs
import com.neomanager.engine.core.vfs.VfsCapability
import com.neomanager.engine.core.vfs.VfsEntry
import com.neomanager.engine.core.vfs.VfsUri
import java.io.IOException
import java.io.InputStream

/**
 * 动态提权后端包装（0.2.0-alpha07）。
 *
 * 背景：Shizuku 用户服务是「运行中逐步就绪」的（启动后自动绑定，先于服务可用发起的
 * 目录列举会失败）。若在装配期对 [com.neomanager.ui.engine.EngineBridge.elevatedVfs]
 * 取一次快照，服务后绑定永远不会反映到注册表，系统目录将一直「读取失败」。
 *
 * 本包装把「取提权后端」延迟到每次调用现场：每次操作现问 [provider]，
 * 服务就绪即刻生效；未就绪时返回带引导文案的失败（UI 层据此显示授权指引）。
 */
public class DynamicElevatedVfs(
    private val provider: () -> Vfs?,
) : Vfs {
    override fun capabilities(): Set<VfsCapability> = provider()?.capabilities() ?: emptySet()

    override fun list(dir: VfsUri): Result<List<VfsEntry>> = attempt("列出") { it.list(dir) }

    override fun openRead(file: VfsUri): Result<InputStream> = attempt("读取") { it.openRead(file) }

    override fun exists(uri: VfsUri): Boolean = provider()?.exists(uri) ?: false

    override fun isDirectory(uri: VfsUri): Boolean = provider()?.isDirectory(uri) ?: false

    override fun mkdir(dir: VfsUri): Result<Unit> = attempt("新建") { it.mkdir(dir) }

    override fun delete(
        uri: VfsUri,
        recursive: Boolean,
    ): Result<Unit> = attempt("删除") { it.delete(uri, recursive) }

    override fun rename(
        uri: VfsUri,
        newName: String,
    ): Result<VfsUri> = attempt("重命名") { it.rename(uri, newName) }

    override fun writeFile(
        target: VfsUri,
        content: InputStream,
        append: Boolean,
    ): Result<Long> = attempt("写入") { it.writeFile(target, content, append) }

    /** 现场取后端执行；未就绪返回可读失败（不含堆栈噪音） */
    private inline fun <T> attempt(
        verb: String,
        operation: (Vfs) -> Result<T>,
    ): Result<T> {
        val backend = provider() ?: return Result.failure(unavailable(verb))
        return operation(backend)
    }

    private fun unavailable(verb: String): IOException = IOException("没有权限${verb}该目录：系统目录需要 Root 或 Shizuku 提权（设置 → 引擎）")
}
