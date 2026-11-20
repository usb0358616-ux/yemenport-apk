package com.example.yemenport

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class YemenPortApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize PDFBox resources for offline PDF text extraction
        runCatching {
            PDFBoxResourceLoader.init(applicationContext)
        }
        // Note: Firebase App Check MUST be configured inside MainActivity.onCreate(intent)
        // to preserve FIREBASE_APPCHECK_DEBUG_TOKEN intent extras.
    }
}
