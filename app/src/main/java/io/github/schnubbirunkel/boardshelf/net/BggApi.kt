package io.github.schnubbirunkel.boardshelf.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder
import java.util.Locale

/**
 * Client for the BoardGameGeek XML API 2. Since autumn 2025 BGG requires a registered
 * application token (https://boardgamegeek.com/applications), sent as Bearer token.
 */
class BggApi(private val http: OkHttpClient, private val token: () -> String) {

    private val base = "https://boardgamegeek.com/xmlapi2/"

    val hasToken: Boolean get() = token().isNotBlank()

    private suspend fun get(path: String): XNode = withContext(Dispatchers.IO) {
        val t = token()
        if (t.isBlank()) throw BggTokenMissingException()
        var attempt = 0
        var result: String? = null
        while (result == null) {
            val req = Request.Builder().url(base + path)
                .header("Authorization", "Bearer $t")
                .build()
            val (code, body) = http.newCall(req).execute().use { r -> r.code to (r.body?.string() ?: "") }
            when (code) {
                200 -> result = body
                401, 403 -> throw BggAuthException()
                202, 429, 500, 502, 503, 504 -> {
                    attempt++
                    if (attempt > 7) throw IOException("BGG busy (HTTP $code)")
                    delay(1500L * attempt)
                }
                else -> throw HttpException(code, base + path)
            }
        }
        parseXml(result!!)
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    suspend fun search(query: String): List<SearchHit> {
        val root = get("search?query=${enc(query)}&type=boardgame")
        val items = root.child("items")?.all("item").orEmpty()
        val hits = items.mapNotNull { item ->
            val id = item.attr("id")?.toIntOrNull() ?: return@mapNotNull null
            val names = item.all("name")
            val name = (names.firstOrNull { it.attr("type") == "primary" } ?: names.firstOrNull())
                ?.attr("value") ?: return@mapNotNull null
            SearchHit(
                source = Source.BGG, id = id.toString(), name = name,
                year = item.value("yearpublished")?.toIntOrNull(), bggId = id
            )
        }.distinctBy { it.id }
        // Rank exact / prefix matches first, then newer games.
        val q = query.lowercase(Locale.ROOT)
        val sorted = hits.sortedWith(compareBy<SearchHit>(
            { if (it.name.lowercase(Locale.ROOT) == q) 0 else if (it.name.lowercase(Locale.ROOT).startsWith(q)) 1 else 2 },
            { -(it.year ?: 0) }
        )).take(40)
        // Fetch thumbnails for the first results (max. 20 ids per request).
        val firstIds = sorted.take(20).mapNotNull { it.bggId }
        if (firstIds.isEmpty()) return sorted
        val thumbs = try {
            get("thing?id=${firstIds.joinToString(",")}").child("items")?.all("item").orEmpty()
                .associate { (it.attr("id")?.toIntOrNull() ?: -1) to it.child("thumbnail")?.text?.trim() }
        } catch (e: Exception) {
            emptyMap()
        }
        return sorted.map { h -> h.copy(thumbnail = thumbs[h.bggId]) }
    }

    suspend fun details(bggId: Int): GameInfo =
        detailsBatch(listOf(bggId)).firstOrNull() ?: throw IOException("BGG item $bggId not found")

    /** Up to 20 ids per request (BGG limit). */
    suspend fun detailsBatch(ids: List<Int>): List<GameInfo> {
        if (ids.isEmpty()) return emptyList()
        val root = get("thing?id=${ids.joinToString(",")}&stats=1&versions=1")
        return root.child("items")?.all("item").orEmpty().map { parseThing(it) }
    }

    private fun parseThing(item: XNode): GameInfo {
        val names = item.all("name")
        val name = (names.firstOrNull { it.attr("type") == "primary" } ?: names.firstOrNull())?.attr("value") ?: "?"
        fun links(type: String) = item.all("link").filter { it.attr("type") == type }.mapNotNull { it.attr("value") }
        val ratings = item.child("statistics")?.child("ratings")
        val rank = ratings?.child("ranks")?.all("rank")
            ?.firstOrNull { it.attr("name") == "boardgame" }?.attr("value")?.toIntOrNull()

        // Box dimensions from the versions list (inches). Prefer the user's language.
        val wantedLang = when (Locale.getDefault().language) {
            "de" -> "German"; "fr" -> "French"; "es" -> "Spanish"; "it" -> "Italian"; else -> "English"
        }
        data class Dim(val w: Float, val l: Float, val d: Float, val lang: List<String>)
        val dims = item.child("versions")?.all("item").orEmpty().mapNotNull { v ->
            val w = v.value("width")?.toFloatOrNull() ?: 0f
            val l = v.value("length")?.toFloatOrNull() ?: 0f
            val d = v.value("depth")?.toFloatOrNull() ?: 0f
            if (w <= 0f || l <= 0f || d <= 0f) null
            else Dim(w, l, d, v.all("link").filter { it.attr("type") == "language" }.mapNotNull { it.attr("value") })
        }
        val dim = dims.firstOrNull { wantedLang in it.lang } ?: dims.firstOrNull()

        fun cm(inches: Float?) = inches?.let { (it * 2.54f).coerceIn(1.5f, 70f) }

        return GameInfo(
            name = name,
            year = item.value("yearpublished")?.toIntOrNull()?.takeIf { it != 0 },
            minPlayers = item.value("minplayers")?.toIntOrNull()?.takeIf { it > 0 },
            maxPlayers = item.value("maxplayers")?.toIntOrNull()?.takeIf { it > 0 },
            playingTime = item.value("playingtime")?.toIntOrNull()?.takeIf { it > 0 },
            minAge = item.value("minage")?.toIntOrNull()?.takeIf { it > 0 },
            description = htmlToText(item.child("description")?.text),
            imageUrl = item.child("image")?.text?.trim()?.takeIf { it.isNotEmpty() },
            designers = links("boardgamedesigner"),
            publishers = links("boardgamepublisher").take(6),
            categories = links("boardgamecategory"),
            mechanics = links("boardgamemechanic"),
            bggRating = ratings?.value("average")?.toDoubleOrNull()?.takeIf { it > 0 },
            bggWeight = ratings?.value("averageweight")?.toDoubleOrNull()?.takeIf { it > 0 },
            bggRank = rank,
            boxWidthCm = cm(dim?.w),
            boxHeightCm = cm(dim?.l),
            boxDepthCm = cm(dim?.d),
            bggId = item.attr("id")?.toIntOrNull(),
        )
    }

    data class CollectionEntry(val bggId: Int, val name: String, val userRating: Float?)

    /** Owned games of a BGG user. BGG answers 202 while the export is being prepared. */
    suspend fun collection(username: String): List<CollectionEntry> {
        val root = get("collection?username=${enc(username)}&own=1&subtype=boardgame&excludesubtype=boardgameexpansion&stats=1")
        root.child("errors")?.let { err ->
            throw IOException(err.child("error")?.child("message")?.text ?: "BGG error")
        }
        return root.child("items")?.all("item").orEmpty().mapNotNull { item ->
            val id = item.attr("objectid")?.toIntOrNull() ?: return@mapNotNull null
            CollectionEntry(
                bggId = id,
                name = item.child("name")?.text?.trim() ?: "?",
                userRating = item.child("stats")?.child("rating")?.attr("value")?.toFloatOrNull()
            )
        }.distinctBy { it.bggId }
    }

    companion object {
        fun gameUrl(bggId: Int) = "https://boardgamegeek.com/boardgame/$bggId"
        fun filesUrl(bggId: Int) = "https://boardgamegeek.com/boardgame/$bggId/x/files"
        const val APPLICATIONS_URL = "https://boardgamegeek.com/applications"
    }
}
