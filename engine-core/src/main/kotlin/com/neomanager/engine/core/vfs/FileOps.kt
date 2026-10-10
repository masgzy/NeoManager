/*
 * Copyright (C) 2026 The NeoManager Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.neomanager.engine.core.vfs

import java.io.IOException
import java.io.InputStream

/**
 * 跨文件系统文件操作引擎：复制 / 移动 / 目录树递归。
 *
 * 职责：当源与目标分属不同 [Vfs]（如本地 ↔ zip 内部）时提供流式搬运；
 * 同后端时交给源后端的快速路径（NIO rename 等）。
 *
 * 全部方法为同步阻塞调用，调用方负责线程调度与进度通知。
 */
public class FileOps(
    private val registry: VfsRegistry,
) {
    /** 单文件/目录复制到目标目录下（保留原名），返回新条目 URI */
    public fun copyInto(
        source: VfsUri,
        targetDir: VfsUri,
    ): Result<VfsUri> =
        runCatching {
            val srcVfs = registry.resolve(source)
            val dstVfs = registry.resolve(targetDir)
            require(srcVfs.exists(source)) { "源不存在：${source.value}" }
            require(dstVfs.isDirectory(targetDir)) { "目标不是目录：${targetDir.value}" }

            val target = targetDir.resolve(source.name)
            if (srcVfs === dstVfs && srcVfs is LocalVfs) {
                // 同本地卷：先试 rename 快路径，失败再回落流式
                val fast = srcVfs.moveFast(source, targetDir)
                if (fast.isSuccess) return@runCatching fast.getOrThrow()
                streamCopy(srcVfs, source, dstVfs, target)
            } else {
                streamCopy(srcVfs, source, dstVfs, target)
            }
            target
        }

    /** 移动 = 复制 + 删除源；目标同后端且为本地时退化为快速 rename */
    public fun moveInto(
        source: VfsUri,
        targetDir: VfsUri,
    ): Result<VfsUri> =
        runCatching {
            val srcVfs = registry.resolve(source)
            val dstVfs = registry.resolve(targetDir)
            if (srcVfs === dstVfs && srcVfs is LocalVfs) {
                srcVfs.moveFast(source, targetDir).getOrThrow()
            } else {
                copyInto(source, targetDir).getOrThrow()
                srcVfs.delete(source, recursive = true).getOrThrow()
            }
            targetDir.resolve(source.name)
        }

    /** 递归流式复制 */
    private fun streamCopy(
        srcVfs: Vfs,
        source: VfsUri,
        dstVfs: Vfs,
        target: VfsUri,
    ) {
        val isDir = srcVfs.isDirectory(source)
        if (isDir) {
            dstVfs.mkdir(target).getOrThrow()
            val children = srcVfs.list(source).getOrThrow()
            for (child in children) {
                streamCopy(srcVfs, child.uri, dstVfs, target.resolve(child.name))
            }
        } else {
            val input = srcVfs.openRead(source).getOrThrow()
            try {
                dstVfs.writeFile(target, input, append = false).getOrThrow()
            } finally {
                input.closeQuietly()
            }
        }
    }

    private fun InputStream.closeQuietly() {
        try {
            close()
        } catch (_: IOException) {
        }
    }
}
