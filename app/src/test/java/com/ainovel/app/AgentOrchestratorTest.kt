package com.ainovel.app

import com.ainovel.app.domain.agent.AgentOrchestrator
import com.ainovel.app.domain.agent.CreationSession
import com.ainovel.app.domain.agent.ContextManager
import com.ainovel.app.domain.agent.PipelineEvent
import com.ainovel.app.domain.agent.PipelinePhase
import com.ainovel.app.domain.agent.SummaryCompressor
import com.ainovel.app.domain.model.CreationMode
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AgentOrchestratorTest {

    private fun buildOrchestrator(agentOutputs: Map<String, String>): AgentOrchestrator {
        val fake = FakeLlmGateway()
        fake.completeHandler = { systemPrompt, userMessage, _, _ ->
            when {
                systemPrompt.contains("世界观架构师") -> agentOutputs["worldview"] ?: "## 人物设定\n主角：阿杰\n## 地理设定\n大陆"
                systemPrompt.contains("大纲规划师") -> agentOutputs["outline"] ?: "第 1 章 《开端》"
                systemPrompt.contains("连续性编辑") -> "## 一致性报告\n- 无设定冲突\n\n## 修正后章节\n第 1 章 《开端》\n修正正文"
                systemPrompt.contains("润色编辑") -> agentOutputs["polished"] ?: "第 1 章 《开端》\n润色后的正文"
                systemPrompt.contains("才华横溢的小说章节作者") -> agentOutputs["chapter"] ?: "第 1 章 《开端》\n章节正文内容"
                else -> userMessage
            }
        }
        return AgentOrchestrator(fake, ContextManager(SummaryCompressor()))
    }

    @Test
    fun run_singleChapter_emitsCompletedAndChapterGenerated() = runTest {
        val orchestrator = buildOrchestrator(emptyMap())
        val session = CreationSession(novelId = 1, mode = CreationMode.AUTO)
        val events = orchestrator.run(
            request = com.ainovel.app.domain.agent.PipelineRequest(
                novelId = 1,
                novelTitle = "测试小说",
                genre = "玄幻",
                theme = "少年成长",
                style = "爽文",
                totalChapters = 1,
                mode = CreationMode.AUTO
            ),
            session = session
        ).toList()

        assertThat(events.any { it is PipelineEvent.Completed }).isTrue()
        assertThat(events.any { it is PipelineEvent.ChapterGenerated }).isTrue()

        val chapter = events.filterIsInstance<PipelineEvent.ChapterGenerated>().single()
        assertThat(chapter.chapterIndex).isEqualTo(1)
        assertThat(chapter.title).contains("开端")
        assertThat(session.state.value.phase).isEqualTo(PipelinePhase.COMPLETED)
    }

    @Test
    fun run_multiChapter_emitsAllChapters() = runTest {
        val fake = FakeLlmGateway()
        fake.completeHandler = { systemPrompt, _, _, _ ->
            when {
                systemPrompt.contains("世界观架构师") -> "## 人物设定\n主角：阿杰"
                systemPrompt.contains("大纲规划师") -> "第 1 章 《开端》\n第 2 章 《冲突》"
                systemPrompt.contains("连续性编辑") -> "## 一致性报告\n- 无设定冲突\n\n## 修正后章节\n第 1 章 《开端》\n修正正文"
                systemPrompt.contains("润色编辑") -> "润色正文"
                systemPrompt.contains("才华横溢的小说章节作者") -> "第 1 章 《开端》\n第一回正文"
                else -> ""
            }
        }
        val orchestrator = AgentOrchestrator(fake, ContextManager(SummaryCompressor()))
        val session = CreationSession(novelId = 1, mode = CreationMode.AUTO)
        val events = orchestrator.run(
            request = com.ainovel.app.domain.agent.PipelineRequest(
                novelId = 1,
                novelTitle = "测试",
                genre = "玄幻",
                theme = "成长",
                style = "爽文",
                totalChapters = 2,
                mode = CreationMode.AUTO
            ),
            session = session
        ).toList()

        val chapters = events.filterIsInstance<PipelineEvent.ChapterGenerated>()
        assertThat(chapters).hasSize(2)
    }

    @Test
    fun run_worldviewFailure_emitsError() = runTest {
        val fake = FakeLlmGateway()
        fake.failForSystemPrompt = "世界观架构师"
        val orchestrator = AgentOrchestrator(fake, ContextManager(SummaryCompressor()))
        val session = CreationSession(novelId = 1, mode = CreationMode.AUTO)
        val events = orchestrator.run(
            request = com.ainovel.app.domain.agent.PipelineRequest(
                novelId = 1,
                novelTitle = "测试",
                genre = "玄幻",
                theme = "成长",
                style = "爽文",
                totalChapters = 1,
                mode = CreationMode.AUTO
            ),
            session = session
        ).toList()

        assertThat(events.any { it is PipelineEvent.Error }).isTrue()
        assertThat(session.state.value.phase).isEqualTo(PipelinePhase.FAILED)
    }

    @Test
    fun run_continuityIssues_areReported() = runTest {
        val fake = FakeLlmGateway()
        fake.completeHandler = { systemPrompt, _, _, _ ->
            when {
                systemPrompt.contains("世界观架构师") -> "## 人物设定\n主角：阿杰，年龄16"
                systemPrompt.contains("大纲规划师") -> "第 1 章 《开端》"
                systemPrompt.contains("连续性编辑") -> "## 一致性报告\n- [人物] 主角年龄前后不一致 → 修正为16岁\n\n## 修正后章节\n第 1 章 《开端》\n修正正文"
                systemPrompt.contains("润色编辑") -> "第 1 章 《开端》\n润色正文"
                systemPrompt.contains("才华横溢的小说章节作者") -> "第 1 章 《开端》\n章节正文"
                else -> ""
            }
        }
        val orchestrator = AgentOrchestrator(fake, ContextManager(SummaryCompressor()))
        val session = CreationSession(novelId = 1, mode = CreationMode.AUTO)
        val events = orchestrator.run(
            request = com.ainovel.app.domain.agent.PipelineRequest(
                novelId = 1,
                novelTitle = "测试",
                genre = "玄幻",
                theme = "成长",
                style = "爽文",
                totalChapters = 1,
                mode = CreationMode.AUTO
            ),
            session = session
        ).toList()

        val continuity = events.filterIsInstance<PipelineEvent.ContinuityResult>().single()
        assertThat(continuity.issues).isNotEmpty()
        assertThat(continuity.correctedText).contains("修正正文")
    }

    @Test
    fun run_continuityNoIssues_keepsOriginalChapter() = runTest {
        val fake = FakeLlmGateway()
        fake.completeHandler = { systemPrompt, _, _, _ ->
            when {
                systemPrompt.contains("世界观架构师") -> "## 人物设定\n主角：阿杰，年龄16"
                systemPrompt.contains("大纲规划师") -> "第 1 章 《开端》"
                // 无问题时仅输出简短报告，不输出修正后章节
                systemPrompt.contains("连续性编辑") -> "## 一致性报告\n- 无设定冲突"
                systemPrompt.contains("润色编辑") -> "第 1 章 《开端》\n润色正文"
                systemPrompt.contains("才华横溢的小说章节作者") -> "第 1 章 《开端》\n章节原始正文"
                else -> ""
            }
        }
        val orchestrator = AgentOrchestrator(fake, ContextManager(SummaryCompressor()))
        val session = CreationSession(novelId = 1, mode = CreationMode.AUTO)
        val events = orchestrator.run(
            request = com.ainovel.app.domain.agent.PipelineRequest(
                novelId = 1,
                novelTitle = "测试",
                genre = "玄幻",
                theme = "成长",
                style = "爽文",
                totalChapters = 1,
                mode = CreationMode.AUTO
            ),
            session = session
        ).toList()

        val continuity = events.filterIsInstance<PipelineEvent.ContinuityResult>().single()
        assertThat(continuity.issues).isEmpty()
        // 无修正章节时保留原章节正文，而不是把报告文本当正文
        assertThat(continuity.correctedText).contains("章节原始正文")
        assertThat(continuity.correctedText).doesNotContain("无设定冲突")
        // 润色基于原始正文继续，管线最终完成
        assertThat(events.any { it is PipelineEvent.Completed }).isTrue()
        assertThat(session.state.value.phase).isEqualTo(PipelinePhase.COMPLETED)
    }

    @Test
    fun run_emitsStateChanged_phaseAdvancesBeyondIdle() = runTest {
        val orchestrator = buildOrchestrator(emptyMap())
        val session = CreationSession(novelId = 1, mode = CreationMode.AUTO)
        val events = orchestrator.run(
            request = com.ainovel.app.domain.agent.PipelineRequest(
                novelId = 1,
                novelTitle = "测试小说",
                genre = "玄幻",
                theme = "少年成长",
                style = "爽文",
                totalChapters = 1,
                mode = CreationMode.AUTO
            ),
            session = session
        ).toList()

        val states = events.filterIsInstance<PipelineEvent.StateChanged>().map { it.state.phase }
        assertThat(states).isNotEmpty()
        assertThat(states.first()).isEqualTo(PipelinePhase.WORLDVIEW)
        assertThat(states).contains(PipelinePhase.WRITE_CHAPTER)
        assertThat(states.last()).isEqualTo(PipelinePhase.COMPLETED)
    }

    @Test
    fun run_skipSetup_stateChangedGoesStraightToWriteChapter() = runTest {
        val orchestrator = buildOrchestrator(emptyMap())
        val session = CreationSession(novelId = 1, mode = CreationMode.AUTO)
        val events = orchestrator.run(
            request = com.ainovel.app.domain.agent.PipelineRequest(
                novelId = 1,
                novelTitle = "导入书",
                genre = "续写",
                theme = "",
                style = "",
                totalChapters = 2,
                mode = CreationMode.AUTO,
                startChapterIndex = 2,
                skipSetup = true,
                existingWorldview = "## 人物设定\n主角：阿杰"
            ),
            session = session
        ).toList()

        val states = events.filterIsInstance<PipelineEvent.StateChanged>().map { it.state.phase }
        assertThat(states.first()).isEqualTo(PipelinePhase.WRITE_CHAPTER)
        assertThat(states).contains(PipelinePhase.COMPLETED)
    }

    @Test
    fun run_confirmMode_requiresConfirmBeforeProceeding() = runTest {
        val orchestrator = buildOrchestrator(emptyMap())
        val session = CreationSession(novelId = 1, mode = CreationMode.CONFIRM_STEP)

        // 在独立协程中运行管线，并延时确认
        val job = launch {
            orchestrator.run(
                request = com.ainovel.app.domain.agent.PipelineRequest(
                    novelId = 1,
                    novelTitle = "测试",
                    genre = "玄幻",
                    theme = "成长",
                    style = "爽文",
                    totalChapters = 1,
                    mode = CreationMode.CONFIRM_STEP
                ),
                session = session
            ).collect {}
        }

        // 等待确认请求
        var confirmed = false
        repeat(20) {
            delay(50)
            if (session.state.value.waitingConfirm) {
                session.update { it.copy(waitingConfirm = false) }
                confirmed = true
            }
        }
        job.cancel()
        assertThat(confirmed).isTrue()
    }

    @Test
    fun run_cancellationDuringChapterGeneration_stopsPipeline() = runBlocking {
        val fake = FakeLlmGateway()
        val started = java.util.concurrent.atomic.AtomicBoolean(false)
        fake.completeHandler = { systemPrompt, _, _, _ ->
            when {
                systemPrompt.contains("连续性编辑") -> "## 一致性报告\n- 无设定冲突"
                systemPrompt.contains("润色编辑") -> "第 1 章 《开端》\n润色正文"
                systemPrompt.contains("才华横溢的小说章节作者") -> {
                    started.set(true)
                    Thread.sleep(3000)
                    "第 1 章 《开端》\n章节正文"
                }
                else -> ""
            }
        }
        val orchestrator = AgentOrchestrator(fake, ContextManager(SummaryCompressor()))
        val session = CreationSession(novelId = 1, mode = CreationMode.AUTO)
        val events = mutableListOf<PipelineEvent>()
        // 管线跑在真实线程（Fake 用 Thread.sleep 阻塞），测试体轮询 flag 后取消
        val job = launch(kotlinx.coroutines.Dispatchers.Default) {
            orchestrator.run(
                request = com.ainovel.app.domain.agent.PipelineRequest(
                    novelId = 1,
                    novelTitle = "测试",
                    genre = "玄幻",
                    theme = "",
                    style = "",
                    totalChapters = 2,
                    mode = CreationMode.AUTO
                ),
                session = session
            ).collect { events += it }
        }

        kotlinx.coroutines.withTimeout(5000) {
            while (!started.get()) delay(50)
        }
        job.cancel()
        kotlinx.coroutines.withTimeout(5000) { job.join() }

        // 取消后不应产出任何章节，phase 不应进入 COMPLETED
        assertThat(events.filterIsInstance<PipelineEvent.ChapterGenerated>()).isEmpty()
        assertThat(events.filterIsInstance<PipelineEvent.Completed>()).isEmpty()
    }

    @Test
    fun run_skipSetup_usesExistingWorldviewAndStyleProfile() = runTest {
        val fake = FakeLlmGateway()
        fake.completeHandler = { systemPrompt, userMessage, _, _ ->
            when {
                systemPrompt.contains("提取世界观") -> "worldview-called"
                systemPrompt.contains("大纲") -> "outline-called"
                else -> "第 2 章 《后续》\n续写正文内容"
            }
        }
        val orchestrator = AgentOrchestrator(fake, ContextManager(SummaryCompressor()))
        val session = CreationSession(novelId = 1, mode = CreationMode.AUTO)
        val events = orchestrator.run(
            request = com.ainovel.app.domain.agent.PipelineRequest(
                novelId = 1,
                novelTitle = "导入书",
                genre = "续写",
                theme = "",
                style = "",
                totalChapters = 2,
                mode = CreationMode.AUTO,
                startChapterIndex = 2,
                styleProfile = "叙事视角：第一人称",
                plotSummary = "第 2 章 《后续》",
                skipSetup = true,
                existingWorldview = "## 人物设定\n主角：阿杰",
                existingChapters = listOf(
                    com.ainovel.app.domain.agent.PreviousChapter("第 1 章 开端", "旧正文")
                )
            ),
            session = session
        ).toList()

        assertThat(events.any { it is PipelineEvent.ChapterGenerated }).isTrue()
        val chapters = events.filterIsInstance<PipelineEvent.ChapterGenerated>()
        assertThat(chapters).hasSize(1)
        assertThat(chapters[0].chapterIndex).isEqualTo(2)
        // skipSetup 模式不应调用世界观架构师/大纲规划师
        assertThat(events.filterIsInstance<PipelineEvent.AgentStarted>()
            .none { it.agent.id == "worldview-architect" }).isTrue()
        assertThat(events.filterIsInstance<PipelineEvent.AgentStarted>()
            .none { it.agent.id == "outline-planner" }).isTrue()
    }

    @Test
    fun run_styleProfileInjectedIntoChapterAuthorSystemPrompt() = runTest {
        val fake = FakeLlmGateway()
        fake.completeHandler = { systemPrompt, _, _, _ ->
            when {
                systemPrompt.contains("连续性编辑") -> "## 一致性报告\n- 无设定冲突"
                systemPrompt.contains("润色编辑") -> "第 2 章 《后续》\n润色正文"
                systemPrompt.contains("才华横溢的小说章节作者") -> "第 2 章 《后续》\n续写正文内容"
                else -> ""
            }
        }
        val orchestrator = AgentOrchestrator(fake, ContextManager(SummaryCompressor()))
        val session = CreationSession(novelId = 1, mode = CreationMode.AUTO)
        val styleProfile = "叙事视角：第一人称\n## 风格样本\n他望着窗外，沉默不语。"
        val events = orchestrator.run(
            request = com.ainovel.app.domain.agent.PipelineRequest(
                novelId = 1,
                novelTitle = "导入书",
                genre = "续写",
                theme = "",
                style = "",
                totalChapters = 2,
                mode = CreationMode.AUTO,
                startChapterIndex = 2,
                styleProfile = styleProfile,
                plotSummary = "第 2 章 《后续》",
                skipSetup = true,
                existingWorldview = "## 人物设定\n主角：阿杰",
                existingChapters = listOf(
                    com.ainovel.app.domain.agent.PreviousChapter("第 1 章 开端", "旧正文")
                )
            ),
            session = session
        ).toList()

        assertThat(events.any { it is PipelineEvent.ChapterGenerated }).isTrue()
        // 画像与风格样本必须注入章节作者 systemPrompt，且润色也保留画像
        assertThat(fake.recordedSystemPrompts.any { it.contains("续写模式") }).isTrue()
        assertThat(fake.recordedSystemPrompts.any { it.contains("他望着窗外，沉默不语。") }).isTrue()
        assertThat(fake.recordedUserMessages.any { it.contains("手法模仿") }).isTrue()
    }

    @Test
    fun run_skipSetup_singleNewChapter_emitsCompleted() = runTest {
        val orchestrator = buildOrchestrator(emptyMap())
        val session = CreationSession(novelId = 1, mode = CreationMode.AUTO)
        val events = orchestrator.run(
            request = com.ainovel.app.domain.agent.PipelineRequest(
                novelId = 1,
                novelTitle = "导入书",
                genre = "续写",
                theme = "",
                style = "",
                totalChapters = 3,
                mode = CreationMode.AUTO,
                startChapterIndex = 3,
                skipSetup = true,
                existingWorldview = "## 人物设定\n主角：阿杰"
            ),
            session = session
        ).toList()

        assertThat(events.any { it is PipelineEvent.ChapterGenerated }).isTrue()
        assertThat(events.any { it is PipelineEvent.Completed }).isTrue()
    }

    @Test
    fun run_contentPolicyViolationOnChapter_retriesWithComplianceHint() = runTest {
        val fake = FakeLlmGateway()
        fake.completeHandler = { systemPrompt, userMessage, _, _ ->
            when {
                systemPrompt.contains("世界观架构师") -> "## 人物设定\n主角：阿杰"
                systemPrompt.contains("大纲规划师") -> "第 1 章 《开端》"
                systemPrompt.contains("连续性编辑") -> "## 一致性报告\n- 无设定冲突\n\n## 修正后章节\n第 1 章 《开端》\n修正正文"
                systemPrompt.contains("润色编辑") -> "第 1 章 《开端》\n润色正文"
                systemPrompt.contains("才华横溢的小说章节作者") -> "第 1 章 《开端》\n章节正文内容"
                else -> ""
            }
        }
        // 章节作者第一次触发内容违规，重试（追加合规指令后）成功
        fake.contentPolicyFailForSystemPrompt = "章节作者"
        fake.contentPolicyFailRemaining = 1
        val orchestrator = AgentOrchestrator(fake, ContextManager(SummaryCompressor()))
        val session = CreationSession(novelId = 1, mode = CreationMode.AUTO)
        val events = orchestrator.run(
            request = com.ainovel.app.domain.agent.PipelineRequest(
                novelId = 1,
                novelTitle = "测试",
                genre = "玄幻",
                theme = "成长",
                style = "爽文",
                totalChapters = 1,
                mode = CreationMode.AUTO
            ),
            session = session
        ).toList()

        // 违规后自动重试成功，管线最终完成
        assertThat(events.any { it is PipelineEvent.Completed }).isTrue()
        assertThat(events.any { it is PipelineEvent.Error }).isFalse()
        assertThat(session.state.value.phase).isEqualTo(PipelinePhase.COMPLETED)
        // 违规已消费一次，说明发生了重试
        assertThat(fake.contentPolicyFailRemaining).isEqualTo(0)
        // 重试时的用户消息应包含合规指令
        assertThat(fake.recordedUserMessages.any { it.contains("【内容合规要求】") }).isTrue()
    }

    @Test
    fun run_contentPolicyViolationWorldview_retriesAndCompletes() = runTest {
        val fake = FakeLlmGateway()
        fake.completeHandler = { systemPrompt, userMessage, _, _ ->
            when {
                systemPrompt.contains("世界观架构师") -> "## 人物设定\n主角：阿杰"
                systemPrompt.contains("大纲规划师") -> "第 1 章 《开端》"
                systemPrompt.contains("连续性编辑") -> "## 一致性报告\n- 无设定冲突\n\n## 修正后章节\n第 1 章 《开端》\n修正正文"
                systemPrompt.contains("润色编辑") -> "第 1 章 《开端》\n润色正文"
                systemPrompt.contains("才华横溢的小说章节作者") -> "第 1 章 《开端》\n章节正文内容"
                else -> ""
            }
        }
        // 世界观第一次触发内容违规，重试后成功
        fake.contentPolicyFailForSystemPrompt = "世界观架构师"
        fake.contentPolicyFailRemaining = 1
        val orchestrator = AgentOrchestrator(fake, ContextManager(SummaryCompressor()))
        val session = CreationSession(novelId = 1, mode = CreationMode.AUTO)
        val events = orchestrator.run(
            request = com.ainovel.app.domain.agent.PipelineRequest(
                novelId = 1,
                novelTitle = "测试",
                genre = "玄幻",
                theme = "成长",
                style = "爽文",
                totalChapters = 1,
                mode = CreationMode.AUTO
            ),
            session = session
        ).toList()

        assertThat(events.any { it is PipelineEvent.Completed }).isTrue()
        assertThat(events.any { it is PipelineEvent.Error }).isFalse()
        assertThat(session.state.value.phase).isEqualTo(PipelinePhase.COMPLETED)
    }

    @Test
    fun run_contentPolicyViolationRetriesExhausted_fails() = runTest {
        val fake = FakeLlmGateway()
        fake.completeHandler = { systemPrompt, userMessage, _, _ ->
            when {
                systemPrompt.contains("世界观架构师") -> "## 人物设定\n主角：阿杰"
                systemPrompt.contains("大纲规划师") -> "第 1 章 《开端》"
                systemPrompt.contains("连续性编辑") -> "## 一致性报告\n- 无设定冲突\n\n## 修正后章节\n第 1 章 《开端》\n修正正文"
                systemPrompt.contains("润色编辑") -> "第 1 章 《开端》\n润色正文"
                systemPrompt.contains("才华横溢的小说章节作者") -> "第 1 章 《开端》\n章节正文内容"
                else -> ""
            }
        }
        // 章节作者始终触发内容违规：首试 + 重试 2 次耗尽后失败
        fake.contentPolicyFailForSystemPrompt = "章节作者"
        fake.contentPolicyFailRemaining = 3
        val orchestrator = AgentOrchestrator(fake, ContextManager(SummaryCompressor()))
        val session = CreationSession(novelId = 1, mode = CreationMode.AUTO)
        val events = orchestrator.run(
            request = com.ainovel.app.domain.agent.PipelineRequest(
                novelId = 1,
                novelTitle = "测试",
                genre = "玄幻",
                theme = "成长",
                style = "爽文",
                totalChapters = 1,
                mode = CreationMode.AUTO
            ),
            session = session
        ).toList()

        assertThat(events.any { it is PipelineEvent.Error }).isTrue()
        assertThat(session.state.value.phase).isEqualTo(PipelinePhase.FAILED)
    }

    @Test
    fun run_contentPolicyViolationContinuity_retriesThenFallsBack() = runTest {
        val fake = FakeLlmGateway()
        fake.completeHandler = { systemPrompt, userMessage, _, _ ->
            when {
                systemPrompt.contains("世界观架构师") -> "## 人物设定\n主角：阿杰"
                systemPrompt.contains("大纲规划师") -> "第 1 章 《开端》"
                systemPrompt.contains("连续性编辑") -> "## 一致性报告\n- 无设定冲突\n\n## 修正后章节\n第 1 章 《开端》\n修正正文"
                systemPrompt.contains("润色编辑") -> "第 1 章 《开端》\n润色正文"
                systemPrompt.contains("才华横溢的小说章节作者") -> "第 1 章 《开端》\n章节正文内容"
                else -> ""
            }
        }
        // 连续性编辑首次触发内容违规：重试成功（连续性校验本就有降级路径）
        fake.contentPolicyFailForSystemPrompt = "连续性编辑"
        fake.contentPolicyFailRemaining = 1
        val orchestrator = AgentOrchestrator(fake, ContextManager(SummaryCompressor()))
        val session = CreationSession(novelId = 1, mode = CreationMode.AUTO)
        val events = orchestrator.run(
            request = com.ainovel.app.domain.agent.PipelineRequest(
                novelId = 1,
                novelTitle = "测试",
                genre = "玄幻",
                theme = "成长",
                style = "爽文",
                totalChapters = 1,
                mode = CreationMode.AUTO
            ),
            session = session
        ).toList()

        assertThat(events.any { it is PipelineEvent.Completed }).isTrue()
        assertThat(session.state.value.phase).isEqualTo(PipelinePhase.COMPLETED)
    }

    @Test
    fun run_chapterWordCountSpecified_injectsWordCountIntoChapterSystemPrompt() = runTest {
        val fake = FakeLlmGateway()
        fake.completeHandler = { systemPrompt, userMessage, _, _ ->
            when {
                systemPrompt.contains("世界观架构师") -> "## 人物设定\n主角：阿杰"
                systemPrompt.contains("大纲规划师") -> "第 1 章 《开端》"
                systemPrompt.contains("连续性编辑") -> "## 一致性报告\n- 无设定冲突\n\n## 修正后章节\n第 1 章 《开端》\n修正正文"
                systemPrompt.contains("润色编辑") -> "第 1 章 《开端》\n润色正文"
                systemPrompt.contains("才华横溢的小说章节作者") -> "第 1 章 《开端》\n章节正文内容"
                else -> ""
            }
        }
        val orchestrator = AgentOrchestrator(fake, ContextManager(SummaryCompressor()))
        val session = CreationSession(novelId = 1, mode = CreationMode.AUTO)
        orchestrator.run(
            request = com.ainovel.app.domain.agent.PipelineRequest(
                novelId = 1,
                novelTitle = "测试",
                genre = "玄幻",
                theme = "成长",
                style = "爽文",
                totalChapters = 1,
                mode = CreationMode.AUTO,
                chapterWordCount = 2000
            ),
            session = session
        ).toList()

        val chapterPrompt = fake.recordedSystemPrompts.first { it.contains("才华横溢的小说章节作者") }
        assertThat(chapterPrompt).contains("【本章字数要求】")
        assertThat(chapterPrompt).contains("2000 字")
        assertThat(chapterPrompt).contains("不得少于 1000 字")
    }

    @Test
    fun run_withoutChapterWordCount_usesMinimumWordCountHint() = runTest {
        val fake = FakeLlmGateway()
        fake.completeHandler = { systemPrompt, userMessage, _, _ ->
            when {
                systemPrompt.contains("世界观架构师") -> "## 人物设定\n主角：阿杰"
                systemPrompt.contains("大纲规划师") -> "第 1 章 《开端》"
                systemPrompt.contains("连续性编辑") -> "## 一致性报告\n- 无设定冲突\n\n## 修正后章节\n第 1 章 《开端》\n修正正文"
                systemPrompt.contains("润色编辑") -> "第 1 章 《开端》\n润色正文"
                systemPrompt.contains("才华横溢的小说章节作者") -> "第 1 章 《开端》\n章节正文内容"
                else -> ""
            }
        }
        val orchestrator = AgentOrchestrator(fake, ContextManager(SummaryCompressor()))
        val session = CreationSession(novelId = 1, mode = CreationMode.AUTO)
        orchestrator.run(
            request = com.ainovel.app.domain.agent.PipelineRequest(
                novelId = 1,
                novelTitle = "测试",
                genre = "玄幻",
                theme = "成长",
                style = "爽文",
                totalChapters = 1,
                mode = CreationMode.AUTO
            ),
            session = session
        ).toList()

        val chapterPrompt = fake.recordedSystemPrompts.first { it.contains("才华横溢的小说章节作者") }
        assertThat(chapterPrompt).contains("【本章字数要求】")
        assertThat(chapterPrompt).contains("不得少于 1000 字")
        assertThat(chapterPrompt).doesNotContain("2000 字")
        assertThat(chapterPrompt).doesNotContain("目标字数：2000")
    }

    @Test
    fun run_polishEditor_streamsTokens() = runTest {
        val fake = FakeLlmGateway()
        fake.completeHandler = { systemPrompt, userMessage, _, _ ->
            when {
                systemPrompt.contains("世界观架构师") -> "## 人物设定\n主角：阿杰"
                systemPrompt.contains("大纲规划师") -> "第 1 章 《开端》"
                systemPrompt.contains("连续性编辑") -> "## 一致性报告\n- 无设定冲突\n\n## 修正后章节\n第 1 章 《开端》\n修正正文"
                systemPrompt.contains("润色编辑") -> "第 1 章 《开端》\n润色后的正文"
                systemPrompt.contains("才华横溢的小说章节作者") -> "第 1 章 《开端》\n章节正文内容"
                else -> ""
            }
        }
        val orchestrator = AgentOrchestrator(fake, ContextManager(SummaryCompressor()))
        val session = CreationSession(novelId = 1, mode = CreationMode.AUTO)
        val events = orchestrator.run(
            request = com.ainovel.app.domain.agent.PipelineRequest(
                novelId = 1,
                novelTitle = "测试",
                genre = "玄幻",
                theme = "成长",
                style = "爽文",
                totalChapters = 1,
                mode = CreationMode.AUTO
            ),
            session = session
        ).toList()

        val tokens = events.filterIsInstance<PipelineEvent.Token>()
        assertThat(tokens).isNotEmpty()
        val chapter = events.filterIsInstance<PipelineEvent.ChapterGenerated>().single()
        assertThat(chapter.content).contains("润色后的正文")
    }
}
