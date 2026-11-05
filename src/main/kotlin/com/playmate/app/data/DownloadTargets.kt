package com.playmate.app.data

import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.VideoStream
import java.util.Locale

// ------------------------------------------------------------------ what can be saved

/** The audio files Playmate can produce. MP3, FLAC and WAV are made with FFmpeg; M4A and Opus are the original sound. */
enum class AudioFormat(val id: String, val label: String, val mime: String) {
    MP3("mp3", "MP3", "audio/mpeg"),
    M4A("m4a", "M4A", "audio/mp4"),
    OPUS("opus", "Opus", "audio/ogg"),
    FLAC("flac", "FLAC", "audio/flac"),
    WAV("wav", "WAV", "audio/wav");

    companion object {
        fun of(id: String?): AudioFormat? = entries.firstOrNull { it.id == id }
    }
}

/**
 * One thing a person can save: an audio file in some format, or a video at one quality and codec.
 *
 * [key] names it in a way that survives a restart: when a download resumes the stream links are
 * looked up again (they expire after hours), and the key finds the same quality in the new list.
 */
class SaveTarget(
    val key: String,
    val isAudio: Boolean,
    val label: String,
    val detail: String,
    val height: Int,
    val format: String,
    val ext: String,
    val mime: String,
    val videoUrl: String?,
    val audioUrl: String?,
    val webm: Boolean,
    val audioFormat: AudioFormat? = null,
    val kbps: Int = 0,
    val videoCodec: String = "",
    /** False for the extra codecs shown under "More formats". */
    val primary: Boolean = true
) {
    val urls: List<String> get() = listOfNotNull(videoUrl, audioUrl)
}

class SaveTargets(val audio: List<SaveTarget>, val video: List<SaveTarget>)

/**
 * Bytes this target will take: null while the stream sizes are still being measured, -1 when
 * unknown. Converted audio is worked out from its bitrate and the video's length.
 */
fun SaveTarget.estimatedBytes(sizes: Map<String, Long>, durationSec: Long): Long? {
    if (durationSec > 0) {
        when (audioFormat) {
            AudioFormat.MP3 -> return kbps * 125L * durationSec
            AudioFormat.FLAC -> return durationSec * 90_000L
            AudioFormat.WAV -> return durationSec * 176_400L
            else -> Unit
        }
    }
    val parts = urls.map { sizes[it] }
    if (parts.any { it == null }) return null
    val sum = parts.sumOf { (it ?: 0L).coerceAtLeast(0L) }
    return if (sum > 0) sum else -1L
}

/** True when [estimatedBytes] is a calculation, not a measurement (so the screen shows a "~"). */
fun SaveTarget.sizeIsApproximate(): Boolean =
    audioFormat == AudioFormat.MP3 || audioFormat == AudioFormat.FLAC || audioFormat == AudioFormat.WAV

// ------------------------------------------------------------------ reading the extractor's streams

/** Reads a getter by name. The extractor's newer fields are optional for us, so a missing one is just null. */
private fun Any.read(name: String): Any? = try {
    javaClass.getMethod(name).invoke(this)
} catch (e: Exception) {
    null
}

private fun heightOf(resolution: String?): Int =
    Regex("(\\d+)p").find(resolution ?: "")?.groupValues?.get(1)?.toIntOrNull() ?: 0

private fun fpsOf(resolution: String?): Int =
    Regex("\\d+p(\\d+)").find(resolution ?: "")?.groupValues?.get(1)?.toIntOrNull() ?: 30

private fun codecOf(v: VideoStream): String {
    val raw = (v.read("getCodec") as? String)?.lowercase(Locale.US).orEmpty()
    return when {
        raw.startsWith("avc") -> "h264"
        raw.startsWith("vp09") || raw.startsWith("vp9") -> "vp9"
        raw.startsWith("av01") || raw.startsWith("av1") -> "av1"
        raw.startsWith("hev") || raw.startsWith("hvc") -> "h265"
        v.format == MediaFormat.WEBM -> "vp9"
        else -> "h264"
    }
}

