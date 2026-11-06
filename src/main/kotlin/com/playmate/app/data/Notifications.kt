package com.playmate.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** What a notification is about; decides which tab shows it. */
enum class NotifKind { Comment, Mention, Other }

class NotificationItem(
    val id: String,
    val message: String,
    /** "5 hours ago", as YouTube words it. */
    val time: String,
    /** The same age in hours, to put the notification under Today / This week / ... */
    val ageHours: Long,
    val avatar: String?,
    val thumb: String?,
    val unread: Boolean,
    val kind: NotifKind,
    /** A video or Short to open when tapped, or null. */
    val videoUrl: String?,
    /** A channel to open when tapped and there is no video, or null. */
    val channelId: String?
)

class NotificationPage(val items: List<NotificationItem>, val token: String?)

/** The account's notifications, from the same call youtube.com's bell makes. Unofficial: YouTube can change it. */
object NotificationRepo {

    suspend fun first(): NotificationPage = withContext(Dispatchers.IO) {
        parse(
            InnerTube.post(
                "notification/get_notification_menu",
                JSONObject().put("notificationsMenuRequestType", "NOTIFICATIONS_MENU_REQUEST_TYPE_INBOX")
            )
        )
    }

    suspend fun more(token: String): NotificationPage = withContext(Dispatchers.IO) {
        parse(InnerTube.post("notification/get_notification_menu", JSONObject().put("ctoken", token)))
    }

    private fun parse(root: JSONObject): NotificationPage {
        val items = findAllObjects(root, "notificationRenderer").mapNotNull { n ->
            val message = ytText(n.optJSONObject("shortMessage")).trim()
            if (message.isEmpty()) return@mapNotNull null

            val nav = n.optJSONObject("navigationEndpoint")
            val link = findString(nav, "url").orEmpty()
            val time = ytText(n.optJSONObject("sentTimeText")).trim()
            val lower = message.lowercase()

            val kind = when {
                "mentioned you" in lower -> NotifKind.Mention
                "lc=" in link || "comment" in lower || "replied" in lower -> NotifKind.Comment
                else -> NotifKind.Other
            }

            val videoUrl = Regex("[?&]v=([A-Za-z0-9_-]{11})").find(link)?.let { "https://www.youtube.com/watch?v=${it.groupValues[1]}" }
                ?: Regex("/shorts/([A-Za-z0-9_-]{11})").find(link)?.let { "https://www.youtube.com/shorts/${it.groupValues[1]}" }
                ?: findString(nav, "videoId")?.takeIf { it.length == 11 }?.let { "https://www.youtube.com/watch?v=$it" }

            NotificationItem(
                id = n.optString("notificationId").ifBlank { "$message|$time" },
                message = message,
                time = time,
                ageHours = hoursOf(time),
                avatar = lastImageUrl(n.optJSONObject("thumbnail")?.optJSONArray("thumbnails")),
                thumb = lastImageUrl(n.optJSONObject("videoThumbnail")?.optJSONArray("thumbnails")),
                unread = !n.optBoolean("read", false),
                kind = kind,
                videoUrl = videoUrl,
                channelId = if (videoUrl == null) findString(nav, "browseId") else null
            )
        }.distinctBy { it.id }
        return NotificationPage(items, findString(root, "ctoken"))
    }

    /** "5 hours ago" -> 5, "2 days ago" -> 48. Large when the words are not understood. */
    private fun hoursOf(text: String): Long {
        val m = Regex("(\\d+)\\s*(second|minute|hour|day|week|month|year)", RegexOption.IGNORE_CASE).find(text)
            ?: return 100_000
        val n = m.groupValues[1].toLongOrNull() ?: return 100_000
        return when (m.groupValues[2].lowercase()) {
            "second", "minute" -> 0
            "hour" -> n
            "day" -> n * 24
            "week" -> n * 24 * 7
            "month" -> n * 24 * 30
            else -> n * 24 * 365
        }
    }
}
