/*
 * Copyright (C) 2026 The NeoManager Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.neomanager.ui.dualpane.actions

import com.neomanager.engine.core.archive.ArchiveEngine
import com.neomanager.engine.core.vfs.FileOps
import com.neomanager.engine.core.vfs.VfsRegistry
import com.neomanager.engine.core.vfs.VfsUri
import com.neomanager.ui.file.FileEntry
import com.neomanager.ui.file.FilePaneState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 文件操作控制器：剪贴板、新建/重命名/删除/压缩/解压/粘贴。
 *
 * 全部操作走 [FileOps]/[VfsRegistry]/[ArchiveEngine]，跨本地与 zip 后端；
 * 结果经 [toasts] 以人类可读消息上抛给 UI。
 */
public class FileActionsController(
    private val registry: VfsRegistry,
) {
    private val ops by lazy { FileOps(registry) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _toasts =
        MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** 操作结果提示流（UI 收集后 Toast） */
    public val toasts: SharedFlow<String> = _toasts

    @Volatile
    private var clipboard: Clipboard? = null

    /** 剪贴板条目数（0 = 空） */
    public fun clipboardCount(): Int = clipboard?.entries?.size ?: 0

    /** 放入剪贴板（copy/move 模式） */
    public fun copyToClipboard(
        entries: List<FileEntry>,
        pane: FilePaneState,
        moveMode: Boolean,
    ) {
        clipboard = Clipboard(entries, moveMode)
        emit(if (moveMode) "已剪切 ${entries.size} 项" else "已复制 ${entries.size} 项")
    }

    /** 粘贴剪贴板到面板当前目录 */
    public suspend fun pasteClipboard(pane: FilePaneState) {
        val clip = clipboard ?: return
        val targetDir = VfsUri.parseOrNull(pane.path) ?: return
        pasteInternal(clip, targetDir, pane)
    }

    /** 单条目「复制到对面/移动到对面」快捷动作 */
    public suspend fun pasteInto(
        entry: FileEntry,
        targetPane: FilePaneState,
    ) {
        val targetDir = VfsUri.parseOrNull(targetPane.path) ?: return
        runAndReport("复制") {
            ops.copyInto(VfsUri.parse(entry.path), targetDir).getOrThrow()
            targetPane.refresh()
            "已复制 ${entry.name} → 对面面板"
        }
    }

    /** 单条目移动到对面面板 */
    public suspend fun moveToOther(
        entry: FileEntry,
        sourcePane: FilePaneState,
        targetPane: FilePaneState,
    ) {
        val targetDir = VfsUri.parseOrNull(targetPane.path) ?: return
        runAndReport("移动") {
            ops.moveInto(VfsUri.parse(entry.path), targetDir).getOrThrow()
            sourcePane.refresh()
            targetPane.refresh()
            "已移动 ${entry.name} → 对面面板"
        }
    }

    /** 新建文件夹 */
    public suspend fun createFolder(
        pane: FilePaneState,
        name: String,
    ) {
        val dir = VfsUri.parseOrNull(pane.path)?.resolve(name) ?: return
        runAndReport("新建") {
            registry.resolve(dir).mkdir(dir).getOrThrow()
            pane.refresh()
            "已创建文件夹 $name"
        }
    }

    /** 重命名 */
    public suspend fun rename(
        entry: FileEntry,
        newName: String,
        pane: FilePaneState,
    ) {
        val uri = VfsUri.parseOrNull(entry.path) ?: return
        runAndReport("重命名") {
            registry.resolve(uri).rename(uri, newName).getOrThrow()
            pane.refresh()
            "已重命名 ${entry.name} → $newName"
        }
    }

    /** 删除（递归） */
    public suspend fun delete(
        entries: List<FileEntry>,
        pane: FilePaneState,
    ) {
        runAndReport("删除") {
            var ok = 0
            for (entry in entries) {
                val uri = VfsUri.parseOrNull(entry.path) ?: continue
                registry.resolve(uri).delete(uri, recursive = true)
                ok++
            }
            pane.refresh()
            "已删除 $ok 项"
        }
    }

    /** 压缩为 zip（zip 压缩级别默认） */
    public suspend fun compressToZip(
        entry: FileEntry,
        archiveName: String,
        pane: FilePaneState,
    ) {
        val parentUri = VfsUri.parseOrNull(entry.path)?.parent() ?: return
        runAndReport("压缩") {
            val parentPath = parentUri.localPath
            val source = File(parentPath, entry.name)
            val target = File(parentPath, archiveName)
            val count = ArchiveEngine.pack(listOf(source), target).getOrThrow()
            pane.refresh()
            "已打包 $count 项 → ${target.name}"
        }
    }

    /** 解压 zip 到当前目录（同名子目录内） */
    public suspend fun extractEntry(
        entry: FileEntry,
        pane: FilePaneState,
    ) {
        val uri = VfsUri.parseOrNull(entry.path) ?: return
        runAndReport("解压") {
            require(uri.isLocal) { "压缩包内的归档请先解出外层" }
            val file = File(uri.localPath)
            val outDir = File(file.parentFile, file.nameWithoutExtension)
            val count = ArchiveEngine.extract(file, outDir).getOrThrow()
            pane.refresh()
            "已解压 $count 项 → ${outDir.name}/"
        }
    }

    // ------------------------------------------------------------------

    private suspend fun pasteInternal(
        clip: Clipboard,
        targetDir: VfsUri,
        pane: FilePaneState,
    ) {
        runAndReport(if (clip.move) "移动" else "复制") {
            var ok = 0
            for (entry in clip.entries) {
                val source = VfsUri.parseOrNull(entry.path) ?: continue
                if (clip.move) {
                    ops.moveInto(source, targetDir).getOrThrow()
                } else {
                    ops.copyInto(source, targetDir).getOrThrow()
                }
                ok++
            }
            pane.refresh()
            val verb = if (clip.move) "移动" else "复制"
            "$verb $ok 项 → ${targetDir.name.ifEmpty { "根目录" }}"
        }
    }

    /** 执行并统一报告结果；[body] 返回成功消息（或默认「完成」） */
    private suspend fun runAndReport(
        verb: String,
        body: suspend () -> String,
    ) {
        withContext(Dispatchers.IO) {
            try {
                val message = body()
                emit(message)
            } catch (e: Throwable) {
                emit("$verb 失败：${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    private fun emit(message: String) {
        _toasts.tryEmit(message)
    }
}
