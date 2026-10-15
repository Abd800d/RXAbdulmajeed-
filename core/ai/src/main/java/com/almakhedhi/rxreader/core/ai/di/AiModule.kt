package com.almakhedhi.rxreader.core.ai.di

import com.almakhedhi.rxreader.core.ai.AiReader
import com.almakhedhi.rxreader.core.ai.FirebaseAiReader
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {

    @Binds
    @Singleton
    abstract fun bindAiReader(impl: FirebaseAiReader): AiReader
}
