package com.playmate.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.BitSet
import java.util.concurrent.atomic.AtomicInteger

/** YouTube stopped honouring this stream link (they last a few hours). The caller looks the video up again. */
internal class ExpiredLinkException(message: String) : IOException(message)

internal class Probe(val total: Long, val ranges: Boolean)

/** Each connection takes the next piece of this size; a piece is the unit that can be skipped when resuming. */
internal const val CHUNK = 4L * 1024 * 1024

private const val BUFFER = 128 * 1024

// ------------------------------------------------------------------ size probe

/** Size in bytes of a stream, or -1 when it can't be told. For showing sizes in the sheets. */
suspend fun probeSize(url: String): Long = withContext(Dispatchers.IO) {
    attempt { probeNow(url).total }.getOrDefault(-1L)
}

internal fun probeNow(url: String): Probe {
    val request = Request.Builder()
        .url(url)
        .header("User-Agent", PmDownloader.USER_AGENT)
        .header("Range", "bytes=0-0")
        .build()
    return PmDownloader.downloadClient.newCall(request).execute().use { r ->
        when (r.code) {
            206 -> Probe(
                r.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull() ?: -1L,
                true
            )
            200 -> Probe(r.body?.contentLength() ?: -1L, false)
            403, 404, 410 -> throw ExpiredLinkException("HTTP ${r.code}")
            else -> throw IOException("HTTP ${r.code}")
        }
    }
}

// ------------------------------------------------------------------ which pieces are done

/** Remembers which pieces of one stream are fully written, so a stopped download carries on instead of starting over. */
internal class ChunkMap(val total: Long, val chunk: Long) {
    val count: Int = ((total + chunk - 1) / chunk).toInt()
    private val bits = BitSet(count)

    @Synchronized
    fun isDone(index: Int): Boolean = bits.get(index)

    @Synchronized
    fun mark(index: Int) {
        bits.set(index)
    }

    fun sizeOf(index: Int): Long = minOf(chunk, total - index * chunk)

    @Synchronized
    fun doneBytes(): Long {
        var sum = 0L
        var i = bits.nextSetBit(0)
        while (i >= 0) {
            sum += sizeOf(i)
            i = bits.nextSetBit(i + 1)
        }
        return sum
    }

    @Synchronized
    private fun snapshot(): ByteArray = bits.toByteArray()

    fun save(file: File) {
        runCatching {
            val bytes = snapshot()
            DataOutputStream(file.outputStream().buffered()).use { out ->
                out.writeLong(total)
                out.writeLong(chunk)
                out.writeInt(bytes.size)
                out.write(bytes)
            }
        }
    }

    companion object {
        fun load(file: File, total: Long, chunk: Long): ChunkMap {
            val map = ChunkMap(total, chunk)
            runCatching {
                if (file.exists()) {
                    DataInputStream(file.inputStream().buffered()).use { input ->
                        if (input.readLong() == total && input.readLong() == chunk) {
                            val bytes = ByteArray(input.readInt())
                            input.readFully(bytes)
                            val saved = BitSet.valueOf(bytes)
                            synchronized(map) { map.bits.or(saved) }
                        }
                    }
                }
            }
            return map
        }
    }
}

// ------------------------------------------------------------------ downloading

private fun writeFully(ch: FileChannel, buffer: ByteBuffer, start: Long) {
    var at = start
    while (buffer.hasRemaining()) at += ch.write(buffer, at)
}

private fun workersFor(total: Long): Int = when {
    total < 6L * 1024 * 1024 -> 2
    total < 40L * 1024 * 1024 -> 4
    else -> 6
}

/**
 * Downloads one stream into [part]: several connections at once, each taking the next piece. Pieces
 * that are done are remembered in [mapFile]; running this again after a pause, a lost connection or an
 * app restart fetches only what is missing. Progress is added to [job] as bytes arrive.
 */
