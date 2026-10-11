/*
 * Copyright (C) 2026 The NeoManager Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.neomanager.ui.bookmark

import android.content.Context
import android.content.SharedPreferences

/**
 * 书签条目（0.2.0-alpha08 分组支持）：所属分组 + 名称 + 路径。
 *
 * @property group 分组名（空串表示未分组，抽屉中直接平铺展示）
 * @property name 展示名
 * @property path 跳转目标（VfsUri 字符串）
 */
public data class BookmarkItem(
    val group: String,
    val name: String,
    val path: String,
)

/**
 * UI 导航状态的纯文本编解码（书签为「分组 + 名称 + 路径」三元组，历史为纯路径列表）。
 *
 * 行内以制表符分隔列；`\`、`\t`、`\n` 三字符经反斜杠转义，
 * 保证任意合法路径（含空格、中文、emoji）无损往返。
 * 书签行兼容两列旧格式（无分组列 → 空分组）；编解码为纯 Kotlin 实现，便于 JVM 单元测试。
 */
public object UiStateCodec {
    private const val SEPARATOR = '\t'

    /** 编码「名称 + 路径」对列表为多行文本 */
    public fun encodePairs(pairs: List<Pair<String, String>>): String =
        pairs.joinToString("\n") { (name, path) -> "${escape(name)}$SEPARATOR${escape(path)}" }

    /** 解码 [encodePairs] 产物；损坏行静默跳过（持久化数据允许局部损坏） */
    public fun decodePairs(text: String): List<Pair<String, String>> =
        text
            .split('\n')
            .mapNotNull { line ->
                val cols = splitUnescape(line)
                if (cols.size == 2 && cols[0].isNotBlank() && cols[1].isNotBlank()) {
                    cols[0] to cols[1]
                } else {
                    null
                }
            }

    /** 编码分组书签列表为多行文本（空分组不写列，保持行短） */
    public fun encodeBookmarks(items: List<BookmarkItem>): String =
        items.joinToString("\n") { (group, name, path) ->
            listOf(group, name, path).filter { it.isNotEmpty() }.joinToString(SEPARATOR.toString()) { escape(it) }
        }

    /**
     * 解码 [encodeBookmarks] 产物；兼容两列旧格式（视为无分组）。
     * 损坏行静默跳过（持久化数据允许局部损坏）。
     */
    public fun decodeBookmarks(text: String): List<BookmarkItem> =
        text
            .split('\n')
            .mapNotNull { line ->
                val cols = splitUnescape(line)
                when (cols.size) {
                    3 -> BookmarkItem(cols[0], cols[1], cols[2])
                    2 -> BookmarkItem("", cols[0], cols[1])
                    else -> null
                }
            }.filter { it.name.isNotBlank() && it.path.isNotBlank() }

    /** 编码路径列表为多行文本 */
    public fun encodePaths(paths: List<String>): String = paths.joinToString("\n") { escape(it) }

    /** 解码 [encodePaths] 产物；空行跳过 */
    public fun decodePaths(text: String): List<String> =
        text.split('\n').map { unescape(it) }.filter { it.isNotBlank() }

    private fun escape(raw: String): String =
        buildString(raw.length) {
            for (ch in raw) {
                when (ch) {
                    '\\' -> append("\\\\")
                    SEPARATOR -> append("\\t")
                    '\n' -> append("\\n")
                    else -> append(ch)
                }
            }
        }

    /** 解转义（遇孤立反斜杠按字面处理，保持健壮） */
    private fun unescape(raw: String): String {
        if ('\\' !in raw) return raw
        return buildString(raw.length) {
            var i = 0
            while (i < raw.length) {
                val ch = raw[i]
                if (ch == '\\' && i + 1 < raw.length) {
                    when (raw[i + 1]) {
                        '\\' -> append('\\')
                        't' -> append(SEPARATOR)
                        'n' -> append('\n')
                        else -> {
                            append(ch)
                            append(raw[i + 1])
                        }
                    }
                    i += 2
                } else {
                    append(ch)
                    i++
                }
            }
        }
    }

    /** 按未转义制表符切分一行（先转义切分、再逐列解转义，防止路径内假分隔） */
    private fun splitUnescape(line: String): List<String> = splitRaw(line).map { unescape(it) }

    private fun splitRaw(line: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            if (ch == '\\' && i + 1 < line.length) {
                sb.append(ch).append(line[i + 1])
                i += 2
                continue
            }
            if (ch == SEPARATOR) {
                out.add(sb.toString())
                sb.setLength(0)
                i++
                continue
            }
            sb.append(ch)
            i++
        }
        out.add(sb.toString())
        return out
    }
}

/**
 * 侧边抽屉导航状态的持久化存储（书签 / 历史记录 / 首页路径）。
 *
 * 存储介质为应用私有 [SharedPreferences]，无任何网络参与（全本地化承诺）。
 * 历史记录最新在前、去重连续重复，上限 [HISTORY_LIMIT] 条。
 */
