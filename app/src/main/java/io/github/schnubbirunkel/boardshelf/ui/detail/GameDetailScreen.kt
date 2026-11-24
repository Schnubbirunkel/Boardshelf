package io.github.schnubbirunkel.boardshelf.ui.detail

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import io.github.schnubbirunkel.boardshelf.ui.theme.GlassTabs
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch
import io.github.schnubbirunkel.boardshelf.R
import io.github.schnubbirunkel.boardshelf.ui.app
import io.github.schnubbirunkel.boardshelf.ui.appViewModel
import io.github.schnubbirunkel.boardshelf.ui.box3d.Box3DViewer
import io.github.schnubbirunkel.boardshelf.ui.errorText
import io.github.schnubbirunkel.boardshelf.ui.rememberCamera
import io.github.schnubbirunkel.boardshelf.ui.rememberImagePicker
import io.github.schnubbirunkel.boardshelf.ui.theme.Deco

const val DETAIL_YAW = -28f
const val DETAIL_PITCH = 14f
const val DETAIL_HEADER_HEIGHT = 360

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameDetailScreen(
    gameId: Long,
    initialTab: Int,
    fromShelf: Boolean = false,
    onBack: () -> Unit,
    onOpenManual: (Long) -> Unit,
    onOpenPhoto: (Long, Int) -> Unit,
    onBrowseManuals: (Long) -> Unit,
    onScanManual: (Long) -> Unit,
    onSearchCover: (Long) -> Unit = {},
    onCropCover: (String) -> Unit = {},
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

    val scope = rememberCoroutineScope()
    val repo = context.app().repository
    val coverCamera = rememberCamera { onCropCover(it.path) }
    val coverPicker = rememberImagePicker { uri ->
        scope.launch { runCatching { repo.coverCandidateFrom(null, uri) }.onSuccess { onCropCover(it.path) } }
    }

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
                            DropdownMenuItem(text = { Text(stringResource(R.string.action_cover_web)) }, onClick = { menu = false; onSearchCover(gameId) })
                            if (game?.coverPath != null) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.action_cover_crop)) }, onClick = {
                                    menu = false
                                    scope.launch {
                                        runCatching { repo.coverCandidateFrom(repo.files.resolve(game?.coverPath), null) }
                                            .onSuccess { onCropCover(it.path) }
                                    }
                                })
                            }
                            DropdownMenuItem(text = { Text(stringResource(R.string.action_cover_photo)) }, onClick = { menu = false; coverCamera() })
                            DropdownMenuItem(text = { Text(stringResource(R.string.action_cover_gallery)) }, onClick = { menu = false; coverPicker() })
                            if (vm.repo.ai.enabled) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.action_ai_research)) }, onClick = { menu = false; vm.aiResearch() })
                            }
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
                    .height(DETAIL_HEADER_HEIGHT.dp)
                    .background(Brush.radialGradient(listOf(Color(0x8034404D), Color.Transparent), radius = 900f))
            ) {
                Box3DViewer(g, intro = !fromShelf, modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding(), start = 24.dp, end = 24.dp, bottom = 8.dp))
            }
            val aiBusy by vm.repo.aiBusy.collectAsStateWithLifecycle()
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (g.id in aiBusy) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("  " + stringResource(R.string.ai_researching), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
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
            GlassTabs(tabs.map { stringResource(it) }, tab, { tab = it }, Modifier.padding(horizontal = 16.dp))
            // tab content slides in the direction of the selected tab (spring, never blocks input)
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = 420f)) { it / 6 * dir } + fadeIn(tween(180))) togetherWith
                        fadeOut(tween(120)) using SizeTransform(clip = false)
                },
                label = "tab",
                modifier = Modifier.padding(16.dp),
            ) { t ->
                when (t) {
                    0 -> InfoTab(g, vm, onSearchCover = { onSearchCover(g.id) }, onCoverPhoto = coverCamera)
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
