package com.almakhedhi.rxreader.core.data.repo

import com.almakhedhi.rxreader.core.database.dao.EnrichCacheDao
import com.almakhedhi.rxreader.core.database.entity.EnrichCacheEntity
import com.almakhedhi.rxreader.core.database.entity.ImageKind
import com.almakhedhi.rxreader.core.network.commons.CommonsApi
import com.almakhedhi.rxreader.core.network.dailymed.DailyMedApi
import com.almakhedhi.rxreader.core.network.di.NetworkModule
import com.almakhedhi.rxreader.core.network.openfda.OpenFdaApi
import com.almakhedhi.rxreader.core.network.rxnorm.RxNormApi
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

data class OfficialMatch(
    val rxcui: String?,
    val name: String,
    val score: Double,
    val sources: List<String>,
)

data class OfficialDetails(
    val rxcui: String?,
    val genericName: String?,
    val brandNames: List<String>,
    val substance: String?,
    val route: String?,
    val dosageForm: String?,
    val manufacturer: String?,
    val indications: String?,
    val dosage: String?,
    val contraindications: String?,
    val adverse: String?,
    val interactions: String?,
    val warnings: String?,
    val packaging: String?,
    val labelUrl: String?,
    val setId: String?,
)

data class EnrichImage(
    val url: String,
    val thumbUrl: String?,
    val width: Int,
    val height: Int,
    val license: String?,
    val credit: String?,
    val kind: String,
    val label: String?,
)

/**
 * Keyless official sources (PLAN §4.3/§18): RxNorm for names, openFDA for
 * label sections, DailyMed for the official label + pack images, and
 * Wikimedia Commons as the keyless image fallback. Payloads are cached in
 * [EnrichCacheEntity] for [CACHE_TTL_MS].
 */
