package io.github.schnubbirunkel.ludothek.ui.box3d

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.LruCache
import androidx.core.graphics.ColorUtils
import io.github.schnubbirunkel.ludothek.R
import io.github.schnubbirunkel.ludothek.data.Game
import io.github.schnubbirunkel.ludothek.data.playersText
import io.github.schnubbirunkel.ludothek.util.AppFiles
import io.github.schnubbirunkel.ludothek.util.Images
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Bitmaps for the six faces of a game box. Faces that are never visible may be null. */
class BoxTextures(
    val front: Bitmap,
    val back: Bitmap?,
    val left: Bitmap?,
    val right: Bitmap?,
    val top: Bitmap?,
    val bottom: Bitmap?,
    val color: Int,
) {
    val bytes: Int
        get() = listOfNotNull(front, back, left, right, top, bottom).sumOf { it.allocationByteCount }
}

/** Generates and caches box textures from the cover image and the game's data. */
class TextureCache(private val context: Context, private val files: AppFiles) {

    private val cache = object : LruCache<String, BoxTextures>((Runtime.getRuntime().maxMemory() / 6).toInt()) {
        override fun sizeOf(key: String, value: BoxTextures) = value.bytes
    }

    private fun key(g: Game, size: Int, full: Boolean) = listOf(
        g.id, g.coverPath, g.name, g.spineColor, g.year, g.minPlayers, g.maxPlayers, g.playingTime, g.minAge,
        g.boxWidthCm.roundToInt(), g.boxHeightCm.roundToInt(), (g.boxDepthCm * 2).roundToInt(), size, full
    ).joinToString("|")

    fun peek(g: Game, size: Int, full: Boolean): BoxTextures? = cache.get(key(g, size, full))

    suspend fun get(g: Game, size: Int, full: Boolean): BoxTextures {
        val k = key(g, size, full)
        cache.get(k)?.let { return it }
        return withContext(Dispatchers.Default) {
            build(g, size, full).also { cache.put(k, it) }
        }
    }

    fun clear() = cache.evictAll()

    private fun build(g: Game, size: Int, full: Boolean): BoxTextures {
        val w = g.boxWidthCm.coerceAtLeast(1f)
        val h = g.boxHeightCm.coerceAtLeast(1f)
        val d = g.boxDepthCm.coerceAtLeast(0.5f)
        val ppc = size / max(w, h)
        val fw = (w * ppc).roundToInt().coerceAtLeast(8)
        val fh = (h * ppc).roundToInt().coerceAtLeast(8)
        val dp = (d * ppc).roundToInt().coerceAtLeast(6)
        val config = if (full) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565

        val color = g.spineColor ?: colorFromName(g.name)
        val cover = files.resolve(g.coverPath)?.let { Images.decodeSampled(it, size * 2) }

        val front = Bitmap.createBitmap(fw, fh, config)
        Canvas(front).let { c ->
            if (cover != null) drawCropped(c, cover, fw, fh) else drawGeneratedFront(c, g, color, fw, fh)
            drawBevel(c, fw, fh)
        }
        val right = sideTexture(g, color, dp, fh, rotate = 90f, config)
        val top = topTexture(g, color, fw, dp, config)
        val back = if (full) backTexture(g, color, cover, fw, fh) else null
        val left = if (full) sideTexture(g, color, dp, fh, rotate = -90f, config) else null
        val bottom = if (full) Bitmap.createBitmap(fw, dp, config).apply {
            eraseColor(ColorUtils.blendARGB(color, Color.BLACK, 0.45f))
        } else null
        return BoxTextures(front, back, left, right, top, bottom, color)
    }

