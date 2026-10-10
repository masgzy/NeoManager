/*
 * Copyright (C) 2026 The NeoManager Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.neomanager.ui.dualpane

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.neomanager.engine.core.vfs.VfsRegistry
import com.neomanager.engine.core.vfs.VfsUri
import com.neomanager.ui.R
import com.neomanager.ui.dualpane.actions.ActionTarget
import com.neomanager.ui.dualpane.actions.DialogKind
import com.neomanager.ui.dualpane.actions.FileActionsController
import com.neomanager.ui.dualpane.actions.SortDialogState
import com.neomanager.ui.engine.LocalEngineBridge
import com.neomanager.ui.file.EngineFileBrowser
import com.neomanager.ui.file.FileEntry
import com.neomanager.ui.file.FilePane
import com.neomanager.ui.file.FilePaneState
import kotlinx.coroutines.launch

/**
 * 双窗口主屏（Phase 1 ② 引擎版）。
 *
 * - 浏览后端：[VfsRegistry]（本地 + zip/apk 内部 + Root/Shizuku 提权回退）
 * - 文件操作：长按条目 → 底部操作菜单（复制/移动/重命名/删除/压缩/解压/属性/编辑）
 * - 权限策略（全本地化承诺，不申请 INTERNET）不变：完整存储访问引导 + 旧版运行时权限
 */
