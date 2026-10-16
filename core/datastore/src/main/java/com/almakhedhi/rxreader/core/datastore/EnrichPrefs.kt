package com.almakhedhi.rxreader.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.enrichStore: DataStore<Preferences> by preferencesDataStore(name = "enrich")

enum class EnrichMode { AUTO, ASK, OFF }

data class EnrichSettings(
    val mode: EnrichMode = EnrichMode.ASK,
    val wifiOnly: Boolean = false,
    val aiDailyCap: Int = DEFAULT_AI_CAP,
    val aiUsedToday: Int = 0,
) {
    val aiRemaining: Int get() = (aiDailyCap - aiUsedToday).coerceAtLeast(0)
}

const val DEFAULT_AI_CAP = 50
const val MAX_AI_CAP = 1000

/** Enrichment preferences + the daily AI quota (PLAN §10: ask-first, Wi-Fi-only, daily cap). */
@Singleton
class EnrichPrefs @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val MODE = stringPreferencesKey("enrich_mode")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val AI_CAP = intPreferencesKey("ai_daily_cap")
        val AI_USED = intPreferencesKey("ai_used_count")
        val AI_DATE = stringPreferencesKey("ai_used_date")
    }

    val settings: Flow<EnrichSettings> =
        context.enrichStore.data.map { prefs ->
            val today = LocalDate.now().toString()
            val used = if (prefs[Keys.AI_DATE] == today) prefs[Keys.AI_USED] ?: 0 else 0
            EnrichSettings(
                mode = prefs[Keys.MODE]?.let {
                    runCatching { EnrichMode.valueOf(it) }.getOrNull()
                } ?: EnrichMode.ASK,
                wifiOnly = prefs[Keys.WIFI_ONLY] ?: false,
                aiDailyCap = (prefs[Keys.AI_CAP] ?: DEFAULT_AI_CAP).coerceIn(0, MAX_AI_CAP),
                aiUsedToday = used,
            )
        }

    suspend fun setMode(mode: EnrichMode) {
        context.enrichStore.edit { it[Keys.MODE] = mode.name }
    }

    suspend fun setWifiOnly(enabled: Boolean) {
        context.enrichStore.edit { it[Keys.WIFI_ONLY] = enabled }
    }

    suspend fun setAiDailyCap(cap: Int) {
        context.enrichStore.edit { it[Keys.AI_CAP] = cap.coerceIn(0, MAX_AI_CAP) }
    }

    /** Atomically consumes [count] AI calls when quota remains (date-aware reset). */
    suspend fun tryConsumeAi(count: Int): Boolean {
        var granted = false
        context.enrichStore.edit { prefs ->
            val today = LocalDate.now().toString()
            val used = if (prefs[Keys.AI_DATE] == today) prefs[Keys.AI_USED] ?: 0 else 0
            val cap = (prefs[Keys.AI_CAP] ?: DEFAULT_AI_CAP).coerceIn(0, MAX_AI_CAP)
            if (used + count <= cap) {
                prefs[Keys.AI_DATE] = today
                prefs[Keys.AI_USED] = used + count
                granted = true
            }
        }
        return granted
    }
}
