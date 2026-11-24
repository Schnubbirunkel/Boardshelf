package io.github.schnubbirunkel.boardshelf.ui.detail

import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.schnubbirunkel.boardshelf.R
import io.github.schnubbirunkel.boardshelf.data.SaveState
import io.github.schnubbirunkel.boardshelf.data.SaveStatePhoto
import io.github.schnubbirunkel.boardshelf.ui.app
import io.github.schnubbirunkel.boardshelf.ui.rememberCamera
import io.github.schnubbirunkel.boardshelf.ui.rememberMultiImagePicker
import io.github.schnubbirunkel.boardshelf.ui.theme.GlassCard
import io.github.schnubbirunkel.boardshelf.util.formatDate
import io.github.schnubbirunkel.boardshelf.util.formatDateTime
import java.io.File

/** Photos chosen for a new save state but not stored yet (camera temp files or gallery uris). */
private data class Pending(val file: File? = null, val uri: Uri? = null) {
    val model: Any get() = file ?: uri!!
}

@Composable
fun SaveStatesTab(vm: GameDetailViewModel, onOpenPhoto: (Long, Int) -> Unit) {
    val states by vm.saveStates.collectAsState()
    val photos by vm.photos.collectAsState()
    val byState = remember(photos) { photos.groupBy { it.saveStateId } }
    var creating by remember { mutableStateOf<List<Pending>?>(null) }
    var editing by remember { mutableStateOf<SaveState?>(null) }
    var deleting by remember { mutableStateOf<SaveState?>(null) }
    val camera = rememberCamera { f -> creating = (creating ?: emptyList()) + Pending(file = f) }
    val picker = rememberMultiImagePicker { uris -> creating = (creating ?: emptyList()) + uris.map { Pending(uri = it) } }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.savestate_explain), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = camera) {
                Icon(Icons.Default.PhotoCamera, null, Modifier.size(18.dp)); Text(" " + stringResource(R.string.savestate_take_photo))
            }
            OutlinedButton(onClick = picker) {
                Icon(Icons.Default.PhotoLibrary, null, Modifier.size(18.dp)); Text(" " + stringResource(R.string.savestate_from_gallery))
            }
        }
        if (states.isEmpty()) {
            Text(stringResource(R.string.savestate_empty), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 24.dp))
        }
        states.forEachIndexed { i, s ->
            val list = byState[s.id].orEmpty()
            SaveStateCard(s, list, current = i == 0, onOpenPhoto = onOpenPhoto, onEdit = { editing = s }, onShare = { vm.shareSaveState(s) }, onDelete = { deleting = s })
        }
    }

    creating?.let { pending ->
        NewSaveStateDialog(
            pending = pending,
            onAddCamera = camera,
            onAddGallery = picker,
            onRemove = { p -> p.file?.delete(); creating = pending - p },
            onDismiss = { pending.forEach { it.file?.delete() }; creating = null },
            onConfirm = { t, n ->
                vm.addSaveState(pending.mapNotNull { it.file }, pending.mapNotNull { it.uri }, t, n)
                creating = null
            }
        )
    }
    editing?.let { s ->
        EditSaveStateDialog(
            state = s, photos = byState[s.id].orEmpty(), vm = vm,
            onDismiss = { editing = null },
        )
    }
    deleting?.let { s ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.savestate_delete_title)) },
            text = { Text(s.title) },
            confirmButton = { TextButton(onClick = { vm.deleteSaveState(s); deleting = null }) { Text(stringResource(R.string.action_delete)) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SaveStateCard(
    s: SaveState, photos: List<SaveStatePhoto>, current: Boolean,
    onOpenPhoto: (Long, Int) -> Unit, onEdit: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit,
) {
    val files = LocalContext.current.app().repository.files
    val paths = photos.map { it.path }.ifEmpty { listOf(s.photoPath) }
    val pager = rememberPagerState { paths.size }
    GlassCard(Modifier.fillMaxWidth().animateContentSize()) {
        Box {
            HorizontalPager(pager, Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))) { page ->
                AsyncImage(
                    model = files.resolve(paths[page]), contentDescription = s.title, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clickable { onOpenPhoto(s.id, page) }
                )
            }
            if (current) Badge(Modifier.align(Alignment.TopStart).padding(12.dp)) {
                Text(stringResource(R.string.savestate_current), modifier = Modifier.padding(4.dp))
            }
            if (paths.size > 1) {
                // page dots
                Row(
                    Modifier.align(Alignment.BottomCenter).padding(10.dp).clip(RoundedCornerShape(12.dp))
                        .background(Color(0x66000000)).padding(horizontal = 8.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    repeat(paths.size) { i ->
                        val w by animateDpAsState(if (pager.currentPage == i) 16.dp else 6.dp, label = "dot")
                        Box(Modifier.height(6.dp).width(w).clip(CircleShape).background(if (pager.currentPage == i) Color.White else Color(0x88FFFFFF)))
                    }
                }
            }
        }
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    formatDateTime(s.createdAt) + if (paths.size > 1) " · " + stringResource(R.string.savestate_photo_count, paths.size) else "",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, stringResource(R.string.action_edit)) }
            IconButton(onClick = onShare) { Icon(Icons.Default.Share, stringResource(R.string.action_share)) }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, stringResource(R.string.action_delete)) }
        }
        s.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) }
    }
}

