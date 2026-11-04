package com.playmate.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

data class Account(val name: String, val handle: String?, val avatar: String?)

/**
 * YouTube's own web API (the one youtube.com uses), called with the signed-in session.
 * This is unofficial: YouTube can change it at any time. The values below are only the
 * fallback: on first use the site's own page is read and its current client version,
 * visitor id and key replace them.
 */
object InnerTube {

    private const val ORIGIN = "https://www.youtube.com"
    private const val KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"
    private const val CLIENT_VERSION = "2.20251020.01.00"
    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/131.0.0.0 Safari/537.36"
    private val JSON_TYPE = "application/json".toMediaType()

    /** What the site's own page said about the current session. */
    private class WebConfig(
        val cookie: String,
        val clientVersion: String?,
        val visitorData: String?,
        val apiKey: String?,
        val loggedIn: Boolean?
    )

    @Volatile
    private var web: WebConfig? = null

    private val clientVersion: String get() = web?.clientVersion ?: CLIENT_VERSION
    private val visitorData: String? get() = web?.visitorData
    private val apiKey: String get() = web?.apiKey ?: KEY

    /** Whether the site itself recognises the saved sign-in (null: not known yet). */
    val sessionRecognised: Boolean? get() = web?.loggedIn

    private fun context(): JSONObject {
        val client = JSONObject()
            .put("clientName", "WEB")
            .put("clientVersion", clientVersion)
            .put("hl", "en")
            .put("gl", "BD")
            .put("userAgent", UA)
            .put("platform", "DESKTOP")
        visitorData?.let { client.put("visitorData", it) }
        return JSONObject()
            .put("client", client)
            .put("user", JSONObject().put("lockedSafetyMode", false))
    }

    private fun signed(builder: Request.Builder): Request.Builder {
        if (Session.cookie.isNotBlank()) {
            builder.header("Cookie", Session.cookie)
            builder.header("Authorization", Session.authHeader(ORIGIN))
            builder.header("X-Goog-AuthUser", "0")
        }
        return builder
    }

