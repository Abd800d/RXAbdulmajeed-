package com.almakhedhi.rxreader.di

import com.almakhedhi.rxreader.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named

/**
 * Provides the Google (web client) ID used by Credential Manager sign-in.
 * Empty until google-services.json is added — see README.
 */
@Module
@InstallIn(SingletonComponent::class)
object AuthConfigModule {

    @Provides
    @Named("google-web-client-id")
    fun provideGoogleWebClientId(): String = BuildConfig.GOOGLE_WEB_CLIENT_ID
}
