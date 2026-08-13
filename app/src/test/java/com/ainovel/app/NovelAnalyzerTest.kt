package com.ainovel.app

import com.ainovel.app.domain.agent.ContextManager
import com.ainovel.app.domain.agent.SummaryCompressor
import com.ainovel.app.domain.analysis.AnalysisEvent
import com.ainovel.app.domain.analysis.AnalysisPersistence
import com.ainovel.app.domain.analysis.AnalysisPhase
import com.ainovel.app.domain.analysis.AnalysisSession
import com.ainovel.app.domain.analysis.NovelAnalyzer
import com.ainovel.app.domain.analysis.SplitChapter
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test

class NovelAnalyzerTest {

    private class FakePersistence : AnalysisPersistence {
        val savedChapters = mutableListOf<SplitChapter>()
        var characters = ""
        var worldview = ""
        var plot = ""
        var style = ""

        override suspend fun saveChapters(novelId: Long, chapters: List<SplitChapter>) {
            savedChapters.addAll(chapters)
        }

        override suspend fun saveCharacters(novelId: Long, charactersText: String) {
            characters = charactersText
        }

        override suspend fun saveWorldview(novelId: Long, worldviewText: String) {
            worldview = worldviewText
        }

        override suspend fun savePlotAndStyle(novelId: Long, plotSummary: String, styleProfile: String) {
            plot = plotSummary
            style = styleProfile
        }
    }

    private fun buildAnalyzer(
        persistence: FakePersistence,
        fake: FakeLlmGateway
    ): NovelAnalyzer {
        fake.completeHandler = { systemPrompt, userMessage, _, _ ->
            when {
                systemPrompt.contains("提取人物信息") ->
                    "## 人物设定\n### 阿杰\n- 身份：主角\n- 性格：坚毅"
                systemPrompt.contains("提取世界观设定") ->
                    "## 地理设定\n大陆\n## 规则体系\n灵力体系\n## 时间线\n纪元一"
                systemPrompt.contains("情节梗概") || systemPrompt.contains("写作技法") ->
                    "## 情节梗概\n全书主线：少年成长\n第 1 章 开端：主角启程\n\n## 手法画像\n- 叙事视角：第三人称限知\n- 句式节奏：长短句交错"
                else -> userMessage
            }
        }
        return NovelAnalyzer(fake, persistence)
    }

    @Test
    fun analyze_runsAllPhasesAndPersists() = runTest {
        val persistence = FakePersistence()
        val fake = FakeLlmGateway()
        val analyzer = buildAnalyzer(persistence, fake)
        val session = AnalysisSession(1)
        val text = """
            第一章 开端
            主角阿杰出发了。

            第二章 冲突
            阿杰遭遇强敌。
        """.trimIndent()

        val events = analyzer.analyze(
            com.ainovel.app.domain.analysis.AnalysisRequest(1, text),
            session
        ).toList()

        assertThat(events.any { it is AnalysisEvent.Completed }).isTrue()
        assertThat(persistence.savedChapters).hasSize(2)
        assertThat(persistence.characters).contains("阿杰")
        assertThat(persistence.worldview).contains("地理设定")
        assertThat(persistence.plot).contains("少年成长")
        assertThat(persistence.style).contains("第三人称限知")
        assertThat(session.state.value.phase).isEqualTo(AnalysisPhase.COMPLETED)
    }

    @Test
    fun analyze_worldviewFailure_emitsError() = runTest {
        val persistence = FakePersistence()
        val fake = FakeLlmGateway()
        fake.failForSystemPrompt = "提取世界观设定"
        val analyzer = buildAnalyzer(persistence, fake)
        val session = AnalysisSession(1)
        val text = """
            第一章 开端
            正文内容
        """.trimIndent()

        val events = analyzer.analyze(
            com.ainovel.app.domain.analysis.AnalysisRequest(1, text),
            session
        ).toList()

        assertThat(events.any { it is AnalysisEvent.Error }).isTrue()
        assertThat(session.state.value.phase).isEqualTo(AnalysisPhase.FAILED)
        // 前两个阶段产物应已保留
        assertThat(persistence.savedChapters).hasSize(1)
        assertThat(persistence.characters).isNotEmpty()
    }