@Composable
public fun DualPaneScreen(
    onOpenEditor: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val engineBridge = LocalEngineBridge.current
    val scope = rememberCoroutineScope()

    val elevated = remember { engineBridge.elevatedVfs() }
    val registry = remember { VfsRegistry(elevated) }
    val browser = remember(registry) { EngineFileBrowser(registry) }

    val startPath =
        remember {
            @Suppress("DEPRECATION")
            Environment.getExternalStorageDirectory()?.absolutePath ?: FilePaneState.ROOT_PATH
        }
    val startUri = remember { VfsUri.ofLocal(startPath).value }

    val leftState = remember { FilePaneState(startUri, browser) }
    val rightState = remember { FilePaneState(startUri, browser) }
    val states = remember { listOf(leftState, rightState) }

    // 文件操作控制器（剪贴板 + 对话框状态）
    val controller =
        remember(registry) {
            FileActionsController(registry = registry)
        }

    // 文件打开策略：目录/压缩包 → 进入；文本 → 编辑器；其余 → 提示
    fun handleOpen(entry: FileEntry) {
        val name = entry.name.lowercase()
        when {
            entry.isDirectory -> return // state.open 已处理
            name.endsWith(".zip") ||
                name.endsWith(".apk") ||
                name.endsWith(".jar") ||
                name.endsWith(".apks") ||
                name.endsWith(".xapk") ||
                name.endsWith(".apkm") -> {
                val inner = VfsUri.zipOf(VfsUri.parseOrNull(entry.path) ?: return, "")
                (states.firstOrNull { it.path == entry.path } ?: leftState).openPath(inner.value)
            }
            else -> onOpenEditor(entry.path)
        }
    }
    leftState.openFileHandler = ::handleOpen
    rightState.openFileHandler = ::handleOpen

    // 操作结果提示
    LaunchedEffect(Unit) {
        controller.toasts.collect { message -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
    }

    // Android 8-10：传统存储权限首启一次性请求
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
        val legacyLauncher =
            rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions(),
            ) { /* 授权结果由文件系统读取成败自然反馈 */ }
        var legacyRequested by remember { mutableStateOf(false) }
        if (!legacyRequested) {
            legacyRequested = true
            legacyLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                ),
            )
        }
    }

    // 「所有文件访问」状态：前台恢复时复查
    var hasAllFilesAccess by remember { mutableStateOf(checkAllFilesAccess()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    hasAllFilesAccess = checkAllFilesAccess()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 菜单与对话框状态
    var actionTarget by remember { mutableStateOf<ActionTarget?>(null) }
    var dialog by remember { mutableStateOf<DialogKind?>(null) }
    var sortDialog by remember { mutableStateOf<SortDialogState?>(null) }
    var paneMenuFor by remember { mutableStateOf<FilePaneState?>(null) }

    fun activePane(): FilePaneState = paneMenuFor ?: leftState

    Column(modifier = modifier) {
        if (!hasAllFilesAccess) {
            AllFilesAccessBanner(
                packageName = context.packageName,
                onResolved = { hasAllFilesAccess = checkAllFilesAccess() },
            )
        }
        DualPaneLayout(
            first = {
                FilePane(
                    state = leftState,
                    onEntryLongPress = { actionTarget = ActionTarget(it, leftState) },
                    onNewFolder = {
                        paneMenuFor = leftState
                        dialog = DialogKind.NewFolder
                    },
                    onSort = { sortDialog = SortDialogState(leftState) },
                    onMore = { paneMenuFor = leftState },
                )
            },
            second = {
                FilePane(
                    state = rightState,
                    onEntryLongPress = { actionTarget = ActionTarget(it, rightState) },
                    onNewFolder = {
                        paneMenuFor = rightState
                        dialog = DialogKind.NewFolder
                    },
                    onSort = { sortDialog = SortDialogState(rightState) },
                    onMore = { paneMenuFor = rightState },
                )
            },
        )
    }

    // 底部操作菜单
    actionTarget?.let { target ->
        ModalBottomSheet(onDismissRequest = { actionTarget = null }) {
            EntryActionSheet(
                target = target,
                onDismiss = { actionTarget = null },
                onAction = { action ->
                    actionTarget = null
                    when (action) {
                        "edit" -> onOpenEditor(target.entry.path)
                        "copy" -> controller.copyToClipboard(listOf(target.entry), target.pane, moveMode = false)
                        "move" -> controller.copyToClipboard(listOf(target.entry), target.pane, moveMode = true)
                        "rename" -> dialog = DialogKind.Rename(target.entry)
                        "delete" -> dialog = DialogKind.ConfirmDelete(listOf(target.entry))
                        "compress" -> dialog = DialogKind.Compress(target.entry)
                        "extract" -> scope.launch { controller.extractEntry(target.entry, target.pane) }
                        "properties" -> dialog = DialogKind.Properties(target.entry)
                        "copy_to_other" ->
                            scope.launch { controller.pasteInto(target.entry, otherPaneOf(target.pane, states)) }
                        "move_to_other" ->
                            scope.launch {
                                controller.moveToOther(
                                    target.entry,
                                    target.pane,
                                    otherPaneOf(target.pane, states),
                                )
                            }
                    }
                },
            )
        }
    }

    // 排序对话框
    sortDialog?.let { sortState ->
        AlertDialog(
            onDismissRequest = { sortDialog = null },
            title = { Text(stringResource(R.string.action_sort)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    com.neomanager.ui.file.SortKey.entries.forEach { key ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    key.displayName + if (sortState.state.sortKey == key) " ✓" else "",
                                )
                            },
                            onClick = { sortState.state.sortKey = key },
                        )
                    }
                    DropdownMenuItem(
                        text = {
                            Text(stringResource(R.string.sort_reverse) + if (sortState.state.sortReverse) " ✓" else "")
                        },
                        onClick = { sortState.state.sortReverse = !sortState.state.sortReverse },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(R.string.show_hidden_files) +
                                    if (sortState.state.showHidden) " ✓" else "",
                            )
                        },
                        onClick = { sortState.state.showHidden = !sortState.state.showHidden },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { sortDialog = null }) { Text(stringResource(R.string.action_done)) }
            },
        )
    }

    // 面板更多菜单（粘贴/隐藏开关/编辑器）
    paneMenuFor?.let { pane ->
        DropdownMenu(expanded = true, onDismissRequest = { paneMenuFor = null }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_paste, controller.clipboardCount())) },
                onClick = {
                    paneMenuFor = null
                    scope.launch { controller.pasteClipboard(pane) }
                },
                enabled = controller.clipboardCount() > 0,
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_back)) },
                onClick = {
                    paneMenuFor = null
                    pane.back()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.show_hidden_files)) },
                onClick = {
                    paneMenuFor = null
                    pane.showHidden = !pane.showHidden
                },
            )
        }
    }

    // 各类输入/确认对话框
    when (val d = dialog) {
        is DialogKind.NewFolder ->
            TextInputDialog(
                title = stringResource(R.string.action_new_folder),
                initial = "",
                onDismiss = { dialog = null },
            ) { name ->
                scope.launch {
                    controller.createFolder(activePane(), name)
                    dialog = null
                }
            }
        is DialogKind.Rename ->
            TextInputDialog(
                title = stringResource(R.string.action_rename),
                initial = d.entry.name,
                onDismiss = { dialog = null },
            ) { newName ->
                scope.launch {
                    controller.rename(d.entry, newName, activePane())
                    dialog = null
                }
            }
        is DialogKind.ConfirmDelete ->
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text(stringResource(R.string.action_delete)) },
                text = { Text(stringResource(R.string.confirm_delete, d.entries.size)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            dialog = null
                            scope.launch { controller.delete(d.entries, activePane()) }
                        },
                    ) { Text(stringResource(R.string.action_delete)) }
                },
                dismissButton = {
                    TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.action_cancel)) }
                },
            )
        is DialogKind.Compress ->
            TextInputDialog(
                title = stringResource(R.string.action_compress_to_zip),
                initial = d.entry.name.removeSuffix("/") + ".zip",
                onDismiss = { dialog = null },
            ) { archiveName ->
                scope.launch {
                    controller.compressToZip(d.entry, archiveName, activePane())
                    dialog = null
                }
            }
        is DialogKind.Properties -> {
            dialog = null
            PropertiesDialog(
                entry = d.entry,
                registry = registry,
                onDismiss = { },
            )
        }
        null -> Unit
    }
}

