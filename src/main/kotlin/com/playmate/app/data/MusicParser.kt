package com.playmate.app.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Reads YouTube Music's home answer. It follows what the server sends, not a fixed list of
 * shelves: every shelf that holds items becomes a [MusicShelf], in the order YouTube Music sent
 * it, under the title YouTube Music gave it.
 */
internal object MusicParser {

    private val SHELF_KEYS = listOf(
        "musicCarouselShelfRenderer",
        "musicImmersiveCarouselShelfRenderer",
        "musicShelfRenderer",
        "musicPlaylistShelfRenderer",
        "musicCardShelfRenderer"
    )

    /** The endpoint an item opens, reduced to what Playmate needs. */
    private class Nav(
        val videoId: String?,
        val playlistId: String?,
        val browseId: String?,
        val params: String?,
        val pageType: String?,
        val videoType: String?
    )

    // ---------------------------------------------------------------- page

    fun parseHome(root: JSONObject): MusicHome {
        val section = root.optJSONObject("contents")
            ?.optJSONObject("singleColumnBrowseResultsRenderer")
            ?.optJSONArray("tabs")?.optJSONObject(0)
            ?.optJSONObject("tabRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("sectionListRenderer")
            ?: root.optJSONObject("continuationContents")?.optJSONObject("sectionListContinuation")
            ?: findObject(root, "sectionListRenderer")
            ?: findObject(root, "sectionListContinuation")

        if (section == null) {
            return MusicHome(emptyList(), emptyList(), null, "no section list. keys: ${keysOf(root)}")
        }

        val chips = parseChips(section)
        val token = section.optJSONArray("continuations")
            ?.optJSONObject(0)
            ?.optJSONObject("nextContinuationData")
            ?.optString("continuation")
            ?.takeIf { it.isNotBlank() }

        val contents = section.optJSONArray("contents") ?: JSONArray()
        val shelves = mutableListOf<MusicShelf>()
        for (i in 0 until contents.length()) {
            val element = contents.optJSONObject(i) ?: continue
            parseShelf(element)?.let { shelves += it }
        }

        val note = if (shelves.isEmpty() && chips.isEmpty()) {
            "no shelves. section keys: ${keysOf(section)}; " +
                "content kinds: ${(0 until contents.length()).mapNotNull { contents.optJSONObject(it) }
                    .joinToString { keysOf(it) }}"
        } else null
        return MusicHome(chips, shelves, token, note)
    }

    private fun parseChips(section: JSONObject): List<MusicChip> {
        val chips = section.optJSONObject("header")
            ?.optJSONObject("chipCloudRenderer")
            ?.optJSONArray("chips") ?: return emptyList()
        val out = mutableListOf<MusicChip>()
        for (i in 0 until chips.length()) {
            val chip = chips.optJSONObject(i)?.optJSONObject("chipCloudChipRenderer") ?: continue
            val label = ytText(chip.optJSONObject("text")).trim()
            if (label.isBlank()) continue
            val params = chip.optJSONObject("navigationEndpoint")
                ?.optJSONObject("browseEndpoint")
                ?.optString("params")
                ?.takeIf { it.isNotBlank() }
            out += MusicChip(label, params, chip.optBoolean("isSelected"))
        }
        return out
    }

    // ---------------------------------------------------------------- shelves

