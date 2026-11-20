package io.github.schnubbirunkel.boardshelf.ui.box3d

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LightingColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Software 3D renderer for a cuboid game box. Each visible face is drawn by mapping its
 * texture onto the projected quad with a perspective [Matrix] (setPolyToPoly), so no
 * OpenGL is needed and it works inside any Compose Canvas.
 */
object BoxRenderer {

    enum class Fit {
        /** Fit the projected bounding box into the target, aligned to the bottom (shelf). */
        BOUNDS_BOTTOM,
        /** Constant scale based on the bounding sphere, centred (rotating viewer). */
        SPHERE_CENTER,
    }

    private class Face(val corners: Array<IntArray>, val normal: FloatArray, val pick: (BoxTextures) -> Bitmap?)

    // Corner signs (x, y, z) in texture order: top-left, top-right, bottom-right, bottom-left.
    private val faces = listOf(
        Face(arrayOf(intArrayOf(-1, 1, 1), intArrayOf(1, 1, 1), intArrayOf(1, -1, 1), intArrayOf(-1, -1, 1)), floatArrayOf(0f, 0f, 1f)) { it.front },
        Face(arrayOf(intArrayOf(1, 1, -1), intArrayOf(-1, 1, -1), intArrayOf(-1, -1, -1), intArrayOf(1, -1, -1)), floatArrayOf(0f, 0f, -1f)) { it.back },
        Face(arrayOf(intArrayOf(1, 1, 1), intArrayOf(1, 1, -1), intArrayOf(1, -1, -1), intArrayOf(1, -1, 1)), floatArrayOf(1f, 0f, 0f)) { it.right },
        Face(arrayOf(intArrayOf(-1, 1, -1), intArrayOf(-1, 1, 1), intArrayOf(-1, -1, 1), intArrayOf(-1, -1, -1)), floatArrayOf(-1f, 0f, 0f)) { it.left },
        Face(arrayOf(intArrayOf(-1, 1, -1), intArrayOf(1, 1, -1), intArrayOf(1, 1, 1), intArrayOf(-1, 1, 1)), floatArrayOf(0f, 1f, 0f)) { it.top },
        Face(arrayOf(intArrayOf(-1, -1, 1), intArrayOf(1, -1, 1), intArrayOf(1, -1, -1), intArrayOf(-1, -1, -1)), floatArrayOf(0f, -1f, 0f)) { it.bottom },
    )

