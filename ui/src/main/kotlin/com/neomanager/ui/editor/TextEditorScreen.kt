/*
 * Copyright (C) 2026 The NeoManager Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.neomanager.ui.editor

import android.widget.Toast
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import com.neomanager.editor.NeoCodeEditor
import com.neomanager.editor.TextCodec
import com.neomanager.engine.core.vfs.VfsRegistry
import com.neomanager.engine.core.vfs.VfsUri
import com.neomanager.ui.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 大文件阈值：超过则提示降级（观察规格编辑器第 1 节） */
public const val EDITOR_LARGE_FILE_THRESHOLD: Int = 2 * 1024 * 1024

/**
 * 文本编辑器页面（Phase 1 ②）：
 * 打开（VFS 任意位置，含 zip 内只读）→ 编辑 → 保存（多字符集）。
 *
 * @param path 目标文件 VfsUri 字符串
 * @param registry VFS 注册表（app 壳按当前提权后端构建后注入）
 * @param onBack 返回回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun TextEditorScreen(
    path: String,
    registry: VfsRegistry,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uri = remember(path) { VfsUri.parseOrNull(path) }

    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var originalBytes by remember { mutableStateOf<ByteArray?>(null) }
    var codec by remember { mutableStateOf(TextCodec.UTF_8) }
    var editorRef by remember { mutableStateOf<io.github.rosemoe.sora.widget.CodeEditor?>(null) }
    var codecMenuOpen by remember { mutableStateOf(false) }
    var dirty by remember { mutableStateOf(false) }

    val writable = remember(uri) { uri != null && !uri.isRoot }
    val inArchive = remember(uri) { uri != null && uri.scheme == VfsUri.SCHEME_ZIP }

    // 加载
    LaunchedEffect(path) {
        val target = uri
        if (target == null) {
            loadError = "无效路径：$path"
            loading = false
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            try {
                val bytes =
                    registry
                        .resolve(target)
                        .openRead(target)
                        .getOrThrow()
                        .use { it.readBytes() }
                originalBytes = bytes
                codec = TextCodec.sniff(bytes)
            } catch (e: Throwable) {
                loadError = e.message ?: e.javaClass.simpleName
            }
        }
        loading = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    text = uri?.name ?: path,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
            },
            actions = {
                // 字符集选择（观察规格：指定保存的编码）
                TextButtonForCodec(codec) { codecMenuOpen = true }
                DropdownMenu(expanded = codecMenuOpen, onDismissRequest = { codecMenuOpen = false }) {
                    TextCodec.entries.forEach { candidate ->
                        DropdownMenuItem(
                            text = { Text(candidate.displayName) },
                            onClick = {
                                codecMenuOpen = false
                                val bytes = originalBytes
                                if (bytes != null && !dirty) {
                                    // 未编辑时切换编码=按新字符集重解码
                                    codec = candidate
                                } else {
                                    codec = candidate
                                    dirty = true
                                }
                            },
                        )
                    }
                }
                IconButton(
                    onClick = {
                        val target = uri
                        val editor = editorRef
                        if (target == null || editor == null) return@IconButton
                        scope.launch {
                            val result =
                                withContext(Dispatchers.IO) {
                                    runCatching {
                                        val text = editor.text.toString()
                                        val bytes = codec.encode(text)
                                        registry.resolve(target).writeFile(target, bytes.inputStream(), append = false)
                                    }
                                }
                            result
                                .onSuccess {
                                    dirty = false
                                    Toast
                                        .makeText(
                                            context,
                                            context.getString(R.string.editor_saved),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                }.onFailure { e ->
                                    Toast
                                        .makeText(
                                            context,
                                            context.getString(R.string.editor_save_failed, e.message ?: ""),
                                            Toast.LENGTH_LONG,
                                        ).show()
                                }
                        }
                    },
                    enabled = writable && originalBytes != null,
                ) {
                    Icon(Icons.Filled.Save, contentDescription = stringResource(R.string.editor_save))
                }
            },
        )

        when {
            loading -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            loadError != null -> {
                Text(
                    text = stringResource(R.string.load_failed) + "\n" + loadError.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(24.dp),
                )
            }
            else -> {
                val bytes = originalBytes
                if (bytes != null && bytes.size > EDITOR_LARGE_FILE_THRESHOLD) {
                    Text(
                        text = stringResource(R.string.editor_large_file),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                NeoCodeEditor(
                    initialText = bytes?.let { codec.decode(it) } ?: "",
                    readOnly = !writable || inArchive,
                    darkTheme = isSystemInDarkTheme(),
                    modifier = Modifier.fillMaxSize(),
                    onReady = { editorRef = it },
                )
                if (inArchive) {
                    Text(
                        text = stringResource(R.string.editor_readonly_archive),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TextButtonForCodec(
    codec: TextCodec,
    onClick: () -> Unit,
) {
    androidx.compose.material3.TextButton(onClick = onClick) {
        Text(codec.displayName)
    }
}
