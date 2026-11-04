package com.playmate.app.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

/** One video (or audio track) saved inside the app. It plays without internet. */
class OfflineVideo(
    val key: String,
    val url: String,
    val title: String,
    val channel: String,
    val durationMs: Long,
    val isAudio: Boolean,
    val quality: String,
    val format: String,
    val fileName: String,
    val thumbName: String?,
    val bytes: Long,
    val savedAt: Long
) {
    /** What the player needs to play this file from the phone. */
    fun playable(ctx: Context): PlayableInfo = PlayableInfo(
        url = url,
        title = title,
        channel = channel,
        views = -1,
        uploaded = null,
        description = "",
        thumb = OfflineStore.thumbUriOf(ctx, this),
        live = false,
        hlsUrl = null,
        qualities = emptyList(),
        audioOnly = null,
        related = emptyList(),
        localUri = Uri.fromFile(OfflineStore.fileOf(ctx, this)).toString()
    )
}

/**
 * The app's own Downloads: files kept in the app's private folder, listed in the Downloads tab and
 * played from there. The list survives app restarts (downloads.json).
 */
object OfflineStore {

    /** For screens: newest first. */
    val items = mutableStateListOf<OfflineVideo>()

    private var ready = false

    private fun dir(ctx: Context) = File(ctx.filesDir, "downloads").apply { mkdirs() }

    private fun index(ctx: Context) = File(ctx.filesDir, "downloads.json")

    fun fileOf(ctx: Context, video: OfflineVideo) = File(dir(ctx), video.fileName)

    fun thumbUriOf(ctx: Context, video: OfflineVideo): String? {
        val name = video.thumbName ?: return null
        val file = File(dir(ctx), name)
        return if (file.exists()) Uri.fromFile(file).toString() else null
    }

    @Synchronized
    fun init(ctx: Context) {
        if (ready) return
        ready = true
        val app = ctx.applicationContext
        val loaded = runCatching { read(app) }.getOrDefault(emptyList())
        val folder = dir(app)
        val alive = loaded.filter { File(folder, it.fileName).exists() }
        items.clear()
        items.addAll(alive.sortedByDescending { it.savedAt })
        if (alive.size != loaded.size) write(app)
    }

    /** Everything saved for this YouTube video (the video and/or its audio). */
    fun forUrl(url: String): List<OfflineVideo> {
        val id = videoIdOf(url) ?: url
        return items.filter { (videoIdOf(it.url) ?: it.url) == id }
    }

    fun totalBytes(): Long = items.sumOf { it.bytes }

    /**
     * Called by the download engine when a download is finished: moves the file into the app's
     * folder, keeps the thumbnail for offline use, and lists the video.
     */
    @Synchronized
    fun add(ctx: Context, src: File, job: SaveJob) {
        val info = job.source ?: throw IOException("Missing video details.")
        val target = job.target
        val folder = dir(ctx)
        val id = videoIdOf(info.url) ?: info.url.hashCode().toString()
        val key = "$id-${if (target.isAudio) "a" else "v"}"
        val fileName = "$key.${target.ext}"
        val dest = File(folder, fileName)
        val part = File(folder, "$fileName.part")

        try {
            src.copyTo(part, overwrite = true)
            if (dest.exists()) dest.delete()
            if (!part.renameTo(dest)) {
                part.copyTo(dest, overwrite = true)
            }
        } finally {
            part.delete()
        }

        val thumbFile = File(folder, "$key.jpg")
        val hasThumb = runCatching { saveThumb(job.thumb, thumbFile) }.getOrDefault(false)
        val thumbName = if (hasThumb) thumbFile.name else null

        val old = items.firstOrNull { it.key == key }
        if (old != null) {
            if (old.fileName != fileName) File(folder, old.fileName).delete()
            val oldThumb = old.thumbName
            if (oldThumb != null && oldThumb != thumbName) File(folder, oldThumb).delete()
        }

        val video = OfflineVideo(
            key = key,
            url = info.url,
            title = info.title,
            channel = info.channel,
            durationMs = durationOf(dest),
            isAudio = target.isAudio,
            quality = target.label,
            format = target.format,
            fileName = fileName,
            thumbName = thumbName,
            bytes = dest.length(),
            savedAt = System.currentTimeMillis()
        )
        items.removeAll { it.key == key }
        items.add(0, video)
        write(ctx)
    }

    @Synchronized
    fun remove(ctx: Context, video: OfflineVideo) {
        val folder = dir(ctx)
        runCatching { File(folder, video.fileName).delete() }
        video.thumbName?.let { name -> runCatching { File(folder, name).delete() } }
        items.removeAll { it.key == video.key }
        write(ctx)
    }

    private fun saveThumb(url: String?, dest: File): Boolean {
        if (url.isNullOrBlank()) return false
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", PmDownloader.USER_AGENT)
            .build()
        PmDownloader.client.newCall(request).execute().use { r ->
            if (!r.isSuccessful) return false
            val body = r.body ?: return false
            dest.outputStream().use { out -> body.byteStream().use { it.copyTo(out) } }
        }
        return true
    }

    private fun durationOf(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun write(ctx: Context) {
        val array = JSONArray()
        items.forEach { v ->
            array.put(
                JSONObject()
                    .put("key", v.key)
                    .put("url", v.url)
                    .put("title", v.title)
                    .put("channel", v.channel)
                    .put("duration", v.durationMs)
                    .put("audio", v.isAudio)
                    .put("quality", v.quality)
                    .put("format", v.format)
                    .put("file", v.fileName)
                    .put("thumb", v.thumbName ?: "")
                    .put("bytes", v.bytes)
                    .put("saved", v.savedAt)
            )
        }
        runCatching { index(ctx).writeText(array.toString()) }
    }

    private fun read(ctx: Context): List<OfflineVideo> {
        val file = index(ctx)
        if (!file.exists()) return emptyList()
        val array = JSONArray(file.readText())
        val out = mutableListOf<OfflineVideo>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val fileName = o.optString("file")
            if (fileName.isBlank()) continue
            out += OfflineVideo(
                key = o.optString("key").ifBlank { fileName.substringBeforeLast('.') },
                url = o.optString("url"),
                title = o.optString("title"),
                channel = o.optString("channel"),
                durationMs = o.optLong("duration"),
                isAudio = o.optBoolean("audio"),
                quality = o.optString("quality"),
                format = o.optString("format"),
                fileName = fileName,
                thumbName = o.optString("thumb").takeIf { it.isNotBlank() },
                bytes = o.optLong("bytes"),
                savedAt = o.optLong("saved")
            )
        }
        return out
    }
}

/** The in-app download of this video that is running right now, if there is one. */
fun activeDownload(url: String): SaveJob? {
    val id = videoIdOf(url) ?: url
    return SaveCenter.jobs.firstOrNull { job ->
        val source = job.source?.url
        job.offline && job.running && source != null && (videoIdOf(source) ?: source) == id
    }
}
