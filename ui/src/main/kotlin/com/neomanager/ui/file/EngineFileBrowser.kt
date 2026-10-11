/*
 * Copyright (C) 2026 The NeoManager Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.neomanager.ui.file

import com.neomanager.engine.core.vfs.VfsRegistry
import com.neomanager.engine.core.vfs.VfsUri
import java.io.IOException
import java.nio.file.AccessDeniedException

/**
 * 引擎后端文件浏览：[VfsRegistry]（本地 + zip 嵌套 + 提权回退）→ UI [FileEntry] 映射。
 *
 * 接替 Phase 1 ① 的 [JavaIoFileBrowser]（保留作回退参照，不再装配）。
 * path 参数即 VfsUri 字符串（file:// 与 zip: 两种方案）。
 *
 * 0.2.0-alpha07：权限类失败统一翻译为带引导的中文文案（提示经 Root/Shizuku 提权）。
 */
public class EngineFileBrowser(
    private val registry: VfsRegistry,
) : FileBrowser {
    override fun list(path: String): Result<List<FileEntry>> {
        val uri = VfsUri.parseOrNull(path) ?: return Result.failure(IllegalArgumentException("无效路径：$path"))
        return registry
            .list(uri)
            .fold(
                onSuccess = { Result.success(it) },
                onFailure = { e -> Result.failure(IOException(readableMessage(e), e)) },
            ).map { entries -> entries.map { it.toUiEntry() } }
    }

    /** 权限类异常翻译为人类可读引导；其余保留原始消息 */
    private fun readableMessage(e: Throwable): String =
        when (e) {
            is AccessDeniedException, is SecurityException ->
                "没有权限访问该目录：系统目录需要 Root 或 Shizuku 提权（设置 → 引擎）"
            else -> e.message ?: e.javaClass.simpleName
        }
}

private fun com.neomanager.engine.core.vfs.VfsEntry.toUiEntry(): FileEntry =
    FileEntry(
        name = name,
        path = uri.value,
        isDirectory = isDirectory,
        sizeBytes = sizeBytes,
        lastModified = lastModified,
        permissions = permissions,
    )
