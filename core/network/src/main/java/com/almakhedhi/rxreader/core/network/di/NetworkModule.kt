package com.almakhedhi.rxreader.core.network.di

import com.almakhedhi.rxreader.core.network.NetworkJson
import com.almakhedhi.rxreader.core.network.commons.CommonsApi
import com.almakhedhi.rxreader.core.network.dailymed.DailyMedApi
import com.almakhedhi.rxreader.core.network.openfda.OpenFdaApi
import com.almakhedhi.rxreader.core.network.rxnorm.RxNormApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /** Descriptive UA: Wikimedia blocks generic clients; NLM/FDA accept it. */
    const val USER_AGENT = "RxReader/0.1 (Android; contact 7a8b1d@gmail.com)"

    @Provides
    @Singleton
    fun provideJson(): Json = NetworkJson.json

    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .build()
                chain.proceed(request)
            }
            .build()

    private fun retrofit(client: OkHttpClient, json: Json, baseUrl: String): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideRxNorm(client: OkHttpClient, json: Json): RxNormApi =
        retrofit(client, json, "https://rxnav.nlm.nih.gov/REST/")
            .create(RxNormApi::class.java)

    @Provides
    @Singleton
    fun provideOpenFda(client: OkHttpClient, json: Json): OpenFdaApi =
        retrofit(client, json, "https://api.fda.gov/")
            .create(OpenFdaApi::class.java)

    @Provides
    @Singleton
    fun provideDailyMed(client: OkHttpClient, json: Json): DailyMedApi =
        retrofit(client, json, "https://dailymed.nlm.nih.gov/")
            .create(DailyMedApi::class.java)

    @Provides
    @Singleton
    fun provideCommons(client: OkHttpClient, json: Json): CommonsApi =
        retrofit(client, json, "https://commons.wikimedia.org/")
            .create(CommonsApi::class.java)
}
