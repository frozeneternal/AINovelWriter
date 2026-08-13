package com.ainovel.app

import com.ainovel.app.domain.agent.detectRefusalResponse
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ContentPolicyGuardTest {

    @Test
    fun detectRefusal_screenshotTypicalRefusal_isDetected() {
        // 图片中出现的实际拒绝话术
        val text = "抱歉，我无法涉足这番构想，也无法生成此类内容。若您另有合乎规范的虚构世界构思，我仍乐于执笔，共赴一座崭新的故事。"
        assertThat(detectRefusalResponse(text)).isNotNull()
    }

    @Test
    fun detectRefusal_explicitRefusalPhrase_isDetected() {
        assertThat(detectRefusalResponse("我无法生成此类内容，请更换话题")).isNotNull()
        assertThat(detectRefusalResponse("我不能提供这方面的创作，抱歉。")).isNotNull()
        assertThat(detectRefusalResponse("I'm sorry, but I cannot generate this content.")).isNotNull()
    }

    @Test
    fun detectRefusal_modelMetaIdentity_isDetected() {
        assertThat(detectRefusalResponse("作为AI助手，我无法协助完成这个请求。")).isNotNull()
        assertThat(detectRefusalResponse("我是一个人工智能，不能创作涉及违规的内容。")).isNotNull()
    }

    @Test
    fun detectRefusal_shortTextWithPolicyTerm_isDetected() {
        assertThat(detectRefusalResponse("抱歉，这个请求违反内容政策，我无法继续。")).isNotNull()
    }

    @Test
    fun detectRefusal_normalLongChapter_notDetected() {
        // 正常长篇小说正文（>200 字）不应被误判为拒绝话术
        val normal = buildString {
            repeat(50) { i ->
                append("他站在城门口，看着远处升起的炊烟。抱歉，我不能回头，因为身后已无退路。\n")
                append("第 $i 段正文内容，描写人物行动与心理活动，长度足够避免误判。\n")
            }
        }
        assertThat(detectRefusalResponse(normal)).isNull()
    }

    @Test
    fun detectRefusal_characterDialogue_shortText_notMisdetected() {
        // 短文本但属于小说内对话（无政策词、无明确拒绝生成短语）不应误判
        assertThat(detectRefusalResponse("抱歉，我不能去那里，你先走吧。")).isNull()
        assertThat(detectRefusalResponse("他遗憾地摇摇头，无法理解她的选择。")).isNull()
    }

    @Test
    fun detectRefusal_blankOrEmpty_returnsNull() {
        assertThat(detectRefusalResponse("")).isNull()
        assertThat(detectRefusalResponse("   ")).isNull()
    }
}
