/*
 * Copyright (C) 2026 The NeoManager Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.neomanager.engine.core.vfs

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [ZipVfs] 行为测试：浏览/读取/回写重建/嵌套容器。
 * 重点校验「改动后未触碰条目内容一致 + STORED 条目方式保持」。
 */
class ZipVfsTest {
    private lateinit var tmpDir: java.nio.file.Path
    private lateinit var registry: VfsRegistry
    private lateinit var zipFile: File

    @BeforeTest
    fun setUp() {
        tmpDir = Files.createTempDirectory("neo-zipvfs-test")
        registry = VfsRegistry()
        zipFile = tmpDir.resolve("sample.zip").toFile()
    }

    @AfterTest
    fun tearDown() {
        tmpDir.toFile().deleteRecursively()
    }

    private fun zipUri(inner: String = ""): VfsUri = VfsUri.zipOf(VfsUri.ofLocal(zipFile.absolutePath), inner)

    /** 用 JDK 构造样例包：含子目录、STORED 条目、中文名 */
    private fun buildSampleZip() {
        ZipOutputStream(zipFile.outputStream()).use { out ->
            out.putNextEntry(ZipEntry("docs/"))
            out.closeEntry()

            out.putNextEntry(ZipEntry("docs/readme.txt"))
            out.write("readme-v1".toByteArray())
            out.closeEntry()

            val stored = ZipEntry("data.bin").apply { method = ZipEntry.STORED }
            val bytes = ByteArray(1024) { (it % 251).toByte() }
            java.util.zip.CRC32().apply {
                update(bytes)
                stored.crc = value
            }
            stored.size = bytes.size.toLong()
            out.putNextEntry(stored)
            out.write(bytes)
            out.closeEntry()

            out.putNextEntry(ZipEntry("中文资源/名字.txt"))
            out.write("中文内容".toByteArray(Charsets.UTF_8))
            out.closeEntry()

            out.putNextEntry(ZipEntry("deep/nested/file.json"))
            out.write("""{"a":1}""".toByteArray())
            out.closeEntry()
        }
    }

    @Test
    fun listSynthesizesDirectoriesAndChildren() {
        buildSampleZip()
        val root = registry.resolve(zipUri()).list(zipUri()).getOrThrow()
        // 直接子代：docs/ data.bin 中文资源/ deep/
        assertEquals(
            listOf("deep", "docs", "中文资源", "data.bin"),
            root.map { it.name },
        )
        assertTrue(root.first { it.name == "docs" }.isDirectory)
        assertEquals(1024L, root.first { it.name == "data.bin" }.sizeBytes)

        val docs = registry.resolve(zipUri()).list(zipUri("docs")).getOrThrow()
        assertEquals(listOf("readme.txt"), docs.map { it.name })
    }

    @Test
    fun readEntryContent() {
        buildSampleZip()
        val content =
            registry
                .resolve(zipUri())
                .openRead(zipUri("docs/readme.txt"))
                .getOrThrow()
                .use { it.readBytes().decodeToString() }
        assertEquals("readme-v1", content)
    }

    @Test
    fun existsAndIsDirectory() {
        buildSampleZip()
        val vfs = registry.resolve(zipUri())
        assertTrue(vfs.exists(zipUri("data.bin")))
        assertTrue(vfs.isDirectory(zipUri("deep")))
        assertTrue(vfs.isDirectory(zipUri("deep/nested"))) // 合成目录
        assertFalse(vfs.exists(zipUri("missing.bin")))
    }

    @Test
    fun writeFileReplacesAndPreservesOthers() {
        buildSampleZip()
        val before = zipFile.readBytes()
        val vfs = registry.resolve(zipUri())

        vfs.writeFile(zipUri("docs/readme.txt"), "readme-v2".byteInputStream(), append = false).getOrThrow()

        assertEquals(
            "readme-v2",
            vfs.openRead(zipUri("docs/readme.txt")).getOrThrow().use { it.readBytes().decodeToString() },
        )
        // STORED 条目保持原内容
        val bin = vfs.openRead(zipUri("data.bin")).getOrThrow().use { it.readBytes() }
        assertEquals(1024, bin.size)
        assertEquals((5 % 251).toByte(), bin[5])
        // 中文条目未受重建影响
        assertEquals(
            "中文内容",
            vfs.openRead(zipUri("中文资源/名字.txt")).getOrThrow().use { String(it.readBytes(), Charsets.UTF_8) },
        )
        // 重建后中文条目仍在（上方断言）；文件本体仍是合法 zip
        assertTrue(zipFile.length() > 0)
    }

