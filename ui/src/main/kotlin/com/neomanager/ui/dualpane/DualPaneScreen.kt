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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.neomanager.engine.core.vfs.VfsRegistry
import com.neomanager.engine.core.vfs.VfsUri
import com.neomanager.ui.R
import com.neomanager.ui.bookmark.UiStateStore
import com.neomanager.ui.dualpane.actions.ActionTarget
import com.neomanager.ui.dualpane.actions.DialogKind
import com.neomanager.ui.dualpane.actions.FileActionsController
import com.neomanager.ui.dualpane.actions.SortDialogState
import com.neomanager.ui.engine.DynamicElevatedVfs
import com.neomanager.ui.engine.LocalEngineBridge
import com.neomanager.ui.file.EngineFileBrowser
import com.neomanager.ui.file.FileEntry
import com.neomanager.ui.file.FilePane
import com.neomanager.ui.file.FilePaneState
import com.neomanager.ui.util.OpenWith
import kotlinx.coroutines.launch

/**
 * 双窗口主屏（0.2.0-alpha07 Material You 重塑）。
 *
 * 布局自上而下：共享顶栏（路径/统计/搜索/多选）→ 左右双窗格（含右下角 FAB）→ 共享底栏
 * （后退/前进/添加/同步/上级，全宽均匀分布）。顶底栏始终作用于「活动窗口」：点击任一窗格即激活。
 *
 * - 浏览后端：[VfsRegistry]（本地 + zip/apk 内部 + Root/Shizuku 动态提权回退）；
 * - FAB：点击或上滑弹出书签/历史快速面板（MT 惯例）；
 * - 文件操作：长按条目 → 底部操作菜单（复制/移动/重命名/删除/压缩/解压/属性/编辑/多选）；
 * - 打开分发：目录/压缩包进入；文本→编辑器；图片→内置查看器；音视频/APK→系统应用；
 * - 权限策略（全本地化承诺，不申请 INTERNET）不变：完整存储访问引导 + 旧版运行时权限。
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
public fun DualPaneScreen(
    onOpenEditor: (String) -> Unit = {},
    onOpenImage: (String) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val engineBridge = LocalEngineBridge.current
    val scope = rememberCoroutineScope()

    // 提权后端动态解析：Shizuku 服务启动后台绑定，就绪后无需重组即对浏览生效
    val registry =
        remember {
            VfsRegistry(DynamicElevatedVfs { engineBridge.elevatedVfs() })
        }
    val browser = remember(registry) { EngineFileBrowser(registry) }

    val startPath =
        remember {
            @Suppress("DEPRECATION")
            Environment.getExternalStorageDirectory()?.absolutePath ?: FilePaneState.ROOT_PATH
        }
    // 抽屉导航状态（书签/历史/首页/手动隐藏持久化）
    val uiStore = remember { UiStateStore.default(context) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    val startUri =
        remember {
            // 首页路径优先（溢出菜单「设为首页」）；未设置或失效时回退内部存储根
            val home = uiStore.homePath()
            if (home != null && VfsUri.parseOrNull(home) != null) {
                home
            } else {
                VfsUri.ofLocal(startPath).value
            }
        }

    val leftState = remember { FilePaneState(startUri, browser) }
    val rightState = remember { FilePaneState(startUri, browser) }
    val states = remember { listOf(leftState, rightState) }

    // 活动窗口：共享顶栏/底栏的操作目标
    var activeIndex by remember { mutableIntStateOf(0) }
    val activeState = states[activeIndex]

    // 手动隐藏列表（两层隐藏开关中的「用户隐藏」，重载时现取保证即时生效）
    leftState.manualHiddenProvider = { uiStore.hiddenPaths() }
    rightState.manualHiddenProvider = { uiStore.hiddenPaths() }

    // 文件操作控制器（剪贴板 + 对话框状态）
    val controller =
        remember(registry) {
            FileActionsController(registry = registry)
        }

    // Shizuku 已授权时启动即自动绑定文件服务（下次列目录起提权后端可用）
    LaunchedEffect(Unit) {
        if (engineBridge.isShizukuGranted()) {
            runCatching { engineBridge.bindShizukuService() }
        }
    }

    // 文件打开策略：目录/压缩包 → 进入；文本 → 编辑器；图片 → 查看器；音视频/APK → 系统
    fun handleOpen(entry: FileEntry) {
        val name = entry.name.lowercase()
        when {
            entry.isDirectory -> return // state.open 已处理
            name.endsWith(".zip") ||
                name.endsWith(".apk") ||
                name.endsWith(".jar") ||
                name.endsWith(".apks") ||
                name.endsWith(".xapk") ||
                name.endsWith(".apkm") ||
                name.endsWith(".tar") ||
                name.endsWith(".gz") ||
                name.endsWith(".xz") ||
                name.endsWith(".zst") ||
                name.endsWith(".bz2") ||
                name.endsWith(".7z") ||
                name.endsWith(".rar") -> {
                val inner = VfsUri.zipOf(VfsUri.parseOrNull(entry.path) ?: return, "")
                (states.firstOrNull { it.path == entry.path } ?: leftState).openPath(inner.value)
            }
            name.endsWith(".jpg") ||
                name.endsWith(".jpeg") ||
                name.endsWith(".png") ||
                name.endsWith(".gif") ||
                name.endsWith(".webp") ||
                name.endsWith(".bmp") -> onOpenImage(entry.path)
            OpenWith.mimeFor(entry.name) != "application/octet-stream" -> {
                val result = OpenWith.openWithSystem(context, entry.path)
                result.onFailure { e ->
                    Toast.makeText(context, e.message ?: "无法打开", Toast.LENGTH_SHORT).show()
                }
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

    // 浏览历史：双窗格任一路径变化即写入（抽屉历史区展示）
    LaunchedEffect(Unit) {
        launch { snapshotFlow { leftState.path }.collect { uiStore.recordHistory(it) } }
        launch { snapshotFlow { rightState.path }.collect { uiStore.recordHistory(it) } }
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
    var quickSheet by remember { mutableStateOf(false) }

    // 首次启动手势引导（确认一次后不再弹出）
    var showTips by remember { mutableStateOf(!uiStore.isTipsSeen()) }
    if (showTips) {
        NeoTipsSheet(onAck = {
            uiStore.setTipsSeen()
            showTips = false
        })
    }

    // 多选批量操作分发：复制/剪切 → 剪贴板；删除 → 确认框（带来源面板）
    fun handleSelectionAction(
        pane: FilePaneState,
        action: String,
    ) {
        val picked = pane.selectedEntries()
        if (picked.isEmpty()) return
        when (action) {
            "copy" -> {
                controller.copyToClipboard(picked, pane, moveMode = false)
                pane.exitSelectionMode()
            }
            "cut" -> {
                controller.copyToClipboard(picked, pane, moveMode = true)
                pane.exitSelectionMode()
            }
            "delete" -> {
                pane.exitSelectionMode()
                dialog = DialogKind.ConfirmDelete(picked, pane)
            }
        }
    }

    // 隐藏开关作用于双窗格（应用级一致，避免两窗格口径不同）
    fun toggleSystemHidden() {
        val next = !activeState.showHidden
        states.forEach { it.showHidden = next }
    }

    fun toggleManualHidden() {
        val next = !activeState.showManualHidden
        states.forEach { it.showManualHidden = next }
    }

    // 隐藏所选文件：写入手动隐藏列表并刷新（顶栏溢出菜单「隐藏所选文件」）
    fun hideSelected(pane: FilePaneState) {
        val picked = pane.selectedEntries()
        if (picked.isEmpty()) return
        picked.forEach { uiStore.hidePath(it.path) }
        pane.exitSelectionMode()
        pane.refresh()
        Toast
            .makeText(context, context.getString(R.string.hidden_selected_done, picked.size), Toast.LENGTH_SHORT)
            .show()
    }

    NeoDrawerScaffold(
        drawerState = drawerState,
        activeState = activeState,
        store = uiStore,
        onNavigate = { uri -> activeState.openPath(uri) },
        onOpenSettings = onOpenSettings,
    ) {
        Column(modifier = modifier) {
            if (!hasAllFilesAccess) {
                AllFilesAccessBanner(
                    packageName = context.packageName,
                    onResolved = { hasAllFilesAccess = checkAllFilesAccess() },
                )
            }
            MtTopBar(
                state = activeState,
                onOpenDrawer = { scope.launch { drawerState.open() } },
                onNavigatePath = { uri -> activeState.openPath(uri) },
                onAddBookmark = {
                    val added =
                        uiStore.addBookmark(
                            name = displayNameFor(activeState.path),
                            path = activeState.path,
                        )
                    val message =
                        if (added) {
                            context.getString(R.string.bookmark_added)
                        } else {
                            context.getString(R.string.bookmark_exists)
                        }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                },
                onSetHome = {
                    uiStore.setHomePath(activeState.path)
                    Toast.makeText(context, context.getString(R.string.home_set), Toast.LENGTH_SHORT).show()
                },
                onSort = { sortDialog = SortDialogState(activeState) },
                onSwapWindows = {
                    val left = leftState.path
                    leftState.setPathInstant(rightState.path)
                    rightState.setPathInstant(left)
                },
                onHideSelected = { hideSelected(activeState) },
                onToggleSystemHidden = { toggleSystemHidden() },
                onToggleManualHidden = { toggleManualHidden() },
                onSelectionAction = { action -> handleSelectionAction(activeState, action) },
            )
            Box(Modifier.weight(1f)) {
                DualPaneLayout(
                    first = {
                        PaneHost(
                            state = leftState,
                            onActivate = { activeIndex = 0 },
                            onEntryLongPress = { entry -> actionTarget = ActionTarget(entry, leftState) },
                            onOpenSettings = onOpenSettings,
                        )
                    },
                    second = {
                        PaneHost(
                            state = rightState,
                            onActivate = { activeIndex = 1 },
                            onEntryLongPress = { entry -> actionTarget = ActionTarget(entry, rightState) },
                            onOpenSettings = onOpenSettings,
                        )
                    },
                )
                // 右下角 FAB：点击或上滑打开书签/历史快速面板（MT「底栏上滑」惯例的落地形态）
                var fabDrag by remember { mutableFloatStateOf(0f) }
                FloatingActionButton(
                    onClick = { quickSheet = true },
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                            .pointerInput(Unit) {
                                detectVerticalDragGestures(
                                    onDragStart = { fabDrag = 0f },
                                    onVerticalDrag = { change, amount ->
                                        change.consume()
                                        fabDrag += amount
                                    },
                                    onDragEnd = { if (fabDrag < -40f) quickSheet = true },
                                )
                            },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = stringResource(R.string.quick_sheet_open),
                    )
                }
            }
            MtBottomBar(
                state = activeState,
                pasteEnabled = controller.clipboardCount() > 0,
                onCreate = { dialog = DialogKind.Create },
                onPaste = { scope.launch { controller.pasteClipboard(activeState) } },
                onSync = { otherPaneOf(activeState, states).setPathInstant(activeState.path) },
            )
        }
    }

    // 书签/历史快速面板（FAB 弹出）
    if (quickSheet) {
        NeoQuickSheet(
            activeState = activeState,
            store = uiStore,
            onNavigate = { uri ->
                quickSheet = false
                activeState.openPath(uri)
            },
            onDismiss = { quickSheet = false },
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
                        "multi_select" -> {
                            target.pane.enterSelectionMode(target.entry)
                        }
                        "edit" -> onOpenEditor(target.entry.path)
                        "open_with" -> {
                            val result = OpenWith.openWithSystem(context, target.entry.path)
                            result.onFailure { e ->
                                Toast.makeText(context, e.message ?: "无法打开", Toast.LENGTH_SHORT).show()
                            }
                        }
                        "copy" -> controller.copyToClipboard(listOf(target.entry), target.pane, moveMode = false)
                        "move" -> controller.copyToClipboard(listOf(target.entry), target.pane, moveMode = true)
                        "rename" -> dialog = DialogKind.Rename(target.entry)
                        "delete" -> dialog = DialogKind.ConfirmDelete(listOf(target.entry), target.pane)
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
                }
            },
            confirmButton = {
                TextButton(onClick = { sortDialog = null }) { Text(stringResource(R.string.action_done)) }
            },
        )
    }

    // 各类输入/确认对话框
    when (val d = dialog) {
        is DialogKind.Create ->
            CreateDialog(
                onCreateFile = { name ->
                    scope.launch {
                        controller.createFile(activeState, name)
                        dialog = null
                    }
                },
                onCreateFolder = { name ->
                    scope.launch {
                        controller.createFolder(activeState, name)
                        dialog = null
                    }
                },
                onDismiss = { dialog = null },
            )
        is DialogKind.Rename ->
            TextInputDialog(
                title = stringResource(R.string.action_rename),
                initial = d.entry.name,
                onDismiss = { dialog = null },
            ) { newName ->
                scope.launch {
                    controller.rename(d.entry, newName, activeState)
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
                            scope.launch { controller.delete(d.entries, d.pane) }
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
                    controller.compressToZip(d.entry, archiveName, activeState)
                    dialog = null
                }
            }
        is DialogKind.Properties ->
            PropertiesDialog(
                entry = d.entry,
                registry = registry,
                onDismiss = { dialog = null },
            )
        null -> Unit
    }
}

/** 单个窗格宿主：空白区点击 / 条目交互均将本窗格设为活动窗口 */
@Composable
private fun PaneHost(
    state: FilePaneState,
    onActivate: () -> Unit,
    onEntryLongPress: (FileEntry) -> Unit,
    onOpenSettings: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onActivate() })
                },
    ) {
        FilePane(
            state = state,
            onEntryLongPress = { entry ->
                onActivate()
                onEntryLongPress(entry)
            },
            onInteract = onActivate,
            onOpenSettings = onOpenSettings,
        )
    }
}

