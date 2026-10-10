/*
 * Copyright (C) 2026 The NeoManager Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.neomanager.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.neomanager.engine.android.EngineAndroid
import com.neomanager.engine.core.vfs.Vfs
import com.neomanager.ui.engine.EngineBridge
import com.neomanager.ui.theme.NeoTheme

/**
 * 单 Activity 入口（蓝图 5 决策）。
 *
 * 全部 UI 由 Jetpack Compose 承载，页面间导航走 Compose Navigation（[NeoApp]）；
 * 平台引擎经 [EngineBridge] 接口注入（UI 不感知 engine-android 实现类）。
 */
public class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NeoTheme {
                val bridge =
                    object : EngineBridge {
                        override fun isRootAvailable(): Boolean = EngineAndroid.isRootAvailable()

                        override fun isShizukuRunning(): Boolean = EngineAndroid.isShizukuRunning()

                        override fun isShizukuGranted(): Boolean = EngineAndroid.isShizukuGranted()

                        override fun requestShizukuPermission() = EngineAndroid.requestShizukuPermission()

                        override suspend fun bindShizukuService(): Result<Unit> =
                            EngineAndroid.bindShizukuService().map { }

                        override fun elevatedVfs(): Vfs? = EngineAndroid.elevatedVfs()
                    }
                NeoApp(
                    versionName = BuildConfig.VERSION_NAME,
                    engineBridge = bridge,
                )
                LastCrashDialog()
            }
        }
    }
}

/**
 * 上次崩溃日志弹窗（0.2.0-alpha03）：检测到未处理的崩溃日志时提示用户
 * 经系统分享面板导出（应用自身不联网）。用户选择后即清除本地日志文件。
 */
@Composable
private fun LastCrashDialog() {
    val context = LocalContext.current
    var crashText by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { crashText = CrashReport.pending(context) }
    val text = crashText ?: return
    AlertDialog(
        onDismissRequest = {
            CrashReport.clear(context)
            crashText = null
        },
        title = { Text(stringResource(R.string.crash_dialog_title)) },
        text = {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    runCatching {
                        context.startActivity(Intent.createChooser(CrashReport.shareIntent(text), null))
                    }
                    CrashReport.clear(context)
                    crashText = null
                },
            ) { Text(stringResource(R.string.crash_dialog_share)) }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    CrashReport.clear(context)
                    crashText = null
                },
            ) { Text(stringResource(R.string.crash_dialog_dismiss)) }
        },
    )
}