    private fun post(endpoint: String, body: JSONObject): JSONObject {
        ensureConfig()
        body.put("context", context())
        val builder = Request.Builder()
            .url("$ORIGIN/youtubei/v1/$endpoint?prettyPrint=false&key=$apiKey")
            .post(body.toString().toRequestBody(JSON_TYPE))
            .header("User-Agent", UA)
            .header("Origin", ORIGIN)
            .header("X-Origin", ORIGIN)
            .header("X-Youtube-Client-Name", "1")
            .header("X-Youtube-Client-Version", clientVersion)
            .header("Accept-Language", "en-US,en;q=0.9")
        visitorData?.let { builder.header("X-Goog-Visitor-Id", it) }
        val request = signed(builder).build()
        PmDownloader.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("YouTube answered ${response.code}")
            val text = response.body?.string() ?: throw IOException("Empty answer from YouTube")
            return JSONObject(text)
        }
    }

    // ---------------------------------------------------------------- the site's own page

    /**
     * Reads the site's page once per sign-in, so API calls carry the same client version and
     * visitor id the site itself uses right now. Best effort: on any failure the constants
     * above stay in use, and the attempt is not repeated for the same sign-in.
     */
    @Synchronized
    private fun ensureConfig() {
        val current = web
        if (current != null && current.cookie == Session.cookie) return
        attempt { readPage("/") }
        if (web?.cookie != Session.cookie) {
            web = WebConfig(Session.cookie, null, null, null, null)
        }
    }

    /** The site's page for [path], fetched with the signed-in cookies; returns its embedded feed data. */
    private fun readPage(path: String): JSONObject {
        val builder = Request.Builder()
            .url("$ORIGIN$path")
            .header("User-Agent", UA)
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Accept-Language", "en-US,en;q=0.9")
        val cookie = Session.cookie
        if (cookie.isNotBlank()) builder.header("Cookie", cookie)

        val html = PmDownloader.client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("page answered ${response.code}")
            response.body?.string() ?: throw IOException("empty page")
        }

        web = WebConfig(
            cookie = cookie,
            clientVersion = grab(html, "INNERTUBE_CLIENT_VERSION"),
            visitorData = grab(html, "VISITOR_DATA"),
            apiKey = grab(html, "INNERTUBE_API_KEY"),
            loggedIn = Regex("\"LOGGED_IN\":\\s*(true|false)").find(html)?.groupValues?.get(1)
                ?.toBooleanStrictOrNull()
        )
        return embeddedData(html) ?: throw IOException("the page carried no feed data")
    }

    suspend fun page(path: String): JSONObject = withContext(Dispatchers.IO) { readPage(path) }

    private fun grab(html: String, key: String): String? =
        Regex("\"$key\":\\s*\"([^\"]+)\"").find(html)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }

    /** The JSON object the page embeds as its initial data. */
    private fun embeddedData(html: String): JSONObject? {
        for (marker in listOf("var ytInitialData = ", "window[\"ytInitialData\"] = ", "ytInitialData = ")) {
            val at = html.indexOf(marker)
            if (at < 0) continue
            val start = at + marker.length
            if (start >= html.length || html[start] != '{') continue
            val json = balancedObject(html, start) ?: continue
            return runCatching { JSONObject(json) }.getOrNull() ?: continue
        }
        return null
    }

    /** The substring from [start] (an opening brace) to its matching closing brace. */
    private fun balancedObject(s: String, start: Int): String? {
        var depth = 0
        var inString = false
        var escaped = false
        var i = start
        while (i < s.length) {
            val c = s[i]
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
            } else {
                when (c) {
                    '"' -> inString = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) return s.substring(start, i + 1)
                    }
                }
            }
            i++
        }
        return null
    }

    // ---------------------------------------------------------------- calls

    suspend fun browse(browseId: String): JSONObject = withContext(Dispatchers.IO) {
        post("browse", JSONObject().put("browseId", browseId))
    }

    suspend fun continuation(token: String): JSONObject = withContext(Dispatchers.IO) {
        post("browse", JSONObject().put("continuation", token))
    }

    suspend fun account(): Account? = withContext(Dispatchers.IO) {
        val root = post("account/account_menu", JSONObject())
        val header = findObject(root, "activeAccountHeaderRenderer") ?: return@withContext null
        val name = header.optJSONObject("accountName")?.optString("simpleText").orEmpty()
        if (name.isBlank()) return@withContext null
        val photos: JSONArray? = header.optJSONObject("accountPhoto")?.optJSONArray("thumbnails")
        val avatar = photos?.optJSONObject(photos.length() - 1)?.optString("url")?.takeIf { it.isNotBlank() }
        val handle = header.optJSONObject("channelHandle")?.optString("simpleText")?.takeIf { it.isNotBlank() }
        Account(name, handle, avatar)
    }

    /**
     * Tells YouTube this video was watched, so it lands in the account's watch history
     * and feeds the recommendations. Two reports are sent, the way the site's own player
     * does: the playback start and the watch-time report (the one that creates the history
     * entry). Throws when YouTube gives no report link or refuses it, so the caller can say why.
     */
    suspend fun registerPlayback(videoId: String) = withContext(Dispatchers.IO) {
        val player = post(
            "player",
            JSONObject().put("videoId", videoId).put("contentCheckOk", true).put("racyCheckOk", true)
        )
        val tracking = player.optJSONObject("playbackTracking")
        val start = tracking?.optJSONObject("videostatsPlaybackUrl")
            ?.optString("baseUrl")?.takeIf { it.isNotBlank() }
        val watchTime = tracking?.optJSONObject("videostatsWatchtimeUrl")
            ?.optString("baseUrl")?.takeIf { it.isNotBlank() }

        if (start == null && watchTime == null) {
            val status = player.optJSONObject("playabilityStatus")?.optString("status").orEmpty()
            throw IOException("no watch-report link (player status ${status.ifBlank { "unknown" }})")
        }
        start?.let { report(it, withWatchTime = false) }
        watchTime?.let { report(it, withWatchTime = true) }
    }

    /** A few seconds in: what the site's player reports shortly after a video starts. */
    private const val FIRST_REPORT_SECONDS = 3.0

    private fun report(base: String, withWatchTime: Boolean) {
        val url = base.toHttpUrlOrNull() ?: throw IOException("unreadable watch-report link")
        val length = url.queryParameter("len")?.toDoubleOrNull()
        val seconds = if (length != null && length > 1.0) {
            minOf(FIRST_REPORT_SECONDS, length - 0.5)
        } else {
            FIRST_REPORT_SECONDS
        }
        val position = seconds.toString()

        val builder = url.newBuilder()
            .setQueryParameter("ver", "2")
            .setQueryParameter("cpn", randomCpn())
            .setQueryParameter("cmt", position)
            .setQueryParameter("el", "detailpage")
        if (url.queryParameter("c") == null) builder.setQueryParameter("c", "WEB")
        if (withWatchTime) {
            builder.setQueryParameter("st", "0").setQueryParameter("et", position)
        }

        val request = signed(
            Request.Builder()
                .url(builder.build())
                .header("User-Agent", UA)
                .header("Origin", ORIGIN)
                .header("X-Origin", ORIGIN)
        ).build()
        PmDownloader.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("watch report answered ${response.code}")
        }
    }

    private fun randomCpn(): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        return (1..16).map { alphabet.random() }.joinToString("")
    }
}
