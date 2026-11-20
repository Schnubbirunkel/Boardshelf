package io.github.schnubbirunkel.boardshelf.ui.shelf

import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.schnubbirunkel.boardshelf.R
import io.github.schnubbirunkel.boardshelf.data.Game
import io.github.schnubbirunkel.boardshelf.data.Repository
import io.github.schnubbirunkel.boardshelf.data.Shelf
import io.github.schnubbirunkel.boardshelf.ui.appViewModel
import io.github.schnubbirunkel.boardshelf.ui.box3d.ShelfBox
import io.github.schnubbirunkel.boardshelf.ui.theme.ShelfLabelStyle
import io.github.schnubbirunkel.boardshelf.ui.theme.Deco
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class ShelfUiState(val shelves: List<Shelf>, val games: List<Game>, val spineMode: Boolean)

class ShelfViewModel(private val repo: Repository) : ViewModel() {
    val state = combine(repo.shelves, repo.games, repo.settings.state) { s, g, set ->
        ShelfUiState(s, g, set.spineMode)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        viewModelScope.launch { repo.ensureShelves() }
    }

    fun applyOrder(order: Map<Long, List<Long>>) {
        viewModelScope.launch { repo.applyOrder(order) }
    }

    fun toggleSpine() = repo.settings.update { it.copy(spineMode = !it.spineMode) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShelfScreen(
    onOpenGame: (Long) -> Unit,
    onAdd: () -> Unit,
    onSettings: () -> Unit,
    onManageShelves: () -> Unit,
) {
    val vm = appViewModel { ShelfViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Deco.wall,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Deco.wallLight),
                title = {
                    if (searching) {
                        TextField(
                            value = query, onValueChange = { query = it },
                            placeholder = { Text(stringResource(R.string.shelf_search_hint)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Column {
                            Text(stringResource(R.string.app_name))
                            state?.let {
                                Text(
                                    stringResource(R.string.shelf_count, it.games.size),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { searching = !searching; if (!searching) query = "" }) {
                        Icon(if (searching) Icons.Default.Close else Icons.Default.Search, stringResource(R.string.action_search))
                    }
                    IconButton(onClick = vm::toggleSpine) {
                        Icon(
                            if (state?.spineMode == true) Icons.Default.ViewModule else Icons.AutoMirrored.Filled.ViewList,
                            stringResource(if (state?.spineMode == true) R.string.action_show_covers else R.string.action_show_spines)
                        )
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, null) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.menu_manage_shelves)) }, onClick = { menu = false; onManageShelves() })
                            DropdownMenuItem(text = { Text(stringResource(R.string.menu_settings)) }, onClick = { menu = false; onSettings() })
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text(stringResource(R.string.action_add_game)) },
            )
        }
    ) { padding ->
        val st = state ?: return@Scaffold
        Box(Modifier.fillMaxSize()) {
            ShelfWall(
                shelves = st.shelves, games = st.games, spineMode = st.spineMode, query = query,
                onOpen = onOpenGame, onOrder = vm::applyOrder, padding = padding
            )
            if (st.games.isEmpty()) {
                ElevatedCard(Modifier.align(Alignment.Center).padding(32.dp)) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.shelf_empty_title), style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.shelf_empty_text), style = MaterialTheme.typography.bodyMedium)
                        FilledTonalButton(onClick = onAdd) { Text(stringResource(R.string.action_add_game)) }
                    }
                }
            }
        }
    }
}

private data class DragState(
    val id: Long,
    val pointer: Offset,
    val grab: Offset,
    val size: Size,
    val baseOrder: Map<Long, List<Long>>,
    val baseLayout: ShelfLayout,
    val preview: Map<Long, List<Long>>,
) {
    val center get() = pointer - grab + Offset(size.width / 2, size.height / 2)
}

