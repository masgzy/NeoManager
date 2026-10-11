/*
 * Copyright (C) 2026 The NeoManager Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.neomanager.ui.file

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neomanager.ui.R
import com.neomanager.ui.theme.NeoIconApk
import com.neomanager.ui.theme.NeoIconArchive
import com.neomanager.ui.theme.NeoIconAudio
import com.neomanager.ui.theme.NeoIconGeneric
import com.neomanager.ui.theme.NeoIconImage
import com.neomanager.ui.theme.NeoIconText
import com.neomanager.ui.theme.NeoIconVideo
import com.neomanager.ui.util.Format

/** 虚拟「..」行的稳定 key（避免与真实条目路径冲突） */
private const val PARENT_ROW_KEY: String = "__parent_row__"

/**
 * 单侧文件面板（0.2.0-alpha07 Material You 版）。
 *
 * 顶栏（路径/统计/搜索/多选操作）与底栏（后退/前进/添加/同步/上级）由双窗口外壳统一持有，
 * 面板仅负责内容区：加载 / 错误 / 条目列表。点击或长按条目会触发 [onInteract]
 * （外壳据此把该面板标记为活动窗口）。
 * 列表支持下拉刷新（Material 3 PullToRefresh）。
 *
 * @param state 面板状态（调用方需在组合内调用一次 [FilePaneState.Effect]）
 * @param onEntryLongPress 长按条目回调（弹出操作菜单）
 * @param onInteract 面板内任意条目交互（标记活动窗口）
 * @param onOpenSettings 权限类错误引导跳转设置页
 */
@Composable
public fun FilePane(
    state: FilePaneState,
    modifier: Modifier = Modifier,
    onEntryLongPress: (FileEntry) -> Unit = {},
    onInteract: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
) {
    // 组合期副作用：path/刷新计数/排序变化时重新加载目录（缺失将导致列表永不加载）
    state.Effect()
    Box(modifier = modifier.fillMaxSize()) {
        when {
            state.isLoading && state.entries.isEmpty() ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            state.error != null ->
                ErrorPane(
                    message = state.error.orEmpty(),
                    onRetry = { state.refresh() },
                    onOpenSettings = onOpenSettings,
                    modifier = Modifier.fillMaxSize(),
                )
            else -> {
                val searching = state.searchActive && state.searchQuery.isNotBlank()
                val visible = if (searching) state.searchResults else state.entries
                EntryList(
                    state = state,
                    entries = visible,
                    searching = searching,
                    onOpen = state::open,
                    onLongPress = onEntryLongPress,
                    onInteract = onInteract,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun ErrorPane(
    message: String,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 权限类失败给出设置引导（Root/Shizuku 提权开关位于设置页引擎分区）
    val needsGrant = message.contains("权限") || message.contains("Root") || message.contains("Shizuku")
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
        Row {
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.action_retry))
            }
            if (needsGrant) {
                TextButton(onClick = onOpenSettings) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.action_settings))
                }
            }
        }
    }
}

/** 条目列表：顶部可选「..」返回行 + 文件行；下拉触发刷新（搜索态不启用） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryList(
    state: FilePaneState,
    entries: List<FileEntry>,
    searching: Boolean,
    onOpen: (FileEntry) -> Unit,
    onLongPress: (FileEntry) -> Unit,
    onInteract: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (entries.isEmpty() && !state.canGoUp) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text =
                    if (searching) {
                        stringResource(R.string.search_no_result)
                    } else {
                        stringResource(R.string.empty_folder)
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    PullToRefreshBox(
        isRefreshing = state.isLoading,
        onRefresh = { state.refresh() },
        modifier = modifier,
    ) {
        LazyColumn(Modifier.fillMaxSize()) {
            if (!searching && state.canGoUp) {
                item(key = PARENT_ROW_KEY) {
                    ParentRow(
                        onClick = {
                            onInteract()
                            state.up()
                        },
                    )
                }
            }
            items(items = entries, key = { it.path }) { entry ->
                FileRow(
                    entry = entry,
                    selected = state.isSelected(entry),
                    selectionMode = state.selectionMode,
                    onClick = {
                        onInteract()
                        onOpen(entry)
                    },
                    onLongPress = {
                        onInteract()
                        onLongPress(entry)
                    },
                )
            }
        }
    }
}

/** 「..」返回上级行（与普通文件夹行同样式，无副标题） */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ParentRow(onClick: () -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MtFolderIcon(modifier = Modifier.size(40.dp))
        Text(
            text = "..",
            style = rowTitleStyle(),
            modifier = Modifier.padding(start = 12.dp),
            maxLines = 1,
        )
    }
}

