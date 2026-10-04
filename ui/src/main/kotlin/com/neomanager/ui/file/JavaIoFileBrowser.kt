/*
 * Copyright (C) 2026 The NeoManager Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.neomanager.ui.file

import java.io.File
import java.io.IOException

/**
 * 基于 java.io.File 的临时浏览实现（Phase 1 ① 演示用）。
 *
 * ⚠️ 这是 UI 骨架期的过渡数据源：仅支持真实文件系统直读，
 * 无 root 能力、无压缩包内部视图。Phase 1 ② 引擎接入后整体移除，
 * 由 engine-core 的统一 IO 引擎实现 [FileBrowser] 接替。
 */
public class JavaIoFileBrowser : FileBrowser {
    override fun list(path: String): Result<List<FileEntry>> =
        runCatching {
            val dir = File(if (path.isEmpty()) "/" else path)
            if (!dir.exists()) throw IOException("路径不存在：${dir.absolutePath}")
            if (!dir.isDirectory) throw IOException("不是目录：${dir.absolutePath}")

            val children =
                dir.listFiles()
                    ?: throw IOException("无法读取目录内容（可能无访问权限）：${dir.absolutePath}")

            children
                .map { child ->
                    FileEntry(
                        name = child.name,
                        path = child.absolutePath,
                        isDirectory = child.isDirectory,
                        sizeBytes = if (child.isFile) child.length() else 0L,
                        lastModified = child.lastModified(),
                    )
                }.sortedWith(
                    compareByDescending<FileEntry> { it.isDirectory }
                        .thenBy { it.name.lowercase() },
                )
        }
}
