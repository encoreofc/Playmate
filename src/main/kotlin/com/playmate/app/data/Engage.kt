package com.playmate.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class PlaylistOption(val id: String, val title: String, val contains: Boolean)

/**
 * What the signed-in account can do on a video, through the same calls youtube.com makes:
 * like / dislike, subscribe, save to a playlist, read and write comments.
 * Every call throws an IOException with YouTube's own message when it is refused.
 */
object Engage {

    // ---------------------------------------------------------------- the watch page

    suspend fun watchMeta(videoId: String): WatchMeta {
        val root = InnerTube.next(videoId)
        return withContext(Dispatchers.Default) { WatchParser.parse(videoId, root) }
    }

    // ---------------------------------------------------------------- like / dislike

    private fun target(videoId: String) =
        JSONObject().put("target", JSONObject().put("videoId", videoId))

    suspend fun like(videoId: String) {
        withContext(Dispatchers.IO) { InnerTube.post("like/like", target(videoId)) }
    }

    suspend fun dislike(videoId: String) {
        withContext(Dispatchers.IO) { InnerTube.post("like/dislike", target(videoId)) }
    }

    /** Takes back a like or a dislike. */
    suspend fun clearReaction(videoId: String) {
        withContext(Dispatchers.IO) { InnerTube.post("like/removelike", target(videoId)) }
    }

    // ---------------------------------------------------------------- subscriptions

    suspend fun subscribe(channelId: String) {
        withContext(Dispatchers.IO) {
            try {
                InnerTube.post(
                    "subscription/subscribe",
                    JSONObject().put("channelIds", JSONArray().put(channelId)).put("params", "EgIIAhgA")
                )
            } catch (e: IOException) {
                // Same call without the extra "params" the site adds.
                InnerTube.post(
                    "subscription/subscribe",
                    JSONObject().put("channelIds", JSONArray().put(channelId))
                )
            }
        }
    }

    suspend fun unsubscribe(channelId: String) {
        withContext(Dispatchers.IO) {
            try {
                InnerTube.post(
                    "subscription/unsubscribe",
                    JSONObject().put("channelIds", JSONArray().put(channelId)).put("params", "CgIIAhgA")
                )
            } catch (e: IOException) {
                InnerTube.post(
                    "subscription/unsubscribe",
                    JSONObject().put("channelIds", JSONArray().put(channelId))
                )
            }
        }
    }

    // ---------------------------------------------------------------- playlists ("Save")

    /** The account's playlists, with a flag for the ones that already hold [videoId]. */
    suspend fun playlistsFor(videoId: String): List<PlaylistOption> {
        val root = withContext(Dispatchers.IO) {
            InnerTube.post(
                "playlist/get_add_to_playlist",
                JSONObject().put("videoId", videoId).put("excludeWatchLater", false)
            )
        }
        val found = findAllObjects(root, "playlistAddToOptionRenderer").mapNotNull { o ->
            val id = o.optString("playlistId")
            if (id.isBlank()) return@mapNotNull null
            PlaylistOption(
                id = id,
                title = ytText(o.optJSONObject("title")).ifBlank { id },
                contains = o.optString("containsSelectedVideos") == "ALL"
            )
        }
        // If the answer could not be read, "Watch later" still works: it always exists.
        return found.ifEmpty { listOf(PlaylistOption("WL", "Watch later", false)) }
    }

    suspend fun setInPlaylist(playlistId: String, videoId: String, add: Boolean) {
        withContext(Dispatchers.IO) {
            val action = JSONObject()
                .put("action", if (add) "ACTION_ADD_VIDEO" else "ACTION_REMOVE_VIDEO_BY_VIDEO_ID")
                .put(if (add) "addedVideoId" else "removedVideoId", videoId)
            val root = InnerTube.post(
                "browse/edit_playlist",
                JSONObject().put("playlistId", playlistId).put("actions", JSONArray().put(action))
            )
            val status = root.optString("status")
            if (status.isNotBlank() && status != "STATUS_SUCCEEDED") {
                throw IOException("YouTube said $status")
            }
        }
    }

    // ---------------------------------------------------------------- comments

    /** A page of comments (or, with [replies], of the replies to one comment). */
    suspend fun comments(token: String, replies: Boolean = false): CommentPage {
        val root = InnerTube.nextContinuation(token)
        return withContext(Dispatchers.Default) { CommentParser.parse(root, asReplies = replies) }
    }

    suspend fun postComment(createParams: String, text: String) {
        withContext(Dispatchers.IO) {
            val root = InnerTube.post(
                "comment/create_comment",
                JSONObject().put("createCommentParams", createParams).put("commentText", text)
            )
            if (root.has("success") && !root.optBoolean("success")) {
                throw IOException("YouTube didn't accept the comment")
            }
        }
    }
}
