/*
 * Copyright (C) 2026 The NeoManager Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.neomanager.engine.core.archive

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** [ArchiveEngine] 打包→列表→解压 往返一致性测试（覆盖全部可写格式） */
class ArchiveEngineTest {
    private lateinit var tmpDir: File

    @BeforeTest
    fun setUp() {
        tmpDir = Files.createTempDirectory("neo-archive-test").toFile()
    }

    @AfterTest
    fun tearDown() {
        tmpDir.deleteRecursively()
    }

    private fun makeSourceDir(): File {
        val dir = tmpDir.resolve("src").apply { mkdirs() }
        File(dir, "hello.txt").writeText("你好，压缩世界")
        File(dir, "nested").mkdirs()
        File(dir, "nested/data.bin").writeBytes(ByteArray(2048) { (it % 7).toByte() })
        return dir
    }

    private fun roundtrip(formatExt: String) {
        val source = makeSourceDir()
        val target = File(tmpDir, "packed$formatExt")
        val packed = ArchiveEngine.pack(listOf(source), target).getOrThrow()
        assertTrue(packed >= 2, "至少包含 hello.txt 与 nested/data.bin：$packed")

        // 魔数探测
        val detected: ArchiveFormat? = target.inputStream().use { ArchiveFormat.detect(it) }
        assertEquals(expectFormat(formatExt), detected, "格式探测失败：$formatExt")

        // 列表
        val entries = ArchiveEngine.list(target).getOrThrow()
        assertTrue(entries.any { it.name.endsWith("hello.txt") }, "清单缺少 hello.txt：${entries.map { it.name }}")
        assertTrue(entries.any { it.name.endsWith("data.bin") })

        // 解压
        val outDir = File(tmpDir, "out$formatExt")
        val count = ArchiveEngine.extract(target, outDir).getOrThrow()
        assertTrue(count >= 2)
        val hello = outDir.walk().firstOrNull { it.name == "hello.txt" }
        assertNotNull(hello, "解压结果缺少 hello.txt")
        assertEquals("你好，压缩世界", hello.readText())
        val bin = outDir.walk().firstOrNull { it.name == "data.bin" }
        assertNotNull(bin)
        assertEquals(2048, bin.length())
    }

    private fun expectFormat(ext: String): ArchiveFormat =
        when (ext) {
            ".zip" -> ArchiveFormat.ZIP
            ".tar" -> ArchiveFormat.TAR
            ".tar.gz" -> ArchiveFormat.GZ // 魔数层面是 gzip，内容层才是 tar
            ".tar.xz" -> ArchiveFormat.XZ
            ".tar.zst" -> ArchiveFormat.ZST
            ".tar.bz2" -> ArchiveFormat.BZ2
            else -> throw IllegalArgumentException(ext)
        }

    @Test
    fun zipRoundtrip() = roundtrip(".zip")

    @Test
    fun tarRoundtrip() = roundtrip(".tar")

    @Test
    fun tarGzRoundtrip() = roundtrip(".tar.gz")

    @Test
    fun tarXzRoundtrip() = roundtrip(".tar.xz")

    @Test
    fun tarZstRoundtrip() = roundtrip(".tar.zst")

    @Test
    fun tarBz2Roundtrip() = roundtrip(".tar.bz2")

    @Test
    fun singleFileCompressionRoundtrip() {
        val file = File(tmpDir, "note.txt").apply { writeText("single file") }
        for (ext in listOf(".gz", ".xz", ".zst", ".bz2")) {
            val target = File(tmpDir, "note.txt$ext")
            ArchiveEngine.pack(listOf(file), target).getOrThrow()
            val out = File(tmpDir, "out-single$ext")
            ArchiveEngine.extract(target, out).getOrThrow()
            assertEquals("single file", out.resolve("note.txt").readText(), "格式 $ext 解压内容不一致")
        }
    }

    @Test
    fun zipSlipRejected() {
        // 构造含 .. 条目名的恶意 zip（JDK 允许写入）
        val evil = File(tmpDir, "evil.zip")
        java.util.zip.ZipOutputStream(evil.outputStream()).use { out ->
            out.putNextEntry(java.util.zip.ZipEntry("../escape.txt"))
            out.write(byteArrayOf(1))
            out.closeEntry()
        }
        val result = ArchiveEngine.extract(evil, File(tmpDir, "evil-out"))
        assertTrue(result.isFailure, "zip-slip 必须被拒绝")
    }

    @Test
    fun detectMagicBytes() {
        assertEquals(ArchiveFormat.ZIP, ArchiveFormat.detect(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0, 0, 0, 0)))
        assertEquals(ArchiveFormat.GZ, ArchiveFormat.detect(byteArrayOf(0x1F, 0x8B.toByte(), 0x08, 0, 0, 0, 0, 0)))
        assertEquals(
            ArchiveFormat.SEVEN_Z,
            ArchiveFormat.detect(byteArrayOf(0x37, 0x7A, 0xBC.toByte(), 0xAF.toByte(), 0x27, 0x1C, 0, 0)),
        )
        assertEquals(ArchiveFormat.RAR, ArchiveFormat.detect("Rar!\u001A\u0007\u0000".toByteArray() + ByteArray(4)))
        assertEquals(
            ArchiveFormat.XZ,
            ArchiveFormat.detect(byteArrayOf(0xFD.toByte(), 0x37, 0x7A, 0x58, 0x5A, 0x00, 0, 0)),
        )
        assertEquals(
            ArchiveFormat.ZST,
            ArchiveFormat.detect(byteArrayOf(0x28.toByte(), 0xB5.toByte(), 0x2F, 0xFD.toByte(), 0, 0, 0, 0)),
        )
    }
}
