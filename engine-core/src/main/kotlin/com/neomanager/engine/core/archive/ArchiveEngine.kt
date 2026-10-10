/*
 * Copyright (C) 2026 The NeoManager Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.neomanager.engine.core.archive

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipFile
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorOutputStream
import org.apache.commons.compress.utils.IOUtils
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files

/**
 * 归档条目模型（tar/zip 通用）。
 */
public data class ArchiveListEntry(
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
)

/**
 * 归档引擎：列表 / 解压 / 打包。
 *
 * 支持矩阵（Phase 1 ②）：
 * - 浏览（随机访问）：zip 家族 → 走 ZipVfs，本类提供一次性全量清单；
 * - 列表 + 解压：zip / tar / tar.gz / tar.xz / tar.zst / tar.bz2 / 7z（只读）/ rar4（只读）；
 * - 打包：zip / tar / tar.gz / tar.xz / tar.zst / tar.bz2（7z 写入需 p7zip JNI，暂缓）。
 *
 * 解压安全：拒绝条目路径穿越（`..` 与绝对路径），拒绝 zip slip。
 */
public object ArchiveEngine {
    private const val BUFFER_SIZE: Int = 64 * 1024

    // ------------------------------------------------------------------
    // 列表
    // ------------------------------------------------------------------

    /** 列出归档全部条目（tar 系为一次流式扫描） */
    public fun list(archiveFile: File): Result<List<ArchiveListEntry>> =
        runCatching {
            val format =
                probeFormat(archiveFile)
                    ?: throw IOException("无法识别的归档格式：${archiveFile.name}")
            when (val effective = effectiveFormat(archiveFile, format)) {
                ArchiveFormat.ZIP -> listZip(archiveFile)
                ArchiveFormat.TAR -> listTar(archiveFile, format)
                ArchiveFormat.SEVEN_Z -> list7z(archiveFile)
                ArchiveFormat.RAR -> listRar(archiveFile)
                else -> {
                    // 单文件压缩：返回还原后的单个条目
                    listOf(ArchiveListEntry(singleFileName(archiveFile.name), false, archiveFile.length(), 0L))
                }
            }
        }

    /**
     * 判定实际内容格式：魔数为 GZ/XZ/ZST/BZ2 时内部可能是裸 tar
     * （xxx.tar.gz 与 file.gz 头部完全相同），尝试解析 tar 头区分。
     */
    private fun effectiveFormat(
        file: File,
        detected: ArchiveFormat,
    ): ArchiveFormat {
        if (detected !in SINGLE_FILE_FORMATS) return detected
        try {
            decompress(Files.newInputStream(file.toPath()), detected).use { decompressed ->
                TarArchiveInputStream(BufferedInputStream(decompressed, BUFFER_SIZE)).use { tar ->
                    return if (tar.nextEntry != null) ArchiveFormat.TAR else detected
                }
            }
        } catch (_: Exception) {
            return detected
        }
    }

    private val SINGLE_FILE_FORMATS: Set<ArchiveFormat> =
        setOf(ArchiveFormat.GZ, ArchiveFormat.XZ, ArchiveFormat.ZST, ArchiveFormat.BZ2)

    private fun listZip(file: File): List<ArchiveListEntry> =
        ZipFile(file, com.neomanager.engine.core.vfs.ZipVfs.FALLBACK_ENCODING).use { zip ->
            java.util.Collections.list(zip.entries).map {
                ArchiveListEntry(
                    name = it.name,
                    isDirectory = it.isDirectory,
                    sizeBytes = it.size,
                    lastModified = it.time,
                )
            }
        }

    private fun listTar(
        file: File,
        format: ArchiveFormat,
    ): List<ArchiveListEntry> =
        TarArchiveInputStream(
            BufferedInputStream(decompress(Files.newInputStream(file.toPath()), format), BUFFER_SIZE),
        ).use { tar ->
            buildList {
                var entry = tar.nextEntry
                while (entry != null) {
                    add(ArchiveListEntry(entry.name, entry.isDirectory, entry.size, entry.modTime.time))
                    entry = tar.nextEntry
                }
            }
        }

