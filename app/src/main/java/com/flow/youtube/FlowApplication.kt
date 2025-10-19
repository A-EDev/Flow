package com.flow.youtube

import android.app.Application
import com.flow.youtube.data.repository.NewPipeDownloader
import org.schabi.newpipe.extractor.NewPipe

class FlowApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize NewPipeExtractor
        NewPipe.init(NewPipeDownloader.getInstance())
    }
}

