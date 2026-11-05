package com.playmate.app.data

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume

/**
 * FFmpeg does the jobs the phone can't: MP3, FLAC and WAV files, a proper .opus file, and joining
 * video and sound in the newer codecs (AV1, H.265) or mixed containers (MKV).
 */
object Ffmpeg {

    /** True only after [warmUp] saw FFmpeg load and its MP3 encoder answer. Until then those formats stay hidden. */
    @Volatile
    var available: Boolean = false
        private set

    /** Checks in the background (the first FFmpeg call loads its native code, which takes a moment). */
    fun warmUp() {
        Thread {
            available = runCatching {
                val session = FFmpegKit.executeWithArguments(arrayOf("-hide_banner", "-encoders"))
                ReturnCode.isSuccess(session.returnCode) &&
                    (session.allLogsAsString ?: "").contains("libmp3lame")
            }.getOrDefault(false)
        }.start()
    }

    /** Runs FFmpeg and waits. Cancelling the coroutine stops FFmpeg too. */
    private suspend fun run(args: List<String>) {
        val result = suspendCancellableCoroutine<Pair<Boolean, String>> { cont ->
            val session = FFmpegKit.executeWithArgumentsAsync(args.toTypedArray()) { done ->
                if (cont.isActive) {
                    val ok = ReturnCode.isSuccess(done.returnCode)
                    cont.resume(ok to (done.failStackTrace ?: done.allLogsAsString ?: ""))
                }
            }
            cont.invokeOnCancellation { FFmpegKit.cancel(session.sessionId) }
        }
        if (!result.first) throw IOException("FFmpeg failed: " + result.second.takeLast(300))
    }

    /** Turns a downloaded sound stream into [format]. MP3 uses [kbps]; the others ignore it. */
    suspend fun convertAudio(src: File, out: File, format: AudioFormat, kbps: Int) {
        val codec = when (format) {
            AudioFormat.MP3 -> listOf("-c:a", "libmp3lame", "-b:a", "${if (kbps > 0) kbps else 192}k", "-f", "mp3")
            AudioFormat.FLAC -> listOf("-c:a", "flac", "-f", "flac")
            AudioFormat.WAV -> listOf("-c:a", "pcm_s16le", "-f", "wav")
            AudioFormat.OPUS -> listOf("-c:a", "copy", "-f", "opus")
            AudioFormat.M4A -> listOf("-c:a", "copy", "-f", "mp4")
        }
        val args = mutableListOf("-y", "-loglevel", "error", "-i", src.absolutePath, "-vn")
        args += codec
        args += out.absolutePath
        run(args)
    }

    /** Puts a video stream and a sound stream into one file without re-encoding. [ext] is mp4, webm or mkv. */
    suspend fun mux(video: File, audio: File, out: File, ext: String) {
        val container = when (ext) {
            "webm" -> "webm"
            "mkv" -> "matroska"
            else -> "mp4"
        }
        val args = mutableListOf(
            "-y", "-loglevel", "error",
            "-i", video.absolutePath,
            "-i", audio.absolutePath,
            "-map", "0:v:0", "-map", "1:a:0",
            "-c", "copy"
        )
        if (container == "mp4") args += listOf("-movflags", "+faststart")
        args += listOf("-f", container, out.absolutePath)
        run(args)
    }
}
