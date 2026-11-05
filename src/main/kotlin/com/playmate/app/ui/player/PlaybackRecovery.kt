package com.playmate.app.ui.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.PlaybackException

/**
 * Shared between the background service (which repairs a broken stream while the screen is off)
 * and the player screen (which shows the error once the repair has given up).
 */
object PlaybackRecovery {

    /** Set by the service when it has used up its retries; the screen reacts and clears it. */
    var gaveUp by mutableStateOf<PlaybackException?>(null)

    fun reset() {
        gaveUp = null
    }

    /**
     * Errors that come from the network or from an expired link, not from the video itself.
     * The service retries these (and fetches fresh links when needed) instead of stopping.
     */
    fun isRecoverable(error: PlaybackException): Boolean = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        PlaybackException.ERROR_CODE_TIMEOUT,
        PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> true
        else -> false
    }

    /** A refused request (403, 410...) usually means the stream link has expired. */
    fun isExpiredLink(error: PlaybackException): Boolean =
        error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
}
