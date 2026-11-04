package com.playmate.app.data

import org.json.JSONObject

enum class Reaction { None, Liked, Disliked }

/**
 * What YouTube's watch page knows about one video beyond the stream itself: the account's
 * like / dislike, the channel and whether it is subscribed, and where the comments start.
 * Every field is best effort: a missing one is simply null.
 */
data class WatchMeta(
    val videoId: String,
    val loaded: Boolean,
    val reaction: Reaction,
    val likeCount: String?,
    val channelId: String?,
    val channelName: String?,
    val channelAvatar: String?,
    val subscribers: String?,
    val subscribed: Boolean?,
    val commentCount: String?,
    val commentsToken: String?
) {
    companion object {
        fun blank(videoId: String) = WatchMeta(
            videoId, false, Reaction.None, null, null, null, null, null, null, null, null
        )
    }
}

object WatchParser {

    fun parse(videoId: String, root: JSONObject): WatchMeta {
        val owner = findObject(root, "videoOwnerRenderer")
        val channelId = findString(owner, "browseId")?.takeIf { it.startsWith("UC") }
        val subscribed = findBoolean(owner?.optJSONObject("subscribeButton"), "subscribed")
            ?: findBoolean(findObject(root, "subscriptionStateEntity"), "subscribed")

        return WatchMeta(
            videoId = videoId,
            loaded = true,
            reaction = reaction(root),
            likeCount = likeCount(root),
            channelId = channelId,
            channelName = ytText(owner?.optJSONObject("title")).ifBlank { null },
            channelAvatar = lastImageUrl(owner?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")),
            subscribers = ytText(owner?.optJSONObject("subscriberCountText")).ifBlank { null },
            subscribed = subscribed,
            commentCount = commentCount(root),
            commentsToken = commentsToken(root)
        )
    }

    private fun reaction(root: JSONObject): Reaction {
        // Newer layouts carry a likeStatus ("LIKE" / "DISLIKE" / "INDIFFERENT").
        val status = findString(root, "likeStatus")?.uppercase()
        if (status != null) {
            return when (status) {
                "LIKE" -> Reaction.Liked
                "DISLIKE" -> Reaction.Disliked
                else -> Reaction.None
            }
        }
        if (findBoolean(findObject(root, "likeButtonViewModel"), "isToggled") == true) return Reaction.Liked
        if (findBoolean(findObject(root, "dislikeButtonViewModel"), "isToggled") == true) return Reaction.Disliked

        // The older layout.
        val seg = findObject(root, "segmentedLikeDislikeButtonRenderer")
        if (seg?.optJSONObject("likeButton")?.optJSONObject("toggleButtonRenderer")
                ?.optBoolean("isToggled") == true
        ) return Reaction.Liked
        if (seg?.optJSONObject("dislikeButton")?.optJSONObject("toggleButtonRenderer")
                ?.optBoolean("isToggled") == true
        ) return Reaction.Disliked
        return Reaction.None
    }

    private fun likeCount(root: JSONObject): String? {
        val vm = findObject(root, "likeButtonViewModel")
        val title = findObject(vm, "buttonViewModel")?.optString("title")
        if (!title.isNullOrBlank() && title.any { it.isDigit() }) return title

        val toggle = findObject(root, "segmentedLikeDislikeButtonRenderer")
            ?.optJSONObject("likeButton")?.optJSONObject("toggleButtonRenderer")
        val old = ytText(toggle?.optJSONObject("defaultText"))
        return old.takeIf { it.isNotBlank() && it.any { c -> c.isDigit() } }
    }

    private fun commentCount(root: JSONObject): String? {
        val entry = findObject(root, "commentsEntryPointHeaderRenderer")
        val text = ytText(entry?.optJSONObject("commentCount"))
        return text.takeIf { it.isNotBlank() }
    }

    /** The token that loads the first page of comments (the comment section's "load" stub). */
    private fun commentsToken(root: JSONObject): String? {
        val section = findAllObjects(root, "itemSectionRenderer")
            .firstOrNull { it.optString("sectionIdentifier") == "comment-item-section" }
        tokenOf(section)?.let { return it }

        val panel = findAllObjects(root, "engagementPanelSectionListRenderer")
            .firstOrNull { it.optString("panelIdentifier") == "engagement-panel-comments-section" }
        return tokenOf(panel)
    }
}
