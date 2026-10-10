/*
 * Copyright (C) 2026 The NeoManager Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.neomanager.engine.android

import android.content.Context
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files

/**
 * Shizuku 用户服务实现：运行在 Shizuku 服务进程（shell/root UID）中，
 * 以其权限执行文件操作。
 *
 * 无状态、只依赖 java.io——服务内的所有权限来自宿主 Shizuku 环境。
 */
public class NeoFileService : INeoFileService.Stub {
    @Suppress("unused")
    constructor() // Shizuku 反射实例化（无参构造）

    constructor(context: Context?) // Shizuku 亦可经 (Context) 构造

    override fun exists(path: String): Boolean = File(path).exists()

    override fun isDirectory(path: String): Boolean = File(path).isDirectory

    override fun list(path: String): MutableList<String> {
        val dir = File(path)
        if (!dir.isDirectory) throw IOException("不是目录：$path")
        val children = dir.listFiles() ?: throw IOException("无法读取目录：$path")
        val out = ArrayList<String>(children.size)
        for (child in children.sortedWith(
            compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() },
        )) {
            val time = child.lastModified()
            out.add(
                when {
                    child.isDirectory -> "d|$time|${child.name}"
                    Files.isSymbolicLink(child.toPath()) ->
                        "l|$time|${readLinkTarget(child)}|${child.name}"
                    else -> "f|${child.length()}|$time|${child.name}"
                },
            )
        }
        return out
    }

    private fun readLinkTarget(file: File): String =
        try {
            java.nio.file.Files
                .readSymbolicLink(file.toPath())
                .toString()
        } catch (_: Exception) {
            ""
        }

    override fun openRead(path: String): ParcelFileDescriptor {
        val file = File(path)
        if (!file.isFile) throw IOException("不是普通文件：$path")
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun writeFile(
        path: String,
        source: ParcelFileDescriptor?,
        append: Boolean,
    ): Long {
        requireNotNull(source) { "缺少内容描述符" }
        val file = File(path)
        file.parentFile?.mkdirs()
        var written = 0L
        ParcelFileDescriptor.AutoCloseInputStream(source).use { input ->
            FileOutputStream(file, append).use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    output.write(buffer, 0, n)
                    written += n
                }
            }
        }
        return written
    }

    override fun mkdirs(path: String): Boolean = File(path).mkdirs()

    override fun delete(
        path: String,
        recursive: Boolean,
    ): Boolean {
        val file = File(path)
        if (!file.exists()) return true
        if (file.isDirectory && recursive) {
            file.walkBottomUp().forEach { it.delete() }
        }
        return file.delete()
    }

    override fun rename(
        path: String,
        newName: String,
    ): Boolean {
        require(!newName.contains('/')) { "非法新名称" }
        val file = File(path)
        return file.renameTo(File(file.parentFile, newName))
    }
}
