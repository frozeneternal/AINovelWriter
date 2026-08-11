package com.ainovel.app.domain.model

fun interface ConfigProvider {
    suspend fun get(): ApiConfig
}