private fun isHdr(v: VideoStream): Boolean {
    val raw = (v.read("getCodec") as? String)?.lowercase(Locale.US).orEmpty()
    return (v.resolution ?: "").contains("HDR", ignoreCase = true) || raw.startsWith("vp09.02")
}

/** Videos with several spoken languages list one audio stream per language: the original is the one we want. */
private fun isOriginalTrack(a: AudioStream): Boolean {
    val type = a.read("getAudioTrackType")?.toString()
    return type == null || type == "ORIGINAL"
}

private fun codecName(codec: String): String = when (codec) {
    "h264" -> "H.264"
    "vp9" -> "VP9"
    "av1" -> "AV1"
    "h265" -> "H.265"
    else -> codec.uppercase(Locale.US)
}

private fun mimeOfVideoExt(ext: String): String = when (ext) {
    "webm" -> "video/webm"
    "3gp" -> "video/3gpp"
    "mkv" -> "video/x-matroska"
    else -> "video/mp4"
}

private class Cand(
    val stream: VideoStream,
    val height: Int,
    val fps: Int,
    val codec: String,
    val hdr: Boolean,
    val muxed: Boolean,
    val bitrate: Int
) {
    /** Lower is friendlier: sound already inside, then H.264 (plays everywhere), then VP9, then AV1. */
    val rank: Int
        get() = if (muxed) -1 else when (codec) {
            "h264" -> 0
            "vp9" -> 1
            "av1" -> 2
            else -> 3
        }
}

// ------------------------------------------------------------------ building the list

