package io.github.schnubbirunkel.boardshelf.net

enum class Source { BGG, WIKIDATA, GAMEUPC }

data class SearchHit(
    val source: Source,
    val id: String,
    val name: String,
    val year: Int? = null,
    val thumbnail: String? = null,
    val subtitle: String? = null,
    val bggId: Int? = null,
    /** Large cover picture, if the source knows one (GameUPC). */
    val imageUrl: String? = null,
    /** GameUPC: URL to confirm this barcode → game mapping (crowd-sourced database). */
    val confirmUrl: String? = null,
)

/** Game information gathered from an online source. Box dimensions in centimetres. */
data class GameInfo(
    val name: String,
    val year: Int? = null,
    val minPlayers: Int? = null,
    val maxPlayers: Int? = null,
    val playingTime: Int? = null,
    val minAge: Int? = null,
    val description: String? = null,
    val imageUrl: String? = null,
    val designers: List<String> = emptyList(),
    val publishers: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
    val mechanics: List<String> = emptyList(),
    val bggRating: Double? = null,
    val bggWeight: Double? = null,
    val bggRank: Int? = null,
    val boxWidthCm: Float? = null,
    val boxHeightCm: Float? = null,
    val boxDepthCm: Float? = null,
    val bggId: Int? = null,
    val wikidataId: String? = null,
    val wikiExtract: String? = null,
    val wikiUrl: String? = null,
)

class BggAuthException : Exception("BGG token missing or invalid")
class BggTokenMissingException : Exception("No BGG token configured")
