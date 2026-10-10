/*
 * Copyright (C) 2026 The NeoManager Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.neomanager.engine.core.vfs

/**
 * 统一虚拟路径模型。
 *
 * 语法：
 * ```
 * uri := "file://" absolutePath                  本地文件系统
 *      | "zip:" encodedContainer "!" encodedInner  ZIP 家族归档内部（可递归嵌套）
 * ```
 *
 * - `zip:` 的容器部分本身又是一个 [VfsUri] 字符串，因此支持 zip 套 zip 的递归组合；
 * - 分隔符 `!` 与转义符 `%` 在任何成分内出现时一律百分号编码为 `%21` / `%25`，
 *   因此解析时「`zip:` 前缀之后第一个未转义的 `!`」即为容器与内部路径的分界；
 * - 内部路径永远以 `/` 分隔、不以 `/` 开头；根目录用空串表示。
 *
 * 本类是不可变值对象：所有构造均经 [parse] / [ofLocal] / [zipOf] 归一化，
 * [value] 可直接序列化到 UI 状态、任务参数与持久化书签。
 */
public class VfsUri private constructor(
    /** 归一化后的完整 URI 字符串 */
    public val value: String,
) {
    /** 协议方案：[SCHEME_FILE] 或 [SCHEME_ZIP] */
    public val scheme: String = value.substringBefore(':', "")

    /** 是否本地文件系统路径 */
    public val isLocal: Boolean get() = scheme == SCHEME_FILE

    /**
     * `file://` 方案下的绝对 POSIX 路径（不含 scheme 前缀）。
     * 非 local URI 访问本属性抛 [IllegalStateException]。
     */
    public val localPath: String
        get() {
            check(isLocal) { "非本地 URI 不可取 localPath：$value" }
            return value.removePrefix(SCHEME_FILE_PREFIX)
        }

    /**
     * `zip:` 方案下「剥离最外层 zip 包装」的父 URI。
     * 例：`zip:file:///a/b.zip!inner/x` → `file:///a/b.zip`；
     * 嵌套例：`zip:zip:file:///a.zip!in.apk!res` → `zip:file:///a.zip!in.apk`。
     */
    public val containerUri: VfsUri
        get() {
            check(scheme == SCHEME_ZIP) { "非 zip URI 不可取 containerUri：$value" }
            val body = value.removePrefix("$SCHEME_ZIP:")
            val sep = body.indexOf(SEPARATOR)
            require(sep >= 0) { "损坏的 zip URI（缺少分隔符）：$value" }
            return VfsUri(decode(body.substring(0, sep)))
        }

    /** `zip:` 方案下的内部路径（`/` 分隔、不以 `/` 开头；根为空串） */
    public val zipInnerPath: String
        get() {
            check(scheme == SCHEME_ZIP) { "非 zip URI 不可取 zipInnerPath：$value" }
            val body = value.removePrefix("$SCHEME_ZIP:")
            val sep = body.indexOf(SEPARATOR)
            require(sep >= 0) { "损坏的 zip URI（缺少分隔符）：$value" }
            return decode(body.substring(sep + 1)).trimStart('/')
        }

    /** 尾段名称；`file:///a/b/c.txt` → `c.txt`，`zip:...!dir/sub` → `sub`，根返回 `/` */
    public val name: String
        get() =
            when (scheme) {
                SCHEME_FILE -> {
                    val p = localPath.trimEnd('/')
                    if (p.isEmpty()) "/" else p.substringAfterLast('/')
                }
                SCHEME_ZIP -> {
                    val inner = zipInnerPath
                    if (inner.isEmpty()) "/" else inner.substringAfterLast('/')
                }
                else -> value
            }

    /** 是否为「根」位置：`file:///` 或 zip 内部根 */
    public val isRoot: Boolean
        get() =
            when (scheme) {
                SCHEME_FILE -> localPath == "/"
                SCHEME_ZIP -> zipInnerPath.isEmpty()
                else -> false
            }

    /** 追加子段：目录 URI + `name`（name 内的 `/` 视为多级路径） */
    public fun resolve(child: String): VfsUri =
        when (scheme) {
            SCHEME_FILE -> VfsUri(SCHEME_FILE_PREFIX + joinPosix(localPath, child))
            SCHEME_ZIP -> {
                val inner = joinZip(zipInnerPath, child)
                VfsUri("$SCHEME_ZIP:" + encode(containerUri.value) + SEPARATOR + encode(inner))
            }
            else -> throw UnsupportedOperationException("不支持 resolve 的方案：$scheme")
        }

    /** 上级位置；已在根返回 null */
    public fun parent(): VfsUri? =
        when (scheme) {
            SCHEME_FILE -> {
                val p = localPath.trimEnd('/')
                if (p.isEmpty() || p == "/") {
                    null
                } else {
                    val idx = p.lastIndexOf('/')
                    VfsUri(SCHEME_FILE_PREFIX + if (idx <= 0) "/" else p.substring(0, idx))
                }
            }
            SCHEME_ZIP -> {
                val inner = zipInnerPath
                if (inner.isEmpty()) {
                    // zip 内部已到根：再上一级是容器文件所在目录
                    containerUri.parent()
                } else {
                    val idx = inner.lastIndexOf('/')
                    zipOf(containerUri, if (idx < 0) "" else inner.substring(0, idx))
                }
            }
            else -> null
        }

    override fun toString(): String = value

    override fun equals(other: Any?): Boolean = other is VfsUri && other.value == value

    override fun hashCode(): Int = value.hashCode()

    public companion object {
        public const val SCHEME_FILE: String = "file"
        public const val SCHEME_ZIP: String = "zip"
        public const val SCHEME_FILE_PREFIX: String = "file://"
        private const val SEPARATOR: String = "!"

        /** 本地绝对路径构造；空串或 "/" 归一为根 */
        public fun ofLocal(absolutePath: String): VfsUri {
            val trimmed = absolutePath.trim()
            val normalized = if (trimmed.isEmpty() || trimmed == "/") "/" else trimmed.trimEnd('/')
            return VfsUri(SCHEME_FILE_PREFIX + normalized)
        }

        /** ZIP 家族内部位置构造：容器 URI（任意方案）+ 内部路径 */
        public fun zipOf(
            container: VfsUri,
            innerPath: String,
        ): VfsUri = VfsUri("$SCHEME_ZIP:" + encode(container.value) + SEPARATOR + encode(joinZip("", innerPath)))

        /** 解析任意 URI 字符串；非法输入抛 [IllegalArgumentException] */
        public fun parse(raw: String): VfsUri {
            val v = raw.trim()
            return when {
                v.startsWith(SCHEME_FILE_PREFIX) -> ofLocal(v.removePrefix(SCHEME_FILE_PREFIX))
                v.startsWith("$SCHEME_ZIP:") -> {
                    val body = v.removePrefix("$SCHEME_ZIP:")
                    val sep = body.indexOf(SEPARATOR)
                    require(sep >= 0) { "损坏的 zip URI（缺少 '!' 分隔符）：$v" }
                    val container = parse(decode(body.substring(0, sep)))
                    val inner = decode(body.substring(sep + 1))
                    zipOf(container, inner)
                }
                else -> throw IllegalArgumentException("无法识别的 URI：$v（期望 file:// 或 zip: 前缀）")
            }
        }

        /** 尝试解析；失败返回 null（用于书签/剪贴板等不可信输入） */
        public fun parseOrNull(raw: String): VfsUri? =
            try {
                parse(raw)
            } catch (_: Exception) {
                null
            }

        // ---- 内部：连接与编码 ----

        private fun joinPosix(
            base: String,
            child: String,
        ): String {
            val b = base.trimEnd('/')
            val c = child.trim('/')
            return when {
                c.isEmpty() -> if (b.isEmpty()) "/" else b
                b.isEmpty() -> "/$c"
                else -> "$b/$c"
            }
        }

        private fun joinZip(
            base: String,
            child: String,
        ): String {
            val b = base.trim('/')
            val c = child.trim('/')
            return when {
                b.isEmpty() -> c
                c.isEmpty() -> b
                else -> "$b/$c"
            }
        }

        /** 保留 `/` 与 URI 语法必需字符，转义 `!` 与 `%` */
        private fun encode(component: String): String =
            buildString(component.length) {
                for (ch in component) {
                    when (ch) {
                        '!' -> append("%21")
                        '%' -> append("%25")
                        else -> append(ch)
                    }
                }
            }

        private fun decode(component: String): String {
            if ('%' !in component) return component
            val sb = StringBuilder(component.length)
            var i = 0
            while (i < component.length) {
                val ch = component[i]
                if (ch == '%' && i + 2 < component.length) {
                    val hex = component.substring(i + 1, i + 3)
                    val code = hex.toIntOrNull(16)
                    if (code != null) {
                        sb.append(code.toChar())
                        i += 3
                        continue
                    }
                }
                sb.append(ch)
                i++
            }
            return sb.toString()
        }
    }
}
