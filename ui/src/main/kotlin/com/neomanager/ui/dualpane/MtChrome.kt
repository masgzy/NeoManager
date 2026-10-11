/*
 * Copyright (C) 2026 The NeoManager Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.neomanager.ui.dualpane

import android.os.StatFs
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neomanager.engine.core.vfs.VfsUri
import com.neomanager.ui.R
import com.neomanager.ui.file.FilePaneState
import com.neomanager.ui.theme.MtChrome
import com.neomanager.ui.theme.MtChromeOn
import com.neomanager.ui.theme.MtChromeOnDim
import com.neomanager.ui.theme.MtChromeUnderline
import kotlinx.coroutines.delay
import java.util.Locale

/** 底栏图标色（白底上的炭灰） */
private val MtBottomIcon = Color(0xFF3B3B3B)

/** 底栏禁用图标色 */
private val MtBottomIconDim = Color(0xFFBDBDBD)

/**
 * MT 风格共享顶栏（0.2.0-alpha05）。
 *
 * 三种形态互斥切换（均作用于活动窗口 [state]）：
 * - 常规：汉堡菜单 + 大号路径 + 统计副标题（目录数/文件数/储存） + 溢出菜单；
 * - 搜索：搜索图标 + 下划线输入框 + 关闭；
 * - 多选：关闭 + 已选计数 + 全选 + 复制/剪切/删除。
 */
@Composable
public fun MtTopBar(
    state: FilePaneState,
    modifier: Modifier = Modifier,
    pasteEnabled: Boolean,
    onPaste: () -> Unit,
    onSort: () -> Unit,
    onSwapWindows: () -> Unit,
    onOpenSettings: () -> Unit,
    onSelectionAction: (String) -> Unit,
) {
    Column(modifier = modifier.background(MtChrome)) {
        Column(Modifier.statusBarsPadding()) {
            when {
                state.selectionMode -> SelectionBar(state = state, onAction = onSelectionAction)
                state.searchActive -> SearchBar(state = state)
                else ->
                    PathBar(
                        state = state,
                        pasteEnabled = pasteEnabled,
                        onPaste = onPaste,
                        onSort = onSort,
                        onSwapWindows = onSwapWindows,
                        onOpenSettings = onOpenSettings,
                    )
            }
        }
    }
}

/** 常规形态：汉堡 + 路径/统计 + 溢出菜单 */
@Composable
private fun PathBar(
    state: FilePaneState,
    pasteEnabled: Boolean,
    onPaste: () -> Unit,
    onSort: () -> Unit,
    onSwapWindows: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var hamburgerOpen by remember { mutableStateOf(false) }
    var overflowOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().height(64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            IconButton(onClick = { hamburgerOpen = true }) {
                Icon(Icons.Filled.Menu, contentDescription = null, tint = MtChromeOn)
            }
            DropdownMenu(expanded = hamburgerOpen, onDismissRequest = { hamburgerOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_paste_plain)) },
                    onClick = {
                        hamburgerOpen = false
                        onPaste()
                    },
                    enabled = pasteEnabled,
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_settings)) },
                    onClick = {
                        hamburgerOpen = false
                        onOpenSettings()
                    },
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f).padding(start = 4.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = state.path,
                style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                color = MtChromeOn,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = paneSubtitle(state),
                style = TextStyle(fontSize = 12.sp),
                color = MtChromeOnDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box {
            IconButton(onClick = { overflowOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = null, tint = MtChromeOn)
            }
            DropdownMenu(expanded = overflowOpen, onDismissRequest = { overflowOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_refresh)) },
                    onClick = {
                        overflowOpen = false
                        state.refresh()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_search)) },
                    onClick = {
                        overflowOpen = false
                        state.openSearch()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_select_all)) },
                    onClick = {
                        overflowOpen = false
                        state.enterSelectAllMode()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_sort)) },
                    onClick = {
                        overflowOpen = false
                        onSort()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.show_hidden_files)) },
                    onClick = {
                        overflowOpen = false
                        state.showHidden = !state.showHidden
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_swap_windows)) },
                    onClick = {
                        overflowOpen = false
                        onSwapWindows()
                    },
                )
            }
        }
    }
}

/** 搜索形态：搜索图标 + 占位提示 + 下划线输入 + 关闭（输入防抖 350ms 自动搜索） */
@Composable
private fun SearchBar(state: FilePaneState) {
    LaunchedEffect(state.searchQuery) {
        delay(350)
        state.search(state.searchQuery)
    }
    Row(
        modifier = Modifier.fillMaxWidth().height(60.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = MtChromeOnDim,
            modifier = Modifier.padding(start = 20.dp).size(22.dp),
        )
        Box(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            if (state.searchQuery.isBlank()) {
                Text(
                    text = stringResource(R.string.search_hint),
                    style = TextStyle(fontSize = 15.sp),
                    color = MtChromeOnDim,
                    maxLines = 1,
                )
            }
            Column {
                BasicTextField(
                    value = state.searchQuery,
                    onValueChange = { state.searchQuery = it },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 15.sp, color = MtChromeOn),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(MtChromeUnderline),
                )
            }
        }
        IconButton(onClick = state::closeSearch) {
            Icon(Icons.Filled.Close, contentDescription = null, tint = MtChromeOn)
        }
    }
}