    private val light = floatArrayOf(-0.35f, 0.65f, 1f).let { v ->
        val l = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]); floatArrayOf(v[0] / l, v[1] / l, v[2] / l)
    }

    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = 0x55000000; strokeJoin = Paint.Join.ROUND
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val matrix = Matrix()
    private val path = Path()
    private val src = FloatArray(8)
    private val dst = FloatArray(8)

    /** Rotates (yaw around Y, then pitch around X). Returns x, y, z. */
    private fun rotate(x: Float, y: Float, z: Float, cy: Float, sy: Float, cp: Float, sp: Float, out: FloatArray) {
        val x1 = x * cy + z * sy
        val z1 = -x * sy + z * cy
        val y2 = y * cp - z1 * sp
        val z2 = y * sp + z1 * cp
        out[0] = x1; out[1] = y2; out[2] = z2
    }

    /** Approximate on-screen size (in the box's own units, e.g. cm) for the given view angles. */
    fun projectedSize(w: Float, h: Float, d: Float, yawDeg: Float, pitchDeg: Float): Pair<Float, Float> {
        val b = bounds(w, h, d, yawDeg, pitchDeg)
        return (b.right - b.left) to (b.bottom - b.top)
    }

    private fun bounds(w: Float, h: Float, d: Float, yawDeg: Float, pitchDeg: Float): RectF {
        val pts = project(w, h, d, yawDeg, pitchDeg)
        val r = RectF(Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
        for (i in 0 until 8) {
            r.left = min(r.left, pts[i * 2]); r.right = max(r.right, pts[i * 2])
            r.top = min(r.top, pts[i * 2 + 1]); r.bottom = max(r.bottom, pts[i * 2 + 1])
        }
        return r
    }

    private fun cornerIndex(sx: Int, sy: Int, sz: Int) = (if (sx > 0) 1 else 0) + (if (sy > 0) 2 else 0) + (if (sz > 0) 4 else 0)

    /** Projects the 8 corners. Screen y grows downwards. */
    private fun project(w: Float, h: Float, d: Float, yawDeg: Float, pitchDeg: Float): FloatArray {
        val yaw = Math.toRadians(yawDeg.toDouble())
        val pitch = Math.toRadians(pitchDeg.toDouble())
        val cy = cos(yaw).toFloat(); val sy = sin(yaw).toFloat()
        val cp = cos(pitch).toFloat(); val sp = sin(pitch).toFloat()
        val cam = 4.2f * max(w, max(h, d))
        val out = FloatArray(16)
        val tmp = FloatArray(3)
        for (sx in intArrayOf(-1, 1)) for (syy in intArrayOf(-1, 1)) for (sz in intArrayOf(-1, 1)) {
            rotate(sx * w / 2f, syy * h / 2f, sz * d / 2f, cy, sy, cp, sp, tmp)
            val f = cam / (cam - tmp[2])
            val i = cornerIndex(sx, syy, sz)
            out[i * 2] = tmp[0] * f
            out[i * 2 + 1] = -tmp[1] * f
        }
        return out
    }

    fun draw(
        canvas: Canvas,
        tex: BoxTextures?,
        w: Float, h: Float, d: Float,
        yawDeg: Float, pitchDeg: Float,
        target: RectF,
        fit: Fit,
        fallbackColor: Int = Color.DKGRAY,
        shadow: Boolean = true,
        dim: Float = 1f,
    ) {
        val pts = project(w, h, d, yawDeg, pitchDeg)
        val scale: Float
        val ox: Float
        val oy: Float
        when (fit) {
            Fit.BOUNDS_BOTTOM -> {
                val b = bounds(w, h, d, yawDeg, pitchDeg)
                scale = min(target.width() / b.width(), target.height() / b.height())
                ox = target.centerX() - b.centerX() * scale
                oy = target.bottom - b.bottom * scale
            }
            Fit.SPHERE_CENTER -> {
                val diag = sqrt(w * w + h * h + d * d)
                scale = min(target.width(), target.height()) / (diag * 1.12f)
                ox = target.centerX(); oy = target.centerY()
            }
        }
        for (i in 0 until 8) {
            pts[i * 2] = pts[i * 2] * scale + ox
            pts[i * 2 + 1] = pts[i * 2 + 1] * scale + oy
        }

        if (shadow) {
            // soft contact shadow below the lowest corners
            var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
            for (i in 0 until 8) { minX = min(minX, pts[i * 2]); maxX = max(maxX, pts[i * 2]); maxY = max(maxY, pts[i * 2 + 1]) }
            val cx = (minX + maxX) / 2f
            val rx = (maxX - minX) * 0.6f
            if (rx > 1f) {
                shadowPaint.shader = RadialGradient(cx, maxY, rx, 0x66000000, 0x00000000, Shader.TileMode.CLAMP)
                canvas.save()
                canvas.scale(1f, 0.18f, cx, maxY)
                canvas.drawCircle(cx, maxY, rx, shadowPaint)
                canvas.restore()
            }
        }

        val yaw = Math.toRadians(yawDeg.toDouble())
        val pitch = Math.toRadians(pitchDeg.toDouble())
        val cy = cos(yaw).toFloat(); val sy = sin(yaw).toFloat()
        val cp = cos(pitch).toFloat(); val sp = sin(pitch).toFloat()
        val n = FloatArray(3)
        edgePaint.strokeWidth = max(1f, scale * 0.08f)

        for (face in faces) {
            for (k in 0 until 4) {
                val c = face.corners[k]
                val idx = cornerIndex(c[0], c[1], c[2])
                dst[k * 2] = pts[idx * 2]; dst[k * 2 + 1] = pts[idx * 2 + 1]
            }
            // back-face culling via the signed area of the projected quad (screen y down)
            val ax = dst[2] - dst[0]; val ay = dst[3] - dst[1]
            val bx = dst[4] - dst[0]; val by = dst[5] - dst[1]
            if (ax * by - ay * bx <= 0f) continue

            rotate(face.normal[0], face.normal[1], face.normal[2], cy, sy, cp, sp, n)
            val lambert = max(0f, n[0] * light[0] + n[1] * light[1] + n[2] * light[2])
            val b = ((0.5f + 0.5f * lambert) * dim).coerceIn(0f, 1f)
            val g = (255 * b).toInt()
            val mul = Color.rgb(g, g, g)

            path.reset()
            path.moveTo(dst[0], dst[1]); path.lineTo(dst[2], dst[3]); path.lineTo(dst[4], dst[5]); path.lineTo(dst[6], dst[7]); path.close()

            val bmp = tex?.let(face.pick)
            if (bmp != null) {
                val bw = bmp.width.toFloat(); val bh = bmp.height.toFloat()
                src[0] = 0f; src[1] = 0f; src[2] = bw; src[3] = 0f; src[4] = bw; src[5] = bh; src[6] = 0f; src[7] = bh
                if (matrix.setPolyToPoly(src, 0, dst, 0, 4)) {
                    bitmapPaint.colorFilter = LightingColorFilter(mul, 0)
                    canvas.save()
                    canvas.clipPath(path)
                    canvas.drawBitmap(bmp, matrix, bitmapPaint)
                    canvas.restore()
                }
            } else {
                val base = tex?.color ?: fallbackColor
                fillPaint.color = Color.rgb(
                    (Color.red(base) * b).toInt(), (Color.green(base) * b).toInt(), (Color.blue(base) * b).toInt()
                )
                canvas.drawPath(path, fillPaint)
            }
            canvas.drawPath(path, edgePaint)
        }
    }
}
