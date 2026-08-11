package com.ainovel.app

import com.ainovel.app.domain.analysis.ChapterSplitter
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ChapterSplitterTest {

    @Test
    fun split_chineseChapterMarkers_detectsChapters() {
        val text = """
            第一章 开端
            这是第一章内容。

            第二章 冲突
            这是第二章内容。

            第三十章 决战
            这是第三十章内容。
        """.trimIndent()
        val chapters = ChapterSplitter.split(text)
        assertThat(chapters).hasSize(3)
        assertThat(chapters[0].title).contains("第一章")
        assertThat(chapters[0].content).contains("这是第一章内容")
        assertThat(chapters[2].title).contains("第三十章")
    }

    @Test
    fun split_arabicMarkers_detectsChapters() {
        val text = """
            第1章 开端
            内容一。

            第2章 发展
            内容二。
        """.trimIndent()
        val chapters = ChapterSplitter.split(text)
        assertThat(chapters).hasSize(2)
    }

    @Test
    fun split_noMarkers_returnsWholeTextAsSingleChapter() {
        val text = "没有章节标记的正文第一段。\n第二段继续。"
        val chapters = ChapterSplitter.split(text)
        assertThat(chapters).hasSize(1)
        assertThat(chapters[0].title).isEqualTo("全文")
        assertThat(chapters[0].content).contains("第一段")
    }

    @Test
    fun split_englishMarkers_detectsChapters() {
        val text = """
            Chapter 1 The Beginning
            content one

            Chapter 2 The Middle
            content two
        """.trimIndent()
        val chapters = ChapterSplitter.split(text)
        assertThat(chapters).hasSize(2)
        assertThat(chapters[1].title).contains("Chapter 2")
    }

    @Test
    fun split_emptyText_returnsEmpty() {
        assertThat(ChapterSplitter.split("")).isEmpty()
        assertThat(ChapterSplitter.split("   ")).isEmpty()
    }
}
