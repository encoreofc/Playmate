package com.playmate.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

/**
 * YouTube Music's own web API (the one music.youtube.com uses), called with the same signed-in
 * session as the rest of Playmate. Unofficial: YouTube can change it at any time. The values
 * below are only the fallback; on first use the site's own page is read and its current client
 * version, visitor id and key replace them.
 */
object MusicClient {

    private const val ORIGIN = "https://music.youtube.com"
    private const val FALLBACK_KEY = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30"
    private const val FALLBACK_VERSION = "1.20251020.03.00"
    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/131.0.0.0 Safari/537.36"
    private val JSON_TYPE = "application/json".toMediaType()

    private class Config(
        val cookie: String,
        val version: String?,
        val visitor: String?,
        val key: String?
    )

    @Volatile
    private var config: Config? = null

    private val version: String get() = config?.version ?: FALLBACK_VERSION
    private val visitor: String? get() = config?.visitor
    private val apiKey: String get() = config?.key ?: FALLBACK_KEY

    private fun context(): JSONObject {
        val client = JSONObject()
            .put("clientName", "WEB_REMIX")
            .put("clientVersion", version)
            .put("hl", "en")
            .put("gl", "BD")
            .put("userAgent", UA)
            .put("platform", "DESKTOP")
        visitor?.let { client.put("visitorData", it) }
        return JSONObject()
            .put("client", client)
            .put("user", JSONObject().put("lockedSafetyMode", false))
    }

    /** The saved cookies, plus the consent cookie that keeps Google's consent page out of the way. */
    private fun cookieHeader(): String {
        val saved = Session.cookie
        return when {
            saved.isBlank() -> "SOCS=CAI"
            saved.contains("SOCS=") -> saved
            else -> "$saved; SOCS=CAI"
        }
    }

    // ---------------------------------------------------------------- the site's own page

    @Synchronized
    private fun ensureConfig() {
        val current = config
        if (current != null && current.cookie == Session.cookie) return
        attempt { readPage() }
        if (config?.cookie != Session.cookie) {
            config = Config(Session.cookie, null, null, null)
        }
    }

    private fun readPage() {
        val request = Request.Builder()
            .url("$ORIGIN/")
            .header("User-Agent", UA)
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Cookie", cookieHeader())
            .build()
        val html = PmDownloader.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("music page answered ${response.code}")
            response.body?.string() ?: throw IOException("empty music page")
        }
        config = Config(
            cookie = Session.cookie,
            version = grab(html, "INNERTUBE_CLIENT_VERSION"),
            visitor = grab(html, "VISITOR_DATA"),
            key = grab(html, "INNERTUBE_API_KEY")
        )
    }

    private fun grab(html: String, key: String): String? =
        Regex("\"$key\":\\s*\"([^\"]+)\"").find(html)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }

    // ---------------------------------------------------------------- calls

    /** One signed call. [extra] is appended to the address (continuations travel there). */
    private fun post(endpoint: String, body: JSONObject, extra: String = ""): JSONObject {
        ensureConfig()
        body.put("context", context())
        val builder = Request.Builder()
            .url("$ORIGIN/youtubei/v1/$endpoint?prettyPrint=false&alt=json&key=$apiKey$extra")
            .post(body.toString().toRequestBody(JSON_TYPE))
            .header("User-Agent", UA)
            .header("Origin", ORIGIN)
            .header("X-Origin", ORIGIN)
            .header("Referer", "$ORIGIN/")
            .header("X-Youtube-Client-Name", "67")
            .header("X-Youtube-Client-Version", version)
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Cookie", cookieHeader())
        visitor?.let { builder.header("X-Goog-Visitor-Id", it) }
        if (Session.cookie.isNotBlank()) {
            builder.header("Authorization", Session.authHeader(ORIGIN))
            builder.header("X-Goog-AuthUser", "0")
        }
        PmDownloader.client.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string()
            if (!response.isSuccessful) {
                val detail = runCatching {
                    JSONObject(text.orEmpty()).optJSONObject("error")?.optString("message")
                }.getOrNull()?.takeIf { it.isNotBlank() }?.take(140)
                throw IOException("YouTube Music answered ${response.code}" + (detail?.let { ": $it" } ?: ""))
            }
            if (text.isNullOrBlank()) throw IOException("Empty answer from YouTube Music")
            return JSONObject(text)
        }
    }

    /** The Music home. [params] picks a mood chip (Workout, Relax...); null is the plain home. */
    suspend fun home(params: String? = null): MusicHome = withContext(Dispatchers.IO) {
        val body = JSONObject().put("browseId", "FEmusic_home")
        if (!params.isNullOrBlank()) body.put("params", params)
        MusicParser.parseHome(post("browse", body))
    }

    /** The next shelves of the home (YouTube Music sends a few at a time). */
    suspend fun more(token: String): MusicHome = withContext(Dispatchers.IO) {
        MusicParser.parseHome(post("browse", JSONObject(), "&ctoken=$token&continuation=$token"))
    }
}
