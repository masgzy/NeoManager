/*
 * Copyright (C) 2026 The NeoManager Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.neomanager.engine.android

import com.neomanager.engine.core.vfs.Vfs
import com.neomanager.engine.core.vfs.VfsCapability
import com.neomanager.engine.core.vfs.VfsEntry
import com.neomanager.engine.core.vfs.VfsUri
import com.topjohnwu.superuser.Shell
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * Root 后端（libsu）：以 root shell 执行文件操作。
 *
 * 读写采用「中转站」策略：root 侧 cp 到 /data/local/tmp 中转（chmod 644）后
 * 由应用进程直接读写，避免 shell 文本流损坏二进制。目录列表解析 `ls -Apl --time-style=+%s`。
 */
public object RootVfs : Vfs {
    private const val STAGING_DIR: String = "/data/local/tmp/.neo_stage"

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
            val path = dir.localPath
            val result = Shell.cmd("ls -Apl --time-style=+%s -- '$path'").exec()
            if (!result.isSuccess) throw IOException("root 列目录失败：${result.err.joinToString().ifEmpty { path }}")
            val entries = ArrayList<VfsEntry>()
            for (line in result.out) {
                if (line.isBlank() || line.startsWith("total ")) continue
                val parsed = parseLsLine(line) ?: continue
                val (perms, size, time, name, linkTarget) = parsed
                entries.add(
                    VfsEntry(
                        name = name,
                        uri = dir.resolve(name),
                        isDirectory = perms.startsWith("d"),
                        isSymlink = perms.startsWith("l"),
                        linkTarget = linkTarget,
                        sizeBytes = size,
                        lastModified = time,
                        permissions = perms,
                    ),
                )
            }
            sort(entries)
        }

    /** `drwxrwx--x root sdcard_ 4096 1700000000 name` / `lrwxrwxrwx ... name -> target` */
    private fun parseLsLine(line: String): Quintet? {
        val tokens = line.split(' ').filter { it.isNotEmpty() }
        if (tokens.size < 6) return null
        val perms = tokens[0]
        val size = tokens[3].toLongOrNull() ?: 0L
        val time = tokens[4].toLongOrNull()?.let { it * 1000 } ?: 0L
        // 名称 = 第 6 列起全部（容忍空格）；软链接含 " -> target"
        val rest = line.substringAfter(tokens[4] + ' ', "")
        val (name, target) =
            if (rest.contains(" -> ")) {
                rest.substringBefore(" -> ") to rest.substringAfter(" -> ")
            } else {
                rest to null
            }
        if (name.isBlank()) return null
        return Quintet(perms, size, time, name.trimEnd('/'), target)
    }

    private data class Quintet(
        val perms: String,
        val size: Long,
        val time: Long,
        val name: String,
        val target: String?,
    )

    override fun openRead(file: VfsUri): Result<InputStream> =
        runCatching {
            val path = file.localPath
            Shell
                .cmd(
                    "mkdir -p '$STAGING_DIR' && cp -f -- '$path' '$STAGING_DIR/read.bin' && chmod 644 '$STAGING_DIR/read.bin'",
                ).exec()
                .takeIf { it.isSuccess }
                ?: throw IOException("root 读取失败：$path")
            val staged = File("$STAGING_DIR/read.bin")
            if (!staged.isFile) throw IOException("root 中转文件缺失：$path")
            // 读完后清理（文件流由调用方关闭，这里先删亦可——POSIX 已打开句柄不受影响）
            staged.inputStream().also {
                staged.delete()
            }
        }

    override fun exists(uri: VfsUri): Boolean = Shell.cmd("test -e -- '${uri.localPath}'").exec().isSuccess

    override fun isDirectory(uri: VfsUri): Boolean = Shell.cmd("test -d -- '${uri.localPath}'").exec().isSuccess

    override fun mkdir(dir: VfsUri): Result<Unit> =
        runCatching {
            val r = Shell.cmd("mkdir -p -- '${dir.localPath}'").exec()
            if (!r.isSuccess) throw IOException("mkdir 失败：${r.err.joinToString()}")
        }

    override fun delete(
        uri: VfsUri,
        recursive: Boolean,
    ): Result<Unit> =
        runCatching {
            val cmd = if (recursive) "rm -rf -- '${uri.localPath}'" else "rm -f -- '${uri.localPath}'"
            val r = Shell.cmd(cmd).exec()
            if (!r.isSuccess) throw IOException("删除失败：${r.err.joinToString()}")
        }

    override fun rename(
        uri: VfsUri,
        newName: String,
    ): Result<VfsUri> =
        runCatching {
            require(!newName.contains('/')) { "非法新名称" }
            val parent = uri.localPath.trimEnd('/').substringBeforeLast('/')
            val target = "$parent/$newName"
            val r = Shell.cmd("mv -- '${uri.localPath}' '$target'").exec()
            if (!r.isSuccess) throw IOException("重命名失败：${r.err.joinToString()}")
            uri.parent()!!.resolve(newName)
        }

    override fun writeFile(
        target: VfsUri,
        content: InputStream,
        append: Boolean,
    ): Result<Long> =
        runCatching {
            val path = target.localPath
            Shell.cmd("mkdir -p '$STAGING_DIR'").exec()
            val staged = File("$STAGING_DIR/write.bin")
            staged.outputStream().use { output -> content.copyTo(output, 64 * 1024) }
            val size = staged.length()
            val op = if (append) "cat" else "cp"
            Shell.cmd("chmod 644 '$STAGING_DIR/write.bin'").exec()
            val r =
                if (append) {
                    Shell.cmd("cat '$STAGING_DIR/write.bin' >> -- '$path'").exec()
                } else {
                    Shell.cmd("$op -f '$STAGING_DIR/write.bin' -- '$path'").exec()
                }
            staged.delete()
            if (!r.isSuccess) throw IOException("root 写入失败：${r.err.joinToString()}")
            size
        }
}
