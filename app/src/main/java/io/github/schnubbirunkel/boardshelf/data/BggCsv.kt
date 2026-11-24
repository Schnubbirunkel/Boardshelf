package io.github.schnubbirunkel.boardshelf.data

/** One row of BoardGameGeek's collection export ("Export" → CSV on the collection page). */
data class BggCsvGame(
    val bggId: Int,
    val name: String,
    val year: Int?,
    val minPlayers: Int?,
    val maxPlayers: Int?,
    val playingTime: Int?,
    val minAge: Int?,
    val userRating: Float?,
    val bggRating: Double?,
    val bggWeight: Double?,
    val bggRank: Int?,
    val isExpansion: Boolean,
    val owned: Boolean,
    val comment: String?,
)

object BggCsv {

    /** RFC-4180-ish CSV parser (quoted fields, doubled quotes, line breaks inside quotes). */
    fun parse(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        val t = text.removePrefix("﻿")
        while (i < t.length) {
            val c = t[i]
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < t.length && t[i + 1] == '"') { field.append('"'); i++ } else quoted = false
                } else field.append(c)
            } else when (c) {
                '"' -> quoted = true
                ',' -> { row.add(field.toString()); field.clear() }
                '\r' -> {}
                '\n' -> { row.add(field.toString()); field.clear(); rows.add(row); row = mutableListOf() }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) { row.add(field.toString()); rows.add(row) }
        return rows.filter { r -> r.any { it.isNotBlank() } }
    }

    fun looksLikeExport(text: String): Boolean = text.take(4000).let { it.contains("objectname") && it.contains("objectid") }

    fun games(text: String): List<BggCsvGame> {
        val rows = parse(text)
        if (rows.isEmpty()) return emptyList()
        val header = rows.first().map { it.trim().lowercase() }
        fun idx(vararg names: String) = names.firstNotNullOfOrNull { n -> header.indexOf(n).takeIf { it >= 0 } }
        val iName = idx("objectname", "originalname") ?: return emptyList()
        val iId = idx("objectid") ?: return emptyList()
        val iYear = idx("yearpublished")
        val iMin = idx("minplayers")
        val iMax = idx("maxplayers")
        val iTime = idx("playingtime", "maxplaytime")
        val iAge = idx("bggrecagerange", "minage")
        val iRating = idx("rating")
        val iAvg = idx("average", "baverage")
        val iWeight = idx("avgweight")
        val iRank = idx("rank")
        val iType = idx("itemtype", "subtype", "objecttype")
        val iOwn = idx("own")
        val iComment = idx("comment", "privatecomment")

        fun List<String>.at(i: Int?) = i?.let { getOrNull(it) }?.trim()?.takeIf { it.isNotEmpty() && it != "N/A" }
        fun num(s: String?) = s?.replace(',', '.')?.toDoubleOrNull()

        return rows.drop(1).mapNotNull { r ->
            val id = r.at(iId)?.toIntOrNull() ?: return@mapNotNull null
            val type = r.at(iType)?.lowercase().orEmpty()
            BggCsvGame(
                bggId = id,
                name = r.at(iName) ?: return@mapNotNull null,
                year = r.at(iYear)?.toIntOrNull()?.takeIf { it > 0 },
                minPlayers = r.at(iMin)?.toIntOrNull()?.takeIf { it > 0 },
                maxPlayers = r.at(iMax)?.toIntOrNull()?.takeIf { it > 0 },
                playingTime = r.at(iTime)?.toIntOrNull()?.takeIf { it > 0 },
                minAge = r.at(iAge)?.filter { it.isDigit() }?.toIntOrNull()?.takeIf { it in 1..99 },
                userRating = num(r.at(iRating))?.toFloat()?.takeIf { it > 0f },
                bggRating = num(r.at(iAvg))?.takeIf { it > 0 },
                bggWeight = num(r.at(iWeight))?.takeIf { it > 0 },
                bggRank = r.at(iRank)?.toIntOrNull()?.takeIf { it > 0 },
                isExpansion = "expansion" in type,
                owned = r.at(iOwn)?.let { it == "1" } ?: true,
                comment = r.at(iComment),
            )
        }.distinctBy { it.bggId }
    }
}
