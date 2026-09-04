package com.example.samehadaku

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class SamehadakuPlugin : Plugin() {
    override fun load(context: Context) {
        // Mendaftarkan class MainAPI Samehadaku agar terbaca di Cloudstream
        registerMainAPI(samehadaku())
    }
}
