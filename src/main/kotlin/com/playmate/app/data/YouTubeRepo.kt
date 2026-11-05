package com.playmate.app.data

import android.os.SystemClock
import androidx.core.text.HtmlCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

object YouTubeRepo {

    private val service get() = ServiceList.YouTube

    suspend fun trending(): List<VideoItem> = withContext(Dispatchers.IO) {
        val fromKiosk = attempt {
            val extractor = service.kioskList.defaultKioskExtractor
            extractor.fetchPage()
            extractor.initialPage.items.toVideos()
        }.getOrNull()

        if (!fromKiosk.isNullOrEmpty()) fromKiosk else search("trending today").items
    }

    suspend fun suggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        service.suggestionExtractor.suggestionList(query)
    }

    suspend fun search(query: String): SearchPage = withContext(Dispatchers.IO) {
        val handler = service.searchQHFactory.fromQuery(query, listOf("videos"), "")
        val info = SearchInfo.getInfo(service, handler)
        SearchPage(info.relatedItems.toVideos(), info.nextPage, handler)
    }

    suspend fun more(page: SearchPage): SearchPage = withContext(Dispatchers.IO) {
        val next = page.next ?: return@withContext page
        val result = SearchInfo.getMoreItems(service, page.handler, next)
        val known = page.items.map { it.url }.toHashSet()
        val fresh = result.items.toVideos().filter { it.url !in known }
        SearchPage(page.items + fresh, result.nextPage, page.handler)
    }

    // ------------------------------------------------------------------ stream cache

    private class Cached(val info: PlayableInfo, val at: Long)

    private val cacheLock = Any()

    /** Recently looked-up streams, so opening a video (or swiping back to a Short) needs no new lookup. */
    private val cache = object : LinkedHashMap<String, Cached>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Cached>?): Boolean =
            size > CACHE_SIZE
    }

    /** Lookups in progress, shared: two callers asking for the same link wait for one lookup. */
    private val pending = HashMap<String, Deferred<PlayableInfo>>()

    /** The lookups run in their own scope, so a caller that stops waiting does not throw the work away. */
    private val lookups = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private const val CACHE_SIZE = 48

    /** Stream links stay valid for hours; this is far inside that. */
    private const val CACHE_TTL_MS = 20 * 60 * 1000L

    /** Starts a lookup in the background so a later [load] of the same link is instant. Failures are ignored. */
    fun prefetch(url: String) {
        lookups.launch { runCatching { load(url) } }
    }

    /** Forgets a link's streams, so the next [load] asks again (used when a stored link stopped working). */
    fun invalidate(url: String) {
        synchronized(cacheLock) { cache.remove(url) }
    }

    suspend fun load(url: String): PlayableInfo {
        synchronized(cacheLock) {
            val hit = cache[url]
            if (hit != null) {
                if (SystemClock.elapsedRealtime() - hit.at < CACHE_TTL_MS) return hit.info
                cache.remove(url)
            }
        }
        val shared: Pair<Deferred<PlayableInfo>, Boolean> = synchronized(cacheLock) {
            val running = pending[url]
            if (running != null) {
                running to false
            } else {
                val lookup = lookups.async(start = CoroutineStart.LAZY) {
                    try {
                        fetch(url).also { found ->
                            synchronized(cacheLock) { cache[url] = Cached(found, SystemClock.elapsedRealtime()) }
                        }
                    } finally {
                        synchronized(cacheLock) { pending.remove(url) }
                    }
                }
                pending[url] = lookup
                lookup to true
            }
        }
        if (shared.second) shared.first.start()
        return shared.first.await()
    }

    private suspend fun fetch(url: String): PlayableInfo = withContext(Dispatchers.IO) {
        val info = StreamInfo.getInfo(service, url)
        val progressive = DeliveryMethod.PROGRESSIVE_HTTP

        val audios = info.audioStreams.filter { it.deliveryMethod == progressive && it.isUrl }
        val bestM4a = audios.filter { it.format == MediaFormat.M4A }.maxByOrNull { it.averageBitrate }
        val bestWebm = audios.filter { it.format == MediaFormat.WEBMA }.maxByOrNull { it.averageBitrate }
        val bestAny = audios.maxByOrNull { it.averageBitrate }

        val options = mutableListOf<Pair<Int, Quality>>()

        val withSound = info.videoStreams
            .filter { it.deliveryMethod == progressive && it.isUrl && !it.isVideoOnly }
        val videoOnly = info.videoOnlyStreams
            .filter { it.deliveryMethod == progressive && it.isUrl }

        withSound.forEach { v ->
            val h = heightOf(v.resolution)
            if (h > 0) options += 0 to Quality(v.resolution, h, v, null)
        }

        videoOnly.forEach { v ->
            val h = heightOf(v.resolution)
            val a = if (v.format == MediaFormat.WEBM) bestWebm ?: bestAny else bestM4a ?: bestAny
            if (h > 0 && a != null) {
                val rank = if (v.format == MediaFormat.MPEG_4) 1 else 2
                options += rank to Quality(v.resolution, h, v, a)
            }
        }

        val qualities = options
            .groupBy { it.second.height }
            .map { (_, group) -> group.minByOrNull { it.first }!!.second }
            .sortedByDescending { it.height }

        val live = info.streamType == StreamType.LIVE_STREAM ||
            info.streamType == StreamType.AUDIO_LIVE_STREAM

        val desc = info.description
        val text = when {
            desc == null -> ""
            desc.type == Description.HTML ->
                HtmlCompat.fromHtml(desc.content, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()
            else -> desc.content.trim()
        }

        // One caption file per language (and kind); WebVTT is preferred, TTML is the fallback.
        val subtitles = attempt {
            info.subtitles
                .filter { it.isUrl && !it.content.isNullOrBlank() }
                .mapNotNull { s ->
                    val mime = when (s.format) {
                        MediaFormat.VTT -> "text/vtt"
                        MediaFormat.TTML -> "application/ttml+xml"
                        else -> null
                    } ?: return@mapNotNull null
                    val lang = s.languageTag.orEmpty()
                    SubtitleTrack(
                        url = s.content,
                        mime = mime,
                        language = lang,
                        label = s.displayLanguageName?.takeIf { it.isNotBlank() } ?: lang.ifBlank { "Captions" },
                        auto = s.isAutoGenerated
                    )
                }
                .groupBy { it.language to it.auto }
                .map { (_, group) -> group.firstOrNull { it.mime == "text/vtt" } ?: group.first() }
        }.getOrDefault(emptyList())

        PlayableInfo(
            url = url,
            title = info.name,
            channel = info.uploaderName ?: "",
            views = info.viewCount,
            uploaded = info.textualUploadDate,
            description = text,
            thumb = info.thumbnails.pick(),
            live = live,
            hlsUrl = info.hlsUrl?.takeIf { it.isNotBlank() },
            qualities = qualities,
            audioOnly = bestM4a ?: bestAny,
            audios = audios,
            related = info.relatedItems.toVideos(),
            channelId = Regex("/channel/(UC[A-Za-z0-9_-]{22})").find(info.uploaderUrl.orEmpty())
                ?.groupValues?.get(1),
            channelAvatar = info.uploaderAvatars.pick(),
            subtitles = subtitles,
            videoStreams = withSound,
            videoOnlyStreams = videoOnly
        )
    }

    private fun heightOf(resolution: String?): Int =
        Regex("(\\d+)p").find(resolution ?: "")?.groupValues?.get(1)?.toIntOrNull() ?: 0
}

private fun List<InfoItem>.toVideos(): List<VideoItem> =
    filterIsInstance<StreamInfoItem>().map {
        VideoItem(
            url = it.url,
            title = it.name,
            channel = it.uploaderName ?: "",
            durationSec = it.duration,
            views = it.viewCount,
            thumb = it.thumbnails.pick(),
            uploaded = it.textualUploadDate,
            live = it.streamType == StreamType.LIVE_STREAM
        )
    }

private fun List<org.schabi.newpipe.extractor.Image>.pick(): String? {
    if (isEmpty()) return null
    val sorted = sortedBy { it.height }
    return (sorted.firstOrNull { it.height >= 360 } ?: sorted.last()).url
}
