package com.almakhedhi.rxreader.core.network.rxnorm

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** Keyless NLM RxNorm REST endpoints used for name normalization. */
interface RxNormApi {

    @GET("approximateTerm.json")
    suspend fun approximateTerm(
        @Query("term") term: String,
        @Query("maxEntries") maxEntries: Int = 8,
    ): RxApproxResponse

    @GET("rxcui/{rxcui}/properties.json")
    suspend fun properties(
        @Path("rxcui") rxcui: String,
    ): RxPropertiesResponse

    @GET("rxcui/{rxcui}/related.json")
    suspend fun related(
        @Path("rxcui") rxcui: String,
    ): RxRelatedResponse
}
