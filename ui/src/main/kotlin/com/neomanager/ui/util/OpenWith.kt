/*
 * Copyright (C) 2026 The NeoManager Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.neomanager.ui.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.neomanager.engine.core.vfs.VfsUri
import java.io.File

/**
 * 「用系统应用打开」工具（观察规格 2026-10-10-file-manager.md 打开方式一节）：
 *
 * - 仅本地路径（file://）可直接分享；zip/apk 内部条目需先解压（VFS 无物理文件）。
 * - MIME 按扩展名推断，覆盖音视频/图片/文档/APK 等常用类型，未知用 octet-stream。
 * - FileProvider authority 约定为 `${applicationId}.fileprovider`（见 app 清单）。
 */
public object OpenWith {
    /** 压缩包内条目无法直接交给外部应用 */
    public fun isLocalPath(path: String): Boolean = VfsUri.parseOrNull(path)?.isLocal == true

    /**
     * 调用系统应用打开 [path]（本地路径）。
     *
     * @return 成功返回 Success；路径非本地、无关联应用等失败返回 Failure（含人类可读消息）
     */
    public fun openWithSystem(
        context: Context,
        path: String,
    ): Result<Unit> {
        val uri = VfsUri.parseOrNull(path) ?: return Result.failure(IllegalArgumentException("无效路径"))
        if (!uri.isLocal) {
            return Result.failure(IllegalStateException("压缩包内文件请先解压后打开"))
        }
        val file = File(uri.localPath)
        if (!file.exists()) return Result.failure(IllegalStateException("文件不存在或已被删除"))
        return try {
            val shared: Uri =
                FileProvider.getUriForFile(
                    context,
                    context.packageName + ".fileprovider",
                    file,
                )
            val intent =
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(shared, mimeFor(file.name))
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            context.startActivity(intent)
            Result.success(Unit)
        } catch (e: ActivityNotFoundException) {
            Result.failure(IllegalStateException("未找到可处理此类型的应用"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 按扩展名推断 MIME 类型（常用类型速查；不区分大小写） */
    public fun mimeFor(name: String): String {
        val lower = name.lowercase()
        return when {
            // 音频
            lower.endsWith(".mp3") -> "audio/mpeg"
            lower.endsWith(".flac") -> "audio/flac"
            lower.endsWith(".wav") -> "audio/x-wav"
            lower.endsWith(".ogg") || lower.endsWith(".opus") -> "audio/ogg"
            lower.endsWith(".m4a") -> "audio/mp4"
            lower.endsWith(".aac") -> "audio/aac"
            lower.endsWith(".mid") || lower.endsWith(".midi") -> "audio/midi"
            // 视频
            lower.endsWith(".mp4") -> "video/mp4"
            lower.endsWith(".mkv") -> "video/x-matroska"
            lower.endsWith(".avi") -> "video/x-msvideo"
            lower.endsWith(".mov") -> "video/quicktime"
            lower.endsWith(".webm") -> "video/webm"
            lower.endsWith(".3gp") -> "video/3gpp"
            // 图片
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
            lower.endsWith(".png") -> "image/png"
            lower.endsWith(".gif") -> "image/gif"
            lower.endsWith(".webp") -> "image/webp"
            lower.endsWith(".bmp") -> "image/bmp"
            lower.endsWith(".svg") -> "image/svg+xml"
            // 文档
            lower.endsWith(".pdf") -> "application/pdf"
            lower.endsWith(".doc") -> "application/msword"
            lower.endsWith(".docx") -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            lower.endsWith(".xls") -> "application/vnd.ms-excel"
            lower.endsWith(".xlsx") -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            lower.endsWith(".ppt") -> "application/vnd.ms-powerpoint"
            lower.endsWith(".pptx") -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            lower.endsWith(".epub") -> "application/epub+zip"
            // 压缩包（外部解压类应用）
            lower.endsWith(".zip") -> "application/zip"
            lower.endsWith(".rar") -> "application/vnd.rar"
            lower.endsWith(".7z") -> "application/x-7z-compressed"
            // Android
            lower.endsWith(".apk") -> "application/vnd.android.package-archive"
            else -> "application/octet-stream"
        }
    }
}
