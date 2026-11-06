package com.playmate.app.data

import org.json.JSONObject

/** The four calls a comment's like and dislike buttons make, plus the one that starts a reply. */
data class CommentActions(
    val like: String? = null,
    val unlike: String? = null,
    val dislike: String? = null,
    val undislike: String? = null,
    /** Opaque value that says which comment a reply belongs to. */
    val replyParams: String? = null
)

data class CommentItem(
    val id: String,
    val author: String,
    val avatar: String?,
    val text: String,
    val published: String,
    /** The like count as YouTube prints it when the account has NOT liked the comment (null: none). */
    val likes: String?,
    /** Reply count as YouTube prints it, or null when there are no replies. */
    val replies: String?,
    /** Token that loads this comment's replies, or null. */
    val repliesToken: String?,
    val isReply: Boolean = false,
    val pinned: Boolean = false,
    /** The count YouTube prints when the account HAS liked it (null: not given, count it ourselves). */
    val likesWhenLiked: String? = null,
    /** What the signed-in account has already done with this comment. */
    val liked: Boolean = false,
    val disliked: Boolean = false,
    val actions: CommentActions = CommentActions(),
    /** Hearted by the video's creator. */
    val hearted: Boolean = false
)

class SortOption(val title: String, val token: String, val selected: Boolean)

class CommentPage(
    val items: List<CommentItem>,
    val next: String?,
    /** Total count, only on the first page. */
    val total: String?,
    val sorts: List<SortOption>,
    /** Opaque value needed to post a new comment, only on the first page. */
    val createParams: String?
)

/**
 * Reads a comments answer. YouTube moved comments from commentRenderer to a view model whose
 * text lives in a separate "entity" list; both layouts are understood. The like / dislike / reply
 * calls are read out of the same answer, so a tap can be sent back exactly as the site would send it.
 */
object CommentParser {

    /** Every entity of the answer, by its key: the whole payload, still wrapped. */
    private class Entities(
        val comments: Map<String, JSONObject>,
        val others: Map<String, JSONObject>
    )

    fun parse(root: JSONObject, asReplies: Boolean = false): CommentPage {
        val entities = entities(root)
        val items = mutableListOf<CommentItem>()
        var next: String? = null

        for (batch in findAllArrays(root, "continuationItems")) {
            for (i in 0 until batch.length()) {
                val item = batch.optJSONObject(i) ?: continue
                val thread = item.optJSONObject("commentThreadRenderer")
                val legacy = item.optJSONObject("commentRenderer")
                val modern = item.optJSONObject("commentViewModel")
                val more = item.optJSONObject("continuationItemRenderer")
                when {
                    thread != null -> fromThread(thread, entities, asReplies)?.let { items += it }
                    legacy != null -> fromLegacy(legacy, null, asReplies)?.let { items += it }
                    modern != null -> fromModern(unwrap(modern), null, entities, asReplies)?.let { items += it }
                    more != null -> next = tokenOf(more) ?: next
                }
            }
        }

        val header = findObject(root, "commentsHeaderRenderer")
        val count = header?.let {
            ytText(it.optJSONObject("countText")).ifBlank { ytText(it.optJSONObject("commentsCount")) }
        }?.let { Regex("[\\d.,]+\\s*[KMB]?").find(it)?.value?.trim() }

        val sorts = mutableListOf<SortOption>()
        val menu = findObject(header, "sortFilterSubMenuRenderer")?.optJSONArray("subMenuItems")
        if (menu != null) {
            for (i in 0 until menu.length()) {
                val entry = menu.optJSONObject(i) ?: continue
                val token = tokenOf(entry) ?: continue
                sorts += SortOption(entry.optString("title"), token, entry.optBoolean("selected"))
            }
        }

        return CommentPage(items, next, count, sorts, findString(header, "createCommentParams"))
    }

    // ---- entity payloads (newer layout) ----

