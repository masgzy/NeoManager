/*
 * Copyright (C) 2026 The NeoManager Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.neomanager.engine.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [PathNormalizer] 行为规格测试。
 *
 * 这些用例即 engine-core 路径语义的"可执行规格"，
 * 后续 ZIP 条目寻址与资源路径对齐功能必须与本规格保持一致。
 */
class PathNormalizerTest {
    // region normalize

    @Test
    fun `normalize 消除多余分隔符与点段`() {
        assertEquals("a/b/c", PathNormalizer.normalize("a//b/./c/"))
        assertEquals("/a/b", PathNormalizer.normalize("//a///b//"))
    }

    @Test
    fun `normalize 折叠绝对路径中可解析的点对段`() {
        assertEquals("/a/c", PathNormalizer.normalize("/a/b/../c"))
        assertEquals("/a", PathNormalizer.normalize("/a/b/c/../.."))
    }

    @Test
    fun `normalize 绝对路径在根之上的点对被钳制`() {
        assertEquals("/b", PathNormalizer.normalize("/a/../../b"))
        assertEquals("/", PathNormalizer.normalize("/a/b/../../../.."))
    }

    @Test
    fun `normalize 相对路径顶端点对保留`() {
        assertEquals("../a", PathNormalizer.normalize("../a"))
        assertEquals("../a/b", PathNormalizer.normalize("a/../../a/b"))
    }

    @Test
    fun `normalize 空串与根`() {
        assertEquals("", PathNormalizer.normalize(""))
        assertEquals("", PathNormalizer.normalize("."))
        assertEquals("/", PathNormalizer.normalize("/"))
        assertEquals("/", PathNormalizer.normalize("/."))
        assertEquals("/", PathNormalizer.normalize("/.."))
    }

    @Test
    fun `normalize 支持自定义分隔符`() {
        assertEquals("a\\b", PathNormalizer.normalize("a\\b", separator = '\\'))
        assertEquals("\\a\\b", PathNormalizer.normalize("\\a\\b", separator = '\\'))
    }

    // endregion

    // region resolve

    @Test
    fun `resolve 相对路径拼接到基路径`() {
        assertEquals("/a/b/c", PathNormalizer.resolve("/a/b", "c"))
        assertEquals("/a/c", PathNormalizer.resolve("/a/b", "../c"))
    }

    @Test
    fun `resolve 相对参数为绝对路径时直接规范化`() {
        assertEquals("/x/y", PathNormalizer.resolve("/a/b", "/x/y"))
    }

    @Test
    fun `resolve 基路径为空即相对根`() {
        assertEquals("a/b", PathNormalizer.resolve("", "a/b"))
        assertEquals("c", PathNormalizer.resolve(".", "c"))
    }

    // endregion

    // region parent

    @Test
    fun `parent 返回规范化父路径`() {
        assertEquals("/a/b", PathNormalizer.parent("/a/b/c"))
        assertEquals("/a", PathNormalizer.parent("/a/b"))
        assertEquals("/", PathNormalizer.parent("/a"))
        assertEquals("a/b", PathNormalizer.parent("a/b/c"))
    }

    @Test
    fun `parent 根与裸名无父返回 null`() {
        assertNull(PathNormalizer.parent("/"))
        assertNull(PathNormalizer.parent(""))
        assertNull(PathNormalizer.parent("file.txt"))
    }

    @Test
    fun `parent 先规范化再取父`() {
        assertEquals("/a", PathNormalizer.parent("/a/b/c/.."))
        assertEquals("a", PathNormalizer.parent("a/b/../c"))
    }

    // endregion

    // region name

    @Test
    fun `name 返回最后一段`() {
        assertEquals("c", PathNormalizer.name("/a/b/c"))
        assertEquals("c.apk", PathNormalizer.name("a/b/c.apk"))
        assertEquals("a", PathNormalizer.name("a"))
    }

    @Test
    fun `name 根路径返回空串`() {
        assertEquals("", PathNormalizer.name("/"))
        assertEquals("", PathNormalizer.name(""))
    }

    // endregion

    // region isInside

    @Test
    fun `isInside 直接子路径成立`() {
        assertTrue(PathNormalizer.isInside("/a", "/a/b"))
        assertTrue(PathNormalizer.isInside("/a", "/a/b/c.txt"))
        assertTrue(PathNormalizer.isInside("a/b", "a/b/c"))
    }

    @Test
    fun `isInside 同路径成立（含自身）`() {
        assertTrue(PathNormalizer.isInside("/a/b", "/a/b"))
    }

    @Test
    fun `isInside 前缀相同但非完整段不成立`() {
        assertFalse(PathNormalizer.isInside("/a/b", "/a/bc"))
        assertFalse(PathNormalizer.isInside("/a/b", "/a/bc/d"))
    }

    @Test
    fun `isInside 伪造点对路径被规范化防御`() {
        assertFalse(PathNormalizer.isInside("/safe", "/safe/../../etc/passwd"))
        assertTrue(PathNormalizer.isInside("/safe", "/safe/../safe/sub"))
    }

    @Test
    fun `isInside 父为空即相对根包含一切相对路径`() {
        assertTrue(PathNormalizer.isInside("", "a/b"))
        assertFalse(PathNormalizer.isInside("a", "/a"))
    }

    // endregion

    // region relative

    @Test
    fun `relative 返回相对段`() {
        assertEquals("b/c", PathNormalizer.relative("/a/b/c", "/a"))
        assertEquals("c.txt", PathNormalizer.relative("a/c.txt", "a"))
        assertEquals("", PathNormalizer.relative("/a", "/a"))
    }

    @Test
    fun `relative 基为空返回规范化目标`() {
        assertEquals("a/b", PathNormalizer.relative("a/b", ""))
    }

    @Test
    fun `relative 目标不在基内返回 null`() {
        assertNull(PathNormalizer.relative("/x/y", "/a"))
        assertNull(PathNormalizer.relative("/a/bc", "/a/b"))
    }

    // endregion
}
