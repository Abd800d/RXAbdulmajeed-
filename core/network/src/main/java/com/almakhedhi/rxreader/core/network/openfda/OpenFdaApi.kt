package com.almakhedhi.rxreader.core.network.openfda

import retrofit2.http.GET
import retrofit2.http.Query

/** Keyless openFDA drug-label endpoint (240 req/min/IP without a key). */
interface OpenFdaApi {

    @GET("drug/label.json")
    suspend fun label(
        @Query("search") search: String,
        @Query("limit") limit: Int = 1,
    ): FdaLabelResponse
}
