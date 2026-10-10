/*
 * Copyright (C) 2026 The NeoManager Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.neomanager.engine.core.vfs

import java.io.IOException
import java.io.InputStream

/**
 * 虚拟文件系统注册表：URI → 后端解析 + 提权回退。
 *
 * - `file://` → [LocalVfs]；不可读时若提供 [elevated]（Root/Shizuku 后端）则回退；
 * - `zip:…`   → [ZipVfs]（容器递归解析）；
 * - [ops] 提供跨后端复制/移动。
 */
public class VfsRegistry(
    /** 提权后端（Root/Shizuku），由 engine-android 装配注入；可为 null */
    public val elevated: Vfs? = null,
) {
    private val fileOpsLazy by lazy { FileOps(this) }

    /** 跨后端文件操作引擎 */
    public val ops: FileOps get() = fileOpsLazy

    /** 解析 URI 对应后端 */
    public fun resolve(uri: VfsUri): Vfs =
        when (uri.scheme) {
            VfsUri.SCHEME_FILE ->
                if (elevated == null) {
                    LocalVfs.INSTANCE
                } else {
                    CompositeVfs(LocalVfs.INSTANCE, elevated)
                }
            VfsUri.SCHEME_ZIP -> ZipVfs(this, uri.containerUri)
            else -> throw UnsupportedOperationException("未知文件系统方案：${uri.scheme}")
        }

    /** 便捷列目录（含提权回退语义） */
    public fun list(dir: VfsUri): Result<List<VfsEntry>> = runCatching { resolve(dir).list(dir).getOrThrow() }

    /**
     * 组合后端：主后端失败且存在提权后端时自动重试（仅本地路径）。
     * 写操作在主后端只读失败时同样走提权后端。
     */
    public class CompositeVfs(
        private val primary: Vfs,
        private val fallback: Vfs?,
    ) : Vfs {
        private fun <T> attempt(operation: (Vfs) -> Result<T>): Result<T> {
            val first = operation(primary)
            if (first.isSuccess || fallback == null) return first
            return operation(fallback)
        }

        override fun capabilities(): Set<VfsCapability> =
            primary.capabilities() + (fallback?.capabilities() ?: emptySet())

        override fun list(dir: VfsUri): Result<List<VfsEntry>> = attempt { it.list(dir) }

        override fun openRead(file: VfsUri): Result<InputStream> = attempt { it.openRead(file) }

        override fun exists(uri: VfsUri): Boolean = primary.exists(uri) || (fallback?.exists(uri) ?: false)

        override fun isDirectory(uri: VfsUri): Boolean =
            primary.isDirectory(uri) || (fallback?.isDirectory(uri) ?: false)

        override fun mkdir(dir: VfsUri): Result<Unit> = attempt { it.mkdir(dir) }

        override fun delete(
            uri: VfsUri,
            recursive: Boolean,
        ): Result<Unit> = attempt { it.delete(uri, recursive) }

        override fun rename(
            uri: VfsUri,
            newName: String,
        ): Result<VfsUri> = attempt { it.rename(uri, newName) }

        override fun writeFile(
            target: VfsUri,
            content: InputStream,
            append: Boolean,
        ): Result<Long> = attempt { it.writeFile(target, content, append) }

        public companion object {
            /** 供装配层构建语义化错误 */
            public fun unavailable(what: String): IOException = IOException("无法访问：$what（且无可用提权后端）")
        }
    }
}
