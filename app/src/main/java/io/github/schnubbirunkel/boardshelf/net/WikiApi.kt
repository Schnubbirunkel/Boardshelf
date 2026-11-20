package io.github.schnubbirunkel.boardshelf.net

import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale

/**
 * Wikidata + Wikipedia as free, key-less secondary source: works without a BGG token
 * and enriches BGG data with an encyclopedia summary in the user's language.
 */
class WikiApi(private val http: OkHttpClient) {

    private val api = "https://www.wikidata.org/w/api.php"
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private val lang: String get() = Locale.getDefault().language.takeIf { it.length == 2 } ?: "en"

    private val gameClasses = setOf(
        "Q131436", // board game
        "Q142714", // card game
        "Q1191150", // tabletop game
        "Q11410",  // game
        "Q1515156", // dice game
        "Q2122041", // eurogame
        "Q1135547", // cooperative board game
    )
    private val gameWords = listOf(
        "board game", "card game", "tabletop", "dice game", "brettspiel", "kartenspiel",
        "gesellschaftsspiel", "würfelspiel", "legespiel", "jeu de société", "gioco da tavolo", "juego de mesa"
    )

    private suspend fun json(url: String): JSONObject = JSONObject(http.getString(url))

    suspend fun search(query: String): List<SearchHit> {
        val l = lang
        val found = json("$api?action=wbsearchentities&format=json&type=item&limit=30&language=$l&uselang=$l&search=${enc(query)}")
            .optJSONArray("search") ?: return emptyList()
        val ids = (0 until found.length()).map { found.getJSONObject(it).getString("id") }
        if (ids.isEmpty()) return emptyList()
        val entities = getEntities(ids, "labels|descriptions|claims")
        return ids.mapNotNull { id ->
            val e = entities.optJSONObject(id) ?: return@mapNotNull null
            val desc = text(e, "descriptions")
            val isGame = claimIds(e, "P31").any { it in gameClasses } ||
                claimString(e, "P2339") != null ||
                gameWords.any { desc?.lowercase()?.contains(it) == true }
            if (!isGame) return@mapNotNull null
            SearchHit(
                source = Source.WIKIDATA,
                id = id,
                name = text(e, "labels") ?: return@mapNotNull null,
                year = year(e),
                thumbnail = claimString(e, "P18")?.let { commonsUrl(it, 160) },
                subtitle = desc,
                bggId = claimString(e, "P2339")?.toIntOrNull(),
            )
        }
    }

    suspend fun details(qid: String): GameInfo {
        val e = getEntities(listOf(qid), "labels|descriptions|claims|sitelinks").getJSONObject(qid)
        val personIds = claimIds(e, "P287") + claimIds(e, "P178")
        val publisherIds = claimIds(e, "P123")
        val labels = if ((personIds + publisherIds).isEmpty()) emptyMap()
        else getEntities((personIds + publisherIds).distinct().take(50), "labels").let { obj ->
            obj.keys().asSequence().associateWith { k -> text(obj.getJSONObject(k), "labels") ?: k }
        }
        val summary = wikipediaSummary(e)
        val minutes = quantity(e, "P2047")?.let { (amount, unit) ->
            when (unit) {
                "Q25235" -> (amount * 60).toInt()
                "Q7727" -> amount.toInt()
                else -> null
            }
        }
        return GameInfo(
            name = text(e, "labels") ?: qid,
            year = year(e),
            minPlayers = quantity(e, "P1872")?.first?.toInt(),
            maxPlayers = quantity(e, "P1873")?.first?.toInt(),
            playingTime = minutes,
            minAge = quantity(e, "P2899")?.first?.toInt(),
            description = summary?.first ?: text(e, "descriptions"),
            imageUrl = claimString(e, "P18")?.let { commonsUrl(it, 800) } ?: summary?.third,
            designers = personIds.distinct().mapNotNull { labels[it] },
            publishers = publisherIds.distinct().mapNotNull { labels[it] },
            bggId = claimString(e, "P2339")?.toIntOrNull(),
            wikidataId = qid,
            wikiExtract = summary?.first,
            wikiUrl = summary?.second,
        )
    }

