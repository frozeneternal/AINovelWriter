package com.ainovel.app.di

import com.ainovel.app.data.remote.LlmClient
import com.ainovel.app.data.remote.MediaClient
import com.ainovel.app.data.repository.AnalysisPersistenceImpl
import com.ainovel.app.data.repository.SettingRepository
import com.ainovel.app.domain.agent.AgentOrchestrator
import com.ainovel.app.domain.agent.ContextManager
import com.ainovel.app.domain.agent.LlmGateway
import com.ainovel.app.domain.agent.SummaryCompressor
import com.ainovel.app.domain.analysis.AnalysisPersistence
import com.ainovel.app.domain.analysis.NovelAnalyzer
import com.ainovel.app.domain.model.ConfigProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    @Provides
    @Singleton
    fun provideConfigProvider(settingRepository: SettingRepository): ConfigProvider {
        return ConfigProvider { settingRepository.getConfig() }
    }

    @Provides
    @Singleton
    fun provideLlmGateway(client: OkHttpClient, configProvider: ConfigProvider): LlmGateway {
        return LlmClient(client, configProvider)
    }

    @Provides
    @Singleton
    fun provideMediaClient(client: OkHttpClient, configProvider: ConfigProvider): MediaClient {
        return MediaClient(client, configProvider)
    }

    @Provides
    @Singleton
    fun provideSummaryCompressor(): SummaryCompressor = SummaryCompressor()

    @Provides
    @Singleton
    fun provideContextManager(compressor: SummaryCompressor): ContextManager = ContextManager(compressor)

    @Provides
    @Singleton
    fun provideAgentOrchestrator(
        llm: LlmGateway,
        contextManager: ContextManager
    ): AgentOrchestrator = AgentOrchestrator(llm, contextManager)

    @Provides
    @Singleton
    fun provideAnalysisPersistence(
        impl: AnalysisPersistenceImpl
    ): AnalysisPersistence = impl

    @Provides
    @Singleton
    fun provideNovelAnalyzer(
        llm: LlmGateway,
        persistence: AnalysisPersistence
    ): NovelAnalyzer = NovelAnalyzer(llm, persistence)
}
