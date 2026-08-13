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
    fun split_longPlainText_withParagraphs_splitsIntoChapters() {
        // 无章节标记但字数超阈值：按段落切分为多章，避免整本一章
        val longParagraph = "这是没有章节标记的长篇正文段落。" + "文字内容填充。".repeat(300)
        val text = (1..5).joinToString("\n\n") { "$longParagraph 第 $it 段" }
        val chapters = ChapterSplitter.split(text)
        assertThat(chapters.size).isGreaterThan(1)
        assertThat(chapters.first().title).startsWith("第 ")
        assertThat(chapters.last().title).startsWith("第 ")
        // 所有段落内容都保留，不丢字
        val joined = chapters.joinToString { it.content }
        assertThat(joined).contains("第 1 段")
        assertThat(joined).contains("第 5 段")
    }

    @Test
    fun split_longPlainText_withoutParagraphs_splitsByChunks() {
        // 无章节标记、无空行段落的长文：按字数切块
        val text = "超长正文" + "内容".repeat(4000)
        val chapters = ChapterSplitter.split(text)
        assertThat(chapters.size).isGreaterThan(1)
        assertThat(chapters.first().title).startsWith("第 ")
    }

    @Test
    fun split_oversizedChapter_splitsIntoSegments() {
        // 有标题但章节内容过长：继续细分，主标题保留，后续追加（2）（3）序号
        val hugeChapter = "第一段" + ("这是一大段内容。".repeat(1500))
        val text = """
            第一章 开端
            $hugeChapter
        """.trimIndent()
        val chapters = ChapterSplitter.split(text)
        assertThat(chapters.size).isGreaterThan(1)
        assertThat(chapters[0].title).contains("第一章")
        assertThat(chapters[1].title).contains("第一章")
        assertThat(chapters[1].title).contains("（2）")
    }

    @Test
    fun split_emptyText_returnsEmpty() {
        assertThat(ChapterSplitter.split("")).isEmpty()
        assertThat(ChapterSplitter.split("   ")).isEmpty()
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
}
