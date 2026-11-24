package io.github.schnubbirunkel.boardshelf.net

import android.graphics.Bitmap
import android.util.Base64
import io.github.schnubbirunkel.boardshelf.data.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

enum class AiProvider(val defaultModel: String) {
    NONE(""),
    CLAUDE("claude-haiku-5-5"),
    GEMINI("gemini-2.5-flash"),
    OPENAI("gpt-4.1-mini"),
    /** Any OpenAI-compatible server: Ollama, LM Studio, vLLM, LocalAI … */
    CUSTOM("qwen2.5vl:7b"),
}

/** The AI's opinion about one picture. [box] is the box front in relative coordinates (l, t, r, b). */
data class CoverVerdict(val index: Int, val isFrontCover: Boolean, val sameGame: Boolean, val confidence: Float, val box: FloatArray?)

data class BoxIdentification(val title: String, val publisher: String?, val box: FloatArray?)

class AiException(message: String) : IOException(message)

/**
 * Optional vision AI (Claude, Gemini, ChatGPT or a self-hosted OpenAI-compatible model) that
 * judges cover candidates and recognises game boxes on photos. Images are sent downscaled.
 */
class AiVision(baseHttp: OkHttpClient, private val settings: () -> AppSettings) {

    private val http = baseHttp.newBuilder().readTimeout(180, TimeUnit.SECONDS).callTimeout(240, TimeUnit.SECONDS).build()
    private val jsonType = "application/json".toMediaType()

    val enabled: Boolean
        get() = settings().let { s ->
            when (s.aiProvider) {
                AiProvider.NONE -> false
                AiProvider.CUSTOM -> s.aiBaseUrl.isNotBlank()
                else -> s.aiKey.isNotBlank()
            }
        }

