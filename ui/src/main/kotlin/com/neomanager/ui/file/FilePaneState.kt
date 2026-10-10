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
import com.neomanager.engine.core.vfs.VfsUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 搜索上限：结果条数（防内存膨胀） */
private const val SEARCH_MAX_RESULTS: Int = 500

/** 搜索上限：递归深度（相对起始目录，防深层环游） */
private const val SEARCH_MAX_DEPTH: Int = 12

/** 搜索上限：扫描目录总数（防超大目录树长时间占用 IO） */
private const val SEARCH_MAX_DIRS: Int = 2000

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
    public var error: String? by mutableStateOf<String?>(null)
        private set

    private val sortKeyState = mutableStateOf(SortKey.NAME)
    private val sortReverseState = mutableStateOf(false)
    private val showHiddenState = mutableStateOf(false)

    /** 排序键 */
    public var sortKey: SortKey
        get() = sortKeyState.value
        set(value) {
            sortKeyState.value = value
            reloadTick++
        }

    /** 逆序排序 */
    public var sortReverse: Boolean
        get() = sortReverseState.value
        set(value) {
            sortReverseState.value = value
            reloadTick++
        }

    /** 显示隐藏文件（`.` 前缀） */
    public var showHidden: Boolean
        get() = showHiddenState.value
        set(value) {
            showHiddenState.value = value
            reloadTick++
        }

    /** 刷新计数：refresh() 自增，驱动重新加载 */
    private var reloadTick: Int by mutableIntStateOf(0)

    /** 面板内返回栈 */
    private val backStack = ArrayDeque<String>()

    // ------------------------------------------------------------------
    // 多选批量操作
    // ------------------------------------------------------------------

    /** 多选模式是否激活 */
    public var selectionMode: Boolean
        get() = selectionModeState.value
        private set(value) {
            selectionModeState.value = value
        }

    private val selectionModeState = mutableStateOf(false)

    /** 已选中条目路径集合（多选模式下；保序用于展示计数） */
    public val selectedPaths: MutableList<String> = mutableStateListOf()

    /** 条目是否处于选中态 */
    public fun isSelected(entry: FileEntry): Boolean = entry.path in selectedPaths

    /** 多选模式下点击条目：切换选中态 */
    public fun toggleSelection(entry: FileEntry) {
        if (entry.path in selectedPaths) {
            selectedPaths.remove(entry.path)
        } else {
            selectedPaths.add(entry.path)
        }
    }

    /** 选中当前列表全部条目 */
    public fun selectAll() {
        for (entry in visibleEntries) {
            if (entry.path !in selectedPaths) selectedPaths.add(entry.path)
        }
    }

    /** 取消全部选中并退出多选模式 */
    public fun exitSelectionMode() {
        selectedPaths.clear()
        selectionMode = false
    }

    /** 当前展示条目（普通模式 = 目录列表；搜索模式 = 搜索结果） */
    private val visibleEntries: List<FileEntry>
        get() = if (searchActiveState.value && searchQueryState.value.isNotBlank()) searchResults else entries

    /** 当前选中的条目对象（按路径回查可见列表） */
    public fun selectedEntries(): List<FileEntry> = visibleEntries.filter { it.path in selectedPaths }

    // ------------------------------------------------------------------
    // 搜索
    // ------------------------------------------------------------------

    /** 搜索面板是否展开 */
    public var searchActive: Boolean
        get() = searchActiveState.value
        private set(value) {
            searchActiveState.value = value
        }

    private val searchActiveState = mutableStateOf(false)

    /** 搜索关键词 */
    public var searchQuery: String
        get() = searchQueryState.value
        set(value) {
            searchQueryState.value = value
        }

    private val searchQueryState = mutableStateOf("")

    /** 搜索结果（搜索激活且关键词非空时替代目录列表展示） */
    public val searchResults: MutableList<FileEntry> = mutableStateListOf()

    /** 是否正在搜索 */
    public var isSearching: Boolean
        get() = isSearchingState.value
        private set(value) {
            isSearchingState.value = value
        }

    private val isSearchingState = mutableStateOf(false)

    /** 搜索是否被上限截断（未扫完全部子树） */
    public var searchTruncated: Boolean
        get() = searchTruncatedState.value
        private set(value) {
            searchTruncatedState.value = value
        }

    private val searchTruncatedState = mutableStateOf(false)

    /**
     * 展开搜索面板（路径栏切换为搜索输入框）。
     * 搜索范围：当前目录（含子目录，深度 [SEARCH_MAX_DEPTH]）。
     */
    public fun openSearch() {
        searchActive = true
    }

    /** 关闭搜索面板并清理结果 */
    public fun closeSearch() {
        searchQuery = ""
        searchResults.clear()
        searchTruncated = false
        isSearching = false
        searchActive = false
    }

    /**
     * 执行名称包含匹配的递归搜索（后台线程）。
     * 大小写不敏感；命中上限时置 [searchTruncated]。
     */
    public suspend fun search(keyword: String) {
        val trimmed = keyword.trim()
        searchResults.clear()
        searchTruncated = false
        if (trimmed.isEmpty()) return
        isSearching = true
        try {
            withContext(Dispatchers.IO) {
                val needle = trimmed.lowercase()
                var dirsScanned = 0
                val queue = ArrayDeque<Pair<String, Int>>()
                queue.addLast(path to 0)
                outer@ while (queue.isNotEmpty()) {
                    val (dirPath, depth) = queue.removeFirst()
                    if (dirsScanned >= SEARCH_MAX_DIRS) {
                        searchTruncated = true
                        break
                    }
                    dirsScanned++
                    val children =
                        runCatching { browser.list(dirPath).getOrNull() }.getOrNull() ?: continue
                    for (child in children) {
                        if (!showHidden && child.name.startsWith(".")) continue
                        if (child.name.lowercase().contains(needle)) {
                            if (searchResults.size >= SEARCH_MAX_RESULTS) {
                                searchTruncated = true
                                break@outer
                            }
                            searchResults.add(child)
                        }
                        if (child.isDirectory && depth < SEARCH_MAX_DEPTH) {
                            queue.addLast(child.path to depth + 1)
                        }
                    }
                }
            }
        } finally {
            isSearching = false
        }
    }

    /** 进入多选模式并选中 [entry]（操作菜单「多选」入口） */
    public fun enterSelectionMode(entry: FileEntry) {
        selectionMode = true
        if (entry.path !in selectedPaths) selectedPaths.add(entry.path)
    }

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

    /** 打开条目：多选模式下切换选中；目录进入；文件交给 [openFileHandler] */
    public fun open(entry: FileEntry) {
        if (selectionMode) {
            toggleSelection(entry)
            return
        }
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
        resetTransientState()
    }

    /** 返回：优先面板内历史栈，其次物理上级目录；多选/搜索激活时先退出 */
    public fun back() {
        when {
            selectionMode -> {
                exitSelectionMode()
                return
            }
            searchActive -> {
                closeSearch()
                return
            }
        }
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
        resetTransientState()
    }

    /** 导航后清理多选与搜索等临时状态（刷新不算导航） */
    private fun resetTransientState() {
        if (selectionMode) exitSelectionMode()
        if (searchActive) closeSearch()
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
