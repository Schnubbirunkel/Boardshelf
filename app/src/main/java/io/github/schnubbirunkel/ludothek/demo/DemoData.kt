package io.github.schnubbirunkel.ludothek.demo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import io.github.schnubbirunkel.ludothek.R
import io.github.schnubbirunkel.ludothek.data.Repository
import io.github.schnubbirunkel.ludothek.net.GameInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** A fictional demo collection with generated cover art (no network needed). */
object DemoData {

    private data class Demo(
        val name: String, val year: Int, val players: Pair<Int, Int>, val time: Int, val age: Int,
        val w: Float, val h: Float, val d: Float, val c1: Int, val c2: Int, val rating: Float, val motif: Int,
        val designer: String,
    )

    private val demos = listOf(
        Demo("Sternenhandel", 2019, 2 to 4, 90, 12, 29.6f, 29.6f, 7.2f, 0xFF1B2A6B.toInt(), 0xFF8E44AD.toInt(), 8.5f, 0, "A. Weber"),
        Demo("Die Gärten von Lumen", 2021, 1 to 4, 60, 10, 29.6f, 29.6f, 7.2f, 0xFF1E6B3A.toInt(), 0xFFB5E36B.toInt(), 9f, 1, "M. Laurent"),
        Demo("Kohle & Kolonien", 2017, 2 to 5, 120, 14, 31.5f, 31.5f, 10f, 0xFF3B2A1E.toInt(), 0xFFD35400.toInt(), 7.5f, 2, "K. Brandt"),
        Demo("Drachenhort", 2022, 3 to 6, 45, 10, 26.5f, 26.5f, 6.5f, 0xFF6B1B1B.toInt(), 0xFFF39C12.toInt(), 8f, 3, "S. Okafor"),
        Demo("Leuchtturmwärter", 2020, 1 to 2, 30, 8, 19f, 13f, 4.5f, 0xFF0E3B5C.toInt(), 0xFF5DADE2.toInt(), 7f, 4, "J. Nielsen"),
        Demo("Atlas der Winde", 2023, 2 to 4, 75, 12, 29.6f, 29.6f, 7.2f, 0xFF16525A.toInt(), 0xFFF7DC6F.toInt(), 0f, 0, "R. Costa"),
        Demo("Der Uhrmacher", 2018, 1 to 4, 100, 14, 30.5f, 30.5f, 9f, 0xFF2C2C2C.toInt(), 0xFFC9A227.toInt(), 9.5f, 2, "E. Fischer"),
        Demo("Nebelinseln", 2024, 2 to 4, 50, 10, 25.5f, 25.5f, 6f, 0xFF4A5D6B.toInt(), 0xFFD5DBDB.toInt(), 6.5f, 1, "L. Moreau"),
        Demo("Pilzsammler", 2016, 2 to 2, 25, 10, 15f, 11.5f, 3.5f, 0xFF6E3B1E.toInt(), 0xFFE74C3C.toInt(), 8f, 3, "T. Berg"),
        Demo("Kartograf der Tiefe", 2021, 1 to 5, 40, 10, 22f, 22f, 5.5f, 0xFF0B2B26.toInt(), 0xFF48C9B0.toInt(), 7.5f, 4, "P. Haas"),
        Demo("Vulkanfeld", 2015, 2 to 4, 60, 10, 29.6f, 29.6f, 7.2f, 0xFF2B0F0F.toInt(), 0xFFFF5722.toInt(), 6f, 0, "D. Ruiz"),
        Demo("Hexenküche", 2022, 3 to 5, 35, 9, 24f, 24f, 6f, 0xFF3A1B4F.toInt(), 0xFF7DCEA0.toInt(), 8.5f, 1, "N. Petrova"),
        Demo("Zugvögel", 2020, 1 to 5, 70, 10, 29.6f, 29.6f, 7.2f, 0xFF1F4E79.toInt(), 0xFFF5B041.toInt(), 9f, 4, "C. Lind"),
        Demo("Bergbahn 1907", 2019, 2 to 4, 120, 12, 39f, 26f, 8f, 0xFF4D3B2A.toInt(), 0xFF85C1E9.toInt(), 7f, 2, "H. Graf"),
        Demo("Mondpost", 2023, 2 to 6, 20, 8, 12f, 17f, 3f, 0xFF1C1C3A.toInt(), 0xFFF4D03F.toInt(), 0f, 3, "Y. Tanaka"),
    )