    @Test
    fun analyze_emptyText_emitsError() = runTest {
        val persistence = FakePersistence()
        val fake = FakeLlmGateway()
        val analyzer = NovelAnalyzer(fake, persistence)
        val session = AnalysisSession(1)
        val events = analyzer.analyze(
            com.ainovel.app.domain.analysis.AnalysisRequest(1, ""),
            session
        ).toList()

        assertThat(events.any { it is AnalysisEvent.Error }).isTrue()
    }

    @Test
    fun analyze_contextManagerStyleProfile_usesFakeSafely() = runTest {
        val cm = ContextManager(SummaryCompressor())
        val ctx = cm.buildChapterContext(
            novelTitle = "书",
            worldview = "设定",
            outline = "大纲",
            previousChapters = emptyList(),
            chapterTitle = "第 1 章",
            plotSummary = "主线：成长",
            styleProfile = "叙事视角：第一人称"
        )
        assertThat(ctx.toUserPrompt()).contains("续写要求")
        assertThat(ctx.toUserPrompt()).contains("第一人称")
        assertThat(ctx.toUserPrompt()).contains("情节梗概")
    }

    @Test
    fun analyze_styleProfileIncludesStyleSamples_persistsFullProfile() = runTest {
        val persistence = FakePersistence()
        val fake = FakeLlmGateway()
        fake.completeHandler = { systemPrompt, _, _, _ ->
            when {
                systemPrompt.contains("提取人物信息") -> "## 人物设定\n### 阿杰\n- 身份：主角"
                systemPrompt.contains("提取世界观设定") -> "## 地理设定\n大陆"
                systemPrompt.contains("情节梗概") || systemPrompt.contains("写作技法") ->
                    "## 情节梗概\n全书主线：少年成长\n\n## 手法画像\n- 叙事视角：第三人称限知\n- 句式节奏：长短句交错\n\n## 风格样本\n（原文1）\n（原文2）"
                else -> ""
            }
        }
        val analyzer = NovelAnalyzer(fake, persistence)
        val session = AnalysisSession(1)
        val events = analyzer.analyze(
            com.ainovel.app.domain.analysis.AnalysisRequest(1, "第一章 开端\n正文"),
            session
        ).toList()

        assertThat(events.any { it is AnalysisEvent.Completed }).isTrue()
        assertThat(persistence.plot).contains("少年成长")
        assertThat(persistence.style).contains("第三人称限知")
        assertThat(persistence.style).contains("风格样本")
        assertThat(persistence.style).contains("原文2")
    }

    @Test
    fun splitPlotAndStyle_handlesHeadingVariantsAndReversedOrder() = runTest {
        val persistence = FakePersistence()
        val fake = FakeLlmGateway()
        fake.completeHandler = { systemPrompt, _, _, _ ->
            when {
                systemPrompt.contains("提取人物信息") -> "## 人物设定\n### 阿杰"
                systemPrompt.contains("提取世界观设定") -> "## 地理设定\n大陆"
                systemPrompt.contains("情节梗概") || systemPrompt.contains("写作技法") ->
                    "## 手法画像\n- 叙事视角：第一人称\n\n## 情节梗概\n全书主线：少年成长"
                else -> ""
            }
        }
        val analyzer = NovelAnalyzer(fake, persistence)
        val session = AnalysisSession(1)
        val events = analyzer.analyze(
            com.ainovel.app.domain.analysis.AnalysisRequest(1, "第一章 开端\n正文"),
            session
        ).toList()

        assertThat(events.any { it is AnalysisEvent.Completed }).isTrue()
        // 手法画像在前、情节梗概在后时，两部分都要正确提取
        assertThat(persistence.plot).contains("少年成长")
        assertThat(persistence.style).contains("第一人称")
    }

