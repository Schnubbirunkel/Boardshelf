package io.github.schnubbirunkel.ludothek.ui.shelf

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.schnubbirunkel.ludothek.R
import io.github.schnubbirunkel.ludothek.data.Shelf
import io.github.schnubbirunkel.ludothek.ui.app
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShelvesScreen(onBack: () -> Unit) {
    val repo = LocalContext.current.app().repository
    val shelves by repo.shelves.collectAsState(initial = emptyList())
    val games by repo.games.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<Shelf?>(null) }
    var adding by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.menu_manage_shelves)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { adding = true }) { Icon(Icons.Default.Add, stringResource(R.string.shelves_add)) }
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(shelves, key = { _, s -> s.id }) { i, s ->
                ElevatedCard {
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { editing = s },
                        headlineContent = { Text(s.name) },
                        supportingContent = { Text(stringResource(R.string.shelf_count, games.count { it.shelfId == s.id })) },
                        trailingContent = {
                            Row {
                                IconButton(enabled = i > 0, onClick = { scope.launch { repo.moveShelf(s, -1) } }) { Icon(Icons.Default.KeyboardArrowUp, null) }
                                IconButton(enabled = i < shelves.lastIndex, onClick = { scope.launch { repo.moveShelf(s, 1) } }) { Icon(Icons.Default.KeyboardArrowDown, null) }
                                IconButton(enabled = shelves.size > 1, onClick = { scope.launch { repo.deleteShelf(s) } }) {
                                    Icon(Icons.Default.Delete, stringResource(R.string.action_delete))
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    if (adding || editing != null) {
        var name by remember(editing) { mutableStateOf(editing?.name ?: "") }
        AlertDialog(
            onDismissRequest = { adding = false; editing = null },
            title = { Text(stringResource(if (adding) R.string.shelves_add else R.string.action_rename)) },
            text = { OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.field_name)) }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    val e = editing
                    scope.launch { if (e != null) repo.renameShelf(e, name.ifBlank { e.name }) else repo.addShelf(name) }
                    adding = false; editing = null
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { adding = false; editing = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