    suspend fun seed(context: Context, repo: Repository) = withContext(Dispatchers.IO) {
        repo.ensureShelves()
        val shelves = repo.db.shelves().all()
        val names = context.resources.getStringArray(R.array.demo_shelf_names)
        shelves.forEachIndexed { i, s -> if (i < names.size) repo.renameShelf(s, names[i]) }
        val desc = context.getString(R.string.demo_description)
        var firstId = -1L
        demos.forEachIndexed { i, d ->
            val id = repo.insertFromInfo(
                GameInfo(
                    name = d.name, year = d.year, minPlayers = d.players.first, maxPlayers = d.players.second,
                    playingTime = d.time, minAge = d.age, description = desc, designers = listOf(d.designer),
                    publishers = listOf("Ludothek Demo"), categories = listOf("Demo"),
                    bggRating = 6.5 + (i % 5) * 0.4, bggWeight = 1.5 + (i % 4) * 0.7,
                    boxWidthCm = d.w, boxHeightCm = d.h, boxDepthCm = d.d,
                ), d.rating
            )
            if (firstId < 0) firstId = id
            val cover = File(context.cacheDir, "demo_cover_$i.jpg")
            cover.outputStream().use { coverArt(d).compress(Bitmap.CompressFormat.JPEG, 90, it) }
            repo.setCoverFromFile(id, cover)
            cover.delete()
            // distribute over the shelves
            val g = repo.db.games().get(id)!!
            val shelf = shelves[(i * shelves.size) / demos.size]
            repo.updateGame(g.copy(shelfId = shelf.id, position = i, ratingFun = (d.rating / 2).toInt(), ratingLooks = 4, ratingReplay = 3, ratingRules = 4))
        }
        // a save state and a scanned manual for the first game
        if (firstId > 0) {
            val table = File(context.cacheDir, "demo_table.jpg")
            table.outputStream().use { tableArt().compress(Bitmap.CompressFormat.JPEG, 90, it) }
            repo.addSaveState(firstId, table, context.getString(R.string.demo_savestate_title), context.getString(R.string.demo_savestate_note))
            val pages = (1..3).map { p ->
                File(context.cacheDir, "demo_page_$p.jpg").also { f -> f.outputStream().use { pageArt(p).compress(Bitmap.CompressFormat.JPEG, 90, it) } }
            }
            repo.addScannedManual(firstId, context.getString(R.string.manual_scan_default_title, demos[0].name), pages)
            pages.forEach { it.delete() }; table.delete()
        }
    }