    @Test
    fun splitPlotAndStyle_headingWithExtraText_stillParses() = runTest {
        val persistence = FakePersistence()
        val fake = FakeLlmGateway()
        fake.completeHandler = { systemPrompt, _, _, _ ->
            when {
                systemPrompt.contains("提取人物信息") -> "## 人物设定\n### 阿杰"
                systemPrompt.contains("提取世界观设定") -> "## 地理设定\n大陆"
                systemPrompt.contains("情节梗概") || systemPrompt.contains("写作技法") ->
                    "### 情节梗概：\n全书主线：少年成长\n\n### 手法画像：\n- 叙事视角：第三人称"
                else -> ""
            }
        }
        val analyzer = NovelAnalyzer(fake, persistence)
        val session = AnalysisSession(1)
        val events = analyzer.analyze(
            com.ainovel.app.domain.analysis.AnalysisRequest(1, "第一章 开端\n正文"),
            session
        ).toList()

        assertThat(events.any { it is AnalysisEvent.Completed }).isTrue()
        assertThat(persistence.plot).contains("少年成长")
        assertThat(persistence.style).contains("第三人称")
    }

    @Test
    fun analyze_longText_splitsIntoBatches_andMergesAllChapters() = runTest {
        val persistence = FakePersistence()
        val fake = FakeLlmGateway()
        // 记录每次调用的 userMessage，验证长文被分批覆盖而非只取前几章
        val characterPrompts = mutableListOf<String>()
        fake.completeHandler = { systemPrompt, userMessage, _, _ ->
            when {
                systemPrompt.contains("提取人物信息") -> {
                    characterPrompts += userMessage
                    val names = """角色\d+""".toRegex()
                        .findAll(userMessage)
                        .map { it.value }
                        .distinct()
                        .joinToString("\n") { "### $it\n- 身份：角色" }
                    "## 人物设定\n$names"
                }
                systemPrompt.contains("提取世界观设定") ->
                    "## 地理设定\n大陆\n## 规则体系\n灵力\n## 时间线\n纪元"
                systemPrompt.contains("情节梗概") || systemPrompt.contains("写作技法") ->
                    "## 情节梗概\n主线片段\n\n## 手法画像\n- 叙事视角：第三人称"
                else -> ""
            }
        }
        val analyzer = NovelAnalyzer(fake, persistence)
        val session = AnalysisSession(1)

        // 构造 20 章长文，远超单批预算（8 章/批）
        val text = (1..20).joinToString("\n\n") { i ->
            "第 $i 章 章节\n这是第 $i 章的人物：角色$i。\n" + "正文内容填充。".repeat(300)
        }

        val events = analyzer.analyze(
            com.ainovel.app.domain.analysis.AnalysisRequest(1, text),
            session
        ).toList()

        assertThat(events.any { it is AnalysisEvent.Completed }).isTrue()
        // 长文被拆成多批，每批不超过 8 章
        assertThat(characterPrompts.size).isGreaterThan(2)
        // 所有章节内容都进入过某批（不丢后半本）
        val allBatches = characterPrompts.joinToString("\n")
        assertThat(allBatches).contains("第 1 章")
        assertThat(allBatches).contains("第 20 章")
        // 合并后的人物去重保留全部章节角色
        assertThat(persistence.characters).contains("角色1")
        assertThat(persistence.characters).contains("角色20")
        // 世界观小节正确合并
        assertThat(persistence.worldview).contains("地理设定")
        assertThat(persistence.worldview).contains("规则体系")
        assertThat(persistence.worldview).contains("时间线")
        assertThat(persistence.plot).contains("主线")
        assertThat(persistence.style).contains("第三人称")
    }

    @Test
    fun analyze_longText_preservesSavedChapterCount() = runTest {
        val persistence = FakePersistence()
        val fake = FakeLlmGateway()
        fake.completeHandler = { systemPrompt, _, _, _ ->
            when {
                systemPrompt.contains("提取人物信息") -> "## 人物设定\n### 阿杰"
                systemPrompt.contains("提取世界观设定") -> "## 地理设定\n大陆"
                systemPrompt.contains("情节梗概") || systemPrompt.contains("写作技法") ->
                    "## 情节梗概\n主线\n\n## 手法画像\n- 叙事视角：第一人称"
                else -> ""
            }
        }
        val analyzer = NovelAnalyzer(fake, persistence)
        val session = AnalysisSession(1)
        val text = (1..30).joinToString("\n\n") { i ->
            "第 $i 章 章节\n这是第 $i 章的正文内容。"
        }

        analyzer.analyze(
            com.ainovel.app.domain.analysis.AnalysisRequest(1, text),
            session
        ).toList()

        assertThat(persistence.savedChapters).hasSize(30)
        assertThat(session.state.value.chapterCount).isEqualTo(30)
    }
}
