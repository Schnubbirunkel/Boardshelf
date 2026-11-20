package io.github.schnubbirunkel.ludothek.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import io.github.schnubbirunkel.ludothek.R
import java.io.File

/** Launches the system camera; [onPhoto] receives a temp file with the picture. */
@Composable
fun rememberCamera(onPhoto: (File) -> Unit): () -> Unit {
    val context = LocalContext.current
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    val callback by rememberUpdatedState(onPhoto)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = pending?.let { File(it) }
        pending = null
        if (ok && f != null && f.exists() && f.length() > 0) callback(f) else f?.delete()
    }
    return {
        val (file, uri) = context.app().repository.files.newCameraTarget()
        pending = file.path
        try {
            launcher.launch(uri)
        } catch (e: Exception) {
            pending = null
            Toast.makeText(context, R.string.err_no_camera, Toast.LENGTH_LONG).show()
        }
    }
}

@Composable
fun rememberImagePicker(onImage: (Uri) -> Unit): () -> Unit {
    val callback by rememberUpdatedState(onImage)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) callback(uri)
    }
    return { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}

@Composable
fun rememberDocumentPicker(mime: Array<String>, onDoc: (Uri) -> Unit): () -> Unit {
    val callback by rememberUpdatedState(onDoc)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) callback(uri)
    }
    return { launcher.launch(mime) }
}
