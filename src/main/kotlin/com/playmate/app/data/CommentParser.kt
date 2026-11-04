package com.playmate.app.data

import org.json.JSONObject

data class CommentItem(
    val id: String,
    val author: String,
    val avatar: String?,
    val text: String,
    val published: String,
    val likes: String?,
    /** Reply count as YouTube prints it, or null when there are no replies. */
    val replies: String?,
    /** Token that loads this comment's replies, or null. */
    val repliesToken: String?,
    val isReply: Boolean = false,
    val pinned: Boolean = false
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
 * text lives in a separate "entity" list; both layouts are understood.
 */
object CommentParser {

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

    private fun entities(root: JSONObject): Map<String, JSONObject> {
        val map = HashMap<String, JSONObject>()
        val mutations = root.optJSONObject("frameworkUpdates")
            ?.optJSONObject("entityBatchUpdate")
            ?.optJSONArray("mutations") ?: return map
        for (i in 0 until mutations.length()) {
            val mutation = mutations.optJSONObject(i) ?: continue
            val payload = mutation.optJSONObject("payload")?.optJSONObject("commentEntityPayload") ?: continue
            val entityKey = mutation.optString("entityKey")
            if (entityKey.isNotBlank()) map[entityKey] = payload
            val key = payload.optString("key")
            if (key.isNotBlank()) map[key] = payload
        }
        return map
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

    private fun fromThread(thread: JSONObject, entities: Map<String, JSONObject>, asReply: Boolean): CommentItem? {
        val repliesToken = tokenOf(thread.optJSONObject("replies"))
        val modern = thread.optJSONObject("commentViewModel")
        if (modern != null) return fromModern(unwrap(modern), repliesToken, entities, asReply)
        val legacy = thread.optJSONObject("comment")?.optJSONObject("commentRenderer") ?: return null
        return fromLegacy(legacy, repliesToken, asReply)
    }

    private fun fromModern(
        vm: JSONObject,
        repliesToken: String?,
        entities: Map<String, JSONObject>,
        asReply: Boolean
    ): CommentItem? {
        val payload = entities[vm.optString("commentKey")] ?: return null
        val props = payload.optJSONObject("properties")
        val text = props?.optJSONObject("content")?.optString("content").orEmpty()
        if (text.isBlank()) return null
        val id = props?.optString("commentId").orEmpty().ifBlank { vm.optString("commentId") }
        val author = payload.optJSONObject("author")
        val avatar = author?.optString("avatarThumbnailUrl")?.takeIf { it.isNotBlank() }
            ?: lastImageUrl(payload.optJSONObject("avatar")?.optJSONObject("image")?.optJSONArray("sources"))
        val toolbar = payload.optJSONObject("toolbar")
        val replies = toolbar?.optString("replyCount")?.takeIf { it.isNotBlank() && it != "0" }
        return CommentItem(
            id = id.ifBlank { "c${text.hashCode()}" },
            author = author?.optString("displayName").orEmpty(),
            avatar = avatar,
            text = text,
            published = props?.optString("publishedTime").orEmpty(),
            likes = toolbar?.optString("likeCountNotliked")?.takeIf { it.isNotBlank() && it != "0" },
            replies = replies ?: if (repliesToken != null) "" else null,
            repliesToken = repliesToken,
            isReply = asReply
        )
    }

    private fun fromLegacy(r: JSONObject, repliesToken: String?, asReply: Boolean): CommentItem? {
        val text = ytText(r.optJSONObject("contentText"))
        if (text.isBlank()) return null
        val replyCount = r.optInt("replyCount", 0)
        val likes = ytText(r.optJSONObject("voteCount"))
            .ifBlank { r.optInt("likeCount", 0).takeIf { it > 0 }?.toString().orEmpty() }
        return CommentItem(
            id = r.optString("commentId").ifBlank { "c${text.hashCode()}" },
            author = ytText(r.optJSONObject("authorText")),
            avatar = lastImageUrl(r.optJSONObject("authorThumbnail")?.optJSONArray("thumbnails")),
            text = text,
            published = ytText(r.optJSONObject("publishedTimeText")),
            likes = likes.ifBlank { null },
            replies = if (replyCount > 0) replyCount.toString() else if (repliesToken != null) "" else null,
            repliesToken = repliesToken,
            isReply = asReply,
            pinned = r.has("pinnedCommentBadge")
        )
    }
}