fun saveTargetsFor(info: PlayableInfo): SaveTargets {
    val ffmpeg = Ffmpeg.available

    val allAudio = info.audios.ifEmpty { listOfNotNull(info.audioOnly) }
    val original = allAudio.filter { isOriginalTrack(it) }.ifEmpty { allAudio }
    val m4a = original.filter { it.format == MediaFormat.M4A }.sortedByDescending { it.averageBitrate }
    val opus = original.filter { it.format == MediaFormat.WEBMA }.sortedByDescending { it.averageBitrate }
    val best = original.maxByOrNull { it.averageBitrate }

    // ---------------------------------------------------------------- audio
    fun audioTarget(
        format: AudioFormat,
        key: String,
        label: String,
        detail: String,
        ext: String,
        mime: String,
        source: AudioStream,
        kbps: Int
    ) = SaveTarget(
        key = key,
        isAudio = true,
        label = label,
        detail = detail,
        height = 0,
        format = ext.uppercase(Locale.US),
        ext = ext,
        mime = mime,
        videoUrl = null,
        audioUrl = source.content,
        webm = source.format == MediaFormat.WEBMA,
        audioFormat = format,
        kbps = kbps
    )

    val audio = mutableListOf<SaveTarget>()
    if (ffmpeg && best != null) {
        for (kbps in listOf(320, 256, 192, 128)) {
            audio += audioTarget(
                AudioFormat.MP3, "a|mp3|$kbps", "MP3 ${kbps}K", "Converted to MP3",
                "mp3", AudioFormat.MP3.mime, best, kbps
            )
        }
    }
    m4a.firstOrNull()?.let { top ->
        val kbps = top.averageBitrate
        audio += audioTarget(
            AudioFormat.M4A, "a|m4a|0", if (kbps > 0) "M4A ${kbps}K" else "M4A",
            "Original AAC, no conversion", "m4a", AudioFormat.M4A.mime, top, kbps
        )
        val low = m4a.lastOrNull()
        if (low != null && low !== top && low.averageBitrate in 1 until (top.averageBitrate * 6 / 10)) {
            audio += audioTarget(
                AudioFormat.M4A, "a|m4a|low", "M4A ${low.averageBitrate}K",
                "Data saver", "m4a", AudioFormat.M4A.mime, low, low.averageBitrate
            )
        }
    }
    opus.firstOrNull()?.let { top ->
        val kbps = top.averageBitrate
        // FFmpeg turns the WebM wrapper into a normal .opus file; without it the original .webm is kept.
        val ext = if (ffmpeg) "opus" else "webm"
        val mime = if (ffmpeg) AudioFormat.OPUS.mime else "audio/webm"
        audio += audioTarget(
            AudioFormat.OPUS, "a|opus|0", if (kbps > 0) "Opus ${kbps}K" else "Opus",
            "Original sound, no conversion", ext, mime, top, kbps
        )
    }
    if (ffmpeg && best != null) {
        audio += audioTarget(
            AudioFormat.FLAC, "a|flac|0", "FLAC", "Lossless container of the original",
            "flac", AudioFormat.FLAC.mime, best, 0
        )
        audio += audioTarget(
            AudioFormat.WAV, "a|wav|0", "WAV", "Uncompressed",
            "wav", AudioFormat.WAV.mime, best, 0
        )
    }

    // ---------------------------------------------------------------- video
    fun audioFor(v: VideoStream): AudioStream? =
        if (v.format == MediaFormat.WEBM) {
            opus.firstOrNull() ?: if (ffmpeg) best else null
        } else {
            m4a.firstOrNull() ?: if (ffmpeg) best else null
        }

    val cands = mutableListOf<Cand>()
    fun add(v: VideoStream, muxed: Boolean) {
        val h = heightOf(v.resolution)
        if (h <= 0) return
        val c = Cand(
            stream = v,
            height = h,
            fps = fpsOf(v.resolution),
            codec = codecOf(v),
            hdr = isHdr(v),
            muxed = muxed,
            bitrate = (v.read("getBitrate") as? Number)?.toInt() ?: 0
        )
        if (!muxed) {
            // The phone's own joiner only knows H.264 + AAC (MP4) and VP9 + Opus (WebM).
            if (!ffmpeg && (c.codec == "av1" || c.codec == "h265")) return
            if (audioFor(v) == null) return
        }
        cands += c
    }
    info.videoStreams.forEach { add(it, muxed = true) }
    info.videoOnlyStreams.forEach { add(it, muxed = false) }

    fun videoTarget(c: Cand, primary: Boolean): SaveTarget {
        val audioStream = if (c.muxed) null else audioFor(c.stream)
        val ext = when {
            c.muxed -> c.stream.format?.suffix ?: "mp4"
            c.stream.format == MediaFormat.WEBM && audioStream?.format == MediaFormat.WEBMA -> "webm"
            c.stream.format == MediaFormat.WEBM || audioStream?.format == MediaFormat.WEBMA -> "mkv"
            else -> "mp4"
        }
        val badge = when {
            c.height >= 4320 -> " 8K"
            c.height >= 2160 -> " 4K"
            c.height >= 1440 -> " 2K"
            c.height >= 720 -> " HD"
            else -> ""
        }
        val label = "${c.height}p" + (if (c.fps > 30) "${c.fps}" else "") + badge + (if (c.hdr) " HDR" else "")
        val codecKey = if (c.muxed) "mux" else c.codec
        return SaveTarget(
            key = "v|${c.height}|${c.fps}|$codecKey${if (c.hdr) "+hdr" else ""}",
            isAudio = false,
            label = label,
            detail = if (c.muxed) "${ext.uppercase(Locale.US)} · with sound" else "${codecName(c.codec)} · ${ext.uppercase(Locale.US)}",
            height = c.height,
            format = ext.uppercase(Locale.US),
            ext = ext,
            mime = mimeOfVideoExt(ext),
            videoUrl = c.stream.content,
            audioUrl = audioStream?.content,
            webm = c.stream.format == MediaFormat.WEBM,
            videoCodec = c.codec,
            primary = primary
        )
    }

    val video = mutableListOf<SaveTarget>()
    val groups = cands.groupBy { Triple(it.height, if (it.fps > 30) 1 else 0, it.hdr) }
    val order = groups.keys.sortedWith(compareBy({ -it.first }, { -it.second }, { it.third }))
    for (groupKey in order) {
        val members = groups.getValue(groupKey)
        // One stream per codec: the friendliest container, and the highest bitrate among equals.
        val perCodec = members
            .groupBy { it.codec }
            .map { (_, list) -> list.minWith(compareBy({ it.rank }, { -it.bitrate })) }
            .sortedBy { it.rank }
        perCodec.forEachIndexed { i, c -> video += videoTarget(c, primary = i == 0) }
    }
    return SaveTargets(audio, video)
}

