package com.ainovel.app

import com.ainovel.app.domain.agent.SummaryCompressor
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SummaryCompressorTest {

    private val compressor = SummaryCompressor()

    @Test
    fun compress_emptyContent_returnsPlaceholder() {
        val result = compressor.compress(
            com.ainovel.app.domain.agent.PreviousChapter("第一章", "  ")
        )
        assertThat(result.summary).contains("本章无内容")
    }

    @Test
    fun compress_singleParagraph_containsOpening() {
        val content = "夜色如墨，林渊推开破旧的木门。"
        val result = compressor.compress(
            com.ainovel.app.domain.agent.PreviousChapter("第一章", content)
        )
        assertThat(result.summary).contains("林渊")
    }

    @Test
    fun compress_multiParagraph_includesOpeningAndClosing() {
        val content = """
            夜色如墨，林渊推开破旧的木门。
            屋内烛火摇曳，映出一张苍白的脸。
            门外传来急促的脚步声，他握紧了手中的剑。
        """.trimIndent()
        val result = compressor.compress(
            com.ainovel.app.domain.agent.PreviousChapter("第一章", content)
        )
        assertThat(result.summary).contains("开篇")
        assertThat(result.summary).contains("结尾")
        assertThat(result.summary).contains("林渊")
    }

    @Test
    fun compress_sameChapter_cachedResultIdentical() {
        val chapter = com.ainovel.app.domain.agent.PreviousChapter("第一章", "一段内容。")
        val first = compressor.compress(chapter)
        val second = compressor.compress(chapter)
        assertThat(first.summary).isEqualTo(second.summary)
    }

    @Test
    fun compress_summaryIsShorterThanContent() {
        val content = buildString {
            repeat(50) { append("这是用于测试压缩效果的一长段文字，包含大量冗余信息。") }
        }
        val result = compressor.compress(
            com.ainovel.app.domain.agent.PreviousChapter("第一章", content)
        )
        assertThat(result.summary.length).isLessThan(content.length)
    }
}
