package io.github.schnubbirunkel.boardshelf.ui.add

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import io.github.schnubbirunkel.boardshelf.R
import io.github.schnubbirunkel.boardshelf.data.Repository
import io.github.schnubbirunkel.boardshelf.net.SearchHit
import io.github.schnubbirunkel.boardshelf.net.Source
import io.github.schnubbirunkel.boardshelf.ui.app
import io.github.schnubbirunkel.boardshelf.ui.errorText
import io.github.schnubbirunkel.boardshelf.ui.theme.GlassCard
import io.github.schnubbirunkel.boardshelf.ui.theme.glassTopBarColors
import kotlinx.coroutines.launch

private sealed interface ScanState {
    data object Idle : ScanState
    data class Loading(val code: String) : ScanState
    data class Done(val result: Repository.BarcodeResult) : ScanState
    data class Failed(val message: String) : ScanState
}

/**
 * Scan the barcode on the box → product databases (UPCitemdb, Open Products Facts, Wikidata GTIN)
 * → probable title → matching games. The shop picture of exactly this box becomes the cover.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarcodeScreen(initialCode: String?, onBack: () -> Unit, onOpenGame: (Long) -> Unit) {
    val context = LocalContext.current
    val repo = context.app().repository
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<ScanState>(ScanState.Idle) }
    var query by rememberSaveable { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
    var importing by remember { mutableStateOf(false) }

    fun lookup(code: String) {
        state = ScanState.Loading(code)
        scope.launch {
            state = try {
                val r = repo.lookupBarcode(code)
                if (r.existing != null) { onOpenGame(r.existing.id); return@launch }
                query = r.query ?: ""
                hits = r.hits
                ScanState.Done(r)
            } catch (e: Exception) {
                ScanState.Failed(context.errorText(e))
            }
        }
    }

    fun scan() {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
            .enableAutoZoom()
            .build()
        GmsBarcodeScanning.getClient(context, options).startScan()
            .addOnSuccessListener { b -> b.rawValue?.let { lookup(it) } }
            .addOnCanceledListener { if (state is ScanState.Idle) onBack() }
            .addOnFailureListener { e -> state = ScanState.Failed(context.getString(R.string.barcode_scanner_failed, e.localizedMessage ?: "")) }
    }

    LaunchedEffect(Unit) { if (initialCode != null) lookup(initialCode) else scan() }

    fun import(hit: SearchHit?, manualName: String?) {
        val r = (state as? ScanState.Done)?.result ?: return
        importing = true
        scope.launch {
            try {
                onOpenGame(repo.importFromBarcode(hit, r, manualName))
            } catch (e: Exception) {
                state = ScanState.Failed(context.errorText(e))
            } finally {
                importing = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = glassTopBarColors(),
                title = { Text(stringResource(R.string.barcode_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } }
            )
        }
    ) { padding ->
        AnimatedContent(state, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "scan", modifier = Modifier.padding(padding)) { st ->
            when (st) {
                ScanState.Idle -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Button(onClick = ::scan) { Icon(Icons.Default.QrCodeScanner, null); Text(" " + stringResource(R.string.action_scan)) }
                }
                is ScanState.Loading -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.barcode_searching, st.code), Modifier.padding(16.dp))
                }
                is ScanState.Failed -> Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(st.message, color = MaterialTheme.colorScheme.error)
                    Button(onClick = ::scan, modifier = Modifier.padding(top = 16.dp)) { Text(stringResource(R.string.barcode_rescan)) }
                }
                is ScanState.Done -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        val p = st.result.products.firstOrNull()
                        val upc = st.result.hits.firstOrNull { it.source == Source.GAMEUPC }
                        GlassCard(Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                val img = upc?.thumbnail ?: st.result.products.flatMap { it.images }.firstOrNull()
                                if (img != null) AsyncImage(img, null, Modifier.size(84.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
                                Column(Modifier.weight(1f)) {
                                    Text(stringResource(R.string.barcode_code, st.result.ean), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(upc?.name ?: p?.title ?: stringResource(R.string.barcode_unknown), style = MaterialTheme.typography.titleMedium)
                                    (if (upc != null) "GameUPC" else p?.source)?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
                                }
                            }
                        }
                    }
                    item {
                        OutlinedTextField(
                            query, { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                            label = { Text(stringResource(R.string.barcode_search_label)) },
                            trailingIcon = {
                                IconButton(onClick = { scope.launch { hits = repo.search(query).hits } }) { Icon(Icons.Default.Search, null) }
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { scope.launch { hits = repo.search(query).hits } }),
                        )
                    }
                    item {
                        Text(
                            stringResource(if (hits.isEmpty()) R.string.barcode_no_match else R.string.barcode_pick),
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    items(hits, key = { it.source.name + it.id }) { hit ->
                        GlassCard(Modifier.fillMaxWidth(), onClick = { if (!importing) import(hit, null) }) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (hit.thumbnail != null) AsyncImage(hit.thumbnail, null, Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                                else Icon(Icons.Default.Casino, null, Modifier.size(52.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(hit.name, style = MaterialTheme.typography.titleMedium)
                                    val sub = listOfNotNull(hit.year?.toString(), hit.subtitle).joinToString(" · ")
                                    if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                                }
                                Text(when (hit.source) { Source.BGG -> "BGG"; Source.GAMEUPC -> "GameUPC"; else -> "Wikidata" }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                            FilledTonalButton(enabled = !importing && query.isNotBlank(), onClick = { import(null, query.trim()) }) {
                                Text(stringResource(R.string.barcode_add_manual))
                            }
                            FilledTonalButton(onClick = ::scan) { Text(stringResource(R.string.barcode_rescan)) }
                        }
                        if (importing) CircularProgressIndicator(Modifier.padding(16.dp))
                    }
                }
            }
        }
    }
}
