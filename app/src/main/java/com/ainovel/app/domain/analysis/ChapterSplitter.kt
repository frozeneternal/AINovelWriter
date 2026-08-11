package com.ainovel.app.domain.analysis

data class SplitChapter(val title: String, val content: String)

/**
 * 规则式章节切分，不消耗 token。支持中英文多种章节标题格式。
 */
object ChapterSplitter {

    private val patterns = listOf(
        Regex("""^\s*第[0-9一二三四五六七八九十百千零〇两]+[章节回卷部篇]\s*\S*"""),
        Regex("""^\s*(Chapter|CHAPTER|chapter)\s+[0-9IVXLCivxlc]+\s*\S*"""),
        Regex("""^\s*(卷一|卷二|卷三|卷四|卷五|卷六|卷七|卷八|卷九|卷十)\s*\S*"""),
        Regex("""^\s*(楔子|序章|尾声|番外|后记|完结篇)\s*\S*""")
    )

    fun split(fullText: String): List<SplitChapter> {
        if (fullText.isBlank()) return emptyList()
        val lines = fullText.split("\n")
        val chapterStartLines = mutableListOf<Pair<Int, String>>()

        lines.forEachIndexed { index, line ->
            if (patterns.any { it.containsMatchIn(line) }) {
                chapterStartLines += index to line.trim()
            }
        }

        if (chapterStartLines.isEmpty()) {
            return listOf(SplitChapter("全文", fullText.trim()))
        }

        val result = mutableListOf<SplitChapter>()
        chapterStartLines.forEachIndexed { i, (lineIdx, title) ->
            val contentEnd = if (i + 1 < chapterStartLines.size) chapterStartLines[i + 1].first else lines.size
            val content = lines.subList(lineIdx + 1, contentEnd).joinToString("\n").trim()
            result += SplitChapter(title, content)
        }
        return result
    }
}
