package com.almakhedhi.rxreader.core.network.dailymed

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** Keyless DailyMed v2: official SPL identity + pack images. */
interface DailyMedApi {

    @GET("dailymed/services/v2/spls.json")
    suspend fun splsByRxcui(
        @Query("rxcui") rxcui: String,
        @Query("pagesize") pageSize: Int = 5,
    ): DmSplListResponse

    @GET("dailymed/services/v2/spls.json")
    suspend fun splsByName(
        @Query("drug_name") name: String,
        @Query("pagesize") pageSize: Int = 5,
    ): DmSplListResponse

    @GET("dailymed/services/v2/spls/{setid}/media.json")
    suspend fun media(
        @Path("setid") setid: String,
    ): DmMediaResponse
}
