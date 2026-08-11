package com.ainovel.app

import com.ainovel.app.domain.agent.PromptTemplates
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PromptTemplatesTest {

    @Test
    fun agents_containsAllFiveExpertRoles() {
        val ids = PromptTemplates.agents.map { it.id }
        assertThat(ids).containsExactly(
            "worldview-architect",
            "outline-planner",
            "chapter-author",
            "continuity-editor",
            "polish-editor"
        ).inOrder()
    }

    @Test
    fun agent_returnsDefinitionById() {
        val author = PromptTemplates.agent("chapter-author")
        assertThat(author.name).isEqualTo("章节作者")
        assertThat(author.systemPrompt).contains("世界观设定")
    }

    @Test
    fun buildNovelRequest_includesMetadata() {
        val request = PromptTemplates.buildNovelRequest(
            title = "星火",
            genre = "玄幻",
            theme = "少年成长",
            chapterCount = 20,
            style = "爽文风"
        )
        assertThat(request.content).contains("星火")
        assertThat(request.content).contains("20")
        assertThat(request.content).contains("爽文风")
    }

    @Test
    fun buildChapterRequest_assemblesContextSections() {
        val request = PromptTemplates.buildChapterRequest(
            novelTitle = "星火",
            worldview = "世界观",
            outline = "大纲",
            chapterTitle = "第 3 章 《觉醒》",
            previousContext = "前文内容"
        )
        assertThat(request.content).contains("【世界观设定】")
        assertThat(request.content).contains("【大纲】")
        assertThat(request.content).contains("【本回目标章节】")
        assertThat(request.content).contains("【前文摘要/上文】")
    }
}
