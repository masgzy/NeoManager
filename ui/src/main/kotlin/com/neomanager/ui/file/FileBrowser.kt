/*
 * Copyright (C) 2026 The NeoManager Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.neomanager.ui.file

/**
 * 文件浏览后端抽象。
 *
 * Phase 1 ① 当前由 [JavaIoFileBrowser] 提供临时演示实现；
 * Phase 1 ② 起由引擎层实现替换（java.io 直读 → engine-core 统一 IO 引擎，
 * 支持压缩包内部浏览、root/Shizuku 受保护目录等），UI 组件只面向本接口。
 */
public fun interface FileBrowser {
    /**
     * 列出 [path] 目录下的条目。
     *
     * @return 成功时返回按「目录优先、名称升序」排序的条目列表；
     * 失败时返回包裹了人类可读错误消息的 [Result.failure]。
     */
    public fun list(path: String): Result<List<FileEntry>>
}