public class UiStateStore(
    private val preferences: SharedPreferences,
) {
    /** 书签列表（不可变快照，含分组；元素为「名称 to 路径」，兼容旧调用方） */
    public fun bookmarks(): List<Pair<String, String>> = bookmarksGrouped().map { it.name to it.path }

    /** 书签列表（含分组，0.2.0-alpha08） */
    public fun bookmarksGrouped(): List<BookmarkItem> =
        UiStateCodec.decodeBookmarks(preferences.getString(KEY_BOOKMARKS, null).orEmpty())

    /** 添加书签（无分组）；同路径已存在时幂等返回 false */
    public fun addBookmark(
        name: String,
        path: String,
    ): Boolean = addBookmark(name, path, group = "")

    /**
     * 添加书签到指定分组（0.2.0-alpha08）；[group] 为空表示未分组。
     * 同路径已存在时幂等返回 false；同路径不同名以已存在为准（跳转目标唯一）。
     */
    public fun addBookmark(
        name: String,
        path: String,
        group: String,
    ): Boolean {
        if (bookmarksGrouped().any { it.path == path }) return false
        val updated = bookmarksGrouped() + BookmarkItem(group.trim(), name, path)
        preferences.edit().putString(KEY_BOOKMARKS, UiStateCodec.encodeBookmarks(updated)).apply()
        return true
    }

    /** 书签分组名列表（未分组不计入；保持插入顺序） */
    public fun bookmarkGroups(): List<String> =
        bookmarksGrouped()
            .mapNotNull {
                it.group.takeIf(String::isNotEmpty)
            }.distinct()

    /** 按路径删除书签（分组信息保留）；返回是否有变更 */
    public fun removeBookmark(path: String): Boolean {
        val current = bookmarksGrouped()
        val updated = current.filterNot { it.path == path }
        if (updated.size == current.size) return false
        preferences.edit().putString(KEY_BOOKMARKS, UiStateCodec.encodeBookmarks(updated)).apply()
        return true
    }

    /** 历史记录（最新在前） */
    public fun history(): List<String> = UiStateCodec.decodePaths(preferences.getString(KEY_HISTORY, null).orEmpty())

    /** 记录一次浏览（最新在前、连续去重、裁剪到上限） */
    public fun recordHistory(path: String) {
        val current = history()
        if (current.firstOrNull() == path) return
        val updated = (listOf(path) + current.filterNot { it == path }).take(HISTORY_LIMIT)
        preferences.edit().putString(KEY_HISTORY, UiStateCodec.encodePaths(updated)).apply()
    }

    /** 清空历史记录 */
    public fun clearHistory() {
        preferences.edit().remove(KEY_HISTORY).apply()
    }

    /** 首页路径（未设置返回 null） */
    public fun homePath(): String? = preferences.getString(KEY_HOME, null)

    /** 设置首页路径（传 null 清除） */
    public fun setHomePath(path: String?) {
        preferences.edit().putString(KEY_HOME, path).apply()
    }

    /** 用户手动隐藏的路径集合（与系统 `.` 隐藏相互独立） */
    public fun hiddenPaths(): Set<String> =
        UiStateCodec
            .decodePaths(preferences.getString(KEY_HIDDEN, null).orEmpty())
            .toSet()

    /** 追加手动隐藏；已存在时幂等返回 false */
    public fun hidePath(path: String): Boolean {
        val current = hiddenPaths()
        if (path in current) return false
        preferences.edit().putString(KEY_HIDDEN, UiStateCodec.encodePaths((current + path).toList())).apply()
        return true
    }

    /** 解除手动隐藏；不存在时返回 false */
    public fun unhidePath(path: String): Boolean {
        val current = hiddenPaths()
        if (path !in current) return false
        preferences.edit().putString(KEY_HIDDEN, UiStateCodec.encodePaths((current - path).toList())).apply()
        return true
    }

    /** 首次启动手势提示是否已确认 */
    public fun isTipsSeen(): Boolean = preferences.getBoolean(KEY_TIPS_SEEN, false)

    /** 标记首次启动手势提示已确认 */
    public fun setTipsSeen() {
        preferences.edit().putBoolean(KEY_TIPS_SEEN, true).apply()
    }

    /** 是否单栏布局（0.2.0-alpha08；默认双栏） */
    public fun isSinglePane(): Boolean = preferences.getBoolean(KEY_SINGLE_PANE, false)

    /** 设置单栏布局开关 */
    public fun setSinglePane(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_SINGLE_PANE, enabled).apply()
    }

    // ------------------------------------------------------------------
    // 回收站索引（0.2.0-alpha08）：回收站内展示名 → 原始路径
    // ------------------------------------------------------------------

    /** 回收站索引快照（trashName to originalPath；外部已删除的条目由调用方按需修剪） */
    public fun trashIndex(): List<Pair<String, String>> =
        UiStateCodec.decodePairs(preferences.getString(KEY_TRASH_INDEX, null).orEmpty())

    /** 记录一个移入回收站的条目 */
    public fun recordTrashItem(
        trashName: String,
        originalPath: String,
    ) {
        val updated = trashIndex() + (trashName to originalPath)
        preferences.edit().putString(KEY_TRASH_INDEX, UiStateCodec.encodePairs(updated)).apply()
    }

    /** 批量移除回收站索引（恢复/单条删除后调用） */
    public fun removeTrashItems(names: Collection<String>) {
        if (names.isEmpty()) return
        val updated = trashIndex().filterNot { it.first in names }
        preferences.edit().putString(KEY_TRASH_INDEX, UiStateCodec.encodePairs(updated)).apply()
    }

    /** 清空回收站索引（清空回收站后调用） */
    public fun clearTrashIndex() {
        preferences.edit().remove(KEY_TRASH_INDEX).apply()
    }

    public companion object {
        private const val KEY_BOOKMARKS = "drawer_bookmarks"
        private const val KEY_HISTORY = "drawer_history"
        private const val KEY_HOME = "home_path"
        private const val KEY_HIDDEN = "manual_hidden_paths"
        private const val KEY_TRASH_INDEX = "trash_index"
        private const val KEY_TIPS_SEEN = "gesture_tips_seen"
        private const val KEY_SINGLE_PANE = "layout_single_pane"

        /** 历史记录容量上限 */
        public const val HISTORY_LIMIT: Int = 50

        /** 便捷构造：应用默认私有偏好文件 */
        public fun default(context: Context): UiStateStore =
            UiStateStore(context.getSharedPreferences("neo_ui_state", Context.MODE_PRIVATE))
    }
}