    private fun drawCropped(c: Canvas, src: Bitmap, w: Int, h: Int) {
        val scale = max(w.toFloat() / src.width, h.toFloat() / src.height)
        val sw = (w / scale).roundToInt()
        val sh = (h / scale).roundToInt()
        val sx = (src.width - sw) / 2
        val sy = (src.height - sh) / 2
        c.drawBitmap(src, Rect(sx, sy, sx + sw, sy + sh), Rect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG))
    }

    private fun drawGeneratedFront(c: Canvas, g: Game, color: Int, w: Int, h: Int) {
        val light = ColorUtils.blendARGB(color, Color.WHITE, 0.35f)
        val dark = ColorUtils.blendARGB(color, Color.BLACK, 0.35f)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), light, dark, Shader.TileMode.CLAMP)
        })
        // decorative frame
        val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = w * 0.015f; this.color = 0x66FFFFFF
        }
        c.drawRoundRect(RectF(w * 0.06f, h * 0.06f, w * 0.94f, h * 0.94f), w * 0.04f, w * 0.04f, frame)
        drawWrappedText(c, g.name, w, h, (w * 0.12f), Color.WHITE, centerY = h * 0.42f)
        g.year?.let {
            val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = 0xCCFFFFFF.toInt(); textSize = w * 0.06f; textAlign = Paint.Align.CENTER
            }
            c.drawText(it.toString(), w / 2f, h * 0.86f, p)
        }
    }

    private fun drawWrappedText(c: Canvas, text: String, w: Int, h: Int, startSize: Float, color: Int, centerY: Float) {
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            setShadowLayer(startSize * 0.08f, 0f, startSize * 0.05f, 0x99000000.toInt())
        }
        var size = startSize
        var layout: StaticLayout
        val maxW = (w * 0.82f).toInt()
        do {
            tp.textSize = size
            layout = StaticLayout.Builder.obtain(text, 0, text.length, tp, maxW)
                .setAlignment(Layout.Alignment.ALIGN_CENTER).setMaxLines(4)
                .setEllipsize(TextUtils.TruncateAt.END).build()
            size *= 0.9f
        } while ((layout.height > h * 0.55f || (0 until layout.lineCount).any { layout.getLineWidth(it) > maxW }) && size > 6f)
        c.save()
        c.translate((w - maxW) / 2f, centerY - layout.height / 2f)
        layout.draw(c)
        c.restore()
    }

    private fun sideTexture(g: Game, color: Int, sw: Int, sh: Int, rotate: Float, config: Bitmap.Config): Bitmap {
        val bmp = Bitmap.createBitmap(sw, sh, config)
        val c = Canvas(bmp)
        c.drawRect(0f, 0f, sw.toFloat(), sh.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, sw.toFloat(), 0f,
                ColorUtils.blendARGB(color, Color.BLACK, 0.15f), ColorUtils.blendARGB(color, Color.WHITE, 0.1f), Shader.TileMode.MIRROR)
        })
        val stripe = Paint().apply { this.color = 0x33FFFFFF }
        c.drawRect(0f, sh * 0.04f, sw.toFloat(), sh * 0.045f, stripe)
        c.drawRect(0f, sh * 0.955f, sw.toFloat(), sh * 0.96f, stripe)
        // Title runs along the spine.
        val textColor = if (ColorUtils.calculateLuminance(color) > 0.5) Color.BLACK else Color.WHITE
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = textColor; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
        }
        tp.textSize = sw * 0.5f
        val maxLen = sh * 0.82f
        while (tp.measureText(g.name) > maxLen && tp.textSize > sw * 0.28f) tp.textSize *= 0.92f
        val title = TextUtils.ellipsize(g.name, tp, maxLen, TextUtils.TruncateAt.END).toString()
        c.save()
        c.translate(sw / 2f, sh / 2f)
        c.rotate(rotate)
        val fm = tp.fontMetrics
        c.drawText(title, 0f, -(fm.ascent + fm.descent) / 2f, tp)
        c.restore()
        drawBevel(c, sw, sh)
        return bmp
    }

    private fun topTexture(g: Game, color: Int, tw: Int, th: Int, config: Bitmap.Config): Bitmap {
        val bmp = Bitmap.createBitmap(tw, th, config)
        val c = Canvas(bmp)
        c.drawColor(ColorUtils.blendARGB(color, Color.WHITE, 0.08f))
        val textColor = if (ColorUtils.calculateLuminance(color) > 0.5) Color.BLACK else Color.WHITE
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = textColor; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
            textSize = th * 0.45f
        }
        val title = TextUtils.ellipsize(g.name, tp, tw * 0.9f, TextUtils.TruncateAt.END).toString()
        val fm = tp.fontMetrics
        c.drawText(title, tw / 2f, th / 2f - (fm.ascent + fm.descent) / 2f, tp)
        drawBevel(c, tw, th)
        return bmp
    }

    private fun backTexture(g: Game, color: Int, cover: Bitmap?, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val bg = ColorUtils.blendARGB(color, Color.BLACK, 0.3f)
        c.drawColor(bg)
        var y = h * 0.06f
        if (cover != null) {
            val ih = h * 0.42f
            val iw = min(w * 0.8f, ih * cover.width / cover.height)
            val dst = RectF((w - iw) / 2f, y, (w + iw) / 2f, y + ih)
            c.drawBitmap(cover, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
            c.drawRect(dst, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = w * 0.006f; this.color = 0x88FFFFFF.toInt()
            })
            y += ih + h * 0.05f
        } else y += h * 0.12f
        val title = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER; textSize = w * 0.075f
        }
        c.drawText(TextUtils.ellipsize(g.name, title, w * 0.9f, TextUtils.TruncateAt.END).toString(), w / 2f, y + title.textSize, title)
        y += title.textSize * 2.2f
        val info = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = 0xEEFFFFFF.toInt(); textAlign = Paint.Align.CENTER; textSize = w * 0.05f
        }
        val parts = listOfNotNull(
            g.playersText?.let { context.getString(R.string.box_players, it) },
            g.playingTime?.let { context.getString(R.string.box_minutes, it) },
            g.minAge?.let { context.getString(R.string.box_age, it) },
        )
        if (parts.isNotEmpty()) {
            c.drawText(parts.joinToString("   ·   "), w / 2f, y, info); y += info.textSize * 1.8f
        }
        g.designers?.let {
            info.textSize = w * 0.04f
            c.drawText(TextUtils.ellipsize(it, info, w * 0.9f, TextUtils.TruncateAt.END).toString(), w / 2f, y, info)
        }
        drawBevel(c, w, h)
        return bmp
    }

    private fun drawBevel(c: Canvas, w: Int, h: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = max(1f, min(w, h) * 0.02f); color = 0x40000000
        }
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
    }

    companion object {
        private val palette = intArrayOf(
            0xFF8E3B2E.toInt(), 0xFF2E5E8E.toInt(), 0xFF3E7D4F.toInt(), 0xFF7A4E8C.toInt(),
            0xFFB07A2A.toInt(), 0xFF2A7A78.toInt(), 0xFF8C2F5A.toInt(), 0xFF4A4E9A.toInt(),
        )

        fun colorFromName(name: String): Int = palette[(name.hashCode() and 0x7fffffff) % palette.size]
    }
}
