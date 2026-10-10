/*
 * Copyright (C) 2026 The NeoManager Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.neomanager.engine.core.vfs

import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.ClosedChannelException
import java.nio.channels.SeekableByteChannel

/**
 * 只读 [SeekableByteChannel]，把任意 [Vfs] 的输入流包装成可寻址通道，
 * 供 commons-compress 的 ZipFile 在「容器位于其他文件系统内部」（zip 套 zip）时随机访问。
 *
 * 定位策略（惰性推进）：[position] 只记录目标位置；真正的流推进发生在下次 [read] 时——
 * 目标在当前流位置之后则顺序跳过，在之前则重开流再推进。
 * zip 中央目录的访问模式是「大步 seek + 局部顺序读」，该策略避免反复重开。
 */
public class VfsSeekableChannel(
    private val vfs: Vfs,
    private val file: VfsUri,
    /** 已知内容大小（如父 zip 中央目录记录的条目解压后大小）；-1 表示未知 */
    private val knownSize: Long = -1L,
) : SeekableByteChannel {
    private var stream: InputStream? = null

    /** 底层流的实际位置 */
    private var streamPosition: Long = 0L

    /** 逻辑目标位置 */
    private var logicalPosition: Long = 0L

    private var resolvedSize: Long = -1L
    private var closed: Boolean = false

    @Synchronized
    override fun read(destination: ByteBuffer): Int {
        ensureOpen()
        val input = positionedStream()
        val len = destination.remaining()
        if (len == 0) return 0
        val buffer = ByteArray(len)
        var total = 0
        while (total < len) {
            val n = input.read(buffer, total, len - total)
            if (n < 0) break
            total += n
        }
        if (total <= 0) return -1
        destination.put(buffer, 0, total)
        logicalPosition += total
        streamPosition += total
        return total
    }

    @Synchronized
    override fun position(): Long {
        ensureOpen()
        return logicalPosition
    }

    @Synchronized
    override fun position(newPosition: Long): SeekableByteChannel {
        ensureOpen()
        require(newPosition >= 0) { "负偏移：$newPosition" }
        logicalPosition = newPosition
        return this
    }

    @Synchronized
    override fun size(): Long {
        ensureOpen()
        if (resolvedSize < 0) {
            resolvedSize = if (knownSize >= 0) knownSize else probeSize()
        }
        return resolvedSize
    }

    override fun write(source: ByteBuffer?): Int = throw IOException("只读通道")

    override fun truncate(size: Long): SeekableByteChannel = throw IOException("只读通道")

    @Synchronized
    override fun isOpen(): Boolean = !closed

    @Synchronized
    override fun close() {
        closed = true
        closeStream()
    }

    // ------------------------------------------------------------------

    private fun ensureOpen() {
        if (closed) throw ClosedChannelException()
    }

    /** 返回已推进到逻辑位置的底层流 */
    private fun positionedStream(): InputStream {
        var input = ensureRawStream()
        if (logicalPosition < streamPosition) {
            // 回退：重开
            closeStream()
            input = ensureRawStream()
        }
        while (streamPosition < logicalPosition) {
            val gap = logicalPosition - streamPosition
            val skipped = input.skip(gap)
            if (skipped > 0) {
                streamPosition += skipped
            } else {
                // skip 不保证前进：补一次真实读取
                if (input.read() < 0) throw EOFException("容器提前结束：${file.value}")
                streamPosition += 1
            }
        }
        return input
    }

    private fun ensureRawStream(): InputStream {
        var input = stream
        if (input == null) {
            input = vfs.openRead(file).getOrThrow()
            stream = input
            streamPosition = 0L
        }
        return input
    }

    private fun closeStream() {
        stream?.let {
            try {
                it.close()
            } catch (_: IOException) {
            }
        }
        stream = null
        streamPosition = 0L
    }

    /** 兜底：未知大小时整流扫描计数（正常路径都传 knownSize） */
    private fun probeSize(): Long {
        val probe = vfs.openRead(file).getOrNull() ?: throw IOException("无法读取容器：${file.value}")
        try {
            var total = 0L
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = probe.read(buf)
                if (n < 0) break
                total += n
            }
            return total
        } finally {
            try {
                probe.close()
            } catch (_: IOException) {
            }
        }
    }
}
