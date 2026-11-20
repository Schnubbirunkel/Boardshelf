package io.github.schnubbirunkel.boardshelf.ui.detail

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.schnubbirunkel.boardshelf.R
import io.github.schnubbirunkel.boardshelf.ui.app
import io.github.schnubbirunkel.boardshelf.ui.appViewModel
import io.github.schnubbirunkel.boardshelf.ui.box3d.Box3DViewer
import io.github.schnubbirunkel.boardshelf.ui.errorText
import io.github.schnubbirunkel.boardshelf.ui.rememberCamera
import io.github.schnubbirunkel.boardshelf.ui.rememberImagePicker
import io.github.schnubbirunkel.boardshelf.ui.theme.Deco

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameDetailScreen(
    gameId: Long,
    initialTab: Int,
    onBack: () -> Unit,
    onOpenManual: (Long) -> Unit,
    onOpenPhoto: (Long) -> Unit,
    onBrowseManuals: (Long) -> Unit,
    onScanManual: (Long) -> Unit,
) {
    val vm = appViewModel(key = "game$gameId") { GameDetailViewModel(it, gameId) }
    val game by vm.game.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    var menu by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(vm) {
        vm.events.collect { e ->
            when (e) {
                is DetailEvent.Error -> snackbar.showSnackbar(context.errorText(e.t))
                is DetailEvent.Message -> snackbar.showSnackbar(context.getString(e.res))
                DetailEvent.Deleted -> onBack()
                is DetailEvent.Share -> {
                    val uri = context.app().repository.files.uriFor(e.file)
                    val send = Intent(Intent.ACTION_SEND).setType(e.mime).putExtra(Intent.EXTRA_STREAM, uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    context.startActivity(Intent.createChooser(send, null))
                }
            }
        }
    }

    val coverCamera = rememberCamera { vm.setCover(it) }
    val coverPicker = rememberImagePicker { vm.setCoverUri(it) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, null) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.action_edit)) }, onClick = { menu = false; editing = true })
                            DropdownMenuItem(text = { Text(stringResource(R.string.action_cover_photo)) }, onClick = { menu = false; coverCamera() })
                            DropdownMenuItem(text = { Text(stringResource(R.string.action_cover_gallery)) }, onClick = { menu = false; coverPicker() })
                            if (game?.bggId != null || game?.wikidataId != null) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.action_refresh)) }, onClick = { menu = false; vm.refresh() })
                            }
                            DropdownMenuItem(text = { Text(stringResource(R.string.action_delete)) }, onClick = { menu = false; confirmDelete = true })
                        }
                    }
                }
            )
        }
    ) { padding ->
        val g = game ?: return@Scaffold
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(360.dp)
                    .background(Brush.radialGradient(listOf(Color(0xFF34404D), Deco.wall), radius = 900f))
            ) {
                Box3DViewer(g, Modifier.fillMaxSize().padding(top = padding.calculateTopPadding(), start = 24.dp, end = 24.dp, bottom = 8.dp))
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(
                g.name, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            )
            Text(
                stringResource(R.string.detail_drag_hint), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            val tabs = listOf(R.string.tab_info, R.string.tab_rating, R.string.tab_savestates, R.string.tab_manuals)
            ScrollableTabRow(selectedTabIndex = tab, containerColor = Color.Transparent, edgePadding = 8.dp) {
                tabs.forEachIndexed { i, res ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(stringResource(res)) })
                }
            }
            Box(Modifier.padding(16.dp)) {
                when (tab) {
                    0 -> InfoTab(g, vm)
                    1 -> RatingTab(g, vm)
                    2 -> SaveStatesTab(vm, onOpenPhoto)
                    else -> ManualsTab(g, vm, onOpenManual, onBrowse = { onBrowseManuals(g.id) }, onScan = { onScanManual(g.id) })
                }
            }
            Spacer(Modifier.height(padding.calculateBottomPadding() + 24.dp))
        }

        if (editing) EditGameDialog(g, vm, onDismiss = { editing = false })
        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text(stringResource(R.string.delete_game_title)) },
                text = { Text(stringResource(R.string.delete_game_text, g.name)) },
                confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete() }) { Text(stringResource(R.string.action_delete)) } },
                dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
            )
        }
    }
}