    /** Finds a Wikipedia summary for a game known by its BGG id (P2339). */
    suspend fun summaryForBggId(bggId: Int): Triple<String, String, String?>? {
        val res = json("$api?action=query&format=json&list=search&srlimit=1&srsearch=${enc("haswbstatement:P2339=$bggId")}")
        val hit = res.optJSONObject("query")?.optJSONArray("search")?.optJSONObject(0) ?: return null
        val qid = hit.optString("title").takeIf { it.startsWith("Q") } ?: return null
        val e = getEntities(listOf(qid), "sitelinks").optJSONObject(qid) ?: return null
        return wikipediaSummary(e)?.let { Triple(it.first, it.second, qid) }
    }

    /** Returns (extract, page url, image url). */
    private suspend fun wikipediaSummary(entity: JSONObject): Triple<String, String, String?>? {
        val sitelinks = entity.optJSONObject("sitelinks") ?: return null
        val (wiki, title) = listOf(lang, "en").firstNotNullOfOrNull { l ->
            sitelinks.optJSONObject("${l}wiki")?.optString("title")?.let { l to it }
        } ?: return null
        return try {
            val s = json("https://$wiki.wikipedia.org/api/rest_v1/page/summary/${enc(title.replace(' ', '_')).replace("+", "%20")}")
            val extract = s.optString("extract").takeIf { it.isNotBlank() } ?: return null
            val url = s.optJSONObject("content_urls")?.optJSONObject("desktop")?.optString("page")
                ?: "https://$wiki.wikipedia.org/wiki/${title.replace(' ', '_')}"
            Triple(extract, url, s.optJSONObject("originalimage")?.optString("source"))
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun getEntities(ids: List<String>, props: String): JSONObject {
        val l = lang
        val langs = if (l == "en") "en" else "$l|en"
        return json("$api?action=wbgetentities&format=json&ids=${ids.joinToString("|")}&props=${enc(props)}&languages=${enc(langs)}")
            .optJSONObject("entities") ?: JSONObject()
    }

    private fun text(e: JSONObject, field: String): String? {
        val o = e.optJSONObject(field) ?: return null
        return (o.optJSONObject(lang) ?: o.optJSONObject("en") ?: o.keys().asSequence().firstOrNull()?.let { o.optJSONObject(it) })
            ?.optString("value")?.takeIf { it.isNotBlank() }
    }

    private fun claims(e: JSONObject, p: String): JSONArray? = e.optJSONObject("claims")?.optJSONArray(p)

    private fun datavalue(e: JSONObject, p: String): List<Any> {
        val arr = claims(e, p) ?: return emptyList()
        return (0 until arr.length()).mapNotNull {
            arr.getJSONObject(it).optJSONObject("mainsnak")?.optJSONObject("datavalue")?.opt("value")
        }
    }

    private fun claimIds(e: JSONObject, p: String): List<String> =
        datavalue(e, p).mapNotNull { (it as? JSONObject)?.optString("id")?.takeIf { s -> s.isNotEmpty() } }

    private fun claimString(e: JSONObject, p: String): String? = datavalue(e, p).firstOrNull() as? String

    private fun quantity(e: JSONObject, p: String): Pair<Double, String>? {
        val v = datavalue(e, p).firstOrNull() as? JSONObject ?: return null
        val amount = v.optString("amount").removePrefix("+").toDoubleOrNull() ?: return null
        return amount to v.optString("unit").substringAfterLast('/')
    }

    private fun year(e: JSONObject): Int? {
        val v = datavalue(e, "P577").firstOrNull() as? JSONObject ?: return null
        return v.optString("time").removePrefix("+").take(4).toIntOrNull()
    }

    private fun commonsUrl(fileName: String, width: Int) =
        "https://commons.wikimedia.org/wiki/Special:FilePath/${enc(fileName.replace(' ', '_')).replace("+", "%20")}?width=$width"
}
