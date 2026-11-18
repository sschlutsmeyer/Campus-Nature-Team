package com.example.campusexplorer.data

import android.content.Context
import android.util.LruCache
import androidx.core.text.HtmlCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URI
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.text.BreakIterator
import java.util.Date
import java.util.Locale
import kotlin.random.Random

/** Read-only iNaturalist integration. Public species searches do not need an API key. */
object INaturalistRepository {
    private const val BASE_URL = "https://api.inaturalist.org/v1/"
    private const val PAGE_SIZE = 50
    private const val PAGE_LIMIT = 200
    private const val CACHE_MS = 15 * 60 * 1000L
    private val requestGate = Mutex()
    private val dailyGate = Mutex()
    private var nextRequestAt = 0L
    private data class CachedJson(val json: String, val storedAt: Long)
    private val cache = LruCache<String, CachedJson>(24)
    private data class CachedPhoto(val photo: LearnPhoto?, val storedAt: Long)
    private val photoCache = LruCache<Long, CachedPhoto>(128)
    private val photoLocks = LruCache<Long, Mutex>(256)

    fun todayKey(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    suspend fun search(
        query: String,
        region: LearnRegion,
        group: LearnGroup,
        page: Int = 1,
        refresh: Boolean = false,
        placeId: Long? = null,
    ): LearnPage {
        require(page in 1..PAGE_LIMIT)
        require(placeId == null || placeId > 0)
        val params = linkedMapOf("locale" to "en", "page" to page.toString(), "per_page" to "$PAGE_SIZE")
        val catalog = placeId == null && region == LearnRegion.ALL && group == LearnGroup.ALL
        val endpoint: String
        if (catalog) {
            endpoint = "taxa"
            params["rank"] = "species"
            params["is_active"] = "true"
            params["order_by"] = "observations_count"
            params["order"] = "desc"
        } else {
            // These filters describe observation locations, not a complete biological range.
            endpoint = "observations/species_counts"
            params["rank"] = "species"
            params["taxon_is_active"] = "true"
            params["verifiable"] = "true"
            group.iconicTaxon?.let { params["iconic_taxa"] = it }
            if (placeId != null) {
                params["place_id"] = placeId.toString()
            } else when (region) {
                LearnRegion.CALIFORNIA -> params["place_id"] = "14"
                LearnRegion.CAMPUS -> {
                    // Same CSU Channel Islands center as PhotoMap.kt; no device GPS required.
                    params["lat"] = "34.1623"
                    params["lng"] = "-119.0433"
                    params["radius"] = "5"
                }
                LearnRegion.ALL -> Unit
            }
        }
        if (query.isNotBlank()) {
            params["q"] = query.trim()
            if (!catalog) params["search_on"] = "names"
        }
        val json = request(endpoint, params, refresh)
        val results = json.optJSONArray("results") ?: throw IOException("iNaturalist returned no results list.")
        val parsed = (0 until results.length()).mapNotNull { index ->
            val entry = results.optJSONObject(index) ?: return@mapNotNull null
            val taxon = if (catalog) entry else entry.optJSONObject("taxon") ?: return@mapNotNull null
            parseSpecies(taxon)?.takeIf { it.rank == "species" }
        }
        val total = json.optLong("total_results", results.length().toLong())
        return LearnPage(parsed, total,
            results.length() > 0 && page.toLong() * PAGE_SIZE < total && page < PAGE_LIMIT)
    }

    // Place IDs are used by species_counts; typed text is never sent as a place ID.
    suspend fun searchPlaces(query: String, refresh: Boolean = false): List<LearnPlace> {
        val term = query.trim()
        if (term.length < 2) return emptyList()
        val response = request("places/autocomplete", mapOf("q" to term), refresh)
        return parsePlaces(response)
    }

    internal fun parsePlaces(response: JSONObject): List<LearnPlace> {
        val results = response.optJSONArray("results")
            ?: throw IOException("iNaturalist returned no location results list.")
        return (0 until results.length()).mapNotNull { index ->
            val place = results.optJSONObject(index) ?: return@mapNotNull null
            val id = place.optLong("id", 0)
            val name = place.text("display_name") ?: place.text("name")
            if (id <= 0 || name == null) null else LearnPlace(id, name)
        }.distinctBy { it.id }.take(10)
    }

    suspend fun details(id: Long, refresh: Boolean = false): LearnSpecies {
        require(id > 0)
        return parseSpecies(detailJson(id, refresh)) ?: throw IOException("Species information is unavailable.")
    }

    // Called only for visible images. Cached successes and empty results avoid repeated lookups.
    suspend fun resolvePhoto(species: LearnSpecies, refresh: Boolean = false): LearnPhoto? {
        species.photo?.let { return it }
        val lock = synchronized(photoLocks) {
            photoLocks.get(species.id) ?: Mutex().also { photoLocks.put(species.id, it) }
        }
        return lock.withLock {
            if (!refresh) photoCache.get(species.id)?.let {
                if (System.currentTimeMillis() - it.storedAt in 0 until CACHE_MS) return@withLock it.photo
            }
            var detailFailure: IOException? = null
            val curated = try {
                preferredPhoto(detailJson(species.id, refresh))
            } catch (e: IOException) {
                detailFailure = e
                null
            }
            val photo = curated ?: run {
                val observations = request("observations", mapOf(
                    "taxon_id" to species.id.toString(), "taxon_is_active" to "true",
                    "photos" to "true", "quality_grade" to "research", "per_page" to "5",
                    "photo_license" to "cc0,cc-by,cc-by-sa,cc-by-nc,cc-by-nc-sa", "locale" to "en",
                ), refresh)
                observationPhoto(observations, species.id)
            }
            if (photo == null && detailFailure != null) throw detailFailure!!
            photoCache.put(species.id, CachedPhoto(photo, System.currentTimeMillis()))
            photo
        }
    }

    // The observation filter includes descendant taxa, such as subspecies of this species.
    internal fun observationPhoto(response: JSONObject, speciesId: Long): LearnPhoto? {
        val observations = response.optJSONArray("results") ?: return null
        for (index in 0 until observations.length()) {
            val observation = observations.optJSONObject(index) ?: continue
            val taxon = observation.optJSONObject("taxon") ?: continue
            val ancestors = taxon.optJSONArray("ancestor_ids")
            val matches = taxon.optLong("id") == speciesId ||
                    (ancestors != null && (0 until ancestors.length()).any { ancestors.optLong(it) == speciesId })
            if (!matches) continue
            val photos = observation.optJSONArray("photos") ?: continue
            for (photoIndex in 0 until photos.length()) {
                parsePhoto(photos.optJSONObject(photoIndex))?.let { return it }
            }
        }
        return null
    }

    private suspend fun detailJson(id: Long, refresh: Boolean = false): JSONObject {
        return request("taxa/$id", mapOf("locale" to "en"), refresh)
            .optJSONArray("results")?.optJSONObject(0)
            ?: throw IOException("This species could not be found on iNaturalist.")
    }

    suspend fun dailySpecies(context: Context): LearnSpecies = dailyGate.withLock {
        withContext(Dispatchers.IO) {
            val preferences = context.getSharedPreferences("INaturalistDailySpecies", Context.MODE_PRIVATE)
            val today = todayKey()
            if (preferences.getString("date", null) == today) {
                val saved = preferences.getString("taxon", null)
                val parsed = saved?.let { runCatching { parseSpecies(JSONObject(it)) }.getOrNull() }
                if (parsed != null) return@withContext parsed
            }
            // Choose from a bounded discovery pool instead of downloading the database.
            val poolJson = request("taxa", mapOf(
                "rank" to "species", "is_active" to "true", "per_page" to "200",
                "order_by" to "observations_count", "order" to "desc", "locale" to "en",
            ))
            val entries = poolJson.optJSONArray("results") ?: throw IOException("Couldn't choose today's species.")
            val candidates = (0 until entries.length()).mapNotNull {
                entries.optJSONObject(it)?.let(::parseSpecies)
            }.filter { it.rank == "species" }
            val photographed = candidates.filter { it.photo != null }
            val choices = photographed.ifEmpty { candidates }
            if (choices.isEmpty()) throw IOException("No species are available for today's selection.")
            val chosen = choices[Random.nextInt(choices.size)]
            val taxonJson = detailJson(chosen.id)
            val species = parseSpecies(taxonJson) ?: throw IOException("Couldn't load today's species.")
            currentCoroutineContext().ensureActive()
            preferences.edit().putString("date", today).putString("taxon", taxonJson.toString()).apply()
            species
        }
    }

    // Prefer iNaturalist's supplied description; otherwise use its linked Wikipedia article.
    // When no link is supplied, Wikipedia commonly redirects the scientific name to its article.
    suspend fun facts(species: LearnSpecies): LearnFacts {
        val supplied = species.descriptionHtml?.let {
            HtmlCompat.fromHtml(it, HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim()
        }
        val linkedPage = species.wikipediaUrl?.let { runCatching { URI(it) }.getOrNull() }
        val title = if (linkedPage != null && linkedPage.host == "en.wikipedia.org" && linkedPage.path.orEmpty().startsWith("/wiki/")) {
            linkedPage.path.substringAfter("/wiki/")
        } else species.scientificName
        val encodedTitle = URLEncoder.encode(title, "UTF-8").replace("+", "%20")
        val articleUrl = "https://en.wikipedia.org/wiki/$encodedTitle"
        if (!supplied.isNullOrBlank()) {
            return LearnFacts(factSentences(supplied), species.wikipediaUrl ?: articleUrl)
        }
        val summary = try {
            requestUrl("https://en.wikipedia.org/api/rest_v1/page/summary/$encodedTitle")
        } catch (e: LearnHttpException) {
            if (e.status == 404) return LearnFacts(emptyList(), articleUrl)
            throw e
        }
        if (summary.optString("type") == "disambiguation") {
            return LearnFacts(emptyList(), articleUrl)
        }
        val extract = summary.text("extract") ?: return LearnFacts(emptyList(), articleUrl)
        val source = summary.optJSONObject("content_urls")?.optJSONObject("desktop")?.text("page")
            ?.takeIf { it.startsWith("https://en.wikipedia.org/wiki/") } ?: articleUrl
        return LearnFacts(factSentences(extract), source)
    }

    private suspend fun request(path: String, params: Map<String, String>, refresh: Boolean = false): JSONObject {
        val query = params.entries.sortedBy { it.key }.joinToString("&") {
            "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}"
        }
        val url = BASE_URL + path + "?" + query
        return requestUrl(url, refresh)
    }

    private suspend fun requestUrl(url: String, refresh: Boolean = false): JSONObject {
        val provider = if (url.startsWith(BASE_URL)) "iNaturalist" else "Wikipedia"
        if (!refresh) cache.get(url)?.let {
            if (System.currentTimeMillis() - it.storedAt in 0 until CACHE_MS) return JSONObject(it.json)
        }
        // Keep API calls below the recommended rate even across concurrent screen requests.
        requestGate.withLock {
            val wait = nextRequestAt - android.os.SystemClock.elapsedRealtime()
            if (wait > 0) delay(wait)
            nextRequestAt = android.os.SystemClock.elapsedRealtime() + 1_100
        }
        val response = withContext(Dispatchers.IO) {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 10_000
                connection.readTimeout = 15_000
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("User-Agent", "NatureNetwork-Android/1.0 (https://github.com/sschlutsmeyer/Campus-Nature-Team)")
                when (val status = connection.responseCode) {
                    in 200..299 -> Unit
                    429 -> throw LearnHttpException(status, "$provider is receiving too many requests. Wait a moment and try again.")
                    else -> throw LearnHttpException(status, "$provider returned HTTP $status. Please try again.")
                }
                connection.inputStream.use { input ->
                    val bytes = input.readBytesLimited(4 * 1024 * 1024)
                    currentCoroutineContext().ensureActive()
                    bytes.toString(Charsets.UTF_8)
                }
            } finally {
                connection.disconnect()
            }
        }
        currentCoroutineContext().ensureActive()
        val parsed = try { JSONObject(response) } catch (e: Exception) {
            throw IOException("$provider returned an unreadable response.", e)
        }
        cache.put(url, CachedJson(response, System.currentTimeMillis()))
        return parsed
    }

    internal fun parseSpecies(json: JSONObject): LearnSpecies? {
        val id = json.optLong("id", 0)
        val scientificName = json.text("name") ?: return null
        if (id <= 0) return null
        val ancestors = json.optJSONArray("ancestors")
        val classification = if (ancestors == null) emptyList() else
            (0 until ancestors.length()).mapNotNull {
                val ancestor = ancestors.optJSONObject(it) ?: return@mapNotNull null
                val name = ancestor.text("name") ?: return@mapNotNull null
                val rank = ancestor.text("rank") ?: return@mapNotNull null
                if (rank in setOf("kingdom", "phylum", "class", "order", "family", "genus")) "$rank: $name" else null
            }
        return LearnSpecies(
            id = id,
            name = json.text("preferred_common_name") ?: scientificName,
            scientificName = scientificName,
            groupName = json.text("iconic_taxon_name") ?: "Other species",
            rank = json.text("rank") ?: "unknown",
            observationsCount = json.optLong("observations_count", 0),
            photo = preferredPhoto(json),
            descriptionHtml = json.text("wikipedia_summary"),
            wikipediaUrl = json.text("wikipedia_url")?.replaceFirst(Regex("^http://"), "https://")
                ?.takeIf { it.startsWith("https://") },
            classification = classification,
        )
    }

    // A taxon may have a suitable curated photo even when its default photo is excluded.
    internal fun preferredPhoto(taxon: JSONObject): LearnPhoto? {
        parsePhoto(taxon.optJSONObject("default_photo"))?.let { return it }
        val curated = taxon.optJSONArray("taxon_photos") ?: return null
        for (index in 0 until curated.length()) {
            val entry = curated.optJSONObject(index) ?: continue
            parsePhoto(entry.optJSONObject("photo") ?: entry)?.let { return it }
        }
        return null
    }

    private fun parsePhoto(json: JSONObject?): LearnPhoto? {
        if (json == null) return null
        val license = json.text("license_code")?.lowercase(Locale.ROOT) ?: return null
        if (license !in setOf("cc0", "cc-by", "cc-by-sa", "cc-by-nc", "cc-by-nc-sa")) return null
        val id = json.optLong("id", 0)
        val source = json.text("medium_url") ?: json.text("square_url") ?: json.text("url") ?: return null
        if (id <= 0 || !source.startsWith("https://")) return null
        fun sized(size: String): String = source.replace(
            Regex("/(square|thumb|small|medium|large|original)(\\.[A-Za-z0-9]+)(?=\\?|$)"), "/$size$2")
        return LearnPhoto(id, sized("small"), sized("medium"),
            json.text("attribution") ?: "Photo contributor on iNaturalist", license.uppercase(Locale.ROOT))
    }

    private fun JSONObject.text(key: String): String? =
        if (isNull(key)) null else optString(key).trim().takeIf { it.isNotEmpty() && it != "null" }
}

data class LearnFacts(val sentences: List<String>, val sourceUrl: String)

private class LearnHttpException(val status: Int, message: String) : IOException(message)

// Use source text directly instead of generating or guessing species facts.
internal fun factSentences(text: String): List<String> {
    val clean = text.replace(Regex("\\s+"), " ").trim()
    if (clean.isEmpty()) return emptyList()
    val breaks = BreakIterator.getSentenceInstance(Locale.ENGLISH)
    breaks.setText(clean)
    val sentences = mutableListOf<String>()
    var start = breaks.first()
    var end = breaks.next()
    while (end != BreakIterator.DONE && sentences.size < 2) {
        val sentence = clean.substring(start, end).trim()
        if (sentence.isNotEmpty()) sentences.add(sentence)
        start = end
        end = breaks.next()
    }
    return sentences
}

internal fun java.io.InputStream.readBytesLimited(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer)
        if (count == -1) break
        if (output.size() + count > limit) throw IOException("The download was larger than expected.")
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
