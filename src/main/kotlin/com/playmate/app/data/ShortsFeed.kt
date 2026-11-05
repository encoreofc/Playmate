package com.playmate.app.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException

/**
 * YouTube's own endless Shorts sequence (what the Shorts tab of youtube.com scrolls through),
 * read with the signed-in session so it is the account's own personalised stream.
 *
 * Both calls go to `reel/reel_watch_sequence`:
 *  - a sequence is started from a Short by sending the protobuf message `{1: videoId}` as
 *    `sequenceParams` (the old `reel_item_watch` + `params` seed is refused with HTTP 400);
 *  - every reel in the answer carries its own `sequenceParams`, which continues the sequence.
 *
 * The answer also holds ads (`videoType == REEL_VIDEO_TYPE_AD`, `adClientParams`); they are dropped.
 *
 * Unofficial like the rest of [InnerTube], so every field is read defensively and the caller
 * ([com.playmate.app.ui.shorts.ShortsController]) falls back to other sources when this
 * answers nothing: the Shorts screen never runs dry.
 */
object ShortsFeed {

    class Page(val items: List<VideoItem>, val token: String?)

    private val ID = Regex("^[A-Za-z0-9_-]{11}$")

    /** The Shorts YouTube plays after [videoId], plus the token for the next page. */
    suspend fun after(videoId: String): Page = withContext(Dispatchers.IO) {
        var failure: Throwable? = null
        for (params in seedParams(videoId)) {
            val result = attempt { sequence(params, videoId) }
            result.onSuccess { if (it.items.isNotEmpty()) return@withContext it }
            result.onFailure { failure = it }
        }
        throw failure ?: IOException("YouTube has no Shorts sequence for this video")
    }

    /** The next page of a sequence, from the token a previous page returned. */
    suspend fun more(token: String): Page = withContext(Dispatchers.IO) { sequence(token, null) }

    private fun sequence(sequenceParams: String, skip: String?): Page {
        val root = InnerTube.post("reel/reel_watch_sequence", JSONObject().put("sequenceParams", sequenceParams))
        return parse(root, skip)
    }

    /** Protobuf `{1: videoId}` (tag 0x0A, length, the id), in the two base64 spellings YouTube may accept. */
    private fun seedParams(videoId: String): List<String> {
        val id = videoId.toByteArray(Charsets.US_ASCII)
        val message = ByteArray(id.size + 2)
        message[0] = 0x0A
        message[1] = id.size.toByte()
        id.copyInto(message, destinationOffset = 2)
        return listOf(
            Base64.encodeToString(message, Base64.NO_WRAP),
            Base64.encodeToString(message, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        ).distinct()
    }

    private fun parse(root: JSONObject, skip: String?): Page {
        val entries = findAllArrays(root, "entries").flatMap { array ->
            (0 until array.length()).mapNotNull { array.optJSONObject(it) }
        }
        val nodes: List<JSONObject> = entries.ifEmpty {
            findAllObjects(root, "reelWatchEndpoint").map { JSONObject().put("reelWatchEndpoint", it) }
        }

        val seen = HashSet<String>()
        val items = ArrayList<VideoItem>()
        var lastSequence: String? = null
        for (entry in nodes) {
            val endpoint = findObject(entry, "reelWatchEndpoint") ?: continue
            if (isAd(entry)) continue
            val id = endpoint.optString("videoId")
            if (!ID.matches(id) || id == skip || !seen.add(id)) continue
            endpoint.optString("sequenceParams").takeIf { it.isNotBlank() }?.let { lastSequence = it }
            val thumbs = endpoint.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
            items += VideoItem(
                url = "https://www.youtube.com/shorts/$id",
                title = "",
                channel = "",
                durationSec = 0L,
                views = -1L,
                thumb = lastImageUrl(thumbs) ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg",
                uploaded = null
            )
        }

        val token = tokenOf(root.optJSONObject("continuationEndpoint"))
            ?: lastSequence
            ?: findString(root, "sequenceContinuation")
        return Page(items, token)
    }

    private fun isAd(entry: JSONObject): Boolean =
        findString(entry, "videoType") == "REEL_VIDEO_TYPE_AD" || findObject(entry, "adClientParams") != null
}