@Composable
private fun Thumb(model: Any?, onRemove: (() -> Unit)?) {
    Box(Modifier.size(76.dp)) {
        AsyncImage(model, null, Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
        if (onRemove != null) FilledTonalIconButton(onClick = onRemove, modifier = Modifier.align(Alignment.TopEnd).size(26.dp), shape = CircleShape) {
            Icon(Icons.Default.Close, stringResource(R.string.action_delete), Modifier.size(14.dp))
        }
    }
}

@Composable
private fun AddPhotoButtons(onCamera: () -> Unit, onGallery: () -> Unit) {
    FilledTonalIconButton(onClick = onCamera, modifier = Modifier.size(76.dp), shape = RoundedCornerShape(14.dp)) {
        Icon(Icons.Default.AddAPhoto, stringResource(R.string.savestate_take_photo))
    }
    FilledTonalIconButton(onClick = onGallery, modifier = Modifier.size(76.dp), shape = RoundedCornerShape(14.dp)) {
        Icon(Icons.Default.PhotoLibrary, stringResource(R.string.savestate_from_gallery))
    }
}

@Composable
private fun NewSaveStateDialog(
    pending: List<Pending>, onAddCamera: () -> Unit, onAddGallery: () -> Unit, onRemove: (Pending) -> Unit,
    onDismiss: () -> Unit, onConfirm: (String, String) -> Unit,
) {
    val initialTitle = stringResource(R.string.savestate_default_title, formatDate(System.currentTimeMillis()))
    var title by remember { mutableStateOf(initialTitle) }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.savestate_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pending.forEach { p -> Thumb(p.model) { onRemove(p) } }
                    AddPhotoButtons(onAddCamera, onAddGallery)
                }
                Text(stringResource(R.string.savestate_photo_count, pending.size), style = MaterialTheme.typography.labelSmall)
                OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.field_title)) }, singleLine = true)
                OutlinedTextField(
                    note, { note = it }, label = { Text(stringResource(R.string.savestate_note_hint)) }, minLines = 3,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                )
            }
        },
        confirmButton = {
            TextButton(enabled = pending.isNotEmpty(), onClick = { onConfirm(title.ifBlank { initialTitle }, note) }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun EditSaveStateDialog(state: SaveState, photos: List<SaveStatePhoto>, vm: GameDetailViewModel, onDismiss: () -> Unit) {
    val files = LocalContext.current.app().repository.files
    var title by remember { mutableStateOf(state.title) }
    var note by remember { mutableStateOf(state.note ?: "") }
    val camera = rememberCamera { f -> vm.addPhotos(state.id, listOf(f), emptyList()) }
    val picker = rememberMultiImagePicker { uris -> vm.addPhotos(state.id, emptyList(), uris) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    photos.forEach { p -> Thumb(files.resolve(p.path)) { vm.deletePhoto(p) } }
                    AddPhotoButtons(camera, picker)
                }
                OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.field_title)) }, singleLine = true)
                OutlinedTextField(
                    note, { note = it }, label = { Text(stringResource(R.string.savestate_note_hint)) }, minLines = 3,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.updateSaveState(state.copy(title = title.ifBlank { state.title }, note = note.ifBlank { null })); onDismiss() }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
