/*
 * Copyright (C) 2026 The NeoManager Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.neomanager.engine.core.archive

import java.io.IOException
import java.io.InputStream

/**
 * 归档/压缩格式与魔数探测。
 *
 * 探测优先读文件头（不可信扩展名）；tar 无固定魔数，靠 USTAR 魔数位置或
 * 解析尝试兜底，探测失败时允许按扩展名回退。
 */
public enum class ArchiveFormat(
    val extension: String,
    val displayName: String,
) {
    ZIP("zip", "ZIP 归档"),
    TAR("tar", "TAR 归档"),
    TAR_GZ("tar.gz", "GZip 压缩 TAR"),
    TAR_XZ("tar.xz", "XZ 压缩 TAR"),
    TAR_ZST("tar.zst", "Zstandard 压缩 TAR"),
    TAR_BZ2("tar.bz2", "BZip2 压缩 TAR"),
    GZ("gz", "GZip 压缩文件"),
    XZ("xz", "XZ 压缩文件"),
    ZST("zst", "Zstandard 压缩文件"),
    BZ2("bz2", "BZip2 压缩文件"),
    SEVEN_Z("7z", "7-Zip 归档"),
    RAR("rar", "RAR 归档"),
    ;

    public companion object {
        private const val TAR_MAGIC_OFFSET: Int = 257

        /** 读文件头探测；无法识别返回 null（tar 系尽力识别） */
        public fun detect(header: ByteArray): ArchiveFormat? {
            if (header.size < 8) return null
            return when {
                header.startsWith(byteArrayOf(0x50, 0x4B, 0x03, 0x04)) ||
                    header.startsWith(byteArrayOf(0x50, 0x4B, 0x05, 0x06)) ||
                    header.startsWith(byteArrayOf(0x50, 0x4B, 0x07, 0x08)) -> ZIP
                header.startsWith(byteArrayOf(0x1F, 0x8B.toByte())) -> GZ
                header.startsWith(byteArrayOf(0xFD.toByte(), 0x37, 0x7A, 0x58, 0x5A, 0x00)) -> XZ
                header.startsWith(byteArrayOf(0x28.toByte(), 0xB5.toByte(), 0x2F, 0xFD.toByte())) -> ZST
                header.startsWith(byteArrayOf(0x42, 0x5A, 0x68)) -> BZ2
                header.startsWith(byteArrayOf(0x37, 0x7A, 0xBC.toByte(), 0xAF.toByte(), 0x27, 0x1C)) -> SEVEN_Z
                header.startsWith("Rar!".toByteArray(Charsets.US_ASCII)) -> RAR
                header.size > TAR_MAGIC_OFFSET + 5 &&
                    String(
                        header,
                        TAR_MAGIC_OFFSET,
                        5,
                        Charsets.US_ASCII,
                    ) == "ustar" -> TAR
                else -> null
            }
        }

        /** 用流探测：读取后由调用方自行 seek/重开 */
        public fun detect(input: InputStream): ArchiveFormat? =
            try {
                val header = ByteArray(512)
                var read = 0
                while (read < header.size) {
                    val n = input.read(header, read, header.size - read)
                    if (n < 0) break
                    read += n
                }
                detect(header.copyOf(read))
            } catch (_: IOException) {
                null
            }

        /** 按文件名扩展名推断（探测失败时的回退） */
        public fun fromExtension(fileName: String): ArchiveFormat? {
            val lower = fileName.lowercase()
            return when {
                lower.endsWith(".tar.gz") || lower.endsWith(".tgz") -> TAR_GZ
                lower.endsWith(".tar.xz") || lower.endsWith(".txz") -> TAR_XZ
                lower.endsWith(".tar.zst") || lower.endsWith(".tzst") -> TAR_ZST
                lower.endsWith(".tar.bz2") || lower.endsWith(".tbz2") -> TAR_BZ2
                lower.endsWith(".tar") -> TAR
                lower.endsWith(".zip") ||
                    lower.endsWith(".jar") ||
                    lower.endsWith(".apk") ||
                    lower.endsWith(".apks") ||
                    lower.endsWith(".xapk") ||
                    lower.endsWith(".apkm") -> ZIP
                lower.endsWith(".7z") -> SEVEN_Z
                lower.endsWith(".rar") -> RAR
                lower.endsWith(".gz") -> GZ
                lower.endsWith(".xz") -> XZ
                lower.endsWith(".zst") -> ZST
                lower.endsWith(".bz2") -> BZ2
                else -> null
            }
        }

        /** 是否为可「进入浏览」的随机访问归档（当前仅 zip 家族） */
        public fun isBrowsable(format: ArchiveFormat): Boolean = format == ZIP

        private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
            if (size < prefix.size) return false
            for (i in prefix.indices) if (this[i] != prefix[i]) return false
            return true
        }
    }
}
