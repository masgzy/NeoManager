/*
 * Copyright (C) 2026 The NeoManager Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.neomanager.ui.file

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.neomanager.ui.R
import com.neomanager.ui.util.Format

/**
 * 单侧文件面板：路径栏（或搜索框）+ 条目列表 + 底部工具栏。
 *
 * 面板为无状态展示组件，状态由 [FilePaneState] 持有并经参数注入；
 * 双面板复用同一实现。长按进入多选模式，多选时底部工具栏切换为批量操作。
 *
 * @param state 面板状态（调用方需在组合内调用一次 [FilePaneState.Effect]）
 * @param onEntryLongPress 长按条目回调（弹出操作菜单）
 * @param onSelectionAction 多选操作回调（"copy"/"cut"/"delete"）
 * @param onNewFolder 新建文件夹回调（工具栏）
 * @param onSort 排序设置回调（工具栏）
 * @param onMore 更多菜单回调（工具栏）
 */
@Composable
public fun FilePane(
    state: FilePaneState,
    modifier: Modifier = Modifier,
    onEntryLongPress: (FileEntry) -> Unit = {},
    onSelectionAction: (String) -> Unit = {},
    onNewFolder: () -> Unit = {},
    onSort: () -> Unit = {},
    onMore: () -> Unit = {},
) {
    // 组合期副作用：path/刷新计数/排序变化时重新加载目录（缺失将导致列表永不加载）
    state.Effect()
    Column(modifier = modifier.fillMaxSize()) {
        // 顶部：多选模式→多选栏；搜索模式→搜索框；否则路径栏
        when {
            state.selectionMode ->
                SelectionBar(
                    state = state,
                    onAction = onSelectionAction,
                    onExit = state::exitSelectionMode,
                )
            state.searchActive ->
                SearchBar(
                    state = state,
                    onClose = state::closeSearch,
                )
            else ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { state.back() }, enabled = state.canGoUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                    Text(
                        text = state.path,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(onClick = state::openSearch) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = stringResource(R.string.action_search),
                        )
                    }
                    IconButton(onClick = { state.refresh() }) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.action_refresh),
                        )
                    }
                }
        }

        HorizontalDivider()

        // 内容区：加载 / 错误 / 列表
        when {
            state.isLoading ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            state.error != null ->
                ErrorPane(
                    message = state.error.orEmpty(),
                    onRetry = { state.refresh() },
                    modifier = Modifier.fillMaxSize(),
                )
            else -> {
                val visible =
                    if (state.searchActive && state.searchQuery.isNotBlank()) {
                        state.searchResults
                    } else {
                        state.entries
                    }
                EntryList(
                    entries = visible,
                    onOpen = state::open,
                    onLongPress = onEntryLongPress,
                    isSelected = state::isSelected,
                    selectionMode = state.selectionMode,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        HorizontalDivider()

        // 底部工具栏：多选模式时由 SelectionBar 占位（顶部），此处仍为常规工具栏
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (!state.selectionMode) {
                IconButton(onClick = onNewFolder) {
                    Icon(
                        imageVector = Icons.Filled.CreateNewFolder,
                        contentDescription = stringResource(R.string.action_new_folder),
                    )
                }
                IconButton(onClick = onSort) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Sort,
                        contentDescription = stringResource(R.string.action_sort),
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.items_count, state.entries.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(onClick = onMore) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.action_more),
                    )
                }
            }
        }
    }
}

/** 多选模式顶栏：全选 + 已选计数 + 批量操作（复制/剪切/删除） + 退出 */
@Composable
private fun SelectionBar(
    state: FilePaneState,
    onAction: (String) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        IconButton(onClick = onExit) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_cancel))
        }
        Text(
            text = stringResource(R.string.selection_count, state.selectedPaths.size),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        IconButton(onClick = state::selectAll, enabled = true) {
            Icon(Icons.Filled.DoneAll, contentDescription = stringResource(R.string.action_select_all))
        }
        IconButton(
            onClick = { onAction("copy") },
            enabled = state.selectedPaths.isNotEmpty(),
        ) {
            Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.action_copy))
        }
        IconButton(
            onClick = { onAction("cut") },
            enabled = state.selectedPaths.isNotEmpty(),
        ) {
            Icon(Icons.Filled.ContentCut, contentDescription = stringResource(R.string.action_cut))
        }
        IconButton(
            onClick = { onAction("delete") },
            enabled = state.selectedPaths.isNotEmpty(),
        ) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
        }
    }
}

