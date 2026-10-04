/*
 * Copyright (C) 2026 The NeoManager Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.neomanager.app

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.neomanager.ui.dualpane.DualPaneScreen
import com.neomanager.ui.settings.SettingsScreen

/** 导航路由 */
private object Routes {
    const val HOME: String = "home"
    const val SETTINGS: String = "settings"
}

/**
 * 应用骨架：顶栏 + Navigation 导航。
 *
 * - home：双窗口文件浏览（[DualPaneScreen]）
 * - settings：设置与关于（[SettingsScreen]）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun NeoApp(
    versionName: String,
    modifier: Modifier = Modifier,
) {
    val navController: NavHostController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        modifier = modifier,
        topBar = {
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
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.HOME) {
                DualPaneScreen()
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(versionName = versionName)
            }
        }
    }
}
