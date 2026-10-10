/*
 * Copyright (C) 2026 The NeoManager Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.neomanager.engine.android

import android.os.ParcelFileDescriptor
import com.neomanager.engine.core.vfs.Vfs
import com.neomanager.engine.core.vfs.VfsCapability
import com.neomanager.engine.core.vfs.VfsEntry
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream

/**
 * Shizuku 后端：把 [INeoFileService]（shell/root UID 进程内）适配为统一 [Vfs]。
 *
 * 仅接受 file:// URI；列表经 AIDL 序列化字符串清单还原为 [VfsEntry]。
 */
public class ShizukuVfs(
    private val service: INeoFileService,
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

    override fun list(dir: com.neomanager.engine.core.vfs.VfsUri): Result<List<VfsEntry>> =
        runCatching {
            val path = dir.localPath
            val entries = ArrayList<VfsEntry>()
            for (line in service.list(path)) {
                val fields = line.split('|')
                when (fields.getOrNull(0)) {
                    "d" ->
                        entries.add(
                            VfsEntry(
                                fields[2],
                                dir.resolve(fields[2]),
                                isDirectory = true,
                                lastModified =
                                    fields[1].toLongOrNull() ?: 0L,
                            ),
                        )
                    "l" ->
                        entries.add(
                            VfsEntry(
                                fields[3],
                                dir.resolve(fields[3]),
                                isDirectory = false,
                                isSymlink = true,
                                linkTarget = fields[2],
                                lastModified =
                                    fields[1].toLongOrNull() ?: 0L,
                            ),
                        )
                    "f" ->
                        entries.add(
                            VfsEntry(
                                fields[3],
                                dir.resolve(fields[3]),
                                isDirectory = false,
                                sizeBytes =
                                    fields[1].toLongOrNull() ?: 0L,
                                lastModified = fields[2].toLongOrNull() ?: 0L,
                            ),
                        )
                }
            }
            sort(entries)
        }

    override fun openRead(file: com.neomanager.engine.core.vfs.VfsUri): Result<InputStream> =
        runCatching {
            val pfd = service.openRead(file.localPath)
            BufferedInputStream(ParcelFileDescriptor.AutoCloseInputStream(pfd), 64 * 1024)
        }

    override fun exists(uri: com.neomanager.engine.core.vfs.VfsUri): Boolean =
        runCatching { service.exists(uri.localPath) }.getOrDefault(false)

    override fun isDirectory(uri: com.neomanager.engine.core.vfs.VfsUri): Boolean =
        runCatching { service.isDirectory(uri.localPath) }.getOrDefault(false)

    override fun mkdir(dir: com.neomanager.engine.core.vfs.VfsUri): Result<Unit> =
        runCatching {
            if (!service.mkdirs(dir.localPath)) throw IOException("创建目录失败：${dir.localPath}")
        }

    override fun delete(
        uri: com.neomanager.engine.core.vfs.VfsUri,
        recursive: Boolean,
    ): Result<Unit> =
        runCatching {
            if (!service.delete(uri.localPath, recursive)) throw IOException("删除失败：${uri.localPath}")
        }

    override fun rename(
        uri: com.neomanager.engine.core.vfs.VfsUri,
        newName: String,
    ): Result<com.neomanager.engine.core.vfs.VfsUri> =
        runCatching {
            if (!service.rename(uri.localPath, newName)) throw IOException("重命名失败：${uri.localPath}")
            uri.parent()!!.resolve(newName)
        }

    override fun writeFile(
        target: com.neomanager.engine.core.vfs.VfsUri,
        content: InputStream,
        append: Boolean,
    ): Result<Long> =
        runCatching {
            val tmp = java.io.File.createTempFile("neo-shizuku-write", ".tmp")
            try {
                tmp.outputStream().use { output -> content.copyTo(output, 64 * 1024) }
                val size = tmp.length()
                ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    service.writeFile(target.localPath, pfd, append)
                }
                size
            } finally {
                tmp.delete()
            }
        }
}
