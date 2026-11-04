package com.playmate.app.data

import android.content.Context
import java.io.File

/**
 * Remembers which videos the Home feed has already shown (newest last, capped), across app starts.
 * The feed uses it to put videos you haven't seen yet first, so a refresh doesn't start with the
 * same cards again.
 */
class SeenStore(context: Context) {

    private val file = File(context.filesDir, "seen_videos.txt")
    private val ids = LinkedHashSet<String>()

    init {
        runCatching { file.readLines().filter { it.isNotBlank() }.takeLast(CAP).forEach { ids += it } }
    }

    @Synchronized
    fun has(url: String): Boolean = keyOf(url) in ids

    /** Returns true when [url] was not known before. */
    @Synchronized
    fun add(url: String): Boolean {
        val key = keyOf(url)
        if (key in ids) return false
        ids += key
        while (ids.size > CAP) ids.remove(ids.first())
        return true
    }

    @Synchronized
    fun save() {
        runCatching { file.writeText(ids.joinToString("\n")) }
    }

    @Synchronized
    fun clear() {
        ids.clear()
        runCatching { file.delete() }
    }

    private fun keyOf(url: String) = videoIdOf(url) ?: url

    private companion object {
        const val CAP = 1500
    }
}
