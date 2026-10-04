/*
 * Copyright (C) 2026 The NeoManager Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.neomanager.ui.file

/**
 * 文件列表条目（UI 层展示模型）。
 *
 * 独立于真实文件系统 API：后续 engine-core 提供的浏览引擎（含 ZIP 内部、root 目录）
 * 都能映射到此模型，UI 侧零改动换引擎。
 *
 * @property name 文件或目录名（不含路径）
 * @property path 完整路径（分隔符统一为 '/'）
 * @property isDirectory 是否目录
 * @property sizeBytes 文件字节数（目录恒为 0，目录大小不递归统计）
 * @property lastModified 最后修改时间（epoch 毫秒；未知为 0）
 */
public data class FileEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long = 0L,
    val lastModified: Long = 0L,
)
