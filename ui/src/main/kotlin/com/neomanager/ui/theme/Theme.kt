/*
 * Copyright (C) 2026 The NeoManager Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.neomanager.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Neo 主题（0.2.0-alpha07 起 Material You）。
 *
 * 取色策略：
 * - Android 12+ 默认壁纸动态取色（dynamicLight/DarkColorScheme），跟随系统个性化；
 * - Android 8-11 回退到以 Neo 蓝为种子的静态 M3 方案（[NeoColorSchemes]）；
 * - 全部界面组件只引用 MaterialTheme.colorScheme 角色色，保证两套来源观感统一；
 * - 文件类型图标保留语义恒定的分类色（[Color.kt]），不随壁纸漂移以便快速辨识。
 */
private object NeoColorSchemes {
    val Light =
        lightColorScheme(
            primary = NeoPrimaryLight,
            onPrimary = NeoOnPrimaryLight,
            primaryContainer = NeoPrimaryContainerLight,
            onPrimaryContainer = NeoOnPrimaryContainerLight,
            secondaryContainer = NeoSecondaryContainerLight,
            onSecondaryContainer = NeoOnSecondaryContainerLight,
            background = NeoSurfaceLight,
            onBackground = NeoOnSurfaceLight,
            surface = NeoSurfaceLight,
            onSurface = NeoOnSurfaceLight,
            onSurfaceVariant = NeoOnSurfaceVariantLight,
            outlineVariant = NeoOutlineVariantLight,
            error = Color(0xFFBA1A1A),
            onError = Color(0xFFFFFFFF),
        )

    val Dark =
        darkColorScheme(
            primary = NeoPrimaryDark,
            onPrimary = NeoOnPrimaryDark,
            primaryContainer = NeoPrimaryContainerDark,
            onPrimaryContainer = NeoOnPrimaryContainerDark,
            secondaryContainer = NeoSecondaryContainerDark,
            onSecondaryContainer = NeoOnSecondaryContainerDark,
            background = NeoSurfaceDark,
            onBackground = NeoOnSurfaceDark,
            surface = NeoSurfaceDark,
            onSurface = NeoOnSurfaceDark,
            onSurfaceVariant = NeoOnSurfaceVariantDark,
            outlineVariant = NeoOutlineVariantDark,
            error = Color(0xFFFFB4AB),
            onError = Color(0xFF690005),
        )
}

/** 是否深色模式（跟随系统；API 12+ 动态取色，低版本回退静态方案） */
@Composable
public fun NeoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            darkTheme -> NeoColorSchemes.Dark
            else -> NeoColorSchemes.Light
        }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content,
    )
}
