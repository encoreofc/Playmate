package com.playmate.app.data

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaMuxer
import android.media.MediaScannerConnection
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.playmate.app.DownloadNotifications
import com.playmate.app.DownloadService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.util.Collections
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import android.media.MediaFormat as TrackFormat

// What can be saved (SaveTarget, saveTargetsFor) lives in DownloadTargets.kt,
// and the fast piece-by-piece downloading lives in ChunkFetcher.kt.

fun formatSize(bytes: Long): String = when {
    bytes < 1_000_000 -> String.format(Locale.US, "%.0f KB", bytes / 1_000.0)
    bytes < 1_000_000_000 -> String.format(Locale.US, "%.1f MB", bytes / 1_000_000.0)
    else -> String.format(Locale.US, "%.2f GB", bytes / 1_000_000_000.0)
}

fun formatSpeed(bytesPerSec: Long): String = when {
    bytesPerSec <= 0 -> "—"
    bytesPerSec < 1_000_000 -> String.format(Locale.US, "%.0f KB/s", bytesPerSec / 1_000.0)
    else -> String.format(Locale.US, "%.1f MB/s", bytesPerSec / 1_000_000.0)
}

/** "" when it can't be told yet. */
fun formatEta(seconds: Long): String = when {
    seconds < 0 -> ""
    seconds < 60 -> "${seconds}s left"
    seconds < 3600 -> "${seconds / 60} min left"
    else -> "${seconds / 3600} h ${(seconds % 3600) / 60} min left"
}

fun safeFileName(raw: String): String {
    val cleaned = raw
        .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .trim('.')
    return cleaned.take(100).trim().ifBlank { "Playmate video" }
}

// ------------------------------------------------------------------ where files go

object SaveFolder {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("playmate_save", Context.MODE_PRIVATE)

    /** The folder the person picked, or null when the default location is in use. */
    fun tree(ctx: Context): Uri? {
        val saved = prefs(ctx).getString("tree", null) ?: return null
        val uri = Uri.parse(saved)
        val allowed = ctx.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isWritePermission }
        return if (allowed) uri else null
    }

    fun choose(ctx: Context, uri: Uri) {
        runCatching {
            ctx.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        prefs(ctx).edit().putString("tree", uri.toString()).apply()
    }

    fun reset(ctx: Context) {
        prefs(ctx).edit().remove("tree").apply()
    }

    fun label(ctx: Context, audio: Boolean): String {
        val custom = tree(ctx)
        if (custom != null) {
            val path = (custom.lastPathSegment ?: "").substringAfter(':', "")
            return path.ifBlank { "Selected folder" }
        }
        return if (Build.VERSION.SDK_INT >= 29) {
            if (audio) "Music/Playmate" else "Movies/Playmate"
        } else {
            "Android/data/com.playmate.app/files/" + if (audio) "Music/Playmate" else "Movies/Playmate"
        }
    }
}

// ------------------------------------------------------------------ jobs

enum class SaveState { Queued, Downloading, Joining, Saving, Done, Failed, Cancelled, Paused }

class SaveJob(
    val id: Int,
    val title: String,
    val thumb: String?,
    target: SaveTarget,
    val name: String,
    /** True for the app's own Downloads (kept inside Playmate); false for "Save to phone". */
    val offline: Boolean = false,
    source: PlayableInfo? = null
) {
    /** What is being saved. After a restart it starts as a stand-in and becomes the real one when the download resumes. */
    var target: SaveTarget = target

    /** The video's details. After a restart it holds just the link and title until the download resumes. */
    var source: PlayableInfo? = source

    var state by mutableStateOf(SaveState.Queued)
    var message by mutableStateOf<String?>(null)
    var uri by mutableStateOf<Uri?>(null)
    var place by mutableStateOf<String?>(null)

    /** Bytes of the file(s) that are safely on disk. */
    val bytes = AtomicLong(0)

    /** Bytes that came over the network in this run (for the speed readout; resumed bytes are not counted). */
    val session = AtomicLong(0)

    /** Connections open right now, so a pause or cancel can close them at once. */
    val calls: MutableSet<Call> = Collections.newSetFromMap(ConcurrentHashMap<Call, Boolean>())

    @Volatile
    var total: Long = 0

    /** Bytes per second right now (smoothed), 0 when nothing is moving. */
    @Volatile
    var speed: Long = 0

    /** Set while a pause is on its way: the download stops but keeps what it has. */
    @Volatile
    var pauseRequested: Boolean = false

    /** True when the app paused it by itself (Wi-Fi only and the phone left Wi-Fi); it resumes by itself too. */
    @Volatile
    var autoPaused: Boolean = false

    var task: Job? = null

    val running: Boolean
        get() = state == SaveState.Queued || state == SaveState.Downloading ||
            state == SaveState.Joining || state == SaveState.Saving || state == SaveState.Paused

    val percent: Int
        get() = if (total > 0) ((bytes.get() * 100) / total).toInt().coerceIn(0, 100) else 0

    /** Seconds left at the current speed, or -1 when it can't be told. */
    val etaSeconds: Long
        get() {
            val perSecond = speed
            val left = total - bytes.get()
            return if (perSecond > 0 && left > 0) left / perSecond else -1L
        }
}

