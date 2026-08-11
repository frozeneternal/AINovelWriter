package com.ainovel.app.domain.model

data class ModelTemplate(
    val name: String,
    val note: String,
    val textBaseUrl: String,
    val textModel: String,
    val imageBaseUrl: String? = null,
    val imageModel: String? = null
)

object ModelTemplates {

    val cnTemplates = listOf(
        ModelTemplate("DeepSeek", "中文写作强、性价比高", "https://api.deepseek.com/v1", "deepseek-chat"),
        ModelTemplate(
            "智谱 GLM", "文本/图片有免费模型", "https://open.bigmodel.cn/api/paas/v4", "glm-4-flash",
            "https://open.bigmodel.cn/api/paas/v4", "cogview-3-flash"
        ),
        ModelTemplate("Moonshot Kimi", "超长上下文", "https://api.moonshot.cn/v1", "kimi-k2-turbo-preview"),
        ModelTemplate(
            "阿里云 通义千问", "国产大而全", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-plus"
        ),
        ModelTemplate(
            "硅基流动", "开源模型一站式", "https://api.siliconflow.cn/v1", "deepseek-ai/DeepSeek-V3",
            "https://api.siliconflow.cn/v1", "black-forest-labs/FLUX.1-dev"
        ),
        ModelTemplate("百度 文心千帆", "文心旗舰", "https://qianfan.baidubce.com/v2", "ernie-4.0-turbo-8k"),
        ModelTemplate("腾讯 混元", "大厂稳定", "https://api.hunyuan.cloud.tencent.com/v1", "hunyuan-turbo"),
        ModelTemplate("MiniMax 海螺", "MoE 大模型", "https://api.minimaxi.com/v1", "MiniMax-Text-01")
    )

    val intlTemplates = listOf(
        ModelTemplate(
            "OpenAI", "最通用", "https://api.openai.com/v1", "gpt-4o-mini",
            "https://api.openai.com/v1", "dall-e-3"
        ),
        ModelTemplate(
            "Google Gemini", "免费额度友好", "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.0-flash"
        ),
        ModelTemplate("Groq", "极速推理", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile"),
        ModelTemplate("OpenRouter", "聚合数百模型", "https://openrouter.ai/api/v1", "deepseek/deepseek-chat-v3-0324"),
        ModelTemplate("Mistral", "欧洲开源强厂", "https://api.mistral.ai/v1", "mistral-large-latest")
    )
}
