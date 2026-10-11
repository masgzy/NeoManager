/*
 * Copyright (C) 2026 The NeoManager Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.neomanager.ui.dualpane.actions

import com.neomanager.ui.file.FileEntry
import com.neomanager.ui.file.FilePaneState

/** 长按操作的目标：条目 + 所属面板 */
public data class ActionTarget(
    val entry: FileEntry,
    val pane: FilePaneState,
)

/** 排序对话框状态 */
public data class SortDialogState(
    val state: FilePaneState,
)

/** 各类对话框载荷 */
public sealed class DialogKind {
    /** 新建（MT 惯例：同一对话框内选择创建文件或文件夹） */
    public data object Create : DialogKind()

    /** 重命名 */
    public data class Rename(
        val entry: FileEntry,
    ) : DialogKind()

    /**
     * 删除确认（pane = 删除后需刷新的面板）。
     * [canTrash] 为 true 时提供「移入回收站」默认动作与「直接删除」并列；
     * 回收站内部或压缩包内条目只能永久删除。
     */
    public data class ConfirmDelete(
        val entries: List<FileEntry>,
        val pane: FilePaneState,
        val canTrash: Boolean = false,
    ) : DialogKind()

    /** 压缩为 zip */
    public data class Compress(
        val entry: FileEntry,
    ) : DialogKind()

    /** 属性查看 */
    public data class Properties(
        val entry: FileEntry,
    ) : DialogKind()

    /**
     * 添加书签（0.2.0-alpha08 支持分组）：预填展示名，分组可留空。
     */
    public data class AddBookmark(
        val path: String,
        val initialName: String,
    ) : DialogKind()
}

/** 剪贴板：源条目 + 是否移动模式 */
public data class Clipboard(
    val entries: List<FileEntry>,
    val move: Boolean,
)