// ------------------------------------------------------------------ finding a target again

/**
 * Finds [key] in a fresh list. Exact first (that is how a resumed download gets the same file back);
 * otherwise the nearest sensible one: "v|1080|*|*" means the best video up to 1080p, a height of 0
 * means the best there is, and an audio key falls back to the same format at any bitrate.
 */
fun findTarget(targets: SaveTargets, key: String): SaveTarget? {
    (targets.audio + targets.video).firstOrNull { it.key == key }?.let { return it }
    val p = key.split('|')
    if (p.firstOrNull() == "a") {
        val format = p.getOrNull(1)
        val kbps = p.getOrNull(2)
        return targets.audio.firstOrNull { it.audioFormat?.id == format && it.kbps.toString() == kbps }
            ?: targets.audio.firstOrNull { it.audioFormat?.id == format }
            ?: targets.audio.firstOrNull { it.audioFormat == AudioFormat.M4A }
            ?: targets.audio.firstOrNull()
    }
    val wanted = p.getOrNull(1)?.toIntOrNull() ?: 0
    val pool = targets.video
    if (pool.isEmpty()) return null
    val within = if (wanted <= 0) pool else pool.filter { it.height <= wanted }
    val top = if (within.isNotEmpty()) within.maxOf { it.height } else pool.minOf { it.height }
    val level = (if (within.isNotEmpty()) within else pool).filter { it.height == top }
    val codec = p.getOrNull(3)?.takeIf { it != "*" }
    return level.firstOrNull { codec != null && it.key.endsWith("|$codec") }
        ?: level.filter { it.primary }.maxByOrNull { fpsOfKey(it.key) }
        ?: level.first()
}

private fun fpsOfKey(key: String): Int = key.split('|').getOrNull(2)?.toIntOrNull() ?: 0

/** What a download that has not looked up its streams yet shows (a playlist item, or one restored after a restart). */
fun placeholderTarget(key: String): SaveTarget {
    val p = key.split('|')
    if (p.firstOrNull() == "a") {
        val format = AudioFormat.of(p.getOrNull(1)) ?: AudioFormat.MP3
        val kbps = p.getOrNull(2)?.toIntOrNull() ?: 0
        return SaveTarget(
            key = key,
            isAudio = true,
            label = if (format == AudioFormat.MP3 && kbps > 0) "MP3 ${kbps}K" else format.label,
            detail = "",
            height = 0,
            format = format.id.uppercase(Locale.US),
            ext = format.id,
            mime = format.mime,
            videoUrl = null,
            audioUrl = null,
            webm = false,
            audioFormat = format,
            kbps = kbps
        )
    }
    val wanted = p.getOrNull(1)?.toIntOrNull() ?: 0
    return SaveTarget(
        key = key,
        isAudio = false,
        label = if (wanted <= 0) "Best quality" else "Up to ${wanted}p",
        detail = "",
        height = wanted,
        format = "MP4",
        ext = "mp4",
        mime = "video/mp4",
        videoUrl = null,
        audioUrl = null,
        webm = false
    )
}
