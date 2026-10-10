// NeoManager — Shizuku 用户服务 AIDL
// Copyright (C) 2026 The NeoManager Contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package com.neomanager.engine.android;

import android.os.ParcelFileDescriptor;

/**
 * Shizuku 用户服务接口：以 shell（或 root）UID 执行文件操作。
 *
 * 清单编码约定：每行一个条目，字段以 '|' 分隔：
 * d|最后修改毫秒|名称       目录
 * l|最后修改毫秒|目标|名称  符号链接
 * f|大小|最后修改毫秒|名称  普通文件
 */
interface INeoFileService {
    boolean exists(String path);

    boolean isDirectory(String path);

    /** 列出目录（按清单编码约定逐行返回）；失败抛出携带原因的 SecurityException/IOException */
    List<String> list(String path);

    ParcelFileDescriptor openRead(String path);

    long writeFile(String path, in ParcelFileDescriptor source, boolean append);

    boolean mkdirs(String path);

    boolean delete(String path, boolean recursive);

    boolean rename(String path, String newName);
}
