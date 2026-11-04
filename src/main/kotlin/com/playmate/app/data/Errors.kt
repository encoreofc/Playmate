package com.playmate.app.data

import kotlinx.coroutines.CancellationException
import java.io.IOException

inline fun <T> attempt(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}

fun friendly(e: Throwable): String {
    val name = e.javaClass.simpleName
    return when {
        e is IOException ->
            "Can't reach YouTube. Check your connection and try again."
        name.contains("AgeRestricted") ->
            "This video is age-restricted. Watching it needs a signed-in account, which Playmate doesn't support yet."
        name.contains("SignIn") ->
            "YouTube is asking this connection to prove it isn't a bot. It usually clears after a few minutes or on another network."
        name.contains("ReCaptcha") ->
            "YouTube is rate-limiting this connection. Wait a minute and try again."
        name.contains("Private") ->
            "This video is private."
        name.contains("Geographic") ->
            "This video isn't available in your country."
        name.contains("Paid") || name.contains("Premium") ->
            "This content needs a paid subscription."
        name.contains("ContentNotAvailable") || name.contains("ContentNotSupported") ->
            e.message?.takeIf { it.isNotBlank() } ?: "This video isn't available."
        else ->
            "Something went wrong while loading this. Try again."
    }
}
