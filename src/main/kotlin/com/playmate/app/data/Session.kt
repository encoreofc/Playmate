package com.playmate.app.data

import android.content.Context
import android.webkit.CookieManager
import java.io.File
import java.security.MessageDigest

/**
 * The signed-in YouTube session.
 * The cookies come from Google's own sign-in page (shown in a WebView); the password
 * is typed on Google's page and never reaches Playmate.
 */
object Session {

    private var file: File? = null

    @Volatile
    var cookie: String = ""
        private set

    fun init(context: Context) {
        // noBackupFilesDir: the session is never copied into cloud backups.
        val f = File(context.noBackupFilesDir, "yt_session.txt")
        file = f
        cookie = runCatching { f.readText().trim() }.getOrDefault("")
    }

    val loggedIn: Boolean get() = sapisid() != null

    fun save(raw: String) {
        cookie = raw.trim()
        runCatching { file?.writeText(cookie) }
    }

    fun clear() {
        cookie = ""
        runCatching { file?.delete() }
        val manager = CookieManager.getInstance()
        manager.removeAllCookies(null)
        manager.flush()
    }

    fun value(name: String): String? =
        cookie.split(";")
            .map { it.trim() }
            .firstOrNull { it.startsWith("$name=") }
            ?.substringAfter("=")
            ?.takeIf { it.isNotBlank() }

    private fun sapisid(): String? = value("SAPISID") ?: value("__Secure-3PAPISID")

    /** Authorization header that YouTube's own web client sends with the session cookies. */
    fun authHeader(origin: String): String {
        val ts = System.currentTimeMillis() / 1000
        val main = sapisid() ?: return ""
        val parts = mutableListOf("SAPISIDHASH ${ts}_${sha1("$ts $main $origin")}")
        value("__Secure-1PAPISID")?.let { parts += "SAPISID1PHASH ${ts}_${sha1("$ts $it $origin")}" }
        value("__Secure-3PAPISID")?.let { parts += "SAPISID3PHASH ${ts}_${sha1("$ts $it $origin")}" }
        return parts.joinToString(" ")
    }

    private fun sha1(text: String): String =
        MessageDigest.getInstance("SHA-1")
            .digest(text.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
