/*
 * Copyright (C) 2026 The NeoManager Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.neomanager.ui.file

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.neomanager.engine.core.vfs.VfsUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 排序键（观察规格 2026-10-10-file-manager.md 第 2 节） */
public enum class SortKey(
    public val displayName: String,
) {
    NAME("名称"),
    SIZE("大小"),
    TIME("修改时间"),
    EXT("扩展名"),
    ;

    public companion object {
        public fun fromOrdinalSafe(index: Int): SortKey = entries.getOrElse(index) { NAME }
    }
}

/**
 * 单侧文件面板的状态持有者（Phase 1 ② 引擎版）。
 *
 * 职责：VfsUri 路径导航（进入/上级/返回/刷新）、条目加载（后台线程）、
 * 排序（目录恒优先）与隐藏文件过滤、加载与错误状态管理。
 * 历史栈支持 zip 内部与本地目录混合导航（zip 根上溯即容器所在目录）。
 */
public class FilePaneState(
    initialPath: String,
    private val browser: FileBrowser,
) {
    /** 当前路径（VfsUri 字符串：file:// 或 zip:） */
    public var path: String by mutableStateOf(initialPath)
        private set

    /** 当前目录条目（已排序、已过滤） */
    public val entries: MutableList<FileEntry> = mutableStateListOf()

    /** 是否正在加载 */
    public var isLoading: Boolean by mutableStateOf(false)
        private set

    /** 加载失败的错误信息（null 表示无错误） */
    public var error: String? by mutableStateOf(null)
        private set

    /** 排序键 */
    public var sortKey: SortKey by mutableStateOf(SortKey.NAME)
        set(value) {
            field = value
            reloadTick++
        }

    /** 逆序排序 */
    public var sortReverse: Boolean by mutableStateOf(false)
        set(value) {
            field = value
            reloadTick++
        }

    /** 显示隐藏文件（`.` 前缀） */
    public var showHidden: Boolean by mutableStateOf(false)
        set(value) {
            field = value
            reloadTick++
        }

    /** 刷新计数：refresh() 自增，驱动重新加载 */
    private var reloadTick: Int by mutableIntStateOf(0)

    /** 面板内返回栈 */
    private val backStack = ArrayDeque<String>()

    /** 文件条目打开回调（编辑器/属性/压缩包进入等由调用方决定） */
    public var openFileHandler: ((FileEntry) -> Unit)? = null

    /** 是否可以返回上级（zip 根可上溯到容器目录；本地根不可） */
    public val canGoUp: Boolean
        get() = VfsUri.parseOrNull(path)?.parent() != null

    /**
     * 组合期副作用：path 或刷新计数变化时重新加载目录。
     * 在面板 composable 中调用一次即可。
     */
    @Composable
    public fun Effect() {
        LaunchedEffect(Unit) {
            snapshotFlow { Triple(path, reloadTick, sortKey) }.collect { (target, _, _) -> load(target) }
        }
    }

    /** 打开条目：目录进入；文件交给 [openFileHandler] */
    public fun open(entry: FileEntry) {
        if (entry.isDirectory) {
            openPath(entry.path)
        } else {
            openFileHandler?.invoke(entry)
        }
    }

    /** 导航到指定路径，当前路径压入历史栈 */
    public fun openPath(target: String) {
        if (target == path) return
        backStack.addLast(path)
        path = target
    }

    /** 返回：优先面板内历史栈，其次物理上级目录 */
    public fun back() {
        val previous = backStack.removeLastOrNull()
        if (previous != null) {
            path = previous
        } else {
            up()
        }
    }

    /** 上级目录；已在根时忽略 */
    public fun up() {
        val parent = VfsUri.parseOrNull(path)?.parent()?.value ?: return
        backStack.addLast(path)
        path = parent
    }

    /** 重新加载当前目录 */
    public fun refresh() {
        reloadTick++
    }

    private suspend fun load(target: String) {
        isLoading = true
        error = null
        val result = withContext(Dispatchers.IO) { browser.list(target) }
        result
            .onSuccess { loaded ->
                val filtered =
                    if (showHidden) {
                        loaded
                    } else {
                        loaded.filter { !it.name.startsWith(".") }
                    }
                entries.clear()
                entries.addAll(sortEntries(filtered))
            }.onFailure { failure ->
                entries.clear()
                error = failure.message ?: failure.javaClass.simpleName
            }
        isLoading = false
    }

    /** 目录恒优先 + 按键排序 + 逆序（观察规格第 2 节） */
    private fun sortEntries(input: List<FileEntry>): List<FileEntry> {
        val comparator =
            when (sortKey) {
                SortKey.NAME -> compareBy<FileEntry> { it.name.lowercase() }
                SortKey.SIZE -> compareBy<FileEntry> { it.sizeBytes }
                SortKey.TIME -> compareBy<FileEntry> { it.lastModified }
                SortKey.EXT -> compareBy<FileEntry> { it.name.substringAfterLast('.', "").lowercase() }
            }
        return input.sortedWith(
            compareByDescending<FileEntry> { it.isDirectory }
                .then(comparator)
                .let { if (sortReverse) it.reversed() else it },
        )
    }

    public companion object {
        /** 物理存储根：POSIX 根目录（VfsUri 字符串） */
        public const val ROOT_PATH: String = "file:///"
    }
}
