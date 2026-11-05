package com.playmate.app.ui.player

import android.content.Context
import android.net.ConnectivityManager
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
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

/**
 * How much is read ahead before playback may start. Playback begins after a quarter of a second of
 * video for Shorts and under a second for normal videos (the default waits 2.5 s), and keeps a modest
 * buffer so a swipe through many Shorts does not download far more than is watched.
 */
private fun loadControlFor(forShorts: Boolean): DefaultLoadControl =
    DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            if (forShorts) 4_000 else 15_000,
            if (forShorts) 15_000 else 50_000,
            if (forShorts) 250 else 800,
            if (forShorts) 1_000 else 2_000
        )
        .setPrioritizeTimeOverSizeThresholds(true)
        .build()

fun createPlayer(context: Context, forShorts: Boolean = false): ExoPlayer =
    ExoPlayer.Builder(context)
        .setLoadControl(loadControlFor(forShorts))
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

/**
 * The tallest picture worth fetching on the current connection: more on Wi-Fi, less on mobile data
 * (a smaller picture starts sooner and costs less). The quality picker can still go higher.
 */
fun preferredMaxHeight(context: Context, shorts: Boolean): Int {
    val metered = runCatching {
        val manager = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        manager.isActiveNetworkMetered
    }.getOrDefault(false)
    return when {
        shorts && metered -> 480
        shorts -> 720
        metered -> 720
        else -> 1080
    }
}

fun defaultQualityIndex(info: PlayableInfo, maxHeight: Int = 720): Int {
    if (info.qualities.isEmpty()) return -1
    val comfortable = info.qualities.indexOfFirst { it.height <= maxHeight }
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