    /** Checks up to ~6 candidate pictures in one request. */
    suspend fun judgeCovers(game: String, publisher: String?, year: Int?, images: List<Bitmap>): List<CoverVerdict> {
        if (images.isEmpty()) return emptyList()
        val desc = listOfNotNull(game, publisher?.let { "publisher: $it" }, year?.let { "year: $it" }).joinToString(", ")
        val prompt = """
            You are checking candidate cover pictures for the board game ($desc).
            There are ${images.size} images, numbered 0 to ${images.size - 1} in the order given.
            For each image decide:
            - is_front_cover: true only if the image shows the FRONT of the game box (not the back side, not a game in play,
              not components, not a table, not an advertisement collage, not a person).
            - same_game: true only if it is this game itself – not an expansion, sequel, spin-off, accessory, sleeves, insert or a different game.
            - confidence: 0.0 to 1.0
            - box: the box front's bounding rectangle as [left, top, right, bottom], each 0.0–1.0 relative to the image size, or null.
            Answer with JSON only: {"results":[{"index":0,"is_front_cover":true,"same_game":true,"confidence":0.9,"box":[0.1,0.05,0.9,0.95]}]}
        """.trimIndent()
        val json = ask(prompt, images)
        val arr = json.optJSONArray("results") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            CoverVerdict(
                index = o.optInt("index", i),
                isFrontCover = o.optBoolean("is_front_cover"),
                sameGame = o.optBoolean("same_game"),
                confidence = o.optDouble("confidence", 0.0).toFloat(),
                box = rect(o.optJSONArray("box")),
            )
        }
    }

    /** Recognises the game on a photo of a box (front or spine). */
    suspend fun identifyBox(photo: Bitmap): BoxIdentification? {
        val prompt = """
            The photo shows a board game box (front, spine or an angled view). Read the printed title.
            Answer with JSON only: {"title":"exact game title as printed, without publisher","publisher":"publisher or null",
            "box":[left, top, right, bottom] of the box FRONT as fractions 0.0–1.0 of the image, or null if the front is not fully visible}
            If no board game box is visible answer {"title":null}.
        """.trimIndent()
        val json = ask(prompt, listOf(photo))
        val title = json.optString("title").takeIf { it.isNotBlank() && it != "null" } ?: return null
        return BoxIdentification(title, json.optString("publisher").takeIf { it.isNotBlank() && it != "null" }, rect(json.optJSONArray("box")))
    }

    /** Small end-to-end test used by the settings screen. Returns the model's answer. */
    suspend fun test(): String {
        val bmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFFE53935.toInt()) }
        val json = ask("What is the main colour of this image? Answer with JSON only: {\"colour\":\"...\"}", listOf(bmp))
        return json.optString("colour").ifBlank { json.toString() }
    }

    private fun rect(a: JSONArray?): FloatArray? {
        if (a == null || a.length() != 4) return null
        val r = FloatArray(4) { a.optDouble(it, -1.0).toFloat() }
        // accept 0..1 and 0..1000 style answers
        val scaled = if (r.any { it > 1.5f }) FloatArray(4) { r[it] / 1000f } else r
        if (scaled.any { it < -0.01f || it > 1.01f }) return null
        val (l, t, rr, b) = scaled.toList()
        if (rr - l < 0.1f || b - t < 0.1f) return null
        return floatArrayOf(l.coerceIn(0f, 1f), t.coerceIn(0f, 1f), rr.coerceIn(0f, 1f), b.coerceIn(0f, 1f))
    }

    private fun b64(bmp: Bitmap): String {
        val maxDim = 640
        val f = minOf(1f, maxDim.toFloat() / maxOf(bmp.width, bmp.height))
        val scaled = if (f < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * f).toInt().coerceAtLeast(1), (bmp.height * f).toInt().coerceAtLeast(1), true) else bmp
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 82, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private suspend fun ask(prompt: String, images: List<Bitmap>): JSONObject = withContext(Dispatchers.IO) {
        val s = settings()
        val model = s.aiModel.ifBlank { s.aiProvider.defaultModel }
        val data = images.map { b64(it) }
        val text = when (s.aiProvider) {
            AiProvider.NONE -> throw AiException("AI disabled")
            AiProvider.CLAUDE -> {
                val content = JSONArray()
                data.forEachIndexed { i, d ->
                    content.put(JSONObject().put("type", "text").put("text", "Image $i:"))
                    content.put(JSONObject().put("type", "image").put("source", JSONObject().put("type", "base64").put("media_type", "image/jpeg").put("data", d)))
                }
                content.put(JSONObject().put("type", "text").put("text", prompt))
                val body = JSONObject().put("model", model).put("max_tokens", 1500)
                    .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
                val res = post("https://api.anthropic.com/v1/messages", body, mapOf("x-api-key" to s.aiKey, "anthropic-version" to "2023-06-01"))
                val parts = res.optJSONArray("content") ?: JSONArray()
                (0 until parts.length()).joinToString("") { parts.getJSONObject(it).optString("text") }
            }
            AiProvider.GEMINI -> {
                val parts = JSONArray()
                data.forEachIndexed { i, d ->
                    parts.put(JSONObject().put("text", "Image $i:"))
                    parts.put(JSONObject().put("inline_data", JSONObject().put("mime_type", "image/jpeg").put("data", d)))
                }
                parts.put(JSONObject().put("text", prompt))
                val body = JSONObject()
                    .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", parts)))
                    .put("generationConfig", JSONObject().put("responseMimeType", "application/json"))
                val res = post("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent", body, mapOf("x-goog-api-key" to s.aiKey))
                val p = res.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts") ?: JSONArray()
                (0 until p.length()).joinToString("") { p.getJSONObject(it).optString("text") }
            }
            AiProvider.OPENAI, AiProvider.CUSTOM -> {
                val content = JSONArray()
                data.forEachIndexed { i, d ->
                    content.put(JSONObject().put("type", "text").put("text", "Image $i:"))
                    content.put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$d")))
                }
                content.put(JSONObject().put("type", "text").put("text", prompt))
                val body = JSONObject().put("model", model)
                    .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
                    .put("temperature", 0)
                val url = if (s.aiProvider == AiProvider.OPENAI) "https://api.openai.com/v1/chat/completions"
                else s.aiBaseUrl.trimEnd('/').let { if (it.endsWith("/chat/completions")) it else "$it/chat/completions" }
                val headers = if (s.aiKey.isNotBlank()) mapOf("Authorization" to "Bearer ${s.aiKey}") else emptyMap()
                val res = post(url, body, headers)
                res.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content") ?: ""
            }
        }
        extractJson(text)
    }

    private fun post(url: String, body: JSONObject, headers: Map<String, String>): JSONObject {
        val b = Request.Builder().url(url).post(body.toString().toRequestBody(jsonType))
        headers.forEach { (k, v) -> b.header(k, v) }
        http.newCall(b.build()).execute().use { r ->
            val text = r.body?.string() ?: ""
            if (!r.isSuccessful) {
                val msg = runCatching { JSONObject(text).let { it.optJSONObject("error")?.optString("message") ?: it.optString("error") } }.getOrNull()
                throw AiException("HTTP ${r.code}: ${msg?.ifBlank { null } ?: text.take(200)}")
            }
            return JSONObject(text)
        }
    }

    /** Models sometimes wrap JSON in prose or ``` fences. */
    private fun extractJson(text: String): JSONObject {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) throw AiException("No JSON in answer: ${text.take(160)}")
        return JSONObject(text.substring(start, end + 1))
    }
}
