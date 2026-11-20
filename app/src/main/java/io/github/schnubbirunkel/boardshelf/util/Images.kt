package io.github.schnubbirunkel.boardshelf.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import androidx.palette.graphics.Palette
import java.io.File
import kotlin.math.max

object Images {

    /** Decodes [file] so that its longest side is at most about [maxDim] pixels. */
    fun decodeSampled(file: File, maxDim: Int): Bitmap? {
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDim) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = BitmapFactory.decodeFile(file.path, opts) ?: return null
        return applyExifRotation(file, bmp)
    }

    private fun applyExifRotation(file: File, bmp: Bitmap): Bitmap {
        val deg = try {
            when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } catch (e: Exception) {
            0f
        }
        if (deg == 0f) return bmp
        val m = Matrix().apply { postRotate(deg) }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }

    /** Re-encodes a (camera) photo to a reasonably sized, correctly rotated JPEG. */
    fun saveScaledJpeg(source: File, target: File, maxDim: Int = 2560, quality: Int = 88): File {
        val bmp = decodeSampled(source, maxDim) ?: throw IllegalArgumentException("Not an image")
        val scaled = scaleDown(bmp, maxDim)
        target.parentFile?.mkdirs()
        target.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, quality, it) }
        return target
    }

    fun scaleDown(bmp: Bitmap, maxDim: Int): Bitmap {
        val longest = max(bmp.width, bmp.height)
        if (longest <= maxDim) return bmp
        val f = maxDim.toFloat() / longest
        return Bitmap.createScaledBitmap(bmp, (bmp.width * f).toInt().coerceAtLeast(1), (bmp.height * f).toInt().coerceAtLeast(1), true)
    }

    /** A dark, saturated colour from the cover – used for the box sides. */
    fun spineColorOf(file: File): Int? {
        val bmp = decodeSampled(file, 200) ?: return null
        val p = Palette.from(bmp).maximumColorCount(16).generate()
        return (p.darkVibrantSwatch ?: p.darkMutedSwatch ?: p.vibrantSwatch ?: p.dominantSwatch)?.rgb
    }

    fun isPdf(file: File): Boolean = try {
        file.inputStream().use { s ->
            val head = ByteArray(1024)
            val n = s.read(head)
            n > 4 && String(head, 0, n, Charsets.ISO_8859_1).contains("%PDF")
        }
    } catch (e: Exception) {
        false
    }
}