/** 多选形态：关闭 + 已选计数 + 全选 + 批量操作 */
@Composable
private fun SelectionBar(
    state: FilePaneState,
    onAction: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = state::exitSelectionMode) {
            Icon(Icons.Filled.Close, contentDescription = null, tint = MtChromeOn)
        }
        Text(
            text = stringResource(R.string.selection_count, state.selectedPaths.size),
            style = TextStyle(fontSize = 16.sp),
            color = MtChromeOn,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(onClick = state::selectAll) {
            Icon(Icons.Filled.DoneAll, contentDescription = null, tint = MtChromeOn)
        }
        val enabled = state.selectedPaths.isNotEmpty()
        IconButton(onClick = { onAction("copy") }, enabled = enabled) {
            Icon(
                Icons.Filled.ContentCopy,
                contentDescription = null,
                tint = if (enabled) MtChromeOn else MtChromeOnDim,
            )
        }
        IconButton(onClick = { onAction("cut") }, enabled = enabled) {
            Icon(
                Icons.Filled.ContentCut,
                contentDescription = null,
                tint = if (enabled) MtChromeOn else MtChromeOnDim,
            )
        }
        IconButton(onClick = { onAction("delete") }, enabled = enabled) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = null,
                tint = if (enabled) MtChromeOn else MtChromeOnDim,
            )
        }
    }
}

/** 副标题统计：搜索模式显示结果数；常规显示目录/文件数与储存容量 */
@Composable
private fun paneSubtitle(state: FilePaneState): String =
    if (state.searchActive) {
        stringResource(R.string.stats_search_results, state.searchResults.size)
    } else {
        val folders = state.entries.count { it.isDirectory }
        val files = state.entries.size - folders
        val storage = remember(state.path) { storageStats(state.path) }
        if (storage != null) {
            stringResource(R.string.stats_folders_files_storage, folders, files, storage)
        } else {
            stringResource(R.string.stats_folders_files, folders, files)
        }
    }

/** 本地目录的储存容量「可用/总容量」；非本地路径或读取失败返回 null */
private fun storageStats(path: String): String? {
    val uri = VfsUri.parseOrNull(path) ?: return null
    if (!uri.isLocal) return null
    return runCatching {
        val stat = StatFs(uri.localPath)
        "${gigabytes(stat.availableBytes)}/${gigabytes(stat.totalBytes)}"
    }.getOrNull()
}

/** 字节转「NN.NNG」容量文案（≥100G 取整） */
private fun gigabytes(bytes: Long): String {
    val value = bytes / 1024.0 / 1024.0 / 1024.0
    val text = if (value >= 100.0) value.toInt().toString() else String.format(Locale.US, "%.2f", value)
    return "${text}G"
}

/**
 * MT 风格共享底栏：后退 / 前进 / 添加 / 同步对侧 / 上级（作用于活动窗口 [state]）。
 */
@Composable
public fun MtBottomBar(
    state: FilePaneState,
    modifier: Modifier = Modifier,
    pasteEnabled: Boolean,
    onNewFolder: () -> Unit,
    onPaste: () -> Unit,
    onSync: () -> Unit,
) {
    var addOpen by remember { mutableStateOf(false) }
    Column(modifier = modifier.background(MaterialTheme.colorScheme.surface)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = state::back) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    tint = MtBottomIcon,
                )
            }
            IconButton(onClick = state::forward, enabled = state.canGoForward) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = stringResource(R.string.action_forward),
                    tint = if (state.canGoForward) MtBottomIcon else MtBottomIconDim,
                )
            }
            Box {
                IconButton(onClick = { addOpen = true }) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = MtBottomIcon)
                }
                DropdownMenu(expanded = addOpen, onDismissRequest = { addOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_new_folder)) },
                        onClick = {
                            addOpen = false
                            onNewFolder()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_paste_plain)) },
                        onClick = {
                            addOpen = false
                            onPaste()
                        },
                        enabled = pasteEnabled,
                    )
                }
            }
            IconButton(onClick = onSync) {
                Icon(
                    Icons.Filled.SwapHoriz,
                    contentDescription = stringResource(R.string.action_sync_windows),
                    tint = MtBottomIcon,
                )
            }
            IconButton(onClick = state::up, enabled = state.canGoUp) {
                Icon(
                    Icons.Filled.ArrowUpward,
                    contentDescription = stringResource(R.string.action_go_up),
                    tint = if (state.canGoUp) MtBottomIcon else MtBottomIconDim,
                )
            }
        }
    }
}
