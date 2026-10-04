/*
 * Copyright (C) 2026 The NeoManager Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.neomanager.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.neomanager.ui.R

/** 「关于」分组的静态信息条目 */
private data class AboutItem(
    val labelRes: Int,
    val value: String,
    val linkUrl: String? = null,
)

/**
 * 设置页（Phase 1 骨架版）。
 *
 * 当前提供「关于」分组（版本 / 许可 / 仓库地址）；
 * 主题模式、排序、编辑器配置等真实设置项随各阶段功能逐步补充。
 *
 * @param versionName 应用版本号（由 app 模块注入）
 */
@Composable
public fun SettingsScreen(
    versionName: String,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val repoUrl = stringResource(R.string.settings_repo_url)
    val aboutItems =
        listOf(
            AboutItem(R.string.settings_version, versionName),
            AboutItem(R.string.settings_license, stringResource(R.string.settings_license_value)),
            AboutItem(R.string.settings_repo, repoUrl, linkUrl = repoUrl),
        )

    LazyColumn(modifier = modifier.fillMaxSize()) {
        item {
            SettingsHeader(stringResource(R.string.settings_about))
        }
        items(aboutItems) { item ->
            val rowModifier =
                if (item.linkUrl != null) {
                    Modifier.clickable { uriHandler.openUri(item.linkUrl) }
                } else {
                    Modifier
                }
            Row(
                modifier =
                    rowModifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(item.labelRes),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = item.value,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            )
        }
        item {
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.settings_privacy_title),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.settings_privacy_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SettingsHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}