    private fun entities(root: JSONObject): Entities {
        val comments = HashMap<String, JSONObject>()
        val others = HashMap<String, JSONObject>()
        val mutations = root.optJSONObject("frameworkUpdates")
            ?.optJSONObject("entityBatchUpdate")
            ?.optJSONArray("mutations") ?: return Entities(comments, others)
        for (i in 0 until mutations.length()) {
            val mutation = mutations.optJSONObject(i) ?: continue
            val wrapper = mutation.optJSONObject("payload") ?: continue
            val entityKey = mutation.optString("entityKey")
            val payload = wrapper.optJSONObject("commentEntityPayload")
            if (payload != null) {
                if (entityKey.isNotBlank()) comments[entityKey] = payload
                val key = payload.optString("key")
                if (key.isNotBlank()) comments[key] = payload
            } else if (entityKey.isNotBlank()) {
                others[entityKey] = wrapper
            }
        }
        return Entities(comments, others)
    }

    /** The view model object that actually carries commentKey, however deeply it is wrapped. */
    private fun unwrap(o: JSONObject): JSONObject {
        var current = o
        var depth = 0
        while (depth < 3) {
            current = current.optJSONObject("commentViewModel") ?: return current
            depth++
        }
        return current
    }

    // ---- one thread ----

    private fun fromThread(thread: JSONObject, entities: Entities, asReply: Boolean): CommentItem? {
        val repliesToken = tokenOf(thread.optJSONObject("replies"))
        val modern = thread.optJSONObject("commentViewModel")
        if (modern != null) return fromModern(unwrap(modern), repliesToken, entities, asReply)
        val legacy = thread.optJSONObject("comment")?.optJSONObject("commentRenderer") ?: return null
        return fromLegacy(legacy, repliesToken, asReply)
    }

    private fun fromModern(
        vm: JSONObject,
        repliesToken: String?,
        entities: Entities,
        asReply: Boolean
    ): CommentItem? {
        val payload = entities.comments[vm.optString("commentKey")] ?: return null
        val props = payload.optJSONObject("properties")
        val text = props?.optJSONObject("content")?.optString("content").orEmpty()
        if (text.isBlank()) return null
        val id = props?.optString("commentId").orEmpty().ifBlank { vm.optString("commentId") }
        val author = payload.optJSONObject("author")
        val avatar = author?.optString("avatarThumbnailUrl")?.takeIf { it.isNotBlank() }
            ?: lastImageUrl(payload.optJSONObject("avatar")?.optJSONObject("image")?.optJSONArray("sources"))
        val toolbar = payload.optJSONObject("toolbar")
        val replies = toolbar?.optString("replyCount")?.takeIf { it.isNotBlank() && it != "0" }

        // The account's own state (liked, disliked, hearted) and the calls behind the buttons.
        val state = entities.others[vm.optString("toolbarStateKey")]?.let { unwrapPayload(it, "likeState") }
        val likeState = state?.optString("likeState").orEmpty()
        val surface = entities.others[vm.optString("toolbarSurfaceKey")]?.let { unwrapPayload(it, "likeCommand") }

        return CommentItem(
            id = id.ifBlank { "c${text.hashCode()}" },
            author = author?.optString("displayName").orEmpty(),
            avatar = avatar,
            text = text,
            published = props?.optString("publishedTime").orEmpty(),
            likes = toolbar?.optString("likeCountNotliked")?.takeIf { it.isNotBlank() && it != "0" },
            replies = replies ?: if (repliesToken != null) "" else null,
            repliesToken = repliesToken,
            isReply = asReply,
            pinned = vm.has("pinnedText"),
            likesWhenLiked = toolbar?.optString("likeCountLiked")?.takeIf { it.isNotBlank() && it != "0" },
            liked = likeState == "TOOLBAR_LIKE_STATE_LIKED",
            disliked = likeState == "TOOLBAR_LIKE_STATE_DISLIKED",
            actions = surface?.let { fromSurface(it) } ?: CommentActions(),
            hearted = state?.optString("heartState") == "TOOLBAR_HEART_STATE_HEARTED"
        )
    }

