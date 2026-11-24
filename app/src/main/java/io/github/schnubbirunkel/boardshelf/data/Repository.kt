package io.github.schnubbirunkel.boardshelf.data

import android.content.Context
import android.net.Uri
import io.github.schnubbirunkel.boardshelf.R
import io.github.schnubbirunkel.boardshelf.net.BggApi
import io.github.schnubbirunkel.boardshelf.net.GameInfo
import io.github.schnubbirunkel.boardshelf.net.SearchHit
import io.github.schnubbirunkel.boardshelf.net.Source
import io.github.schnubbirunkel.boardshelf.net.WikiApi
import io.github.schnubbirunkel.boardshelf.net.download
import io.github.schnubbirunkel.boardshelf.util.AppFiles
import io.github.schnubbirunkel.boardshelf.util.Images
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
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
    val products: io.github.schnubbirunkel.boardshelf.net.ProductApi,
    val ai: io.github.schnubbirunkel.boardshelf.net.AiVision,
    val settings: SettingsStore,
) {
    val games: Flow<List<Game>> = db.games().observeAll()

    /** For work that should outlive a screen (automatic cover search). */
    private val background = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)

    /** Games handed over between screens so the detail view can draw instantly. */
    val handover = java.util.concurrent.ConcurrentHashMap<Long, Game>()
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

    /** Usable width of one shelf board in cm on this device (the shelf is drawn at 4.3 dp per cm). */
    private fun shelfCapacityCm(): Float {
        val dm = context.resources.displayMetrics
        val widthDp = minOf(dm.widthPixels, dm.heightPixels) / dm.density
        return ((widthDp - 44f) / 4.3f).coerceAtLeast(20f)
    }

    /**
     * Where a new box goes: the board after the last used one that still has room; when all
     * boards are full, a new board is added automatically.
     */
    private suspend fun placeForNewGame(depthCm: Float): Pair<Long, Int> {
        ensureShelves()
        val shelves = db.shelves().all()
        val games = db.games().all()
        val cap = shelfCapacityCm()
        val lastUsed = shelves.indexOfLast { s -> games.any { it.shelfId == s.id } }.coerceAtLeast(0)
        for (i in lastUsed until shelves.size) {
            val onShelf = games.filter { it.shelfId == shelves[i].id }
            val used = onShelf.sumOf { (it.boxDepthCm + 0.25f).toDouble() }
            if (onShelf.isEmpty() || used + depthCm <= cap) {
                return shelves[i].id to ((onShelf.maxOfOrNull { it.position } ?: -1) + 1)
            }
        }
        addShelf(null)
        return db.shelves().all().last().id to 0
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
        val bggJob = if (bgg.hasToken) async { runCatching { bgg.search(query) } } else null
        val wikiJob = if (s.useWikidata || !bgg.hasToken) async { runCatching { wiki.search(query) } } else null
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
        val hasToken = bgg.hasToken
        return when (hit.source) {
            Source.BGG -> enrichWithWiki(bgg.details(hit.id.toInt()))
            Source.GAMEUPC -> {
                val bggId = hit.bggId!!
                val base = if (hasToken) runCatching { bgg.details(bggId) }.getOrNull() else null
                base?.let { enrichWithWiki(it) } ?: run {
                    // no BGG token: take the details from Wikidata via the BGG id
                    val qid = runCatching { wiki.qidForBggId(bggId) }.getOrNull()
                    val w = qid?.let { runCatching { wiki.details(it) }.getOrNull() }
                    (w ?: GameInfo(name = hit.name)).copy(
                        name = hit.name, year = w?.year ?: hit.year, bggId = bggId,
                        imageUrl = hit.imageUrl ?: w?.imageUrl,
                    )
                }
            }
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

    suspend fun insertFromInfo(info: GameInfo, userRating: Float? = null, enrich: Boolean = true): Long {
        val draft = Game(name = info.name).withInfo(info)
        val (shelfId, pos) = placeForNewGame(draft.boxDepthCm)
        val game = draft.copy(shelfId = shelfId, position = pos, myRating = userRating?.coerceIn(0f, 10f) ?: 0f)
        val id = db.games().insert(game)
        info.imageUrl?.let { downloadCover(id, it) }
        if (enrich && info.publishers.none { it == "Boardshelf Demo" } && (ai.enabled || db.games().get(id)?.coverPath == null)) {
            // in the background: AI checks can take a while, the box appears on the shelf right away
            background.launch { runCatching { if (ai.enabled) aiEnrich(id) else { fillFromWiki(db.games().get(id)!!); autoCover(id) } } }
        }
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
            g.bggId != null && bgg.hasToken -> enrichWithWiki(bgg.details(g.bggId))
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

    /**
     * Downloads an image picked in the cover search into the cache (for cropping).
     * Supports http(s) and data: URLs and rejects tiny images (thumbnails, icons).
     */
    suspend fun downloadCoverCandidate(url: String, headers: Map<String, String>): File = withContext(Dispatchers.IO) {
        val tmp = newCoverCandidate()
        try {
            if (url.startsWith("data:")) {
                val b64 = url.substringAfter("base64,", "")
                if (b64.isEmpty()) throw IOException("Unsupported image")
                tmp.writeBytes(android.util.Base64.decode(b64, android.util.Base64.DEFAULT))
            } else {
                http.download(url, tmp, headers)
            }
            val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(tmp.path, o)
            if (o.outWidth <= 0 || o.outHeight <= 0) throw IOException("Not an image")
            if (minOf(o.outWidth, o.outHeight) < MIN_COVER_PX) throw CoverTooSmallException(o.outWidth, o.outHeight)
            tmp
        } catch (e: Exception) {
            tmp.delete(); throw e
        }
    }

    /** A fresh temp file for a cover that still has to be cropped. */
    fun newCoverCandidate(): File =
        File(File(context.cacheDir, "cover_candidates").apply { mkdirs() }, "${UUID.randomUUID()}.jpg")

    /** Copies the current cover (or a gallery image) into a temp file for cropping. */
    suspend fun coverCandidateFrom(source: File?, uri: Uri?): File = withContext(Dispatchers.IO) {
        val tmp = newCoverCandidate()
        when {
            uri != null -> files.copyUriTo(uri, tmp)
            source != null -> source.copyTo(tmp, overwrite = true)
            else -> throw IOException("No image")
        }
        tmp
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
        db.saveStates().forGame(game.id).forEach { st ->
            db.saveStatePhotos().forState(st.id).forEach { files.deleteRelative(it.path) }
            files.deleteRelative(st.photoPath)
        }
        db.manuals().forGame(game.id).forEach { files.deleteRelative(it.path) }
        files.deleteRelative(game.coverPath)
        db.games().delete(game)
    }

    // ---------------------------------------------------------------- barcode & covers

    data class BarcodeResult(
        val ean: String,
        val existing: Game?,
        val products: List<io.github.schnubbirunkel.boardshelf.net.Product>,
        val query: String?,
        val hits: List<SearchHit>,
    )

    /** Barcode → product databases → probable title → game search (Wikidata GTIN first). */
    suspend fun lookupBarcode(code: String): BarcodeResult = coroutineScope {
        val ean = code.filter { it.isDigit() }
        db.games().byEan(ean)?.let { return@coroutineScope BarcodeResult(ean, it, emptyList(), null, emptyList()) }
        val upcJob = async { runCatching { products.gameUpc(ean) }.getOrDefault(emptyList()) }
        val gtinHit = async { runCatching { wiki.byGtin(ean) }.getOrNull() }
        val productsJob = async { runCatching { products.byBarcode(ean) }.getOrDefault(emptyList()) }
        val upc = upcJob.await()
        val prods = productsJob.await()
        val query = upc.firstOrNull()?.name
            ?: prods.firstOrNull()?.let { io.github.schnubbirunkel.boardshelf.net.ProductApi.cleanTitle(it.title, it.brand) }
        val found = if (upc.isEmpty() && query != null) search(query).hits else emptyList()
        val hits = (upc + listOfNotNull(gtinHit.await()) + found).distinctBy { it.bggId?.let { b -> "bgg$b" } ?: (it.source.name + it.id) }
        BarcodeResult(ean, null, prods, query, hits)
    }

    /** Imports a hit found via barcode: remembers the EAN and uses the shop picture of exactly this box as cover. */
    suspend fun importFromBarcode(hit: SearchHit?, result: BarcodeResult, manualName: String? = null): Long {
        val id = if (hit != null) importHit(hit) else createManualGame(manualName ?: result.query ?: result.ean)
        hit?.confirmUrl?.let { url -> background.launch { runCatching { products.confirmGameUpc(url, installId()) } } }
        db.games().get(id)?.let { g -> if (g.ean == null) db.games().update(g.copy(ean = result.ean)) }
        val g = db.games().get(id) ?: return id
        if (g.coverPath == null) {
            for (url in (listOfNotNull(hit?.imageUrl) + result.products.flatMap { it.images }).distinct()) {
                if (trySetCover(id, url)) break
            }
        }
        return id
    }

    private suspend fun trySetCover(gameId: Long, url: String): Boolean = try {
        val f = downloadCoverCandidate(url, emptyMap())
        setCoverFromFile(gameId, f)
        f.delete()
        true
    } catch (e: Exception) {
        false
    }

    /** Cover suggestions from all key-less sources, best first. */
    suspend fun coverCandidates(game: Game): List<io.github.schnubbirunkel.boardshelf.net.CoverCandidate> = coroutineScope {
        val P = io.github.schnubbirunkel.boardshelf.net.ProductApi
        val out = mutableListOf<io.github.schnubbirunkel.boardshelf.net.CoverCandidate>()
        val byEan = game.ean?.let { e -> async { runCatching { products.byBarcode(e) }.getOrDefault(emptyList()) } }
        val byName = async { runCatching { products.searchProducts(game.name) }.getOrDefault(emptyList()) }
        val fromBgg = if (game.bggId != null && bgg.hasToken) async { runCatching { bgg.details(game.bggId).imageUrl }.getOrNull() } else null
        fromBgg?.await()?.let { out += io.github.schnubbirunkel.boardshelf.net.CoverCandidate(it, "BoardGameGeek", game.name, 100) }
        byEan?.await()?.forEach { p -> p.images.forEach { out += io.github.schnubbirunkel.boardshelf.net.CoverCandidate(it, p.source, p.title, 90) } }
        byName.await().forEach { p ->
            val sc = P.score(p, game.name, game.publishers)
            if (sc > 0) p.images.forEach { out += io.github.schnubbirunkel.boardshelf.net.CoverCandidate(it, p.source, p.title, sc) }
        }
        game.imageUrl?.let { out += io.github.schnubbirunkel.boardshelf.net.CoverCandidate(it, "Wikimedia", game.name, 40) }
        out.distinctBy { it.url }.sortedByDescending { it.score }
    }

    /**
     * Sets a cover automatically. With a vision AI configured, the AI checks the candidates
     * (front of the box? same game?) and the picture is cropped to the box it found. Without AI
     * only very likely candidates (score) are used.
     */
    suspend fun autoCover(gameId: Long, force: Boolean = false): Boolean {
        val g = db.games().get(gameId) ?: return false
        if (g.coverPath != null && !force) return true
        val candidates = coverCandidates(g)
        if (ai.enabled) {
            if (runCatching { aiCover(g, candidates) }.getOrDefault(false)) return true
            if (force) return false
        }
        for (c in candidates.filter { it.score >= 75 }.take(4)) {
            if (trySetCover(gameId, c.url)) return true
        }
        return false
    }

    /** Games the AI is currently researching (shown as progress in the detail screen). */
    val aiBusy = kotlinx.coroutines.flow.MutableStateFlow<Set<Long>>(emptySet())

    /**
     * The AI researches a newly added game on the web by itself: it fills in missing facts and
     * finds a picture of the box front, which is verified and cropped before it becomes the cover.
     * Self-hosted models without web access check Boardshelf's own candidates instead.
     */
    suspend fun aiEnrich(gameId: Long, replaceCover: Boolean = false): Boolean {
        aiBusy.value = aiBusy.value + gameId
        val name = db.games().get(gameId)?.name ?: return false
        try {
            var gotCover = false
            val imageUrls = mutableListOf<String>()
            if (ai.canSearchWeb) {
                val (filled, r) = aiResearchFacts(db.games().get(gameId)!!)
                log(name, "$filled Angaben ergänzt")
                imageUrls += r.boxImages
                // pictures declared by the pages the AI actually visited (more reliable than links it writes)
                for (page in (r.pages + r.sources).distinct().take(8)) {
                    imageUrls += runCatching { pageImages(page) }.getOrDefault(emptyList()).take(3)
                }
            }
            val g = db.games().get(gameId) ?: return false
            if (g.coverPath == null || replaceCover) {
                g.bggId?.let { id -> bggPageImage(id)?.let { imageUrls += it } }
                val candidates = imageUrls.distinct().map { io.github.schnubbirunkel.boardshelf.net.CoverCandidate(it, "AI", g.name, 100) } +
                    runCatching { coverCandidates(g) }.getOrDefault(emptyList())
                log(name, "${candidates.size} Bildkandidaten werden geprüft")
                gotCover = aiCover(g, candidates.distinctBy { it.url })
                log(name, if (gotCover) "Cover gesetzt" else "kein geprüftes Schachtelbild gefunden")
            }
            aiStatus.value = aiStatus.value + (gameId to (if (gotCover) "ok" else "nocover"))
            return gotCover
        } catch (t: Throwable) {
            log(name, "Fehler: ${t.message}")
            aiStatus.value = aiStatus.value + (gameId to ("error:" + (t.message ?: t.javaClass.simpleName)))
            throw t
        } finally {
            aiBusy.value = aiBusy.value - gameId
        }
    }

    /** Downloads the pictures the AI found, lets it verify them and uses the best one (cropped). */
    private suspend fun aiCoverFromUrls(g: Game, urls: List<String>): Boolean =
        aiCover(g, urls.distinct().map { io.github.schnubbirunkel.boardshelf.net.CoverCandidate(it, "AI", g.name, 100) })

    /** Lets the AI pick (and crop) the best cover among the candidates. Throws on AI errors. */
    suspend fun aiCover(g: Game, candidates: List<io.github.schnubbirunkel.boardshelf.net.CoverCandidate>): Boolean = withContext(Dispatchers.IO) {
        val loaded = mutableListOf<Pair<File, android.graphics.Bitmap>>()
        try {
            for (c in candidates.take(16)) {
                if (loaded.size >= 8) break
                val f = runCatching { downloadCoverCandidate(c.url, emptyMap()) }.getOrNull() ?: continue
                val bmp = Images.decodeSampled(f, 1600)
                if (bmp == null) { f.delete(); continue }
                loaded += f to bmp
            }
            log(g.name, "${loaded.size} Bilder geladen")
            if (loaded.isEmpty()) return@withContext false
            val verdicts = loaded.chunked(4).flatMapIndexed { chunk, part ->
                ai.judgeCovers(g.name, g.publishers?.substringBefore(','), g.year, part.map { it.second })
                    .map { it.copy(index = it.index + chunk * 4) }
            }
            val best = verdicts
                .filter { it.isFrontCover && it.sameGame && it.confidence >= 0.6f && it.index in loaded.indices }
                .maxByOrNull { it.confidence } ?: return@withContext false
            setCoverCropped(g.id, loaded[best.index].second, best.box)
            true
        } finally {
            loaded.forEach { it.first.delete() }
        }
    }

    /** Anonymous, random id of this installation (GameUPC uses it to spot abuse). */
    private fun installId(): String {
        val prefs = context.getSharedPreferences("install", Context.MODE_PRIVATE)
        return prefs.getString("id", null) ?: ("boardshelf-" + UUID.randomUUID().toString().take(12)).also { prefs.edit().putString("id", it).apply() }
    }

    fun infoFoundText(n: Int, sources: List<String>) = context.getString(R.string.info_found, n, sources.joinToString(", "))

    fun unknownBoxText(): String = context.getString(R.string.add_photo_ai_none)

    /** AI recognises the game on a photo of the box. */
    suspend fun identifyPhoto(file: File): Pair<io.github.schnubbirunkel.boardshelf.net.BoxIdentification, android.graphics.Bitmap>? =
        withContext(Dispatchers.IO) {
            val bmp = Images.decodeSampled(file, 1600) ?: return@withContext null
            ai.identifyBox(bmp)?.let { it to bmp }
        }

    /** Crops [bmp] to the relative [box] (with a small margin) and uses it as cover. */
    suspend fun setCoverCropped(gameId: Long, bmp: android.graphics.Bitmap, box: FloatArray?) = withContext(Dispatchers.IO) {
        val out = if (box == null) bmp else {
            val m = 0.01f
            val l = ((box[0] - m).coerceAtLeast(0f) * bmp.width).toInt()
            val t = ((box[1] - m).coerceAtLeast(0f) * bmp.height).toInt()
            val r = ((box[2] + m).coerceAtMost(1f) * bmp.width).toInt()
            val b = ((box[3] + m).coerceAtMost(1f) * bmp.height).toInt()
            if (r - l < 50 || b - t < 50) bmp else android.graphics.Bitmap.createBitmap(bmp, l, t, r - l, b - t)
        }
        val f = newCoverCandidate()
        f.outputStream().use { out.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, it) }
        setCoverFromFile(gameId, f)
        f.delete()
    }

    // ---------------------------------------------------------------- BGG collection export (CSV)

    data class CsvImportResult(val added: Int, val skipped: Int, val expansionsSkipped: Int)

    /** Progress of the background job that loads covers and details after an import. */
    val enrichProgress = kotlinx.coroutines.flow.MutableStateFlow<Pair<Int, Int>?>(null)
    private val enrichQueue = kotlinx.coroutines.channels.Channel<Long>(kotlinx.coroutines.channels.Channel.UNLIMITED)
    private var enrichTotal = 0
    private var enrichDone = 0

    init {
        // one worker, gently paced, so BGG/Wikidata are not hammered during a big import
        background.launch {
            for (id in enrichQueue) {
                runCatching { enrichImported(id) }
                enrichDone++
                enrichProgress.value = if (enrichDone >= enrichTotal) null else enrichDone to enrichTotal
                delay(1200)
            }
        }
    }

    /** Imports BoardGameGeek's collection export. New boards are added when the shelf is full. */
    suspend fun importBggCsv(text: String, includeExpansions: Boolean, onlyOwned: Boolean, progress: (Int, Int) -> Unit): CsvImportResult {
        if (!BggCsv.looksLikeExport(text)) throw IOException(context.getString(R.string.import_not_csv))
        val all = BggCsv.games(text).filter { !onlyOwned || it.owned }
        val expansions = all.count { it.isExpansion }
        val rows = all.filter { includeExpansions || !it.isExpansion }
        val existing = db.games().all().mapNotNull { it.bggId }.toSet()
        val todo = rows.filter { it.bggId !in existing }
        val newIds = mutableListOf<Long>()
        todo.forEachIndexed { i, r ->
            val id = insertFromInfo(
                GameInfo(
                    name = r.name, year = r.year, minPlayers = r.minPlayers, maxPlayers = r.maxPlayers,
                    playingTime = r.playingTime, minAge = r.minAge, bggRating = r.bggRating, bggWeight = r.bggWeight,
                    bggRank = r.bggRank, bggId = r.bggId,
                ),
                userRating = r.userRating, enrich = false,
            )
            r.comment?.let { c -> db.games().get(id)?.let { g -> db.games().update(g.copy(notes = c)) } }
            newIds += id
            progress(i + 1, todo.size)
        }
        enrichTotal += newIds.size
        enrichProgress.value = enrichDone to enrichTotal
        newIds.forEach { enrichQueue.send(it) }
        return CsvImportResult(todo.size, rows.size - todo.size, if (includeExpansions) 0 else expansions)
    }

    /** After an import: details from Wikidata/Wikipedia (by BGG id) and the cover from the BGG game page. */
    private suspend fun enrichImported(gameId: Long) {
        val g = db.games().get(gameId) ?: return
        fillFromWiki(g)
        val now = db.games().get(gameId) ?: return
        if (now.coverPath == null && now.bggId != null) {
            val img = bggPageImage(now.bggId)
            android.util.Log.i("Boardshelf", "import ${now.name}: bgg image $img")
            img?.let { if (!trySetCover(gameId, it)) android.util.Log.w("Boardshelf", "cover download failed: $it") }
        }
        if (db.games().get(gameId)?.coverPath == null) runCatching { autoCover(gameId) }
    }

    /** The cover picture BGG shows for a game (og:image of the public game page). */
    suspend fun bggPageImage(bggId: Int): String? =
        runCatching { pageImages("https://boardgamegeek.com/boardgame/$bggId").firstOrNull { "geekdo-images" in it } }.getOrNull()

    /** Picture URLs a web page declares for itself (og:image, twitter:image, JSON-LD image). */
    suspend fun pageImages(pageUrl: String): List<String> = withContext(Dispatchers.IO) {
        // like a browser: the device's WebView user agent and its cookies (e.g. after the BGG login)
        val ua = runCatching { android.webkit.WebSettings.getDefaultUserAgent(context) }.getOrNull()
            ?: "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
        val cookies = runCatching { android.webkit.CookieManager.getInstance().getCookie(pageUrl) }.getOrNull()
        val req = okhttp3.Request.Builder().url(pageUrl)
            .header("User-Agent", ua)
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Accept-Language", java.util.Locale.getDefault().toLanguageTag())
            .apply { if (!cookies.isNullOrBlank()) header("Cookie", cookies) }
            .build()
        val (finalUrl, html) = http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) {
                android.util.Log.w("Boardshelf", "page $pageUrl -> HTTP ${r.code}")
                return@withContext emptyList()
            }
            val ct = r.header("Content-Type").orEmpty()
            if (ct.startsWith("image/")) return@withContext listOf(r.request.url.toString())
            r.request.url to (r.body?.source()?.let { src -> src.request(800_000); src.buffer.clone().readUtf8() } ?: "")
        }
        val urls = mutableListOf<String>()
        Regex("<meta[^>]+(?:property|name)=[\"'](?:og:image(?::secure_url)?|twitter:image(?::src)?)[\"'][^>]*>", RegexOption.IGNORE_CASE).findAll(html).forEach { m ->
            Regex("content=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(m.value)?.groupValues?.get(1)?.let { urls += it }
        }
        Regex("<link[^>]+rel=[\"']image_src[\"'][^>]*href=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).findAll(html).forEach { urls += it.groupValues[1] }
        Regex("\"image\"\\s*:\\s*(?:\\[\\s*)?\"(https?:[^\"]+)\"").findAll(html).take(4).forEach { urls += it.groupValues[1].replace("\\/", "/") }
        val result = urls.mapNotNull { u -> runCatching { finalUrl.resolve(android.text.Html.fromHtml(u, 0).toString())?.toString() }.getOrNull() }
            .filter { it.startsWith("http") }.distinct()
        android.util.Log.i("Boardshelf", "page $pageUrl -> ${html.length} chars, images: ${result.take(3)}")
        result
    }

    // ---------------------------------------------------------------- info search

    /** Fills empty fields from Wikidata/Wikipedia (by BGG id, Wikidata id or exact name). Returns the number of filled fields. */
    suspend fun fillFromWiki(g: Game): Int {
        val qid = g.wikidataId
            ?: g.bggId?.let { runCatching { wiki.qidForBggId(it) }.getOrNull() }
            ?: runCatching { wiki.search(g.name) }.getOrNull()
                ?.firstOrNull { io.github.schnubbirunkel.boardshelf.net.ProductApi.norm(it.name) == io.github.schnubbirunkel.boardshelf.net.ProductApi.norm(g.name) }?.id
            ?: return 0
        val info = runCatching { wiki.details(qid) }.getOrNull() ?: return 0
        return fillMissing(g.id, info.copy(name = g.name), wikidataId = qid)
    }

    /** Writes only fields that are still empty. Returns how many were filled. */
    private suspend fun fillMissing(gameId: Long, info: GameInfo, wikidataId: String? = null): Int {
        val cur = db.games().get(gameId) ?: return 0
        var n = 0
        fun <T> pick(old: T?, new: T?): T? = if (old == null && new != null) { n++; new } else old
        fun pickList(old: String?, new: List<String>): String? = if (old.isNullOrBlank() && new.isNotEmpty()) { n++; new.joinToString(", ") } else old
        val updated = cur.copy(
            wikidataId = cur.wikidataId ?: wikidataId,
            year = pick(cur.year, info.year),
            minPlayers = pick(cur.minPlayers, info.minPlayers),
            maxPlayers = pick(cur.maxPlayers, info.maxPlayers),
            playingTime = pick(cur.playingTime, info.playingTime),
            minAge = pick(cur.minAge, info.minAge),
            description = pick(cur.description?.takeIf { it.isNotBlank() }, info.description),
            designers = pickList(cur.designers, info.designers),
            publishers = pickList(cur.publishers, info.publishers),
            categories = pickList(cur.categories, info.categories),
            mechanics = pickList(cur.mechanics, info.mechanics),
            bggRating = pick(cur.bggRating, info.bggRating),
            bggWeight = pick(cur.bggWeight, info.bggWeight),
            bggRank = pick(cur.bggRank, info.bggRank),
            wikiExtract = cur.wikiExtract ?: info.wikiExtract,
            wikiUrl = cur.wikiUrl ?: info.wikiUrl,
            imageUrl = cur.imageUrl ?: info.imageUrl,
            updatedAt = System.currentTimeMillis(),
        )
        if (updated != cur) db.games().update(updated)
        return n
    }

    data class InfoSearchResult(val filled: Int, val sources: List<String>, val errors: List<Throwable>)

    /** "Search info online": BGG (with token), Wikidata/Wikipedia and – if configured – the AI's web research. */
    suspend fun searchInfoOnline(gameId: Long): InfoSearchResult {
        val sources = mutableListOf<String>()
        val errors = mutableListOf<Throwable>()
        var filled = 0
        val g = db.games().get(gameId) ?: return InfoSearchResult(0, sources, errors)
        if (g.bggId != null && bgg.hasToken) {
            runCatching { fillMissing(gameId, bgg.details(g.bggId)) }.onSuccess { filled += it; sources += "BoardGameGeek" }.onFailure { errors += it }
        }
        runCatching { fillFromWiki(db.games().get(gameId)!!) }.onSuccess { if (it > 0) sources += "Wikidata/Wikipedia"; filled += it }.onFailure { errors += it }
        if (ai.canSearchWeb) {
            val cur = db.games().get(gameId)!!
            runCatching { aiResearchFacts(cur) }.onSuccess { (n, _) -> filled += n; sources += aiName() }.onFailure { errors += it; log(cur.name, "Fehler: ${it.message}") }
        }
        return InfoSearchResult(filled, sources, errors)
    }

    private fun aiName() = when (settings.current.aiProvider) {
        io.github.schnubbirunkel.boardshelf.net.AiProvider.CLAUDE -> "Claude"
        io.github.schnubbirunkel.boardshelf.net.AiProvider.GEMINI -> "Gemini"
        io.github.schnubbirunkel.boardshelf.net.AiProvider.OPENAI -> "ChatGPT"
        else -> "KI"
    }

    /** Short log of what the AI did (shown in the settings, helps when nothing is found). */
    val aiLog = kotlinx.coroutines.flow.MutableStateFlow<List<String>>(emptyList())
    /** Last AI result per game (shown on the detail page). */
    val aiStatus = kotlinx.coroutines.flow.MutableStateFlow<Map<Long, String>>(emptyMap())

    private fun log(game: String, msg: String) {
        val t = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        aiLog.value = (listOf("$t  $game: $msg") + aiLog.value).take(60)
    }

    /** AI web research → fills empty fields; returns (filled fields, research). */
    private suspend fun aiResearchFacts(g: Game): Pair<Int, io.github.schnubbirunkel.boardshelf.net.GameResearch> {
        val lang = java.util.Locale.getDefault().getDisplayLanguage(java.util.Locale.ENGLISH).ifBlank { "English" }
        log(g.name, "Websuche gestartet (${aiName()})")
        val r = ai.researchGame(g.name, g.year, g.publishers?.substringBefore(','), g.ean, lang)
        log(g.name, "Antwort: ${listOfNotNull(r.minPlayers?.let { "Spieler" }, r.playingTime?.let { "Dauer" }, r.description?.let { "Beschreibung" }).joinToString()} · ${r.boxImages.size} Bildlinks · ${r.sources.size} Quellen")
        val n = fillMissing(
            g.id,
            GameInfo(
                name = g.name, year = r.year, minPlayers = r.minPlayers, maxPlayers = r.maxPlayers, playingTime = r.playingTime,
                minAge = r.minAge, description = r.description, designers = r.designers, publishers = r.publishers, categories = r.categories,
            )
        )
        val cur = db.games().get(g.id)
        val size = r.boxSizeCm?.sortedArrayDescending()
        if (cur != null && size != null && cur.boxWidthCm == 29.6f && cur.boxHeightCm == 29.6f && cur.boxDepthCm == 7.2f) {
            db.games().update(cur.copy(boxWidthCm = size[0], boxHeightCm = size[1], boxDepthCm = size[2]))
        }
        return n to r
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

    fun saveStatePhotos(gameId: Long): Flow<List<SaveStatePhoto>> = db.saveStatePhotos().observeForGame(gameId)
    suspend fun photosOf(stateId: Long): List<SaveStatePhoto> = db.saveStatePhotos().forState(stateId)

    /** Stores a photo (camera file or gallery uri) as JPEG and returns the relative path. */
    private suspend fun storePhoto(file: File?, uri: Uri?): String = withContext(Dispatchers.IO) {
        val target = files.newFile("savestates", "jpg")
        if (file != null) Images.saveScaledJpeg(file, target)
        else {
            val tmp = File(context.cacheDir, "import_${UUID.randomUUID()}.jpg")
            try {
                files.copyUriTo(uri!!, tmp); Images.saveScaledJpeg(tmp, target)
            } finally {
                tmp.delete()
            }
        }
        files.relative(target)
    }

    /** A save state with one or more photos of the table. */
    suspend fun addSaveState(gameId: Long, photos: List<File>, uris: List<Uri>, title: String, note: String?): Long = withContext(Dispatchers.IO) {
        val paths = photos.map { storePhoto(it, null) } + uris.map { storePhoto(null, it) }
        require(paths.isNotEmpty())
        val id = db.saveStates().insert(SaveState(gameId = gameId, title = title, note = note?.takeIf { it.isNotBlank() }, photoPath = paths.first()))
        paths.forEachIndexed { i, p -> db.saveStatePhotos().insert(SaveStatePhoto(saveStateId = id, path = p, position = i)) }
        photos.forEach { it.delete() }
        id
    }

    suspend fun addSaveState(gameId: Long, photo: File, title: String, note: String?): Long =
        addSaveState(gameId, listOf(photo), emptyList(), title, note)

    suspend fun addPhotos(stateId: Long, photos: List<File>, uris: List<Uri>) = withContext(Dispatchers.IO) {
        var pos = (db.saveStatePhotos().forState(stateId).maxOfOrNull { it.position } ?: -1) + 1
        (photos.map { storePhoto(it, null) } + uris.map { storePhoto(null, it) }).forEach { p ->
            db.saveStatePhotos().insert(SaveStatePhoto(saveStateId = stateId, path = p, position = pos++))
        }
        photos.forEach { it.delete() }
    }

    /** Removes one photo; the state's main photo follows the first remaining one. Last photo → state is deleted. */
    suspend fun deletePhoto(photo: SaveStatePhoto) = withContext(Dispatchers.IO) {
        val state = db.saveStates().get(photo.saveStateId) ?: return@withContext
        val rest = db.saveStatePhotos().forState(state.id).filter { it.id != photo.id }
        if (rest.isEmpty()) { deleteSaveState(state); return@withContext }
        files.deleteRelative(photo.path)
        db.saveStatePhotos().delete(photo)
        db.saveStatePhotos().updateAll(rest.mapIndexed { i, p -> p.copy(position = i) })
        db.saveStates().update(state.copy(photoPath = rest.first().path))
    }

    suspend fun updateSaveState(state: SaveState) = db.saveStates().update(state)

    suspend fun deleteSaveState(state: SaveState) = withContext(Dispatchers.IO) {
        db.saveStatePhotos().forState(state.id).forEach { files.deleteRelative(it.path) }
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

    companion object {
        const val MIN_COVER_PX = 150
    }
}

class CoverTooSmallException(val w: Int, val h: Int) : IOException("Image too small: ${w}x$h")
