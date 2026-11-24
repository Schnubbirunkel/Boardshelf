package io.github.schnubbirunkel.boardshelf.ui.manuals

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import io.github.schnubbirunkel.boardshelf.ui.theme.bouncyClick
import io.github.schnubbirunkel.boardshelf.ui.theme.glass
import coil.request.ImageRequest
import io.github.schnubbirunkel.boardshelf.R
import io.github.schnubbirunkel.boardshelf.data.Game
import io.github.schnubbirunkel.boardshelf.ui.app
import io.github.schnubbirunkel.boardshelf.ui.errorText
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.util.Locale

/**
 * Lets the user pick the exact cover image on the web (image search, Amazon, BGG website …).
 * Long-press an image → preview → "use as cover". Nothing is chosen automatically, so a photo
 * of a game table can never end up as cover by accident.
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CoverSearchScreen(gameId: Long, onDone: () -> Unit, onPicked: (String) -> Unit) {
    val context = LocalContext.current
    val repo = context.app().repository
    val game by produceState<Game?>(null, gameId) { value = repo.game(gameId).first() }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var progress by remember { mutableIntStateOf(0) }
    var picked by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var selectedSource by remember { mutableIntStateOf(0) }

    val g = game
    val enc = { s: String -> URLEncoder.encode(s, "UTF-8") }
    val german = Locale.getDefault().language == "de"
    val sources: List<Pair<String, String>> = if (g == null) emptyList() else {
        val q = "${g.name} ${context.getString(R.string.cover_search_term)}"
        val amazonHost = when (Locale.getDefault().country) {
            "AT", "DE", "CH" -> "www.amazon.de"
            "GB" -> "www.amazon.co.uk"
            "FR" -> "www.amazon.fr"
            else -> if (german) "www.amazon.de" else "www.amazon.com"
        }
        listOf(
            context.getString(R.string.cover_source_images) to "https://duckduckgo.com/?q=${enc(q)}&iax=images&ia=images",
            "Google" to "https://www.google.com/search?tbm=isch&q=${enc(q)}",
            "Amazon" to "https://$amazonHost/s?k=${enc("${g.name} ${context.getString(R.string.cover_amazon_term)}")}",
            "BoardGameGeek" to (g.bggId?.let { "https://boardgamegeek.com/boardgame/$it/x/images" }
                ?: "https://boardgamegeek.com/geeksearch.php?action=search&objecttype=boardgame&q=${enc(g.name)}"),
        )
    }

    BackHandler(enabled = webView?.canGoBack() == true) { webView?.goBack() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                colors = io.github.schnubbirunkel.boardshelf.ui.theme.glassTopBarColors(),
                title = {
                    Column {
                        Text(stringResource(R.string.cover_search_title), style = MaterialTheme.typography.titleMedium)
                        Text(g?.name ?: "", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.Default.Close, stringResource(R.string.action_close)) } }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                sources.forEachIndexed { i, (label, url) ->
                    FilterChip(
                        selected = selectedSource == i,
                        onClick = { selectedSource = i; webView?.loadUrl(url) },
                        label = { Text(label) }
                    )
                }
            }
            // automatic suggestions from the key-less sources (best first)
            if (g != null) {
                val candidates by produceState<List<io.github.schnubbirunkel.boardshelf.net.CoverCandidate>?>(null, g.id) {
                    value = runCatching { repo.coverCandidates(g) }.getOrDefault(emptyList())
                }
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 6.dp)) {
                    Text(stringResource(R.string.cover_suggestions), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    if (repo.ai.enabled) {
                        androidx.compose.material3.FilledTonalButton(
                            enabled = !saving,
                            onClick = {
                                saving = true
                                scope.launch {
                                    try {
                                        val ok = repo.aiEnrich(g.id, replaceCover = true)
                                        if (ok) onDone() else snackbar.showSnackbar(context.getString(R.string.cover_ai_none))
                                    } catch (e: Exception) {
                                        snackbar.showSnackbar(context.errorText(e))
                                    } finally {
                                        saving = false
                                    }
                                }
                            }
                        ) { Text(stringResource(R.string.cover_ai_pick)) }
                    }
                }
                when (val c = candidates) {
                    null -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(12.dp))
                    else -> if (c.isEmpty()) Text(
                        stringResource(R.string.cover_suggestions_none), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 12.dp)
                    ) else androidx.compose.foundation.lazy.LazyRow(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(c.size) { i ->
                            val cand = c[i]
                            Column(
                                Modifier.width(112.dp).bouncyClick { picked = cand.url },
                                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                            ) {
                                AsyncImage(
                                    cand.url, cand.label, contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(112.dp).glass(RoundedCornerShape(18.dp))
                                )
                                Text(cand.source, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            }
                        }
                    }
                }
            }
            Text(
                stringResource(R.string.cover_search_hint), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
            )
            if (progress in 1..99 || saving) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (g != null) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.builtInZoomControls = true
                            settings.displayZoomControls = false
                            CookieManager.getInstance().setAcceptCookie(true)
                            webViewClient = WebViewClient()
                            webChromeClient = object : android.webkit.WebChromeClient() {
                                override fun onProgressChanged(view: WebView, newProgress: Int) {
                                    progress = newProgress
                                }
                            }
                            setOnLongClickListener { v ->
                                val wv = v as WebView
                                val hit = wv.hitTestResult
                                when (hit.type) {
                                    WebView.HitTestResult.IMAGE_TYPE -> {
                                        hit.extra?.let { picked = it }; true
                                    }
                                    WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                                        // image inside a link: ask the WebView for the image's own src
                                        val handler = Handler(Looper.getMainLooper()) { msg: Message ->
                                            (msg.data.getString("src") ?: hit.extra)?.let { picked = it }
                                            true
                                        }
                                        wv.requestImageRef(handler.obtainMessage())
                                        true
                                    }
                                    else -> false
                                }
                            }
                            webView = this
                            loadUrl(sources.first().second)
                        }
                    },
                    onRelease = { it.destroy() }
                )
            }
        }
    }

    picked?.let { url ->
        AlertDialog(
            onDismissRequest = { picked = null },
            title = { Text(stringResource(R.string.cover_use_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(
                            if (url.startsWith("data:")) runCatching { android.util.Base64.decode(url.substringAfter("base64,"), android.util.Base64.DEFAULT) }.getOrNull() else url
                        ).crossfade(true).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp).background(Color(0x22FFFFFF))
                    )
                    Text(stringResource(R.string.cover_use_hint), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    picked = null
                    saving = true
                    scope.launch {
                        try {
                            val headers = buildMap {
                                if (!url.startsWith("data:")) CookieManager.getInstance().getCookie(url)?.let { put("Cookie", it) }
                                webView?.settings?.userAgentString?.let { put("User-Agent", it) }
                                webView?.url?.let { put("Referer", it) }
                            }
                            val file = repo.downloadCoverCandidate(url, headers)
                            onPicked(file.path)
                        } catch (e: Exception) {
                            snackbar.showSnackbar(context.errorText(e))
                        } finally {
                            saving = false
                        }
                    }
                }) { Text(stringResource(R.string.cover_use_confirm)) }
            },
            dismissButton = { TextButton(onClick = { picked = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