@Singleton
class EnrichmentRepository @Inject constructor(
    private val rxNorm: RxNormApi,
    private val openFda: OpenFdaApi,
    private val dailyMed: DailyMedApi,
    private val commons: CommonsApi,
    private val client: OkHttpClient,
    private val cache: EnrichCacheDao,
    private val json: Json,
    private val images: ImageRepository,
) {
    suspend fun searchOfficial(query: String): List<OfficialMatch> {
        val q = query.trim()
        if (q.length < 2) return emptyList()
        val norm = q.lowercase()
        val approx = runCatching {
            cached("rxnorm", "approx:$norm") { rxNorm.approximateTerm(q) }
        }.getOrNull() ?: return emptyList()
        val candidates = approx.approximateGroup?.candidate.orEmpty()
            .filter { !it.rxcui.isNullOrBlank() }
            .sortedByDescending { it.scoreValue() }
            .take(MAX_CANDIDATES)
        if (candidates.isEmpty()) return emptyList()
        return coroutineScope {
            candidates.map { candidate ->
                async {
                    val props = runCatching {
                        cached("rxnorm", "props:${candidate.rxcui}") {
                            rxNorm.properties(candidate.rxcui!!)
                        }
                    }.getOrNull()?.properties
                    OfficialMatch(
                        rxcui = candidate.rxcui,
                        name = props?.name ?: candidate.name ?: q,
                        score = (candidate.scoreValue() / 100.0).coerceIn(0.0, 1.0),
                        sources = listOf("RxNorm"),
                    )
                }
            }.map { it.await() }.sortedByDescending { it.score }
        }
    }

    suspend fun officialDetails(rxcui: String?, name: String): OfficialDetails =
        coroutineScope {
            val fdaDeferred = async {
                runCatching {
                    val search = if (!rxcui.isNullOrBlank()) {
                        "openfda.rxcui:\"$rxcui\""
                    } else {
                        "openfda.brand_name:\"$name\"+openfda.generic_name:\"$name\""
                    }
                    cached("fda", "label:${rxcui ?: name}") { openFda.label(search) }
                }.getOrNull()?.results?.firstOrNull()
            }
            val splDeferred = async {
                runCatching {
                    if (!rxcui.isNullOrBlank()) {
                        cached("dm", "spls:$rxcui") { dailyMed.splsByRxcui(rxcui) }
                    } else {
                        cached("dm", "spls:$name") { dailyMed.splsByName(name) }
                    }
                }.getOrNull()?.data?.firstOrNull()
            }
            val propsDeferred = async {
                if (rxcui.isNullOrBlank()) {
                    null
                } else {
                    runCatching {
                        cached("rxnorm", "props:$rxcui") { rxNorm.properties(rxcui) }
                    }.getOrNull()?.properties
                }
            }
            val relatedDeferred = async {
                if (rxcui.isNullOrBlank()) {
                    null
                } else {
                    runCatching {
                        cached("rxnorm", "related:$rxcui") { rxNorm.related(rxcui) }
                    }.getOrNull()?.relatedGroup
                }
            }
            val fda = fdaDeferred.await()
            val spl = splDeferred.await()
            val props = propsDeferred.await()
            val related = relatedDeferred.await()
            val brands = buildSet {
                fda?.openfda?.brand_name?.let { addAll(it) }
                related?.conceptGroup.orEmpty()
                    .filter { it.tty == "SBD" || it.tty == "SBDC" }
                    .flatMap { it.conceptProperties }
                    .mapNotNullTo(this) { it.name }
            }
            OfficialDetails(
                rxcui = rxcui ?: fda?.openfda?.rxcui?.firstOrNull(),
                genericName = fda?.openfda?.generic_name?.firstOrNull() ?: props?.name,
                brandNames = brands.toList().take(6),
                substance = fda?.openfda?.substance_name?.firstOrNull(),
                route = fda?.openfda?.route?.firstOrNull(),
                dosageForm = fda?.openfda?.dosage_form?.firstOrNull(),
                manufacturer = fda?.openfda?.manufacturer_name?.firstOrNull(),
                indications = fda?.first(fda.indications_and_usage),
                dosage = fda?.first(fda.dosage_and_administration),
                contraindications = fda?.first(fda.contraindications),
                adverse = fda?.first(fda.adverse_reactions),
                interactions = fda?.first(fda.drug_interactions),
                warnings = fda?.first(fda.warnings),
                packaging = fda?.first(fda.packaging),
                labelUrl = spl?.labelUrl(),
                setId = spl?.setid,
            )
        }

    /** DailyMed pack images first (official), then Commons results. */
    suspend fun searchImages(query: String, setId: String? = null): List<EnrichImage> =
        coroutineScope {
            val q = query.trim()
            val dmDeferred = async {
                if (setId.isNullOrBlank()) {
                    emptyList()
                } else {
                    runCatching {
                        cached("dm", "media:$setId") { dailyMed.media(setId) }
                    }.getOrNull()?.data?.media.orEmpty()
                        .filter {
                            it.mime_type?.startsWith("image/") == true &&
                                !it.url.isNullOrBlank()
                        }
                        .map {
                            EnrichImage(
                                url = it.url!!,
                                thumbUrl = null,
                                width = 0,
                                height = 0,
                                license = "DailyMed (U.S. National Library of Medicine)",
                                credit = null,
                                kind = ImageKind.DAILYMED,
                                label = it.name,
                            )
                        }
                }
            }
            val commonsDeferred = async {
                if (q.length < 2) {
                    emptyList()
                } else {
                    runCatching {
                        cached("commons", "img:${q.lowercase()}") {
                            commons.searchImages(query = "$q medication")
                        }
                    }.getOrNull()?.query?.pages?.values.orEmpty()
                        .flatMap { page ->
                            page.imageinfo.map { info -> info to page.title }
                        }
                        .filter { (info, _) -> !info.url.isNullOrBlank() }
                        .take(MAX_CANDIDATES)
                        .map { (info, title) ->
                            EnrichImage(
                                url = info.url!!,
                                thumbUrl = info.thumburl,
                                width = info.width ?: 0,
                                height = info.height ?: 0,
                                license = info.license(),
                                credit = info.artist(),
                                kind = ImageKind.COMMONS,
                                label = title?.removePrefix("File:"),
                            )
                        }
                }
            }
            dmDeferred.await() + commonsDeferred.await()
        }

    /** Downloads [image] (thumbnail when available) into the drug's gallery. */
    suspend fun downloadToDrug(drugId: Long, image: EnrichImage): Long =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(image.thumbUrl ?: image.url)
                .header("User-Agent", NetworkModule.USER_AGENT)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val declared = response.header("Content-Length")?.toLongOrNull()
                if (declared != null && declared > MAX_DOWNLOAD_BYTES) {
                    throw IOException("image too large")
                }
                val bytes = response.body?.bytes() ?: throw IOException("empty body")
                if (bytes.size > MAX_DOWNLOAD_BYTES) throw IOException("image too large")
                images.addLocalImage(
                    drugId = drugId,
                    bytes = bytes,
                    kind = image.kind,
                    sourceUrl = image.url,
                    license = image.license ?: image.credit,
                )
            }
        }

    suspend fun pruneCache() {
        runCatching { cache.clearBefore(System.currentTimeMillis() - CACHE_TTL_MS) }
    }

    private suspend inline fun <reified T> cached(
        source: String,
        key: String,
        fetch: suspend () -> T,
    ): T {
        val cacheKey = "$source:$key"
        cache.get(cacheKey)?.let { entry ->
            if (System.currentTimeMillis() - entry.updatedAt < CACHE_TTL_MS) {
                runCatching { return json.decodeFromString<T>(entry.payload) }
            }
        }
        val fresh = fetch()
        runCatching {
            cache.put(
                EnrichCacheEntity(
                    cacheKey = cacheKey,
                    source = source,
                    payload = json.encodeToString(fresh),
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
        return fresh
    }

    companion object {
        const val MAX_CANDIDATES = 8
        const val MAX_DOWNLOAD_BYTES = 8 * 1024 * 1024
        const val CACHE_TTL_MS = 30L * 24 * 60 * 60 * 1000
    }
}