/** 搜索模式顶栏：返回 + 关键词输入框（输入防抖后自动执行递归搜索） */
@Composable
private fun SearchBar(
    state: FilePaneState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    // 输入防抖：停顿 350ms 后自动搜索
    LaunchedEffect(state.searchQuery) {
        kotlinx.coroutines.delay(350)
        state.search(state.searchQuery)
    }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
        }
        BasicTextField(
            value = state.searchQuery,
            onValueChange = { state.searchQuery = it },
            modifier =
                Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
            decorationBox = { inner ->
                Box {
                    if (state.searchQuery.isEmpty()) {
                        Text(
                            text = stringResource(R.string.search_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    inner()
                }
            },
        )
        if (state.isSearching) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
        }
    }
}

@Composable
private fun ErrorPane(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.load_failed),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onRetry) {
            Text(stringResource(R.string.action_retry))
        }
    }
}

@Composable
private fun EntryList(
    entries: List<FileEntry>,
    onOpen: (FileEntry) -> Unit,
    onLongPress: (FileEntry) -> Unit,
    isSelected: (FileEntry) -> Boolean,
    selectionMode: Boolean,
    modifier: Modifier = Modifier,
) {
    if (entries.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.empty_folder),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(modifier = modifier) {
        items(items = entries, key = { it.path }) { entry ->
            FileRow(
                entry = entry,
                onClick = { onOpen(entry) },
                onLongPress = { onLongPress(entry) },
                selected = isSelected(entry),
                selectionMode = selectionMode,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        }
    }
}

/** 列表行：类型图标 + 名称 + 摘要（目录条数 / 大小与时间）；多选模式显示勾选框 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
    entry: FileEntry,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    selected: Boolean,
    selectionMode: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongPress)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectionMode) {
            Checkbox(checked = selected, onCheckedChange = { onClick() })
        } else {
            Icon(
                imageVector = entry.icon(),
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint =
                    if (entry.isDirectory) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
            )
        }
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
        ) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (entry.isDirectory) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = entry.subtitle(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 条目摘要文案：目录返回空串（子项计数需引擎遍历支持，Phase 1 ② 提供），
 * 文件返回「大小 + 修改时间」。
 */
private fun FileEntry.subtitle(): String =
    when {
        isDirectory -> ""
        else ->
            listOf(Format.fileSize(sizeBytes), Format.modifiedTime(lastModified))
                .filter { it.isNotEmpty() }
                .joinToString("  ")
    }

/** 依据名称与类型推断展示图标 */
private fun FileEntry.icon(): ImageVector =
    when {
        isDirectory -> Icons.Filled.Folder
        isApkLike() -> Icons.Filled.Android
        isArchive() -> Icons.Filled.FolderZip
        isImage() -> Icons.Filled.Image
        isAudio() -> Icons.Filled.MusicNote
        isVideo() -> Icons.Filled.Movie
        isTextLike() -> Icons.Filled.Description
        else -> Icons.AutoMirrored.Filled.InsertDriveFile
    }

private fun FileEntry.isApkLike(): Boolean =
    name.endsWith(".apk", true) ||
        name.endsWith(".xapk", true) ||
        name.endsWith(".apks", true)

private fun FileEntry.isArchive(): Boolean =
    name.endsWith(".zip", true) ||
        name.endsWith(".7z", true) ||
        name.endsWith(".rar", true) ||
        name.endsWith(".tar", true) ||
        name.endsWith(".gz", true) ||
        name.endsWith(".xz", true) ||
        name.endsWith(".zst", true)

private fun FileEntry.isImage(): Boolean =
    name.endsWith(".jpg", true) ||
        name.endsWith(".jpeg", true) ||
        name.endsWith(".png", true) ||
        name.endsWith(".gif", true) ||
        name.endsWith(".webp", true) ||
        name.endsWith(".bmp", true) ||
        name.endsWith(".svg", true)

private fun FileEntry.isAudio(): Boolean =
    name.endsWith(".mp3", true) ||
        name.endsWith(".flac", true) ||
        name.endsWith(".wav", true) ||
        name.endsWith(".ogg", true) ||
        name.endsWith(".m4a", true)

private fun FileEntry.isVideo(): Boolean =
    name.endsWith(".mp4", true) ||
        name.endsWith(".mkv", true) ||
        name.endsWith(".avi", true) ||
        name.endsWith(".mov", true) ||
        name.endsWith(".webm", true)

private fun FileEntry.isTextLike(): Boolean = TEXT_EXTENSIONS.any { name.endsWith(it, true) }

private val TEXT_EXTENSIONS =
    arrayOf(
        ".txt",
        ".xml",
        ".json",
        ".kt",
        ".java",
        ".smali",
        ".md",
        ".log",
        ".csv",
        ".yml",
        ".yaml",
        ".html",
        ".js",
        ".py",
        ".sh",
        ".ini",
        ".properties",
    )
