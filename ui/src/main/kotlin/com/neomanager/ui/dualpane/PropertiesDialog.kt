/*
 * Copyright (C) 2026 The NeoManager Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.neomanager.ui.dualpane

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.neomanager.engine.core.vfs.VfsRegistry
import com.neomanager.engine.core.vfs.VfsUri
import com.neomanager.ui.R
import com.neomanager.ui.file.FileEntry
import com.neomanager.ui.util.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.zip.CRC32

/**
 * 属性对话框（观察规格 2026-10-10-file-manager.md 第 4 节）：
 * 基本信息 + 权限串 + 目录子项数 + 校验和页（MD5 / SHA1 / SHA256 / CRC32），计算在后台线程；
 * 校验和点击复制（0.2.0-alpha08）。
 */
@Composable
public fun PropertiesDialog(
    entry: FileEntry,
    registry: VfsRegistry,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var checksum by remember(entry.path) { mutableStateOf<ChecksumResult?>(null) }
    var computing by remember(entry.path) { mutableStateOf(false) }
    var childCount by remember(entry.path) { mutableIntStateOf(-1) }

    LaunchedEffect(entry.path) {
        val uri = VfsUri.parseOrNull(entry.path) ?: return@LaunchedEffect
        if (entry.isDirectory) {
            childCount =
                withContext(Dispatchers.IO) {
                    runCatching {
                        registry
                            .resolve(uri)
                            .list(uri)
                            .getOrDefault(emptyList())
                            .size
                    }.getOrDefault(-1)
                }
            return@LaunchedEffect
        }
        computing = true
        checksum =
            withContext(Dispatchers.IO) {
                runCatching {
                    val bytes =
                        registry
                            .resolve(uri)
                            .openRead(uri)
                            .getOrThrow()
                            .use { it.readBytes() }
                    ChecksumResult(
                        md5 = digest("MD5", bytes),
                        sha1 = digest("SHA-1", bytes),
                        sha256 = digest("SHA-256", bytes),
                        crc32 = crc32(bytes),
                    )
                }.getOrNull()
            }
        computing = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.name, maxLines = 2) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                PropRow(stringResource(R.string.prop_type), if (entry.isDirectory) "目录" else "文件")
                entry.permissions?.let { PropRow(stringResource(R.string.prop_permissions), it, monospace = true) }
                if (entry.isDirectory && childCount >= 0) {
                    PropRow(stringResource(R.string.prop_children), childCount.toString())
                }
                if (!entry.isDirectory) {
                    val sizeText = Format.fileSize(entry.sizeBytes) + " (${entry.sizeBytes} B)"
                    PropRow(stringResource(R.string.prop_size), sizeText)
                }
                if (entry.lastModified > 0) {
                    PropRow(stringResource(R.string.prop_modified), Format.modifiedTime(entry.lastModified))
                }
                PropRow(stringResource(R.string.prop_path), entry.path, monospace = true)
                when {
                    computing -> {
                        Row(
                            modifier = Modifier.padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CircularProgressIndicator(modifier = Modifier.padding(0.dp))
                            Text(stringResource(R.string.prop_computing))
                        }
                    }
                    checksum != null -> {
                        Text(
                            text = stringResource(R.string.prop_checksum),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        ChecksumRow(context, "MD5", checksum!!.md5)
                        ChecksumRow(context, "SHA-1", checksum!!.sha1)
                        ChecksumRow(context, "SHA-256", checksum!!.sha256)
                        ChecksumRow(context, "CRC32", checksum!!.crc32)
                    }
                    else ->
                        Text(
                            text = stringResource(R.string.prop_checksum_failed),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) }
        },
    )
}

private data class ChecksumResult(
    val md5: String,
    val sha1: String,
    val sha256: String,
    val crc32: String,
)

/** 校验和单行：点击复制到系统剪贴板（0.2.0-alpha08） */
@Composable
private fun ChecksumRow(
    context: Context,
    label: String,
    value: String,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 12.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            maxLines = 1,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
                        Toast.makeText(context, context.getString(R.string.prop_copied), Toast.LENGTH_SHORT).show()
                    },
        )
    }
}

@Composable
private fun PropRow(
    label: String,
    value: String,
    monospace: Boolean = false,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 12.dp),
        )
        Text(
            text = value,
            style =
                MaterialTheme.typography.bodySmall.copy(
                    fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
                ),
            maxLines = if (monospace) 1 else 3,
        )
    }
}

private fun digest(
    algorithm: String,
    bytes: ByteArray,
): String = MessageDigest.getInstance(algorithm).digest(bytes).joinToString("") { "%02x".format(it) }

private fun crc32(bytes: ByteArray): String {
    val crc = CRC32()
    crc.update(bytes)
    return "%08x".format(crc.value)
}
