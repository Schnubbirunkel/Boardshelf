package io.github.schnubbirunkel.boardshelf.net

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.net.URLEncoder
import java.text.Normalizer
import java.util.Locale

/** A product found by its barcode (or by title) in a public product database. */
data class Product(
    val ean: String?,
    val title: String,
    val brand: String?,
    val images: List<String>,
    val source: String,
)

/** A possible cover picture with a confidence score (higher = better). */
data class CoverCandidate(val url: String, val source: String, val label: String, val score: Int)

/**
 * Key-less product sources:
 *  - UPCitemdb (free plan without sign-up, about 100 lookups / 20 searches per day and device IP)
 *  - Open Products Facts (open database)
 *  - Wikidata GTIN statements (P3962)
 */
class ProductApi(private val http: OkHttpClient, private val gameUpcKey: () -> String = { "" }) {

    /**
     * GameUPC: a board-game barcode database linked to BoardGameGeek ids (also European EANs).
     * Without an own key the public test access is used.
     */
    suspend fun gameUpc(ean: String): List<SearchHit> {
        val key = gameUpcKey().trim()
        val url = if (key.isEmpty()) "https://api.gameupc.com/test/upc/$ean" else "https://api.gameupc.com/v1/upc/$ean"
        val json = JSONObject(http.getString(url, mapOf("x-api-key" to key.ifEmpty { "test_test_test_test_test" }, "Accept" to "application/json")))
        val infos = json.optJSONArray("bgg_info") ?: return emptyList()
        return (0 until infos.length()).mapNotNull { i ->
            val o = infos.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optInt("id", -1).takeIf { it > 0 } ?: return@mapNotNull null
            SearchHit(
                source = Source.GAMEUPC,
                id = id.toString(),
                name = o.optString("name").ifBlank { json.optString("name") },
                year = o.optString("published").toIntOrNull(),
                thumbnail = o.optString("thumbnail_url").ifBlank { null },
                subtitle = "GameUPC · ${o.optInt("confidence")} %",
                bggId = id,
                imageUrl = o.optString("image_url").ifBlank { null },
            )
        }.filter { it.name.isNotBlank() }
    }


    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    suspend fun byBarcode(code: String): List<Product> = coroutineScope {
        val ean = code.filter { it.isDigit() }
        val upc = async { runCatching { upcItemDbLookup(ean) }.getOrDefault(emptyList()) }
        val opf = async { runCatching { openProductsFacts(ean) }.getOrNull() }
        (upc.await() + listOfNotNull(opf.await())).filter { it.title.isNotBlank() }
    }

    private suspend fun upcItemDbLookup(ean: String): List<Product> {
        val json = JSONObject(http.getString("https://api.upcitemdb.com/prod/trial/lookup?upc=$ean", mapOf("Accept" to "application/json")))
        return parseUpcItems(json)
    }

    /** Product search by title – used to find cover pictures of a known game. */
    suspend fun searchProducts(title: String): List<Product> {
        val json = JSONObject(
            http.getString("https://api.upcitemdb.com/prod/trial/search?s=${enc(title)}&match_mode=0&type=product", mapOf("Accept" to "application/json"))
        )
        return parseUpcItems(json)
    }

    private fun parseUpcItems(json: JSONObject): List<Product> {
        val items = json.optJSONArray("items") ?: return emptyList()
        return (0 until items.length()).map { i ->
            val o = items.getJSONObject(i)
            val imgs = o.optJSONArray("images")
            Product(
                ean = o.optString("ean").ifBlank { null },
                title = o.optString("title").trim(),
                brand = o.optString("brand").ifBlank { null },
                images = (0 until (imgs?.length() ?: 0)).mapNotNull { imgs?.optString(it)?.takeIf { u -> u.startsWith("http") } }
                    .map { it.replace("http://", "https://") },
                source = "UPCitemdb",
            )
        }
    }

    private suspend fun openProductsFacts(ean: String): Product? {
        val json = JSONObject(
            http.getString("https://world.openproductsfacts.org/api/v2/product/$ean.json?fields=product_name,product_name_de,product_name_en,brands,image_front_url,image_url")
        )
        if (json.optInt("status") != 1) return null
        val p = json.optJSONObject("product") ?: return null
        val name = listOf("product_name_de", "product_name", "product_name_en").map { p.optString(it).trim() }.firstOrNull { it.isNotEmpty() } ?: return null
        val img = p.optString("image_front_url").ifBlank { p.optString("image_url") }.ifBlank { null }
        return Product(ean, name, p.optString("brands").split(',').firstOrNull()?.trim()?.ifBlank { null }, listOfNotNull(img), "Open Products Facts")
    }

    companion object {
        private val noiseWords = listOf(
            "brettspiel", "gesellschaftsspiel", "familienspiel", "strategiespiel", "kartenspiel", "partyspiel", "kennerspiel",
            "expertenspiel", "spiel", "board game", "boardgame", "card game", "game", "deutsch", "deutsche ausgabe", "german",
            "english", "englisch", "edition", "ausgabe", "neuauflage", "multilingual", "spiel des jahres", "nominiert",
        )
        private val wrongProduct = listOf(
            "erweiterung", "expansion", "sleeve", "hüllen", "huellen", "insert", "organizer", "einsatz", "puzzle", "promo",
            "ersatz", "replacement", "playmat", "spielmatte", "t-shirt", "tasse", "mug", "poster", "buch", "book", "roman", "novel",
        )

        fun norm(s: String): String = Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "").replace("ß", "ss").replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

        /** Turns a shop product title into a probable game title ("KOSMOS 682590 Catan - Das Spiel, Brettspiel ab 10" → "Catan"). */
        fun cleanTitle(title: String, brand: String?): String {
            var t = title
            brand?.let { b -> t = t.replace(Regex("(?i)\\b" + Regex.escape(b) + "\\b"), " ") }
            t = t.replace(Regex("\\(.*?\\)|\\[.*?]"), " ")
                .replace(Regex("(?i)\\bab \\d+ jahren?\\b.*"), " ")
                .replace(Regex("(?i)\\b(für|for) \\d+.*"), " ")
                .replace(Regex("\\b\\d{4,}\\b"), " ")
            // keep the part before typical separators if it is long enough
            val head = t.split(Regex("\\s[-–|:,]\\s|,")).firstOrNull()?.trim().orEmpty()
            if (head.length >= 3) t = head
            var n = t
            noiseWords.forEach { w -> n = n.replace(Regex("(?i)\\b" + Regex.escape(w) + "\\b"), " ") }
            return n.replace(Regex("\\s+"), " ").trim().ifBlank { title.trim() }
        }

        /**
         * Rates a product as cover source for [gameName]: the product title must contain the game
         * name, must not be an expansion/accessory, a matching publisher brand gives a bonus.
         */
        fun score(product: Product, gameName: String, publishers: String?): Int {
            val title = norm(product.title)
            val name = norm(gameName)
            if (name.isEmpty() || product.images.isEmpty()) return 0
            var s = 0
            if (title.contains(name)) s += 50 else {
                val words = name.split(' ').filter { it.length > 2 }
                if (words.isEmpty() || words.any { it !in title }) return 0
                s += 30
            }
            if (wrongProduct.any { it in title } && wrongProduct.none { it in name }) return 0
            val brand = product.brand?.let { norm(it) }
            if (brand != null && publishers != null && norm(publishers).split(' ').any { it.length > 3 && it in brand }) s += 25
            if (title.length < name.length + 40) s += 10
            return s
        }
    }
}