private fun otherPaneOf(
    current: FilePaneState,
    states: List<FilePaneState>,
): FilePaneState = states.firstOrNull { it !== current } ?: current

/**
 * MT 式「新建」对话框：文件名输入 + 取消 / 文件 / 文件夹（0.2.0-alpha07）。
 */
@Composable
private fun CreateDialog(
    onCreateFile: (String) -> Unit,
    onCreateFolder: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val valid = text.isNotBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_new)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        confirmButton = {
            Row {
                TextButton(
                    onClick = { onCreateFile(text.trim()) },
                    enabled = valid,
                ) { Text(stringResource(R.string.action_new_file)) }
                TextButton(
                    onClick = { onCreateFolder(text.trim()) },
                    enabled = valid,
                ) { Text(stringResource(R.string.action_new_folder)) }
            }
        },
    )
}

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
            "multi_select" to stringResource(R.string.action_multi_select),
            "copy" to stringResource(R.string.action_copy),
            "move" to stringResource(R.string.action_move),
            "copy_to_other" to stringResource(R.string.action_copy_to_other_pane),
            "move_to_other" to stringResource(R.string.action_move_to_other_pane),
            "rename" to stringResource(R.string.action_rename),
            "delete" to stringResource(R.string.action_delete),
            "compress" to stringResource(R.string.action_compress_to_zip),
            "extract" to stringResource(R.string.action_extract_here),
            "edit" to stringResource(R.string.action_edit_text),
            "open_with" to stringResource(R.string.action_open_with_system),
            "properties" to stringResource(R.string.action_properties),
        ).forEach { (id, label) ->
            val enabled =
                when (id) {
                    "extract" -> isZip
                    "copy_to_other", "move_to_other" -> !isZip
                    "open_with" -> !isZip && OpenWith.isLocalPath(target.entry.path)
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

/** 通用文本输入对话框（重命名/压缩命名） */
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

/** 「所有文件访问」状态（防御性包裹：个别框架/ROM 下系统接口可能异常，失败按未授权处理） */
private fun checkAllFilesAccess(): Boolean =
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
        true
    } else {
        runCatching { Environment.isExternalStorageManager() }.getOrDefault(false)
    }

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
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.all_files_access_banner),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
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
