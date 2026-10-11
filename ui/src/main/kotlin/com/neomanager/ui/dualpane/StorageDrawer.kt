/*
 * Copyright (C) 2026 The NeoManager Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.neomanager.ui.dualpane

import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neomanager.engine.core.vfs.VfsUri
import com.neomanager.ui.R
import com.neomanager.ui.bookmark.UiStateStore
import com.neomanager.ui.file.FilePaneState
import java.io.File

/** 抽屉历史展示条数上限（存储仍为全量 [UiStateStore.HISTORY_LIMIT] 条） */
private const val HISTORY_SHOW_LIMIT: Int = 20

/** 单条存储位置（标签 + VfsUri 字符串） */
internal data class StorageLocation(
    val label: String,
    val uri: String,
)

/**
 * 枚举可浏览的存储位置（规格 2026-10-10-file-manager.md 第 1 节「首页可为存储列表」）：
 * 内部存储优先，其次 /storage 下可读的外置卷（SD 卡/USB OTG），末尾附根目录。
 * 展示名与 [com.neomanager.ui.file.SortKey.displayName] 同惯例采用中文字面量。
 */
internal fun storageLocations(): List<StorageLocation> {
    val out = mutableListOf<StorageLocation>()

    @Suppress("DEPRECATION")
    val external = Environment.getExternalStorageDirectory()
    val seen = mutableSetOf<String>()
    if (external != null) {
        val uri = VfsUri.ofLocal(external.absolutePath).value
        out += StorageLocation("内部存储", uri)
        seen += uri
    }
    File("/storage")
        .listFiles()
        ?.sortedBy { it.name }
        ?.forEach { volume ->
            if (!volume.isDirectory || !volume.canRead()) return@forEach
            val uri = VfsUri.ofLocal(volume.absolutePath).value
            if (uri in seen) return@forEach
            seen += uri
            out += StorageLocation(volume.name, uri)
        }
    out += StorageLocation("根目录", FilePaneState.ROOT_PATH)
    return out
}

/** 从路径推导展示名：本地/zip 取末段，解析失败原样返回 */
internal fun displayNameFor(path: String): String = VfsUri.parseOrNull(path)?.name ?: path

/**
 * 路径的净化展示形式（0.2.0-alpha07）：
 * - 本地：剥离 file:// 前缀，直接显示 POSIX 绝对路径（根为 /）；
 * - zip：显示「容器名/内部路径」（zip 根仅显示容器名）；
 * - 解析失败原样返回。
 */
internal fun displayPath(path: String): String {
    val uri = VfsUri.parseOrNull(path) ?: return path
    return when (uri.scheme) {
        VfsUri.SCHEME_FILE -> uri.localPath
        VfsUri.SCHEME_ZIP -> {
            val inner = uri.zipInnerPath
            val container = uri.containerUri
            buildString {
                append(container.name)
                if (inner.isNotEmpty()) {
                    append('/')
                    append(inner)
                }
            }
        }
        else -> uri.value
    }
}

/** 供顶栏路径跳转使用的解析：接受完整 URI 或本地绝对路径；非法返回 null */
internal fun resolvePathInput(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    VfsUri.parseOrNull(trimmed)?.let { return it.value }
    if (trimmed.startsWith("/")) return VfsUri.ofLocal(trimmed).value
    return null
}

/**
 * MT 风格侧边抽屉脚手架（规格第 1/7 节）：存储位置 + 书签 + 历史记录，包裹主界面 [content]。
 *
 * 跳转作用于「活动窗口」[activeState]；书签支持添加（标题栏 +）/ 删除（行尾按钮），
 * 历史记录由主屏在导航时写入 [store]，可一键清除；全部数据应用内私有持久化（无网络）。
 */