private class JoinException(cause: Throwable) : Exception(cause.message, cause)

private class ConvertException(cause: Throwable) : Exception(cause.message, cause)

private fun partialDir(ctx: Context): File = File(ctx.filesDir, "partial").apply { mkdirs() }

/** Deletes everything a job left behind (pieces, piece maps, joined output). */
private fun wipe(ctx: Context, id: Int) {
    partialDir(ctx).listFiles()?.forEach { if (it.name.startsWith("$id-")) it.delete() }
}

private fun isMetered(ctx: Context): Boolean = runCatching {
    (ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).isActiveNetworkMetered
}.getOrDefault(false)

object SaveCenter {
    /** For screens. */
    val jobs = mutableStateListOf<SaveJob>()

    /** For the notification: safe to read from any thread. Paused jobs stay in it, so the notification stays. */
    private val live = CopyOnWriteArrayList<SaveJob>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** How many downloads are running right now; the limit is the person's setting. */
    private val slots = AtomicInteger(0)

    private var monitor: Job? = null

    @Volatile
    private var appContext: Context? = null

    fun liveJobs(): List<SaveJob> = live.toList()

    fun start(
        context: Context,
        info: PlayableInfo,
        target: SaveTarget,
        name: String,
        offline: Boolean = false
    ): SaveJob {
        val app = context.applicationContext
        appContext = app
        val job = SaveJob(DownloadPrefs.nextJobId(app), info.title, info.thumb, target, name, offline, info)
        jobs.add(0, job)
        purgeOrphans(app)
        begin(app, job)
        return job
    }

    /** Brings back what was unfinished when the app was closed. Everything comes back paused; the person resumes it. */
    fun restore(context: Context) {
        val app = context.applicationContext
        appContext = app
        JobStore.load(app).forEach { saved ->
            val stand = PlayableInfo(
                url = saved.url,
                title = saved.title,
                channel = "",
                views = -1,
                uploaded = null,
                description = "",
                thumb = saved.thumb,
                live = false,
                hlsUrl = null,
                qualities = emptyList(),
                audioOnly = null,
                related = emptyList()
            )
            val job = SaveJob(saved.id, saved.title, saved.thumb, placeholderTarget(saved.key), saved.name, saved.offline, stand)
            job.state = SaveState.Paused
            jobs.add(job)
        }
        purgeOrphans(app)
    }

    // ---------------------------------------------------------------- pause, resume, cancel

    fun pause(job: SaveJob) = pauseJob(job, auto = false)

    fun pauseAll() {
        live.toList().forEach { pauseJob(it, auto = false) }
    }

    /** Carries on a paused (or failed) download from the pieces it already has. */
    fun resume(context: Context, job: SaveJob) {
        if (job.state != SaveState.Paused && job.state != SaveState.Failed) return
        val app = context.applicationContext
        appContext = app
        begin(app, job)
    }

    fun resumeAll(context: Context) {
        jobs.toList().filter { it.state == SaveState.Paused }.forEach { resume(context, it) }
    }

