package com.playmate.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class FeedPage(val items: List<VideoItem>, val token: String?)

object FeedRepo {
    suspend fun home(): FeedPage = parse(InnerTube.browse("FEwhat_to_watch"))
    suspend fun history(): FeedPage = parse(InnerTube.browse("FEhistory"))
    suspend fun more(token: String): FeedPage = parse(InnerTube.continuation(token))

    private suspend fun parse(root: JSONObject): FeedPage =
        withContext(Dispatchers.Default) { FeedParser.parse(root) }
}

fun videoIdOf(url: String): String? =
    Regex("(?:[?&]v=|youtu\\.be/|/shorts/|/live/)([A-Za-z0-9_-]{11})").find(url)?.groupValues?.get(1)

/** First object stored under [key] anywhere inside [node]. */
fun findObject(node: Any?, key: String): JSONObject? {
    when (node) {
        is JSONObject -> {
            node.optJSONObject(key)?.let { return it }
            val keys = node.keys()
            while (keys.hasNext()) {
                findObject(node.opt(keys.next()), key)?.let { return it }
            }
        }
        is JSONArray -> for (i in 0 until node.length()) {
            findObject(node.opt(i), key)?.let { return it }
        }
    }
    return null
}

private fun collectObjects(node: Any?, key: String, out: MutableList<JSONObject>) {
    when (node) {
        is JSONObject -> {
            val keys = node.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = node.opt(k)
                if (k == key && v is JSONObject) out += v else collectObjects(v, key, out)
            }
        }
        is JSONArray -> for (i in 0 until node.length()) collectObjects(node.opt(i), key, out)
    }
}

/**
 * Reads YouTube's feed JSON without depending on its exact layout: it walks the whole
 * tree and picks out every video card, whichever of the known card formats it uses.
 * Ad slots and promoted cards are skipped.
 */
object FeedParser {

    private val VIDEO_RENDERERS = setOf(
        "videoRenderer", "gridVideoRenderer", "compactVideoRenderer", "playlistVideoRenderer"
    )
    private val AD_KEYS = setOf(
        "adSlotRenderer", "promotedVideoRenderer", "promotedSparklesWebRenderer",
        "inFeedAdLayoutRenderer", "displayAdRenderer", "bannerPromoRenderer"
    )

    fun parse(root: JSONObject): FeedPage {
        val found = LinkedHashMap<String, VideoItem>()
        var token: String? = null

        fun add(item: VideoItem?) {
            if (item != null && item.url !in found) found[item.url] = item
        }

        fun visit(node: Any?) {
            when (node) {
                is JSONArray -> for (i in 0 until node.length()) visit(node.opt(i))
                is JSONObject -> {
                    val keys = node.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val value = node.opt(key)
                        when {
                            key in AD_KEYS -> Unit
                            key in VIDEO_RENDERERS && value is JSONObject -> add(renderer(value))
                            key == "lockupViewModel" && value is JSONObject -> add(lockup(value))
                            key == "continuationItemRenderer" && value is JSONObject -> {
                                if (token == null) {
                                    token = value.str("continuationEndpoint", "continuationCommand", "token")
                                        .ifBlank { null }
                                }
                            }
                            else -> visit(value)
                        }
                    }
                }
            }
        }

