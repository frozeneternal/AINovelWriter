package com.ainovel.app.domain.agent

import java.io.IOException

/**
 * 内容合规违规异常：LLM 返回内容安全策略违规（违禁词/敏感内容）被拒绝时抛出。
 * 调用方可捕获此类异常，在 prompt 中追加合规指令后自动重试。
 */
class ContentPolicyException(message: String) : IOException(message)
