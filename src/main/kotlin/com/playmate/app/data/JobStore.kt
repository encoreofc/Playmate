package com.playmate.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** What is needed to bring an unfinished download back after the app was closed. */
internal class SavedJob(
    val id: Int,
    val url: String,
    val title: String,
    val thumb: String?,
    val key: String,
    val name: String,
    val offline: Boolean
)

/** Keeps the list of unfinished downloads on disk (save_queue.json). The pieces themselves are in the "partial" folder. */
internal object JobStore {

    private fun file(ctx: Context) = File(ctx.filesDir, "save_queue.json")

    @Synchronized
    fun save(ctx: Context, jobs: List<SaveJob>) {
        runCatching {
            val array = JSONArray()
            for (job in jobs) {
                val url = job.source?.url ?: continue
                array.put(
                    JSONObject()
                        .put("id", job.id)
                        .put("url", url)
                        .put("title", job.title)
                        .put("thumb", job.thumb)
                        .put("key", job.target.key)
                        .put("name", job.name)
                        .put("offline", job.offline)
                )
            }
            val temp = File(ctx.filesDir, "save_queue.json.tmp")
            temp.writeText(array.toString())
            if (!temp.renameTo(file(ctx))) {
                file(ctx).writeText(array.toString())
                temp.delete()
            }
        }
    }

    @Synchronized
    fun load(ctx: Context): List<SavedJob> = runCatching {
        val f = file(ctx)
        if (!f.exists()) return@runCatching emptyList<SavedJob>()
        val array = JSONArray(f.readText())
        (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val url = o.optString("url", "")
            val key = o.optString("key", "")
            if (url.isEmpty() || key.isEmpty()) return@mapNotNull null
            SavedJob(
                id = o.optInt("id", -1),
                url = url,
                title = o.optString("title", ""),
                thumb = o.optString("thumb", "").takeIf { it.isNotEmpty() },
                key = key,
                name = o.optString("name", "Playmate video"),
                offline = o.optBoolean("offline", false)
            )
        }.filter { it.id >= 0 }
    }.getOrDefault(emptyList())
}
