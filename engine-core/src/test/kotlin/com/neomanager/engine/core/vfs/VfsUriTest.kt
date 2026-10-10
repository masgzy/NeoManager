/*
 * Copyright (C) 2026 The NeoManager Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.neomanager.engine.core.vfs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [VfsUri] 路径模型与编码往返测试 */
class VfsUriTest {
    @Test
    fun localParseAndNormalize() {
        assertEquals("file:///a/b/c.txt", VfsUri.parse("file:///a/b/c.txt").value)
        assertEquals("file:///a/b", VfsUri.parse("file:///a/b/").value)
        assertEquals("file:///", VfsUri.parse("file://").value)
        assertEquals("/", VfsUri.ofLocal("/").localPath)
        assertEquals("/x/y", VfsUri.ofLocal("/x/y//").localPath)
    }

    @Test
    fun zipUriRoundTrip() {
        val uri = VfsUri.zipOf(VfsUri.ofLocal("/sdcard/app.apk"), "res/layout/main.xml")
        assertEquals("zip", uri.scheme)
        assertEquals("/sdcard/app.apk", uri.containerUri.localPath)
        assertEquals("res/layout/main.xml", uri.zipInnerPath)
        assertEquals(uri, VfsUri.parse(uri.value))
    }

    @Test
    fun zipRootAndResolve() {
        val root = VfsUri.zipOf(VfsUri.ofLocal("/a.zip"), "")
        assertTrue(root.isRoot)
        assertEquals("a.zip", root.containerUri.name)
        val child = root.resolve("assets/data.json")
        assertEquals("assets/data.json", child.zipInnerPath)
        assertEquals("data.json", child.name)
        assertEquals("assets", child.parent()!!.zipInnerPath)
        // parent 两次到 zip 根，三次回到容器所在目录（file:///）
        assertEquals("zip:file:///a.zip!", child.parent()!!.parent()!!.value)
        assertEquals(
            "file:///",
            child
                .parent()!!
                .parent()!!
                .parent()!!
                .value,
        )
        assertNull(
            child
                .parent()!!
                .parent()!!
                .parent()!!
                .parent(),
        )
    }

    @Test
    fun nestedZipUri() {
        val outer = VfsUri.ofLocal("/sdcard/outer.zip")
        val middle = VfsUri.zipOf(outer, "inner.apk")
        val deep = VfsUri.zipOf(middle, "classes.dex")
        // 外层容器的 '!' 分隔符会被编码，保证「第一个未转义 '!' 即分界」不变式
        assertEquals("zip:zip:file:///sdcard/outer.zip%21inner.apk!classes.dex", deep.value)
        assertEquals("inner.apk", deep.containerUri.zipInnerPath)
        assertEquals("/sdcard/outer.zip", deep.containerUri.containerUri.localPath)
        assertEquals(deep, VfsUri.parse(deep.value))
    }

    @Test
    fun specialCharsEscaped() {
        // 文件名里带 ! 和 % 也不能破坏 zip 分隔
        val uri = VfsUri.zipOf(VfsUri.ofLocal("/a/b!c.zip"), "dir/file!100%.txt")
        assertEquals("b!c.zip", uri.containerUri.name)
        assertEquals("dir/file!100%.txt", uri.zipInnerPath)
        assertEquals(uri, VfsUri.parse(uri.value))
    }

    @Test
    fun localPathWithExclamationMark() {
        // 本地路径天然允许 !：zip 容器编码后不影响解析
        val local = VfsUri.ofLocal("/data/joy!.zip")
        assertEquals("/data/joy!.zip", local.localPath)
        val zip = VfsUri.zipOf(local, "x")
        assertEquals(local, zip.containerUri)
    }

    @Test
    fun parentOfLocal() {
        assertNull(VfsUri.ofLocal("/").parent())
        assertEquals("/", VfsUri.ofLocal("/a")!!.parent()!!.localPath)
        assertEquals("/a", VfsUri.ofLocal("/a/b")!!.parent()!!.localPath)
    }

    @Test
    fun invalidInputRejected() {
        assertFailsWith<IllegalArgumentException> { VfsUri.parse("http://x") }
        assertFailsWith<IllegalArgumentException> { VfsUri.parse("zip:file:///a.zip") }
        assertFalse(VfsUri.parseOrNull(" nonsense ") != null)
    }

    @Test
    fun nameAndRootHelpers() {
        assertEquals("c.txt", VfsUri.ofLocal("/a/b/c.txt").name)
        assertTrue(VfsUri.ofLocal("/").isRoot)
        assertTrue(VfsUri.zipOf(VfsUri.ofLocal("/z.zip"), "").isRoot)
        assertFalse(VfsUri.zipOf(VfsUri.ofLocal("/z.zip"), "f").isRoot)
    }
}
