package io.github.schnubbirunkel.boardshelf.ui.manuals

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.schnubbirunkel.boardshelf.R
import io.github.schnubbirunkel.boardshelf.ui.app
import io.github.schnubbirunkel.boardshelf.ui.errorText
import io.github.schnubbirunkel.boardshelf.util.Images
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private enum class Handle { NONE, MOVE, TL, TR, BR, BL }

/**
 * Crop a cover candidate before it becomes the box front. The crop rectangle lives in
 * bitmap pixel coordinates; aspect can be locked to the box (from its dimensions), square or free.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropScreen(gameId: Long, path: String, onDone: () -> Unit) {
    val context = LocalContext.current
    val repo = context.app().repository
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val source = remember(path) { File(path) }

    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    var boxRatio by remember { mutableFloatStateOf(1f) }
    var mode by remember { mutableIntStateOf(0) } // 0 = box, 1 = square, 2 = free
    var crop by remember { mutableStateOf(Rect.Zero) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun ratio(): Float? = when (mode) { 0 -> boxRatio; 1 -> 1f; else -> null }

    fun initialCrop(b: Bitmap): Rect {
        val r = ratio() ?: return Rect(0f, 0f, b.width.toFloat(), b.height.toFloat()).deflate(min(b.width, b.height) * 0.04f)
        var w = b.width.toFloat()
        var h = w / r
        if (h > b.height) { h = b.height.toFloat(); w = h * r }
        w *= 0.92f; h *= 0.92f
        return Rect(Offset((b.width - w) / 2f, (b.height - h) / 2f), Size(w, h))
    }

    LaunchedEffect(path) {
        val g = repo.game(gameId).first()
        g?.let { boxRatio = (it.boxWidthCm / it.boxHeightCm).coerceIn(0.2f, 5f) }
        val b = withContext(Dispatchers.IO) { Images.decodeSampled(source, 2048) }
        if (b == null) { error = context.getString(R.string.err_generic); return@LaunchedEffect }
        bitmap = b; image = b.asImageBitmap(); crop = initialCrop(b)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = io.github.schnubbirunkel.boardshelf.ui.theme.glassTopBarColors(),
                title = { Text(stringResource(R.string.crop_title)) },
                navigationIcon = { IconButton(onClick = { source.delete(); onDone() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
                actions = {
                    IconButton(onClick = {
                        bitmap?.let { b ->
                            val m = Matrix().apply { postRotate(90f) }
                            val r = Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
                            bitmap = r; image = r.asImageBitmap(); crop = initialCrop(r)
                        }
                    }) { Icon(Icons.AutoMirrored.Filled.RotateRight, stringResource(R.string.crop_rotate)) }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(R.string.crop_box, R.string.crop_square, R.string.crop_free).forEachIndexed { i, label ->
                    FilterChip(selected = mode == i, onClick = { mode = i; bitmap?.let { crop = initialCrop(it) } }, label = { Text(stringResource(label)) })
                }
            }
            Text(
                stringResource(R.string.crop_hint), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(16.dp)) {
                val img = image
                val b = bitmap
                if (img == null || b == null) {
                    error?.let { Text(it, Modifier.align(Alignment.Center)) } ?: CircularProgressIndicator(Modifier.align(Alignment.Center))
                    return@BoxWithConstraints
                }
                val vw = constraints.maxWidth.toFloat()
                val vh = constraints.maxHeight.toFloat()
                val s = min(vw / b.width, vh / b.height)
                val ox = (vw - b.width * s) / 2f
                val oy = (vh - b.height * s) / 2f
                val touch = with(density) { 32.dp.toPx() }
                val minSize = max(40f, 40f / s)

                Canvas(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(b, mode, vw, vh) {
                            var handle = Handle.NONE
                            detectDragGestures(
                                onDragStart = { p ->
                                    val c = crop
                                    fun near(x: Float, y: Float) = abs(p.x - (ox + x * s)) < touch && abs(p.y - (oy + y * s)) < touch
                                    handle = when {
                                        near(c.left, c.top) -> Handle.TL
                                        near(c.right, c.top) -> Handle.TR
                                        near(c.right, c.bottom) -> Handle.BR
                                        near(c.left, c.bottom) -> Handle.BL
                                        Rect(ox + c.left * s, oy + c.top * s, ox + c.right * s, oy + c.bottom * s).contains(p) -> Handle.MOVE
                                        else -> Handle.NONE
                                    }
                                },
                                onDragEnd = { handle = Handle.NONE },
                            ) { change, drag ->
                                if (handle == Handle.NONE) return@detectDragGestures
                                change.consume()
                                val dx = drag.x / s
                                val dy = drag.y / s
                                val c = crop
                                val bw = b.width.toFloat(); val bh = b.height.toFloat()
                                crop = if (handle == Handle.MOVE) {
                                    val nx = (c.left + dx).coerceIn(0f, bw - c.width)
                                    val ny = (c.top + dy).coerceIn(0f, bh - c.height)
                                    Rect(Offset(nx, ny), c.size)
                                } else {
                                    // the opposite corner stays fixed
                                    val ax = if (handle == Handle.TL || handle == Handle.BL) c.right else c.left
                                    val ay = if (handle == Handle.TL || handle == Handle.TR) c.bottom else c.top
                                    val px = (if (handle == Handle.TL || handle == Handle.BL) c.left else c.right) + dx
                                    val py = (if (handle == Handle.TL || handle == Handle.TR) c.top else c.bottom) + dy
                                    val sx = if (px < ax) -1f else 1f
                                    val sy = if (py < ay) -1f else 1f
                                    val maxW = if (sx < 0) ax else bw - ax
                                    val maxH = if (sy < 0) ay else bh - ay
                                    var w = abs(px - ax).coerceIn(minSize, maxW)
                                    var h = abs(py - ay).coerceIn(minSize, maxH)
                                    ratio()?.let { r ->
                                        // follow the larger movement, keep the ratio, stay inside the image
                                        if (w / r > h) h = w / r else w = h * r
                                        if (w > maxW) { w = maxW; h = w / r }
                                        if (h > maxH) { h = maxH; w = h * r }
                                    }
                                    val l = if (sx < 0) ax - w else ax
                                    val t = if (sy < 0) ay - h else ay
                                    Rect(l, t, l + w, t + h)
                                }
                            }
                        }
                ) {
                    drawImage(
                        img,
                        dstOffset = IntOffset(ox.roundToInt(), oy.roundToInt()),
                        dstSize = IntSize((b.width * s).roundToInt(), (b.height * s).roundToInt())
                    )
                    val c = crop
                    val r = Rect(ox + c.left * s, oy + c.top * s, ox + c.right * s, oy + c.bottom * s)
                    val shade = Color(0xAA000000)
                    drawRect(shade, Offset(0f, 0f), Size(size.width, r.top))
                    drawRect(shade, Offset(0f, r.bottom), Size(size.width, size.height - r.bottom))
                    drawRect(shade, Offset(0f, r.top), Size(r.left, r.height))
                    drawRect(shade, Offset(r.right, r.top), Size(size.width - r.right, r.height))
                    drawRect(Color.White, r.topLeft, r.size, style = Stroke(2.dp.toPx()))
                    for (k in 1..2) {
                        drawLine(Color(0x66FFFFFF), Offset(r.left + r.width * k / 3f, r.top), Offset(r.left + r.width * k / 3f, r.bottom), 1f)
                        drawLine(Color(0x66FFFFFF), Offset(r.left, r.top + r.height * k / 3f), Offset(r.right, r.top + r.height * k / 3f), 1f)
                    }
                    val hl = 18.dp.toPx(); val hw = 4.dp.toPx()
                    listOf(r.topLeft to Offset(1f, 1f), r.topRight to Offset(-1f, 1f), r.bottomRight to Offset(-1f, -1f), r.bottomLeft to Offset(1f, -1f))
                        .forEach { (p, d) ->
                            drawLine(Color.White, p, p + Offset(hl * d.x, 0f), hw)
                            drawLine(Color.White, p, p + Offset(0f, hl * d.y), hw)
                        }
                }
            }
            error?.let { if (bitmap != null) Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp)) }
            Button(
                enabled = bitmap != null && !saving,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                onClick = {
                    val b = bitmap ?: return@Button
                    saving = true
                    scope.launch {
                        try {
                            val out = withContext(Dispatchers.IO) {
                                val c = crop
                                val l = c.left.roundToInt().coerceIn(0, b.width - 1)
                                val t = c.top.roundToInt().coerceIn(0, b.height - 1)
                                val w = c.width.roundToInt().coerceIn(1, b.width - l)
                                val h = c.height.roundToInt().coerceIn(1, b.height - t)
                                val cropped = Bitmap.createBitmap(b, l, t, w, h)
                                val f = repo.newCoverCandidate()
                                f.outputStream().use { cropped.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                                f
                            }
                            repo.setCoverFromFile(gameId, out)
                            out.delete(); source.delete()
                            onDone()
                        } catch (e: Exception) {
                            error = context.errorText(e)
                        } finally {
                            saving = false
                        }
                    }
                }
            ) { Text(stringResource(R.string.crop_apply)) }
        }
    }
}

