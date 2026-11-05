package com.playmate.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * One page of a feed. [note] is only filled when the page came back without a single video:
 * it is a short, readable summary of what the server did send, so a blank feed can be explained.
 */
class FeedPage(
    val items: List<VideoItem>,
    val token: String?,
    val note: String? = null,
    /** True when the account itself has nothing to show (no watch history yet): not an error. */
    val accountEmpty: Boolean = false,
    /** The topic chips YouTube sent with this page (empty when it sent none). */
    val chips: List<FeedChip> = emptyList()
)

object FeedRepo {
    suspend fun home(): FeedPage = load("FEwhat_to_watch", "/")
    suspend fun history(): FeedPage = load("FEhistory", "/feed/history")
    suspend fun subscriptions(): FeedPage = load("FEsubscriptions", "/feed/subscriptions")
    suspend fun more(token: String): FeedPage = parse(InnerTube.continuation(token))

    private suspend fun parse(root: JSONObject): FeedPage =
        withContext(Dispatchers.Default) { FeedParser.parse(root) }

    /**
     * Loads a feed the most reliable way available:
     *  1. the API call;
     *  2. if that page is empty but carries a continuation, the continuation (some answers are
     *     only a "load the rest" stub);
     *  3. if the API gave nothing at all, the site's own page for the same feed.
     */
    private suspend fun load(browseId: String, webPath: String): FeedPage {
        val api = attempt { followStub(parse(InnerTube.browse(browseId))) }
        api.getOrNull()?.takeIf { it.items.isNotEmpty() }?.let { return it }

        val web = attempt { followStub(parse(InnerTube.page(webPath))) }
        web.getOrNull()?.takeIf { it.items.isNotEmpty() }?.let { return it }

        val apiNote = api.fold({ "api: ${it.note ?: "no videos"}" }, { "api failed: ${it.message}" })
        val webNote = web.fold({ "page: ${it.note ?: "no videos"}" }, { "page failed: ${it.message}" })
        if (api.isFailure && web.isFailure) {
            throw java.io.IOException("$apiNote | $webNote")
        }
        // The server answered normally and says the account has nothing yet: a new feed
        // ("Start watching videos...") or a history list with no entries.
        val answer = "$apiNote | $webNote"
        val noRecommendations = answer.contains("feedNudgeRenderer")
        val noHistory = answer.contains("messageRenderer") && answer.contains("itemSectionRenderer")
        if (InnerTube.sessionRecognised != false && (noRecommendations || noHistory)) {
            return FeedPage(emptyList(), null, null, accountEmpty = true)
        }

        val signIn = when (InnerTube.sessionRecognised) {
            true -> "signed in"
            false -> "NOT recognised as signed in"
            null -> "sign-in state unknown"
        }
        return FeedPage(emptyList(), null, "$signIn. $apiNote | $webNote")
    }

    /** Follows up to three empty "stub" pages to the real content. */
    private suspend fun followStub(first: FeedPage): FeedPage {
        var page = first
        var hops = 0
        while (page.items.isEmpty() && page.token != null && hops < 3) {
            page = parse(InnerTube.continuation(page.token!!))
            hops++
        }
        return page
    }
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
        val chips = ArrayList<FeedChip>()

        var section: String? = null

        fun add(item: VideoItem?) {
            if (item != null && item.url !in found) {
                found[item.url] = if (section != null) item.copy(section = section) else item
            }
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
                            key == "shortsLockupViewModel" && value is JSONObject -> add(shortsLockup(value))
                            key == "reelItemRenderer" && value is JSONObject -> add(reelItem(value))
                            key == "itemSectionRenderer" && value is JSONObject -> {
                                // A day group (History: "Yesterday", "Tuesday"): its videos carry that heading.
                                val before = section
                                sectionLabel(value)?.let { section = it }
                                visit(value)
                                section = before
                            }
                            key == "chipCloudChipRenderer" && value is JSONObject -> chip(value)?.let { chips += it }
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
        val items = found.values.toList()
        return FeedPage(
            items, token, if (items.isEmpty()) describe(root) else null,
            chips = chips.distinctBy { it.label }
        )
    }

    /** The heading of an item section, e.g. "Yesterday" in History, or null when it has none. */
    private fun sectionLabel(section: JSONObject): String? {
        val header = section.optJSONObject("header")?.optJSONObject("itemSectionHeaderRenderer") ?: return null
        return text(header.optJSONObject("title")).trim().ifBlank { null }
    }

    /** One real topic chip. Chips that lead nowhere (no continuation, not selected) are dropped. */
    private fun chip(o: JSONObject): FeedChip? {
        val label = text(o.optJSONObject("text")).trim()
        if (label.isBlank()) return null
        val token = findObject(o, "continuationCommand")?.optString("token")?.ifBlank { null }
        val selected = o.optBoolean("isSelected")
        if (token == null && !selected) return null
        return FeedChip(label, token, selected)
    }

    private fun firstUrl(sources: JSONArray?): String? =
        sources?.optJSONObject(0)?.optString("url")?.takeIf { it.isNotBlank() }

    /**
     * A short summary of what an answer contains, e.g.
     * "keys: responseContext,contents; seen: richItemRenderer=24 lockupViewModel=24".
     * Used to explain an empty feed.
     */
    fun describe(root: JSONObject): String {
        val counts = LinkedHashMap<String, Int>()
        val texts = LinkedHashSet<String>()

        fun walk(node: Any?) {
            when (node) {
                is JSONObject -> {
                    val keys = node.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        if (k.endsWith("Renderer") || k.endsWith("ViewModel")) {
                            counts[k] = (counts[k] ?: 0) + 1
                        }
                        walk(node.opt(k))
                    }
                    if (texts.size < 4 && (node.has("simpleText") || node.has("runs"))) {
                        val line = text(node).trim()
                        if (line.isNotBlank()) texts += line.take(90)
                    }
                }
                is JSONArray -> for (i in 0 until node.length()) walk(node.opt(i))
            }
        }

