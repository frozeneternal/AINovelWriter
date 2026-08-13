package com.ainovel.app.domain.analysis

data class SplitChapter(val title: String, val content: String)

/**
 * 规则式章节切分，不消耗 token。支持中英文多种章节标题格式。
 *
 * 无章节标题的纯文本（或长文）按段落/字数切分为多个章节，避免整本作为一章；
 * 标题章节内容过长时也会继续细分，保证每章长度适中、便于后续分片解析与续写。
 */
object ChapterSplitter {

    private val patterns = listOf(
        Regex("""^\s*第\s*[0-9一二三四五六七八九十百千零〇两]+\s*[章节回卷部篇]\s*\S*"""),
        Regex("""^\s*(Chapter|CHAPTER|chapter)\s+[0-9IVXLCivxlc]+\s*\S*"""),
        Regex("""^\s*(卷一|卷二|卷三|卷四|卷五|卷六|卷七|卷八|卷九|卷十)\s*\S*"""),
        Regex("""^\s*(楔子|序章|尾声|番外|后记|完结篇)\s*\S*""")
    )

    /** 无标题文本按段落/字数切分时每章目标字数 */
    private const val PLAIN_CHAPTER_CHARS = 3000

    /** 单个章节超过该字数时继续细分 */
    private const val MAX_CHAPTER_CHARS = 6000

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
            return splitPlainText(fullText.trim())
        }

        val result = mutableListOf<SplitChapter>()
        chapterStartLines.forEachIndexed { i, (lineIdx, title) ->
            val contentEnd = if (i + 1 < chapterStartLines.size) chapterStartLines[i + 1].first else lines.size
            val content = lines.subList(lineIdx + 1, contentEnd).joinToString("\n").trim()
            result += splitLongChapter(title, content)
        }
        return result
    }

    /** 无标题纯文本：短文本整本一章（保留"全文"标题），长文本按段落/字数切分 */
    private fun splitPlainText(text: String): List<SplitChapter> {
        if (text.length <= PLAIN_CHAPTER_CHARS) {
            return listOf(SplitChapter("全文", text))
        }
        val paragraphs = text.split(Regex("\n\\s*\n")).map { it.trim() }.filter { it.isNotBlank() }
        if (paragraphs.size > 1) {
            return splitByParagraphs(paragraphs)
        }
        return splitByChunks(text)
    }

    private fun splitByParagraphs(paragraphs: List<String>): List<SplitChapter> {
        val result = mutableListOf<SplitChapter>()
        var buffer = StringBuilder()
        var currentLen = 0
        paragraphs.forEach { p ->
            if (buffer.isNotEmpty() && currentLen + p.length > PLAIN_CHAPTER_CHARS) {
                result += SplitChapter("第 ${result.size + 1} 章", buffer.toString().trim())
                buffer = StringBuilder()
                currentLen = 0
            }
            buffer.append(p).append("\n\n")
            currentLen += p.length
        }
        if (buffer.isNotBlank()) {
            result += SplitChapter("第 ${result.size + 1} 章", buffer.toString().trim())
        }
        return result
    }

    /** 无空行段落时按字数切块 */
    private fun splitByChunks(text: String): List<SplitChapter> {
        val chunks = text.chunked(PLAIN_CHAPTER_CHARS).filter { it.isNotBlank() }
        if (chunks.isEmpty()) return emptyList()
        return chunks.mapIndexed { i, c ->
            SplitChapter("第 ${i + 1} 章", c.trim())
        }
    }

    /** 标题章节过长时按段落/字数继续细分，保持主标题并追加分卷序号 */
    private fun splitLongChapter(title: String, content: String): List<SplitChapter> {
        if (content.length <= MAX_CHAPTER_CHARS) {
            return listOf(SplitChapter(title, content))
        }
        val paragraphs = content.split(Regex("\n\\s*\n")).map { it.trim() }.filter { it.isNotBlank() }
        val parts = if (paragraphs.size > 1) {
            splitParagraphsIntoParts(paragraphs)
        } else {
            content.chunked(MAX_CHAPTER_CHARS).filter { it.isNotBlank() }
        }
        return parts.mapIndexed { i, part ->
            if (i == 0) SplitChapter(title, part) else SplitChapter("$title（${i + 1}）", part)
        }
    }

    private fun splitParagraphsIntoParts(paragraphs: List<String>): List<String> {
        val parts = mutableListOf<String>()
        var buffer = StringBuilder()
        var currentLen = 0
        paragraphs.forEach { p ->
            if (buffer.isNotEmpty() && currentLen + p.length > MAX_CHAPTER_CHARS) {
                parts += buffer.toString().trim()
                buffer = StringBuilder()
                currentLen = 0
            }
            buffer.append(p).append("\n\n")
            currentLen += p.length
        }
        if (buffer.isNotBlank()) parts += buffer.toString().trim()
        return parts
    }
}
