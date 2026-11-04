package com.playmate.app.data

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaMuxer
import android.media.MediaScannerConnection
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import android.media.MediaFormat as TrackFormat

// ------------------------------------------------------------------ what can be saved

/** One thing a person can save: an audio file, or a video at one quality. */
class SaveTarget(
    val isAudio: Boolean,
    val label: String,
    val height: Int,
    val format: String,
    val ext: String,
    val mime: String,
    val videoUrl: String?,
    val audioUrl: String?,
    val webm: Boolean
) {
    val urls: List<String> get() = listOfNotNull(videoUrl, audioUrl)
}

class SaveTargets(val audio: List<SaveTarget>, val video: List<SaveTarget>)

fun saveTargetsFor(info: PlayableInfo): SaveTargets {
    val streams = info.audios.ifEmpty { listOfNotNull(info.audioOnly) }

    fun audioTarget(s: AudioStream): SaveTarget {
        val webm = s.format == MediaFormat.WEBMA
        val kbps = s.averageBitrate
        return SaveTarget(
            isAudio = true,
            label = if (kbps > 0) "${kbps}K" else "Audio",
            height = 0,
            format = if (webm) "WEBM" else "M4A",
            ext = if (webm) "webm" else "m4a",
            mime = if (webm) "audio/webm" else "audio/mp4",
            videoUrl = null,
            audioUrl = s.content,
            webm = webm
        )
    }

    val m4a = streams.filter { it.format == MediaFormat.M4A }.sortedByDescending { it.averageBitrate }
    val opus = streams.filter { it.format == MediaFormat.WEBMA }.sortedByDescending { it.averageBitrate }
    val picks = mutableListOf<AudioStream>()
    m4a.firstOrNull()?.let { picks += it }
    val lowest = m4a.lastOrNull()
    val highest = m4a.firstOrNull()
    if (lowest != null && highest != null && lowest.averageBitrate in 1 until (highest.averageBitrate * 6 / 10)) {
        picks += lowest
    }
    opus.firstOrNull()?.let { picks += it }
    val audio = picks.map(::audioTarget)

    val video = info.qualities.map { q ->
        val format = q.video.format
        val webm = format == MediaFormat.WEBM
        val ext = when {
            q.audio == null -> format?.suffix ?: "mp4"
            webm -> "webm"
            else -> "mp4"
        }
        val mime = when (ext) {
            "webm" -> "video/webm"
            "3gp" -> "video/3gpp"
            else -> "video/mp4"
        }
        SaveTarget(
            isAudio = false,
            label = if (q.height >= 720) "${q.label} HD" else q.label,
            height = q.height,
            format = ext.uppercase(Locale.US),
            ext = ext,
            mime = mime,
            videoUrl = q.video.content,
            audioUrl = q.audio?.content,
            webm = webm
        )
    }
    return SaveTargets(audio, video)
}

