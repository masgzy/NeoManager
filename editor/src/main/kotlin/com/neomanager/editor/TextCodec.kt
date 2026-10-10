/*
 * Copyright (C) 2026 The NeoManager Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.neomanager.editor

import java.nio.charset.Charset

/** 容错字符集查找：找不到时回退 ISO-8859-1（文件顶层函数，避免枚举构造期访问 companion） */
private fun charsetOf(name: String): Charset =
    try {
        Charset.forName(name)
    } catch (_: Exception) {
        Charsets.ISO_8859_1
    }

/**
 * 文本编解码器：读取字节 → 按指定字符集解码；保存时反向编码。
 *
 * Phase 1 ② 内置五种常用字符集（观察规格 docs/specs/2026-10-10-editor-reverse.md 第 1 节，
 * 完整字符集清单在后续版本补齐）。注意：GBK 等非 Unicode 字符集经 String 往返
 * 对个别非法字节序列有损；保存前已解码的文本按【当前选择】字符集重新编码。
 */
public enum class TextCodec(
    public val displayName: String,
    public val charset: Charset,
) {
    UTF_8("UTF-8", Charsets.UTF_8),
    UTF_16LE("UTF-16LE", Charsets.UTF_16LE),
    UTF_16BE("UTF-16BE", Charsets.UTF_16BE),
    GBK("GBK", charsetOf("GBK")),
    ISO_8859_1("ISO-8859-1", Charsets.ISO_8859_1),
    ;

    public fun decode(bytes: ByteArray): String = String(bytes, charset)

    public fun encode(text: String): ByteArray = text.toByteArray(charset)

    /** 是否无损往返（ISO-8859-1 字节级无损但会混淆语义，仅编辑兜底用） */
    public val losslessRoundTrip: Boolean
        get() = this != GBK

    public companion object {
        /** 按 BOM 嗅探；无 BOM 默认 UTF-8 */
        public fun sniff(bytes: ByteArray): TextCodec =
            when {
                bytes.size >= 3 &&
                    bytes[0] == 0xEF.toByte() &&
                    bytes[1] == 0xBB.toByte() &&
                    bytes[2] == 0xBF.toByte() -> UTF_8
                bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> UTF_16LE
                bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> UTF_16BE
                else -> UTF_8
            }
    }
}
