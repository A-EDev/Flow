package com.flow.youtube

import android.app.Application
import android.util.Log
import com.flow.youtube.data.repository.NewPipeDownloader
import org.schabi.newpipe.extractor.NewPipe

class FlowApplication : Application() {
    
    companion object {
        private const val TAG = "FlowApplication"
    }
    
    override fun onCreate() {
        super.onCreate()
        
        try {
            // Initialize NewPipeExtractor with error handling
            NewPipe.init(NewPipeDownloader.getInstance())
            Log.d(TAG, "NewPipe initialized successfully")
        } catch (e: Exception) {
            // Log error but don't crash the app
            Log.e(TAG, "Failed to initialize NewPipe", e)
        }
    }
}

