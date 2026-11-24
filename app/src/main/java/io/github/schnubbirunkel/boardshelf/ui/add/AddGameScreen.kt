package io.github.schnubbirunkel.boardshelf.ui.add

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import io.github.schnubbirunkel.boardshelf.ui.theme.GlassCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import io.github.schnubbirunkel.boardshelf.R
import io.github.schnubbirunkel.boardshelf.data.Repository
import io.github.schnubbirunkel.boardshelf.net.SearchHit
import io.github.schnubbirunkel.boardshelf.net.Source
import io.github.schnubbirunkel.boardshelf.ui.appViewModel
import io.github.schnubbirunkel.boardshelf.ui.errorText
import io.github.schnubbirunkel.boardshelf.ui.theme.bouncyClick
import io.github.schnubbirunkel.boardshelf.ui.theme.glass
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AddUiState(
    val query: String = "",
    val loading: Boolean = false,
    val hits: List<SearchHit> = emptyList(),
    val errors: List<Throwable> = emptyList(),
    val searched: Boolean = false,
    val importing: String? = null,
    val importError: Throwable? = null,
)

class AddGameViewModel(private val repo: Repository) : ViewModel() {
    private val _state = MutableStateFlow(AddUiState())
    val state = _state.asStateFlow()
    val hasToken get() = repo.bgg.hasToken

    fun setQuery(q: String) = _state.update { it.copy(query = q) }

    fun search() {
        val q = _state.value.query.trim()
        if (q.isEmpty()) return
        _state.update { it.copy(loading = true, errors = emptyList()) }
        viewModelScope.launch {
            val r = repo.search(q)
            _state.update { it.copy(loading = false, hits = r.hits, errors = r.errors, searched = true) }
        }
    }

    fun import(hit: SearchHit, onDone: (Long) -> Unit) {
        _state.update { it.copy(importing = hit.name, importError = null) }
        viewModelScope.launch {
            try {
                val id = repo.importHit(hit)
                _state.update { it.copy(importing = null) }
                onDone(id)
            } catch (t: Throwable) {
                _state.update { it.copy(importing = null, importError = t) }
            }
        }
    }

    fun createManual(name: String, onDone: (Long) -> Unit) {
        viewModelScope.launch { onDone(repo.createManualGame(name)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddGameScreen(onBack: () -> Unit, onAdded: (Long) -> Unit, onSettings: () -> Unit, onScan: () -> Unit = {}) {
    val vm = appViewModel { AddGameViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    var manualDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = io.github.schnubbirunkel.boardshelf.ui.theme.glassTopBarColors(),
                title = { Text(stringResource(R.string.add_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::setQuery,
                label = { Text(stringResource(R.string.add_search_label)) },
                singleLine = true,
                trailingIcon = { IconButton(onClick = { keyboard?.hide(); vm.search() }) { Icon(Icons.Default.Search, null) } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); vm.search() }),
                modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onScan) {
                    Icon(Icons.Default.QrCodeScanner, null, Modifier.size(18.dp)); Text(" " + stringResource(R.string.action_scan))
                }
                OutlinedButton(onClick = { manualDialog = true }) {
                    Icon(Icons.Default.EditNote, null, Modifier.size(18.dp)); Text(" " + stringResource(R.string.add_manual))
                }
            }
            if (!vm.hasToken) {
                GlassCard(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(stringResource(R.string.add_no_token), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = onSettings) { Text(stringResource(R.string.menu_settings)) }
                    }
                }
            }
            state.errors.forEach {
                Text(context.errorText(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            state.importError?.let {
                Text(context.errorText(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Box(Modifier.fillMaxSize()) {
                if (state.loading) CircularProgressIndicator(Modifier.align(Alignment.Center))
                else if (state.searched && state.hits.isEmpty()) {
                    Text(stringResource(R.string.add_no_results), Modifier.align(Alignment.Center))
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(state.hits, key = { it.source.name + it.id }) { hit ->
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                            modifier = Modifier
                                .glass(RoundedCornerShape(20.dp))
                                .bouncyClick(enabled = state.importing == null) { vm.import(hit, onAdded) },
                            leadingContent = {
                                if (hit.thumbnail != null) {
                                    AsyncImage(
                                        hit.thumbnail, null, contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(6.dp))
                                    )
                                } else {
                                    Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) { Icon(Icons.Default.Casino, null) }
                                }
                            },
                            headlineContent = { Text(hit.name) },
                            supportingContent = {
                                val parts = listOfNotNull(hit.year?.toString(), hit.subtitle)
                                if (parts.isNotEmpty()) Text(parts.joinToString(" · "), maxLines = 2)
                            },
                            trailingContent = {
                                SuggestionChip(onClick = {}, label = {
                                    Text(if (hit.source == Source.BGG) "BGG" else "Wikidata", style = MaterialTheme.typography.labelSmall)
                                })
                            }
                        )
                    }
                }
            }
        }
    }

    state.importing?.let { name ->
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text(stringResource(R.string.add_importing)) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator(Modifier.size(32.dp)); Text(name)
                }
            }
        )
    }

    if (manualDialog) {
        var name by remember { mutableStateOf(state.query) }
        AlertDialog(
            onDismissRequest = { manualDialog = false },
            title = { Text(stringResource(R.string.add_manual)) },
            text = { OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.field_name)) }, singleLine = true) },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = { manualDialog = false; vm.createManual(name.trim(), onAdded) }) {
                    Text(stringResource(R.string.action_add))
                }
            },
            dismissButton = { TextButton(onClick = { manualDialog = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

