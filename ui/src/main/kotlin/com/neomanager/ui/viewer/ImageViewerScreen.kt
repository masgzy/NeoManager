/*
 * Copyright (C) 2026 The NeoManager Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.neomanager.ui.viewer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.neomanager.engine.core.vfs.VfsRegistry
import com.neomanager.engine.core.vfs.VfsUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

/**
 * 内置图片查看页（观察规格：打开方式之查看图片）。
 *
 * - 任意 VFS 位置（本地 + 压缩包内部）均可查看；
 * - 大图降采样解码（最长边 2048px），避免 OOM；
 * - 支持双指缩放与拖动（transformable）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun ImageViewerScreen(
    path: String,
    registry: VfsRegistry,
    onBack: () -> Unit,
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    // 解码：按目标尺寸降采样（zip 内条目同样走 VFS 流）
    LaunchedEffect(path) {
        error = null
        withContext(Dispatchers.IO) {
            try {
                val uri = VfsUri.parse(path)
                registry
                    .resolve(uri)
                    .openRead(uri)
                    .getOrThrow()
                    .use { input ->
                        bitmap = decodeSampled(input)
                    }
            } catch (e: Throwable) {
                error = e.message ?: e.javaClass.simpleName
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    text = VfsUri.parseOrNull(path)?.name ?: path,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                }
            },
        )

        val image = bitmap
        when {
            error != null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "无法解码图片\n" + error.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
            image == null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            else -> {
                var scale by remember { mutableStateOf(1f) }
                var offset by remember { mutableStateOf(Offset.Zero) }
                val state =
                    rememberTransformableState { zoomChange, panChange, _ ->
                        scale = (scale * zoomChange).coerceIn(0.5f, 8f)
                        offset += panChange
                    }
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        bitmap = image.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offset.x,
                                    translationY = offset.y,
                                ).transformable(state),
                    )
                }
            }
        }
    }
}

/** 降采样解码：最长边不超过 2048px；解码失败返回 null */
private fun decodeSampled(input: InputStream): Bitmap? {
    val maxDimenPx = 2048
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    input.mark(Int.MAX_VALUE)
    BitmapFactory.decodeStream(input, null, bounds)
    input.reset()
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= maxDimenPx || bounds.outHeight / (sample * 2) >= maxDimenPx) {
        sample *= 2
    }
    val options =
        BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
    return BitmapFactory.decodeStream(input, null, options)
}
