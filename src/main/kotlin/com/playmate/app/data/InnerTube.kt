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
        return send(endpoint, body, UA, "1", clientVersion)
    }

    /** One signed API call; [clientId] / [version] say which YouTube client this pretends to be. */
    private fun send(endpoint: String, body: JSONObject, ua: String, clientId: String, version: String): JSONObject {
        val builder = Request.Builder()
            .url("$ORIGIN/youtubei/v1/$endpoint?prettyPrint=false&key=$apiKey")
            .post(body.toString().toRequestBody(JSON_TYPE))
            .header("User-Agent", ua)
            .header("Origin", ORIGIN)
            .header("X-Origin", ORIGIN)
            .header("X-Youtube-Client-Name", clientId)
            .header("X-Youtube-Client-Version", version)
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
    private fun embeddedData(html: String): JSONObject? = embedded(
        html, listOf("var ytInitialData = ", "window[\"ytInitialData\"] = ", "ytInitialData = ")
    )

    private fun embedded(html: String, markers: List<String>): JSONObject? {
        for (marker in markers) {
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


    // ---------------------------------------------------------------- watch history

    private const val MOBILE_ORIGIN = "https://m.youtube.com"
    private const val MOBILE_UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/131.0.0.0 Mobile Safari/537.36"
    private const val TV_UA = "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/Version"
    private const val TV_VERSION = "7.20250219.14.00"

    /** The watch-report links YouTube hands out for one video, and where they came from. */
    class WatchTracking(
        val source: String,
        val playbackUrl: String?,
        val watchtimeUrl: String?,
        val lengthSec: Double?
    )

    private fun trackingOf(player: JSONObject?, source: String): WatchTracking? {
        val tracking = player?.optJSONObject("playbackTracking") ?: return null
        val start = tracking.optJSONObject("videostatsPlaybackUrl")
            ?.optString("baseUrl")?.takeIf { it.isNotBlank() }
        val watch = tracking.optJSONObject("videostatsWatchtimeUrl")
            ?.optString("baseUrl")?.takeIf { it.isNotBlank() }
        if (start == null && watch == null) return null
        val length = player.optJSONObject("videoDetails")?.optString("lengthSeconds")?.toDoubleOrNull()
        return WatchTracking(source, start, watch, length)
    }

    /** The player data embedded in the site's own watch page (what a browser tab receives). */
    private fun watchPagePlayer(origin: String, ua: String, videoId: String): JSONObject {
        val builder = Request.Builder()
            .url("$origin/watch?v=$videoId&bpctr=9999999999&has_verified=1")
            .header("User-Agent", ua)
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Accept-Language", "en-US,en;q=0.9")
        if (Session.cookie.isNotBlank()) builder.header("Cookie", Session.cookie)
        val html = PmDownloader.client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("page answered ${response.code}")
            response.body?.string() ?: throw IOException("empty page")
        }
        return embedded(html, listOf("var ytInitialPlayerResponse = ", "ytInitialPlayerResponse = "))
            ?: throw IOException("page had no player data")
    }

    private fun playerBody(videoId: String) =
        JSONObject().put("videoId", videoId).put("contentCheckOk", true).put("racyCheckOk", true)

    /** The same player call the YouTube TV app makes (it accepts the signed-in cookies). */
    private fun tvPlayer(videoId: String): JSONObject {
        ensureConfig()
        val client = JSONObject()
            .put("clientName", "TVHTML5")
            .put("clientVersion", TV_VERSION)
            .put("hl", "en")
            .put("gl", "BD")
            .put("userAgent", TV_UA)
        visitorData?.let { client.put("visitorData", it) }
        val body = playerBody(videoId).put(
            "context",
            JSONObject().put("client", client).put("user", JSONObject().put("lockedSafetyMode", false))
        )
        return send("player", body, TV_UA, "7", TV_VERSION)
    }

    private fun webPlayer(videoId: String): JSONObject = post("player", playerBody(videoId))

    /**
     * Finds the watch-report links for [videoId]. The site's own watch page goes first (it needs
     * no extra proof from the app); the API clients follow. The first source that has the links wins.
     * If none does, the error lists what each one said, so the cause is visible on screen.
     */
    private fun findTracking(videoId: String): WatchTracking {
        val tried = mutableListOf<String>()
        val sources: List<Pair<String, () -> JSONObject>> = listOf(
            "watch page" to { watchPagePlayer(ORIGIN, UA, videoId) },
            "mobile page" to { watchPagePlayer(MOBILE_ORIGIN, MOBILE_UA, videoId) },
            "tv api" to { tvPlayer(videoId) },
            "web api" to { webPlayer(videoId) }
        )
        for ((name, fetch) in sources) {
            val answer = attempt { fetch() }
            val player = answer.getOrNull()
            if (player == null) {
                tried += "$name: ${answer.exceptionOrNull()?.message ?: "failed"}"
                continue
            }
            trackingOf(player, name)?.let { return it }
            val status = player.optJSONObject("playabilityStatus")?.optString("status").orEmpty()
            tried += "$name: ${status.ifBlank { "no status" }}"
        }
        throw IOException("no watch-report link (${tried.joinToString("; ")})")
    }

    /**
     * Starts reporting a watch to YouTube so it lands in the account's history and feeds the
     * recommendations, the way the site's own player does: one playback-start report now, then
     * watch-time reports as the video really plays (see [WatchSession.progress]).
     */
    suspend fun beginWatch(videoId: String): WatchSession = withContext(Dispatchers.IO) {
        val tracking = findTracking(videoId)
        WatchSession(tracking, randomCpn()).also { it.sendStart() }
    }

    /** One video being watched. Reports use one playback id ([cpn]), as the site's player does. */
    class WatchSession internal constructor(
        private val tracking: WatchTracking,
        private val cpn: String
    ) {
        val source: String get() = tracking.source

        private val startedAt = System.nanoTime()
        private var lastWall = startedAt
        private var lastEnd = 0.0

        private val length: Double?
            get() = tracking.lengthSec
                ?: (tracking.watchtimeUrl ?: tracking.playbackUrl)?.toHttpUrlOrNull()
                    ?.queryParameter("len")?.toDoubleOrNull()

        internal fun sendStart() {
            val base = tracking.playbackUrl ?: return
            ping(base, position = 0.0, from = null, playing = true, final = false)
        }

        /**
         * Reports how far the video has really been watched. Call every few seconds while it
         * plays, and once more with [final] when it is closed. A jump in position (a seek) is
         * not counted as watched time.
         */
        suspend fun progress(position: Double, playing: Boolean, final: Boolean) =
            withContext(Dispatchers.IO) {
                val now = System.nanoTime()
                val wall = (now - lastWall) / 1e9
                val limit = length
                val pos = if (limit != null && limit > 0) position.coerceIn(0.0, limit) else position.coerceAtLeast(0.0)
                val moved = pos - lastEnd
                val sought = moved < 0 || moved > wall * 2.5 + 2.0
                val from = if (sought) pos else lastEnd
                lastEnd = pos
                lastWall = now
                if (!final && (sought || pos <= from)) return@withContext
                val base = tracking.watchtimeUrl ?: return@withContext
                ping(base, position = pos, from = from, playing = playing, final = final)
            }

        private fun ping(base: String, position: Double, from: Double?, playing: Boolean, final: Boolean) {
            val url = base.toHttpUrlOrNull() ?: throw IOException("unreadable watch-report link")
            val builder = url.newBuilder()
                .setQueryParameter("ver", "2")
                .setQueryParameter("cpn", cpn)
                .setQueryParameter("cmt", num(position))
                .setQueryParameter("state", if (playing) "playing" else "paused")
                .setQueryParameter("rt", num((System.nanoTime() - startedAt) / 1e9))
                .setQueryParameter("volume", "100")
                .setQueryParameter("muted", "0")
            if (url.queryParameter("el") == null) builder.setQueryParameter("el", "detailpage")
            if (url.queryParameter("c") == null) builder.setQueryParameter("c", "WEB")
            if (url.queryParameter("len") == null) length?.let { builder.setQueryParameter("len", num(it)) }
            if (from != null) {
                builder.setQueryParameter("st", num(from)).setQueryParameter("et", num(position))
            }
            if (final) builder.setQueryParameter("final", "1")

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

        private fun num(v: Double) = String.format(java.util.Locale.US, "%.3f", v)
    }

    private fun randomCpn(): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        return (1..16).map { alphabet.random() }.joinToString("")
    }
}
