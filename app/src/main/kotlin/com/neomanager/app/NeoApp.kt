/*
 * Copyright (C) 2026 The NeoManager Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.neomanager.app

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.neomanager.engine.core.vfs.VfsRegistry
import com.neomanager.ui.dualpane.DualPaneScreen
import com.neomanager.ui.editor.TextEditorScreen
import com.neomanager.ui.engine.EngineBridge
import com.neomanager.ui.engine.LocalEngineBridge
import com.neomanager.ui.settings.SettingsScreen
import com.neomanager.ui.viewer.ImageViewerScreen

/** 导航路由 */
private object Routes {
    const val HOME: String = "home"
    const val SETTINGS: String = "settings"

    /** 编辑器：path 为 URL 编码后的 VfsUri 字符串 */
    const val EDITOR: String = "editor?path={path}"
    const val EDITOR_ARG_PATH: String = "path"

    /** 图片查看：path 为 URL 编码后的 VfsUri 字符串 */
    const val IMAGE: String = "image?path={path}"
}

/**
 * 应用骨架：Navigation 导航 + 各页面自持顶栏。
 *
 * - home：双窗口文件浏览（[DualPaneScreen]），MT 风格全屏自绘顶/底栏，无框架顶栏；
 * - editor：文本编辑（[TextEditorScreen]，VfsUri 经 URL 编码传参）；
 * - settings：设置与关于（[SettingsScreen]）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun NeoApp(
    versionName: String,
    engineBridge: EngineBridge,
    modifier: Modifier = Modifier,
) {
    val navController: NavHostController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    CompositionLocalProvider(LocalEngineBridge provides engineBridge) {
        Scaffold(
            modifier = modifier,
            topBar = {
                when (currentRoute) {
                    Routes.EDITOR, Routes.IMAGE ->
                        TopAppBar(
                            title = { Text(stringResource(R.string.editor_title)) },
                            navigationIcon = {
                                IconButton(onClick = { navController.popBackStack() }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = stringResource(R.string.action_back),
                                    )
                                }
                            },
                        )
                    Routes.SETTINGS ->
                        TopAppBar(
                            title = { Text(stringResource(R.string.app_name)) },
                            navigationIcon = {
                                IconButton(onClick = { navController.popBackStack() }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = stringResource(R.string.action_back),
                                    )
                                }
                            },
                        )
                    else -> Unit // home：MT 风格由 DualPaneScreen 自绘全屏 Chrome
                }
            },
            contentWindowInsets =
                if (currentRoute == Routes.HOME) {
                    WindowInsets(0, 0, 0, 0) // home 全屏绘制，状态栏/导航栏区域由 MT Chrome 着色
                } else {
                    ScaffoldDefaults.contentWindowInsets
                },
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Routes.HOME,
                modifier = Modifier.padding(innerPadding),
            ) {
                composable(Routes.HOME) {
                    DualPaneScreen(
                        onOpenEditor = { path ->
                            val encoded = android.net.Uri.encode(path)
                            navController.navigate("editor?path=$encoded")
                        },
                        onOpenImage = { path ->
                            val encoded = android.net.Uri.encode(path)
                            navController.navigate("image?path=$encoded")
                        },
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    )
                }
                composable(
                    route = Routes.EDITOR,
                    arguments =
                        listOf(
                            navArgument(Routes.EDITOR_ARG_PATH) {
                                type = NavType.StringType
                            },
                        ),
                ) { entry ->
                    val path = entry.arguments?.getString(Routes.EDITOR_ARG_PATH).orEmpty()
                    val bridge = LocalEngineBridge.current
                    val registry = remember(bridge) { VfsRegistry(bridge.elevatedVfs()) }
                    TextEditorScreen(
                        path = android.net.Uri.decode(path),
                        registry = registry,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(versionName = versionName)
                }
                composable(
                    route = Routes.IMAGE,
                    arguments =
                        listOf(
                            navArgument(Routes.EDITOR_ARG_PATH) {
                                type = NavType.StringType
                            },
                        ),
                ) { entry ->
                    val path = entry.arguments?.getString(Routes.EDITOR_ARG_PATH).orEmpty()
                    val bridge = LocalEngineBridge.current
                    val registry = remember(bridge) { VfsRegistry(bridge.elevatedVfs()) }
                    ImageViewerScreen(
                        path = android.net.Uri.decode(path),
                        registry = registry,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}