        // Only the feed body: the page shell (menus, shortcuts, top bar) is the same for every answer.
        val body: Any = root.opt("contents") ?: root
        walk(body)
        val top = counts.entries
            .sortedByDescending { it.value }
            .take(10)
            .joinToString(" ") { "${it.key}=${it.value}" }
        val tab = findObject(body, "tabRenderer")?.keys()?.asSequence()?.take(8)?.joinToString(",")
        return buildString {
            append("keys: ").append(root.keys().asSequence().take(6).joinToString(","))
            append("; seen: ").append(top.ifBlank { "nothing" })
            if (tab != null) append("; tab: ").append(tab)
            if (texts.isNotEmpty()) append("; text: ").append(texts.joinToString(" / ") { "\"$it\"" })
        }
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

        // History, search and playlists list Shorts as ordinary cards; YouTube marks them with a reel
        // endpoint, a /shorts/ link or a "SHORTS" badge. They must open in the Shorts player.
        var short = findObject(o, "reelWatchEndpoint") != null ||
            o.optJSONObject("navigationEndpoint")?.optJSONObject("commandMetadata")
                ?.optJSONObject("webCommandMetadata")?.optString("url").orEmpty().contains("/shorts/")
        if (overlays != null) {
            for (i in 0 until overlays.length()) {
                val style = overlays.optJSONObject(i)
                    ?.optJSONObject("thumbnailOverlayTimeStatusRenderer")?.optString("style")
                if (style.equals("SHORTS", ignoreCase = true)) short = true
            }
        }

        return VideoItem(
            url = if (short) shortUrl(id) else watchUrl(id),
            title = title,
            channel = channel,
            durationSec = duration,
            views = count(viewsText),
            thumb = thumb,
            uploaded = text(o.optJSONObject("publishedTimeText")).ifBlank { null },
            live = live,
            avatar = firstUrl(
                findObject(o, "channelThumbnailWithLinkRenderer")
                    ?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
            )
        )
    }

    // ---- newer cards (lockupViewModel) ----

    private fun lockup(o: JSONObject): VideoItem? {
        val type = o.optString("contentType")
        val id = o.optString("contentId")
        if (id.isBlank()) return null
        // Videos only: playlists, channels and mixes use other content types.
        val isVideo = type == "LOCKUP_CONTENT_TYPE_VIDEO" ||
            (type.isBlank() && Regex("^[A-Za-z0-9_-]{11}$").matches(id))
        if (!isVideo) return null
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

        val short = findObject(o, "reelWatchEndpoint") != null

        return VideoItem(
            url = if (short) shortUrl(id) else watchUrl(id),
            title = title,
            channel = channel,
            durationSec = duration,
            views = count(viewsText),
            thumb = thumb,
            uploaded = published,
            live = live,
            avatar = firstUrl(findObject(meta, "avatarViewModel")?.optJSONObject("image")?.optJSONArray("sources"))
        )
    }

    // ---- short-form cards ----

    /** Short cards in the newer layout (shortsLockupViewModel). */
    private fun shortsLockup(o: JSONObject): VideoItem? {
        val id = findObject(o, "reelWatchEndpoint")?.optString("videoId").orEmpty()
            .ifBlank { o.optString("entityId").substringAfterLast("-") }
        if (!Regex("^[A-Za-z0-9_-]{11}$").matches(id)) return null

        val overlay = o.optJSONObject("overlayMetadata")
        val title = text(overlay?.optJSONObject("primaryText"))
            .ifBlank { o.optString("accessibilityText").substringBefore(",") }
        if (title.isBlank()) return null
        val viewsText = text(overlay?.optJSONObject("secondaryText"))

        val sources = o.optJSONObject("thumbnail")?.optJSONArray("sources")
            ?: findObject(o, "thumbnail")?.optJSONArray("sources")
        val thumb = sources?.optJSONObject(sources.length() - 1)?.optString("url")
            ?.takeIf { it.isNotBlank() } ?: fallbackThumb(id)

        return VideoItem(
            url = shortUrl(id),
            title = title,
            channel = "Shorts",
            durationSec = 0L,
            views = count(viewsText),
            thumb = thumb,
            uploaded = null,
            live = false
        )
    }

    /** Short cards in the older layout (reelItemRenderer). */
    private fun reelItem(o: JSONObject): VideoItem? {
        val id = o.optString("videoId")
        if (id.isBlank()) return null
        val title = text(o.optJSONObject("headline"))
        if (title.isBlank()) return null
        val thumbs = o.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
        val thumb = thumbs?.optJSONObject(thumbs.length() - 1)?.optString("url")
            ?.takeIf { it.isNotBlank() } ?: fallbackThumb(id)
        return VideoItem(
            url = shortUrl(id),
            title = title,
            channel = "Shorts",
            durationSec = 0L,
            views = count(text(o.optJSONObject("viewCountText"))),
            thumb = thumb,
            uploaded = null,
            live = false
        )
    }

    // ---- small helpers ----

    private fun watchUrl(id: String) = "https://www.youtube.com/watch?v=$id"

    private fun shortUrl(id: String) = "https://www.youtube.com/shorts/$id"

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
