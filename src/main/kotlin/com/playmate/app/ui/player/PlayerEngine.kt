package com.playmate.app.ui.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.playmate.app.data.PlayableInfo
import com.playmate.app.data.PmDownloader

fun createPlayer(context: Context): ExoPlayer =
    ExoPlayer.Builder(context)
        .setHandleAudioBecomingNoisy(true)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            true
        )
        .build()

fun defaultQualityIndex(info: PlayableInfo): Int {
    if (info.qualities.isEmpty()) return -1
    val comfortable = info.qualities.indexOfFirst { it.height <= 720 }
    return if (comfortable >= 0) comfortable else info.qualities.lastIndex
}

fun buildSource(info: PlayableInfo, qualityIndex: Int): MediaSource? {
    val http = OkHttpDataSource.Factory(PmDownloader.client)
        .setUserAgent(PmDownloader.USER_AGENT)

    if (info.live && info.hlsUrl != null) {
        return HlsMediaSource.Factory(http).createMediaSource(MediaItem.fromUri(info.hlsUrl))
    }

    val quality = info.qualities.getOrNull(qualityIndex) ?: return null
    val progressive = ProgressiveMediaSource.Factory(http)
    fun source(url: String) = progressive.createMediaSource(MediaItem.fromUri(url))

    val video = source(quality.video.content)
    val audio = quality.audio
    return if (audio != null) MergingMediaSource(video, source(audio.content)) else video
}
