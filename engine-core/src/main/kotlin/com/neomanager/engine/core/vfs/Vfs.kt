/*
 * Copyright (C) 2026 The NeoManager Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.neomanager.engine.core.vfs

/**
 * 统一文件条目模型：本地目录、root 目录、Shizuku 目录、ZIP 内部条目共用。
 *
 * @property name 条目名（不含路径分隔符；符号链接保留原名）
 * @property uri 完整 [VfsUri]（可直接用于导航与再解析）
 * @property isDirectory 是否目录
 * @property isSymlink 是否符号链接（root/Shizuku 后端提供；本地后端由 NIO 判断）
 * @property linkTarget 符号链接目标（非链接为 null）
 * @property sizeBytes 文件字节数（目录为 0，不递归统计）
 * @property lastModified 最后修改时间（epoch 毫秒；后端无法提供时为 0）
 * @property permissions 权限串（如 `drwxrwx--x`；后端无法提供时为 null）
 */
public data class VfsEntry(
    val name: String,
    val uri: VfsUri,
    val isDirectory: Boolean,
    val isSymlink: Boolean = false,
    val linkTarget: String? = null,
    val sizeBytes: Long = 0L,
    val lastModified: Long = 0L,
    val permissions: String? = null,
) {
    /** 目录优先、名称不区分大小写升序（与 MT 管理器默认排序一致） */
    public fun sortedList(): Comparator<VfsEntry> =
        compareByDescending<VfsEntry> { it.isDirectory }.thenBy { it.name.lowercase() }
}

/** 文件系统能力位：决定 UI 操作菜单的可见性与可用性 */
public enum class VfsCapability {
    LIST,
    READ,
    WRITE,
    MKDIR,
    DELETE,
    RENAME,
}

/**
 * 统一虚拟文件系统接口。
 *
 * 实现方约定：
 * - 所有方法均为同步阻塞调用，调用方（UI/任务层）负责调度到后台线程；
 * - 返回值统一使用 [Result]，失败时携带人类可读中文错误消息；
 * - 实现必须线程安全（列表/读路径会被双窗口并发调用）；
 * - 引擎红线：本接口及其实现位于 engine-core，禁止引入 Android 依赖。
 */
public interface Vfs {
    /** 本后端支持的能力集合 */
    public fun capabilities(): Set<VfsCapability>

    /** 列出目录条目（已按目录优先排序）；非目录 URI 返回 failure */
    public fun list(dir: VfsUri): Result<List<VfsEntry>>

    /** 打开只读流；调用方负责关闭 */
    public fun openRead(file: VfsUri): Result<java.io.InputStream>

    /** 判断存在性；探测失败返回 false 而非 failure（用于 UI 状态刷新） */
    public fun exists(uri: VfsUri): Boolean

    /** 目标是否目录；不存在返回 false */
    public fun isDirectory(uri: VfsUri): Boolean

    /** 创建目录（含父目录） */
    public fun mkdir(dir: VfsUri): Result<Unit>

    /** 删除文件或目录（[recursive] 为 true 时递归删除目录内容） */
    public fun delete(
        uri: VfsUri,
        recursive: Boolean,
    ): Result<Unit>

    /** 重命名（同目录内改名；不做跨目录移动） */
    public fun rename(
        uri: VfsUri,
        newName: String,
    ): Result<VfsUri>

    /** 写入文件：覆盖或追加；返回写入字节数 */
    public fun writeFile(
        target: VfsUri,
        content: java.io.InputStream,
        append: Boolean,
    ): Result<Long>

    /** 目录默认排序导出，供实现方与测试复用 */
    public fun sort(entries: List<VfsEntry>): List<VfsEntry> =
        entries.sortedWith(compareByDescending<VfsEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
}
