package com.ainovel.app.domain.agent

import java.io.IOException

/**
 * 内容合规违规异常：LLM 返回内容安全策略违规（违禁词/敏感内容）被拒绝时抛出。
 * 调用方可捕获此类异常，在 prompt 中追加合规指令后自动重试。
 */
class ContentPolicyException(message: String) : IOException(message)

/**
 * 检测 LLM 正常返回（HTTP 200）但内容实为"拒绝生成"话术的情况。
 *
 * 部分模型在遇到敏感/违禁内容时不会抛错，而是返回一段礼貌拒绝的元话术
 * （如"抱歉，我无法涉足这番构想，也无法生成此类内容……"），
 * 这类文本若被当作章节正文保存，会直接显示给用户。命中时返回拒绝原因，否则返回 null。
 */
fun detectRefusalResponse(text: String): String? {
    val t = text.trim()
    if (t.isBlank()) return null
    val lower = t.lowercase()

    // 强信号：明确拒绝生成的短语（正常小说正文不会出现"我无法生成/无法涉足"这类说法）
    val refusalPhrases = listOf(
        "无法生成", "不能生成", "无法提供", "不能提供", "无法协助", "无法帮助",
        "无法满足", "无法涉足", "无法创作", "无法撰写", "无法编写", "无法完成",
        "不能创作", "无法继续", "拒绝生成", "无法生成此类",
        "cannot generate", "can't generate", "unable to generate",
        "cannot create", "can't create", "unable to create",
        "cannot provide", "can't provide", "unable to provide",
        "i cannot", "i can't", "i'm unable", "i am unable", "i'm sorry, but i"
    )

    // 强信号：模型元身份自述（正常小说正文不会以"作为AI"自述）
    val metaIdentity = listOf(
        "作为ai", "作为人工智能", "作为一个ai", "我是ai", "我是人工智能",
        "我是一个ai", "我是一个人工智能", "我是个人工智能",
        "ai助手", "人工智能助手", "语言模型", "as an ai", "as a language model"
    )

    // 弱信号：道歉词 + 创作动词类否定/政策词，仅在短文本（<200 字）时判定，
    // 避免误伤长篇幅小说正文中角色的"抱歉，我不能……"式对话
    val apologies = listOf("抱歉", "很抱歉", "对不起", "遗憾", "sorry", "apologize", "apologies")
    val policyTerms = listOf(
        "内容政策", "安全政策", "内容规范", "合规", "内容审核", "安全审查",
        "内容安全", "违反政策", "违反规定", "合乎规范", "违规内容", "内容安全审核",
        "content policy", "safety policy", "content guidelines", "content moderation"
    )
    val creationVerbs = listOf(
        "生成", "创作", "撰写", "编写", "提供", "协助", "涉足", "满足", "完成", "回答", "解决"
    )
    val negations = listOf("无法", "不能", "不便", "难以", "cannot", "can't", "unable")

    if (metaIdentity.any { lower.contains(it) }) return "模型返回拒绝生成话术"
    if (refusalPhrases.any { lower.contains(it) }) return "模型返回拒绝生成话术"

    if (t.length <= 200) {
        // 道歉 + 创作动词类否定（如"抱歉，我无法生成……"）或政策词 → 判定为拒绝话术
        val hasCreationNegation = negations.any { n -> creationVerbs.any { v -> lower.contains(n + v) } }
        val hasApology = apologies.any { lower.contains(it) }
        val hasPolicy = policyTerms.any { lower.contains(it) }
        if (hasApology && hasCreationNegation) return "模型返回拒绝生成话术"
        if (hasPolicy) return "模型返回拒绝生成话术"
    }

    return null
}
