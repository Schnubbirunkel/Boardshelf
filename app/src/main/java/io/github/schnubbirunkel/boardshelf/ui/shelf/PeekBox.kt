package io.github.schnubbirunkel.boardshelf.ui.shelf

import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextUtils
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import io.github.schnubbirunkel.boardshelf.data.Game
import io.github.schnubbirunkel.boardshelf.ui.box3d.BoxRenderer
import io.github.schnubbirunkel.boardshelf.ui.box3d.TextureCache
import io.github.schnubbirunkel.boardshelf.ui.box3d.rememberBoxTextures
import kotlin.math.min

private const val PEEK_YAW = -14f
private const val PEEK_PITCH = 8f

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

/**
 * A box pulled out of the shelf by the peek gesture. [p] runs from 0 (standing in the shelf,
 * spine to the front) to 1 (in front of the shelf, turned so the cover is visible). Drawn in the
 * shelf's content coordinates.
 */
@Composable
fun PeekBox(g: Game, item: ItemPos, p: Float, @Suppress("UNUSED_PARAMETER") layout: ShelfLayout) {
    // the small shelf textures are already loaded – use them until the large ones are ready
    val small = rememberBoxTextures(g, 300, full = false)
    val large = rememberBoxTextures(g, 700, full = true)
    val tex = large ?: small
    val label = listOfNotNull(g.name, g.year?.toString()).joinToString(" · ")
    Canvas(Modifier.fillMaxSize()) {
        val t = p.coerceIn(0f, 1.15f) // the spring may overshoot a little – looks like a small "pop"
        val r = t.coerceIn(0f, 1f)
        val w = g.boxWidthCm; val h = g.boxHeightCm; val d = g.boxDepthCm

        val start = BoxRenderer.placement(
            w, h, d, SPINE_YAW, SHELF_PITCH,
            RectF(item.x, item.y, item.x + item.w, item.y + item.h), BoxRenderer.Fit.BOUNDS_BOTTOM
        )
        val margin = 10.dp.toPx()
        val tw = min(size.width * 0.6f, 250.dp.toPx())
        val th = tw * 1.2f
        val cx = (item.x + item.w / 2f).coerceIn(tw / 2f + margin, size.width - tw / 2f - margin)
        val cy = item.y + item.h / 2f - 28.dp.toPx()
        val end = BoxRenderer.placement(w, h, d, PEEK_YAW, PEEK_PITCH, RectF(cx - tw / 2f, cy - th / 2f, cx + tw / 2f, cy + th / 2f), BoxRenderer.Fit.SPHERE_CENTER)

        val scale = lerp(start[0], end[0], t)
        val ox = lerp(start[1], end[1], t)
        val oy = lerp(start[2], end[2], t)
        val yaw = lerp(SPINE_YAW, PEEK_YAW, r)
        val pitch = lerp(SHELF_PITCH, PEEK_PITCH, r)

        // soft darkening around the pulled-out box so it stands out from the shelf
        if (r > 0.05f) {
            drawCircle(
                Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.55f * r), Color.Transparent), center = Offset(ox, oy), radius = tw),
                radius = tw, center = Offset(ox, oy)
            )
        }
        drawIntoCanvas { c ->
            BoxRenderer.draw(
                c.nativeCanvas, tex, w, h, d, yaw, pitch,
                RectF(0f, 0f, size.width, size.height), BoxRenderer.Fit.EXPLICIT,
                fallbackColor = TextureCache.colorFromName(g.name), shadow = r > 0.3f,
                explicit = floatArrayOf(scale, ox, oy),
            )
            // title pill under the box
            if (r > 0.6f) {
                val a = ((r - 0.6f) / 0.4f).coerceIn(0f, 1f)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.WHITE; alpha = (255 * a).toInt()
                    textSize = 14.dp.toPx(); typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
                }
                val text = TextUtils.ellipsize(label, android.text.TextPaint(paint), tw * 1.2f, TextUtils.TruncateAt.END).toString()
                val ty = oy + th * 0.5f + 18.dp.toPx()
                val tw2 = paint.measureText(text) / 2f + 12.dp.toPx()
                val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xE6161A20.toInt(); alpha = (230 * a).toInt() }
                c.nativeCanvas.drawRoundRect(ox - tw2, ty - 20.dp.toPx(), ox + tw2, ty + 8.dp.toPx(), 14.dp.toPx(), 14.dp.toPx(), bg)
                c.nativeCanvas.drawText(text, ox, ty, paint)
            }
        }
    }
}
