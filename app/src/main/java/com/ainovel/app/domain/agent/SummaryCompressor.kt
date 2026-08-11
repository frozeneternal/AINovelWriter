package com.ainovel.app.domain.agent

/**
 * 将较早章节压缩为摘要，节省上下文窗口。
 */
class SummaryCompressor {

    private val cache = mutableMapOf<String, String>()

    fun compress(chapter: PreviousChapter): CompressedSummary {
        val key = chapter.title
        cache[key]?.let { return CompressedSummary(chapter.title, it) }

        val summary = summarize(chapter.content)
        cache[key] = summary
        return CompressedSummary(chapter.title, summary)
    }

    fun clear() {
        cache.clear()
    }

    private fun summarize(content: String): String {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return "（本章无内容）"

        val paragraphs = trimmed.split("\n").filter { it.isNotBlank() }
        val firstParagraph = paragraphs.firstOrNull()?.trim() ?: ""
        val lastParagraph = paragraphs.lastOrNull()?.trim() ?: ""

        // 以首尾段构造摘要，兼顾开端事件与章末钩子
        val summary = buildString {
            if (firstParagraph.isNotEmpty()) {
                append("开篇：").append(firstParagraph.take(180))
            }
            if (lastParagraph.isNotEmpty() && lastParagraph != firstParagraph) {
                append("\n结尾：").append(lastParagraph.take(180))
            }
        }
        return if (summary.isBlank()) "（章节内容较短，保留关键信息）" else summary
    }
}

data class CompressedSummary(
    val title: String,
    val summary: String
)
