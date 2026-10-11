/*
 * Copyright (C) 2026 The NeoManager Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.neomanager.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Neo 主题（MT 风格固定取色，0.2.0-alpha05 起）。
 *
 * 取色策略：
 * - 不再使用 Material You 动态取色（壁纸取色会让界面偏离 MT 参考观感）；
 * - 浅色：白底黑字 + 炭黑顶栏 + 蓝色强调；深色：纯黑列表 + 亮字，同构映射。
 * 文件夹/文件图标的专属色（[MtFolderLight]/[MtFolderDark] 等）由列表组件直接引用。
 */
private object MtColorSchemes {
    val Light =
        lightColorScheme(
            primary = MtPrimaryLight,
            onPrimary = Color(0xFFFFFFFF),
            secondary = MtPrimaryLight,
            background = MtSurfaceLight,
            onBackground = MtOnSurfaceLight,
            surface = MtSurfaceLight,
            onSurface = MtOnSurfaceLight,
            surfaceVariant = MtSurfaceVariantLight,
            onSurfaceVariant = MtOnSurfaceVariantLight,
            outline = MtOnSurfaceVariantLight,
            outlineVariant = MtOutlineVariantLight,
            error = Color(0xFFBA1A1A),
            onError = Color(0xFFFFFFFF),
        )

    val Dark =
        darkColorScheme(
            primary = MtPrimaryDark,
            onPrimary = Color(0xFF0B2A44),
            secondary = MtPrimaryDark,
            background = MtSurfaceDark,
            onBackground = MtOnSurfaceDark,
            surface = MtSurfaceDark,
            onSurface = MtOnSurfaceDark,
            surfaceVariant = MtSurfaceVariantDark,
            onSurfaceVariant = MtOnSurfaceVariantDark,
            outline = MtOnSurfaceVariantDark,
            outlineVariant = MtOutlineVariantDark,
            error = Color(0xFFFFB4AB),
            onError = Color(0xFF690005),
        )
}

/** 是否深色模式（跟随系统；MT 风格两套色板见 [MtColorSchemes]） */
@Composable
public fun NeoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) MtColorSchemes.Dark else MtColorSchemes.Light,
        typography = Typography(),
        content = content,
    )
}
