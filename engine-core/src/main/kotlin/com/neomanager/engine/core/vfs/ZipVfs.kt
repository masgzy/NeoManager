/*
 * Copyright (C) 2026 The NeoManager Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.neomanager.engine.core.vfs

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * ZIP 家族（zip/jar/apk）内部文件系统：随机访问浏览 + 改动回写。
 *
 * - 浏览/读取：commons-compress ZipFile 随机访问（容器为本地文件时走 File 快路径，
 *   嵌套容器时经 [VfsSeekableChannel] 读取解压流）；
 * - 写入（改条目内容/新建/删除/改名）：整包重建——按原条目顺序复制未改动条目
 *   （保持压缩方式与时间戳），套用变更计划后写同级临时文件，成功后原子落位；
 *   嵌套容器则先物化到临时文件再重建，最后经父 Vfs 写回（父层递归重建）。
 *   这是同类工具的标准做法：zip 没有原地编辑语义。
 *
 * 目录语义：zip 目录 = 名字以 / 结尾的空条目；浏览时也会为文件条目路径中的
 * 中间段合成目录（zip 不强制携带目录条目）。
 */
public class ZipVfs(
    private val registry: VfsRegistry,
    private val container: VfsUri,
) : Vfs {
    override fun capabilities(): Set<VfsCapability> =
        setOf(
            VfsCapability.LIST,
            VfsCapability.READ,
            VfsCapability.WRITE,
            VfsCapability.MKDIR,
            VfsCapability.DELETE,
            VfsCapability.RENAME,
        )

    override fun list(dir: VfsUri): Result<List<VfsEntry>> =
        runCatching {
            val inner = dir.zipInnerPath
            openZip().use { zip ->
                val seen = LinkedHashMap<String, VfsEntry>()
                val entries = zip.entries
                while (entries.hasMoreElements()) {
                    val raw = entries.nextElement().name.trimStart('/')
                    if (raw.isEmpty()) continue
                    val bare = raw.removeSuffix("/")
                    if (bare.isEmpty()) continue

                    // 逐级合成中间目录（仅当该目录是 inner 的直接子目录时收录）
                    var idx = bare.indexOf('/')
                    while (idx > 0) {
                        val segment = bare.substring(0, idx)
                        if (segment.substringBeforeLast('/', "") == inner) {
                            putDir(seen, dir, segment)
                        }
                        idx = bare.indexOf('/', idx + 1)
                    }

                    // 条目本身（直接子代）
                    if (bare.substringBeforeLast('/', "") == inner) {
                        if (raw.endsWith("/")) {
                            putDir(seen, dir, bare)
                        } else {
                            val entry = zip.getEntry(bare)
                            seen[bare.substringAfterLast('/')] =
                                VfsEntry(
                                    name = bare.substringAfterLast('/'),
                                    uri = dir.resolve(bare.substringAfterLast('/')),
                                    isDirectory = false,
                                    sizeBytes = entry?.size ?: 0L,
                                    lastModified = entry?.time ?: 0L,
                                )
                        }
                    }
                }
                sort(seen.values.toList())
            }
        }

    override fun openRead(file: VfsUri): Result<InputStream> =
        runCatching {
            val inner = file.zipInnerPath
            val zip = openZip()
            try {
                val entry = zip.getEntry(inner) ?: throw IOException("压缩包内不存在：$inner")
                val stream = zip.getInputStream(entry)
                // 流关闭时一并释放 ZipFile 句柄
                object : FilterInputStream(stream) {
                    override fun close() {
                        try {
                            super.close()
                        } finally {
                            try {
                                zip.close()
                            } catch (_: Exception) {
                            }
                        }
                    }
                }
            } catch (e: Throwable) {
                try {
                    zip.close()
                } catch (_: Exception) {
                }
                throw e
            }
        }

    override fun exists(uri: VfsUri): Boolean {
        val inner = uri.zipInnerPath
        if (inner.isEmpty()) return true
        return runCatching {
            openZip().use { zip ->
                zip.getEntry(inner) != null || zip.getEntry("$inner/") != null || hasChildUnder(zip, inner)
            }
        }.getOrDefault(false)
    }

    override fun isDirectory(uri: VfsUri): Boolean {
        val inner = uri.zipInnerPath
        if (inner.isEmpty()) return true
        return runCatching {
            openZip().use { zip ->
                zip.getEntry("$inner/") != null || hasChildUnder(zip, inner)
            }
        }.getOrDefault(false)
    }

    override fun mkdir(dir: VfsUri): Result<Unit> =
        runCatching {
            val inner = dir.zipInnerPath.trimEnd('/')
            require(inner.isNotEmpty()) { "压缩包根目录恒存在" }
            rewrite(RewritePlan(addDirs = listOf("$inner/")))
        }

    override fun delete(
        uri: VfsUri,
        recursive: Boolean,
    ): Result<Unit> =
        runCatching {
            val inner = uri.zipInnerPath.trimEnd('/')
            require(inner.isNotEmpty()) { "不能删除压缩包根" }
            rewrite(RewritePlan(removePrefixes = listOf(inner)))
        }

    override fun rename(
        uri: VfsUri,
        newName: String,
    ): Result<VfsUri> =
        runCatching {
            require(newName.isNotBlank() && !newName.contains('/')) { "非法新名称：$newName" }
            val inner = uri.zipInnerPath.trimEnd('/')
            require(inner.isNotEmpty()) { "压缩包根不可重命名" }
            val parent = inner.substringBeforeLast('/', missingDelimiterValue = "")
            val newInner = if (parent.isEmpty()) newName else "$parent/$newName"
            rewrite(RewritePlan(movePrefix = inner to newInner))
            uri.parent()!!.resolve(newName)
        }

    override fun writeFile(
        target: VfsUri,
        content: InputStream,
        append: Boolean,
    ): Result<Long> =
        runCatching {
            require(!append) { "压缩包内暂不支持追加写入（需整包重建）" }
            val inner = target.zipInnerPath.trim('/')
            require(inner.isNotEmpty()) { "写入目标不能为压缩包根" }
            val bytes = content.readBytes()
            rewrite(RewritePlan(replacements = mapOf(inner to bytes)))
            bytes.size.toLong()
        }

    // ------------------------------------------------------------------
    // 打开
    // ------------------------------------------------------------------

    private fun openZip(): ZipFile {
        val containerVfs = registry.resolve(container)
        return if (containerVfs is LocalVfs) {
            ZipFile(File(container.localPath), FALLBACK_ENCODING)
        } else {
            val knownSize = runCatching { containerEntrySize() }.getOrDefault(-1L)
            ZipFile(VfsSeekableChannel(containerVfs, container, knownSize), FALLBACK_ENCODING)
        }
    }

    /** 容器本身位于另一 zip 内时，从父中央目录直接取解压后大小 */
    private fun containerEntrySize(): Long {
        if (container.scheme != VfsUri.SCHEME_ZIP) return -1L
        val parent = ZipVfs(registry, container.containerUri)
        return parent.openZip().use { zip ->
            zip.getEntry(container.zipInnerPath)?.size ?: -1L
        }
    }

    // ------------------------------------------------------------------
    // 重建引擎
    // ------------------------------------------------------------------

    /** 重建计划：替换条目内容 / 删除前缀 / 前缀改名 / 新增目录条目 */
    private data class RewritePlan(
        val replacements: Map<String, ByteArray> = emptyMap(),
        val removePrefixes: List<String> = emptyList(),
        val movePrefix: Pair<String, String>? = null,
        val addDirs: List<String> = emptyList(),
    )

    private fun rewrite(plan: RewritePlan) {
        val containerVfs = registry.resolve(container)
        if (containerVfs is LocalVfs) {
            val sourceFile = File(container.localPath)
            val tmpFile =
                File(
                    sourceFile.parentFile ?: Files.createTempDirectory("neo-zip").toFile(),
                    sourceFile.name + ".neo-tmp-" + System.nanoTime(),
                )
            try {
                rebuild(plan, sourceFile, tmpFile)
                if (sourceFile.exists() && !sourceFile.delete()) {
                    throw IOException("无法替换原压缩包：$sourceFile")
                }
                if (!tmpFile.renameTo(sourceFile)) {
                    throw IOException("临时文件落位失败：$tmpFile -> $sourceFile")
                }
            } finally {
                tmpFile.delete()
            }
        } else {
            // 嵌套容器：物化 → 重建 → 经父 Vfs 写回（父层递归重建）
            val tmpDir = Files.createTempDirectory("neo-zip-rewrite")
            try {
                val src = tmpDir.resolve("src.zip")
                containerVfs.openRead(container).getOrThrow().use { input ->
                    Files.copy(input, src, StandardCopyOption.REPLACE_EXISTING)
                }
                val out = tmpDir.resolve("out.zip")
                rebuild(plan, src.toFile(), out.toFile())
                java.io.FileInputStream(out.toFile()).use { input ->
                    containerVfs.writeFile(container, input, append = false).getOrThrow()
                }
            } finally {
                tmpDir.toFile().deleteRecursively()
            }
        }
    }

    /** 把 [plan] 套用到 [sourceFile] 并输出到 [targetFile]（两侧均为普通文件） */
    private fun rebuild(
        plan: RewritePlan,
        sourceFile: File,
        targetFile: File,
    ) {
        if (!sourceFile.exists() || sourceFile.length() == 0L) {
            if (plan.replacements.isEmpty() && plan.addDirs.isEmpty()) {
                throw IOException("压缩包不存在：$sourceFile")
            }
        }
        val existingNames: Set<String> =
            if (sourceFile.exists() && sourceFile.length() > 0) {
                ZipFile(sourceFile, FALLBACK_ENCODING).use { zip ->
                    java.util.Collections
                        .list(zip.entries)
                        .map { it.name }
                        .toSet()
                }
            } else {
                emptySet()
            }

        ZipArchiveOutputStream(targetFile.outputStream().buffered(BUFFER_SIZE)).use { out ->
            if (existingNames.isNotEmpty()) {
                ZipFile(sourceFile, FALLBACK_ENCODING).use { zip ->
                    val iterator = zip.entries
                    while (iterator.hasMoreElements()) {
                        val entry = iterator.nextElement() as ZipArchiveEntry
                        val raw = entry.name
                        val bare = raw.trimEnd('/')
                        if (bare.isEmpty()) continue

                        if (plan.removePrefixes.any { bare == it || bare.startsWith("$it/") }) continue

                        val finalName =
                            plan.movePrefix?.let { (from, to) ->
                                when {
                                    bare == from -> to
                                    bare.startsWith("$from/") -> "$to${bare.removePrefix(from)}"
                                    else -> null
                                }
                            }
                        val replaced = plan.replacements[bare]

                        when {
                            replaced != null ->
                                writeEntry(
                                    out,
                                    finalName ?: bare,
                                    replaced,
                                    isDirectory = false,
                                    time = entry.time,
                                )
                            finalName != null ->
                                writeEntry(
                                    out,
                                    finalName,
                                    zip.getInputStream(entry).readBytes(),
                                    isDirectory = false,
                                    time = entry.time,
                                )
                            else -> {
                                val isDir = raw.endsWith("/")
                                val newEntry = ZipArchiveEntry(bare + if (isDir) "/" else "")
                                newEntry.time = entry.time
                                if (entry.method == ZipArchiveOutputStream.STORED) {
                                    // 保持 STORED（APK 的 resources.arsc/so 库常用）：需预置 size+crc
                                    newEntry.method = ZipArchiveOutputStream.STORED
                                    val bytes = if (isDir) ByteArray(0) else zip.getInputStream(entry).readBytes()
                                    newEntry.size = bytes.size.toLong()
                                    newEntry.crc =
                                        java.util.zip
                                            .CRC32()
                                            .apply { update(bytes) }
                                            .value
                                    out.putArchiveEntry(newEntry)
                                    if (!isDir) out.write(bytes)
                                    out.closeArchiveEntry()
                                } else {
                                    newEntry.method = ZipArchiveOutputStream.DEFLATED
                                    out.putArchiveEntry(newEntry)
                                    if (!isDir) zip.getInputStream(entry).copyTo(out, BUFFER_SIZE)
                                    out.closeArchiveEntry()
                                }
                            }
                        }
                    }
                }
            }
            // 新建条目（原包中不存在）
            for ((name, bytes) in plan.replacements) {
                if (name in existingNames || "$name/" in existingNames) continue
                writeEntry(out, name, bytes, isDirectory = false, time = System.currentTimeMillis())
            }
            for (dir in plan.addDirs) {
                val name = dir.trimEnd('/')
                if (name in existingNames) continue
                writeEntry(out, name, ByteArray(0), isDirectory = true, time = System.currentTimeMillis())
            }
        }
    }

    private fun writeEntry(
        out: ZipArchiveOutputStream,
        name: String,
        bytes: ByteArray,
        isDirectory: Boolean,
        time: Long,
    ) {
        val entry = ZipArchiveEntry(name + if (isDirectory) "/" else "")
        entry.time = time
        if (isDirectory) {
            entry.method = ZipArchiveOutputStream.STORED
            entry.size = 0L
            entry.crc = 0L
        } else {
            entry.method = ZipArchiveOutputStream.DEFLATED
        }
        out.putArchiveEntry(entry)
        if (!isDirectory) out.write(bytes)
        out.closeArchiveEntry()
    }

    // ------------------------------------------------------------------
    // 列表辅助
    // ------------------------------------------------------------------

    private fun putDir(
        seen: MutableMap<String, VfsEntry>,
        dir: VfsUri,
        dirPath: String,
    ) {
        val name = dirPath.substringAfterLast('/')
        if (!seen.containsKey(name)) {
            seen[name] =
                VfsEntry(
                    name = name,
                    uri = dir.resolve(name),
                    isDirectory = true,
                    lastModified = 0L,
                )
        }
    }

    private fun hasChildUnder(
        zip: ZipFile,
        prefix: String,
    ): Boolean {
        val iterator = zip.entries
        val withSlash = "$prefix/"
        while (iterator.hasMoreElements()) {
            val name = iterator.nextElement().name.trimStart('/')
            if (name == prefix || name.startsWith(withSlash)) return true
        }
        return false
    }

    public companion object {
        /**
         * 非显式 UTF-8（EFS）条目的回退解码字符集：中文场景最常见的 GBK。
         * 带 EFS 标志的条目始终按 UTF-8 解码，不受此影响。
         */
        internal const val FALLBACK_ENCODING: String = "GBK"
        private const val BUFFER_SIZE: Int = 64 * 1024
    }
}
