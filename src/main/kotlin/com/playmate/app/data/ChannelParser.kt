package com.playmate.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class ChannelTab(val title: String, val params: String?, val selected: Boolean)

class ChannelInfo(
    val id: String,
    val name: String,
    val handle: String?,
    val avatar: String?,
    val subscribers: String?,
    val videoCount: String?,
    val description: String?,
    val subscribed: Boolean?,
    val tabs: List<ChannelTab>
)

class ChannelData(val info: ChannelInfo, val page: FeedPage)

object ChannelRepo {

    /** The channel's first page: its header and the videos of the tab YouTube opens by default. */
    suspend fun open(id: String): ChannelData {
        val root = InnerTube.browse(id)
        return withContext(Dispatchers.Default) {
            ChannelData(ChannelParser.info(id, root), FeedParser.parse(root))
        }
    }

    /** The videos of another tab ([params] comes from [ChannelTab.params]). */
    suspend fun tab(id: String, params: String): FeedPage {
        val root = InnerTube.browse(id, params)
        return withContext(Dispatchers.Default) { FeedParser.parse(root) }
    }
}

object ChannelParser {

    /** Tabs worth showing: the ones whose content is made of videos. */
    private val VIDEO_TABS = setOf("home", "videos", "shorts", "live")

    fun info(id: String, root: JSONObject): ChannelInfo {
        val meta = root.optJSONObject("metadata")?.optJSONObject("channelMetadataRenderer")
        val header = findObject(root, "c4TabbedHeaderRenderer")
            ?: findObject(root, "pageHeaderRenderer")
            ?: findObject(root, "channelHeaderRenderer")

        val name = meta?.optString("title").orEmpty()
            .ifBlank { header?.optString("title").orEmpty() }
            .ifBlank { ytText(header?.optJSONObject("title")) }
            .ifBlank { header?.optString("pageTitle").orEmpty() }
        if (name.isBlank()) throw IOException("the channel page had no data")

        val avatar = lastImageUrl(meta?.optJSONObject("avatar")?.optJSONArray("thumbnails"))
            ?: lastImageUrl(header?.optJSONObject("avatar")?.optJSONArray("thumbnails"))
            ?: findAllArrays(header, "sources").firstNotNullOfOrNull { lastImageUrl(it) }

        val lines = strings(header)
        val handle = meta?.optString("vanityChannelUrl")?.substringAfterLast("/")?.takeIf { it.startsWith("@") }
            ?: lines.firstOrNull { it.startsWith("@") && !it.contains(' ') && it.length < 40 }
        val subscribers = lines.filter { it.contains("subscriber", ignoreCase = true) && it.length < 40 }
            .minByOrNull { it.length }
        val videos = lines.filter {
            Regex("\\d\\s*videos?$", RegexOption.IGNORE_CASE).containsMatchIn(it) && it.length < 30
        }.minByOrNull { it.length }

        val subscribed = findBoolean(findObject(header, "subscribeButtonRenderer"), "subscribed")
            ?: findBoolean(findObject(root, "subscriptionStateEntity"), "subscribed")

        return ChannelInfo(
            id = id,
            name = name,
            handle = handle,
            avatar = avatar,
            subscribers = subscribers,
            videoCount = videos,
            description = meta?.optString("description")?.trim()?.takeIf { it.isNotBlank() },
            subscribed = subscribed,
            tabs = tabs(root)
        )
    }

    private fun tabs(root: JSONObject): List<ChannelTab> {
        val all = findAllObjects(root, "tabRenderer").mapNotNull { t ->
            val title = t.optString("title")
            if (title.isBlank()) return@mapNotNull null
            val params = findObject(t.optJSONObject("endpoint"), "browseEndpoint")
                ?.optString("params")?.takeIf { it.isNotBlank() }
            ChannelTab(title, params, t.optBoolean("selected"))
        }
        return all.filter { it.title.lowercase() in VIDEO_TABS }
    }

    /** Every string inside [node], so the header can be searched without knowing its layout. */
    private fun strings(node: Any?): List<String> {
        val out = mutableListOf<String>()
        fun walk(n: Any?) {
            when (n) {
                is String -> if (n.isNotBlank()) out += n.trim()
                is JSONObject -> {
                    val keys = n.keys()
                    while (keys.hasNext()) walk(n.opt(keys.next()))
                }
                is JSONArray -> for (i in 0 until n.length()) walk(n.opt(i))
            }
        }
        walk(node)
        return out
    }
}
