package io.github.schnubbirunkel.boardshelf.ui.manuals

import android.graphics.Bitmap
import android.graphics.Color as AColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.schnubbirunkel.boardshelf.R
import io.github.schnubbirunkel.boardshelf.data.Manual
import io.github.schnubbirunkel.boardshelf.data.ManualKind
import io.github.schnubbirunkel.boardshelf.data.SaveState
import io.github.schnubbirunkel.boardshelf.ui.app
import io.github.schnubbirunkel.boardshelf.util.formatDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Thread-safe wrapper around [PdfRenderer]. */
private class PdfDoc(file: File) {
    private val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(fd)
    private val mutex = Mutex()
    val pageCount = renderer.pageCount
    val aspect: List<Float> = (0 until pageCount).map { i ->
        renderer.openPage(i).use { it.width.toFloat() / it.height.toFloat() }
    }

    suspend fun render(index: Int, width: Int): Bitmap = mutex.withLock {
        withContext(Dispatchers.IO) {
            renderer.openPage(index).use { page ->
                val h = (width * page.height.toFloat() / page.width).toInt().coerceAtLeast(1)
                val bmp = Bitmap.createBitmap(width, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(AColor.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bmp
            }
        }
    }

    fun close() {
        try { renderer.close(); fd.close() } catch (_: Exception) {}
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualViewerScreen(manualId: Long, onBack: () -> Unit) {
    val repo = LocalContext.current.app().repository
    val manual by produceState<Manual?>(null, manualId) { value = repo.manual(manualId) }
    Scaffold(
        containerColor = Color(0xFF111418),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1A1F25)),
                title = { Text(manual?.title ?: "", maxLines = 1) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } }
            )
        }
    ) { padding ->
        val m = manual ?: return@Scaffold
        val zoom = remember { ZoomState() }
        val listState = rememberLazyListState()
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (m.kind == ManualKind.PDF) {
                val file = repo.files.resolve(m.path)
                val doc = remember(file) { file?.let { runCatching { PdfDoc(it) }.getOrNull() } }
                DisposableEffect(doc) { onDispose { doc?.close() } }
                if (doc == null) {
                    Text(stringResource(R.string.manual_open_failed), Modifier.align(Alignment.Center))
                    return@Box
                }
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val renderWidth = (constraints.maxWidth * 1.3f).toInt().coerceIn(400, 1600)
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().zoomable(zoom, listState),
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(doc.pageCount) { i ->
                            val bmp by produceState<Bitmap?>(null, i, renderWidth) { value = doc.render(i, renderWidth) }
                            Box(Modifier.fillMaxWidth().aspectRatio(doc.aspect[i]).background(Color.White)) {
                                bmp?.let {
                                    Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                                } ?: CircularProgressIndicator(Modifier.align(Alignment.Center))
                            }
                        }
                    }
                }
            } else {
                val pages = remember(m) { repo.manualPages(m) }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().zoomable(zoom, listState),
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(pages.size) { i ->
                        AsyncImage(pages[i], null, Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PhotoViewerScreen(saveStateId: Long, startIndex: Int, onBack: () -> Unit) {
    val repo = LocalContext.current.app().repository
    val state by produceState<SaveState?>(null, saveStateId) { value = repo.saveState(saveStateId) }
    val photos by produceState<List<String>>(emptyList(), saveStateId) {
        value = repo.photosOf(saveStateId).map { it.path }.ifEmpty { listOfNotNull(repo.saveState(saveStateId)?.photoPath) }
    }
    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0x99000000)),
                title = {
                    Column {
                        Text(state?.title ?: "", maxLines = 1)
                        state?.let { Text(formatDateTime(it.createdAt), style = MaterialTheme.typography.labelSmall) }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } }
            )
        }
    ) { padding ->
        val s = state ?: return@Scaffold
        if (photos.isEmpty()) return@Scaffold
        val pager = androidx.compose.foundation.pager.rememberPagerState(initialPage = startIndex.coerceIn(0, photos.lastIndex)) { photos.size }
        Box(Modifier.fillMaxSize().padding(padding)) {
            androidx.compose.foundation.pager.HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
                val zoom = remember(page) { ZoomState() }
                AsyncImage(
                    repo.files.resolve(photos[page]), s.title,
                    Modifier.fillMaxSize().zoomable(zoom), contentScale = ContentScale.Fit
                )
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0xAA000000)).padding(16.dp)) {
                if (photos.size > 1) Text("${pager.currentPage + 1} / ${photos.size}", color = Color.White, style = MaterialTheme.typography.labelMedium)
                s.note?.let { Text(it, color = Color.White) }
            }
        }
    }
}
