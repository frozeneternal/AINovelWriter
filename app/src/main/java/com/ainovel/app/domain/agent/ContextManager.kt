package com.ainovel.app.domain.agent

/**
 * 组装注入 LLM 的上下文，保证多章创作的前后文连贯性。
 */
class ContextManager(
    private val summaryCompressor: SummaryCompressor,
    private val maxContextTokens: Int = 6000,
    private val recentChaptersInContext: Int = 5
) {

    /**
     * 组装创作章节的上下文。
     *
     * @param worldview 世界观设定原文
     * @param outline 大纲原文
     * @param previousChapters 已完成的章节（按顺序）
     * @param chapterTitle 目标章节标题
     */
    suspend fun buildChapterContext(
        novelTitle: String,
        worldview: String,
        outline: String,
        previousChapters: List<PreviousChapter>,
        chapterTitle: String,
        plotSummary: String = "",
        styleProfile: String = ""
    ): ChapterContext {
        val recent = previousChapters.takeLast(recentChaptersInContext)
        val older = previousChapters.dropLast(recentChaptersInContext)

        val olderText = older
            .map { summaryCompressor.compress(it) }
            .joinToString("\n\n") { "（前文摘要：${it.summary}）" }

        val recentText = recent.joinToString("\n\n---\n\n") { chapter ->
            "${chapter.title}\n${chapter.content}"
        }

        return ChapterContext(
            novelTitle = novelTitle,
            worldview = worldview.take(maxContextTokens),
            outline = outline.take(maxContextTokens / 2),
            olderSummary = olderText,
            recentChapters = recentText,
            chapterTitle = chapterTitle,
            plotSummary = plotSummary.take(maxContextTokens / 3),
            styleProfile = styleProfile
        )
    }

    fun estimateTokenCount(text: String): Int = (text.length / 1.6).toInt()
}

data class PreviousChapter(
    val title: String,
    val content: String
)

data class ChapterContext(
    val novelTitle: String,
    val worldview: String,
    val outline: String,
    val olderSummary: String,
    val recentChapters: String,
    val chapterTitle: String,
    val plotSummary: String = "",
    val styleProfile: String = ""
) {
    fun toUserPrompt(): String = buildString {
        append("书名：《$novelTitle》")
        append("\n\n【世界观设定】\n").append(worldview)
        append("\n\n【大纲】\n").append(outline)
        if (plotSummary.isNotBlank()) append("\n\n【情节梗概】\n").append(plotSummary)
        if (olderSummary.isNotBlank()) append("\n\n").append(olderSummary)
        if (recentChapters.isNotBlank()) append("\n\n【前文】\n").append(recentChapters)
        if (chapterTitle.isNotBlank()) append("\n\n【本回目标章节】\n").append(chapterTitle)
        if (styleProfile.isNotBlank()) {
            append("\n\n【写作手法指令】\n")
            append("以下为原作者的写作手法画像，续写时必须严格模仿，保持风格一致：\n")
            append(styleProfile)
        }
        append("\n\n请根据以上上下文，创作当前章节正文。")
    }
}
