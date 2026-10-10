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
    /** 新建文件夹 */
    public data object NewFolder : DialogKind()

    /** 重命名 */
    public data class Rename(
        val entry: FileEntry,
    ) : DialogKind()

    /** 删除确认 */
    public data class ConfirmDelete(
        val entries: List<FileEntry>,
    ) : DialogKind()

    /** 压缩为 zip */
    public data class Compress(
        val entry: FileEntry,
    ) : DialogKind()

    /** 属性查看 */
    public data class Properties(
        val entry: FileEntry,
    ) : DialogKind()
}

/** 剪贴板：源条目 + 是否移动模式 */
public data class Clipboard(
    val entries: List<FileEntry>,
    val move: Boolean,
)
