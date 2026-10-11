/*
 * Copyright (C) 2026 The NeoManager Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.neomanager.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Neo 配色（0.2.0-alpha07 起 Material You）。
 *
 * - Android 12+ 默认走壁纸动态取色（[Theme.kt]），本文件仅提供两组职责：
 *   1. API < 31 的静态回退方案（以 Neo 蓝 #0061A4 为种子的标准 M3 色调映射）；
 *   2. 文件类型图标的分类色（语义恒定，不随壁纸漂移，与 Google Files 惯例一致）。
 * - 结构性界面（顶栏/底栏/列表/对话框/抽屉）一律引用 MaterialTheme.colorScheme，
 *   不得再引用本文件的固定色，保证动态取色下整体观感统一。
 */

public val NeoPrimaryLight: Color = Color(0xFF0061A4)
public val NeoOnPrimaryLight: Color = Color(0xFFFFFFFF)
public val NeoPrimaryContainerLight: Color = Color(0xFFD1E4FF)
public val NeoOnPrimaryContainerLight: Color = Color(0xFF001D36)
public val NeoSecondaryContainerLight: Color = Color(0xFFD7E3F7)
public val NeoOnSecondaryContainerLight: Color = Color(0xFF101C2B)
public val NeoSurfaceLight: Color = Color(0xFFF8F9FF)
public val NeoOnSurfaceLight: Color = Color(0xFF191C20)
public val NeoOnSurfaceVariantLight: Color = Color(0xFF43474E)
public val NeoOutlineVariantLight: Color = Color(0xFFC3C7CF)

public val NeoPrimaryDark: Color = Color(0xFFA0CAFD)
public val NeoOnPrimaryDark: Color = Color(0xFF003259)
public val NeoPrimaryContainerDark: Color = Color(0xFF00497D)
public val NeoOnPrimaryContainerDark: Color = Color(0xFFD1E4FF)
public val NeoSecondaryContainerDark: Color = Color(0xFF37465A)
public val NeoOnSecondaryContainerDark: Color = Color(0xFFD7E3F7)
public val NeoSurfaceDark: Color = Color(0xFF111418)
public val NeoOnSurfaceDark: Color = Color(0xFFE1E2E8)
public val NeoOnSurfaceVariantDark: Color = Color(0xFFC3C7CF)
public val NeoOutlineVariantDark: Color = Color(0xFF43474E)

public val NeoIconApk: Color = Color(0xFF43A047)
public val NeoIconArchive: Color = Color(0xFF8D6E63)
public val NeoIconImage: Color = Color(0xFF5C6BC0)
public val NeoIconAudio: Color = Color(0xFFAB47BC)
public val NeoIconVideo: Color = Color(0xFF42A5F5)
public val NeoIconText: Color = Color(0xFF78909C)
public val NeoIconGeneric: Color = Color(0xFF90A4AE)
