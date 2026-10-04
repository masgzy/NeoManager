/*
 * Copyright (C) 2026 The NeoManager Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.neomanager.editor

/**
 * editor：代码编辑器封装层占位。
 *
 * 规划内容（Phase 1 第②步接入）：
 * - sora-editor（LGPL-2.1，动态依赖）的 Compose 互操作封装：`AndroidView(factory = { CodeEditorView })`
 * - 语法高亮方案注册：smali / XML / JSON / 纯文本（Phase 2-3 扩展）
 * - 大文件加载策略：分片读取 + 惰性高亮
 *
 * 本模块仅封装编辑器组件与配置，不承载任何业务编辑逻辑；
 * 编辑行为的正确性由调用方（ui / app）与引擎层（engine-core）保证。
 */
public object EditorBindings {
    /**
     * 模块就绪状态标记：true 表示 sora-editor 已接入。
     * Phase 1 ② 完成后由真实装配逻辑替换。
     */
    public const val PLACEHOLDER: Boolean = false
}
