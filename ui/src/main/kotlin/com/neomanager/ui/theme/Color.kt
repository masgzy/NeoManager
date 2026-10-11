/*
 * Copyright (C) 2026 The NeoManager Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.neomanager.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * MT 风格经典色板（0.2.0-alpha05 起）。
 *
 * 视觉基准为 MT 管理器浅色主题：炭黑共享顶栏 + 纯白列表 + 黑字灰副标题 + 蓝色强调；
 * 深色主题为同构映射（纯黑列表 + 亮字），文件夹图标按 MT 惯例浅色炭灰、深色琥珀黄。
 * 固定取色、不随壁纸动态变化，保证与参考观感一致；
 * 首组常量为共享顶栏/状态栏区域的炭黑底与白字。
 */

public val MtChrome = Color(0xFF2B2B2B)
public val MtChromeOn = Color(0xFFFFFFFF)
public val MtChromeOnDim = Color(0xB3FFFFFF)
public val MtChromeUnderline = Color(0xFF6B6B6B)

// 浅色主题
public val MtSurfaceLight = Color(0xFFFFFFFF)
public val MtOnSurfaceLight = Color(0xFF212121)
public val MtOnSurfaceVariantLight = Color(0xFF757575)
public val MtPrimaryLight = Color(0xFF1E7AD4)
public val MtOutlineVariantLight = Color(0xFFDCDCDC)
public val MtSurfaceVariantLight = Color(0xFFF2F2F2)
public val MtRowSelectedLight = Color(0xFFD9E9F9)

// 深色主题
public val MtSurfaceDark = Color(0xFF121212)
public val MtOnSurfaceDark = Color(0xFFE0E0E0)
public val MtOnSurfaceVariantDark = Color(0xFF9E9E9E)
public val MtPrimaryDark = Color(0xFF64B5F6)
public val MtOutlineVariantDark = Color(0xFF333333)
public val MtSurfaceVariantDark = Color(0xFF262626)
public val MtRowSelectedDark = Color(0xFF37474F)

// 文件夹图标（浅色炭灰立体夹 / 深色 MT 经典黄夹）
public val MtFolderLight = Color(0xFF3B3B3B)
public val MtFolderDark = Color(0xFFFFCA28)
public val MtFolderSheet = Color(0xFFF4F4F4)

// 文件类型图标（明暗共用，中饱和度）
public val MtIconApk = Color(0xFF43A047)
public val MtIconArchive = Color(0xFF8D6E63)
public val MtIconImage = Color(0xFF5C6BC0)
public val MtIconAudio = Color(0xFFAB47BC)
public val MtIconVideo = Color(0xFF42A5F5)
public val MtIconText = Color(0xFF78909C)
public val MtIconGeneric = Color(0xFF90A4AE)
