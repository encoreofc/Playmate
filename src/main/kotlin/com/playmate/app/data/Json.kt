package com.playmate.app.data

import org.json.JSONArray
import org.json.JSONObject

/** Small helpers for reading YouTube's JSON without depending on its exact layout. */

/** The text of a YouTube "text" object: either simpleText, or the runs joined together. */
fun ytText(o: JSONObject?): String {
    if (o == null) return ""
    val simple = o.optString("simpleText")
    if (simple.isNotBlank()) return simple
    val runs = o.optJSONArray("runs")
    if (runs != null) {
        return buildString {
            for (i in 0 until runs.length()) append(runs.optJSONObject(i)?.optString("text").orEmpty())
        }
    }
    return o.optString("content")
}

/** The url of the last (usually largest) entry of a thumbnails / sources array. */
fun lastImageUrl(arr: JSONArray?): String? {
    if (arr == null || arr.length() == 0) return null
    return arr.optJSONObject(arr.length() - 1)?.optString("url")?.takeIf { it.isNotBlank() }
}

/** Every object stored under [key] anywhere inside [node] (matches are not searched further). */
fun findAllObjects(node: Any?, key: String): List<JSONObject> {
    val out = mutableListOf<JSONObject>()
    collectAll(node, key, out, null)
    return out
}

/** Every array stored under [key] anywhere inside [node] (matches are not searched further). */
fun findAllArrays(node: Any?, key: String): List<JSONArray> {
    val out = mutableListOf<JSONArray>()
    collectAll(node, key, null, out)
    return out
}

private fun collectAll(node: Any?, key: String, objects: MutableList<JSONObject>?, arrays: MutableList<JSONArray>?) {
    when (node) {
        is JSONObject -> {
            val keys = node.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = node.opt(k)
                when {
                    k == key && v is JSONObject && objects != null -> objects += v
                    k == key && v is JSONArray && arrays != null -> arrays += v
                    else -> collectAll(v, key, objects, arrays)
                }
            }
        }
        is JSONArray -> for (i in 0 until node.length()) collectAll(node.opt(i), key, objects, arrays)
    }
}

/** First non-blank string stored under [key] anywhere inside [node]. */
fun findString(node: Any?, key: String): String? {
    when (node) {
        is JSONObject -> {
            val direct = node.opt(key)
            if (direct is String && direct.isNotBlank()) return direct
            val keys = node.keys()
            while (keys.hasNext()) {
                findString(node.opt(keys.next()), key)?.let { return it }
            }
        }
        is JSONArray -> for (i in 0 until node.length()) {
            findString(node.opt(i), key)?.let { return it }
        }
    }
    return null
}

/** First boolean stored under [key] anywhere inside [node]. */
fun findBoolean(node: Any?, key: String): Boolean? {
    when (node) {
        is JSONObject -> {
            val direct = node.opt(key)
            if (direct is Boolean) return direct
            val keys = node.keys()
            while (keys.hasNext()) {
                findBoolean(node.opt(keys.next()), key)?.let { return it }
            }
        }
        is JSONArray -> for (i in 0 until node.length()) {
            findBoolean(node.opt(i), key)?.let { return it }
        }
    }
    return null
}

/** The "load more" token inside [node] (first continuationCommand), if any. */
fun tokenOf(node: Any?): String? =
    findObject(node, "continuationCommand")?.optString("token")?.takeIf { it.isNotBlank() }
