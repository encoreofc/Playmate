package com.playmate.app.ui.player

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.playmate.app.data.PmDownloader
import java.io.File

/**
 * Disk cache for Shorts, the same idea Media3 documents for short-form feeds: the first part of the
 * next Shorts is fetched before the viewer swipes, so playback starts from the phone's own storage
 * instead of waiting for a connection. Whatever was loaded also stays there, so swiping back is instant.
 */
object ShortsCache {
    private const val MAX_BYTES = 200L * 1024 * 1024

    /** How much of each stream is fetched ahead: enough for the first moments of a Short. */
    const val HEAD_BYTES = 768L * 1024

    @Volatile
    private var cache: SimpleCache? = null

    fun get(context: Context): Cache =
        cache ?: synchronized(this) {
            cache ?: SimpleCache(
                File(context.applicationContext.cacheDir, "shorts"),
                LeastRecentlyUsedCacheEvictor(MAX_BYTES),
                StandaloneDatabaseProvider(context.applicationContext)
            ).also { cache = it }
        }

    /** Reads through the cache, and goes to the network for what is not in it. */
    fun dataSourceFactory(context: Context): CacheDataSource.Factory =
        CacheDataSource.Factory()
            .setCache(get(context))
            .setUpstreamDataSourceFactory(
                OkHttpDataSource.Factory(PmDownloader.client).setUserAgent(PmDownloader.USER_AGENT)
            )
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    /** A running fetch of the first bytes of one stream. [cancel] stops it (the viewer scrolled past). */
    class Fetch(private val writer: CacheWriter) {
        fun cancel() = writer.cancel()

        /** Blocks until the bytes are in the cache, cancelled, or failed. Call from a background thread. */
        fun download() {
            runCatching { writer.cache() }
        }
    }

    fun fetchHead(context: Context, url: String): Fetch {
        val spec = DataSpec.Builder()
            .setUri(url)
            .setPosition(0)
            .setLength(HEAD_BYTES)
            .build()
        val source = dataSourceFactory(context).createDataSource()
        return Fetch(CacheWriter(source, spec, null, null))
    }
}