    private fun coverArt(d: Demo): Bitmap {
        val w = 700
        val h = (w * d.h / d.w).toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val rnd = Random(d.name.hashCode())
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, h.toFloat(), d.c1, d.c2, Shader.TileMode.CLAMP)
        })
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        when (d.motif) {
            0 -> { // stars and planet
                p.color = Color.WHITE
                repeat(80) { c.drawCircle(rnd.nextFloat() * w, rnd.nextFloat() * h * 0.7f, rnd.nextFloat() * 2.5f + 0.5f, p) }
                p.shader = RadialGradient(w * 0.7f, h * 0.62f, w * 0.3f, d.c2, d.c1, Shader.TileMode.CLAMP)
                c.drawCircle(w * 0.68f, h * 0.66f, w * 0.26f, p); p.shader = null
            }
            1 -> { // leaves / hills
                repeat(5) { k ->
                    p.color = Color.argb(70 + k * 30, 255, 255, 255)
                    val path = Path().apply {
                        moveTo(0f, h * (0.62f + k * 0.07f))
                        cubicTo(w * 0.3f, h * (0.5f + k * 0.06f), w * 0.6f, h * (0.75f + k * 0.03f), w.toFloat(), h * (0.58f + k * 0.08f))
                        lineTo(w.toFloat(), h.toFloat()); lineTo(0f, h.toFloat()); close()
                    }
                    c.drawPath(path, p)
                }
            }
            2 -> { // gears
                p.style = Paint.Style.STROKE; p.strokeWidth = w * 0.025f
                listOf(Triple(0.3f, 0.68f, 0.16f), Triple(0.62f, 0.62f, 0.11f), Triple(0.78f, 0.8f, 0.08f)).forEach { (x, y, r) ->
                    p.color = Color.argb(150, 255, 230, 160)
                    c.drawCircle(w * x, h * y, w * r, p)
                    for (t in 0 until 12) {
                        val a = t * Math.PI / 6
                        c.drawLine(w * x + (w * r * cos(a)).toFloat(), h * y + (w * r * sin(a)).toFloat(),
                            w * x + (w * r * 1.25f * cos(a)).toFloat(), h * y + (w * r * 1.25f * sin(a)).toFloat(), p)
                    }
                }
                p.style = Paint.Style.FILL
            }
            3 -> { // mountains with a moon
                p.color = Color.argb(220, 255, 240, 200)
                c.drawCircle(w * 0.75f, h * 0.38f, w * 0.09f, p)
                p.color = Color.argb(200, 20, 10, 10)
                c.drawPath(Path().apply {
                    moveTo(0f, h.toFloat()); lineTo(w * 0.25f, h * 0.55f); lineTo(w * 0.42f, h * 0.75f)
                    lineTo(w * 0.62f, h * 0.48f); lineTo(w.toFloat(), h * 0.85f); lineTo(w.toFloat(), h.toFloat()); close()
                }, p)
            }
            else -> { // waves and lighthouse beam
                p.color = Color.argb(60, 255, 255, 200)
                c.drawPath(Path().apply { moveTo(w * 0.5f, h * 0.55f); lineTo(0f, h * 0.35f); lineTo(0f, h * 0.6f); close() }, p)
                p.color = Color.argb(140, 255, 255, 255)
                p.style = Paint.Style.STROKE; p.strokeWidth = 6f
                for (k in 0 until 6) {
                    val y = h * (0.72f + k * 0.05f)
                    c.drawPath(Path().apply {
                        moveTo(0f, y)
                        var x = 0f
                        while (x < w) { quadTo(x + 35f, y - 18f, x + 70f, y); x += 70f }
                    }, p)
                }
                p.style = Paint.Style.FILL
            }
        }
        // title
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD); textSize = w * 0.11f
            setShadowLayer(10f, 0f, 4f, Color.argb(180, 0, 0, 0))
        }
        val layout = StaticLayout.Builder.obtain(d.name, 0, d.name.length, tp, (w * 0.86f).toInt())
            .setAlignment(Layout.Alignment.ALIGN_CENTER).build()
        c.save(); c.translate(w * 0.07f, h * 0.1f); layout.draw(c); c.restore()
        val small = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textSize = w * 0.04f; textAlign = Paint.Align.CENTER
            setShadowLayer(6f, 0f, 2f, Color.BLACK)
        }
        c.drawText("${d.players.first}–${d.players.second}  ·  ${d.time}'  ·  ${d.age}+", w / 2f, h * 0.95f, small)
        return bmp
    }

    private fun tableArt(): Bitmap {
        val w = 1600; val h = 1200
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), 0xFF8B5A2B.toInt(), 0xFF5C3A1A.toInt(), Shader.TileMode.CLAMP)
        })
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        // board with hex grid
        p.color = 0xFF1B2A6B.toInt()
        c.drawRect(200f, 150f, 1400f, 1050f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 3f; p.color = 0x88FFFFFF.toInt()
        val r = 60f
        var row = 0
        var y = 220f
        while (y < 1000f) {
            var x = 260f + if (row % 2 == 1) r * 0.87f else 0f
            while (x < 1360f) {
                val path = Path()
                for (k in 0..5) {
                    val a = Math.PI / 3 * k + Math.PI / 6
                    val px = x + (r * cos(a)).toFloat(); val py = y + (r * sin(a)).toFloat()
                    if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close(); c.drawPath(path, p)
                x += r * 1.74f
            }
            y += r * 1.5f; row++
        }
        p.style = Paint.Style.FILL
        val rnd = Random(7)
        val colors = intArrayOf(0xFFE74C3C.toInt(), 0xFFF1C40F.toInt(), 0xFF2ECC71.toInt(), 0xFF3498DB.toInt())
        repeat(26) {
            p.color = colors[it % 4]
            val cx = 280f + rnd.nextFloat() * 1040f; val cy = 230f + rnd.nextFloat() * 760f
            c.drawCircle(cx, cy - 18f, 14f, p); c.drawRoundRect(cx - 18f, cy - 6f, cx + 18f, cy + 20f, 6f, 6f, p)
        }
        // cards
        repeat(5) {
            p.color = Color.WHITE
            c.save(); c.rotate(-12f + it * 6f, 1500f, 1150f)
            c.drawRoundRect(1420f + it * 20f, 700f, 1560f + it * 20f, 900f, 12f, 12f, p)
            c.restore()
        }
        return bmp
    }

    private fun pageArt(page: Int): Bitmap {
        val w = 1240; val h = 1754
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(0xFFFBF6EC.toInt())
        val title = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3B2614.toInt(); textSize = 64f; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD) }
        c.drawText(if (page == 1) "Spielregel" else "Spielablauf – Seite $page", 100f, 180f, title)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF9E8A79.toInt() }
        val rnd = Random(page)
        var y = 280f
        while (y < h - 150) {
            if (rnd.nextInt(7) == 0) {
                p.color = 0xFFD9C3A5.toInt(); c.drawRoundRect(100f, y, w - 100f, y + 260f, 20f, 20f, p); y += 300f; p.color = 0xFF9E8A79.toInt()
            } else {
                c.drawRoundRect(100f, y, 100f + (w - 200f) * (0.6f + rnd.nextFloat() * 0.4f), y + 18f, 9f, 9f, p); y += 44f
            }
        }
        return bmp
    }
}
