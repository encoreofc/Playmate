package com.playmate.app.ui.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.playmate.app.data.PlayableInfo
import com.playmate.app.data.PmDownloader

/** One player for the whole app, shared by the UI and the background PlaybackService. */
object PlayerHolder {
    @Volatile
    private var instance: ExoPlayer? = null

    fun get(context: Context): ExoPlayer =
        instance ?: synchronized(this) {
            instance ?: createPlayer(context.applicationContext).also { instance = it }
        }
}

/** Turns the video renderer off in the background (audio keeps playing, saves data). */
fun ExoPlayer.setVideoEnabled(enabled: Boolean) {
    trackSelectionParameters = trackSelectionParameters
        .buildUpon()
        .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, !enabled)
        .build()
}

private fun mediaItemFor(info: PlayableInfo, url: String): MediaItem =
    MediaItem.Builder()
        .setUri(url)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(info.title)
                .setArtist(info.channel)
                .setArtworkUri(info.thumb?.let { Uri.parse(it) })
                .build()
        )
        .build()

fun createPlayer(context: Context): ExoPlayer =
    ExoPlayer.Builder(context)
        .setHandleAudioBecomingNoisy(true)
        .setWakeMode(C.WAKE_MODE_NETWORK)
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
    // A download kept inside the app: play the file from the phone.
    info.localUri?.let { local ->
        return ProgressiveMediaSource.Factory(FileDataSource.Factory())
            .createMediaSource(mediaItemFor(info, local))
    }

    val http = OkHttpDataSource.Factory(PmDownloader.client)
        .setUserAgent(PmDownloader.USER_AGENT)

    if (info.live && info.hlsUrl != null) {
        return HlsMediaSource.Factory(http).createMediaSource(mediaItemFor(info, info.hlsUrl))
    }

    val quality = info.qualities.getOrNull(qualityIndex) ?: return null
    val progressive = ProgressiveMediaSource.Factory(http)
    fun plain(url: String) = progressive.createMediaSource(MediaItem.fromUri(url))

    // The first source carries the title/channel/artwork shown in the notification.
    val video = progressive.createMediaSource(mediaItemFor(info, quality.video.content))
    val audio = quality.audio
    return if (audio != null) MergingMediaSource(video, plain(audio.content)) else video
}
