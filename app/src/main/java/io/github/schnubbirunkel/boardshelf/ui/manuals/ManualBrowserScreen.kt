package io.github.schnubbirunkel.boardshelf.ui.manuals

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.schnubbirunkel.boardshelf.R
import io.github.schnubbirunkel.boardshelf.data.Game
import io.github.schnubbirunkel.boardshelf.net.BggApi
import io.github.schnubbirunkel.boardshelf.ui.app
import io.github.schnubbirunkel.boardshelf.ui.errorText
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.net.URLEncoder

/**
 * In-app browser to find rulebooks (BGG file section, publisher sites, web search).
 * Any PDF the user opens or downloads can be stored as manual of the game.
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ManualBrowserScreen(gameId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = context.app().repository
    val game by produceState<Game?>(null, gameId) { value = repo.game(gameId).first() }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var progress by remember { mutableStateOf(0) }
    var currentUrl by remember { mutableStateOf("") }
    var offer by remember { mutableStateOf<Pair<String, String?>?>(null) } // url + suggested name
    var saving by remember { mutableStateOf(false) }

    val g = game
    val enc = { s: String -> URLEncoder.encode(s, "UTF-8") }
    val sources: List<Pair<String, String>> = if (g == null) emptyList() else buildList {
        g.bggId?.let { add(context.getString(R.string.browser_bgg_files) to BggApi.filesUrl(it)) }
        val q = context.getString(R.string.manual_search_query, g.name)
        add("DuckDuckGo" to "https://duckduckgo.com/?q=${enc(q)}")
        add("Google" to "https://www.google.com/search?q=${enc(q)}")
        add("1j1ju" to "https://www.1j1ju.com/?s=${enc(g.name)}")
    }

    BackHandler(enabled = webView?.canGoBack() == true) { webView?.goBack() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.browser_title), style = MaterialTheme.typography.titleMedium)
                        Text(currentUrl, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.Close, stringResource(R.string.action_close)) } }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                sources.forEach { (label, url) ->
                    AssistChip(onClick = { webView?.loadUrl(url) }, label = { Text(label) })
                }
            }
            Text(
                stringResource(R.string.browser_hint), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 12.dp)
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
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                    val u = request.url.toString()
                                    if (u.substringBefore('?').lowercase().endsWith(".pdf")) {
                                        offer = u to null
                                        return true
                                    }
                                    return false
                                }

                                override fun onPageFinished(view: WebView, url: String) {
                                    currentUrl = url
                                }
                            }
                            webChromeClient = object : android.webkit.WebChromeClient() {
                                override fun onProgressChanged(view: WebView, newProgress: Int) {
                                    progress = newProgress
                                }
                            }
                            setDownloadListener { url, _, contentDisposition, mimetype, _ ->
                                val name = URLUtil.guessFileName(url, contentDisposition, mimetype)
                                offer = url to name
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

    offer?.let { (url, name) ->
        var title by remember(url) {
            mutableStateOf(
                name?.substringBeforeLast('.')?.takeIf { it.isNotBlank() && it.length > 3 }
                    ?: context.getString(R.string.manual_default_title, g?.name ?: "")
            )
        }
        AlertDialog(
            onDismissRequest = { offer = null },
            title = { Text(stringResource(R.string.browser_save_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(url, style = MaterialTheme.typography.labelSmall, maxLines = 3)
                    OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.field_title)) }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    offer = null
                    saving = true
                    scope.launch {
                        try {
                            val headers = buildMap {
                                CookieManager.getInstance().getCookie(url)?.let { put("Cookie", it) }
                                webView?.settings?.userAgentString?.let { put("User-Agent", it) }
                                webView?.url?.let { put("Referer", it) }
                            }
                            repo.downloadManual(gameId, url, title, headers)
                            snackbar.showSnackbar(context.getString(R.string.manual_saved))
                        } catch (e: Exception) {
                            snackbar.showSnackbar(context.errorText(e))
                        } finally {
                            saving = false
                        }
                    }
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { offer = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