    fun cancel(job: SaveJob) {
        val active = job.task?.isActive == true
        job.pauseRequested = false
        job.task?.cancel()
        job.calls.toList().forEach { runCatching { it.cancel() } }
        if (job.running) job.state = SaveState.Cancelled
        if (!active) {
            // Nothing is running to clean up after itself (it was paused), so clean up here.
            appContext?.let { wipe(it, job.id) }
            live.remove(job)
            persist()
        }
    }

    fun cancelAll() {
        live.toList().forEach { cancel(it) }
    }

    /** Stops a download (if it runs), forgets it and deletes the pieces it kept. */
    fun discard(context: Context, job: SaveJob) {
        val app = context.applicationContext
        appContext = app
        cancel(job)
        jobs.remove(job)
        wipe(app, job.id)
        persist()
    }

    fun clearFinished() {
        jobs.removeAll { !it.running }
        appContext?.let { purgeOrphans(it) }
        persist()
    }

    private fun pauseJob(job: SaveJob, auto: Boolean) {
        if (job.state != SaveState.Downloading && job.state != SaveState.Queued) return
        job.autoPaused = auto
        job.pauseRequested = true
        job.task?.cancel()
        job.calls.toList().forEach { runCatching { it.cancel() } }
    }

    // ---------------------------------------------------------------- running

    private fun begin(app: Context, job: SaveJob) {
        job.pauseRequested = false
        job.autoPaused = false
        job.message = null
        job.state = SaveState.Queued
        if (job !in live) live.add(job)
        persist()
        DownloadService.start(app)
        val task = scope.launch { runJob(app, job) }
        job.task = task
        task.invokeOnCompletion { cause ->
            if (job.task === task) {
                // Stopped before its first line ran: nobody has set what state it ended in.
                if (cause != null && job.running && job.state != SaveState.Paused) stopped(job)
                if (job.state != SaveState.Paused) live.remove(job)
                persist()
            }
        }
        ensureMonitor()
    }

    /** What state a download that was stopped on purpose ends in: paused or cancelled. */
    private fun stopped(job: SaveJob) {
        if (job.pauseRequested) {
            job.speed = 0
            if (job.autoPaused) job.message = "Waiting for Wi-Fi"
            job.state = SaveState.Paused
        } else {
            job.state = SaveState.Cancelled
        }
    }

    private suspend fun runJob(app: Context, job: SaveJob) {
        try {
            waitForWifi(app, job)
            acquire(app)
        } catch (e: CancellationException) {
            stopped(job)
            if (job.state == SaveState.Cancelled) wipe(app, job.id)
            persist()
            throw e
        }
        try {
            execute(app, job)
        } finally {
            slots.decrementAndGet()
        }
    }

    /** Waits for a free place among the downloads that may run at the same time. */
    private suspend fun acquire(app: Context) {
        while (true) {
            val limit = DownloadPrefs.parallel(app)
            val now = slots.get()
            if (now < limit && slots.compareAndSet(now, now + 1)) return
            delay(250)
        }
    }

    private suspend fun waitForWifi(app: Context, job: SaveJob) {
        var told = false
        while (DownloadPrefs.wifiOnly(app) && isMetered(app)) {
            if (!told) {
                job.message = "Waiting for Wi-Fi"
                told = true
            }
            delay(2000)
        }
        if (told) job.message = null
    }

    private fun persist() {
        val app = appContext ?: return
        JobStore.save(app, jobs.toList().filter { it.running || it.state == SaveState.Failed })
    }

    /** Deletes pieces that belong to no download in the list (a removed one, or one from an older run). */
    private fun purgeOrphans(app: Context) {
        val keep = jobs.map { it.id }.toSet()
        partialDir(app).listFiles()?.forEach { file ->
            val id = file.name.substringBefore('-').toIntOrNull()
            if (id == null || id !in keep) file.delete()
        }
    }

    // ---------------------------------------------------------------- speed and network watching

    @Synchronized
    private fun ensureMonitor() {
        if (monitor != null) return
        monitor = scope.launch {
            val last = HashMap<Int, Long>()
            var tick = 0
            while (true) {
                delay(1000)
                if (stopMonitor()) break
                tick++
                for (job in live.toList()) {
                    val now = job.session.get()
                    if (job.state == SaveState.Downloading) {
                        val moved = now - (last[job.id] ?: now)
                        job.speed = if (job.speed == 0L) moved else (job.speed * 6 + moved * 4) / 10
                    } else {
                        job.speed = 0
                    }
                    last[job.id] = now
                }
                if (tick % 3 == 0) checkNetwork()
            }
        }
    }

