package com.ainovel.app.domain.agent

import com.ainovel.app.data.repository.NovelRepository
import com.ainovel.app.domain.model.NovelSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 参与方向生成的素材来源配置，用户可在设置页勾选并补充额外要求。
 */
data class DirectionSources(
    val includeRecentChapters: Boolean = true,
    val includeCharacters: Boolean = true,
    val includeStyleProfile: Boolean = true,
    val includePlotSummary: Boolean = true,
    val extraHint: String = ""
)

/**
 * 续写/创作方向推荐器：基于小说既有材料（梗概、人物、手法画像、最近章节）
 * 调用 LLM 生成若干差异化剧情发展方向，供用户在设置页选择。
 */
@Singleton
class DirectionSuggester @Inject constructor(
    private val llm: LlmGateway,
    private val novelRepository: NovelRepository
) {

    /**
     * @param isContinuation true=续写场景（方向承接最近章节结尾），false=创作场景
     * @param sources 参与生成的材料来源与补充要求
     * @return 生成的方向列表（最多 5 条），失败或材料不足时返回空列表
     */
    suspend fun suggest(
        novelId: Long,
        isContinuation: Boolean,
        sources: DirectionSources = DirectionSources()
    ): List<String> {
        val novel = novelRepository.getNovel(novelId) ?: return emptyList()
        val chapters = novelRepository.getChapters(novelId)
        val worldview = novelRepository.getWorldview(novelId)

        val recentContext = chapters.takeLast(3).joinToString("\n\n") {
            "${it.title.ifBlank { "第 ${it.indexInNovel} 章" }}\n${it.content.take(800)}"
        }
        val userMessage = buildString {
            append("请为小说《${novel.title}》${if (isContinuation) "设计续写" else "设计创作"}方向。\n")
            append("书籍类型：${if (novel.source == NovelSource.IMPORTED) "导入小说（需贴合原作者写作手法）" else "原创小说"}。\n\n")
            if (sources.includePlotSummary && !worldview?.plotSummary.isNullOrBlank()) {
                append("【情节梗概】\n${worldview?.plotSummary}\n\n")
            }
            if (sources.includeStyleProfile && !worldview?.styleProfile.isNullOrBlank()) {
                append("【写作手法画像】\n${worldview?.styleProfile}\n\n")
            }
            if (sources.includeCharacters && !worldview?.characters.isNullOrBlank()) {
                append("【主要人物】\n${worldview?.characters?.take(1500)}\n\n")
            }
            if (sources.includeRecentChapters && recentContext.isNotBlank()) {
                append("【最近章节】\n${recentContext}\n\n")
            }
            if (sources.extraHint.isNotBlank()) {
                append("【用户补充要求】\n${sources.extraHint}\n\n")
            }
        }

        val systemPrompt = """
你是资深的小说编辑与剧情策划顾问，擅长基于已有材料设计差异化、可执行的剧情发展方向。
你的职责：
1. 基于提供的小说材料（情节梗概/人物设定/手法画像/最近章节），给出 3 个截然不同的剧情发展方向
2. 每个方向用一句话描述（30-80 字），点明核心冲突、主要推进目标与该方向区别于其他的走向
3. ${if (isContinuation) "续写场景：方向必须紧密承接最近章节的结尾，延续人物状态与已有设定，保持原有写作手法" else "创作场景：方向需围绕既有梗概与大纲展开，不得与已有设定冲突"}
4. 三个方向要在情节走向上明显区分（例如：悬念揭示、冲突升级、引入新势力、主角抉择等）
5. 只输出方向列表，每行一个，格式：1. 方向描述
        """.trimIndent()

        val raw = runCatching {
            llm.complete(systemPrompt, userMessage, 0.8, 800)
        }.getOrDefault("")
        return parseDirections(raw)
    }

    private fun parseDirections(raw: String): List<String> =
        raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                line.replaceFirst(Regex("^\\s*\\d+[.、．:：)]\\s*"), "")
                    .replaceFirst(Regex("^\\s*[-•*]\\s*"), "")
                    .takeIf { it.isNotBlank() }
            }
            .take(5)
            .toList()
}
