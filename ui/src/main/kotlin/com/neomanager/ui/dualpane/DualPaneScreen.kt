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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.neomanager.ui.R
import com.neomanager.ui.file.FilePane
import com.neomanager.ui.file.FilePaneState
import com.neomanager.ui.file.JavaIoFileBrowser

/**
 * 双窗口主屏：权限横幅 + [DualPaneLayout] 承载左右两列 [FilePane]。
 *
 * 存储权限策略（全本地化承诺，不申请 INTERNET）：
 * - Android 11+：引导用户授予「所有文件访问」（MANAGE_EXTERNAL_STORAGE），
 *   未授予时顶部横幅提示，返回前台时自动复查；
 * - Android 8-10：首启请求传统 READ/WRITE_EXTERNAL_STORAGE 运行时权限。
 */
@Composable
public fun DualPaneScreen(modifier: Modifier = Modifier) {
    val browser = remember { JavaIoFileBrowser() }
    val context = LocalContext.current

    val startPath =
        remember {
            @Suppress("DEPRECATION")
            Environment.getExternalStorageDirectory()?.absolutePath ?: FilePaneState.ROOT_PATH
        }

    val leftState = remember { FilePaneState(startPath, browser) }
    val rightState = remember { FilePaneState(startPath, browser) }

    // 组合期副作用：路径/刷新变化驱动加载
    leftState.Effect()
    rightState.Effect()

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

    // 「所有文件访问」状态：前台恢复时复查（用户可能刚从系统设置页返回）
    var hasAllFilesAccess by remember {
        mutableStateOf(checkAllFilesAccess())
    }
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

    Column(modifier = modifier) {
        if (!hasAllFilesAccess) {
            AllFilesAccessBanner(
                packageName = context.packageName,
                onResolved = { hasAllFilesAccess = checkAllFilesAccess() },
            )
        }
        DualPaneLayout(
            first = { FilePane(state = leftState) },
            second = { FilePane(state = rightState) },
        )
    }
}

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
