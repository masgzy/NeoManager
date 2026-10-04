/*
 * Copyright (C) 2026 The NeoManager Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.neomanager.engine.core

/**
 * 路径规范化工具。
 *
 * engine-core 的基础纯 Kotlin 组件之一：不依赖任何平台 API（java.nio / android.os 均不可用），
 * 以单一分隔符字符串模型处理层级路径，服务于后续 ZIP 条目寻址、ARSC 资源路径匹配
 * 与对比引擎的路径对齐（Phase 1 ② 起）。
 *
 * 语义约定：
 * - 绝对路径以分隔符开头，根路径即为单个分隔符 `"/"`；
 * - 相对路径不以分隔符开头，根为空字符串 `""`；
 * - `.` 表示当前层，直接消除；`..` 表示上一层；
 * - 绝对路径在根之上的 `..` 会被钳制到根；相对路径顶端的 `..` 予以保留（无法再上溯）；
 * - [normalize] 结果不含尾部分隔符（根路径除外）。
 */
object PathNormalizer {
    /**
     * 规范化路径：消除多余的 [separator]、`.` 段与可折叠的 `..` 段。
     *
     * 示例（separator = '/'）：
     * - `"a//b/./c/"` → `"a/b/c"`
     * - `"/a/b/../c"` → `"/a/c"`
     * - `"a/b/../../c"` → `"c"`
     * - `"/a/../../b"` → `"/b"`
     * - `"../a"` → `"../a"`
     */
    fun normalize(
        path: String,
        separator: Char = '/',
    ): String {
        if (path.isEmpty()) return ""

        val isAbsolute = path.startsWith(separator)
        val stack = ArrayDeque<String>()

        for (segment in path.split(separator)) {
            when (segment) {
                "", "." -> continue
                ".." -> {
                    if (stack.isNotEmpty() && stack.last() != "..") {
                        stack.removeLast()
                    } else if (!isAbsolute) {
                        // 相对路径顶端无法再上溯，保留 ..
                        stack.addLast("..")
                    }
                    // 绝对路径在根之上：钳制到根（丢弃）
                }
                else -> stack.addLast(segment)
            }
        }

        val joined = stack.joinToString(separator.toString())
        return if (isAbsolute) "$separator$joined" else joined
    }

    /**
     * 以 [base] 为起点解析 [relative]：relative 为绝对路径则直接规范化返回。
     */
    fun resolve(
        base: String,
        relative: String,
        separator: Char = '/',
    ): String {
        val isAbsoluteRelative = relative.startsWith(separator)
        return if (isAbsoluteRelative) {
            normalize(relative, separator)
        } else {
            normalize(base, separator).let { normalizedBase ->
                if (normalizedBase.isEmpty()) {
                    normalize(relative, separator)
                } else {
                    normalize("$normalizedBase$separator$relative", separator)
                }
            }
        }
    }

    /**
     * 返回父路径；无父层（根或裸文件名）时返回 null。
     * 结果保证规范化。
     */
    fun parent(
        path: String,
        separator: Char = '/',
    ): String? {
        val normalized = normalize(path, separator)
        if (normalized.isEmpty() || normalized == separator.toString()) return null
        val idx = normalized.lastIndexOf(separator)
        return when {
            idx < 0 -> null
            idx == 0 -> separator.toString()
            else -> normalized.substring(0, idx)
        }
    }

    /**
     * 返回路径最后一段名称（文件名或目录名）；根路径返回空字符串。
     */
    fun name(
        path: String,
        separator: Char = '/',
    ): String {
        val normalized = normalize(path, separator)
        if (normalized.isEmpty() || normalized == separator.toString()) return ""
        return normalized.substringAfterLast(separator)
    }

    /**
     * 判断 [child] 是否位于 [parent] 目录内（含 parent 本身）。
     * 双方均会规范化后比较，可防御 `"a/b/../c"` 一类伪造路径。
     */
    fun isInside(
        parent: String,
        child: String,
        separator: Char = '/',
    ): Boolean {
        val p = normalize(parent, separator)
        val c = normalize(child, separator)
        if (p == c) return true
        if (p.isEmpty()) return true // 相对路径根包含一切相对路径
        return c.startsWith("$p$separator")
    }

    /**
     * 计算 [target] 相对于 [base] 的路径；[target] 不在 [base] 内时返回 null。
     * 结果为相对路径（不含前导分隔符）。
     */
    fun relative(
        target: String,
        base: String,
        separator: Char = '/',
    ): String? {
        val t = normalize(target, separator)
        val b = normalize(base, separator)
        if (!isInside(b, t, separator)) return null
        if (b.isEmpty()) return t
        if (b == t) return ""
        val prefixLength = b.length + 1 // 跳过 base 与其后的分隔符
        return t.substring(prefixLength)
    }
}