    /**
     * An entity arrives as {"somethingEntityPayload": {...}}; this is the inner object, found by a
     * field it is known to carry ([marker]) so it works whatever the wrapper is called.
     */
    private fun unwrapPayload(wrapper: JSONObject, marker: String): JSONObject {
        if (wrapper.has(marker)) return wrapper
        var firstObject: JSONObject? = null
        val keys = wrapper.keys()
        while (keys.hasNext()) {
            val child = wrapper.optJSONObject(keys.next()) ?: continue
            if (child.has(marker)) return child
            if (firstObject == null) firstObject = child
        }
        return firstObject ?: wrapper
    }

    /** The toolbar surface lists one command per button; each hides its call a few levels down. */
    private fun fromSurface(surface: JSONObject): CommentActions = CommentActions(
        like = actionIn(surface.optJSONObject("likeCommand")),
        unlike = actionIn(surface.optJSONObject("unlikeCommand")),
        dislike = actionIn(surface.optJSONObject("dislikeCommand")),
        undislike = actionIn(surface.optJSONObject("undislikeCommand")),
        replyParams = findString(surface.optJSONObject("replyCommand"), "createReplyParams")
    )

    /** The encoded action of a performCommentActionEndpoint found anywhere inside [command]. */
    private fun actionIn(command: JSONObject?): String? {
        val endpoint = findObject(command, "performCommentActionEndpoint") ?: return null
        endpoint.optString("action").takeIf { it.isNotBlank() }?.let { return it }
        val list = endpoint.optJSONArray("actions") ?: return null
        return list.optString(0).takeIf { it.isNotBlank() }
    }

    private fun fromLegacy(r: JSONObject, repliesToken: String?, asReply: Boolean): CommentItem? {
        val text = ytText(r.optJSONObject("contentText"))
        if (text.isBlank()) return null
        val replyCount = r.optInt("replyCount", 0)
        val likes = ytText(r.optJSONObject("voteCount"))
            .ifBlank { r.optInt("likeCount", 0).takeIf { it > 0 }?.toString().orEmpty() }

        val buttons = r.optJSONObject("actionButtons")?.optJSONObject("commentActionButtonsRenderer")
        val likeToggle = buttons?.optJSONObject("likeButton")?.optJSONObject("toggleButtonRenderer")
        val dislikeToggle = buttons?.optJSONObject("dislikeButton")?.optJSONObject("toggleButtonRenderer")
        val liked = likeToggle?.optBoolean("isToggled") == true || r.optString("voteStatus") == "LIKE"
        val disliked = dislikeToggle?.optBoolean("isToggled") == true || r.optString("voteStatus") == "DISLIKE"
        val actions = CommentActions(
            like = actionIn(likeToggle?.optJSONObject("defaultServiceEndpoint")),
            unlike = actionIn(likeToggle?.optJSONObject("toggledServiceEndpoint")),
            dislike = actionIn(dislikeToggle?.optJSONObject("defaultServiceEndpoint")),
            undislike = actionIn(dislikeToggle?.optJSONObject("toggledServiceEndpoint")),
            replyParams = findString(buttons?.optJSONObject("replyButton"), "createReplyParams")
        )

        // In the older layout the printed count already includes the account's own like.
        val printed = likes.ifBlank { null }
        return CommentItem(
            id = r.optString("commentId").ifBlank { "c${text.hashCode()}" },
            author = ytText(r.optJSONObject("authorText")),
            avatar = lastImageUrl(r.optJSONObject("authorThumbnail")?.optJSONArray("thumbnails")),
            text = text,
            published = ytText(r.optJSONObject("publishedTimeText")),
            likes = if (liked) uncounted(printed) else printed,
            replies = if (replyCount > 0) replyCount.toString() else if (repliesToken != null) "" else null,
            repliesToken = repliesToken,
            isReply = asReply,
            pinned = r.has("pinnedCommentBadge"),
            likesWhenLiked = if (liked) printed else null,
            liked = liked,
            disliked = disliked,
            actions = actions,
            hearted = findBoolean(buttons?.optJSONObject("creatorHeart"), "isHearted") == true
        )
    }

    /** The same count with the account's own like taken off; only a plain number can be. */
    private fun uncounted(text: String?): String? {
        val n = text?.replace(",", "")?.toLongOrNull() ?: return text
        return (n - 1).takeIf { it > 0 }?.toString()
    }
}
