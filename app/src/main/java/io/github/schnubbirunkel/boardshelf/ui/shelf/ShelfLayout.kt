package io.github.schnubbirunkel.boardshelf.ui.shelf

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.schnubbirunkel.boardshelf.data.Game
import io.github.schnubbirunkel.boardshelf.data.Shelf

/** Boxes stand with their spine facing straight out. */
const val SPINE_YAW = -90f
const val SHELF_PITCH = 0f

/** All measurements of the shelf in pixels. */
class ShelfDims(density: Density, val width: Float, val startY: Float, val bottomExtra: Float) {
    private fun Density.px(v: Float) = v.dp.toPx()
    val side = density.px(14f)
    val inner = density.px(8f)
    val lineHeight = density.px(150f)
    val board = density.px(18f)
    val crown = density.px(16f)
    val gapCover = density.px(7f)
    val gapSpine = density.px(1f)
    val pxPerCm = density.px(4.3f)
    val maxBoxHeight = lineHeight - density.px(12f)
    val left get() = side + inner
    val right get() = width - side - inner
}

data class ItemPos(
    val id: Long, val shelfId: Long, val index: Int, val line: Int,
    val x: Float, val y: Float, val w: Float, val h: Float,
) {
    val rect get() = Rect(x, y, x + w, y + h)
}

data class ShelfPos(val shelf: Shelf, val top: Float, val height: Float, val lines: Int) {
    val boardTop get() = top + height
}

data class ShelfLayout(val items: Map<Long, ItemPos>, val shelves: List<ShelfPos>, val totalHeight: Float)

/** Footprint of a box on the shelf in cm: it stands upright with the spine (depth × height) facing out. */
@Suppress("UNUSED_PARAMETER")
fun footprintCm(g: Game, spine: Boolean): Pair<Float, Float> = g.boxDepthCm to g.boxHeightCm

fun computeLayout(
    shelves: List<Shelf>,
    order: Map<Long, List<Long>>,
    games: Map<Long, Game>,
    dims: ShelfDims,
    spine: Boolean,
): ShelfLayout {
    val items = HashMap<Long, ItemPos>()
    val out = ArrayList<ShelfPos>()
    var y = dims.startY + dims.crown
    val gap = if (spine) dims.gapSpine else dims.gapCover
    val maxW = dims.right - dims.left
    for (shelf in shelves) {
        var x = dims.left
        var line = 0
        order[shelf.id].orEmpty().forEachIndexed { i, id ->
            val g = games[id] ?: return@forEachIndexed
            val (cw, ch) = footprintCm(g, spine)
            var w = cw * dims.pxPerCm
            var h = ch * dims.pxPerCm
            if (h > dims.maxBoxHeight) { val f = dims.maxBoxHeight / h; w *= f; h *= f }
            if (w > maxW) { val f = maxW / w; w *= f; h *= f }
            if (x + w > dims.right && x > dims.left) { line++; x = dims.left }
            items[id] = ItemPos(id, shelf.id, i, line, x, y + line * dims.lineHeight + dims.lineHeight - h, w, h)
            x += w + gap
        }
        val lines = line + 1
        val height = lines * dims.lineHeight
        out += ShelfPos(shelf, y, height, lines)
        y += height + dims.board
    }
    return ShelfLayout(items, out, y + dims.bottomExtra)
}

fun ShelfLayout.hitTest(p: Offset): Long? =
    items.values.firstOrNull { it.rect.contains(p) }?.id

/** Where would a box with its centre at [p] be inserted? Returns shelf id and index. */
fun ShelfLayout.dropTarget(p: Offset, dims: ShelfDims): Pair<Long, Int>? {
    if (shelves.isEmpty()) return null
    val sp = shelves.firstOrNull { p.y < it.boardTop + dims.board / 2 } ?: shelves.last()
    val line = ((p.y - sp.top) / dims.lineHeight).toInt().coerceIn(0, sp.lines - 1)
    val onShelf = items.values.filter { it.shelfId == sp.shelf.id }.sortedBy { it.index }
    val idx = onShelf.indexOfFirst { it.line > line || (it.line == line && it.x + it.w / 2 > p.x) }
    return sp.shelf.id to (if (idx < 0) onShelf.size else idx)
}

fun Map<Long, List<Long>>.without(id: Long): Map<Long, List<Long>> = mapValues { (_, v) -> v.filter { it != id } }

fun Map<Long, List<Long>>.withInserted(id: Long, shelfId: Long, index: Int): Map<Long, List<Long>> =
    mapValues { (sid, v) ->
        if (sid == shelfId) v.toMutableList().apply { add(index.coerceIn(0, size), id) } else v
    }

/** The box under (or next to) the finger on the shelf line the finger is on – for the peek gesture. */
fun ShelfLayout.peekTarget(p: Offset, dims: ShelfDims): Long? {
    val sp = shelves.firstOrNull { p.y >= it.top && p.y < it.boardTop + dims.board } ?: return null
    val line = ((p.y - sp.top) / dims.lineHeight).toInt().coerceIn(0, sp.lines - 1)
    val onLine = items.values.filter { it.shelfId == sp.shelf.id && it.line == line }
    if (onLine.isEmpty()) return null
    onLine.firstOrNull { p.x >= it.x && p.x <= it.x + it.w }?.let { return it.id }
    val nearest = onLine.minByOrNull { kotlin.math.abs(it.x + it.w / 2 - p.x) } ?: return null
    return if (kotlin.math.abs(nearest.x + nearest.w / 2 - p.x) < dims.lineHeight / 3) nearest.id else null
}
