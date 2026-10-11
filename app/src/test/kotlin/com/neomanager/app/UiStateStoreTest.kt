/*
 * Copyright (C) 2026 The NeoManager Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.neomanager.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.neomanager.ui.bookmark.BookmarkItem
import com.neomanager.ui.bookmark.UiStateCodec
import com.neomanager.ui.bookmark.UiStateStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * 侧边抽屉持久化（书签/历史/首页）单元测试（Robolectric 真实 SharedPreferences）。
 * 编解码（UiStateCodec）为纯 JVM 断言；存储行为经真实 SharedPreferences 往返验证。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
public class UiStateStoreTest {
    private fun newStore(): UiStateStore {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return UiStateStore(
            context.getSharedPreferences("neo_ui_state_test", Context.MODE_PRIVATE),
        )
    }

    // -------- UiStateCodec：编解码无损往返 --------

    @Test
    public fun codec_pairsRoundTripWithSpecialCharacters() {
        val pairs =
            listOf(
                "内部 存储" to "file:///storage/emulated/0/下载 目录",
                "zip!" to "zip:file:///a b.zip!in%21ner",
                "back\\slash" to "file:///",
                "tab\tsep" to "file:///t",
                "new\nline" to "file:///n",
            )
        val text = UiStateCodec.encodePairs(pairs)
        assertEquals(pairs, UiStateCodec.decodePairs(text))
    }

    @Test
    public fun codec_pathsRoundTripAndSkipCorruptLines() {
        val paths = listOf("file:///a", "zip:file:///x.zip!", "file:///含 中文")
        assertEquals(paths, UiStateCodec.decodePaths(UiStateCodec.encodePaths(paths)))
        // 损坏行（无制表符分隔的单列 pair 行）被静默跳过
        assertTrue(UiStateCodec.decodePairs("only-one-column").isEmpty())
        assertEquals(emptyList<String>(), UiStateCodec.decodePaths(""))
    }

    // -------- UiStateStore：书签 --------

    @Test
    public fun bookmarks_addListRemove_withIdempotence() {
        val store = newStore()
        assertTrue(store.addBookmark("下载", "file:///storage/emulated/0/Download"))
        assertFalse("同路径重复添加应幂等", store.addBookmark("别名", "file:///storage/emulated/0/Download"))
        assertEquals(listOf("下载" to "file:///storage/emulated/0/Download"), store.bookmarks())
        assertTrue(store.removeBookmark("file:///storage/emulated/0/Download"))
        assertFalse("删除不存在的书签应返回 false", store.removeBookmark("file:///x"))
        assertTrue(store.bookmarks().isEmpty())
    }

    @Test
    public fun bookmarks_persistAcrossStoreInstances() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val first =
            UiStateStore(context.getSharedPreferences("neo_ui_state_test2", Context.MODE_PRIVATE))
        first.addBookmark("zip 书签", "zip:file:///sdcard/a.zip!")
        // 新实例（模拟进程重启）读同一偏好文件
        val second =
            UiStateStore(context.getSharedPreferences("neo_ui_state_test2", Context.MODE_PRIVATE))
        assertEquals(listOf("zip 书签" to "zip:file:///sdcard/a.zip!"), second.bookmarks())
    }

    // -------- UiStateStore：历史 --------

    @Test
    public fun history_recordsLatestFirst_dedupsAndCapsLimit() {
        val store = newStore()
        store.recordHistory("file:///a")
        store.recordHistory("file:///b")
        store.recordHistory("file:///a")
        assertEquals(listOf("file:///a", "file:///b"), store.history())
        store.recordHistory("file:///a")
        assertEquals("连续重复只记一次", listOf("file:///a", "file:///b"), store.history())
        for (i in 0 until UiStateStore.HISTORY_LIMIT + 10) {
            store.recordHistory("file:///gen/$i")
        }
        assertEquals(UiStateStore.HISTORY_LIMIT, store.history().size)
        assertEquals("file:///gen/${UiStateStore.HISTORY_LIMIT + 9}", store.history().first())
    }

    @Test
    public fun history_clearWorks() {
        val store = newStore()
        store.recordHistory("file:///a")
        store.clearHistory()
        assertTrue(store.history().isEmpty())
    }

    // -------- UiStateStore：首页 --------

    @Test
    public fun homePath_setAndClear() {
        val store = newStore()
        assertNull(store.homePath())
        store.setHomePath("file:///storage/emulated/0")
        assertEquals("file:///storage/emulated/0", store.homePath())
        store.setHomePath(null)
        assertNull(store.homePath())
    }

    // -------- UiStateStore：手动隐藏（0.2.0-alpha07） --------

    @Test
    public fun hiddenPaths_addRemove_withIdempotence() {
        val store = newStore()
        assertTrue(store.hiddenPaths().isEmpty())
        assertTrue(store.hidePath("file:///storage/emulated/0/.secret"))
        assertFalse("重复隐藏应幂等", store.hidePath("file:///storage/emulated/0/.secret"))
        assertEquals(setOf("file:///storage/emulated/0/.secret"), store.hiddenPaths())
        assertTrue(store.unhidePath("file:///storage/emulated/0/.secret"))
        assertFalse("解除不存在的隐藏应返回 false", store.unhidePath("file:///x"))
        assertTrue(store.hiddenPaths().isEmpty())
    }

    @Test
    public fun tips_seenFlagDefaultsFalseAndPersists() {
        val store = newStore()
        assertFalse(store.isTipsSeen())
        store.setTipsSeen()
        assertTrue(store.isTipsSeen())
    }

    // -------- 书签分组（0.2.0-alpha08） --------

    @Test
    public fun codec_bookmarksRoundTripAndLegacyTwoColumnCompat() {
        val items =
            listOf(
                BookmarkItem("", "下载", "file:///storage/emulated/0/Download"),
                BookmarkItem("工作", "项目", "file:///data/projects"),
                BookmarkItem("含\t转义", "名字", "file:///a b"),
            )
        val text = UiStateCodec.encodeBookmarks(items)
        assertEquals(items, UiStateCodec.decodeBookmarks(text))
        // 旧两列格式（无分组）兼容读取
        val legacy = UiStateCodec.encodePairs(listOf("旧书签" to "file:///legacy"))
        assertEquals(
            listOf(BookmarkItem("", "旧书签", "file:///legacy")),
            UiStateCodec.decodeBookmarks(legacy),
        )
        // 空分组行不写第三列，仍可往返
        assertEquals(
            listOf(BookmarkItem("", "a", "file:///a")),
            UiStateCodec.decodeBookmarks(UiStateCodec.encodeBookmarks(listOf(BookmarkItem("", "a", "file:///a")))),
        )
    }

    @Test
    public fun bookmarks_groupedAddListRemove_preservesGroups() {
        val store = newStore()
        assertTrue(store.addBookmark("未分组", "file:///plain"))
        assertTrue(store.addBookmark("工作书签", "file:///work", group = "工作"))
        assertTrue(store.addBookmark("生活书签", "file:///life", group = "生活"))
        assertFalse("同路径重复添加应幂等（跨分组）", store.addBookmark("别名", "file:///work", group = "生活"))
        assertEquals(listOf("工作", "生活"), store.bookmarkGroups())
        assertEquals(
            listOf(
                BookmarkItem("", "未分组", "file:///plain"),
                BookmarkItem("工作", "工作书签", "file:///work"),
                BookmarkItem("生活", "生活书签", "file:///life"),
            ),
            store.bookmarksGrouped(),
        )
        // 兼容旧 API：分组信息折叠为「名称 to 路径」
        assertEquals(
            listOf("未分组" to "file:///plain", "工作书签" to "file:///work", "生活书签" to "file:///life"),
            store.bookmarks(),
        )
        // 删除保留其余分组
        assertTrue(store.removeBookmark("file:///work"))
        assertEquals(listOf("生活"), store.bookmarkGroups())
        assertEquals(2, store.bookmarksGrouped().size)
    }

    // -------- 回收站索引（0.2.0-alpha08） --------

    @Test
    public fun trashIndex_recordRemoveClear() {
        val store = newStore()
        assertTrue(store.trashIndex().isEmpty())
        store.recordTrashItem("20261011_080000_a.txt", "file:///sdcard/a.txt")
        store.recordTrashItem("20261011_080001_b", "file:///sdcard/b")
        assertEquals(
            listOf("20261011_080000_a.txt" to "file:///sdcard/a.txt", "20261011_080001_b" to "file:///sdcard/b"),
            store.trashIndex(),
        )
        store.removeTrashItems(listOf("20261011_080000_a.txt"))
        assertEquals(listOf("20261011_080001_b" to "file:///sdcard/b"), store.trashIndex())
        store.clearTrashIndex()
        assertTrue(store.trashIndex().isEmpty())
    }

    // -------- 单栏布局开关（0.2.0-alpha08） --------

    @Test
    public fun singlePane_defaultsFalseAndPersists() {
        val store = newStore()
        assertFalse(store.isSinglePane())
        store.setSinglePane(true)
        assertTrue(store.isSinglePane())
        store.setSinglePane(false)
        assertFalse(store.isSinglePane())
    }
}
