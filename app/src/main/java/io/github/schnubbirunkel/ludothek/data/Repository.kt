package io.github.schnubbirunkel.ludothek.data

import android.content.Context
import android.net.Uri
import io.github.schnubbirunkel.ludothek.R
import io.github.schnubbirunkel.ludothek.net.BggApi
import io.github.schnubbirunkel.ludothek.net.GameInfo
import io.github.schnubbirunkel.ludothek.net.SearchHit
import io.github.schnubbirunkel.ludothek.net.Source
import io.github.schnubbirunkel.ludothek.net.WikiApi
import io.github.schnubbirunkel.ludothek.net.download
import io.github.schnubbirunkel.ludothek.util.AppFiles
import io.github.schnubbirunkel.ludothek.util.Images
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class Repository(
    private val context: Context,
    val db: AppDatabase,
    val files: AppFiles,
    val http: OkHttpClient,
    val bgg: BggApi,
    val wiki: WikiApi,
    val settings: SettingsStore,
) {
    val games: Flow<List<Game>> = db.games().observeAll()
    val shelves: Flow<List<Shelf>> = db.shelves().observeAll()
    fun game(id: Long): Flow<Game?> = db.games().observe(id)
    fun saveStates(gameId: Long): Flow<List<SaveState>> = db.saveStates().observeForGame(gameId)
    fun manuals(gameId: Long): Flow<List<Manual>> = db.manuals().observeForGame(gameId)
    suspend fun manual(id: Long) = db.manuals().get(id)
    suspend fun saveState(id: Long) = db.saveStates().get(id)

    // ---------------------------------------------------------------- shelves

    suspend fun ensureShelves() {
        if (db.shelves().all().isEmpty()) {
            repeat(4) { i ->
                db.shelves().insert(Shelf(name = context.getString(R.string.shelf_default_name, i + 1), position = i))
            }
        }
    }

    suspend fun addShelf(name: String?) {
        val all = db.shelves().all()
        val n = name?.takeIf { it.isNotBlank() } ?: context.getString(R.string.shelf_default_name, all.size + 1)
        db.shelves().insert(Shelf(name = n, position = (all.maxOfOrNull { it.position } ?: -1) + 1))
    }

    suspend fun renameShelf(shelf: Shelf, name: String) = db.shelves().update(shelf.copy(name = name))

    suspend fun moveShelf(shelf: Shelf, delta: Int) {
        val all = db.shelves().all().toMutableList()
        val i = all.indexOfFirst { it.id == shelf.id }
        val j = (i + delta).coerceIn(0, all.lastIndex)
        if (i < 0 || i == j) return
        val s = all.removeAt(i)
        all.add(j, s)
        db.shelves().updateAll(all.mapIndexed { idx, sh -> sh.copy(position = idx) })
    }

    /** Deletes a shelf; its games move to the end of a neighbouring shelf. */
    suspend fun deleteShelf(shelf: Shelf): Boolean {
        val all = db.shelves().all()
        if (all.size <= 1) return false
        val idx = all.indexOfFirst { it.id == shelf.id }
        val target = if (idx > 0) all[idx - 1] else all[1]
        var pos = (db.games().maxPosition(target.id) ?: -1) + 1
        val moved = db.games().all().filter { it.shelfId == shelf.id }.sortedBy { it.position }
            .map { it.copy(shelfId = target.id, position = pos++) }
        db.games().updateAll(moved)
        db.shelves().delete(shelf)
        return true
    }

    /** Persists a complete ordering: shelf id -> game ids from left to right. */
    suspend fun applyOrder(order: Map<Long, List<Long>>) {
        val byId = db.games().all().associateBy { it.id }
        val changed = mutableListOf<Game>()
        order.forEach { (shelfId, ids) ->
            ids.forEachIndexed { index, id ->
                val g = byId[id] ?: return@forEachIndexed
                if (g.shelfId != shelfId || g.position != index) changed += g.copy(shelfId = shelfId, position = index)
            }
        }
        if (changed.isNotEmpty()) db.games().updateAll(changed)
    }

    // ---------------------------------------------------------------- search & import

    data class SearchResult(val hits: List<SearchHit>, val errors: List<Throwable>)

    suspend fun search(query: String): SearchResult = coroutineScope {
        val s = settings.current
        val bggJob = if (s.bggToken.isNotBlank()) async { runCatching { bgg.search(query) } } else null
        val wikiJob = if (s.useWikidata || s.bggToken.isBlank()) async { runCatching { wiki.search(query) } } else null
        val bggRes = bggJob?.await()
        val wikiRes = wikiJob?.await()
        val bggHits = bggRes?.getOrNull().orEmpty()
        val knownBgg = bggHits.mapNotNull { it.bggId }.toSet()
        // Wikidata entries that are already covered by a BGG hit are dropped.
        val wikiHits = wikiRes?.getOrNull().orEmpty().filter { it.bggId == null || it.bggId !in knownBgg }
        SearchResult(
            hits = bggHits + wikiHits,
            errors = listOfNotNull(bggRes?.exceptionOrNull(), wikiRes?.exceptionOrNull())
        )
    }

    /** Imports a search hit and returns the id of the (new or existing) game. */
    suspend fun importHit(hit: SearchHit): Long {
        hit.bggId?.let { id -> db.games().byBggId(id)?.let { return it.id } }
        val info = loadInfo(hit)
        info.bggId?.let { id -> db.games().byBggId(id)?.let { return it.id } }
        return insertFromInfo(info)
    }

    private suspend fun loadInfo(hit: SearchHit): GameInfo {
        val hasToken = settings.current.bggToken.isNotBlank()
        return when (hit.source) {
            Source.BGG -> enrichWithWiki(bgg.details(hit.id.toInt()))
            Source.WIKIDATA -> {
                val w = wiki.details(hit.id)
                val bggId = w.bggId
                if (bggId != null && hasToken) {
                    runCatching { bgg.details(bggId) }.getOrNull()?.copy(
                        wikidataId = w.wikidataId, wikiExtract = w.wikiExtract, wikiUrl = w.wikiUrl
                    ) ?: w
                } else w
            }
        }
    }

    private suspend fun enrichWithWiki(info: GameInfo): GameInfo {
        if (!settings.current.useWikidata) return info
        val bggId = info.bggId ?: return info
        val s = runCatching { wiki.summaryForBggId(bggId) }.getOrNull() ?: return info
        return info.copy(wikiExtract = s.first, wikiUrl = s.second, wikidataId = s.third)
    }

    suspend fun insertFromInfo(info: GameInfo, userRating: Float? = null): Long {
        ensureShelves()
        val shelf = db.shelves().all().last()
        val pos = (db.games().maxPosition(shelf.id) ?: -1) + 1
        val game = Game(shelfId = shelf.id, position = pos, name = info.name).withInfo(info)
            .copy(myRating = userRating?.coerceIn(0f, 10f) ?: 0f)
        val id = db.games().insert(game)
        info.imageUrl?.let { downloadCover(id, it) }
        return id
    }

    private fun Game.withInfo(info: GameInfo): Game = copy(
        bggId = info.bggId ?: bggId,
        wikidataId = info.wikidataId ?: wikidataId,
        name = info.name,
        year = info.year ?: year,
        minPlayers = info.minPlayers ?: minPlayers,
        maxPlayers = info.maxPlayers ?: maxPlayers,
        playingTime = info.playingTime ?: playingTime,
        minAge = info.minAge ?: minAge,
        description = info.description ?: description,
        imageUrl = info.imageUrl ?: imageUrl,
        designers = info.designers.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: designers,
        publishers = info.publishers.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: publishers,
        categories = info.categories.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: categories,
        mechanics = info.mechanics.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: mechanics,
        bggRating = info.bggRating ?: bggRating,
        bggWeight = info.bggWeight ?: bggWeight,
        bggRank = info.bggRank ?: bggRank,
        boxWidthCm = info.boxWidthCm ?: boxWidthCm,
        boxHeightCm = info.boxHeightCm ?: boxHeightCm,
        boxDepthCm = info.boxDepthCm ?: boxDepthCm,
        wikiExtract = info.wikiExtract ?: wikiExtract,
        wikiUrl = info.wikiUrl ?: wikiUrl,
        updatedAt = System.currentTimeMillis(),
    )

    /** Reloads the online data of a game but keeps everything personal. */
    suspend fun refreshFromSources(gameId: Long) {
        val g = db.games().get(gameId) ?: return
        val info = when {
            g.bggId != null && settings.current.bggToken.isNotBlank() -> enrichWithWiki(bgg.details(g.bggId))
            g.wikidataId != null -> wiki.details(g.wikidataId)
            else -> throw IOException("No online source linked")
        }
        db.games().update(g.withInfo(info))
        if (g.coverPath == null) info.imageUrl?.let { downloadCover(gameId, it) }
    }

    suspend fun downloadCover(gameId: Long, url: String) {
        try {
            val f = files.newFile("covers", "jpg")
            http.download(url, f)
            setCoverInternal(gameId, f)
        } catch (e: Exception) {
            // Cover is optional – the box gets a generated front instead.
        }
    }

    /** Uses an own photo (camera or gallery) as cover. */
    suspend fun setCoverFromFile(gameId: Long, source: File) = withContext(Dispatchers.IO) {
        val f = files.newFile("covers", "jpg")
        Images.saveScaledJpeg(source, f, maxDim = 1400)
        setCoverInternal(gameId, f)
    }

    private suspend fun setCoverInternal(gameId: Long, f: File) = withContext(Dispatchers.IO) {
        val color = Images.spineColorOf(f)
        val g = db.games().get(gameId) ?: return@withContext
        files.deleteRelative(g.coverPath)
        db.games().update(g.copy(coverPath = files.relative(f), spineColor = color ?: g.spineColor, updatedAt = System.currentTimeMillis()))
    }

    suspend fun updateGame(game: Game) = db.games().update(game.copy(updatedAt = System.currentTimeMillis()))

    suspend fun createManualGame(name: String): Long =
        insertFromInfo(GameInfo(name = name))

    suspend fun deleteGame(game: Game) = withContext(Dispatchers.IO) {
        db.saveStates().forGame(game.id).forEach { files.deleteRelative(it.photoPath) }
        db.manuals().forGame(game.id).forEach { files.deleteRelative(it.path) }
        files.deleteRelative(game.coverPath)
        db.games().delete(game)
    }

    // ---------------------------------------------------------------- BGG collection import

    data class ImportResult(val added: Int, val skipped: Int)

    suspend fun importBggCollection(username: String, progress: (done: Int, total: Int) -> Unit): ImportResult {
        val entries = bgg.collection(username)
        val existing = db.games().all().mapNotNull { it.bggId }.toSet()
        val todo = entries.filter { it.bggId !in existing }
        var done = 0
        progress(0, todo.size)
        todo.chunked(20).forEachIndexed { i, chunk ->
            if (i > 0) delay(2500)
            val infos = bgg.detailsBatch(chunk.map { it.bggId }).associateBy { it.bggId }
            chunk.forEach { entry ->
                val info = infos[entry.bggId] ?: GameInfo(name = entry.name, bggId = entry.bggId)
                insertFromInfo(info, entry.userRating)
                done++
                progress(done, todo.size)
            }
        }
        return ImportResult(added = todo.size, skipped = entries.size - todo.size)
    }

    // ---------------------------------------------------------------- save states

    suspend fun addSaveState(gameId: Long, photo: File, title: String, note: String?): Long = withContext(Dispatchers.IO) {
        val target = files.newFile("savestates", "jpg")
        Images.saveScaledJpeg(photo, target)
        db.saveStates().insert(SaveState(gameId = gameId, title = title, note = note?.takeIf { it.isNotBlank() }, photoPath = files.relative(target)))
    }

    suspend fun addSaveStateFromUri(gameId: Long, uri: Uri, title: String, note: String?): Long = withContext(Dispatchers.IO) {
        val tmp = File(context.cacheDir, "import_${UUID.randomUUID()}.jpg")
        try {
            files.copyUriTo(uri, tmp)
            addSaveState(gameId, tmp, title, note)
        } finally {
            tmp.delete()
        }
    }

    suspend fun updateSaveState(state: SaveState) = db.saveStates().update(state)

    suspend fun deleteSaveState(state: SaveState) = withContext(Dispatchers.IO) {
        files.deleteRelative(state.photoPath)
        db.saveStates().delete(state)
    }

    // ---------------------------------------------------------------- manuals

    suspend fun addManualPdfFromUri(gameId: Long, uri: Uri, title: String): Long = withContext(Dispatchers.IO) {
        val f = files.newFile("manuals", "pdf")
        files.copyUriTo(uri, f)
        if (!Images.isPdf(f)) {
            f.delete(); throw IOException("Not a PDF")
        }
        db.manuals().insert(Manual(gameId = gameId, title = title, kind = ManualKind.PDF, path = files.relative(f)))
    }

    suspend fun downloadManual(gameId: Long, url: String, title: String, headers: Map<String, String> = emptyMap()): Long =
        withContext(Dispatchers.IO) {
            val f = files.newFile("manuals", "pdf")
            try {
                http.download(url, f, headers)
            } catch (e: Exception) {
                f.delete(); throw e
            }
            if (!Images.isPdf(f)) {
                f.delete(); throw IOException("Not a PDF")
            }
            db.manuals().insert(Manual(gameId = gameId, title = title, kind = ManualKind.PDF, path = files.relative(f), sourceUrl = url))
        }

    /** Stores scanned pages (camera photos) as an image manual. */
    suspend fun addScannedManual(gameId: Long, title: String, pages: List<File>): Long = withContext(Dispatchers.IO) {
        val dir = files.dir("manuals/scan_${UUID.randomUUID()}")
        pages.forEachIndexed { i, p ->
            Images.saveScaledJpeg(p, File(dir, "page_%03d.jpg".format(i + 1)), maxDim = 2400, quality = 90)
        }
        db.manuals().insert(Manual(gameId = gameId, title = title, kind = ManualKind.IMAGES, path = files.relative(dir), pageCount = pages.size))
    }

    suspend fun renameManual(manual: Manual, title: String) = db.manuals().update(manual.copy(title = title))

    suspend fun deleteManual(manual: Manual) = withContext(Dispatchers.IO) {
        files.deleteRelative(manual.path)
        db.manuals().delete(manual)
    }

    fun manualPages(manual: Manual): List<File> =
        files.resolve(manual.path)?.listFiles()?.filter { it.isFile }?.sortedBy { it.name }.orEmpty()

    /** Converts an image manual to a single PDF in the cache (for sharing). */
    suspend fun manualAsPdf(manual: Manual): File = withContext(Dispatchers.IO) {
        if (manual.kind == ManualKind.PDF) return@withContext files.resolve(manual.path)!!
        val out = File(File(context.cacheDir, "share").apply { mkdirs() }, manual.title.replace(Regex("[^\\w\\- ]"), "_") + ".pdf")
        val doc = android.graphics.pdf.PdfDocument()
        manualPages(manual).forEachIndexed { i, page ->
            val bmp = Images.decodeSampled(page, 1600) ?: return@forEachIndexed
            val info = android.graphics.pdf.PdfDocument.PageInfo.Builder(bmp.width, bmp.height, i + 1).create()
            val p = doc.startPage(info)
            p.canvas.drawBitmap(bmp, 0f, 0f, null)
            doc.finishPage(p)
        }
        out.outputStream().use { doc.writeTo(it) }
        doc.close()
        out
    }

    // ---------------------------------------------------------------- backup

    suspend fun exportBackup(target: Uri) = withContext(Dispatchers.IO) {
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
        val dbFile = context.getDatabasePath(AppDatabase.NAME)
        context.contentResolver.openOutputStream(target).use { os ->
            requireNotNull(os)
            ZipOutputStream(os.buffered()).use { zip ->
                zip.putNextEntry(ZipEntry("db/${AppDatabase.NAME}"))
                dbFile.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("settings/bgg_username.txt"))
                zip.write(settings.current.bggUsername.toByteArray())
                zip.closeEntry()
                listOf("covers", "savestates", "manuals").forEach { dirName ->
                    val dir = File(files.root, dirName)
                    dir.walkTopDown().filter { it.isFile }.forEach { f ->
                        zip.putNextEntry(ZipEntry("files/" + f.relativeTo(files.root).path))
                        f.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            }
        }
    }

    /** Restores a backup. The app has to be restarted afterwards. */
    suspend fun importBackup(source: Uri) = withContext(Dispatchers.IO) {
        val staging = File(context.cacheDir, "restore").apply { deleteRecursively(); mkdirs() }
        context.contentResolver.openInputStream(source).use { input ->
            requireNotNull(input)
            ZipInputStream(input.buffered()).use { zip ->
                var e = zip.nextEntry
                while (e != null) {
                    val out = File(staging, e.name).canonicalFile
                    if (!out.path.startsWith(staging.canonicalPath)) throw IOException("Bad zip entry")
                    if (e.isDirectory) out.mkdirs() else {
                        out.parentFile?.mkdirs()
                        out.outputStream().use { zip.copyTo(it) }
                    }
                    e = zip.nextEntry
                }
            }
        }
        val newDb = File(staging, "db/${AppDatabase.NAME}")
        if (!newDb.exists()) throw IOException("No database in backup")
        db.close()
        listOf("covers", "savestates", "manuals").forEach { File(files.root, it).deleteRecursively() }
        File(staging, "files").takeIf { it.exists() }?.copyRecursively(files.root, overwrite = true)
        val dbFile = context.getDatabasePath(AppDatabase.NAME)
        listOf("", "-wal", "-shm", "-journal").forEach { File(dbFile.path + it).delete() }
        newDb.copyTo(dbFile, overwrite = true)
        File(staging, "settings/bgg_username.txt").takeIf { it.exists() }?.readText()?.let { u ->
            if (u.isNotBlank()) settings.update { it.copy(bggUsername = u) }
        }
        staging.deleteRecursively()
    }
}