    @Test
    fun deleteRemovesSubtree() {
        buildSampleZip()
        val vfs = registry.resolve(zipUri())
        vfs.delete(zipUri("deep"), recursive = true).getOrThrow()
        assertFalse(vfs.exists(zipUri("deep/nested/file.json")))
        assertTrue(vfs.exists(zipUri("docs/readme.txt")))
    }

    @Test
    fun renameInsideZip() {
        buildSampleZip()
        val vfs = registry.resolve(zipUri())
        val renamed = vfs.rename(zipUri("docs/readme.txt"), "manual.txt").getOrThrow()
        assertEquals("manual.txt", renamed.name)
        assertFalse(vfs.exists(zipUri("docs/readme.txt")))
        assertEquals(
            "readme-v1",
            vfs.openRead(zipUri("docs/manual.txt")).getOrThrow().use { it.readBytes().decodeToString() },
        )
    }

    @Test
    fun mkdirCreatesPlaceholderEntry() {
        buildSampleZip()
        val vfs = registry.resolve(zipUri())
        vfs.mkdir(zipUri("empty-dir")).getOrThrow()
        assertTrue(vfs.isDirectory(zipUri("empty-dir")))
        assertTrue(vfs.list(zipUri("empty-dir")).getOrThrow().isEmpty())
    }

    @Test
    fun nestedZipBrowseAndEdit() {
        // 外层 zip 包含 inner.zip（其自身是合法 zip）
        val inner = tmpDir.resolve("inner.zip").toFile()
        ZipOutputStream(inner.outputStream()).use { out ->
            out.putNextEntry(ZipEntry("level2.txt"))
            out.write("inner-v1".toByteArray())
            out.closeEntry()
        }
        ZipOutputStream(zipFile.outputStream()).use { out ->
            out.putNextEntry(ZipEntry("inner.zip"))
            inner.inputStream().copyTo(out)
            out.closeEntry()
        }

        val innerUri = VfsUri.zipOf(VfsUri.zipOf(VfsUri.ofLocal(zipFile.absolutePath), "inner.zip"), "")
        val innerVfs = registry.resolve(innerUri)
        assertEquals(listOf("level2.txt"), innerVfs.list(innerUri).getOrThrow().map { it.name })

        // 编辑嵌套 zip 内的文件 → 触发两层重建
        innerVfs.writeFile(innerUri.resolve("level2.txt"), "inner-v2".byteInputStream(), append = false).getOrThrow()
        assertEquals(
            "inner-v2",
            innerVfs.openRead(innerUri.resolve("level2.txt")).getOrThrow().use {
                it.readBytes().decodeToString()
            },
        )
        // 外层其余内容仍在
        val outer = registry.resolve(zipUri())
        assertTrue(outer.exists(zipUri("inner.zip")))
    }

    @Test
    fun storeMethodPreservedAfterRebuild() {
        buildSampleZip()
        registry
            .resolve(
                zipUri(),
            ).writeFile(zipUri("docs/readme.txt"), "x".byteInputStream(), append = false)
            .getOrThrow()

        // 重建后 data.bin 仍应为 STORED（JDK ZipInputStream 读 method 不可见，
        // 用 commons-compress 读回验证）
        org.apache.commons.compress.archivers.zip.ZipFile(zipFile, "UTF-8").use { zip ->
            assertEquals(
                org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream.STORED,
                zip.getEntry("data.bin").method,
            )
            assertEquals(
                org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream.DEFLATED,
                zip.getEntry("docs/readme.txt").method,
            )
        }
    }
}
