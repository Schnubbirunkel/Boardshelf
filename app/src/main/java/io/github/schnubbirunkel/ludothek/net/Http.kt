package io.github.schnubbirunkel.ludothek.net

import android.text.Html
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.IOException
import java.io.StringReader
import java.util.concurrent.TimeUnit

const val USER_AGENT = "Ludothek/1.0 (Android board game shelf; https://github.com/Schnubbirunkel/ludothek)"

fun createHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(20, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .followRedirects(true)
    .addInterceptor { chain ->
        val req = chain.request()
        if (req.header("User-Agent") == null) {
            chain.proceed(req.newBuilder().header("User-Agent", USER_AGENT).build())
        } else chain.proceed(req)
    }
    .build()

suspend fun OkHttpClient.getString(url: String, headers: Map<String, String> = emptyMap()): String =
    withContext(Dispatchers.IO) {
        val b = Request.Builder().url(url)
        headers.forEach { (k, v) -> b.header(k, v) }
        newCall(b.build()).execute().use { r ->
            if (!r.isSuccessful) throw HttpException(r.code, url)
            r.body?.string() ?: ""
        }
    }

/** Downloads [url] to [target]. Returns the content type reported by the server. */
suspend fun OkHttpClient.download(
    url: String,
    target: File,
    headers: Map<String, String> = emptyMap(),
): String? = withContext(Dispatchers.IO) {
    val b = Request.Builder().url(url)
    headers.forEach { (k, v) -> b.header(k, v) }
    newCall(b.build()).execute().use { r ->
        if (!r.isSuccessful) throw HttpException(r.code, url)
        val body = r.body ?: throw IOException("Empty body")
        target.parentFile?.mkdirs()
        target.outputStream().use { out -> body.byteStream().copyTo(out) }
        r.header("Content-Type")
    }
}

class HttpException(val code: Int, url: String) : IOException("HTTP $code for $url")

/** A very small DOM, good enough for BGG's XML API. */
class XNode(val name: String, val attrs: Map<String, String>) {
    val children = mutableListOf<XNode>()
    val textBuilder = StringBuilder()
    val text: String get() = textBuilder.toString()

    fun child(n: String): XNode? = children.firstOrNull { it.name == n }
    fun all(n: String): List<XNode> = children.filter { it.name == n }
    fun attr(n: String): String? = attrs[n]
    fun value(childName: String): String? = child(childName)?.attr("value")
}

fun parseXml(xml: String): XNode {
    val factory = XmlPullParserFactory.newInstance()
    factory.isNamespaceAware = false
    val p = factory.newPullParser()
    try {
        p.setFeature("http://xmlpull.org/v1/doc/features.html#relaxed", true)
    } catch (_: Exception) {
    }
    p.setInput(StringReader(xml))
    val root = XNode("#root", emptyMap())
    val stack = ArrayDeque<XNode>().apply { addLast(root) }
    var ev = p.eventType
    while (ev != XmlPullParser.END_DOCUMENT) {
        when (ev) {
            XmlPullParser.START_TAG -> {
                val attrs = HashMap<String, String>()
                for (i in 0 until p.attributeCount) attrs[p.getAttributeName(i)] = p.getAttributeValue(i)
                val node = XNode(p.name, attrs)
                stack.last().children.add(node)
                stack.addLast(node)
            }
            XmlPullParser.END_TAG -> if (stack.size > 1) stack.removeLast()
            XmlPullParser.TEXT -> stack.last().textBuilder.append(p.text)
        }
        ev = p.next()
    }
    return root
}

/** BGG descriptions are HTML-escaped once more; turn them into plain text. */
fun htmlToText(s: String?): String? {
    if (s.isNullOrBlank()) return null
    val withBreaks = s.replace("\n", "<br>")
    return Html.fromHtml(withBreaks, Html.FROM_HTML_MODE_LEGACY).toString().trim()
}
