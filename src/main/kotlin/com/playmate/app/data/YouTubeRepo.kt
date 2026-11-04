package com.playmate.app.data

import androidx.core.text.HtmlCompat
import kotlinx.coroutines.Dispatchers
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

    suspend fun load(url: String): PlayableInfo = withContext(Dispatchers.IO) {
        val info = StreamInfo.getInfo(service, url)
        val progressive = DeliveryMethod.PROGRESSIVE_HTTP

        val audios = info.audioStreams.filter { it.deliveryMethod == progressive && it.isUrl }
        val bestM4a = audios.filter { it.format == MediaFormat.M4A }.maxByOrNull { it.averageBitrate }
        val bestWebm = audios.filter { it.format == MediaFormat.WEBMA }.maxByOrNull { it.averageBitrate }
        val bestAny = audios.maxByOrNull { it.averageBitrate }

        val options = mutableListOf<Pair<Int, Quality>>()

        info.videoStreams
            .filter { it.deliveryMethod == progressive && it.isUrl && !it.isVideoOnly }
            .forEach { v ->
                val h = heightOf(v.resolution)
                if (h > 0) options += 0 to Quality(v.resolution, h, v, null)
            }

        info.videoOnlyStreams
            .filter { it.deliveryMethod == progressive && it.isUrl }
            .forEach { v ->
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
            channelAvatar = info.uploaderAvatars.pick()
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