    private fun list7z(file: File): List<ArchiveListEntry> =
        org.apache.commons.compress.archivers.sevenz.SevenZFile(file).use { seven ->
            seven.entries.map {
                ArchiveListEntry(it.name, it.isDirectory, if (it.isDirectory) 0 else it.size, 0L)
            }
        }

    private fun listRar(file: File): List<ArchiveListEntry> =
        com.github.junrar.Archive(file).use { rar ->
            rar.fileHeaders.map {
                ArchiveListEntry(it.fileName, it.isDirectory, it.unpSize, it.mTime?.time ?: 0L)
            }
        }

    // ------------------------------------------------------------------
    // 解压
    // ------------------------------------------------------------------

    /** 解压到目标目录（自动创建）；[filter] 返回 true 的条目才解出 */
    public fun extract(
        archiveFile: File,
        targetDir: File,
        filter: (String) -> Boolean = { true },
    ): Result<Int> =
        runCatching {
            val format =
                probeFormat(archiveFile)
                    ?: throw IOException("无法识别的归档格式：${archiveFile.name}")
            val effective = effectiveFormat(archiveFile, format)
            targetDir.mkdirs()
            var count = 0
            when (effective) {
                ArchiveFormat.ZIP ->
                    ZipFile(archiveFile, com.neomanager.engine.core.vfs.ZipVfs.FALLBACK_ENCODING).use { zip ->
                        val iterator = zip.entries
                        while (iterator.hasMoreElements()) {
                            val entry = iterator.nextElement() as ZipArchiveEntry
                            if (!filter(entry.name)) continue
                            if (entry.isDirectory) {
                                File(targetDir, safeName(entry.name)).mkdirs()
                            } else {
                                val out = File(targetDir, safeName(entry.name))
                                out.parentFile?.mkdirs()
                                zip.getInputStream(entry).use { input ->
                                    Files.copy(input, out.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                                }
                                count++
                            }
                        }
                    }
                ArchiveFormat.TAR, ArchiveFormat.TAR_GZ, ArchiveFormat.TAR_XZ,
                ArchiveFormat.TAR_ZST, ArchiveFormat.TAR_BZ2,
                -> {
                    TarArchiveInputStream(
                        BufferedInputStream(
                            decompress(Files.newInputStream(archiveFile.toPath()), format),
                            BUFFER_SIZE,
                        ),
                    ).use { tar ->
                        var entry = tar.nextEntry
                        while (entry != null) {
                            if (filter(entry.name)) {
                                val out = File(targetDir, safeName(entry.name))
                                if (entry.isDirectory) {
                                    out.mkdirs()
                                } else {
                                    out.parentFile?.mkdirs()
                                    Files.newOutputStream(out.toPath()).use { output -> IOUtils.copy(tar, output) }
                                    count++
                                }
                            } else {
                                tar.skip(Long.MAX_VALUE)
                            }
                            entry = tar.nextEntry
                        }
                    }
                }
                ArchiveFormat.SEVEN_Z ->
                    org.apache.commons.compress.archivers.sevenz.SevenZFile(archiveFile).use { seven ->
                        var entry: org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry? = seven.nextEntry
                        while (entry != null) {
                            if (filter(entry.name)) {
                                val out = File(targetDir, safeName(entry.name))
                                if (entry.isDirectory) {
                                    out.mkdirs()
                                } else {
                                    out.parentFile?.mkdirs()
                                    seven.getInputStream(entry).use { input ->
                                        Files.copy(
                                            input,
                                            out.toPath(),
                                            java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                                        )
                                    }
                                    count++
                                }
                            }
                            entry = seven.nextEntry
                        }
                    }
                ArchiveFormat.RAR ->
                    com.github.junrar.Archive(archiveFile).use { rar ->
                        for (header in rar.fileHeaders) {
                            if (!filter(header.fileName)) continue
                            val out = File(targetDir, safeName(header.fileName))
                            if (header.isDirectory) {
                                out.mkdirs()
                            } else {
                                out.parentFile?.mkdirs()
                                Files.newOutputStream(out.toPath()).use { output -> rar.extractFile(header, output) }
                                count++
                            }
                        }
                    }
                // 单文件压缩（gz/xz/zst/bz2）：还原为去掉扩展名的单文件
                ArchiveFormat.GZ, ArchiveFormat.XZ, ArchiveFormat.ZST, ArchiveFormat.BZ2 -> {
                    val outName = singleFileName(archiveFile.name)
                    val out = File(targetDir, outName)
                    decompress(Files.newInputStream(archiveFile.toPath()), effective).use { input ->
                        Files.copy(input, out.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                    }
                    count++
                }
            }
            count
        }

    // ------------------------------------------------------------------
    // 打包
    // ------------------------------------------------------------------

    /**
     * 打包目录/文件列表为归档。
     *
     * @param sources 待打包条目（文件或目录，目录递归）
     * @param targetFile 目标归档（扩展名决定格式）
     * @return 打包条目数
     */
    public fun pack(
        sources: List<File>,
        targetFile: File,
        onProgress: ((Int) -> Unit)? = null,
    ): Result<Int> =
        runCatching {
            val format =
                ArchiveFormat.fromExtension(targetFile.name)
                    ?: throw IOException("无法从扩展名识别目标格式：${targetFile.name}")
            targetFile.parentFile?.mkdirs()
            var count = 0
            when (format) {
                ArchiveFormat.ZIP ->
                    ZipArchiveOutputStream(targetFile.outputStream().buffered(BUFFER_SIZE)).use { out ->
                        for (source in sources) {
                            if (source.isFile) {
                                putZipEntry(out, source, source.name, source.lastModified())
                                count++
                            } else if (source.isDirectory) {
                                Files.walk(source.toPath()).use { walk ->
                                    for (child in walk.sorted().toList()) {
                                        val f = child.toFile()
                                        val rel =
                                            source
                                                .toPath()
                                                .parent
                                                ?.relativize(child)
                                                ?.toString() ?: f.name
                                        if (f.isDirectory) {
                                            putZipEntry(out, f, "$rel/", f.lastModified())
                                        } else {
                                            putZipEntry(out, f, rel, f.lastModified())
                                            count++
                                        }
                                    }
                                }
                            }
                        }
                        onProgress?.invoke(count)
                    }
                ArchiveFormat.TAR, ArchiveFormat.TAR_GZ, ArchiveFormat.TAR_XZ,
                ArchiveFormat.TAR_ZST, ArchiveFormat.TAR_BZ2,
                ->
                    TarArchiveOutputStream(
                        BufferedOutputStream(compress(targetFile.outputStream(), format), BUFFER_SIZE),
                    ).use { out ->
                        out.setLongFileMode(2) // LONGFILEMODE_POSIX：PAX 扩展头支持超长路径
                        for (source in sources) {
                            if (source.isFile) {
                                putTarEntry(out, source, source.name)
                                count++
                            } else if (source.isDirectory) {
                                Files.walk(source.toPath()).use { walk ->
                                    for (child in walk.sorted().toList()) {
                                        val f = child.toFile()
                                        if (f.isFile) {
                                            val rel =
                                                source
                                                    .toPath()
                                                    .parent
                                                    ?.relativize(child)
                                                    ?.toString() ?: f.name
                                            putTarEntry(out, f, rel)
                                            count++
                                        }
                                    }
                                }
                            }
                        }
                        onProgress?.invoke(count)
                    }
                // 单文件压缩：第一个源必须为普通文件，直接包压缩流
                ArchiveFormat.GZ, ArchiveFormat.XZ, ArchiveFormat.ZST, ArchiveFormat.BZ2 -> {
                    val source =
                        sources.firstOrNull()
                            ?: throw IOException("未指定待压缩文件")
                    require(source.isFile) { "$format 为单文件压缩格式，不能打包目录（请先打 tar）" }
                    compress(targetFile.outputStream().buffered(BUFFER_SIZE), format).use { output ->
                        source.inputStream().use { it.copyTo(output, BUFFER_SIZE) }
                    }
                    count = 1
                }
                else -> throw IOException("$format 暂不支持打包（7z 写入需 p7zip JNI，规划后续版本）")
            }
            count
        }

    private fun putZipEntry(
        out: ZipArchiveOutputStream,
        file: File,
        entryName: String,
        time: Long,
    ) {
        val entry = ZipArchiveEntry(entryName)
        entry.time = time
        out.putArchiveEntry(entry)
        if (!file.isDirectory) file.inputStream().use { it.copyTo(out, BUFFER_SIZE) }
        out.closeArchiveEntry()
    }

    private fun putTarEntry(
        out: TarArchiveOutputStream,
        file: File,
        entryName: String,
    ) {
        val entry = TarArchiveEntry(file, entryName)
        out.putArchiveEntry(entry)
        if (file.isFile) file.inputStream().use { it.copyTo(out, BUFFER_SIZE) }
        out.closeArchiveEntry()
    }

    // ------------------------------------------------------------------
    // 压缩流
    // ------------------------------------------------------------------

    /** 按格式包一层解压流 */
    public fun decompress(
        raw: InputStream,
        format: ArchiveFormat,
    ): InputStream =
        when (format) {
            ArchiveFormat.TAR_GZ, ArchiveFormat.GZ -> GzipCompressorInputStream(raw, true)
            ArchiveFormat.TAR_XZ, ArchiveFormat.XZ -> XZCompressorInputStream(raw)
            ArchiveFormat.TAR_ZST, ArchiveFormat.ZST -> ZstdCompressorInputStream(raw)
            ArchiveFormat.TAR_BZ2, ArchiveFormat.BZ2 -> BZip2CompressorInputStream(raw)
            else -> raw
        }

    /** 按格式包一层压缩流 */
    public fun compress(
        raw: OutputStream,
        format: ArchiveFormat,
    ): OutputStream =
        when (format) {
            ArchiveFormat.TAR_GZ, ArchiveFormat.GZ -> GzipCompressorOutputStream(raw)
            ArchiveFormat.TAR_XZ, ArchiveFormat.XZ -> XZCompressorOutputStream(raw)
            ArchiveFormat.TAR_ZST, ArchiveFormat.ZST -> ZstdCompressorOutputStream(raw)
            ArchiveFormat.TAR_BZ2, ArchiveFormat.BZ2 -> BZip2CompressorOutputStream(raw)
            else -> raw
        }

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    private fun probeFormat(file: File): ArchiveFormat? {
        Files.newInputStream(file.toPath()).use { input ->
            return ArchiveFormat.detect(input) ?: ArchiveFormat.fromExtension(file.name)
        }
    }

    /** zip-slip 防护：拒绝 .. 与绝对路径，折叠多余斜杠 */
    private fun safeName(raw: String): String {
        val normalized = raw.replace('\\', '/').trimStart('/')
        check(!normalized.contains("..")) { "归档条目包含路径穿越：$raw" }
        return normalized
    }

    /** pictures.tar.gz → pictures；file.bin.gz → file.bin */
    private fun singleFileName(archiveName: String): String =
        archiveName.lowercase().let { lower ->
            when {
                lower.endsWith(".tar.gz") -> archiveName.removeSuffix(".tar.gz")
                lower.endsWith(".tgz") -> archiveName.removeSuffix(".tgz") + ".tar"
                lower.endsWith(".tar.xz") -> archiveName.removeSuffix(".tar.xz")
                lower.endsWith(".txz") -> archiveName.removeSuffix(".txz") + ".tar"
                lower.endsWith(".tar.zst") -> archiveName.removeSuffix(".tar.zst")
                lower.endsWith(".tzst") -> archiveName.removeSuffix(".tzst") + ".tar"
                lower.endsWith(".tar.bz2") -> archiveName.removeSuffix(".tar.bz2")
                lower.endsWith(".tbz2") -> archiveName.removeSuffix(".tbz2") + ".tar"
                lower.endsWith(".gz") -> archiveName.removeSuffix(".gz")
                lower.endsWith(".xz") -> archiveName.removeSuffix(".xz")
                lower.endsWith(".zst") -> archiveName.removeSuffix(".zst")
                lower.endsWith(".bz2") -> archiveName.removeSuffix(".bz2")
                else -> archiveName + ".out"
            }
        }
}
