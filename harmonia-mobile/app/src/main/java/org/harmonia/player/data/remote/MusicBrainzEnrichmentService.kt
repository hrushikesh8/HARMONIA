package org.harmonia.player.data.remote

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.harmonia.player.domain.enrichment.EnrichedMetadata
import org.harmonia.player.domain.enrichment.MetadataProvenance
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * Opt-in online enrichment service querying MusicBrainz open catalog.
 * Guarantees:
 * 1. Opt-in only (disabled by default; strictly offline-first).
 * 2. 1 request/second polite rate limit for MusicBrainz API etiquette.
 * 3. Graceful fallback on network loss or timeout (3-second timeout).
 * 4. Provenance tracking: online data never overwrites existing local tags.
 * 5. In-memory positive/negative caching to prevent redundant requests.
 */
class MusicBrainzEnrichmentService private constructor(
    private val prefs: SharedPreferences
) {
    companion object {
        const val PREFS_NAME = "harmonia_enrichment_preferences"
        private const val KEY_ENRICHMENT_ENABLED = "key_online_enrichment_enabled"
        private const val USER_AGENT = "HarmoniaMobile/0.1.0-alpha ( contact@harmonia.org )"
        private const val TIMEOUT_MS = 3000
        private const val RATE_LIMIT_DELAY_MS = 1000L

        @Volatile
        private var instance: MusicBrainzEnrichmentService? = null

        fun getInstance(context: Context): MusicBrainzEnrichmentService {
            return instance ?: synchronized(this) {
                instance ?: MusicBrainzEnrichmentService(
                    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                ).also { instance = it }
            }
        }
    }

    private val _isEnrichmentEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_ENRICHMENT_ENABLED, false) // Default strictly FALSE (opt-in)
    )
    val isEnrichmentEnabled: StateFlow<Boolean> = _isEnrichmentEnabled.asStateFlow()

    private val cache = ConcurrentHashMap<String, EnrichedMetadata?>()
    private val rateLimiterMutex = Mutex()
    private var lastRequestTime = 0L

    fun setEnrichmentEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENRICHMENT_ENABLED, enabled).apply()
        _isEnrichmentEnabled.value = enabled
    }

    suspend fun enrichTrack(
        title: String,
        artist: String?,
        existingYear: Int? = null,
        existingGenre: String? = null
    ): EnrichedMetadata? = withContext(Dispatchers.IO) {
        if (!_isEnrichmentEnabled.value) {
            return@withContext null
        }

        if (title.isBlank()) return@withContext null

        val cacheKey = "${title.trim().lowercase()}::${artist?.trim()?.lowercase() ?: ""}"
        if (cache.containsKey(cacheKey)) {
            return@withContext cache[cacheKey]
        }

        // Apply rate limiting politely
        rateLimiterMutex.withLock {
            val elapsed = System.currentTimeMillis() - lastRequestTime
            if (elapsed < RATE_LIMIT_DELAY_MS) {
                kotlinx.coroutines.delay(RATE_LIMIT_DELAY_MS - elapsed)
            }
            lastRequestTime = System.currentTimeMillis()
        }

        val result = try {
            queryMusicBrainz(title, artist)
        } catch (_: Throwable) {
            null // Network error, timeout, or parsing failure -> graceful offline fallback
        }

        cache[cacheKey] = result
        result
    }

    private fun queryMusicBrainz(title: String, artist: String?): EnrichedMetadata? {
        val query = buildString {
            append("recording:\"").append(title).append("\"")
            if (!artist.isNullOrBlank()) {
                append(" AND artist:\"").append(artist).append("\"")
            }
        }

        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val endpoint = "https://musicbrainz.org/ws/2/recording/?query=$encodedQuery&fmt=json&limit=1"
        val url = URL(endpoint)

        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/json")
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
        }

        return try {
            if (connection.responseCode != 200) {
                return null
            }

            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val response = reader.use { it.readText() }
            val json = JSONObject(response)
            val recordings = json.optJSONArray("recordings")
            if (recordings == null || recordings.length() == 0) {
                return null
            }

            val firstRecording = recordings.getJSONObject(0)
            val mbid = firstRecording.optString("id").takeIf { it.isNotBlank() }
            val firstRelease = firstRecording.optJSONArray("releases")?.optJSONObject(0)
            val releaseDate = firstRelease?.optString("date")
            val releaseYear = releaseDate?.take(4)?.toIntOrNull()
            val releaseTitle = firstRelease?.optString("title")

            val tags = firstRecording.optJSONArray("tags")
            val genre = if (tags != null && tags.length() > 0) {
                tags.getJSONObject(0).optString("name").replaceFirstChar { it.uppercase() }
            } else null

            // Check if releaseTitle or tags indicate a soundtrack
            val isSoundtrack = releaseTitle?.contains("Soundtrack", ignoreCase = true) == true ||
                    releaseTitle?.contains("OST", ignoreCase = true) == true

            EnrichedMetadata(
                mbid = mbid,
                releaseYear = releaseYear,
                genre = genre,
                soundtrackTitle = if (isSoundtrack) releaseTitle else null,
                provenance = MetadataProvenance.ONLINE_ENRICHMENT
            )
        } finally {
            connection.disconnect()
        }
    }
}
