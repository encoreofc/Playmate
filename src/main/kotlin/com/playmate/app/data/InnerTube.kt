package com.playmate.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

data class Account(val name: String, val handle: String?, val avatar: String?)

/**
 * YouTube's own web API (the one youtube.com uses), called with the signed-in session.
 * This is unofficial: YouTube can change it at any time. If the feed stops loading,
 * CLIENT_VERSION below is the first thing to update.
 */
object InnerTube {

    private const val ORIGIN = "https://www.youtube.com"
    private const val KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"
    private const val CLIENT_VERSION = "2.20251020.01.00"
    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/131.0.0.0 Safari/537.36"
    private val JSON_TYPE = "application/json".toMediaType()

    private fun context(): JSONObject = JSONObject()
        .put(
            "client", JSONObject()
                .put("clientName", "WEB")
                .put("clientVersion", CLIENT_VERSION)
                .put("hl", "en")
                .put("gl", "BD")
                .put("userAgent", UA)
                .put("platform", "DESKTOP")
        )
        .put("user", JSONObject().put("lockedSafetyMode", false))

    private fun signed(builder: Request.Builder): Request.Builder {
        if (Session.cookie.isNotBlank()) {
            builder.header("Cookie", Session.cookie)
            builder.header("Authorization", Session.authHeader(ORIGIN))
            builder.header("X-Goog-AuthUser", "0")
        }
        return builder
    }

    private fun post(endpoint: String, body: JSONObject): JSONObject {
        body.put("context", context())
        val request = signed(
            Request.Builder()
                .url("$ORIGIN/youtubei/v1/$endpoint?prettyPrint=false&key=$KEY")
                .post(body.toString().toRequestBody(JSON_TYPE))
                .header("User-Agent", UA)
                .header("Origin", ORIGIN)
                .header("X-Origin", ORIGIN)
                .header("X-Youtube-Client-Name", "1")
                .header("X-Youtube-Client-Version", CLIENT_VERSION)
                .header("Accept-Language", "en-US,en;q=0.9")
        ).build()
        PmDownloader.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("YouTube answered ${response.code}")
            val text = response.body?.string() ?: throw IOException("Empty answer from YouTube")
            return JSONObject(text)
        }
    }

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
     * and feeds the recommendations. Best effort: failures are ignored by the caller.
     */
    suspend fun registerPlayback(videoId: String) = withContext(Dispatchers.IO) {
        val player = post(
            "player",
            JSONObject().put("videoId", videoId).put("contentCheckOk", true).put("racyCheckOk", true)
        )
        val base = player.optJSONObject("playbackTracking")
            ?.optJSONObject("videostatsPlaybackUrl")
            ?.optString("baseUrl")
            ?.takeIf { it.isNotBlank() }
            ?: return@withContext
        val request = signed(
            Request.Builder()
                .url("$base&ver=2&c=WEB&cpn=${randomCpn()}&cmt=0&el=detailpage&st=0&et=0")
                .header("User-Agent", UA)
                .header("Origin", ORIGIN)
                .header("X-Origin", ORIGIN)
        ).build()
        PmDownloader.client.newCall(request).execute().use { }
    }

    private fun randomCpn(): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        return (1..16).map { alphabet.random() }.joinToString("")
    }
}
