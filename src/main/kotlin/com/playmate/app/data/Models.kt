package com.playmate.app.data

import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.VideoStream

data class VideoItem(
    val url: String,
    val title: String,
    val channel: String,
    val durationSec: Long,
    val views: Long,
    val thumb: String?,
    val uploaded: String?,
    val live: Boolean = false,
    /** Round channel picture shown next to the title, when the feed carries one. */
    val avatar: String? = null
) {
    /** Short-form video (vertical): shown in the Shorts shelf, not as a normal card. */
    val isShort: Boolean get() = url.contains("/shorts/")
}

/** A real topic chip from YouTube's own home feed (All, Music, ...). [token] loads that topic. */
data class FeedChip(val label: String, val token: String?, val selected: Boolean)

class Quality(
    val label: String,
    val height: Int,
    val video: VideoStream,
    val audio: AudioStream?
)

/** One caption file of a video: [mime] is "text/vtt" or "application/ttml+xml". */
class SubtitleTrack(
    val url: String,
    val mime: String,
    val language: String,
    val label: String,
    val auto: Boolean
)

class PlayableInfo(
    val url: String,
    val title: String,
    val channel: String,
    val views: Long,
    val uploaded: String?,
    val description: String,
    val thumb: String?,
    val live: Boolean,
    val hlsUrl: String?,
    val qualities: List<Quality>,
    val audioOnly: AudioStream?,
    val related: List<VideoItem>,
    val channelId: String? = null,
    val channelAvatar: String? = null,
    val audios: List<AudioStream> = emptyList(),
    /** Set when this is a download kept inside the app: the file to play, no internet needed. */
    val localUri: String? = null,
    /** Caption files (creator-made and auto-generated), one per language. */
    val subtitles: List<SubtitleTrack> = emptyList(),
    /** Every video stream that already has sound inside (all codecs), for the save sheets. */
    val videoStreams: List<VideoStream> = emptyList(),
    /** Every video-only stream (all codecs and qualities), joined with an audio stream when saving. */
    val videoOnlyStreams: List<VideoStream> = emptyList()
)

class SearchPage(
    val items: List<VideoItem>,
    val next: org.schabi.newpipe.extractor.Page?,
    val handler: org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler
)

sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ready<T>(val data: T) : Load<T>
    data class Failed(val message: String) : Load<Nothing>
}

data class Playing(val seed: VideoItem, val info: Load<PlayableInfo>)
