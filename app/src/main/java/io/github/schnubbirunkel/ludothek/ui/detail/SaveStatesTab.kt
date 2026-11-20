package io.github.schnubbirunkel.ludothek.ui.detail

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import coil.compose.AsyncImage
import io.github.schnubbirunkel.ludothek.R
import io.github.schnubbirunkel.ludothek.data.SaveState
import io.github.schnubbirunkel.ludothek.ui.app
import io.github.schnubbirunkel.ludothek.ui.rememberCamera
import io.github.schnubbirunkel.ludothek.ui.rememberImagePicker
import io.github.schnubbirunkel.ludothek.util.formatDate
import io.github.schnubbirunkel.ludothek.util.formatDateTime
import java.io.File

@Composable
fun SaveStatesTab(vm: GameDetailViewModel, onOpenPhoto: (Long) -> Unit) {
    val context = LocalContext.current
    val states by vm.saveStates.collectAsState()
    var pendingFile by remember { mutableStateOf<File?>(null) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var editing by remember { mutableStateOf<SaveState?>(null) }
    var deleting by remember { mutableStateOf<SaveState?>(null) }
    val camera = rememberCamera { pendingFile = it }
    val picker = rememberImagePicker { pendingUri = it }
    val files = context.app().repository.files

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
            ElevatedCard(Modifier.fillMaxWidth()) {
                Box {
                    AsyncImage(
                        model = files.resolve(s.photoPath),
                        contentDescription = s.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(4f / 3f)
                            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                            .clickable { onOpenPhoto(s.id) }
                    )
                    if (i == 0) Badge(Modifier.align(Alignment.TopStart).padding(10.dp)) {
                        Text(stringResource(R.string.savestate_current), modifier = Modifier.padding(4.dp))
                    }
                }
                Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(s.title, style = MaterialTheme.typography.titleMedium)
                        Text(formatDateTime(s.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { editing = s }) { Icon(Icons.Default.Edit, stringResource(R.string.action_edit)) }
                    IconButton(onClick = { vm.shareSaveState(s) }) { Icon(Icons.Default.Share, stringResource(R.string.action_share)) }
                    IconButton(onClick = { deleting = s }) { Icon(Icons.Default.Delete, stringResource(R.string.action_delete)) }
                }
                s.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp)) }
            }
        }
    }

    if (pendingFile != null || pendingUri != null) {
        SaveStateDialog(
            initialTitle = stringResource(R.string.savestate_default_title, formatDate(System.currentTimeMillis())),
            initialNote = "",
            onDismiss = { pendingFile?.delete(); pendingFile = null; pendingUri = null },
            onConfirm = { t, n ->
                pendingFile?.let { vm.addSaveState(it, t, n) }
                pendingUri?.let { vm.addSaveStateUri(it, t, n) }
                pendingFile = null; pendingUri = null
            }
        )
    }
    editing?.let { s ->
        SaveStateDialog(
            initialTitle = s.title, initialNote = s.note ?: "",
            onDismiss = { editing = null },
            onConfirm = { t, n -> vm.updateSaveState(s.copy(title = t, note = n.ifBlank { null })); editing = null }
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

@Composable
private fun SaveStateDialog(initialTitle: String, initialNote: String, onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var title by remember { mutableStateOf(initialTitle) }
    var note by remember { mutableStateOf(initialNote) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.savestate_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.field_title)) }, singleLine = true)
                OutlinedTextField(
                    note, { note = it }, label = { Text(stringResource(R.string.savestate_note_hint)) }, minLines = 3,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(title.ifBlank { initialTitle }, note) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