    private fun parseShelf(element: JSONObject): MusicShelf? {
        val key = SHELF_KEYS.firstOrNull { element.has(it) } ?: return null
        val shelf = element.optJSONObject(key) ?: return null

        val header = shelf.optJSONObject("header")?.let { h ->
            h.optJSONObject("musicCarouselShelfBasicHeaderRenderer")
                ?: h.optJSONObject("musicImmersiveCarouselShelfBasicHeaderRenderer")
                ?: h
        }
        val title = ytText(header?.optJSONObject("title")).ifBlank { ytText(shelf.optJSONObject("title")) }.trim()
        val strapline = ytText(header?.optJSONObject("strapline")).trim().takeIf { it.isNotBlank() }
        val avatar = thumbOf(header?.optJSONObject("thumbnail"))

        val more = header?.optJSONObject("moreContentButton")
            ?.let { findObject(it, "browseEndpoint") }
            ?: shelf.optJSONObject("bottomEndpoint")?.optJSONObject("browseEndpoint")
        val playAll = header?.let { readNav(wrapEndpoint(it)) }

        val raw = shelf.optJSONArray("contents") ?: shelf.optJSONArray("items") ?: JSONArray()
        val items = mutableListOf<MusicItem>()
        val seen = linkedMapOf<String, Int>()
        for (i in 0 until raw.length()) {
            val item = raw.optJSONObject(i) ?: continue
            val kind = item.keys().asSequence().firstOrNull() ?: continue
            seen[kind] = (seen[kind] ?: 0) + 1
            parseItem(item, key == "musicImmersiveCarouselShelfRenderer")?.let { items += it }
        }
        if (items.isEmpty()) return null

        val perColumn = shelf.optString("numItemsPerColumn").toIntOrNull()?.coerceAtLeast(1) ?: 1
        val style = when {
            title.equals("Speed dial", ignoreCase = true) && items.all { it.layout == MusicLayout.Tile } -> ShelfStyle.Grid
            key == "musicImmersiveCarouselShelfRenderer" -> ShelfStyle.Immersive
            items.all { it.layout == MusicLayout.Button } -> ShelfStyle.Buttons
            items.first().layout == MusicLayout.Episode -> ShelfStyle.Episodes
            items.first().layout == MusicLayout.Row -> ShelfStyle.Rows
            else -> ShelfStyle.Tiles
        }

        val debug = buildString {
            append(key.removeSuffix("Renderer"))
            append(" | \"").append(title).append("\"")
            append(" | itemSize=").append(shelf.optString("itemSize").ifBlank { "-" })
            append(" | perColumn=").append(perColumn)
            append(" | items=").append(items.size)
            append(" | ").append(seen.entries.joinToString(",") { "${it.key.removeSuffix("Renderer")}x${it.value}" })
        }

        return MusicShelf(
            title = title,
            strapline = strapline,
            avatar = avatar,
            style = style,
            rowsPerColumn = if (style == ShelfStyle.Rows) perColumn else 1,
            items = items,
            moreBrowseId = more?.optString("browseId")?.takeIf { it.isNotBlank() },
            moreParams = more?.optString("params")?.takeIf { it.isNotBlank() },
            playAllVideoId = playAll?.videoId,
            playAllPlaylistId = playAll?.playlistId,
            debug = debug,
            itemSize = shelf.optString("itemSize")
        )
    }

    // ---------------------------------------------------------------- items

    private fun parseItem(element: JSONObject, cards: Boolean): MusicItem? {
        element.optJSONObject("musicTwoRowItemRenderer")?.let { r ->
            val base = tile(r) ?: return null
            return if (cards) base.copy(tracks = nestedTracks(element, null)) else base
        }
        element.optJSONObject("musicResponsiveListItemRenderer")?.let { return row(it) }
        element.optJSONObject("musicMultiRowListItemRenderer")?.let { r ->
            val ep = episode(r) ?: return null
            return ep.copy(tracks = nestedTracks(element, r))
        }
        element.optJSONObject("musicNavigationButtonRenderer")?.let { return button(it) }
        return null
    }

    /** Songs or episodes sitting inside a big card, read exactly as the server lists them (at most 3). */
    private fun nestedTracks(element: JSONObject, self: JSONObject?): List<MusicItem> {
        val out = mutableListOf<MusicItem>()
        collectTracks(element, self, out)
        return out
    }

    private fun collectTracks(node: Any?, self: JSONObject?, out: MutableList<MusicItem>) {
        if (out.size >= 3) return
        when (node) {
            is JSONObject -> for (key in node.keys()) {
                val child = node.opt(key)
                if (child === self) continue
                when {
                    key == "musicResponsiveListItemRenderer" && child is JSONObject -> row(child)?.let { out += it }
                    key == "musicMultiRowListItemRenderer" && child is JSONObject -> episode(child)?.let { out += it }
                    else -> collectTracks(child, self, out)
                }
                if (out.size >= 3) return
            }
            is JSONArray -> for (i in 0 until node.length()) {
                collectTracks(node.opt(i), self, out)
                if (out.size >= 3) return
            }
        }
    }

