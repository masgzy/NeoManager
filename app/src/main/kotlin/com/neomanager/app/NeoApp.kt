/*
 * Copyright (C) 2026 The NeoManager Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.neomanager.app

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
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

/** 导航路由 */
private object Routes {
    const val HOME: String = "home"
    const val SETTINGS: String = "settings"

    /** 编辑器：path 为 URL 编码后的 VfsUri 字符串 */
    const val EDITOR: String = "editor?path={path}"
    const val EDITOR_ARG_PATH: String = "path"
}

/**
 * 应用骨架：顶栏 + Navigation 导航。
 *
 * - home：双窗口文件浏览（[DualPaneScreen]）
 * - editor：文本编辑（[TextEditorScreen]，VfsUri 经 URL 编码传参）
 * - settings：设置与关于（[SettingsScreen]）
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

    // 编辑器页面使用独立顶栏，主框架顶栏仅用于 home/settings
    val showTopBar = currentRoute != Routes.EDITOR

    CompositionLocalProvider(LocalEngineBridge provides engineBridge) {
        Scaffold(
            modifier = modifier,
            topBar = {
                if (showTopBar) {
                    TopAppBar(
                        title = { Text(stringResource(R.string.app_name)) },
                        actions = {
                            if (currentRoute != Routes.SETTINGS) {
                                IconButton(onClick = { navController.navigate(Routes.SETTINGS) }) {
                                    Icon(
                                        imageVector = Icons.Filled.Settings,
                                        contentDescription = stringResource(R.string.action_settings),
                                    )
                                }
                            }
                        },
                    )
                } else {
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
                }
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
            }
        }
    }
}
