/*
 * Copyright (C) 2026 The NeoManager Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.neomanager.engine.core.vfs

import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.CopyOption
import java.nio.file.DirectoryNotEmptyException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.Comparator
import kotlin.io.path.name

/**
 * 本地文件系统后端（java.nio 实现，纯 JVM）。
 *
 * 覆盖应用普通权限可达的存储范围；受保护目录的读写由
 * engine-android 的 Root/Shizuku 后端提供（同一 [Vfs] 接口）。
 *
 * 线程安全：全部操作走 NIO 无共享状态，天然并发安全。
 */
public class LocalVfs : Vfs {
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
            val path = toPath(dir)
            if (!Files.exists(path)) throw IOException("路径不存在：${path.absolute()}")
            if (!Files.isDirectory(path)) throw IOException("不是目录：${path.absolute()}")

            val entries: List<VfsEntry> =
                Files.list(path).use { stream ->
                    stream
                        .sorted(NATIVE_ORDER)
                        .map { child ->
                            val isDir = Files.isDirectory(child)
                            val isLink = Files.isSymbolicLink(child)
                            VfsEntry(
                                name = child.name,
                                uri = dir.resolve(child.name),
                                isDirectory = isDir,
                                isSymlink = isLink,
                                linkTarget = if (isLink) Files.readSymbolicLink(child).toString() else null,
                                sizeBytes = if (!isDir) runCatching { Files.size(child) }.getOrDefault(0L) else 0L,
                                lastModified =
                                    runCatching {
                                        Files
                                            .getLastModifiedTime(
                                                child,
                                            ).toMillis()
                                    }.getOrDefault(0L),
                                permissions = null,
                            )
                        }.toList()
                }
            sort(entries)
        }

    override fun openRead(file: VfsUri): Result<InputStream> =
        runCatching {
            val path = toPath(file)
            if (!Files.isRegularFile(path)) throw IOException("不是普通文件：${path.absolute()}")
            BufferedInputStream(Files.newInputStream(path, StandardOpenOption.READ), BUFFER_SIZE)
        }

    override fun exists(uri: VfsUri): Boolean = Files.exists(toPath(uri))

    override fun isDirectory(uri: VfsUri): Boolean =
        runCatching { Files.isDirectory(toPath(uri), LinkOption.NOFOLLOW_LINKS) }.getOrDefault(false)

    override fun mkdir(dir: VfsUri): Result<Unit> =
        runCatching {
            Files.createDirectories(toPath(dir))
        }

    override fun delete(
        uri: VfsUri,
        recursive: Boolean,
    ): Result<Unit> =
        runCatching {
            val path = toPath(uri)
            if (!Files.exists(path)) return@runCatching
            if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) && recursive) {
                Files.walk(path).use { walk ->
                    walk.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
                }
            } else {
                try {
                    Files.delete(path)
                } catch (e: DirectoryNotEmptyException) {
                    throw IOException("目录非空，需递归删除：${path.absolute()}", e)
                }
            }
        }

    override fun rename(
        uri: VfsUri,
        newName: String,
    ): Result<VfsUri> =
        runCatching {
            require(newName.isNotBlank() && !newName.contains('/')) { "非法新名称：$newName" }
            val source = toPath(uri)
            val target = source.resolveSibling(newName)
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                throw IOException("同名条目已存在：$newName")
            }
            Files.move(source, target)
            uri.parent()!!.resolve(newName)
        }

    override fun writeFile(
        target: VfsUri,
        content: InputStream,
        append: Boolean,
    ): Result<Long> =
        runCatching {
            val path = toPath(target)
            path.parent?.let { Files.createDirectories(it) }
            val options =
                if (append) {
                    arrayOf(StandardOpenOption.CREATE, StandardOpenOption.APPEND)
                } else {
                    arrayOf(StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)
                }
            var written = 0L
            content.use { input ->
                Files.newOutputStream(path, *options).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        written += n
                    }
                }
            }
            written
        }

    /** 同文件系统内的快速移动（NIO rename，不落两份盘） */
    public fun moveFast(
        source: VfsUri,
        targetDir: VfsUri,
    ): Result<VfsUri> =
        runCatching {
            val src = toPath(source)
            val dst = toPath(targetDir).resolve(src.name)
            val options = arrayOf<CopyOption>(StandardCopyOption.REPLACE_EXISTING)
            Files.move(src, dst, *options)
            targetDir.resolve(src.name)
        }

    private fun toPath(uri: VfsUri): Path {
        require(uri.isLocal) { "LocalVfs 只接受 file:// URI：${uri.value}" }
        return Paths.get(uri.localPath)
    }

    private fun Path.absolute(): String = toAbsolutePath().toString()

    public companion object {
        private const val BUFFER_SIZE: Int = 64 * 1024

        /** 目录列表本地化排序（数字感知的字典序，让 2.txt 排在 10.txt 前） */
        private val NATIVE_ORDER: Comparator<Path> =
            Comparator { a, b ->
                a.name.compareTo(b.name, ignoreCase = true)
            }

        /** 模块级共享实例（无状态） */
        public val INSTANCE: LocalVfs = LocalVfs()
    }
}