fun formatSize(bytes: Long): String = when {
    bytes < 1_000_000 -> String.format(Locale.US, "%.0f KB", bytes / 1_000.0)
    bytes < 1_000_000_000 -> String.format(Locale.US, "%.1f MB", bytes / 1_000_000.0)
    else -> String.format(Locale.US, "%.2f GB", bytes / 1_000_000_000.0)
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

enum class SaveState { Queued, Downloading, Joining, Saving, Done, Failed, Cancelled }

class SaveJob(
    val id: Int,
    val title: String,
    val thumb: String?,
    val target: SaveTarget,
    val name: String,
    /** True for the app's own Downloads (kept inside Playmate); false for "Save to phone". */
    val offline: Boolean = false,
    val source: PlayableInfo? = null
) {
    var state by mutableStateOf(SaveState.Queued)
    var message by mutableStateOf<String?>(null)
    var uri by mutableStateOf<Uri?>(null)
    var place by mutableStateOf<String?>(null)

    val bytes = AtomicLong(0)

    @Volatile
    var total: Long = 0

    var task: Job? = null

    val running: Boolean
        get() = state == SaveState.Queued || state == SaveState.Downloading ||
            state == SaveState.Joining || state == SaveState.Saving

    val percent: Int
        get() = if (total > 0) ((bytes.get() * 100) / total).toInt().coerceIn(0, 100) else 0
}

private class JoinException(cause: Throwable) : Exception(cause.message, cause)

private class Probe(val total: Long, val ranges: Boolean)

private const val CHUNK = 2L * 1024 * 1024
private const val WORKERS = 4

object SaveCenter {
    /** For screens. */
    val jobs = mutableStateListOf<SaveJob>()

    /** For the notification: safe to read from any thread. */
    private val live = CopyOnWriteArrayList<SaveJob>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gate = Semaphore(2)
    private val ids = AtomicInteger(0)

    fun liveJobs(): List<SaveJob> = live.toList()

    fun start(
        context: Context,
        info: PlayableInfo,
        target: SaveTarget,
        name: String,
        offline: Boolean = false
    ): SaveJob {
        val app = context.applicationContext
        val job = SaveJob(ids.incrementAndGet(), info.title, info.thumb, target, name, offline, info)
        jobs.add(0, job)
        live.add(job)
        DownloadService.start(app)
        val task = scope.launch { gate.withPermit { execute(app, job) } }
        job.task = task
        task.invokeOnCompletion { live.remove(job) }
        return job
    }

    fun cancel(job: SaveJob) {
        job.task?.cancel()
        if (job.running) job.state = SaveState.Cancelled
    }

    fun cancelAll() {
        live.toList().forEach { cancel(it) }
    }

    fun clearFinished() {
        jobs.removeAll { !it.running }
    }

    private suspend fun execute(ctx: Context, job: SaveJob) {
        val dir = File(ctx.cacheDir, "save").apply { mkdirs() }
        val vFile = File(dir, "${job.id}-v.tmp")
        val aFile = File(dir, "${job.id}-a.tmp")
        val mFile = File(dir, "${job.id}-m.tmp")
        try {
            val t = job.target
            val vUrl = t.videoUrl
            val aUrl = t.audioUrl
            job.state = SaveState.Downloading

            val vProbe = vUrl?.let { withContext(Dispatchers.IO) { probeNow(it) } }
            val aProbe = aUrl?.let { withContext(Dispatchers.IO) { probeNow(it) } }
            job.total = listOfNotNull(vProbe, aProbe).sumOf { it.total.coerceAtLeast(0L) }

            val free = StatFs(ctx.cacheDir.path).availableBytes
            if (job.total > 0 && free < job.total * 2 + 64L * 1024 * 1024) {
                throw IOException("ENOSPC: not enough free space")
            }

            if (vUrl != null && vProbe != null) fetch(vUrl, vFile, vProbe, job)
            if (aUrl != null && aProbe != null) fetch(aUrl, aFile, aProbe, job)

            val result: File = when {
                vUrl != null && aUrl != null -> {
                    job.state = SaveState.Joining
                    try {
                        withContext(Dispatchers.IO) { mux(vFile, aFile, mFile, t.webm) { ensureActive() } }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        throw JoinException(e)
                    }
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
            job.state = SaveState.Done
        } catch (e: CancellationException) {
            job.state = SaveState.Cancelled
            throw e
        } catch (e: Exception) {
            job.message = explain(e)
            job.state = SaveState.Failed
        } finally {
            vFile.delete()
            aFile.delete()
            mFile.delete()
            DownloadNotifications.finished(ctx, job)
        }
    }
}

private fun explain(e: Throwable): String {
    val tech = "${e.javaClass.simpleName}: ${e.message}"
    val text = e.message ?: ""
    return when {
        e is SecurityException ->
            "Playmate can't write to the folder you picked. Tap Change and pick it again."
        text.contains("ENOSPC") || text.contains("No space left") ->
            "There isn't enough free space on your phone for this file."
        e is JoinException ->
            "This quality can't be joined on your phone. Try another quality.  [$tech]"
        e is IOException && text.startsWith("HTTP 4") ->
            "YouTube refused this download ($text). Open the video again and retry.  [$tech]"
        e is IOException ->
            "The download was interrupted. Check your connection and try again.  [$tech]"
        else -> "Couldn't save this video.  [$tech]"
    }
}

// ------------------------------------------------------------------ size probe and download

/** Size in bytes of a stream, or -1 when it can't be told. For showing sizes in the sheet. */
suspend fun probeSize(url: String): Long = withContext(Dispatchers.IO) {
    attempt { probeNow(url).total }.getOrDefault(-1L)
}

private fun probeNow(url: String): Probe {
    val request = Request.Builder()
        .url(url)
        .header("User-Agent", PmDownloader.USER_AGENT)
        .header("Range", "bytes=0-0")
        .build()
    return PmDownloader.client.newCall(request).execute().use { r ->
        when (r.code) {
            206 -> Probe(
                r.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull() ?: -1L,
                true
            )
            200 -> Probe(r.body?.contentLength() ?: -1L, false)
            else -> throw IOException("HTTP ${r.code}")
        }
    }
}

private fun writeFully(ch: FileChannel, buffer: ByteBuffer, start: Long) {
    var at = start
    while (buffer.hasRemaining()) at += ch.write(buffer, at)
}

private fun streamWhole(url: String, ch: FileChannel, onBytes: (Long) -> Unit, alive: () -> Unit) {
    val request = Request.Builder()
        .url(url)
        .header("User-Agent", PmDownloader.USER_AGENT)
        .build()
    PmDownloader.client.newCall(request).execute().use { r ->
        if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
        val src = (r.body ?: throw IOException("Empty response")).byteStream()
        val buf = ByteArray(64 * 1024)
        var at = 0L
        while (true) {
            alive()
            val n = src.read(buf)
            if (n < 0) break
            writeFully(ch, ByteBuffer.wrap(buf, 0, n), at)
            at += n
            onBytes(n.toLong())
        }
    }
}

private fun getRange(
    url: String,
    from: Long,
    to: Long,
    ch: FileChannel,
    onBytes: (Long) -> Unit,
    alive: () -> Unit
) {
    var pos = from
    var failures = 0
    while (pos <= to) {
        alive()
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", PmDownloader.USER_AGENT)
                .header("Range", "bytes=$pos-$to")
                .build()
            PmDownloader.client.newCall(request).execute().use { r ->
                if (r.code != 206) throw IOException("HTTP ${r.code}")
                val src = (r.body ?: throw IOException("Empty response")).byteStream()
                val buf = ByteArray(64 * 1024)
                while (pos <= to) {
                    alive()
                    val n = src.read(buf)
                    if (n < 0) break
                    val take = minOf(n.toLong(), to - pos + 1).toInt()
                    writeFully(ch, ByteBuffer.wrap(buf, 0, take), pos)
                    pos += take
                    onBytes(take.toLong())
                }
            }
            if (pos <= to) throw IOException("Connection closed early")
        } catch (e: IOException) {
            failures++
            if (failures >= 4) throw e
            Thread.sleep(300L * failures)
        }
    }
}

/** Downloads one stream into [dest]: several connections at once, each taking the next 2 MB piece. */
private suspend fun fetch(url: String, dest: File, probe: Probe, job: SaveJob) {
    val count: (Long) -> Unit = { n -> job.bytes.addAndGet(n) }
    RandomAccessFile(dest, "rw").use { raf ->
        val ch = raf.channel
        if (!probe.ranges || probe.total <= 0) {
            coroutineScope {
                withContext(Dispatchers.IO) {
                    streamWhole(url, ch, count) { ensureActive() }
                }
            }
        } else {
            raf.setLength(probe.total)
            coroutineScope {
                val next = AtomicLong(0)
                val workers = List(WORKERS) {
                    async(Dispatchers.IO) {
                        while (true) {
                            ensureActive()
                            val from = next.getAndAdd(CHUNK)
                            if (from >= probe.total) break
                            val to = minOf(from + CHUNK, probe.total) - 1
                            getRange(url, from, to, ch, count) { ensureActive() }
                        }
                    }
                }
                workers.awaitAll()
            }
        }
    }
}

// ------------------------------------------------------------------ joining video and audio

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