    @Synchronized
    private fun stopMonitor(): Boolean {
        if (live.isNotEmpty()) return false
        monitor = null
        return true
    }

    /** Wi-Fi only: when the phone leaves Wi-Fi, running downloads pause; when it comes back they carry on. */
    private fun checkNetwork() {
        val app = appContext ?: return
        val blocked = DownloadPrefs.wifiOnly(app) && isMetered(app)
        for (job in live.toList()) {
            if (blocked && job.state == SaveState.Downloading) {
                pauseJob(job, auto = true)
            } else if (!blocked && job.state == SaveState.Paused && job.autoPaused) {
                begin(app, job)
            }
        }
    }

    // ---------------------------------------------------------------- one download

    private suspend fun execute(ctx: Context, job: SaveJob) {
        val dir = partialDir(ctx)
        val vFile = File(dir, "${job.id}-v.part")
        val vMap = File(dir, "${job.id}-v.map")
        val aFile = File(dir, "${job.id}-a.part")
        val aMap = File(dir, "${job.id}-a.map")
        val mFile = File(dir, "${job.id}-m.tmp")
        try {
            job.state = SaveState.Downloading
            job.message = null
            var t = job.target

            // A download restored after a restart knows only the video's link and what was chosen.
            if (t.videoUrl == null && t.audioUrl == null) {
                val source = job.source ?: throw IOException("This download lost its video link.")
                val fresh = YouTubeRepo.load(source.url)
                job.source = fresh
                t = findTarget(saveTargetsFor(fresh), t.key)
                    ?: throw IOException("This quality isn't offered for the video any more.")
                job.target = t
            }

            // Stream links last a few hours. If one is refused, look the video up again and carry on.
            var round = 0
            while (true) {
                try {
                    pull(t, job, dir, vFile, vMap, aFile, aMap)
                    break
                } catch (e: ExpiredLinkException) {
                    round++
                    val source = job.source
                    if (round > 1 || source == null) throw e
                    YouTubeRepo.invalidate(source.url)
                    val fresh = YouTubeRepo.load(source.url)
                    t = findTarget(saveTargetsFor(fresh), job.target.key) ?: throw e
                    job.target = t
                }
            }

            val vUrl = t.videoUrl
            val aUrl = t.audioUrl
            val result: File = when {
                t.isAudio -> convertAudio(t, job, aFile, mFile)
                vUrl != null && aUrl != null -> {
                    job.state = SaveState.Joining
                    join(t, vFile, aFile, mFile)
                    vFile.delete()
                    aFile.delete()
                    mFile
                }
                vUrl != null -> vFile
                else -> aFile
            }

            job.state = SaveState.Saving
            if (job.offline) {
                withContext(Dispatchers.IO) { OfflineStore.add(ctx, result, job) }
                job.place = "Downloads"
            } else {
                val saved = withContext(Dispatchers.IO) { deliver(ctx, result, job.name, t) }
                job.uri = saved.uri
                job.place = saved.place
            }
            job.speed = 0
            job.state = SaveState.Done
        } catch (e: CancellationException) {
            stopped(job)
            throw e
        } catch (e: Exception) {
            job.speed = 0
            job.message = explain(e)
            job.state = SaveState.Failed
        } finally {
            mFile.delete()
            // A paused or failed download keeps its pieces, so it can carry on instead of starting over.
            if (job.state == SaveState.Done || job.state == SaveState.Cancelled) wipe(ctx, job.id)
            DownloadNotifications.finished(ctx, job)
            persist()
        }
    }
}

