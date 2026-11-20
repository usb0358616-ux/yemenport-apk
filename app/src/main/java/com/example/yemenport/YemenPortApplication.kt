package com.example.yemenport

import android.app.Application
import com.example.yemenport.data.remote.SovereignSyncScheduler
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class YemenPortApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize PDFBox resources for offline PDF text extraction
        runCatching {
            PDFBoxResourceLoader.init(applicationContext)
        }
        // Register WorkManager periodic sync constrained to NetworkType.CONNECTED
        runCatching {
            SovereignSyncScheduler.schedulePeriodicSync(applicationContext)
        }
        // Note: Firebase App Check MUST be configured inside MainActivity.onCreate(intent)
        // to preserve FIREBASE_APPCHECK_DEBUG_TOKEN intent extras.
    }
}
