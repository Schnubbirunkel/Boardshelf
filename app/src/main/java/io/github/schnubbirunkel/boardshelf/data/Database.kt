package io.github.schnubbirunkel.boardshelf.data

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

    @Query("SELECT * FROM games WHERE ean = :ean LIMIT 1")
    suspend fun byEan(ean: String): Game?

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
interface SaveStatePhotoDao {
    @Query("SELECT * FROM save_state_photos WHERE saveStateId IN (SELECT id FROM save_states WHERE gameId = :gameId) ORDER BY saveStateId, position")
    fun observeForGame(gameId: Long): Flow<List<SaveStatePhoto>>

    @Query("SELECT * FROM save_state_photos WHERE saveStateId = :stateId ORDER BY position")
    suspend fun forState(stateId: Long): List<SaveStatePhoto>

    @Insert
    suspend fun insert(photo: SaveStatePhoto): Long

    @Update
    suspend fun updateAll(photos: List<SaveStatePhoto>)

    @Delete
    suspend fun delete(photo: SaveStatePhoto)
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
    entities = [Game::class, Shelf::class, SaveState::class, SaveStatePhoto::class, Manual::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun games(): GameDao
    abstract fun shelves(): ShelfDao
    abstract fun saveStates(): SaveStateDao
    abstract fun manuals(): ManualDao
    abstract fun saveStatePhotos(): SaveStatePhotoDao

    companion object {
        const val NAME = "boardshelf.db"
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NAME).addMigrations(MIGRATION_1_2).build()

        /** 1.3.0: barcode per game, several photos per save state. */
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `games` ADD COLUMN `ean` TEXT")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `save_state_photos` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`saveStateId` INTEGER NOT NULL, `path` TEXT NOT NULL, `position` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`saveStateId`) REFERENCES `save_states`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_save_state_photos_saveStateId` ON `save_state_photos` (`saveStateId`)")
                db.execSQL("INSERT INTO `save_state_photos` (`saveStateId`, `path`, `position`) SELECT `id`, `photoPath`, 0 FROM `save_states`")
            }
        }
    }
}
