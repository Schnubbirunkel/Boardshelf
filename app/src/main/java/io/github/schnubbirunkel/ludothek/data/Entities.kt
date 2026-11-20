package io.github.schnubbirunkel.ludothek.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** One board of the shelf (a "Regalboden"). */
@Entity(tableName = "shelves")
data class Shelf(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val position: Int,
)

/**
 * A game in the collection. File paths are stored relative to the app's files dir
 * so that backups can be restored on another device.
 */
@Entity(tableName = "games", indices = [Index("shelfId"), Index("bggId")])
data class Game(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bggId: Int? = null,
    val wikidataId: String? = null,
    val name: String,
    val year: Int? = null,
    val minPlayers: Int? = null,
    val maxPlayers: Int? = null,
    val playingTime: Int? = null,
    val minAge: Int? = null,
    val description: String? = null,
    val imageUrl: String? = null,
    val coverPath: String? = null,
    val designers: String? = null,
    val publishers: String? = null,
    val categories: String? = null,
    val mechanics: String? = null,
    val bggRating: Double? = null,
    val bggWeight: Double? = null,
    val bggRank: Int? = null,
    val boxWidthCm: Float = 29.6f,
    val boxHeightCm: Float = 29.6f,
    val boxDepthCm: Float = 7.2f,
    val spineColor: Int? = null,
    /** Personal overall rating 0..10 (0 = not rated), half steps allowed. */
    val myRating: Float = 0f,
    val ratingFun: Int = 0,
    val ratingReplay: Int = 0,
    val ratingLooks: Int = 0,
    val ratingRules: Int = 0,
    val review: String? = null,
    val notes: String? = null,
    val wikiExtract: String? = null,
    val wikiUrl: String? = null,
    val shelfId: Long? = null,
    val position: Int = 0,
    val addedAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

val Game.playersText: String?
    get() = when {
        minPlayers == null && maxPlayers == null -> null
        minPlayers == maxPlayers || maxPlayers == null -> "${minPlayers ?: maxPlayers}"
        minPlayers == null -> "$maxPlayers"
        else -> "$minPlayers–$maxPlayers"
    }

/** A photo of the game table that preserves the current state of a running game. */
@Entity(
    tableName = "save_states",
    foreignKeys = [ForeignKey(
        entity = Game::class, parentColumns = ["id"], childColumns = ["gameId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("gameId")]
)
data class SaveState(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val gameId: Long,
    val title: String,
    val note: String? = null,
    val photoPath: String,
    val createdAt: Long = System.currentTimeMillis(),
)

object ManualKind {
    const val PDF = 0
    const val IMAGES = 1
}

/** A rulebook: either a PDF file or a set of scanned page images (stored in a directory). */
@Entity(
    tableName = "manuals",
    foreignKeys = [ForeignKey(
        entity = Game::class, parentColumns = ["id"], childColumns = ["gameId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("gameId")]
)
data class Manual(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val gameId: Long,
    val title: String,
    val kind: Int,
    val path: String,
    val sourceUrl: String? = null,
    val pageCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)
