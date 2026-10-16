package com.almakhedhi.rxreader.core.network

import kotlinx.serialization.json.Json

/** Lenient JSON shared by every public API client (all fields optional upstream). */
object NetworkJson {
    val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }
}