internal suspend fun fetchResumable(url: String, part: File, mapFile: File, probe: Probe, job: SaveJob) {
    if (!probe.ranges || probe.total <= 0) {
        // The server won't do pieces: one plain stream, and it starts over after an interruption.
        part.delete()
        mapFile.delete()
        RandomAccessFile(part, "rw").use { raf ->
            withContext(Dispatchers.IO) { streamWhole(url, raf.channel, job) { ensureActive() } }
        }
        return
    }

    val total = probe.total
    var map = ChunkMap.load(mapFile, total, CHUNK)
    if (!part.exists() || part.length() != total) {
        // The file is gone or is not the one the map describes.
        part.delete()
        map = ChunkMap(total, CHUNK)
    }
    val done = map

    RandomAccessFile(part, "rw").use { raf ->
        if (raf.length() != total) raf.setLength(total)
        val ch = raf.channel
        job.bytes.addAndGet(done.doneBytes())

        coroutineScope {
            val next = AtomicInteger(0)
            val saver = launch(Dispatchers.IO) {
                while (true) {
                    delay(1500)
                    persist(ch, done, mapFile)
                }
            }
            try {
                List(workersFor(total)) {
                    async(Dispatchers.IO) {
                        while (true) {
                            ensureActive()
                            val index = next.getAndIncrement()
                            if (index >= done.count) break
                            if (done.isDone(index)) continue
                            val from = index * CHUNK
                            val to = from + done.sizeOf(index) - 1
                            getRange(url, from, to, ch, job) { ensureActive() }
                            done.mark(index)
                        }
                    }
                }.awaitAll()
            } finally {
                saver.cancel()
                persist(ch, done, mapFile)
            }
        }
    }
}

private fun persist(ch: FileChannel, map: ChunkMap, mapFile: File) {
    runCatching { ch.force(false) }
    map.save(mapFile)
}

private fun streamWhole(url: String, ch: FileChannel, job: SaveJob, alive: () -> Unit) {
    var counted = 0L
    try {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", PmDownloader.USER_AGENT)
            .build()
        val call = PmDownloader.downloadClient.newCall(request)
        job.calls.add(call)
        try {
            call.execute().use { r ->
                if (r.code == 403 || r.code == 404 || r.code == 410) throw ExpiredLinkException("HTTP ${r.code}")
                if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
                val src = (r.body ?: throw IOException("Empty response")).byteStream()
                val buf = ByteArray(BUFFER)
                var at = 0L
                while (true) {
                    alive()
                    val n = src.read(buf)
                    if (n < 0) break
                    writeFully(ch, ByteBuffer.wrap(buf, 0, n), at)
                    at += n
                    counted += n
                    job.bytes.addAndGet(n.toLong())
                    job.session.addAndGet(n.toLong())
                }
            }
        } finally {
            job.calls.remove(call)
        }
    } catch (e: Throwable) {
        job.bytes.addAndGet(-counted)
        throw e
    }
}

/** Fetches bytes [from]..[to] into the file, picking up where it left off after a dropped connection. */
private fun getRange(url: String, from: Long, to: Long, ch: FileChannel, job: SaveJob, alive: () -> Unit) {
    var pos = from
    var failures = 0
    var counted = 0L
    try {
        while (pos <= to) {
            alive()
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", PmDownloader.USER_AGENT)
                    .header("Range", "bytes=$pos-$to")
                    .build()
                val call = PmDownloader.downloadClient.newCall(request)
                job.calls.add(call)
                try {
                    call.execute().use { r ->
                        when {
                            r.code == 206 -> Unit
                            r.code == 403 || r.code == 404 || r.code == 410 ->
                                throw ExpiredLinkException("HTTP ${r.code}")
                            else -> throw IOException("HTTP ${r.code}")
                        }
                        val src = (r.body ?: throw IOException("Empty response")).byteStream()
                        val buf = ByteArray(BUFFER)
                        while (pos <= to) {
                            alive()
                            val n = src.read(buf)
                            if (n < 0) break
                            val take = minOf(n.toLong(), to - pos + 1).toInt()
                            writeFully(ch, ByteBuffer.wrap(buf, 0, take), pos)
                            pos += take
                            counted += take
                            job.bytes.addAndGet(take.toLong())
                            job.session.addAndGet(take.toLong())
                        }
                    }
                } finally {
                    job.calls.remove(call)
                }
                if (pos <= to) throw IOException("Connection closed early")
            } catch (e: ExpiredLinkException) {
                throw e
            } catch (e: IOException) {
                // A pause or cancel closes the connection on purpose: that is not a failure to retry.
                alive()
                failures++
                if (failures >= 5) throw e
                Thread.sleep(400L * failures)
            }
        }
    } catch (e: Throwable) {
        // This piece starts again from its beginning next time, so its bytes do not count yet.
        job.bytes.addAndGet(-counted)
        throw e
    }
}
