/*
 * Copyright (C) 2026 The NeoManager Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.neomanager.editor

/**
 * editor 模块元信息。
 *
 * sora-editor 已接入（Phase 1 ②）：[NeoCodeEditor] 提供 Compose 互操作封装，
 * [TextCodec] 提供多字符集读写。本模块仅封装编辑器组件与配置，
 * 不承载任何业务编辑逻辑；编辑行为的正确性由调用方（ui / app）与引擎层保证。
 */
public object EditorBindings {
    /** sora-editor 依赖坐标（展示在「关于」页） */
    public const val SORA_EDITOR_ARTIFACT: String = "io.github.Rosemoe.sora-editor:editor"

    /** 已内置的字符集数量（TextCodec.values） */
    public val codecCount: Int get() = TextCodec.entries.size
}
