/*
 * Copyright (C) 2026 The NeoManager Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.neomanager.ui.bookmark

import android.content.Context
import android.content.SharedPreferences

/**
 * UI 导航状态的纯文本编解码（书签为「名称 + 路径」对，历史为纯路径列表）。
 *
 * 行内以制表符分隔两列；`\`、`\t`、`\n` 三字符经反斜杠转义，
 * 保证任意合法路径（含空格、中文、emoji）无损往返。
 * 编解码为纯 Kotlin 实现，便于 JVM 单元测试。
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
    /** 书签列表（不可变快照），元素为「名称 to 路径」 */
    public fun bookmarks(): List<Pair<String, String>> =
        UiStateCodec.decodePairs(preferences.getString(KEY_BOOKMARKS, null).orEmpty())

    /** 添加书签；同路径已存在时幂等返回 false */
    public fun addBookmark(
        name: String,
        path: String,
    ): Boolean {
        if (bookmarks().any { it.second == path }) return false
        val updated = bookmarks() + (name to path)
        preferences.edit().putString(KEY_BOOKMARKS, UiStateCodec.encodePairs(updated)).apply()
        return true
    }

    /** 按路径删除书签；返回是否有变更 */
    public fun removeBookmark(path: String): Boolean {
        val updated = bookmarks().filterNot { it.second == path }
        if (updated.size == bookmarks().size) return false
        preferences.edit().putString(KEY_BOOKMARKS, UiStateCodec.encodePairs(updated)).apply()
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

    public companion object {
        private const val KEY_BOOKMARKS = "drawer_bookmarks"
        private const val KEY_HISTORY = "drawer_history"
        private const val KEY_HOME = "home_path"

        /** 历史记录容量上限 */
        public const val HISTORY_LIMIT: Int = 50

        /** 便捷构造：应用默认私有偏好文件 */
        public fun default(context: Context): UiStateStore =
            UiStateStore(context.getSharedPreferences("neo_ui_state", Context.MODE_PRIVATE))
    }
}
