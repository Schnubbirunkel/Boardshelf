package io.github.schnubbirunkel.ludothek.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {
    @Query("SELECT * FROM games ORDER BY shelfId, position")
    fun observeAll(): Flow<List<Game>>

    @Query("SELECT * FROM games WHERE id = :id")
    fun observe(id: Long): Flow<Game?>

    @Query("SELECT * FROM games WHERE id = :id")
    suspend fun get(id: Long): Game?

    @Query("SELECT * FROM games")
    suspend fun all(): List<Game>

    @Query("SELECT * FROM games WHERE bggId = :bggId LIMIT 1")
    suspend fun byBggId(bggId: Int): Game?

    @Query("SELECT MAX(position) FROM games WHERE shelfId = :shelfId")
    suspend fun maxPosition(shelfId: Long): Int?

    @Query("SELECT COUNT(*) FROM games")
    suspend fun count(): Int

    @Insert
    suspend fun insert(game: Game): Long

    @Update
    suspend fun update(game: Game)

    @Update
    suspend fun updateAll(games: List<Game>)

    @Delete
    suspend fun delete(game: Game)
}

@Dao
interface ShelfDao {
    @Query("SELECT * FROM shelves ORDER BY position")
    fun observeAll(): Flow<List<Shelf>>

    @Query("SELECT * FROM shelves ORDER BY position")
    suspend fun all(): List<Shelf>

    @Insert
    suspend fun insert(shelf: Shelf): Long

    @Update
    suspend fun update(shelf: Shelf)

    @Update
    suspend fun updateAll(shelves: List<Shelf>)

    @Delete
    suspend fun delete(shelf: Shelf)
}

@Dao
interface SaveStateDao {
    @Query("SELECT * FROM save_states WHERE gameId = :gameId ORDER BY createdAt DESC")
    fun observeForGame(gameId: Long): Flow<List<SaveState>>

    @Query("SELECT * FROM save_states WHERE gameId = :gameId")
    suspend fun forGame(gameId: Long): List<SaveState>

    @Query("SELECT * FROM save_states WHERE id = :id")
    suspend fun get(id: Long): SaveState?

    @Insert
    suspend fun insert(state: SaveState): Long

    @Update
    suspend fun update(state: SaveState)

    @Delete
    suspend fun delete(state: SaveState)
}

@Dao
interface ManualDao {
    @Query("SELECT * FROM manuals WHERE gameId = :gameId ORDER BY createdAt")
    fun observeForGame(gameId: Long): Flow<List<Manual>>

    @Query("SELECT * FROM manuals WHERE gameId = :gameId")
    suspend fun forGame(gameId: Long): List<Manual>

    @Query("SELECT * FROM manuals WHERE id = :id")
    suspend fun get(id: Long): Manual?

    @Insert
    suspend fun insert(manual: Manual): Long

    @Update
    suspend fun update(manual: Manual)

    @Delete
    suspend fun delete(manual: Manual)
}

@Database(
    entities = [Game::class, Shelf::class, SaveState::class, Manual::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun games(): GameDao
    abstract fun shelves(): ShelfDao
    abstract fun saveStates(): SaveStateDao
    abstract fun manuals(): ManualDao

    companion object {
        const val NAME = "ludothek.db"
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NAME).build()
    }
}
