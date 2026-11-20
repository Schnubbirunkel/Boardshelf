package io.github.schnubbirunkel.ludothek.ui.manuals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.schnubbirunkel.ludothek.R
import io.github.schnubbirunkel.ludothek.data.Game
import io.github.schnubbirunkel.ludothek.ui.app
import io.github.schnubbirunkel.ludothek.ui.errorText
import io.github.schnubbirunkel.ludothek.ui.rememberCamera
import io.github.schnubbirunkel.ludothek.ui.rememberImagePicker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/** Photograph the pages of a printed rulebook and store them as a manual. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanManualScreen(gameId: Long, onDone: () -> Unit) {
    val context = LocalContext.current
    val repo = context.app().repository
    val game by produceState<Game?>(null, gameId) { value = repo.game(gameId).first() }
    // page paths joined with '\n' so the list survives configuration changes
    var pagesRaw by rememberSaveable { mutableStateOf("") }
    val pages = pagesRaw.lines().filter { it.isNotBlank() }
    var title by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun addPage(f: File) {
        pagesRaw = (pagesRaw.lines().filter { it.isNotBlank() } + f.path).joinToString("\n")
    }

    val camera = rememberCamera { addPage(it) }
    val picker = rememberImagePicker { uri ->
        val f = File(File(context.cacheDir, "camera").apply { mkdirs() }, "${UUID.randomUUID()}.jpg")
        runCatching { repo.files.copyUriTo(uri, f) }.onSuccess { addPage(f) }
    }
    val effectiveTitle = title ?: game?.let { context.getString(R.string.manual_scan_default_title, it.name) } ?: ""

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.manual_scan)) },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(effectiveTitle, { title = it }, label = { Text(stringResource(R.string.field_title)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text(stringResource(R.string.manual_scan_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = camera) {
                    Icon(Icons.Default.PhotoCamera, null, Modifier.size(18.dp)); Text(" " + stringResource(R.string.manual_scan_add_page))
                }
                OutlinedButton(onClick = picker) {
                    Icon(Icons.Default.PhotoLibrary, null, Modifier.size(18.dp)); Text(" " + stringResource(R.string.savestate_from_gallery))
                }
            }
            if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            LazyVerticalGrid(GridCells.Adaptive(110.dp), Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(pages) { i, p ->
                    Box {
                        AsyncImage(File(p), null, Modifier.fillMaxWidth().aspectRatio(0.72f), contentScale = ContentScale.Crop)
                        Text(
                            "${i + 1}", color = Color.White, style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)
                        )
                        FilledTonalIconButton(
                            onClick = { File(p).delete(); pagesRaw = pages.filterIndexed { j, _ -> j != i }.joinToString("\n") },
                            modifier = Modifier.align(Alignment.TopEnd).size(30.dp),
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledTonalIconButtonColors()
                        ) { Icon(Icons.Default.Close, stringResource(R.string.action_delete), Modifier.size(16.dp)) }
                    }
                }
            }
            Button(
                enabled = pages.isNotEmpty() && !saving,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    saving = true
                    scope.launch {
                        try {
                            repo.addScannedManual(gameId, effectiveTitle, pages.map { File(it) })
                            pages.forEach { File(it).delete() }
                            onDone()
                        } catch (e: Exception) {
                            error = context.errorText(e)
                        } finally {
                            saving = false
                        }
                    }
                }
            ) { Text(stringResource(R.string.manual_scan_save, pages.size)) }
        }
    }
}