@Composable
public fun NeoDrawerScaffold(
    drawerState: DrawerState,
    activeState: FilePaneState,
    store: UiStateStore,
    onNavigate: (String) -> Unit,
    onOpenSettings: () -> Unit,
    content: @Composable () -> Unit,
) {
    val open = drawerState.targetValue == DrawerValue.Open
    // 每次打开抽屉重算存储列表与历史（反映外置卷插拔与最新浏览）
    val locations = remember(open) { storageLocations() }
    var bookmarks by remember(open) { mutableStateOf(store.bookmarks()) }
    var history by remember(open) { mutableStateOf(store.history()) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surface) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    DrawerSectionTitle(stringResource(R.string.drawer_storage))
                    locations.forEach { location ->
                        DrawerRow(
                            icon = Icons.Filled.Folder,
                            title = location.label,
                            subtitle = displayPath(location.uri),
                        ) {
                            onNavigate(location.uri)
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DrawerSectionTitle(
                            stringResource(R.string.drawer_bookmarks),
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = {
                            if (store.addBookmark(displayNameFor(activeState.path), activeState.path)) {
                                bookmarks = store.bookmarks()
                            }
                        }) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = stringResource(R.string.action_add_bookmark),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (bookmarks.isEmpty()) {
                        DrawerHint(stringResource(R.string.drawer_empty_bookmarks))
                    }
                    bookmarks.forEach { (name, path) ->
                        DrawerRow(
                            icon = Icons.Filled.Folder,
                            title = name,
                            subtitle = displayPath(path),
                            trailing = {
                                IconButton(onClick = {
                                    if (store.removeBookmark(path)) {
                                        bookmarks = store.bookmarks()
                                    }
                                }) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = stringResource(R.string.action_delete),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            },
                        ) {
                            onNavigate(path)
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DrawerSectionTitle(
                            stringResource(R.string.drawer_history),
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = {
                            store.clearHistory()
                            history = emptyList()
                        }) {
                            Icon(
                                Icons.Filled.History,
                                contentDescription = stringResource(R.string.action_clear_history),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    history.take(HISTORY_SHOW_LIMIT).forEach { path ->
                        DrawerRow(
                            icon = Icons.Filled.History,
                            title = displayNameFor(path),
                            subtitle = displayPath(path),
                        ) {
                            onNavigate(path)
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    DrawerRow(
                        icon = Icons.Filled.Settings,
                        title = stringResource(R.string.action_settings),
                        subtitle = null,
                    ) {
                        onOpenSettings()
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        },
        content = content,
    )
}

/** 分区标题 */
@Composable
private fun DrawerSectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/**
 * 快速面板（0.2.0-alpha07）：右下角 FAB 点击/上滑弹出的书签 + 历史记录底部弹层（MT 惯例）。
 *
 * 与侧边抽屉共享 [UiStateStore] 数据；跳转作用于「活动窗口」。
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
public fun NeoQuickSheet(
    activeState: FilePaneState,
    store: UiStateStore,
    onNavigate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var bookmarks by remember { mutableStateOf(store.bookmarks()) }
    var history by remember { mutableStateOf(store.history()) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DrawerSectionTitle(
                    stringResource(R.string.drawer_bookmarks),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = {
                    if (store.addBookmark(displayNameFor(activeState.path), activeState.path)) {
                        bookmarks = store.bookmarks()
                    }
                }) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = stringResource(R.string.action_add_bookmark),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (bookmarks.isEmpty()) {
                DrawerHint(stringResource(R.string.drawer_empty_bookmarks))
            }
            bookmarks.forEach { (name, path) ->
                DrawerRow(
                    icon = Icons.Filled.Star,
                    title = name,
                    subtitle = displayPath(path),
                    trailing = {
                        IconButton(onClick = {
                            if (store.removeBookmark(path)) {
                                bookmarks = store.bookmarks()
                            }
                        }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.action_delete),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    },
                ) {
                    onNavigate(path)
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DrawerSectionTitle(
                    stringResource(R.string.drawer_history),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = {
                    store.clearHistory()
                    history = emptyList()
                }) {
                    Icon(
                        Icons.Filled.History,
                        contentDescription = stringResource(R.string.action_clear_history),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (history.isEmpty()) {
                DrawerHint(stringResource(R.string.drawer_empty_history))
            }
            history.take(HISTORY_SHOW_LIMIT).forEach { path ->
                DrawerRow(
                    icon = Icons.Filled.History,
                    title = displayNameFor(path),
                    subtitle = displayPath(path),
                ) {
                    onNavigate(path)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 弱提示文案（如空书签） */
@Composable
private fun DrawerHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

/**
 * 首次启动手势提示（0.2.0-alpha07，MT 惯例的新手引导）：确认后经 [onAck] 持久化不再弹出。
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
public fun NeoTipsSheet(onAck: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onAck) {
        Column(Modifier.padding(horizontal = 24.dp)) {
            Text(
                text = stringResource(R.string.tips_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            TipRow(stringResource(R.string.tips_quick_sheet))
            TipRow(stringResource(R.string.tips_pull_refresh))
            TipRow(stringResource(R.string.tips_long_press))
            TipRow(stringResource(R.string.tips_path_jump))
            TipRow(stringResource(R.string.tips_drawer))
            TipRow(stringResource(R.string.tips_hidden_files))
            TextButton(
                onClick = onAck,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 24.dp),
            ) {
                Text(stringResource(R.string.tips_got_it))
            }
        }
    }
}

/** 提示单行：主色圆点 + 文案 */
@Composable
private fun TipRow(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(6.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

/** 抽屉单行：图标 + 标题 + 副标题（可选）+ 行尾控件（可选） */
@Composable
private fun DrawerRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
    }
}
