/*
 * Copyright (C) 2026 The NeoManager Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.neomanager.ui.dualpane

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.neomanager.ui.R

/** 分隔条触控区域厚度（触摸目标 ≥ 24dp，满足可达性要求） */
private val DividerTouchThickness = 24.dp

/** 分隔条可见线条厚度 */
private val DividerLineThickness = 2.dp

/** 首栏占比下限 */
private const val MIN_FRACTION = 0.2f

/** 首栏占比上限 */
private const val MAX_FRACTION = 0.8f

/**
 * 双窗口布局容器（Neo 双栏骨架）。
 *
 * 无论横屏竖屏均为左右两列，
 * 竖屏下每列是窄幅纵向文件列表（而非上下堆叠的面板）。
 *
 * - 分隔条可拖拽调节占比（20%-80%），双击复位居中；
 * - 占比经 [rememberSaveable] 持久化，旋转/进程恢复后保持。
 *
 * @param first 第一窗格内容
 * @param second 第二窗格内容
 */
@Composable
public fun DualPaneLayout(
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    var fraction by rememberSaveable { mutableFloatStateOf(0.5f) }
    var containerWidth by remember { mutableIntStateOf(1) }
    var isDragging by remember { mutableStateOf(false) }

    val dividerColor: Color =
        if (isDragging) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant
        }

    val dividerDescription = stringResource(R.string.dual_pane_divider)

    val dividerModifier =
        Modifier
            .semantics { contentDescription = dividerDescription }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { isDragging = true },
                    onDragEnd = { isDragging = false },
                    onDragCancel = { isDragging = false },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (containerWidth > 0) {
                            fraction =
                                (fraction + dragAmount.x / containerWidth)
                                    .coerceIn(MIN_FRACTION, MAX_FRACTION)
                        }
                    },
                )
            }.pointerInput(Unit) {
                // 双击分隔条复位为居中
                detectTapGestures(onDoubleTap = { fraction = 0.5f })
            }

    Row(
        modifier =
            modifier
                .fillMaxSize()
                .onSizeChanged { containerWidth = it.width },
    ) {
        Box(Modifier.weight(fraction)) { first() }
        DividerBody(
            modifier = dividerModifier,
            lineColor = dividerColor,
        )
        Box(Modifier.weight(1f - fraction)) { second() }
    }
}

/**
 * 分隔条本体：透明触控区域内嵌可见竖线。
 * 触控区加宽便于手指拖拽，线条居中显示。
 */
@Composable
private fun DividerBody(
    modifier: Modifier,
    lineColor: Color,
) {
    Box(
        modifier = modifier.width(DividerTouchThickness),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .width(DividerLineThickness)
                .background(lineColor),
        )
    }
}