@Composable
private fun ShelfWall(
    shelves: List<Shelf>,
    games: List<Game>,
    spineMode: Boolean,
    query: String,
    onOpen: (Long) -> Unit,
    onOrder: (Map<Long, List<Long>>) -> Unit,
    padding: PaddingValues,
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Deco.wallLight, Deco.wall)))
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val viewportH = constraints.maxHeight.toFloat()
        val topPx = with(density) { padding.calculateTopPadding().toPx() }
        val bottomPx = with(density) { padding.calculateBottomPadding().toPx() + 88.dp.toPx() }
        val dims = remember(density, widthPx, topPx, bottomPx) { ShelfDims(density, widthPx, topPx, bottomPx) }
        val gamesById = remember(games) { games.associateBy { it.id } }
        val dbOrder = remember(shelves, games) {
            val known = shelves.map { it.id }.toSet()
            val orphans = games.filter { it.shelfId == null || it.shelfId !in known }.map { it.id }
            shelves.mapIndexed { i, s ->
                s.id to (games.filter { it.shelfId == s.id }.sortedBy { it.position }.map { it.id } + if (i == 0) orphans else emptyList())
            }.toMap()
        }
        var order by remember(dbOrder) { mutableStateOf(dbOrder) }
        var drag by remember { mutableStateOf<DragState?>(null) }
        val displayOrder = drag?.preview ?: order
        val layout = remember(shelves, displayOrder, gamesById, dims, spineMode) {
            computeLayout(shelves, displayOrder, gamesById, dims, spineMode)
        }
        val scroll = rememberScrollState()
        val currentLayout by rememberUpdatedState(layout)
        val currentOrder by rememberUpdatedState(order)
        val currentGames by rememberUpdatedState(gamesById)
        val currentSpine by rememberUpdatedState(spineMode)
        val currentShelves by rememberUpdatedState(shelves)

        fun moved(d: DragState, newPointer: Offset): DragState {
            val nd = d.copy(pointer = newPointer)
            val target = d.baseLayout.dropTarget(nd.center, dims) ?: return nd
            return nd.copy(preview = d.baseOrder.withInserted(d.id, target.first, target.second))
        }

        // Auto-scroll while dragging near the top or bottom edge.
        LaunchedEffect(drag != null) {
            while (drag != null) {
                awaitFrame()
                val d = drag ?: break
                val edge = with(density) { 80.dp.toPx() }
                val yInView = d.center.y - scroll.value
                val delta = when {
                    yInView < topPx + edge -> -((topPx + edge - yInView) / edge).coerceAtMost(1f) * 22f
                    yInView > viewportH - edge -> ((yInView - (viewportH - edge)) / edge).coerceAtMost(1f) * 22f
                    else -> 0f
                }
                if (delta != 0f) {
                    val consumed = scroll.scrollBy(delta)
                    if (consumed != 0f) drag = drag?.let { moved(it, it.pointer + Offset(0f, consumed)) }
                }
            }
        }

        val q = query.trim().lowercase()
        Box(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll, enabled = drag == null)
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(with(density) { layout.totalHeight.toDp() })
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { p -> currentLayout.hitTest(p)?.let(onOpen) })
                    }
                    .pointerInput(Unit) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { p ->
                                val l = currentLayout
                                val id = l.hitTest(p) ?: return@detectDragGesturesAfterLongPress
                                val item = l.items[id] ?: return@detectDragGesturesAfterLongPress
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                val base = currentOrder.without(id)
                                drag = DragState(
                                    id = id, pointer = p, grab = p - Offset(item.x, item.y),
                                    size = Size(item.w, item.h), baseOrder = base,
                                    baseLayout = computeLayout(currentShelves, base, currentGames, dims, currentSpine),
                                    preview = currentOrder,
                                )
                            },
                            onDrag = { change, amount ->
                                val d = drag ?: return@detectDragGesturesAfterLongPress
                                change.consume()
                                drag = moved(d, d.pointer + amount)
                            },
                            onDragEnd = {
                                drag?.let { d ->
                                    order = d.preview
                                    onOrder(d.preview)
                                }
                                drag = null
                            },
                            onDragCancel = { drag = null },
                        )
                    }
            ) {
                Canvas(Modifier.fillMaxSize()) { drawShelf(layout, dims) }

                // shelf labels on the boards
                layout.shelves.forEach { sp ->
                    Text(
                        sp.shelf.name,
                        style = ShelfLabelStyle,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .offset { IntOffset((dims.side + dims.inner).roundToInt(), (sp.boardTop + dims.board * 0.18f).roundToInt()) }
                            .background(Deco.label.copy(alpha = 0.9f), RoundedCornerShape(3.dp))
                            .padding(horizontal = 6.dp)
                    )
                }

                val dragging = drag
                layout.items.values.forEach { item ->
                    if (item.id == dragging?.id) return@forEach
                    val g = gamesById[item.id] ?: return@forEach
                    key(item.id) {
                        val pos by animateIntOffsetAsState(IntOffset(item.x.roundToInt(), item.y.roundToInt()), label = "box")
                        val match = q.isEmpty() || g.name.lowercase().contains(q) ||
                            (g.designers?.lowercase()?.contains(q) == true) || (g.categories?.lowercase()?.contains(q) == true)
                        ShelfBox(
                            g, if (spineMode) SPINE_YAW else COVER_YAW, SHELF_PITCH,
                            Modifier
                                .offset { pos }
                                .size(with(density) { item.w.toDp() }, with(density) { item.h.toDp() })
                                .alpha(if (match) 1f else 0.25f)
                        )
                    }
                }

                if (dragging != null) {
                    val g = gamesById[dragging.id]
                    if (g != null) {
                        val tl = dragging.pointer - dragging.grab
                        ShelfBox(
                            g, if (spineMode) SPINE_YAW else COVER_YAW, SHELF_PITCH,
                            Modifier
                                .offset { IntOffset(tl.x.roundToInt(), tl.y.roundToInt()) }
                                .size(with(density) { dragging.size.width.toDp() }, with(density) { dragging.size.height.toDp() })
                                .scale(1.1f)
                                .shadow(16.dp, RoundedCornerShape(2.dp), clip = false)
                        )
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawShelf(layout: ShelfLayout, d: ShelfDims) {
    val w = size.width
    if (layout.shelves.isEmpty()) return
    val top = d.startY
    val bottom = layout.shelves.last().boardTop + d.board
    // back panel
    layout.shelves.forEach { sp ->
        drawRect(
            Brush.verticalGradient(
                0f to Color(0xFF7E8790), 0.22f to Deco.back, 1f to Color(0xFFCDD3D9),
                startY = sp.top, endY = sp.boardTop
            ),
            topLeft = Offset(d.side, sp.top), size = Size(w - 2 * d.side, sp.height)
        )
        // subtle vertical plank seams of the back panel
        var x = d.side + 90f
        while (x < w - d.side) {
            drawLine(Color(0x14000000), Offset(x, sp.top), Offset(x, sp.boardTop), strokeWidth = 2f)
            x += 180f
        }
    }
    val plank = Brush.verticalGradient(listOf(Deco.plankLight, Deco.plank, Deco.plankDark))
    // crown
    drawRect(plank, topLeft = Offset(0f, top), size = Size(w, d.crown))
    // boards
    layout.shelves.forEach { sp ->
        drawRect(
            Brush.verticalGradient(listOf(Deco.plankLight, Deco.plank, Deco.plankDark), startY = sp.boardTop, endY = sp.boardTop + d.board),
            topLeft = Offset(0f, sp.boardTop), size = Size(w, d.board)
        )
        drawLine(Color(0xFFFFFFFF), Offset(0f, sp.boardTop + 1f), Offset(w, sp.boardTop + 1f), strokeWidth = 2f)
        for (k in 1..3) {
            val gy = sp.boardTop + d.board * k / 4f
            drawLine(Deco.grain, Offset(0f, gy), Offset(w, gy + (k - 2) * 3f), strokeWidth = 1.5f)
        }
        // shadow that the board casts onto the compartment below
        drawRect(
            Brush.verticalGradient(listOf(Color(0x55000000), Color.Transparent), startY = sp.boardTop + d.board, endY = sp.boardTop + d.board + 40f),
            topLeft = Offset(d.side, sp.boardTop + d.board), size = Size(w - 2 * d.side, 40f)
        )
    }
    // side walls
    val sideBrush = Brush.horizontalGradient(listOf(Deco.plankDark, Deco.plank, Deco.plankLight, Deco.plank), startX = 0f, endX = d.side)
    drawRect(sideBrush, topLeft = Offset(0f, top), size = Size(d.side, bottom - top))
    val sideBrushR = Brush.horizontalGradient(listOf(Deco.plank, Deco.plankLight, Deco.plank, Deco.plankDark), startX = w - d.side, endX = w)
    drawRect(sideBrushR, topLeft = Offset(w - d.side, top), size = Size(d.side, bottom - top))
}
