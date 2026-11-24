package io.github.schnubbirunkel.boardshelf.ui.settings

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.schnubbirunkel.boardshelf.R
import io.github.schnubbirunkel.boardshelf.data.BggCsv
import io.github.schnubbirunkel.boardshelf.ui.app
import io.github.schnubbirunkel.boardshelf.ui.errorText
import io.github.schnubbirunkel.boardshelf.ui.rememberDocumentPicker
import io.github.schnubbirunkel.boardshelf.ui.theme.GlassCard
import io.github.schnubbirunkel.boardshelf.ui.theme.glassTopBarColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder

/**
 * Imports the user's own BoardGameGeek collection without API token: the user logs in to BGG
 * inside the app and Boardshelf downloads the collection export (CSV) with that session –
 * or the user picks a CSV file exported on the BGG website.
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BggImportScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = context.app().repository
    val scope = rememberCoroutineScope()
    var username by rememberSaveable { mutableStateOf(repo.settings.current.bggUsername) }
    var expansions by rememberSaveable { mutableStateOf(false) }
    var onlyOwned by rememberSaveable { mutableStateOf(true) }
    var status by remember { mutableStateOf<String?>(null) }
    var running by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    val enrich by repo.enrichProgress.collectAsStateWithLifecycle()

    fun runImport(text: String) {
        running = true
        scope.launch {
            status = try {
                val r = repo.importBggCsv(text, expansions, onlyOwned) { done, total ->
                    status = context.getString(R.string.settings_import_progress, done, total)
                }
                context.getString(R.string.import_done, r.added, r.skipped) +
                    if (r.expansionsSkipped > 0) " " + context.getString(R.string.import_expansions_skipped, r.expansionsSkipped) else ""
            } catch (e: Exception) {
                context.errorText(e)
            } finally {
                running = false
            }
        }
    }

    fun downloadExport() {
        val user = username.trim()
        if (user.isEmpty()) return
        repo.settings.update { it.copy(bggUsername = user) }
        running = true
        status = context.getString(R.string.import_downloading)
        scope.launch {
            val url = "https://boardgamegeek.com/geekcollection.php?action=exportcsv&subtype=boardgame&username=${URLEncoder.encode(user, "UTF-8")}&all=1&exporttype=csv"
            val text = try {
                withContext(Dispatchers.IO) {
                    val req = okhttp3.Request.Builder().url(url)
                        .header("Cookie", CookieManager.getInstance().getCookie("https://boardgamegeek.com") ?: "")
                        .header("User-Agent", webView?.settings?.userAgentString ?: "Mozilla/5.0")
                        .build()
                    repo.http.newCall(req).execute().use { it.body?.string() ?: "" }
                }
            } catch (e: Exception) {
                status = context.errorText(e); running = false; return@launch
            }
            if (BggCsv.looksLikeExport(text)) runImport(text)
            else {
                running = false
                status = context.getString(R.string.import_need_login)
            }
        }
    }

    val picker = rememberDocumentPicker(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/octet-stream")) { uri ->
        scope.launch {
            val text = withContext(Dispatchers.IO) { runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull() }
            if (text != null) runImport(text) else status = context.getString(R.string.err_generic)
        }
    }

    BackHandler(enabled = webView?.canGoBack() == true) { webView?.goBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = glassTopBarColors(),
                title = { Text(stringResource(R.string.import_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            GlassCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.import_explain), style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        username, { username = it }, label = { Text(stringResource(R.string.settings_bgg_user)) },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(onlyOwned, { onlyOwned = it }); Text(stringResource(R.string.import_only_owned), style = MaterialTheme.typography.bodySmall)
                        Checkbox(expansions, { expansions = it }); Text(stringResource(R.string.import_expansions), style = MaterialTheme.typography.bodySmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = !running && username.isNotBlank(), onClick = ::downloadExport) { Text(stringResource(R.string.import_start)) }
                        OutlinedButton(enabled = !running, onClick = picker) { Text(stringResource(R.string.import_csv_file)) }
                    }
                    if (running) LinearProgressIndicator(Modifier.fillMaxWidth())
                    status?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
                    enrich?.let { (d, t) -> Text(stringResource(R.string.import_enriching, d, t), style = MaterialTheme.typography.labelSmall) }
                }
            }
            Text(
                stringResource(R.string.import_login_hint), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webViewClient = WebViewClient()
                        // if the user exports manually on the website, take the downloaded CSV
                        setDownloadListener { url, _, _, _, _ ->
                            if ("export" in url || url.endsWith(".csv") || "csv" in url) {
                                scope.launch {
                                    val text = withContext(Dispatchers.IO) {
                                        runCatching {
                                            repo.http.newCall(
                                                okhttp3.Request.Builder().url(url)
                                                    .header("Cookie", CookieManager.getInstance().getCookie(url) ?: "").build()
                                            ).execute().use { it.body?.string() ?: "" }
                                        }.getOrDefault("")
                                    }
                                    if (BggCsv.looksLikeExport(text)) runImport(text) else status = context.getString(R.string.import_need_login)
                                }
                            }
                        }
                        webView = this
                        loadUrl("https://boardgamegeek.com/login")
                    }
                },
                onRelease = { it.destroy() }
            )
        }
    }
}