        visit(root)
        return FeedPage(found.values.toList(), token)
    }

    // ---- classic cards (videoRenderer & friends) ----

    private fun renderer(o: JSONObject): VideoItem? {
        val id = o.optString("videoId")
        if (id.isBlank() || o.has("upcomingEventData")) return null
        val title = text(o.optJSONObject("title"))
        if (title.isBlank()) return null

        val channel = text(o.optJSONObject("longBylineText"))
            .ifBlank { text(o.optJSONObject("ownerText")) }
            .ifBlank { text(o.optJSONObject("shortBylineText")) }

        var duration = clock(text(o.optJSONObject("lengthText")))
        var live = false
        val overlays = o.optJSONArray("thumbnailOverlays")
        if (overlays != null) {
            for (i in 0 until overlays.length()) {
                val status = overlays.optJSONObject(i)
                    ?.optJSONObject("thumbnailOverlayTimeStatusRenderer") ?: continue
                if (status.optString("style") == "LIVE") live = true
                if (duration <= 0L) duration = clock(text(status.optJSONObject("text")))
            }
        }

        val viewsText = text(o.optJSONObject("viewCountText"))
            .ifBlank { text(o.optJSONObject("shortViewCountText")) }
        if (viewsText.contains("watching", ignoreCase = true)) live = true

        val thumbs = o.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
        val thumb = thumbs?.optJSONObject(thumbs.length() - 1)?.optString("url")
            ?.takeIf { it.isNotBlank() } ?: fallbackThumb(id)

        return VideoItem(
            url = watchUrl(id),
            title = title,
            channel = channel,
            durationSec = duration,
            views = count(viewsText),
            thumb = thumb,
            uploaded = text(o.optJSONObject("publishedTimeText")).ifBlank { null },
            live = live
        )
    }

    // ---- newer cards (lockupViewModel) ----

    private fun lockup(o: JSONObject): VideoItem? {
        if (o.optString("contentType") != "LOCKUP_CONTENT_TYPE_VIDEO") return null
        val id = o.optString("contentId")
        if (id.isBlank()) return null
        val meta = o.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel") ?: return null
        val title = text(meta.optJSONObject("title"))
        if (title.isBlank()) return null

        val rows = meta.optJSONObject("metadata")
            ?.optJSONObject("contentMetadataViewModel")
            ?.optJSONArray("metadataRows")
        val cells = mutableListOf<List<String>>()
        if (rows != null) {
            for (i in 0 until rows.length()) {
                val parts = rows.optJSONObject(i)?.optJSONArray("metadataParts") ?: continue
                cells += List(parts.length()) { j -> text(parts.optJSONObject(j)?.optJSONObject("text")) }
            }
        }
        val channel = cells.getOrNull(0)?.firstOrNull().orEmpty()
        val stats = cells.getOrNull(1).orEmpty()
        val viewsText = stats.getOrNull(0).orEmpty()
        val published = stats.getOrNull(1)?.ifBlank { null }

        var duration = 0L
        var live = viewsText.contains("watching", ignoreCase = true)
        val badges = mutableListOf<JSONObject>()
        collectObjects(o.optJSONObject("contentImage"), "thumbnailBadgeViewModel", badges)
        for (badge in badges) {
            val label = badge.optString("text")
            if (label.equals("LIVE", ignoreCase = true)) live = true
            else if (duration <= 0L) duration = clock(label)
        }

        val sources = findObject(o.optJSONObject("contentImage"), "image")?.optJSONArray("sources")
        val thumb = sources?.optJSONObject(sources.length() - 1)?.optString("url")
            ?.takeIf { it.isNotBlank() } ?: fallbackThumb(id)

        return VideoItem(
            url = watchUrl(id),
            title = title,
            channel = channel,
            durationSec = duration,
            views = count(viewsText),
            thumb = thumb,
            uploaded = published,
            live = live
        )
    }

    // ---- small helpers ----

    private fun watchUrl(id: String) = "https://www.youtube.com/watch?v=$id"

    private fun fallbackThumb(id: String) = "https://i.ytimg.com/vi/$id/hqdefault.jpg"

    private fun text(o: JSONObject?): String {
        if (o == null) return ""
        val simple = o.optString("simpleText")
        if (simple.isNotBlank()) return simple
        val runs = o.optJSONArray("runs") ?: return o.optString("content")
        return buildString {
            for (i in 0 until runs.length()) append(runs.optJSONObject(i)?.optString("text").orEmpty())
        }
    }

    private fun clock(raw: String): Long {
        val s = raw.trim()
        if (!Regex("^\\d{1,2}(:\\d{2}){1,2}$").matches(s)) return 0L
        return s.split(":").fold(0L) { acc, part -> acc * 60 + part.toLong() }
    }

    private fun count(raw: String): Long {
        val m = Regex("([\\d.,]+)\\s*([KMBkmb])?").find(raw) ?: return -1L
        val n = m.groupValues[1].replace(",", "").toDoubleOrNull() ?: return -1L
        val factor = when (m.groupValues[2].uppercase()) {
            "K" -> 1e3
            "M" -> 1e6
            "B" -> 1e9
            else -> 1.0
        }
        return (n * factor).toLong()
    }

    private fun JSONObject.str(vararg path: String): String {
        var cur: Any? = this
        for (key in path) cur = (cur as? JSONObject)?.opt(key)
        return (cur as? String).orEmpty()
    }
}