    /** A cover tile: album, playlist, mix, artist, video, podcast. */
    private fun tile(r: JSONObject): MusicItem? {
        val title = ytText(r.optJSONObject("title")).trim()
        if (title.isBlank()) return null
        val nav = readNav(r.optJSONObject("navigationEndpoint"))
        val play = readNav(findObject(r.optJSONObject("thumbnailOverlay"), "playNavigationEndpoint"))
        val time = findObject(r.optJSONObject("thumbnailOverlay"), "thumbnailOverlayTimeStatusRenderer")
        val resume = findObject(r, "thumbnailOverlayResumePlaybackRenderer")
        return MusicItem(
            layout = MusicLayout.Tile,
            kind = kindOf(nav, play, asRow = false),
            title = title,
            subtitle = ytText(r.optJSONObject("subtitle")).trim(),
            thumb = thumbOf(r.optJSONObject("thumbnailRenderer")),
            explicit = isExplicit(r),
            videoId = nav?.videoId ?: play?.videoId,
            playlistId = nav?.playlistId ?: play?.playlistId,
            browseId = nav?.browseId,
            params = nav?.params ?: play?.params,
            duration = ytText(time?.optJSONObject("text")).trim().takeIf { it.isNotBlank() },
            progress = resume?.optInt("percentDurationWatched", -1)?.takeIf { it in 0..100 }
        )
    }

    /** A list row: a song (Quick picks) or a video (Covers and remixes). */
    private fun row(r: JSONObject): MusicItem? {
        val columns = r.optJSONArray("flexColumns") ?: return null
        fun column(i: Int): JSONObject? =
            columns.optJSONObject(i)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")?.optJSONObject("text")

        val title = ytText(column(0)).trim()
        if (title.isBlank()) return null
        val subtitle = (1 until columns.length())
            .map { ytText(column(it)).trim() }
            .filter { it.isNotBlank() }
            .joinToString(" • ")

        val own = readNav(r.optJSONObject("navigationEndpoint"))
            ?: readNav(column(0)?.optJSONArray("runs")?.optJSONObject(0)?.optJSONObject("navigationEndpoint"))
        val play = readNav(findObject(r.optJSONObject("overlay"), "playNavigationEndpoint"))
        val data = r.optJSONObject("playlistItemData")
        val videoId = play?.videoId ?: own?.videoId ?: data?.optString("videoId")?.takeIf { it.isNotBlank() }
        val time = ytText(findObject(r, "thumbnailOverlayTimeStatusRenderer")?.optJSONObject("text")).trim()
            .ifBlank { ytText(r.optJSONArray("fixedColumns")?.optJSONObject(0)
                ?.optJSONObject("musicResponsiveListItemFixedColumnRenderer")?.optJSONObject("text")).trim() }

        return MusicItem(
            layout = MusicLayout.Row,
            kind = kindOf(own, play, asRow = true),
            title = title,
            subtitle = subtitle,
            thumb = thumbOf(r.optJSONObject("thumbnail")),
            explicit = isExplicit(r),
            videoId = videoId,
            playlistId = play?.playlistId ?: own?.playlistId,
            browseId = own?.browseId,
            params = play?.params ?: own?.params,
            duration = time.takeIf { it.isNotBlank() }
        )
    }

    /** A podcast episode row ("The Psychology of Nostalgia • 6d ago • 59 min left"). */
    private fun episode(r: JSONObject): MusicItem? {
        val title = ytText(r.optJSONObject("title")).trim()
        if (title.isBlank()) return null
        val nav = readNav(r.optJSONObject("onTap"))
        val play = readNav(findObject(r.optJSONObject("overlay"), "playNavigationEndpoint"))
        val progress = findObject(r, "musicPlaybackProgressRenderer")
            ?.optInt("playbackProgressPercentage", -1)?.takeIf { it in 0..100 }
        return MusicItem(
            layout = MusicLayout.Episode,
            kind = MusicKind.Episode,
            title = title,
            subtitle = ytText(r.optJSONObject("subtitle")).trim(),
            thumb = thumbOf(r.optJSONObject("thumbnail")),
            explicit = isExplicit(r),
            videoId = play?.videoId ?: nav?.videoId,
            playlistId = play?.playlistId ?: nav?.playlistId,
            browseId = nav?.browseId,
            params = nav?.params,
            progress = progress
        )
    }

