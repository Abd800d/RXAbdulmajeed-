package com.almakhedhi.rxreader.core.network.commons

import retrofit2.http.GET
import retrofit2.http.Query

/** Keyless Wikimedia Commons image search (namespace 6 = files). */
interface CommonsApi {

    @GET("w/api.php")
    suspend fun searchImages(
        @Query("action") action: String = "query",
        @Query("generator") generator: String = "search",
        @Query("gsrsearch") query: String,
        @Query("gsrnamespace") namespace: Int = 6,
        @Query("gsrlimit") limit: Int = 8,
        @Query("prop") prop: String = "imageinfo",
        @Query(value = "iiprop", encoded = true) iiprop: String = "url|size|extmetadata",
        @Query("iiurlwidth") thumbWidth: Int = 800,
        @Query("format") format: String = "json",
    ): CommonsResponse
}