/** Fetches the video stream and the sound stream (whichever the target has) at the same time. */
private suspend fun pull(
    t: SaveTarget,
    job: SaveJob,
    dir: File,
    vFile: File,
    vMap: File,
    aFile: File,
    aMap: File
) {
    val vUrl = t.videoUrl
    val aUrl = t.audioUrl
    val vProbe = vUrl?.let { withContext(Dispatchers.IO) { probeNow(it) } }
    val aProbe = aUrl?.let { withContext(Dispatchers.IO) { probeNow(it) } }
    job.total = listOfNotNull(vProbe, aProbe).sumOf { it.total.coerceAtLeast(0L) }
    job.bytes.set(0)

    // What an earlier run already fetched does not need room again.
    val alreadyHave = listOf(vProbe to vMap, aProbe to aMap).sumOf { (probe, map) ->
        if (probe != null && probe.ranges && probe.total > 0) {
            ChunkMap.load(map, probe.total, CHUNK).doneBytes()
        } else {
            0L
        }
    }
    val free = StatFs(dir.path).availableBytes
    if (job.total > 0 && free < job.total * 2 - alreadyHave + 64L * 1024 * 1024) {
        throw IOException("ENOSPC: not enough free space")
    }

    coroutineScope {
        val work = mutableListOf<Deferred<Unit>>()
        if (vUrl != null && vProbe != null) work += async { fetchResumable(vUrl, vFile, vMap, vProbe, job) }
        if (aUrl != null && aProbe != null) work += async { fetchResumable(aUrl, aFile, aMap, aProbe, job) }
        work.awaitAll()
    }
}

/** MP3, FLAC, WAV and .opus are made from the downloaded sound; M4A and .webm Opus are kept as they came. */
private suspend fun convertAudio(t: SaveTarget, job: SaveJob, src: File, out: File): File {
    val format = t.audioFormat ?: return src
    val needsFfmpeg = format == AudioFormat.MP3 || format == AudioFormat.FLAC ||
        format == AudioFormat.WAV || (format == AudioFormat.OPUS && t.ext == "opus")
    if (!needsFfmpeg) return src
    job.state = SaveState.Joining
    try {
        Ffmpeg.convertAudio(src, out, format, t.kbps)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw ConvertException(e)
    }
    src.delete()
    return out
}

/**
 * Joins video and sound. The phone's own joiner is fastest for H.264 + AAC (MP4) and VP9 + Opus (WebM);
 * everything else goes to FFmpeg. If the first way fails, the other one gets a turn.
 */
private suspend fun join(t: SaveTarget, video: File, audio: File, out: File) {
    val native = (t.ext == "mp4" && t.videoCodec == "h264") || (t.ext == "webm" && t.videoCodec == "vp9")
    val order = if (native || !Ffmpeg.available) listOf(false, true) else listOf(true)
    var last: Exception? = null
    for (useFfmpeg in order) {
        if (useFfmpeg && !Ffmpeg.available) continue
        out.delete()
        try {
            if (useFfmpeg) {
                Ffmpeg.mux(video, audio, out, t.ext)
            } else {
                withContext(Dispatchers.IO) { mux(video, audio, out, t.webm) { ensureActive() } }
            }
            return
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            last = e
        }
    }
    throw JoinException(last ?: IOException("No way to join this quality."))
}

private fun explain(e: Throwable): String {
    val tech = "${e.javaClass.simpleName}: ${e.message}"
    val text = e.message ?: ""
    return when {
        e is SecurityException ->
            "Playmate can't write to the folder you picked. Tap Change and pick it again."
        text.contains("ENOSPC") || text.contains("No space left") ->
            "There isn't enough free space on your phone for this file."
        e is ConvertException ->
            "Couldn't convert the sound to this format. Try M4A instead.  [$tech]"
        e is JoinException ->
            "This quality can't be joined on your phone. Try another quality.  [$tech]"
        e is ExpiredLinkException ->
            "YouTube's download link ran out and couldn't be renewed. Open the video again and retry.  [$tech]"
        e is IOException && text.startsWith("HTTP 4") ->
            "YouTube refused this download ($text). Open the video again and retry.  [$tech]"
        e is IOException ->
            "The download was interrupted. Check your connection and try again.  [$tech]"
        else -> "Couldn't save this video.  [$tech]"
    }
}

// ------------------------------------------------------------------ joining video and audio on the phone

private fun trackOf(extractor: MediaExtractor, prefix: String): Int {
    for (i in 0 until extractor.trackCount) {
        val mime = extractor.getTrackFormat(i).getString(TrackFormat.KEY_MIME)
        if (mime != null && mime.startsWith(prefix)) return i
    }
    return -1
}

