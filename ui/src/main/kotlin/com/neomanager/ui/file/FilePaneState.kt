/*
 * Copyright (C) 2026 The NeoManager Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.neomanager.ui.file

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 单侧文件面板的状态持有者。
 *
 * 职责：路径导航（进入/上级/返回/刷新）、条目加载（IO 调度在后台线程）、
 * 加载与错误状态管理。使用 Compose Snapshot 状态驱动 UI 重组。
 *
 * 历史栈 [backStack] 支持面板内返回，引擎接入后导航行为保持不变。
 */
public class FilePaneState(
    initialPath: String,
    private val browser: FileBrowser,
) {
    /** 当前路径（'/' 分隔） */
    public var path: String by mutableStateOf(initialPath)
        private set

    /** 当前目录条目（已排序） */
    public val entries: MutableList<FileEntry> = mutableStateListOf()

    /** 是否正在加载 */
    public var isLoading: Boolean by mutableStateOf(false)
        private set

    /** 加载失败的错误信息（null 表示无错误） */
    public var error: String? by mutableStateOf(null)
        private set

    /** 刷新计数：refresh() 自增，驱动重新加载 */
    private var reloadTick: Int by mutableIntStateOf(0)

    /** 面板内返回栈 */
    private val backStack = ArrayDeque<String>()

    /** 是否可以返回上级（根目录不可再上溯） */
    public val canGoUp: Boolean get() = path != ROOT_PATH

    /**
     * 组合期副作用：path 或刷新计数变化时重新加载目录。
     * 在面板 composable 中调用一次即可。
     */
    @Composable
    public fun Effect() {
        LaunchedEffect(Unit) {
            snapshotFlow { path to reloadTick }.collect { (target, _) -> load(target) }
        }
    }

    /** 打开条目：目录进入；文件暂不响应（查看/编辑属 Phase 1 ②） */
    public fun open(entry: FileEntry) {
        if (entry.isDirectory) {
            openPath(entry.path)
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
        val parent = parentOf(path)
        if (parent != null) {
            backStack.addLast(path)
            path = parent
        }
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
                entries.clear()
                entries.addAll(loaded)
            }.onFailure { failure ->
                entries.clear()
                error = failure.message ?: failure.javaClass.simpleName
            }
        isLoading = false
    }

    public companion object {
        /** 物理存储根：POSIX 根目录 */
        public const val ROOT_PATH: String = "/"

        /** 计算上级目录；根返回 null */
        public fun parentOf(path: String): String? {
            if (path.isEmpty() || path == ROOT_PATH) return null
            val trimmed = path.trimEnd('/')
            val idx = trimmed.lastIndexOf('/')
            return when {
                idx <= 0 -> ROOT_PATH
                else -> trimmed.substring(0, idx)
            }
        }
    }
}
