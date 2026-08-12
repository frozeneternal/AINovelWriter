package com.ainovel.app

import com.ainovel.app.domain.agent.ContextManager
import com.ainovel.app.domain.agent.PreviousChapter
import com.ainovel.app.domain.agent.SummaryCompressor
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ContextManagerTest {

    private val compressor = SummaryCompressor()
    private val manager = ContextManager(compressor, recentChaptersInContext = 3)

    @Test
    fun buildChapterContext_recentChaptersIncluded() = runTest {
        val previous = listOf(
            PreviousChapter("第一章", "第一章正文"),
            PreviousChapter("第二章", "第二章正文"),
            PreviousChapter("第三章", "第三章正文"),
            PreviousChapter("第四章", "第四章正文")
        )
        val context = manager.buildChapterContext(
            novelTitle = "测试",
            worldview = "世界设定",
            outline = "大纲",
            previousChapters = previous,
            chapterTitle = "第五章"
        )
        val prompt = context.toUserPrompt()
        assertThat(prompt).contains("第四章")
        assertThat(prompt).contains("第五章")
        // 最早一章被压缩为摘要
        assertThat(prompt).contains("前文摘要")
    }

    @Test
    fun buildChapterContext_noPreviousChapters_omitsSections() = runTest {
        val context = manager.buildChapterContext(
            novelTitle = "测试",
            worldview = "世界设定",
            outline = "大纲",
            previousChapters = emptyList(),
            chapterTitle = "第一章"
        )
        val prompt = context.toUserPrompt()
        assertThat(prompt).contains("世界设定")
        assertThat(prompt).doesNotContain("【前文】")
    }

    @Test
    fun estimateTokenCount_scalesWithLength() {
        val short = manager.estimateTokenCount("你好")
        val long = manager.estimateTokenCount("你好".repeat(100))
        assertThat(long).isGreaterThan(short)
    }

    @Test
    fun buildChapterContext_withDirection_injectsDirectionSection() = runTest {
        val context = manager.buildChapterContext(
            novelTitle = "测试",
            worldview = "世界设定",
            outline = "大纲",
            previousChapters = listOf(PreviousChapter("第一章", "第一章正文")),
            chapterTitle = "第二章",
            styleProfile = "第一人称",
            continuationDirection = "主角解开身世之谜后向帝都进发"
        )
        val prompt = context.toUserPrompt()
        assertThat(prompt).contains("【剧情发展方向】")
        assertThat(prompt).contains("主角解开身世之谜后向帝都进发")
        // 续写要求仍然保留
        assertThat(prompt).contains("续写要求")
    }

    @Test
    fun buildChapterContext_withoutDirection_omitsDirectionSection() = runTest {
        val context = manager.buildChapterContext(
            novelTitle = "测试",
            worldview = "世界设定",
            outline = "大纲",
            previousChapters = listOf(PreviousChapter("第一章", "第一章正文")),
            chapterTitle = "第二章",
            styleProfile = "第一人称"
        )
        val prompt = context.toUserPrompt()
        assertThat(prompt).doesNotContain("【剧情发展方向】")
        assertThat(prompt).contains("续写要求")
    }
}
