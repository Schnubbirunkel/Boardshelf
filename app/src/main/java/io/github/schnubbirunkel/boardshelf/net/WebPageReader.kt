package io.github.schnubbirunkel.boardshelf.net

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import kotlin.coroutines.resume

/**
 * Opens a page in an invisible WebView – a real browser, so sites with bot protection
 * (e.g. BoardGameGeek) work like in the user's browser – and reads the pictures the page
 * declares (og:image, twitter:image). One page at a time.
 */
class WebPageReader(private val context: Context) {

    private val js = """
        (function() {
          var out = [];
          document.querySelectorAll('meta[property="og:image"],meta[property="og:image:secure_url"],meta[name="twitter:image"],link[rel="image_src"]')
            .forEach(function(m){ var v = m.getAttribute('content') || m.getAttribute('href'); if (v) out.push(v); });
          // pictures rendered by the page itself (BGG builds its page with JavaScript)
          var good = ['__opengraph', '__imagepage', '__itemrep', '__original', '__d', '__md'];
          var imgs = Array.prototype.slice.call(document.querySelectorAll('img'))
            .map(function(i){ return i.currentSrc || i.src || i.getAttribute('data-src') || ''; })
            .filter(function(u){ return u.indexOf('geekdo-images.com') >= 0; });
          imgs.sort(function(a, b) {
            function rank(u){ for (var k = 0; k < good.length; k++) if (u.indexOf(good[k]) >= 0) return k; return 99; }
            return rank(a) - rank(b);
          });
          imgs.forEach(function(u){ if (out.indexOf(u) < 0 && u.indexOf('__micro') < 0 && u.indexOf('__thumb') < 0 && u.indexOf('__avatar') < 0) out.push(u); });
          return JSON.stringify(out);
        })()
    """.trimIndent()

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun images(url: String, timeoutMs: Long = 25_000): List<String> = withContext(Dispatchers.Main) {
        var webView: WebView? = null
        try {
            withTimeoutOrNull(timeoutMs) {
                val wv = WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadsImagesAutomatically = false
                }
                webView = wv
                suspendCancellableCoroutine<Unit> { cont ->
                    wv.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, u: String) {
                            if (cont.isActive) cont.resume(Unit)
                        }
                    }
                    wv.loadUrl(url)
                }
                // bot checks redirect once more after the first load; poll the meta tags a few times
                var found = emptyList<String>()
                repeat(8) {
                    found = read(wv)
                    if (found.isNotEmpty()) return@withTimeoutOrNull found
                    delay(1500)
                }
                found
            }.orEmpty()
        } finally {
            webView?.apply { stopLoading(); destroy() }
        }
    }

    private suspend fun read(wv: WebView): List<String> = suspendCancellableCoroutine { cont ->
        wv.evaluateJavascript(js) { raw ->
            val list = runCatching {
                // evaluateJavascript returns a JSON-encoded string
                val s = org.json.JSONTokener(raw).nextValue() as? String ?: "[]"
                val a = JSONArray(s)
                (0 until a.length()).map { a.getString(it) }
            }.getOrDefault(emptyList())
            if (cont.isActive) cont.resume(list.filter { it.startsWith("http") })
        }
    }
}
