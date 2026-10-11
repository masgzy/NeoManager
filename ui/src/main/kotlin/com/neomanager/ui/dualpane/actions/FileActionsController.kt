/*
 * Copyright (C) 2026 The NeoManager Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.neomanager.ui.dualpane.actions

import com.neomanager.engine.core.archive.ArchiveEngine
import com.neomanager.engine.core.vfs.FileOps
import com.neomanager.engine.core.vfs.VfsRegistry
import com.neomanager.engine.core.vfs.VfsUri
import com.neomanager.ui.bookmark.UiStateStore
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
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

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

    /** 新建空文件（0.2.0-alpha07，MT「新建」对话框的「文件」按钮） */
    public suspend fun createFile(
        pane: FilePaneState,
        name: String,
    ) {
        val target = VfsUri.parseOrNull(pane.path)?.resolve(name) ?: return
        runAndReport("新建") {
            val backend = registry.resolve(target)
            check(!backend.exists(target)) { "同名条目已存在：$name" }
            backend.writeFile(target, java.io.ByteArrayInputStream(ByteArray(0)), append = false).getOrThrow()
            pane.refresh()
            "已创建文件 $name"
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

    /** 删除（递归，直接永久删除） */
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

    // ------------------------------------------------------------------
    // 回收站（0.2.0-alpha08，观察规格第 8 节）
    // ------------------------------------------------------------------

    /**
     * 移入回收站：本地条目移动到 [trashDir]（时间戳前缀防同名冲突），
     * 索引（回收站名 → 原路径）写入 [store]。压缩包内等非本地条目不适用，由调用方先行过滤。
     */
    public suspend fun moveToTrash(
        entries: List<FileEntry>,
        pane: FilePaneState,
        trashDir: File,
        store: UiStateStore,
    ) {
        runAndReport("移入回收站") {
            val dirUri = VfsUri.ofLocal(trashDir.absolutePath)
            val backend = registry.resolve(dirUri)
            backend.mkdir(dirUri).getOrThrow()
            val stamp = LocalDateTime.now().format(TRASH_STAMP)
            var ok = 0
            for (entry in entries) {
                val source = VfsUri.parseOrNull(entry.path) ?: continue
                if (!source.isLocal) continue
                val moved = ops.moveInto(source, dirUri).getOrThrow()
                // 同批同名冲突时补序号（时间戳只到秒）
                var candidate = "${stamp}_${entry.name}"
                var n = 1
                while (backend.exists(dirUri.resolve(candidate))) {
                    candidate = "${stamp}_${n}_${entry.name}"
                    n++
                }
                if (moved.name != candidate) {
                    backend.rename(moved, candidate).getOrThrow()
                }
                store.recordTrashItem(candidate, entry.path)
                ok++
            }
            pane.refresh()
            "已移入回收站 $ok 项"
        }
    }

    /** 从回收站恢复条目到原始位置（索引丢失时回落到内部存储根，避免数据不可达） */
    public suspend fun restoreFromTrash(
        entries: List<FileEntry>,
        pane: FilePaneState,
        trashDir: File,
        store: UiStateStore,
    ) {
        runAndReport("恢复") {
            val index = store.trashIndex().toMap()
            var ok = 0
            val restored = mutableListOf<String>()
            for (entry in entries) {
                val uri = VfsUri.parseOrNull(entry.path) ?: continue
                if (!uri.isLocal) continue
                val original = index[entry.name]
                val targetDir =
                    if (original != null) {
                        VfsUri.parseOrNull(original)?.parent()
                    } else {
                        null
                    } ?: VfsUri.ofLocal(trashDir.parent ?: "/")
                ops.moveInto(uri, targetDir).getOrThrow()
                restored += entry.name
                ok++
            }
            store.removeTrashItems(restored)
            pane.refresh()
            "已恢复 $ok 项"
        }
    }

    /** 清空回收站：删除目录内全部条目并清空索引 */
    public suspend fun emptyTrash(
        pane: FilePaneState,
        trashDir: File,
        store: UiStateStore,
    ) {
        runAndReport("清空回收站") {
            val dirUri = VfsUri.ofLocal(trashDir.absolutePath)
            val backend = registry.resolve(dirUri)
            var ok = 0
            if (backend.exists(dirUri)) {
                for (child in backend.list(dirUri).getOrDefault(emptyList())) {
                    backend.delete(child.uri, recursive = true).getOrThrow()
                    ok++
                }
            }
            store.clearTrashIndex()
            pane.refresh()
            "已清空回收站（$ok 项）"
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

    private companion object {
        /** 回收站条目名时间戳前缀（精确到秒，与序号去重配合防冲突） */
        val TRASH_STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
    }
}
