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
    val live: Boolean = false
)

class Quality(
    val label: String,
    val height: Int,
    val video: VideoStream,
    val audio: AudioStream?
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
    val related: List<VideoItem>
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
