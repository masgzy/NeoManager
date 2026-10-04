/*
 * Copyright (C) 2026 The NeoManager Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.neomanager.ui.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 展示格式化工具：文件大小、修改时间。
 * 纯函数、无状态，供列表行与详情展示复用。
 */
public object Format {
    private const val UNIT_STEP: Double = 1024.0
    private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    private val UNITS = arrayOf("B", "KB", "MB", "GB", "TB")

    /**
     * 字节数转人类可读大小："1.2 KB"、"3 MB"；目录或未知返回空串。
     * 1024 进制（存储器行业惯例），两位有效小数。
     */
    public fun fileSize(bytes: Long): String {
        if (bytes <= 0L) return ""
        var value = bytes.toDouble()
        var unitIndex = 0
        while (value >= UNIT_STEP && unitIndex < UNITS.lastIndex) {
            value /= UNIT_STEP
            unitIndex++
        }
        val text =
            if (unitIndex == 0) {
                value.toInt().toString()
            } else {
                // 有效数字两位：小于 10 保留一位小数，否则取整
                if (value < 10.0) {
                    val rounded = (value * 10).toInt() / 10.0
                    trimZero(rounded)
                } else {
                    value.toInt().toString()
                }
            }
        return "$text ${UNITS[unitIndex]}"
    }

    /** epoch 毫秒转 "yyyy-MM-dd HH:mm"；未知（<=0）返回空串 */
    public fun modifiedTime(epochMillis: Long): String {
        if (epochMillis <= 0L) return ""
        return DATE_FORMAT.format(Date(epochMillis))
    }

    /** 去掉 ".0" 结尾 */
    private fun trimZero(value: Double): String {
        val text = value.toString()
        return if (text.endsWith(".0")) text.removeSuffix(".0") else text
    }
}
