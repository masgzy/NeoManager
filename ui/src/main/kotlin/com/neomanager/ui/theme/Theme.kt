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
import androidx.compose.ui.platform.LocalContext

/**
 * Neo 主题（Material Design 3）。
 *
 * 取色策略（蓝图 5 决策）：
 * - Android 12+（API 31）：Material You 动态取色，跟随系统壁纸；
 * - Android 8-11（API 26-30）与 dynamicColor = false 时：使用 [BrandColorSchemes] 品牌色回退。
 * 两条分支均为必做路径，缺一不可（验收项）。
 */
private object BrandColorSchemes {
    val Light =
        lightColorScheme(
            primary = BrandPrimaryLight,
            onPrimary = BrandOnPrimaryLight,
            primaryContainer = BrandPrimaryContainerLight,
            onPrimaryContainer = BrandOnPrimaryContainerLight,
            secondary = BrandSecondaryLight,
            onSecondary = BrandOnSecondaryLight,
            secondaryContainer = BrandSecondaryContainerLight,
            onSecondaryContainer = BrandOnSecondaryContainerLight,
            tertiary = BrandTertiaryLight,
            onTertiary = BrandOnTertiaryLight,
            tertiaryContainer = BrandTertiaryContainerLight,
            onTertiaryContainer = BrandOnTertiaryContainerLight,
            error = BrandErrorLight,
            onError = BrandOnErrorLight,
            errorContainer = BrandErrorContainerLight,
            onErrorContainer = BrandOnErrorContainerLight,
            background = BrandBackgroundLight,
            onBackground = BrandOnBackgroundLight,
            surface = BrandSurfaceLight,
            onSurface = BrandOnSurfaceLight,
            surfaceVariant = BrandSurfaceVariantLight,
            onSurfaceVariant = BrandOnSurfaceVariantLight,
            outline = BrandOutlineLight,
        )

    val Dark =
        darkColorScheme(
            primary = BrandPrimaryDark,
            onPrimary = BrandOnPrimaryDark,
            primaryContainer = BrandPrimaryContainerDark,
            onPrimaryContainer = BrandOnPrimaryContainerDark,
            secondary = BrandSecondaryDark,
            onSecondary = BrandOnSecondaryDark,
            secondaryContainer = BrandSecondaryContainerDark,
            onSecondaryContainer = BrandOnSecondaryContainerDark,
            tertiary = BrandTertiaryDark,
            onTertiary = BrandOnTertiaryDark,
            tertiaryContainer = BrandTertiaryContainerDark,
            onTertiaryContainer = BrandOnTertiaryContainerDark,
            error = BrandErrorDark,
            onError = BrandOnErrorDark,
            errorContainer = BrandErrorContainerDark,
            onErrorContainer = BrandOnErrorContainerDark,
            background = BrandBackgroundDark,
            onBackground = BrandOnBackgroundDark,
            surface = BrandSurfaceDark,
            onSurface = BrandOnSurfaceDark,
            surfaceVariant = BrandSurfaceVariantDark,
            onSurfaceVariant = BrandOnSurfaceVariantDark,
            outline = BrandOutlineDark,
        )
}

/**
 * 应用根主题。
 *
 * @param darkTheme 是否深色模式，默认跟随系统
 * @param dynamicColor 是否启用 Android 12+ 动态取色，默认开启
 */
@Composable
public fun NeoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            darkTheme -> BrandColorSchemes.Dark
            else -> BrandColorSchemes.Light
        }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content,
    )
}