    /** A round button such as New releases, Charts, Moods & genres. */
    private fun button(r: JSONObject): MusicItem? {
        val title = ytText(r.optJSONObject("buttonText")).trim()
        if (title.isBlank()) return null
        val nav = readNav(r.optJSONObject("clickCommand"))
        return MusicItem(
            layout = MusicLayout.Button,
            kind = MusicKind.Other,
            title = title,
            subtitle = "",
            thumb = null,
            explicit = false,
            videoId = null,
            playlistId = null,
            browseId = nav?.browseId,
            params = nav?.params,
            icon = findString(r.optJSONObject("iconStyle"), "iconType")
        )
    }

    // ---------------------------------------------------------------- helpers

    private fun str(o: JSONObject?, key: String): String? = o?.optString(key)?.takeIf { it.isNotBlank() }

    /** Reads an endpoint object like {"watchEndpoint": {...}} or {"browseEndpoint": {...}}. */
    private fun readNav(node: JSONObject?): Nav? {
        if (node == null) return null
        node.optJSONObject("watchEndpoint")?.let { watch ->
            return Nav(
                videoId = str(watch, "videoId"),
                playlistId = str(watch, "playlistId"),
                browseId = null,
                params = str(watch, "params"),
                pageType = null,
                videoType = findString(watch, "musicVideoType")
            )
        }
        node.optJSONObject("watchPlaylistEndpoint")?.let { list ->
            return Nav(null, str(list, "playlistId"), null, str(list, "params"), null, null)
        }
        node.optJSONObject("browseEndpoint")?.let { browse ->
            return Nav(null, null, str(browse, "browseId"), str(browse, "params"), findString(browse, "pageType"), null)
        }
        return null
    }

    /** The first play endpoint found inside [node] (a shelf header's "Play all"), as a readable endpoint. */
    private fun wrapEndpoint(node: JSONObject): JSONObject? {
        findObject(node, "watchPlaylistEndpoint")?.let { return JSONObject().put("watchPlaylistEndpoint", it) }
        findObject(node, "watchEndpoint")?.let { return JSONObject().put("watchEndpoint", it) }
        return null
    }

    private fun kindOf(nav: Nav?, play: Nav?, asRow: Boolean): MusicKind {
        val videoId = nav?.videoId ?: play?.videoId
        val videoType = nav?.videoType ?: play?.videoType
        if (videoId != null) {
            return when {
                videoType == null -> if (asRow) MusicKind.Song else MusicKind.Video
                videoType.contains("PODCAST") -> MusicKind.Episode
                videoType.endsWith("_ATV") -> MusicKind.Song
                else -> MusicKind.Video
            }
        }
        val page = nav?.pageType.orEmpty()
        val id = nav?.browseId.orEmpty()
        return when {
            page.contains("ALBUM") || page.contains("AUDIOBOOK") || id.startsWith("MPRE") -> MusicKind.Album
            page.contains("PLAYLIST") || id.startsWith("VL") -> MusicKind.Playlist
            page.contains("ARTIST") || page.contains("USER_CHANNEL") || id.startsWith("UC") -> MusicKind.Artist
            page.contains("PODCAST") || id.startsWith("MPSP") -> MusicKind.Podcast
            nav?.playlistId != null || play?.playlistId != null -> MusicKind.Mix
            else -> MusicKind.Other
        }
    }

    private fun thumbOf(node: JSONObject?): String? =
        findAllArrays(node, "thumbnails").firstOrNull()?.let { lastImageUrl(it) }

    private fun isExplicit(node: JSONObject): Boolean = node.toString().contains("MUSIC_EXPLICIT_BADGE")

    private fun keysOf(node: JSONObject): String = node.keys().asSequence().toList().joinToString(",")
}
