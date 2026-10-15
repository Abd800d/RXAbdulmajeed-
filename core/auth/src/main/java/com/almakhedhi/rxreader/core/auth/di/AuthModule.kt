package com.almakhedhi.rxreader.core.auth.di

import com.almakhedhi.rxreader.core.auth.AuthRepository
import com.almakhedhi.rxreader.core.auth.FirebaseAuthRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: FirebaseAuthRepository): AuthRepository
}