/** 文件行：类型图标 + 名称 + 摘要；多选模式在行尾显示勾选框，选中行铺次级容器底色 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
    entry: FileEntry,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                .combinedClickable(onClick = onClick, onLongClick = onLongPress)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MtEntryIcon(entry = entry)
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
        ) {
            Text(
                text = entry.name,
                style = rowTitleStyle(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = entry.subtitle(),
                style = TextStyle(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selectionMode) {
            Checkbox(checked = selected, onCheckedChange = { onClick() })
        }
    }
}

@Composable
private fun rowTitleStyle(): TextStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal)

/**
 * 条目摘要文案：目录返回修改时间（MT 惯例），文件返回「大小 + 修改时间」。
 */
private fun FileEntry.subtitle(): String =
    when {
        isDirectory -> Format.modifiedTime(lastModified)
        else ->
            listOf(Format.fileSize(sizeBytes), Format.modifiedTime(lastModified))
                .filter { it.isNotEmpty() }
                .joinToString("  ")
    }

/** 条目图标：目录为自绘立体文件夹（动态主色）；文件为按类型着色的矢量图标（40dp 视觉框内居中） */
@Composable
private fun MtEntryIcon(entry: FileEntry) {
    if (entry.isDirectory) {
        MtFolderIcon(modifier = Modifier.size(40.dp))
    } else {
        Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = entry.fileIcon(),
                contentDescription = null,
                modifier = Modifier.size(30.dp),
                tint = entry.iconColor(),
            )
        }
    }
}

/** 立体文件夹图标：主题主色夹体 + 左侧内页纸（内页用容器色保证明暗可读） */
@Composable
private fun MtFolderIcon(modifier: Modifier = Modifier) {
    val body = MaterialTheme.colorScheme.primary
    val sheet = MaterialTheme.colorScheme.surface
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val radius = CornerRadius(w * 0.09f, w * 0.09f)
        // 夹耳（顶部小圆角块）
        drawRoundRect(
            color = body,
            topLeft = Offset(w * 0.10f, h * 0.16f),
            size = Size(w * 0.46f, h * 0.24f),
            cornerRadius = radius,
        )
        // 夹体
        drawRoundRect(
            color = body,
            topLeft = Offset(w * 0.08f, h * 0.28f),
            size = Size(w * 0.84f, h * 0.54f),
            cornerRadius = radius,
        )
        // 左侧内页（纸）
        drawRoundRect(
            color = sheet,
            topLeft = Offset(w * 0.17f, h * 0.42f),
            size = Size(w * 0.13f, h * 0.30f),
            cornerRadius = CornerRadius(w * 0.03f, w * 0.03f),
        )
    }
}

/** 依据名称与类型推断文件图标（目录不适用） */
private fun FileEntry.fileIcon(): ImageVector =
    when {
        isApkLike() -> Icons.Filled.Android
        isArchive() -> Icons.Filled.FolderZip
        isImage() -> Icons.Filled.Image
        isAudio() -> Icons.Filled.MusicNote
        isVideo() -> Icons.Filled.Movie
        isTextLike() -> Icons.AutoMirrored.Filled.InsertDriveFile
        else -> Icons.AutoMirrored.Filled.InsertDriveFile
    }

private fun FileEntry.iconColor(): Color =
    when {
        isApkLike() -> NeoIconApk
        isArchive() -> NeoIconArchive
        isImage() -> NeoIconImage
        isAudio() -> NeoIconAudio
        isVideo() -> NeoIconVideo
        isTextLike() -> NeoIconText
        else -> NeoIconGeneric
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
