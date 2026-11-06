package com.playmate.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * Keeps a signed-in account's search history in step with YouTube's own.
 * Unofficial: it uses the same calls youtube.com makes, so YouTube can change them at any time.
 */
object SearchSync {

    private const val ORIGIN = "https://www.youtube.com"
    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/131.0.0.0 Safari/537.36"

    /**
     * What YouTube's search box offers this account for [query]. For an empty [query] these are the
     * account's own recent searches, newest first. Throws if YouTube does not answer.
     */
    suspend fun suggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        val url = "https://suggestqueries-clients6.youtube.com/complete/search".toHttpUrl().newBuilder()
            .addQueryParameter("client", "youtube")
            .addQueryParameter("hl", "en")
            .addQueryParameter("gl", "BD")
            .addQueryParameter("gs_ri", "youtube")
            .addQueryParameter("ds", "yt")
            .addQueryParameter("xhr", "t")
            .addQueryParameter("xssi", "t")
            .addQueryParameter("q", query)
            .build()
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Origin", ORIGIN)
            .header("Referer", "$ORIGIN/")
            .header("Accept-Language", "en-US,en;q=0.9")
        if (Session.cookie.isNotBlank()) {
            builder.header("Cookie", Session.cookie)
            builder.header("Authorization", Session.authHeader(ORIGIN))
            builder.header("X-Goog-AuthUser", "0")
        }
        PmDownloader.client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("suggestions answered ${response.code}")
            parse(response.body?.string().orEmpty())
        }
    }

    /**
     * Tells YouTube this account searched for [query], so it lands in the account's search history
     * (the same search call the website makes while signed in). The answer is not needed.
     */
    suspend fun record(query: String) {
        withContext(Dispatchers.IO) {
            InnerTube.post("search", JSONObject().put("query", query))
        }
    }

    /** The answer starts with a safety prefix, then a JSON list: ["query", [["suggestion", ...], ...], ...]. */
    private fun parse(raw: String): List<String> {
        val start = raw.indexOf('[')
        if (start < 0) return emptyList()
        val root = JSONArray(raw.substring(start))
        val list = root.optJSONArray(1) ?: return emptyList()
        val tags = Regex("<[^>]*>")
        return (0 until list.length())
            .mapNotNull { i ->
                val entry = list.opt(i)
                val text = when (entry) {
                    is JSONArray -> entry.optString(0)
                    is String -> entry
                    else -> ""
                }
                text.replace(tags, "").trim().takeIf { it.isNotEmpty() }
            }
            .distinct()
    }
}
