/*
 * Copyright (C) 2026 The NeoManager Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.neomanager.editor

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

/**
 * sora-editor 的 Compose 互操作封装（蓝图 5 决策：View 体系经 `AndroidView` 承载）。
 *
 * 职责边界：仅承载编辑器组件与基础观感配置（行号/自动换行/字号/配色跟随深浅色），
 * 语法高亮 grammar、大文件降级、编码转换由调用方与 engine 层负责。
 *
 * @param initialText 初始文本（重组时【不会】覆盖用户输入）
 * @param readOnly 只读模式（压缩包内浏览默认只读）
 * @param darkTheme 深色模式（切换编辑器配色方案）
 * @param onReady 编辑器实例就绪回调（调用方持有引用以读取/保存文本）
 */
@Composable
public fun NeoCodeEditor(
    initialText: String,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    darkTheme: Boolean = false,
    onReady: (CodeEditor) -> Unit = {},
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            CodeEditor(context).apply {
                setText(initialText)
                isLineNumberEnabled = true
                isWordwrap = true
                isEditable = !readOnly
                applyNeoScheme(this, darkTheme)
                onReady(this)
            }
        },
        update = { editor ->
            editor.isEditable = !readOnly
            applyNeoScheme(editor, darkTheme)
        },
    )
}

private fun applyNeoScheme(
    editor: CodeEditor,
    darkTheme: Boolean,
) {
    val scheme = editor.colorScheme
    if (darkTheme) {
        scheme.setColor(EditorColorScheme.WHOLE_BACKGROUND, 0xFF10141A.toInt())
        scheme.setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, 0xFF10141A.toInt())
        scheme.setColor(EditorColorScheme.CURRENT_LINE, 0xFF1A2028.toInt())
    } else {
        scheme.setColor(EditorColorScheme.WHOLE_BACKGROUND, 0xFFFAFAFA.toInt())
        scheme.setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, 0xFFFAFAFA.toInt())
        scheme.setColor(EditorColorScheme.CURRENT_LINE, 0xFFEFF2F6.toInt())
    }
}