/** Puts the video track and the audio track into one file. Nothing is re-encoded, so it is fast. */
private fun mux(video: File, audio: File, out: File, webm: Boolean, alive: () -> Unit) {
    val format = if (webm) MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM else MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
    val muxer = MediaMuxer(out.absolutePath, format)
    val vx = MediaExtractor()
    val ax = MediaExtractor()
    var started = false
    try {
        vx.setDataSource(video.absolutePath)
        ax.setDataSource(audio.absolutePath)
        val vi = trackOf(vx, "video/")
        val ai = trackOf(ax, "audio/")
        if (vi < 0 || ai < 0) throw IOException("A downloaded stream has no usable track.")
        vx.selectTrack(vi)
        ax.selectTrack(ai)

        val vf = vx.getTrackFormat(vi)
        if (vf.containsKey(TrackFormat.KEY_ROTATION)) {
            muxer.setOrientationHint(vf.getInteger(TrackFormat.KEY_ROTATION))
        }
        val vt = muxer.addTrack(vf)
        val at = muxer.addTrack(ax.getTrackFormat(ai))
        muxer.start()
        started = true

        val buffer = ByteBuffer.allocate(8 * 1024 * 1024)
        val info = MediaCodec.BufferInfo()
        while (true) {
            alive()
            val vTime = vx.sampleTime
            val aTime = ax.sampleTime
            if (vTime < 0 && aTime < 0) break
            val useVideo = vTime >= 0 && (aTime < 0 || vTime <= aTime)
            val source = if (useVideo) vx else ax
            val track = if (useVideo) vt else at
            buffer.clear()
            val size = source.readSampleData(buffer, 0)
            if (size < 0) {
                source.advance()
                continue
            }
            val flags = if (source.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                MediaCodec.BUFFER_FLAG_KEY_FRAME
            } else {
                0
            }
            info.set(0, size, source.sampleTime, flags)
            muxer.writeSampleData(track, buffer, info)
            source.advance()
        }
        muxer.stop()
        started = false
    } finally {
        if (started) runCatching { muxer.stop() }
        runCatching { muxer.release() }
        vx.release()
        ax.release()
    }
}

// ------------------------------------------------------------------ handing the file to the phone

private class Saved(val uri: Uri?, val place: String)

private fun deliver(ctx: Context, src: File, name: String, t: SaveTarget): Saved {
    val resolver = ctx.contentResolver
    val fileName = "$name.${t.ext}"
    val place = SaveFolder.label(ctx, t.isAudio)

    // A folder the person picked.
    val tree = SaveFolder.tree(ctx)
    if (tree != null) {
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val doc = DocumentsContract.createDocument(resolver, parent, "application/octet-stream", fileName)
            ?: throw IOException("Couldn't create the file in that folder.")
        val out = resolver.openOutputStream(doc) ?: throw IOException("Couldn't open the file for writing.")
        out.use { o -> src.inputStream().use { it.copyTo(o, 256 * 1024) } }
        return Saved(doc, place)
    }

    // Android 10 and newer: gallery / music library through MediaStore.
    if (Build.VERSION.SDK_INT >= 29) {
        val collection = if (t.isAudio) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, t.mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, if (t.isAudio) "Music/Playmate" else "Movies/Playmate")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: throw IOException("Couldn't create the file.")
        try {
            val out = resolver.openOutputStream(uri) ?: throw IOException("Couldn't open the file for writing.")
            out.use { o -> src.inputStream().use { it.copyTo(o, 256 * 1024) } }
            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
        return Saved(uri, place)
    }

    // Android 8 and 9: the app's own folder (no storage permission needed).
    val base = ctx.getExternalFilesDir(if (t.isAudio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES)
        ?: ctx.filesDir
    val dir = File(base, "Playmate").apply { mkdirs() }
    var target = File(dir, fileName)
    var n = 1
    while (target.exists()) {
        target = File(dir, "$name ($n).${t.ext}")
        n++
    }
    src.copyTo(target)
    MediaScannerConnection.scanFile(ctx, arrayOf(target.path), null, null)
    return Saved(null, place)
}
