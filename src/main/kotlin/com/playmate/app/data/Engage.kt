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

    // ---------------------------------------------------------------- feed feedback

    /** Sends a "Not interested" / "Don't recommend this channel" token back to YouTube. */
    suspend fun sendFeedback(token: String) {
        withContext(Dispatchers.IO) {
            val root = InnerTube.post(
                "feedback",
                JSONObject()
                    .put("feedbackTokens", JSONArray().put(token))
                    .put("isFeedbackTokenUnencrypted", false)
                    .put("shouldMerge", false)
            )
            val processed = root.optJSONArray("feedbackResponses")?.optJSONObject(0)?.optBoolean("isProcessed", true)
            if (processed == false) throw IOException("YouTube didn't accept the feedback")
        }
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

    /**
     * The account's playlists, with a flag for the ones that already hold [videoId].
     *
     * YouTube changed what this call wants: the site now sends a list ("videoIds") where it used to
     * send one "videoId", and the old form is answered with 400 "Request contains an invalid
     * argument". The current form goes first; the old one stays as a fallback in case a
     * different version of the site is still being served.
     */
    suspend fun playlistsFor(videoId: String): List<PlaylistOption> {
        val bodies = listOf<() -> JSONObject>(
            { JSONObject().put("videoIds", JSONArray().put(videoId)).put("excludeWatchLater", false) },
            { JSONObject().put("videoId", videoId).put("excludeWatchLater", false) },
            { JSONObject().put("videoIds", JSONArray().put(videoId)) }
        )
        var failure: IOException? = null
        var root: JSONObject? = null
        for (body in bodies) {
            try {
                root = withContext(Dispatchers.IO) { InnerTube.post("playlist/get_add_to_playlist", body()) }
                break
            } catch (e: IOException) {
                failure = e
            }
        }
        if (root == null) throw failure ?: IOException("Couldn't load your playlists")

        val found = findAllObjects(root, "playlistAddToOptionRenderer").mapNotNull { o ->
            val id = o.optString("playlistId")
            if (id.isBlank()) return@mapNotNull null
            PlaylistOption(
                id = id,
                title = ytText(o.optJSONObject("title")).ifBlank { id },
                contains = o.optString("containsSelectedVideos") == "ALL"
            )
        }.distinctBy { it.id }
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

    /**
     * Like, unlike, dislike or undislike one comment. [action] is the opaque value YouTube handed
     * out with that comment for exactly this button; it is sent back unchanged.
     */
    suspend fun commentAction(action: String) {
        withContext(Dispatchers.IO) {
            val root = InnerTube.post(
                "comment/perform_comment_action",
                JSONObject().put("actions", JSONArray().put(action))
            )
            val results = root.optJSONArray("actionResults")
            if (results != null) {
                for (i in 0 until results.length()) {
                    val status = results.optJSONObject(i)?.optString("status").orEmpty()
                    if (status.contains("FAIL", ignoreCase = true)) throw IOException("YouTube said $status")
                }
            }
        }
    }

    /** Writes a reply under a comment; [replyParams] came with that comment. */
    suspend fun replyToComment(replyParams: String, text: String) {
        withContext(Dispatchers.IO) {
            val root = InnerTube.post(
                "comment/create_comment_reply",
                JSONObject().put("createReplyParams", replyParams).put("commentText", text)
            )
            if (root.has("success") && !root.optBoolean("success")) {
                throw IOException("YouTube didn't accept the reply")
            }
        }
    }
}
