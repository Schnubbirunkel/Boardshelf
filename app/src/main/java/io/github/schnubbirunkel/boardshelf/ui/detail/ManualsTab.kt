package io.github.schnubbirunkel.boardshelf.ui.detail

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import io.github.schnubbirunkel.boardshelf.ui.theme.GlassCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.schnubbirunkel.boardshelf.R
import io.github.schnubbirunkel.boardshelf.data.Game
import io.github.schnubbirunkel.boardshelf.data.Manual
import io.github.schnubbirunkel.boardshelf.data.ManualKind
import io.github.schnubbirunkel.boardshelf.ui.rememberDocumentPicker

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ManualsTab(g: Game, vm: GameDetailViewModel, onOpen: (Long) -> Unit, onBrowse: () -> Unit, onScan: () -> Unit) {
    val manuals by vm.manuals.collectAsState()
    var linkDialog by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Manual?>(null) }
    var deleting by remember { mutableStateOf<Manual?>(null) }
    val defaultTitle = stringResource(R.string.manual_default_title, g.name)
    val pdfPicker = rememberDocumentPicker(arrayOf("application/pdf")) { uri: Uri -> vm.addManualUri(uri, defaultTitle) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.manual_explain), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ActionButton(Icons.Default.TravelExplore, R.string.manual_search_online, onBrowse)
            ActionButton(Icons.Default.DocumentScanner, R.string.manual_scan, onScan)
            ActionButton(Icons.Default.UploadFile, R.string.manual_import_pdf, pdfPicker)
            ActionButton(Icons.Default.Link, R.string.manual_from_link) { linkDialog = true }
        }
        if (manuals.isEmpty()) {
            Text(stringResource(R.string.manual_empty), style = MaterialTheme.typography.bodyMedium)
        }
        manuals.forEach { m ->
            GlassCard {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { onOpen(m.id) },
                    leadingContent = {
                        Icon(if (m.kind == ManualKind.PDF) Icons.Default.PictureAsPdf else Icons.Default.DocumentScanner, null)
                    },
                    headlineContent = { Text(m.title) },
                    supportingContent = {
                        Text(
                            if (m.kind == ManualKind.IMAGES) stringResource(R.string.manual_scanned_pages, m.pageCount)
                            else m.sourceUrl?.let { Uri.parse(it).host } ?: stringResource(R.string.manual_pdf)
                        )
                    },
                    trailingContent = {
                        var menu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, null) }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.action_share)) }, onClick = { menu = false; vm.shareManual(m) })
                                DropdownMenuItem(text = { Text(stringResource(R.string.action_rename)) }, onClick = { menu = false; renaming = m })
                                DropdownMenuItem(text = { Text(stringResource(R.string.action_delete)) }, onClick = { menu = false; deleting = m })
                            }
                        }
                    }
                )
            }
        }
    }

    if (linkDialog) {
        var url by remember { mutableStateOf("") }
        var title by remember { mutableStateOf(defaultTitle) }
        AlertDialog(
            onDismissRequest = { linkDialog = false },
            title = { Text(stringResource(R.string.manual_from_link)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(url, { url = it.trim() }, label = { Text("https://…/rules.pdf") }, singleLine = true)
                    OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.field_title)) }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(enabled = url.startsWith("http"), onClick = { linkDialog = false; vm.downloadManual(url, title) }) {
                    Text(stringResource(R.string.action_download))
                }
            },
            dismissButton = { TextButton(onClick = { linkDialog = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    renaming?.let { m ->
        var title by remember(m.id) { mutableStateOf(m.title) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text(stringResource(R.string.action_rename)) },
            text = { OutlinedTextField(title, { title = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { vm.renameManual(m, title); renaming = null }) { Text(stringResource(R.string.action_save)) } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    deleting?.let { m ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.manual_delete_title)) },
            text = { Text(m.title) },
            confirmButton = { TextButton(onClick = { vm.deleteManual(m); deleting = null }) { Text(stringResource(R.string.action_delete)) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun ActionButton(icon: ImageVector, label: Int, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick) {
        Icon(icon, null, Modifier.size(18.dp)); Text(" " + stringResource(label))
    }
}
