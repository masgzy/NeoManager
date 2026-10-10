/*
 * Copyright (C) 2026 The NeoManager Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.neomanager.ui.file

import com.neomanager.engine.core.vfs.VfsRegistry
import com.neomanager.engine.core.vfs.VfsUri

/**
 * 引擎后端文件浏览：[VfsRegistry]（本地 + zip 嵌套 + 提权回退）→ UI [FileEntry] 映射。
 *
 * 接替 Phase 1 ① 的 [JavaIoFileBrowser]（保留作回退参照，不再装配）。
 * path 参数即 VfsUri 字符串（file:// 与 zip: 两种方案）。
 */
public class EngineFileBrowser(
    private val registry: VfsRegistry,
) : FileBrowser {
    override fun list(path: String): Result<List<FileEntry>> {
        val uri = VfsUri.parseOrNull(path) ?: return Result.failure(IllegalArgumentException("无效路径：$path"))
        return registry.list(uri).map { entries -> entries.map { it.toUiEntry() } }
    }
}

private fun com.neomanager.engine.core.vfs.VfsEntry.toUiEntry(): FileEntry =
    FileEntry(
        name = name,
        path = uri.value,
        isDirectory = isDirectory,
        sizeBytes = sizeBytes,
        lastModified = lastModified,
    )
