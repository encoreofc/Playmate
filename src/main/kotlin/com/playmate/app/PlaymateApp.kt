package com.playmate.app

import android.app.Application
import com.playmate.app.data.Ffmpeg
import com.playmate.app.data.OfflineStore
import com.playmate.app.data.PmDownloader
import com.playmate.app.data.SaveCenter
import com.playmate.app.ui.player.ShortsCache
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization

class PlaymateApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NewPipe.init(PmDownloader, Localization("en", "BD"), ContentCountry("BD"))
        OfflineStore.init(this)
        // Downloads that were unfinished when the app closed come back, paused.
        SaveCenter.restore(this)
        // Opening the Shorts disk cache reads the disk; do it now, away from the screen.
        Thread { runCatching { ShortsCache.get(this) } }.start()
        // Finds out in the background whether FFmpeg (and its MP3 encoder) is really usable.
        Ffmpeg.warmUp()
    }
}
