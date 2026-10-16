package com.almakhedhi.rxreader.di

import com.almakhedhi.rxreader.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named

/**
 * Direct Gemini Developer API key (see DirectGeminiReader). Baked at build
 * time from local.properties / env; empty when absent (CI), in which case
 * only Firebase AI Logic is available.
 */
@Module
@InstallIn(SingletonComponent::class)
object AiKeysModule {

    @Provides
    @Named("gemini-api-key")
    fun provideGeminiApiKey(): String = BuildConfig.GEMINI_API_KEY
}
