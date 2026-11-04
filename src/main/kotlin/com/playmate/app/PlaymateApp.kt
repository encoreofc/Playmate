package com.playmate.app

import android.app.Application
import com.playmate.app.data.PmDownloader
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization

class PlaymateApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NewPipe.init(PmDownloader, Localization("en", "BD"), ContentCountry("BD"))
    }
}
