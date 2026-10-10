/*
 * Copyright (C) 2026 The NeoManager Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.neomanager.app

import android.content.Context
import android.content.Intent
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 本地崩溃日志（0.2.0-alpha03 引入）。
 *
 * alpha02 在部分真机上出现闪退且用户无法提供 logcat，故内置崩溃捕获：
 * - 全局未捕获异常 → 写入应用私有外部目录 `crash/last_crash.txt`（无需任何存储权限）；
 * - 写入完成后回调系统默认处理器，保留系统崩溃对话框流程不变；
 * - 下次启动由 [MainActivity] 读取并弹窗，用户可选择经系统分享面板导出日志；
 *
 * 隐私红线：全程不联网、不上传；日志仅含堆栈与设备型号，写入应用自身私有目录。
 */
public object CrashReport {
    private const val DIR_NAME: String = "crash"
    private const val FILE_NAME: String = "last_crash.txt"
    private val TIME_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    /** 应用启动时安装全局崩溃捕获 */
    public fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { write(context.applicationContext, thread, throwable) }
            // 链回系统默认处理：保留崩溃对话框/自动重启等系统行为
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** 读取上次崩溃日志；无则返回 null */
    public fun pending(context: Context): String? =
        runCatching { crashFile(context)?.takeIf { it.isFile && it.length() > 0 }?.readText() }.getOrNull()

    /** 清除已处理的崩溃日志 */
    public fun clear(context: Context) {
        runCatching { crashFile(context)?.delete() }
    }

    /** 构造系统分享意图（用户自选分享目标，应用不做任何网络行为） */
    public fun shareIntent(text: String): Intent =
        Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, "NeoManager 崩溃日志")
            .putExtra(Intent.EXTRA_TEXT, text)

    private fun crashFile(context: Context): File? {
        val base = context.getExternalFilesDir(null) ?: context.filesDir ?: return null
        return File(File(base, DIR_NAME), FILE_NAME)
    }

    private fun write(
        context: Context,
        thread: Thread,
        throwable: Throwable,
    ) {
        val file = crashFile(context) ?: return
        file.parentFile?.mkdirs()
        val stack = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
        val report =
            buildString {
                appendLine("time: ${TIME_FORMAT.format(Date())}")
                appendLine("thread: ${thread.name}")
                appendLine("device: ${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                appendLine("app: ${appVersion(context)}")
                appendLine()
                append(stack)
            }
        file.writeText(report)
    }

    private fun appVersion(context: Context): String =
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            "${info.packageName} ${info.versionName}"
        }.getOrDefault(context.packageName)
}
