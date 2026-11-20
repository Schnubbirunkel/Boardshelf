package io.github.schnubbirunkel.ludothek.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.UUID

/** Helpers for files stored inside the app's private storage. Paths in the DB are relative. */
class AppFiles(private val context: Context) {
    val root: File get() = context.filesDir

    fun resolve(relative: String?): File? = relative?.let { File(root, it) }

    fun relative(file: File): String = file.relativeTo(root).path

    fun dir(name: String): File = File(root, name).apply { mkdirs() }

    fun newFile(dirName: String, ext: String): File =
        File(dir(dirName), "${UUID.randomUUID()}.$ext")

    /** A temp file in cache plus a content:// Uri usable by the system camera. */
    fun newCameraTarget(): Pair<File, Uri> {
        val f = File(File(context.cacheDir, "camera").apply { mkdirs() }, "${UUID.randomUUID()}.jpg")
        return f to uriFor(f)
    }

    fun uriFor(file: File): Uri =
        FileProvider.getUriForFile(context, context.packageName + ".files", file)

    fun copyUriTo(uri: Uri, target: File): File {
        target.parentFile?.mkdirs()
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Cannot open $uri" }
            target.outputStream().use { input.copyTo(it) }
        }
        return target
    }

    fun deleteRelative(relative: String?) {
        val f = resolve(relative) ?: return
        if (f.isDirectory) f.deleteRecursively() else f.delete()
    }
}

fun formatDateTime(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))

fun formatDate(millis: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))