private fun otherPaneOf(
    current: FilePaneState,
    states: List<FilePaneState>,
): FilePaneState = states.firstOrNull { it !== current } ?: current

/** 条目操作菜单（观察规格 2026-10-10-file-manager.md 第 3 节，文案为自有实现） */
@Composable
private fun EntryActionSheet(
    target: ActionTarget,
    onDismiss: () -> Unit,
    onAction: (String) -> Unit,
) {
    val isZip =
        target.entry.name
            .substringAfterLast('.', "")
            .lowercase() in ZIP_EXTS
    Column(modifier = Modifier.padding(bottom = 24.dp)) {
        Text(
            text = target.entry.name,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        listOf(
            "copy" to stringResource(R.string.action_copy),
            "move" to stringResource(R.string.action_move),
            "copy_to_other" to stringResource(R.string.action_copy_to_other_pane),
            "move_to_other" to stringResource(R.string.action_move_to_other_pane),
            "rename" to stringResource(R.string.action_rename),
            "delete" to stringResource(R.string.action_delete),
            "compress" to stringResource(R.string.action_compress_to_zip),
            "extract" to stringResource(R.string.action_extract_here),
            "edit" to stringResource(R.string.action_edit_text),
            "properties" to stringResource(R.string.action_properties),
        ).forEach { (id, label) ->
            val enabled =
                when (id) {
                    "extract" -> isZip
                    "copy_to_other", "move_to_other" -> !isZip
                    else -> true
                }
            DropdownMenuItem(
                text = { Text(label, color = MaterialTheme.colorScheme.onSurface) },
                onClick = { onAction(id) },
                enabled = enabled,
            )
        }
        TextButton(onClick = onDismiss, modifier = Modifier.padding(start = 12.dp)) {
            Text(stringResource(R.string.action_cancel))
        }
    }
}

/** 通用文本输入对话框（新建/重命名/压缩命名） */
@Composable
private fun TextInputDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (text.isNotBlank()) onConfirm(text.trim()) },
                enabled = text.isNotBlank(),
            ) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

private val ZIP_EXTS = setOf("zip", "jar", "apk", "apks", "xapk", "apkm", "tar", "gz", "xz", "zst", "bz2", "7z", "rar")

private fun checkAllFilesAccess(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.R ||
        Environment.isExternalStorageManager()

/**
 * 「所有文件访问」授权引导横幅（Android 11+ 显示）。
 *
 * @param packageName 宿主应用包名（由 app 模块运行时注入，避免硬编码）
 * @param onResolved 授权动作触发后的状态复查回调
 */
@Composable
private fun AllFilesAccessBanner(
    packageName: String,
    onResolved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val intentLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { onResolved() }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.tertiaryContainer)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.all_files_access_banner),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
        Button(
            onClick = {
                val intent =
                    Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:$packageName"),
                    )
                intentLauncher.launch(intent)
            },
        ) {
            Text(stringResource(R.string.all_files_access_grant))
        }
    }
}
