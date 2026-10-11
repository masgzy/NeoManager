/*
 * Copyright (C) 2026 The NeoManager Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.neomanager.ui.trash

import android.os.Environment
import com.neomanager.engine.core.vfs.VfsUri
import java.io.File

/**
 * 回收站约定（观察规格 2026-10-10-file-manager.md 第 8 节）：
 *
 * - 目录固定为内部存储根下的 [.DIR_NAME]（`.` 前缀隐藏目录）；
 * - 移入条目以「时间戳_原名」重命名防同名冲突，原始路径记录在应用私有索引
 *   （[com.neomanager.ui.bookmark.UiStateStore.trashIndex]）；
 * - 回收站内部与压缩包内部的删除不走回收站（直接永久删除）。
 */
public object Trash {
    /** 回收站目录名（内部存储根下；`.` 前缀对齐系统隐藏惯例） */
    public const val DIR_NAME: String = ".NeoTrash"

    /** 回收站目录（懒创建：仅在实际移入时创建，避免无谓占用） */
    public fun dir(): File {
        @Suppress("DEPRECATION")
        val root = Environment.getExternalStorageDirectory()
        return File(root, DIR_NAME)
    }

    /** 回收站目录的 VfsUri 字符串（抽屉跳转/位置判断共用） */
    public fun dirUri(): String = VfsUri.ofLocal(dir().absolutePath).value

    /**
     * 路径是否位于回收站内部（含回收站目录本身）；
     * 用于决定删除动作走回收站还是永久删除，以及是否展示「恢复」操作。
     */
    public fun isInside(path: String): Boolean {
        val uri = VfsUri.parseOrNull(path) ?: return false
        if (!uri.isLocal) return false
        val trashRoot = dir().absolutePath.trimEnd('/')
        val trashPrefix = "$trashRoot/"
        return uri.localPath == trashRoot || uri.localPath.startsWith(trashPrefix)
    }
}
