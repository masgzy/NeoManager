/*
 * Copyright (C) 2026 The NeoManager Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.neomanager.engine.core.vfs

import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** [LocalVfs] 与 [VfsRegistry] 组合回退行为测试 */
class LocalVfsTest {
    private lateinit var root: java.nio.file.Path
    private val vfs = LocalVfs.INSTANCE

    @BeforeTest
    fun setUp() {
        root = Files.createTempDirectory("neo-localvfs-test")
    }

    @AfterTest
    fun tearDown() {
        root.toFile().deleteRecursively()
    }

    private fun uri(child: String = ""): VfsUri = VfsUri.ofLocal(root.resolve(child).toString())

    @Test
    fun listSortsDirectoriesFirst() {
        Files.createDirectories(root.resolve("zdir"))
        Files.write(root.resolve("afile.txt"), byteArrayOf(1))
        Files.createDirectories(root.resolve("adir"))

        val entries = vfs.list(uri()).getOrThrow()
        assertEquals(listOf("adir", "zdir", "afile.txt"), entries.map { it.name })
        assertTrue(entries.first().isDirectory)
        assertEquals(1L, entries.last().sizeBytes)
    }

    @Test
    fun writeReadRoundTrip() {
        val file = uri("hello.txt")
        val payload = "你好 NeoManager".toByteArray(Charsets.UTF_8)
        vfs.writeFile(file, payload.inputStream(), append = false).getOrThrow()
        assertTrue(vfs.exists(file))
        assertFalse(vfs.isDirectory(file))
        val read = vfs.openRead(file).getOrThrow().use { it.readBytes() }
        assertEquals(payload.decodeToString(), read.decodeToString())
    }

    @Test
    fun mkdirDeleteRename() {
        val dir = uri("parent/child")
        vfs.mkdir(dir).getOrThrow()
        assertTrue(vfs.isDirectory(dir))

        vfs.rename(dir, "child2").getOrThrow().let { renamed ->
            assertTrue(vfs.isDirectory(renamed))
        }
        vfs.delete(uri("parent"), recursive = true).getOrThrow()
        assertFalse(vfs.exists(uri("parent")))
    }

    @Test
    fun symlinkDetected() {
        val target = Files.createFile(root.resolve("target.txt"))
        val link = Files.createSymbolicLink(root.resolve("link"), target)
        val entries = vfs.list(uri()).getOrThrow()
        val linkEntry = entries.first { it.name == link.fileName.toString() }
        assertTrue(linkEntry.isSymlink)
        assertEquals(target.toString(), linkEntry.linkTarget)
    }

    @Test
    fun registryResolvesZipScheme() {
        val registry = VfsRegistry()
        val zipUri = VfsUri.zipOf(VfsUri.ofLocal("/tmp/x.zip"), "a")
        assertTrue(registry.resolve(zipUri) is ZipVfs)
        assertTrue(registry.resolve(VfsUri.ofLocal("/tmp")) is LocalVfs)
    }

    @Test
    fun compositeFallsBackToElevated() {
        val primary = LocalVfs.INSTANCE
        val fallbackVfs =
            object : Vfs {
                override fun capabilities() = setOf(VfsCapability.LIST)

                override fun list(dir: VfsUri) =
                    Result.success(listOf(VfsEntry("root-only.txt", dir.resolve("root-only.txt"), false)))

                override fun openRead(file: VfsUri) = Result.success("secret".byteInputStream())

                override fun exists(uri: VfsUri) = true

                override fun isDirectory(uri: VfsUri) = false

                override fun mkdir(dir: VfsUri) = Result.success(Unit)

                override fun delete(
                    uri: VfsUri,
                    recursive: Boolean,
                ) = Result.success(Unit)

                override fun rename(
                    uri: VfsUri,
                    newName: String,
                ) = Result.success(uri)

                override fun writeFile(
                    target: VfsUri,
                    content: java.io.InputStream,
                    append: Boolean,
                ) = Result.success(0L)
            }
        val composite = VfsRegistry.CompositeVfs(primary, fallbackVfs)
        // primary 对不存在目录失败 → 回退到 fallback
        val result = composite.list(uri("denied"))
        assertTrue(result.isSuccess)
        assertEquals("root-only.txt", result.getOrThrow().single().name)
    }
}
